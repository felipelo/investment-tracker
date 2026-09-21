#!/usr/bin/env python3
"""
Backfill price_snapshot from historical daily closing prices (yfinance).

Writes one row per (security, trading day):
  price_snapshot(security_id, snapshot_date, price) = unadjusted daily Close

Every run also overlays the latest 1-minute quote onto that session's date so
same-day reruns refresh "now", and the next day's official Close replaces it.

Portfolio value and period returns are computed live from these rows plus the
transaction history, so a dense price_snapshot is what makes the dashboard's
5D / 1M / 6M / 1Y returns work.

Idempotent via upsert on (security_id, snapshot_date).
"""

from __future__ import annotations

import argparse
import logging
import os
import sys
from dataclasses import dataclass
from datetime import date, timedelta
from decimal import Decimal, ROUND_HALF_UP
from zoneinfo import ZoneInfo

import pandas as pd
import psycopg2
import psycopg2.extras
import yfinance as yf

logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
log = logging.getLogger(__name__)

MONEY_SCALE = Decimal("0.0001")  # 4 decimal places, matches NUMERIC(18,4)

TSX_PREFIXES = frozenset({"TSE", "TSX"})
TORONTO = ZoneInfo("America/Toronto")


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


@dataclass(frozen=True)
class Security:
    id: int
    ticker: str
    first_txn: date


def db_connect():
    return psycopg2.connect(
        host=os.environ.get("POSTGRES_HOST", "localhost"),
        port=int(os.environ.get("POSTGRES_PORT", "5432")),
        dbname=os.environ.get("POSTGRES_DB", "investment_tracker"),
        user=os.environ.get("POSTGRES_USER", "investment_tracker"),
        password=os.environ.get("POSTGRES_PASSWORD", "investment_tracker"),
    )


def load_securities(conn, portfolio_id: int | None) -> list[Security]:
    """Held securities with their earliest transaction date (optionally scoped to one portfolio)."""
    if portfolio_id is not None:
        sql = """
            SELECT s.id, s.ticker, MIN(t.txn_date)
            FROM security_transaction t
            JOIN security s ON s.id = t.security_id
            JOIN account a ON a.id = t.account_id
            WHERE a.portfolio_id = %s
            GROUP BY s.id, s.ticker
            ORDER BY s.ticker
        """
        params: tuple = (portfolio_id,)
    else:
        sql = """
            SELECT s.id, s.ticker, MIN(t.txn_date)
            FROM security_transaction t
            JOIN security s ON s.id = t.security_id
            GROUP BY s.id, s.ticker
            ORDER BY s.ticker
        """
        params = ()

    with conn.cursor() as cur:
        cur.execute(sql, params)
        rows = cur.fetchall()
    return [Security(id=r[0], ticker=r[1], first_txn=r[2]) for r in rows]


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


def fetch_latest_quote(yahoo_symbol: str) -> tuple[date, Decimal] | None:
    """Latest 1-minute Close; snapshot_date is the bar in America/Toronto."""
    try:
        hist = yf.Ticker(yahoo_symbol).history(
            period="5d",
            interval="1m",
            auto_adjust=False,
        )
    except Exception as exc:
        log.warning("yfinance latest quote failed for %s: %s", yahoo_symbol, exc)
        return None

    if hist is None or hist.empty or "Close" not in hist.columns:
        return None

    closes = hist["Close"].dropna()
    if closes.empty:
        return None

    ts = closes.index[-1]
    if getattr(ts, "tzinfo", None) is not None:
        ts = ts.tz_convert(TORONTO)
    snapshot_date = ts.date()
    price = Decimal(str(closes.iloc[-1]))
    if price <= 0:
        return None
    return snapshot_date, price


def overlay_latest(closes: pd.Series | None, snapshot_date: date, price: Decimal) -> pd.Series:
    """Replace (or insert) the live quote on snapshot_date so upsert overwrites that row."""
    ts = pd.Timestamp(snapshot_date).normalize()
    value = float(price)
    if closes is None or closes.empty:
        return pd.Series([value], index=pd.DatetimeIndex([ts]))
    closes = closes.copy()
    if getattr(closes.index, "tz", None) is not None:
        ts = ts.tz_localize(closes.index.tz)
    closes.loc[ts] = value
    return closes.sort_index()


def upsert_prices(conn, security_id: int, closes: pd.Series, dry_run: bool) -> int:
    rows = []
    for ts, val in closes.items():
        if val is None or val <= 0:
            continue
        rows.append((security_id, ts.date(), scale_money(Decimal(str(val)))))

    if dry_run or not rows:
        return len(rows)

    sql = """
        INSERT INTO price_snapshot (security_id, snapshot_date, price)
        VALUES %s
        ON CONFLICT ON CONSTRAINT uq_price_snapshot_security_date
        DO UPDATE SET price = EXCLUDED.price
    """
    with conn.cursor() as cur:
        psycopg2.extras.execute_values(cur, sql, rows, template="(%s, %s, %s)")
    conn.commit()
    return len(rows)


def process_security(conn, security: Security, days: int | None, dry_run: bool) -> None:
    today = date.today()
    start_date = security.first_txn
    if days is not None:
        start_date = max(start_date, today - timedelta(days=days))
    if start_date > today:
        log.info("%s: start date in the future, skipping", security.ticker)
        return

    yahoo = to_yahoo(security.ticker)
    closes = fetch_closes(yahoo, start_date, today)
    latest = fetch_latest_quote(yahoo)
    if latest is not None:
        closes = overlay_latest(closes, latest[0], latest[1])
    elif closes is None:
        print(f"  WARNING: no price data for {security.ticker} ({yahoo}); skipping")
        log.warning("No price data for %s (%s)", security.ticker, yahoo)
        return
    else:
        log.warning("No latest quote for %s (%s); writing daily closes only", security.ticker, yahoo)

    count = upsert_prices(conn, security.id, closes, dry_run)
    action = "would upsert" if dry_run else "upserted"
    live_note = ""
    if latest is not None:
        live_note = (
            f" | live {latest[0]} ${float(latest[1]):,.2f} "
            f"(overrides same-day snapshot)"
        )
    print(
        f"  {security.ticker} ({yahoo}): {action} {count} rows | "
        f"{closes.index[0].date()} ${closes.iloc[0]:,.2f} -> "
        f"{closes.index[-1].date()} ${closes.iloc[-1]:,.2f}"
        f"{live_note}"
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Backfill price_snapshot from historical yfinance closes, "
            "overlaying the latest quote on the current session"
        ),
    )
    parser.add_argument(
        "--portfolio-id",
        type=int,
        default=None,
        help="Only backfill securities held in this portfolio (default: all held securities)",
    )
    parser.add_argument(
        "--days",
        type=int,
        default=None,
        help="Cap lookback to N days (default: back to first transaction)",
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
        securities = load_securities(conn, args.portfolio_id)
        if not securities:
            log.info("No held securities found")
            return 0

        log.info("Backfilling %d securities", len(securities))
        for security in securities:
            process_security(conn, security, args.days, args.dry_run)
    finally:
        conn.close()

    return 0


if __name__ == "__main__":
    sys.exit(main())
