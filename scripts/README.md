# Price snapshot backfill

Fetches historical daily closing prices via [yfinance](https://pypi.org/project/yfinance/) and upserts one row per (security, trading day) into `price_snapshot`. Every run also overlays the latest 1-minute quote onto that session's date: a second run the same day refreshes that row, and the next day's official `Close` replaces it. Portfolio value and the dashboard period returns (5 Days / One Month / Six Month / One Year) are computed live from these prices plus the transaction history, so a dense `price_snapshot` is what makes those returns work.

On a laptop this is a script (venv or Docker). In AWS it is the same image on a nightly EventBridge Scheduler → Fargate task; see [docs/AWS-PRICE-BACKFILL.md](../docs/AWS-PRICE-BACKFILL.md). Build, push, and schedule commands for this image (and the app image) are in [docs/AWS-BUILD-AND-DEPLOY.md](../docs/AWS-BUILD-AND-DEPLOY.md).

To rebuild and roll out an **already created** app or backfill image (no new RDS / IAM / networking):

```bash
# from the repository root
./scripts/update_aws.sh            # interactive
./scripts/update_aws.sh app        # versioned JVM image → App Runner
./scripts/update_aws.sh status
./scripts/update_aws.sh rollback app --to <version>
```

The first run against a service still on `:latest` should be `./scripts/update_aws.sh bootstrap`. See [docs/AWS-BUILD-AND-DEPLOY.md](../docs/AWS-BUILD-AND-DEPLOY.md).

## Setup

```bash
cd scripts
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

Ensure Postgres is running (e.g. `docker compose -f backend/docker-compose.yml up -d postgres`).

## Docker (local)

Same image as AWS. Default command is `--days 7`. Postgres must already be up.

```bash
# from the repository root
docker compose -f backend/docker-compose.yml --profile backfill run --rm price-backfill

# full history (first transaction → today)
docker compose -f backend/docker-compose.yml --profile backfill run --rm price-backfill \
  python backfill_price_snapshots.py
```

## Usage

```bash
# Preview all held securities (no writes)
python backfill_price_snapshots.py --dry-run

# Backfill securities in one portfolio, capped at 365 days
python backfill_price_snapshots.py --portfolio-id 1 --days 365

# Backfill all held securities from first transaction date to today
python backfill_price_snapshots.py
```

### Options

| Flag | Description |
|------|-------------|
| `--portfolio-id <id>` | Only backfill securities held in this portfolio (default: all held securities) |
| `--days <N>` | Cap lookback to N days; start = `max(firstTxnDate, today - N)` |
| `--dry-run` | Compute and print summary; no database writes |

### Environment

Reads Postgres connection from env (defaults match `backend/docker-compose.yml`):

| Variable | Default |
|----------|---------|
| `POSTGRES_HOST` | `localhost` |
| `POSTGRES_PORT` | `5432` |
| `POSTGRES_DB` | `investment_tracker` |
| `POSTGRES_USER` | `investment_tracker` |
| `POSTGRES_PASSWORD` | `investment_tracker` |

## How it works

For each held security, fetch its unadjusted daily `Close` from its first transaction date to today, then overlay the latest 1-minute quote on that bar's date (America/Toronto) and upsert:

```
price_snapshot(security_id, snapshot_date, price) = Close_on(snapshot_date)
# latest session: last 1-minute Close (same unique key)
```

- Past days use unadjusted daily `Close` (not `Adj Close`), because splits are already modeled via SPLIT transactions.
- The latest quote is stamped with the 1-minute bar's date, not blindly `today` — a weekend run writes Friday, so Monday's official Close can replace it.
- Same-day reruns upsert the newer last price onto the same `(security_id, snapshot_date)`.
- The next day's daily history has yesterday's official `Close`; that upsert replaces yesterday's intra-day row.
- The dashboard then computes value as of any date D as `Σ shareBalance_as_of(D) × nearestClose_on_or_before(D)`.

## Caveats

- **No FX conversion**: The app sums `price × shares` across currencies directly. Mixed USD/CAD portfolios will have values that are not currency-normalized.
- **Unadjusted closes**: Uses raw `Close`, not split/dividend-adjusted prices, to avoid double-counting with SPLIT transactions and the separate dividends feature.
- **Yahoo delay**: 1-minute quotes on the free API are typically delayed ~15 minutes. That is as current as this job can get without a paid feed.
- **Unresolved tickers**: If yfinance cannot resolve a symbol, that security is skipped with a warning. Fix the ticker mapping or add prices manually.
- **TSX tickers**: `TSE:ENB` / `TSX:ENB` → `ENB.TO`; Alpha Vantage-style `ENB.TRT` / `ENB.TRV` → `ENB.TO`. Bare US tickers (e.g. `GOOG`) pass through unchanged.
