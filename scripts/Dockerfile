# Nightly (and on-demand) price snapshot backfill. yfinance + psycopg2, writes to Postgres.
#
# Build from this directory:
#   docker build --platform linux/amd64 -t investment-tracker-backfill .
# Or from the repository root:
#   docker build --platform linux/amd64 -f scripts/Dockerfile -t investment-tracker-backfill scripts/

FROM python:3.12-slim

WORKDIR /app

# date.today() in the script must match the JVM's "today" (America/Toronto).
ENV TZ=America/Toronto \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

RUN apt-get update \
    && apt-get install -y --no-install-recommends tzdata \
    && ln -snf /usr/share/zoneinfo/America/Toronto /etc/localtime \
    && echo America/Toronto > /etc/timezone \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY backfill_price_snapshots.py .

RUN useradd --create-home --uid 1000 app
USER 1000:1000

# Nightly catch-up. Override the command (drop --days) for a full-history backfill.
CMD ["python", "backfill_price_snapshots.py", "--days", "7"]
