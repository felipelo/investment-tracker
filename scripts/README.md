# Portfolio snapshot backfill

One-time script that fetches historical daily closing prices via [yfinance](https://pypi.org/project/yfinance/), reconstructs each portfolio's whole-portfolio market value for every business day since the first purchase, and upserts into `portfolio_snapshot`. This populates the dashboard period returns (5 Days / One Month / Six Month / One Year).

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
# Preview all portfolios (no writes)
python backfill_portfolio_snapshots.py --dry-run

# Backfill one portfolio, capped at 365 days
python backfill_portfolio_snapshots.py --portfolio-id 1 --days 365

# Backfill all portfolios from first transaction date to today
python backfill_portfolio_snapshots.py
```

### Options

| Flag | Description |
|------|-------------|
| `--portfolio-id <id>` | Process a single portfolio (default: all) |
| `--days <N>` | Cap lookback to N days; start = `max(firstBuyDate, today - N)` |
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

For each portfolio and each business day D:

```
value(D) = Σ shareBalance_as_of(D) × closingPrice_on(D)
```

- Share balances are replayed from `security_transaction` (BUY +, SELL −, SPLIT × ratio).
- Prices are fetched from yfinance using unadjusted `Close` (not `Adj Close`), because splits are already modeled via SPLIT transactions.
- Results are upserted into `portfolio_snapshot` on `(portfolio_id, snapshot_date)`.

## Caveats

- **No FX conversion**: The app sums `price × shares` across currencies directly. The script mirrors this so historical values are comparable to the current dashboard value. Mixed USD/CAD portfolios will have values that are not currency-normalized.
- **Unadjusted closes**: Uses raw `Close`, not split/dividend-adjusted prices, to avoid double-counting with SPLIT transactions and the separate dividends feature.
- **Unresolved tickers**: If yfinance cannot resolve a symbol, that security contributes 0 for all days and a warning is printed. Fix the ticker mapping or add the security manually.
- **TSX tickers**: `TSE:ENB` / `TSX:ENB` → `ENB.TO`; Alpha Vantage-style `ENB.TRT` / `ENB.TRV` → `ENB.TO`. Bare US tickers (e.g. `GOOG`) pass through unchanged.
