#!/usr/bin/env python3
"""
Backfill portfolio_snapshot from historical daily closing prices (yfinance).

Reconstructs whole-portfolio market value per business day:
  value(D) = sum( shareBalance_as_of(D) * close_on(D) )

Run once; idempotent via upsert on (portfolio_id, snapshot_date).
"""

from __future__ import annotations

import argparse
import logging
import os
import sys
from dataclasses import dataclass
from datetime import date, timedelta
from decimal import Decimal, ROUND_HALF_UP

import pandas as pd
import psycopg2
import psycopg2.extras
import yfinance as yf

logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
log = logging.getLogger(__name__)

MONEY_SCALE = Decimal("0.0001")  # 4 decimal places, matches NUMERIC(18,4)
SHARE_SCALE = Decimal("0.000001")  # 6 decimal places, matches ACB engine

TSX_PREFIXES = frozenset({"TSE", "TSX"})


def to_yahoo(ticker: str) -> str:
    """Map app-stored ticker to Yahoo Finance symbol."""
    t = ticker.strip().upper()
    if ":" in t:
        exchange, symbol = t.split(":", 1)
        if exchange in TSX_PREFIXES:
            return f"{symbol}.TO"
        return symbol
    # Alpha Vantage TSX suffixes stored directly (e.g. XEI.TRT)
    if t.endswith(".TRT") or t.endswith(".TRV"):
        return f"{t.rsplit('.', 1)[0]}.TO"
    return t


def scale_money(value: Decimal) -> Decimal:
    return value.quantize(MONEY_SCALE, rounding=ROUND_HALF_UP)


def scale_shares(value: Decimal) -> Decimal:
    return value.quantize(SHARE_SCALE, rounding=ROUND_HALF_UP)


@dataclass(frozen=True)
class Transaction:
    security_id: int
    ticker: str
    txn_date: date
    action: str
    shares: Decimal | None
    split_ratio: Decimal | None


@dataclass(frozen=True)
class Portfolio:
    id: int
    name: str


def db_connect():
    return psycopg2.connect(
        host=os.environ.get("POSTGRES_HOST", "localhost"),
        port=int(os.environ.get("POSTGRES_PORT", "5432")),
        dbname=os.environ.get("POSTGRES_DB", "investment_tracker"),
        user=os.environ.get("POSTGRES_USER", "investment_tracker"),
        password=os.environ.get("POSTGRES_PASSWORD", "investment_tracker"),
    )


def load_portfolios(conn, portfolio_id: int | None) -> list[Portfolio]:
    with conn.cursor() as cur:
        if portfolio_id is not None:
            cur.execute(
                "SELECT id, name FROM portfolio WHERE id = %s",
                (portfolio_id,),
            )
        else:
            cur.execute("SELECT id, name FROM portfolio ORDER BY id")
        rows = cur.fetchall()
    if portfolio_id is not None and not rows:
        raise SystemExit(f"Portfolio {portfolio_id} not found")
    return [Portfolio(id=r[0], name=r[1]) for r in rows]


def load_transactions(conn, portfolio_id: int) -> list[Transaction]:
    sql = """
        SELECT s.id, s.ticker, t.txn_date, t.action, t.shares, t.split_ratio
        FROM security_transaction t
        JOIN security s ON s.id = t.security_id
        JOIN account a ON a.id = t.account_id
        WHERE a.portfolio_id = %s
        ORDER BY t.txn_date ASC, t.id ASC
    """
    with conn.cursor() as cur:
        cur.execute(sql, (portfolio_id,))
        rows = cur.fetchall()

    return [
        Transaction(
            security_id=r[0],
            ticker=r[1],
            txn_date=r[2],
            action=r[3],
            shares=Decimal(str(r[4])) if r[4] is not None else None,
            split_ratio=Decimal(str(r[5])) if r[5] is not None else None,
        )
        for r in rows
    ]


def business_days(start: date, end: date) -> pd.DatetimeIndex:
    return pd.bdate_range(start=start, end=end)


def fetch_closes(yahoo_symbol: str, start: date, end: date) -> pd.Series | None:
    """Fetch unadjusted daily Close; returns date-indexed Series or None."""
    try:
        df = yf.download(
            yahoo_symbol,
            start=start.isoformat(),
            end=(end + timedelta(days=1)).isoformat(),
            auto_adjust=False,
            progress=False,
        )
    except Exception as exc:
        log.warning("yfinance download failed for %s: %s", yahoo_symbol, exc)
        return None

    if df is None or df.empty:
        return None

    # yfinance may return MultiIndex columns for a single ticker in newer versions
    if isinstance(df.columns, pd.MultiIndex):
        if ("Close", yahoo_symbol) in df.columns:
            closes = df[("Close", yahoo_symbol)]
        elif "Close" in df.columns.get_level_values(0):
            closes = df["Close"].iloc[:, 0]
        else:
            return None
    else:
        if "Close" not in df.columns:
            return None
        closes = df["Close"]

    closes = closes.dropna()
    if closes.empty:
        return None

    closes.index = pd.to_datetime(closes.index).normalize()
    return closes


def share_change(txn: Transaction, prior_balance: Decimal) -> Decimal:
    if txn.action == "BUY":
        if txn.shares is None or txn.shares <= 0:
            return Decimal("0")
        return scale_shares(txn.shares)
    if txn.action == "SELL":
        if txn.shares is None or txn.shares <= 0:
            return Decimal("0")
        return scale_shares(-txn.shares)
    if txn.action == "SPLIT":
        if txn.split_ratio is None or txn.split_ratio <= 0:
            return Decimal("0")
        return scale_shares(prior_balance * (txn.split_ratio - Decimal("1")))
    return Decimal("0")


def build_share_balance_series(
    txns: list[Transaction],
    grid: pd.DatetimeIndex,
) -> pd.Series:
    """Replay BUY/SELL/SPLIT into an as-of share balance on each grid day."""
    if not txns:
        return pd.Series(0.0, index=grid)

    balance = Decimal("0")
    # Map each txn date to cumulative balance after applying that day's txns
    by_date: dict[date, Decimal] = {}
    for txn in txns:
        balance = scale_shares(balance + share_change(txn, balance))
        d = txn.txn_date
        by_date[d] = balance  # last txn on a day wins

    # Build step series: balance holds until next change
    sorted_dates = sorted(by_date.keys())
    values: list[float] = []
    idx = 0
    current = Decimal("0")
    for ts in grid:
        d = ts.date()
        while idx < len(sorted_dates) and sorted_dates[idx] <= d:
            current = by_date[sorted_dates[idx]]
            idx += 1
        values.append(float(current))

    return pd.Series(values, index=grid)


def first_buy_date(txns: list[Transaction]) -> date | None:
    buys = [t.txn_date for t in txns if t.action == "BUY"]
    return min(buys) if buys else None


def compute_portfolio_values(
    transactions: list[Transaction],
    start_date: date,
    end_date: date,
) -> tuple[pd.Series, list[str]]:
    """
    Returns (value_series indexed by business days, list of warning messages).
    """
    warnings: list[str] = []
    grid = business_days(start_date, end_date)

    # Group transactions by security
    by_security: dict[int, list[Transaction]] = {}
    ticker_by_id: dict[int, str] = {}
    for txn in transactions:
        by_security.setdefault(txn.security_id, []).append(txn)
        ticker_by_id[txn.security_id] = txn.ticker

    total = pd.Series(0.0, index=grid)

    for security_id, sec_txns in by_security.items():
        ticker = ticker_by_id[security_id]
        yahoo = to_yahoo(ticker)

        sec_start = max(start_date, min(t.txn_date for t in sec_txns))
        closes = fetch_closes(yahoo, sec_start, end_date)
        if closes is None:
            msg = f"  WARNING: no price data for {ticker} ({yahoo}); treating as 0"
            warnings.append(msg)
            log.warning("No price data for %s (%s)", ticker, yahoo)
            continue

        closes_on_grid = closes.reindex(grid, method="ffill").fillna(0.0)
        balances = build_share_balance_series(sec_txns, grid)
        total = total + balances * closes_on_grid

    return total, warnings


def upsert_snapshots(
    conn,
    portfolio_id: int,
    values: pd.Series,
    dry_run: bool,
) -> int:
    rows = []
    for ts, val in values.items():
        if val <= 0:
            continue
        d = ts.date()
        money = scale_money(Decimal(str(val)))
        rows.append((portfolio_id, d, money))

    if dry_run:
        return len(rows)

    sql = """
        INSERT INTO portfolio_snapshot (portfolio_id, snapshot_date, market_value)
        VALUES %s
        ON CONFLICT ON CONSTRAINT uq_portfolio_snapshot_portfolio_date
        DO UPDATE SET market_value = EXCLUDED.market_value
    """
    with conn.cursor() as cur:
        psycopg2.extras.execute_values(
            cur,
            sql,
            rows,
            template="(%s, %s, %s)",
        )
    conn.commit()
    return len(rows)


def process_portfolio(
    conn,
    portfolio: Portfolio,
    days: int | None,
    dry_run: bool,
) -> None:
    txns = load_transactions(conn, portfolio.id)
    if not txns:
        log.info("Portfolio %d (%s): no transactions, skipping", portfolio.id, portfolio.name)
        return

    first_buy = first_buy_date(txns)
    if first_buy is None:
        log.info(
            "Portfolio %d (%s): no BUY transactions, skipping",
            portfolio.id,
            portfolio.name,
        )
        return

    today = date.today()
    if days is not None:
        start_date = max(first_buy, today - timedelta(days=days))
    else:
        start_date = first_buy

    if start_date > today:
        log.info("Portfolio %d (%s): start date in the future, skipping", portfolio.id, portfolio.name)
        return

    log.info(
        "Portfolio %d (%s): %s .. %s (%d txns)",
        portfolio.id,
        portfolio.name,
        start_date,
        today,
        len(txns),
    )

    values, warnings = compute_portfolio_values(txns, start_date, today)
    for w in warnings:
        print(w)

    non_zero = values[values > 0]
    if non_zero.empty:
        log.warning("Portfolio %d (%s): all computed values are zero", portfolio.id, portfolio.name)
        return

    count = upsert_snapshots(conn, portfolio.id, values, dry_run)

    first_val = non_zero.iloc[0]
    last_val = non_zero.iloc[-1]
    action = "would upsert" if dry_run else "upserted"
    print(
        f"  {action} {count} rows | "
        f"{non_zero.index[0].date()} ${first_val:,.2f} -> "
        f"{non_zero.index[-1].date()} ${last_val:,.2f}"
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Backfill portfolio_snapshot from historical yfinance closes",
    )
    parser.add_argument(
        "--portfolio-id",
        type=int,
        default=None,
        help="Process a single portfolio (default: all)",
    )
    parser.add_argument(
        "--days",
        type=int,
        default=None,
        help="Cap lookback to N days (default: back to first BUY)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Compute and print summary without writing to the database",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.dry_run:
        log.info("Dry run — no database writes")

    try:
        conn = db_connect()
    except psycopg2.Error as exc:
        log.error("Database connection failed: %s", exc)
        return 1

    try:
        portfolios = load_portfolios(conn, args.portfolio_id)
        if not portfolios:
            log.info("No portfolios found")
            return 0

        for portfolio in portfolios:
            process_portfolio(conn, portfolio, args.days, args.dry_run)
    finally:
        conn.close()

    return 0


if __name__ == "__main__":
    sys.exit(main())
