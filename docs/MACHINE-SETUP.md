# Machine setup (macOS)

Everything needed to build and run this repo on a fresh Mac. Written for Apple Silicon; on Intel swap
the GraalVM download for the `x64` build.

Verified against macOS 26.5, GraalVM CE 25.0.2, Node v24.4.1, Python 3.14.4, Docker 29.4.

---

## 1. Prerequisites

```bash
xcode-select --install                                   # git + toolchain
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"
```

Budget ~10 GB of free disk: the GraalVM toolchain, the native binary, and container images are large.

---

## 2. Java / GraalVM 25

The backend targets Java 25, and native images require **GraalVM 25** specifically. Maven itself is
**not** needed — `backend/mvnw` is committed and downloads its own Maven.

```bash
mkdir -p ~/.local/graalvm && cd ~/.local/graalvm
curl -sSL -O https://github.com/graalvm/graalvm-ce-builds/releases/download/jdk-25.0.2/graalvm-community-jdk-25.0.2_macos-aarch64_bin.tar.gz
tar xzf graalvm-community-jdk-25.0.2_macos-aarch64_bin.tar.gz
```

Add to `~/.zshrc`:

```bash
export JAVA_HOME=~/.local/graalvm/graalvm-community-openjdk-25.0.2+10.1/Contents/Home
export PATH="$JAVA_HOME/bin:$PATH"
```

The tarball is used instead of `brew install --cask graalvm-jdk@25` because the cask installs into
`/Library/Java/JavaVirtualMachines` (needs `sudo`) and ships Oracle GraalVM under the GFTC license,
while the images we publish are built with GraalVM Community.

---

## 3. Docker

Install Docker Desktop (or colima), start it, and give the VM **at least 8 GB of memory** —
`Dockerfile.native` peaks around 7 GB while compiling.

```bash
brew install --cask docker
```

Container builds pull base images from `ghcr.io`, `gcr.io`, and Docker Hub. If your network blocks
those registries, the builds hang on the pull; build natively on the host instead (section 6).

---

## 4. Node 24 (frontend)

```bash
brew install node@24
brew link --overwrite node@24
cd frontend && npm ci
```

---

## 5. Python 3 (scripts)

Only needed for the price backfill helper in [`scripts/`](../scripts).

```bash
brew install python@3.14
cd scripts
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
```

---

## 6. Environment variables

Secrets go through the environment only — never into `application.yml`.

The default configuration is the deployable one: it has no host and no credential fallbacks, so a
missing variable fails startup instead of quietly connecting somewhere unintended. The `local`
profile supplies laptop defaults for all of them, which is why section 7 needs none of this.

| Variable | Needed for | Default | Default under `local` |
|---|---|---|---|
| `POSTGRES_HOST` | database connection | none — required | `localhost` |
| `POSTGRES_PORT` | database connection | `5432` | `5432` |
| `POSTGRES_DB` | database connection | `investment_tracker` | `investment_tracker` |
| `POSTGRES_USER` | database connection | none — required | `investment_tracker` |
| `POSTGRES_PASSWORD` | database connection | none — required | `investment_tracker` |
| `APP_AUTH_USERNAME` | HTTP Basic login | none — required | auth disabled |
| `APP_AUTH_PASSWORD_HASH` | HTTP Basic login (bcrypt digest, never plaintext) | none — required | auth disabled |
| `ALPHAVANTAGE_API_KEY` | live quotes (`/api/v1/quotes`) | empty, quotes disabled | same |
| `TZ` | the date `LocalDate.now()` reports | the container's zone (UTC) | the host's zone |

Every endpoint except `/actuator/health` requires the single `APP_AUTH_USERNAME` credential. Generate
its bcrypt hash without installing anything:

```bash
docker run --rm httpd:alpine htpasswd -nbBC 10 "" 'your-password' | cut -d: -f2
```

`TZ` matters because the dashboard's "today" and the default tax year come from the JVM's zone. Both
images set `TZ=America/Toronto`; override it if you track a different jurisdiction.

---

## 7. Run it

```bash
cd backend
docker compose up -d postgres                                   # Postgres 17 on :5432

# backend on the JVM
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# or backend as a native binary (~2.5 min to compile)
./mvnw -Pnative native:compile -DskipTests
SPRING_PROFILES_ACTIVE=local ./target/investment-tracker

cd ../frontend && npm run dev                                   # Vite dev server
```

The `local` profile turns authentication off, so the dev flow needs no credentials. Containers run
without that profile and therefore always require them.

To run the whole thing the way it deploys — one container serving both the SPA and the API on
`:8080`, no Vite proxy — build from the repository root:

```bash
docker build -f backend/Dockerfile -t investment-tracker:jvm .
```

The static mock pages in [`mock/`](../mock) are plain HTML — open them directly in a browser.

API docs live at `http://localhost:8080/swagger-ui.html` once the backend is up.

---

## 8. Verify your setup

```bash
java -version          # openjdk 25.0.2 ... GraalVM CE 25.0.2
native-image --version # native-image 25.0.2 ... GraalVM CE
docker info            # Server Version + Total Memory >= 8 GiB
node -v                # v24.x
python3 -V             # Python 3.x
cd backend && ./mvnw verify   # 113 tests, BUILD SUCCESS
```

If `./mvnw verify` passes you have a working build environment; if `native-image --version` also
works, you can produce the native image.
