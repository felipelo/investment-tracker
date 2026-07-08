// Maps an app-stored ticker to an Alpha Vantage symbol.
//
// Stored tickers carry an exchange prefix (e.g. "TSE:ENB"), while Alpha Vantage
// uses an exchange suffix (".TRT" for the TSX). US-style bare tickers ("GOOG")
// and any unrecognized prefix pass through unchanged. Grounded in the seeded
// tickers TSE:XEI, TSE:BANK, TSE:ENB (004-seed-lookups.yaml).

const TSX_PREFIXES = new Set(['TSE', 'TSX']);

export function tickerToMarketSymbol(ticker: string): string {
  const trimmed = ticker.trim().toUpperCase();
  const colon = trimmed.indexOf(':');
  if (colon === -1) {
    return trimmed;
  }

  const exchange = trimmed.slice(0, colon);
  const symbol = trimmed.slice(colon + 1);
  if (TSX_PREFIXES.has(exchange)) {
    return `${symbol}.TRT`;
  }
  return symbol;
}
