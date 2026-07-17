# Price snapshot backfill

One-time script that fetches historical daily closing prices via [yfinance](https://pypi.org/project/yfinance/) and upserts one row per (security, trading day) into `price_snapshot`. Portfolio value and the dashboard period returns (5 Days / One Month / Six Month / One Year) are computed live from these prices plus the transaction history, so a dense `price_snapshot` is what makes those returns work.

## Setup

```bash
cd scripts
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
```

Ensure Postgres is running (e.g. `docker compose -f backend/docker-compose.yml up -d postgres`).

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

For each held security, fetch its unadjusted daily `Close` from its first transaction date to today and upsert each close:

```
price_snapshot(security_id, snapshot_date, price) = Close_on(snapshot_date)
```

- Prices are fetched from yfinance using unadjusted `Close` (not `Adj Close`), because splits are already modeled via SPLIT transactions.
- Results are upserted into `price_snapshot` on `(security_id, snapshot_date)`.
- The dashboard then computes value as of any date D as `Σ shareBalance_as_of(D) × nearestClose_on_or_before(D)`.

## Caveats

- **No FX conversion**: The app sums `price × shares` across currencies directly. Mixed USD/CAD portfolios will have values that are not currency-normalized.
- **Unadjusted closes**: Uses raw `Close`, not split/dividend-adjusted prices, to avoid double-counting with SPLIT transactions and the separate dividends feature.
- **Unresolved tickers**: If yfinance cannot resolve a symbol, that security is skipped with a warning. Fix the ticker mapping or add prices manually.
- **TSX tickers**: `TSE:ENB` / `TSX:ENB` → `ENB.TO`; Alpha Vantage-style `ENB.TRT` / `ENB.TRV` → `ENB.TO`. Bare US tickers (e.g. `GOOG`) pass through unchanged.
