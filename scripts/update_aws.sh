#!/usr/bin/env bash
# Build an already-deployed artifact and roll it out under an immutable version tag.
# Does not create RDS, IAM, security groups, App Runner, or the Fargate schedule.
#
# Usage:
#   ./scripts/update_aws.sh                 # interactive
#   ./scripts/update_aws.sh app
#   ./scripts/update_aws.sh backfill
#   ./scripts/update_aws.sh both
#   ./scripts/update_aws.sh backfill --run-task          # also RunTask --days 7
#   ./scripts/update_aws.sh backfill --run-task=full     # also RunTask with no --days
#   ./scripts/update_aws.sh run                          # RunTask only, no build/push
#   ./scripts/update_aws.sh run --run-task=full          # RunTask only, full history
#   ./scripts/update_aws.sh status
#   ./scripts/update_aws.sh bootstrap                    # name :latest (app + backfill)
#   ./scripts/update_aws.sh bootstrap app
#   ./scripts/update_aws.sh rollback app [--to VERSION]
#   ./scripts/update_aws.sh rollback backfill [--to VERSION]
#   ./scripts/update_aws.sh app --allow-dirty
#
# Env: AWS_REGION (default us-east-1), APP_NAME (default investment-tracker)
#      NO_COLOR=1 disables color.

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AWS_REGION="${AWS_REGION:-us-east-1}"
APP_NAME="${APP_NAME:-investment-tracker}"
PLATFORM="${DOCKER_PLATFORM:-linux/amd64}"
export AWS_PAGER=""

if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
  RESET=$'\033[0m'
  BOLD=$'\033[1m'
  DIM=$'\033[2m'
  RED=$'\033[31m'
  GREEN=$'\033[32m'
  YELLOW=$'\033[33m'
  BLUE=$'\033[34m'
  MAGENTA=$'\033[35m'
  CYAN=$'\033[36m'
else
  RESET= BOLD= DIM= RED= GREEN= YELLOW= BLUE= MAGENTA= CYAN=
fi

usage() {
  cat <<EOF
${BOLD}Build and update${RESET} an existing Investment Tracker artifact in AWS.

  ${CYAN}$(basename "$0")${RESET} ${YELLOW}[app|backfill|both|run|status|bootstrap|rollback]${RESET}
      ${DIM}[--run-task[=nightly|full|none]] [--allow-dirty] [--to VERSION]${RESET}

With no command, you get a prompt. Infrastructure (RDS, IAM, networking,
App Runner service, ECS cluster, EventBridge) is left as-is.

Images are tagged ${BOLD}YYYY.MM.DD-<gitsha>${RESET} (immutable). There is no ${CYAN}:latest${RESET}.

  ${GREEN}app${RESET}        JVM image (SPA + API) → ECR → App Runner update-service
  ${GREEN}backfill${RESET}   Python image → ECR → new Fargate task-definition revision
  ${GREEN}both${RESET}       app, then backfill (same version)
  ${GREEN}run${RESET}        No build — start the backfill Fargate task on the pinned image
  ${GREEN}status${RESET}     What App Runner / Fargate are running, plus recent ECR tags
  ${GREEN}bootstrap${RESET}  One-shot: name the live ${CYAN}:latest${RESET} digest and pin services to it
  ${GREEN}rollback${RESET}   Re-pin app or backfill to an existing ECR tag (no build)

  ${YELLOW}--run-task${RESET}         After pushing backfill, start a Fargate task (--days 7)
  ${YELLOW}--run-task=full${RESET}    Same, but full history (no --days)
  ${YELLOW}--run-task=none${RESET}    Push only (default when not a TTY)
  ${YELLOW}--allow-dirty${RESET}      Deploy a dirty worktree as ${DIM}…-dirty.<epoch>${RESET} (no git tag)
  ${YELLOW}--to VERSION${RESET}       Rollback target (required when stdin is not a TTY)

Region defaults to ${BOLD}us-east-1${RESET}. Override with AWS_REGION / APP_NAME.
Set ${DIM}NO_COLOR=1${RESET} to disable color.
EOF
}

log()  { printf '%s\n' "$*"; }
ok()   { printf '%s\n' "${GREEN}✔${RESET} $*"; }
warn() { printf '%s\n' "${YELLOW}!${RESET} $*"; }
info() { printf '%s\n' "${DIM}  $*${RESET}"; }
header() {
  log ""
  log "${BOLD}${CYAN}$*${RESET}"
}
fail() {
  printf '%s\n' "${RED}${BOLD}error:${RESET} ${RED}$*${RESET}" >&2
  exit 1
}

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || fail "missing command: $1"
}

is_tty() { [ -t 0 ] && [ -t 1 ]; }

confirm() {
  local prompt="${1:-Proceed?}"
  local reply
  if ! is_tty; then
    return 0
  fi
  printf '%s ' "${YELLOW}${prompt}${RESET} ${DIM}[Y/n]${RESET}"
  read -r reply || true
  case "$reply" in
    ''|y|Y|yes|YES) return 0 ;;
    *) warn "aborted."; exit 0 ;;
  esac
}

ask_yes() {
  local prompt="$1"
  local reply
  if ! is_tty; then
    return 1
  fi
  printf '%s ' "${YELLOW}${prompt}${RESET} ${DIM}[y/N]${RESET}"
  read -r reply || true
  case "$reply" in
    y|Y|yes|YES) return 0 ;;
    *) return 1 ;;
  esac
}

prompt_choice() {
  local prompt="$1"
  shift
  local i=1
  local opt
  local reply
  log ""
  log "${BOLD}${prompt}${RESET}"
  for opt in "$@"; do
    log "  ${CYAN}${i})${RESET} ${opt}"
    i=$((i + 1))
  done
  printf '%s ' "${YELLOW}Choice${RESET} ${DIM}[1]${RESET}:"
  read -r reply || true
  if [ -z "$reply" ]; then
    reply=1
  fi
  case "$reply" in
    *[!0-9]*) fail "not a number: $reply" ;;
  esac
  if [ "$reply" -lt 1 ] || [ "$reply" -gt $# ]; then
    fail "choice out of range: $reply"
  fi
  eval "PROMPT_RESULT=\${$reply}"
}

require_value() {
  local name="$1"
  local value="$2"
  if [ -z "$value" ] || [ "$value" = "None" ] || [ "$value" = "null" ]; then
    fail "$name not found in $AWS_REGION. This script only updates existing resources — see docs/AWS-BUILD-AND-DEPLOY.md"
  fi
}

ecr_uri() {
  local repo="$1"
  printf '%s\n' "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/${repo}"
}

image_ref() {
  local repo="$1"
  local tag="$2"
  printf '%s:%s\n' "$(ecr_uri "$repo")" "$tag"
}

tag_from_ref() {
  local ref="${1##*:}"
  printf '%s\n' "$ref"
}

ecr_login() {
  log "${BLUE}→${RESET} ECR login ${DIM}($AWS_REGION)${RESET}"
  aws ecr get-login-password --region "$AWS_REGION" \
    | podman login --username AWS --password-stdin \
      "${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com" >/dev/null
  ok "logged in to ECR"
}

wait_apprunner_running() {
  local arn="$1"
  local expected="${2:-}"
  local status ident
  log "${BLUE}→${RESET} Waiting for App Runner to become ${GREEN}RUNNING${RESET}…"
  while true; do
    status="$(aws apprunner describe-service --region "$AWS_REGION" \
      --service-arn "$arn" --query Service.Status --output text)"
    ident="$(aws apprunner describe-service --region "$AWS_REGION" \
      --service-arn "$arn" \
      --query 'Service.SourceConfiguration.ImageRepository.ImageIdentifier' \
      --output text)"
    case "$status" in
      RUNNING)
        if [ -n "$expected" ] && [ "$ident" != "$expected" ]; then
          log "  ${DIM}$(date '+%H:%M:%S')${RESET} status=${GREEN}${status}${RESET} image=${YELLOW}${ident}${RESET}"
        else
          log "  ${DIM}$(date '+%H:%M:%S')${RESET} status=${GREEN}${BOLD}${status}${RESET}"
          return 0
        fi
        ;;
      UPDATE_FAILED|CREATE_FAILED|DELETED)
        log "  ${DIM}$(date '+%H:%M:%S')${RESET} status=${RED}${BOLD}${status}${RESET}"
        fail "App Runner ended in $status"
        ;;
      *)
        log "  ${DIM}$(date '+%H:%M:%S')${RESET} status=${YELLOW}${status}${RESET}"
        ;;
    esac
    sleep 20
  done
}

resolve_app_runner() {
  SERVICE_ARN="$(aws apprunner list-services --region "$AWS_REGION" \
    --query "ServiceSummaryList[?ServiceName=='${APP_NAME}'].ServiceArn | [0]" \
    --output text)"
  require_value "App Runner service ${APP_NAME}" "$SERVICE_ARN"
}

resolve_ecr_repo() {
  local name="$1"
  aws ecr describe-repositories --region "$AWS_REGION" \
    --repository-names "$name" --query 'repositories[0].repositoryUri' \
    --output text >/dev/null 2>&1 \
    || fail "ECR repository ${name} not found in ${AWS_REGION}"
}

current_app_image() {
  resolve_app_runner
  aws apprunner describe-service --region "$AWS_REGION" \
    --service-arn "$SERVICE_ARN" \
    --query 'Service.SourceConfiguration.ImageRepository.ImageIdentifier' \
    --output text
}

current_app_status() {
  resolve_app_runner
  aws apprunner describe-service --region "$AWS_REGION" \
    --service-arn "$SERVICE_ARN" --query Service.Status --output text
}

current_backfill_image() {
  aws ecs describe-task-definition --region "$AWS_REGION" \
    --task-definition "${APP_NAME}-backfill" \
    --query 'taskDefinition.containerDefinitions[0].image' --output text
}

current_backfill_revision() {
  aws ecs describe-task-definition --region "$AWS_REGION" \
    --task-definition "${APP_NAME}-backfill" \
    --query 'taskDefinition.revision' --output text
}

ecr_tag_exists() {
  local repo="$1"
  local tag="$2"
  aws ecr describe-images --region "$AWS_REGION" \
    --repository-name "$repo" --image-ids "imageTag=${tag}" \
    --query 'imageDetails[0].imageDigest' --output text >/dev/null 2>&1
}

ecr_bootstrap_tag() {
  local repo="$1"
  aws ecr describe-images --region "$AWS_REGION" \
    --repository-name "$repo" --output json \
    | python3 -c '
import json, sys
data = json.load(sys.stdin)
tags = []
for d in data.get("imageDetails") or []:
    for t in d.get("imageTags") or []:
        if t.endswith("-bootstrap"):
            tags.append(t)
if tags:
    print(sorted(tags)[-1])
'
}

ensure_version() {
  if [ -n "${VERSION:-}" ]; then
    return 0
  fi
  need_cmd git
  local sha dirty
  sha="$(git -C "$ROOT" rev-parse --short=7 HEAD)"
  VERSION="$(date +%Y.%m.%d)-${sha}"
  SKIP_GIT_TAG=0
  dirty="$(git -C "$ROOT" status --porcelain)"
  if [ -n "$dirty" ]; then
    if [ "$ALLOW_DIRTY" != "1" ]; then
      fail "worktree is dirty; commit your changes or pass --allow-dirty"
    fi
    VERSION="${VERSION}-dirty.$(date +%s)"
    SKIP_GIT_TAG=1
    warn "dirty worktree — version ${CYAN}${VERSION}${RESET} (no git tag)"
  else
    ok "version ${BOLD}${VERSION}${RESET}"
  fi
}

maybe_tag_git() {
  local kind="$1"
  local tag="deploy/${kind}/${VERSION}"
  if [ "${SKIP_GIT_TAG:-0}" = "1" ]; then
    return 0
  fi
  if git -C "$ROOT" rev-parse "$tag" >/dev/null 2>&1; then
    info "git tag ${tag} already exists"
    return 0
  fi
  git -C "$ROOT" tag -a "$tag" -m "Deploy ${kind} ${VERSION}"
  ok "git tag ${tag}"
  if git -C "$ROOT" remote get-url origin >/dev/null 2>&1; then
    if git -C "$ROOT" push origin "refs/tags/${tag}" >/dev/null 2>&1; then
      ok "pushed ${tag}"
    else
      warn "could not push ${tag} to origin (tag is local)"
    fi
  fi
}

commit_from_version() {
  python3 - "$1" <<'PY'
import re, sys
v = sys.argv[1]
if v.endswith("-bootstrap"):
    sys.exit(1)
m = re.match(r"^\d{4}\.\d{2}\.\d{2}-([0-9a-f]+)(?:-dirty\.\d+)?$", v)
if not m:
    sys.exit(1)
print(m.group(1))
PY
}

warn_if_changelog_changed() {
  local current_ref current_tag sha
  current_ref="$(current_app_image)" || return 0
  current_tag="$(tag_from_ref "$current_ref")"
  if ! sha="$(commit_from_version "$current_tag")"; then
    info "live app tag ${current_tag} has no git SHA — skipping changelog diff"
    return 0
  fi
  if ! git -C "$ROOT" cat-file -e "${sha}^{commit}" 2>/dev/null; then
    warn "commit ${sha} is not in this clone — skipping changelog diff"
    return 0
  fi
  local diff
  diff="$(git -C "$ROOT" diff "${sha}" HEAD -- backend/src/main/resources/db/changelog || true)"
  if [ -z "$diff" ]; then
    return 0
  fi
  warn "Liquibase changelog changed since live ${CYAN}${current_tag}${RESET} (${sha})"
  info "Rolling the image back will NOT roll the schema back."
  info "Snapshot first, then continue:"
  info "aws rds create-db-snapshot --region ${AWS_REGION} --db-instance-identifier ${APP_NAME}-pg --db-snapshot-identifier ${APP_NAME}-pre-${VERSION}"
  confirm "Changelog changed. Continue deploy anyway?"
}

pin_app_image() {
  local version="$1"
  local skip_health="${2:-0}"
  local image env_count tmp prev_ref prev_tag
  resolve_ecr_repo "$APP_NAME"
  resolve_app_runner
  image="$(image_ref "$APP_NAME" "$version")"
  prev_ref="$(current_app_image)"
  prev_tag="$(tag_from_ref "$prev_ref")"

  if [ "$prev_tag" = "$version" ]; then
    ok "App Runner already on ${CYAN}${version}${RESET}"
    return 0
  fi

  tmp="$(mktemp)"
  aws apprunner describe-service --region "$AWS_REGION" \
    --service-arn "$SERVICE_ARN" \
    --query Service.SourceConfiguration --output json >"$tmp"
  env_count="$(NEW_IMAGE="$image" python3 - "$tmp" <<'PY'
import json, os, sys
path = sys.argv[1]
with open(path) as f:
    src = json.load(f)
src.pop("AutoDeploymentsEnabled", None)
img = src.get("ImageRepository") or {}
if not img.get("ImageIdentifier"):
    raise SystemExit("described SourceConfiguration has no ImageIdentifier")
cfg = img.get("ImageConfiguration") or {}
env = cfg.get("RuntimeEnvironmentVariables") or {}
if not env:
    raise SystemExit("empty RuntimeEnvironmentVariables — refusing to call update-service")
img["ImageIdentifier"] = os.environ["NEW_IMAGE"]
src["ImageRepository"] = img
with open(path, "w") as f:
    json.dump(src, f)
print(len(env))
PY
)"
  if [ -z "$env_count" ] || [ "$env_count" -lt 1 ]; then
    rm -f "$tmp"
    fail "empty RuntimeEnvironmentVariables — refusing to call update-service"
  fi

  log "${BLUE}→${RESET} App Runner update-service ${CYAN}${image}${RESET} ${DIM}(${env_count} env vars kept)${RESET}"
  aws apprunner update-service --region "$AWS_REGION" \
    --service-arn "$SERVICE_ARN" \
    --source-configuration "file://${tmp}" \
    --query Service.Status --output text >/dev/null
  rm -f "$tmp"
  wait_apprunner_running "$SERVICE_ARN" "$image"

  if [ "$skip_health" = "1" ]; then
    return 0
  fi
  check_app_health "$prev_tag"
}

check_app_health() {
  local prev_tag="$1"
  local app_url body i
  need_cmd curl
  app_url="$(aws apprunner describe-service --region "$AWS_REGION" \
    --service-arn "$SERVICE_ARN" --query Service.ServiceUrl --output text)"
  log "${BLUE}→${RESET} Health check ${DIM}https://${app_url}/actuator/health${RESET}"
  for i in 1 2 3 4 5 6 7 8; do
    body="$(curl -fsS --max-time 15 "https://${app_url}/actuator/health" 2>/dev/null || true)"
    if printf '%s' "$body" | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
      ok "health UP"
      log ""
      ok "App updated: ${BOLD}https://${app_url}${RESET}"
      info "Health: curl -sS https://${app_url}/actuator/health"
      info "Version: curl -sS -u USER:PASS https://${app_url}/actuator/info"
      return 0
    fi
    log "  ${DIM}$(date '+%H:%M:%S')${RESET} waiting for UP…"
    sleep 10
  done
  warn "health check did not return UP"
  if [ -n "$prev_tag" ] && [ "$prev_tag" != "None" ]; then
    if ask_yes "Roll back to ${prev_tag}?"; then
      pin_app_image "$prev_tag" 1
      fail "rolled back to ${prev_tag} after failed health check"
    fi
    fail "health check failed; rollback with: $0 rollback app --to ${prev_tag}"
  fi
  fail "health check failed"
}

register_backfill_image() {
  local version="$1"
  local image tmp current
  resolve_ecr_repo "${APP_NAME}-backfill"
  image="$(image_ref "${APP_NAME}-backfill" "$version")"
  current="$(current_backfill_image)"
  if [ "$current" = "$image" ]; then
    ok "task definition already on ${CYAN}${version}${RESET}"
    return 0
  fi
  tmp="$(mktemp)"
  aws ecs describe-task-definition --region "$AWS_REGION" \
    --task-definition "${APP_NAME}-backfill" \
    --query taskDefinition --output json >"$tmp"
  NEW_IMAGE="$image" python3 - "$tmp" <<'PY'
import json, os, sys
allowed = {
    "family", "taskRoleArn", "executionRoleArn", "networkMode",
    "containerDefinitions", "volumes", "placementConstraints",
    "requiresCompatibilities", "cpu", "memory", "pidMode", "ipcMode",
    "proxyConfiguration", "inferenceAccelerators", "ephemeralStorage",
    "runtimePlatform", "enableFaultInjection",
}
path = sys.argv[1]
with open(path) as f:
    td = json.load(f)
td = {k: v for k, v in td.items() if k in allowed and v is not None}
containers = td.get("containerDefinitions") or []
if not containers:
    raise SystemExit("task definition has no containerDefinitions")
found = False
for c in containers:
    if c.get("name") == "backfill":
        c["image"] = os.environ["NEW_IMAGE"]
        found = True
        break
if not found:
    containers[0]["image"] = os.environ["NEW_IMAGE"]
with open(path, "w") as f:
    json.dump(td, f)
PY
  log "${BLUE}→${RESET} Registering task definition ${CYAN}${image}${RESET}"
  local revision
  revision="$(aws ecs register-task-definition --region "$AWS_REGION" \
    --cli-input-json "file://${tmp}" \
    --query 'taskDefinition.revision' --output text)"
  rm -f "$tmp"
  require_value "task definition revision" "$revision"
  ok "task definition ${APP_NAME}-backfill:${revision}"
  info "EventBridge family ARN picks the newest ACTIVE revision automatically"
}

list_ecr_tags() {
  local repo="$1"
  aws ecr describe-images --region "$AWS_REGION" \
    --repository-name "$repo" --output json \
    | python3 -c '
import json, sys
data = json.load(sys.stdin)
rows = []
for d in data.get("imageDetails") or []:
    pushed = d.get("imagePushedAt") or ""
    size = d.get("imageSizeInBytes") or 0
    tags = d.get("imageTags") or []
    if not tags:
        continue
    for t in tags:
        if t == "latest":
            continue
        rows.append((pushed, t, size))
rows.sort()
for pushed, tag, size in rows[-10:]:
    mb = size / (1024 * 1024)
    print(f"{tag}\t{pushed}\t{mb:.1f}")
'
}

print_ecr_tag_table() {
  local repo="$1"
  local tag pushed mb
  info "recent tags in ${repo}:"
  while IFS=$'\t' read -r tag pushed mb; do
    [ -z "$tag" ] && continue
    info "  ${tag}  ${pushed}  ${mb} MB"
  done <<EOF
$(list_ecr_tags "$repo")
EOF
}

apply_ecr_hygiene() {
  local repo="$1"
  local policy
  log "${BLUE}→${RESET} ECR hygiene ${repo}"
  aws ecr put-image-tag-mutability --region "$AWS_REGION" \
    --repository-name "$repo" \
    --image-tag-mutability IMMUTABLE >/dev/null
  ok "${repo} tags are IMMUTABLE"
  policy="$(mktemp)"
  cat >"$policy" <<'EOF'
{
  "rules": [
    {
      "rulePriority": 1,
      "description": "Keep the newest 15 images",
      "selection": {
        "tagStatus": "any",
        "countType": "imageCountMoreThan",
        "countNumber": 15
      },
      "action": { "type": "expire" }
    }
  ]
}
EOF
  aws ecr put-lifecycle-policy --region "$AWS_REGION" \
    --repository-name "$repo" \
    --lifecycle-policy-text "$(cat "$policy")" >/dev/null
  rm -f "$policy"
  ok "${repo} lifecycle keeps last 15 images"
  if ecr_tag_exists "$repo" latest; then
    aws ecr batch-delete-image --region "$AWS_REGION" \
      --repository-name "$repo" \
      --image-ids imageTag=latest >/dev/null
    ok "deleted ${repo}:latest"
  else
    info "${repo}:latest already absent"
  fi
}

retag_latest_as_bootstrap() {
  local repo="$1"
  local existing pushed tag err
  BOOTSTRAP_TAG=""
  existing="$(ecr_bootstrap_tag "$repo" || true)"
  if [ -n "$existing" ]; then
    ok "${repo} already has ${CYAN}${existing}${RESET} — skipping retag"
    BOOTSTRAP_TAG="$existing"
    return 0
  fi
  if ! ecr_tag_exists "$repo" latest; then
    fail "${repo}:latest not found — cannot bootstrap"
  fi
  pushed="$(aws ecr describe-images --region "$AWS_REGION" \
    --repository-name "$repo" --image-ids imageTag=latest \
    --query 'imageDetails[0].imagePushedAt' --output text)"
  require_value "${repo}:latest imagePushedAt" "$pushed"
  tag="$(python3 - "$pushed" <<'PY'
from datetime import datetime, timezone
import sys
raw = sys.argv[1].replace("Z", "+00:00")
dt = datetime.fromisoformat(raw)
print(dt.astimezone(timezone.utc).strftime("%Y.%m.%d") + "-bootstrap")
PY
)"
  log "${BLUE}→${RESET} Tagging ${repo}:latest digest as ${CYAN}${tag}${RESET}"
  local manifest_file media
  manifest_file="$(mktemp)"
  media="$(aws ecr batch-get-image --region "$AWS_REGION" \
    --repository-name "$repo" --image-ids imageTag=latest --output json \
    | python3 -c '
import json, sys
path = sys.argv[1]
data = json.load(sys.stdin)
images = data.get("images") or []
if not images or not images[0].get("imageManifest"):
    raise SystemExit("no manifest for :latest")
with open(path, "w") as f:
    f.write(images[0]["imageManifest"])
print(images[0].get("imageManifestMediaType") or "")
' "$manifest_file")"
  require_value "${repo}:latest manifest" "$(head -c 20 "$manifest_file")"
  err="$(mktemp)"
  if aws ecr put-image --region "$AWS_REGION" \
      --repository-name "$repo" \
      --image-tag "$tag" \
      --image-manifest "$(cat "$manifest_file")" \
      --image-manifest-media-type "$media" >/dev/null 2>"$err"; then
    ok "${repo}:${tag}"
  elif grep -q ImageAlreadyExistsException "$err"; then
    ok "${repo}:${tag} already present"
  else
    cat "$err" >&2
    rm -f "$err" "$manifest_file"
    fail "put-image ${repo}:${tag} failed"
  fi
  rm -f "$err" "$manifest_file"
  BOOTSTRAP_TAG="$tag"
}

bootstrap_app() {
  header "Bootstrap app"
  resolve_ecr_repo "$APP_NAME"
  resolve_app_runner
  retag_latest_as_bootstrap "$APP_NAME"
  pin_app_image "$BOOTSTRAP_TAG"
  apply_ecr_hygiene "$APP_NAME"
}

bootstrap_backfill() {
  header "Bootstrap backfill"
  resolve_ecr_repo "${APP_NAME}-backfill"
  retag_latest_as_bootstrap "${APP_NAME}-backfill"
  register_backfill_image "$BOOTSTRAP_TAG"
  apply_ecr_hygiene "${APP_NAME}-backfill"
}

run_bootstrap() {
  local target="${TARGET:-both}"
  case "$target" in
    app) bootstrap_app ;;
    backfill) bootstrap_backfill ;;
    both)
      bootstrap_app
      bootstrap_backfill
      ;;
    *) fail "bootstrap target must be app, backfill, or both (got ${target})" ;;
  esac
}

show_status() {
  local app_image app_status job_image job_rev
  header "Status"
  app_image="$(current_app_image)"
  app_status="$(current_app_status)"
  log "App Runner  ${BOLD}${APP_NAME}${RESET}  status=${GREEN}${app_status}${RESET}"
  info "image: ${app_image}"
  job_rev="$(current_backfill_revision)"
  job_image="$(current_backfill_image)"
  log "Fargate     ${BOLD}${APP_NAME}-backfill:${job_rev}${RESET}"
  info "image: ${job_image}"
  log ""
  print_ecr_tag_table "$APP_NAME"
  log ""
  print_ecr_tag_table "${APP_NAME}-backfill"
}

pick_rollback_tag() {
  local repo="$1"
  local current="$2"
  local lines=()
  local display=()
  local line tag
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    tag="${line%%$'\t'*}"
    if [ "$tag" = "$current" ]; then
      continue
    fi
    lines+=("$tag")
    display+=("$line")
  done <<EOF
$(list_ecr_tags "$repo" | python3 -c 'import sys; print("".join(reversed(sys.stdin.readlines())), end="")')
EOF
  if [ "${#lines[@]}" -eq 0 ]; then
    fail "no other tags in ${repo} to roll back to"
  fi
  prompt_choice "Roll back ${repo} (currently ${current})?" "${display[@]}"
  ROLLBACK_TAG="${PROMPT_RESULT%%$'\t'*}"
}

run_rollback() {
  local target="${TARGET:-}"
  local repo current
  case "$target" in
    app)
      repo="$APP_NAME"
      current="$(tag_from_ref "$(current_app_image)")"
      ;;
    backfill)
      repo="${APP_NAME}-backfill"
      current="$(tag_from_ref "$(current_backfill_image)")"
      ;;
    *) fail "rollback requires app or backfill" ;;
  esac
  resolve_ecr_repo "$repo"
  header "Rollback ${target}"
  info "currently: ${current}"
  if [ -z "$VERSION_TO" ]; then
    is_tty || fail "pass --to VERSION (stdin is not a TTY)"
    pick_rollback_tag "$repo" "$current"
    VERSION_TO="$ROLLBACK_TAG"
  fi
  ecr_tag_exists "$repo" "$VERSION_TO" \
    || fail "${repo}:${VERSION_TO} not in ECR"
  info "target:    ${VERSION_TO}"
  confirm "Pin ${target} to ${VERSION_TO}?"
  case "$target" in
    app) pin_app_image "$VERSION_TO" ;;
    backfill) register_backfill_image "$VERSION_TO" ;;
  esac
}

update_app() {
  local ecr repo
  repo="$APP_NAME"
  ecr="$(ecr_uri "$repo")"
  ensure_version
  resolve_ecr_repo "$repo"
  resolve_app_runner
  warn_if_changelog_changed

  header "App (SPA + API)"
  info "version:  ${VERSION}"
  info "build:    podman build --platform $PLATFORM -f backend/Dockerfile"
  info "push:     ${ecr}:${VERSION}"
  info "deploy:   App Runner update-service"
  info "service:  $SERVICE_ARN"
  confirm "Build (if needed), push, and update App Runner to ${VERSION}?"

  if ecr_tag_exists "$repo" "$VERSION"; then
    ok "ECR already has ${CYAN}${repo}:${VERSION}${RESET} — skipping build"
  else
    need_cmd podman
    podman info >/dev/null 2>&1 || fail "Podman is not running"
    ecr_login
    log ""
    log "${BLUE}→${RESET} Building JVM image ${DIM}(npm + Maven; several minutes)${RESET}…"
    podman build --platform "$PLATFORM" \
      --build-arg "DEPLOY_VERSION=${VERSION}" \
      -f "$ROOT/backend/Dockerfile" \
      -t "${APP_NAME}:jvm" \
      "$ROOT"
    podman tag "${APP_NAME}:jvm" "${ecr}:${VERSION}"
    log "${BLUE}→${RESET} Pushing ${CYAN}${ecr}:${VERSION}${RESET}…"
    podman push "${ecr}:${VERSION}"
    ok "image pushed"
  fi

  pin_app_image "$VERSION"
  maybe_tag_git app
}

run_backfill_task() {
  local mode="$1"
  local sg_id subnet_ids task_arn
  sg_id="$(aws ec2 describe-security-groups --region "$AWS_REGION" \
    --filters "Name=group-name,Values=${APP_NAME}-backfill" \
    --query 'SecurityGroups[0].GroupId' --output text)"
  require_value "security group ${APP_NAME}-backfill" "$sg_id"

  subnet_ids="$(aws rds describe-db-instances --region "$AWS_REGION" \
    --db-instance-identifier "${APP_NAME}-pg" \
    --query 'join(`,`, DBInstances[0].DBSubnetGroup.Subnets[*].SubnetIdentifier)' \
    --output text)"
  require_value "RDS subnets for ${APP_NAME}-pg" "$subnet_ids"

  log "${BLUE}→${RESET} Starting Fargate task ${MAGENTA}($mode)${RESET}…"
  if [ "$mode" = "full" ]; then
    task_arn="$(aws ecs run-task --region "$AWS_REGION" \
      --cluster "$APP_NAME" \
      --task-definition "${APP_NAME}-backfill" \
      --launch-type FARGATE \
      --network-configuration "awsvpcConfiguration={subnets=[${subnet_ids}],securityGroups=[${sg_id}],assignPublicIp=ENABLED}" \
      --overrides '{"containerOverrides":[{"name":"backfill","command":["python","backfill_price_snapshots.py"]}]}' \
      --query 'tasks[0].taskArn' --output text)"
  else
    task_arn="$(aws ecs run-task --region "$AWS_REGION" \
      --cluster "$APP_NAME" \
      --task-definition "${APP_NAME}-backfill" \
      --launch-type FARGATE \
      --network-configuration "awsvpcConfiguration={subnets=[${subnet_ids}],securityGroups=[${sg_id}],assignPublicIp=ENABLED}" \
      --query 'tasks[0].taskArn' --output text)"
  fi
  require_value "Fargate task ARN" "$task_arn"
  ok "task ${DIM}${task_arn}${RESET}"
  info "Logs: aws logs tail /ecs/${APP_NAME}-backfill --region $AWS_REGION --since 15m"
}

maybe_run_backfill() {
  local mode="$RUN_TASK"
  if [ -z "$mode" ] || [ "$mode" = "ask" ]; then
    if ! is_tty; then
      mode="none"
    else
      prompt_choice "Run a Fargate task now? (schedule still fires at 18:00 America/Toronto)" \
        "No — wait for tonight" \
        "Yes — nightly catch-up (--days 7)" \
        "Yes — full history (first txn → today)"
      case "$PROMPT_RESULT" in
        No*) mode="none" ;;
        *nightly*) mode="nightly" ;;
        *full*) mode="full" ;;
      esac
    fi
  fi
  case "$mode" in
    none|'') ok "Image pinned. Next scheduled RunTask uses the newest task-definition revision." ;;
    nightly) run_backfill_task nightly ;;
    full)    run_backfill_task full ;;
    *) fail "unknown --run-task value: $mode (use nightly, full, or none)" ;;
  esac
}

update_backfill() {
  local ecr repo
  repo="${APP_NAME}-backfill"
  ecr="$(ecr_uri "$repo")"
  ensure_version
  resolve_ecr_repo "$repo"

  header "Price backfill"
  info "version:  ${VERSION}"
  info "build:    podman build --platform $PLATFORM -f scripts/Dockerfile"
  info "push:     ${ecr}:${VERSION}"
  info "deploy:   new Fargate task-definition revision"
  confirm "Build (if needed), push, and register ${VERSION}?"

  if ecr_tag_exists "$repo" "$VERSION"; then
    ok "ECR already has ${CYAN}${repo}:${VERSION}${RESET} — skipping build"
  else
    need_cmd podman
    podman info >/dev/null 2>&1 || fail "Podman is not running"
    ecr_login
    log ""
    log "${BLUE}→${RESET} Building backfill image…"
    podman build --platform "$PLATFORM" \
      -f "$ROOT/scripts/Dockerfile" \
      -t "${APP_NAME}-backfill" \
      "$ROOT/scripts"
    podman tag "${APP_NAME}-backfill" "${ecr}:${VERSION}"
    log "${BLUE}→${RESET} Pushing ${CYAN}${ecr}:${VERSION}${RESET}…"
    podman push "${ecr}:${VERSION}"
    ok "image pushed"
  fi

  register_backfill_image "$VERSION"
  maybe_tag_git backfill
  maybe_run_backfill
}

run_only() {
  header "Backfill task"
  info "cluster:  $APP_NAME"
  info "task-def: ${APP_NAME}-backfill (newest ACTIVE revision)"
  info "image:    $(current_backfill_image)"
  case "$RUN_TASK" in
    nightly|full) ;;
    *) is_tty && RUN_TASK="ask" || RUN_TASK="nightly" ;;
  esac
  maybe_run_backfill
}

pick_command() {
  prompt_choice "What do you want to do?" \
    "App (SPA + API → App Runner)" \
    "Price backfill (yfinance → Fargate)" \
    "Both" \
    "Run backfill task only (no build)" \
    "Status (what is live)" \
    "Bootstrap (name the current :latest)" \
    "Rollback app" \
    "Rollback backfill" \
    "Quit"
  case "$PROMPT_RESULT" in
    App*) COMMAND="app" ;;
    Price*) COMMAND="backfill" ;;
    Both) COMMAND="both" ;;
    Run*) COMMAND="run" ;;
    Status*) COMMAND="status" ;;
    Bootstrap*) COMMAND="bootstrap"; TARGET="both" ;;
    "Rollback app") COMMAND="rollback"; TARGET="app" ;;
    "Rollback backfill") COMMAND="rollback"; TARGET="backfill" ;;
    Quit) warn "aborted."; exit 0 ;;
  esac
}

COMMAND=""
TARGET=""
VERSION=""
VERSION_TO=""
ALLOW_DIRTY=0
SKIP_GIT_TAG=0
RUN_TASK=""
SERVICE_ARN=""
BOOTSTRAP_TAG=""

while [ $# -gt 0 ]; do
  case "$1" in
    -h|--help)
      usage
      exit 0
      ;;
    --allow-dirty)
      ALLOW_DIRTY=1
      shift
      ;;
    --to)
      [ $# -ge 2 ] || fail "--to needs a version"
      VERSION_TO="$2"
      shift 2
      ;;
    --to=*)
      VERSION_TO="${1#--to=}"
      shift
      ;;
    --run-task)
      RUN_TASK="nightly"
      shift
      ;;
    --run-task=*)
      RUN_TASK="${1#--run-task=}"
      shift
      ;;
    app|backfill|both|run|status|bootstrap|rollback)
      if [ -z "$COMMAND" ]; then
        COMMAND="$1"
      elif [ "$COMMAND" = bootstrap ] || [ "$COMMAND" = rollback ]; then
        TARGET="$1"
      else
        fail "unexpected argument: $1 (try --help)"
      fi
      shift
      ;;
    *)
      fail "unknown argument: $1 (try --help)"
      ;;
  esac
done

need_cmd aws
need_cmd python3

log "${BOLD}${MAGENTA}Investment Tracker${RESET} ${DIM}AWS update${RESET}"
log "${BLUE}→${RESET} Checking AWS identity…"
AWS_ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
require_value "AWS account" "$AWS_ACCOUNT_ID"

ok "account=${BOLD}${AWS_ACCOUNT_ID}${RESET}  region=${BOLD}${AWS_REGION}${RESET}  app=${BOLD}${APP_NAME}${RESET}"

if [ -z "$COMMAND" ]; then
  is_tty || fail "pass app, backfill, both, run, status, bootstrap, or rollback (stdin is not a TTY)"
  pick_command
fi

if [ "$COMMAND" = bootstrap ] && [ -z "$TARGET" ]; then
  TARGET="both"
fi
if [ "$COMMAND" = rollback ] && [ -z "$TARGET" ]; then
  is_tty || fail "rollback requires app or backfill"
  prompt_choice "Rollback which artifact?" "App (App Runner)" "Price backfill (Fargate)"
  case "$PROMPT_RESULT" in
    App*) TARGET="app" ;;
    Price*) TARGET="backfill" ;;
  esac
fi

case "$COMMAND" in
  app)
    update_app
    ;;
  backfill)
    update_backfill
    ;;
  both)
    update_app
    update_backfill
    ;;
  run)
    run_only
    ;;
  status)
    show_status
    ;;
  bootstrap)
    run_bootstrap
    ;;
  rollback)
    run_rollback
    ;;
  *)
    fail "unknown command: $COMMAND"
    ;;
esac

log ""
ok "${BOLD}Done.${RESET}"
