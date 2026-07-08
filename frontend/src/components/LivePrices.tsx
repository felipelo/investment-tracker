import type { Holding, Quote } from '../api/types';
import { tickerToMarketSymbol } from '../lib/symbols';
import { formatMoney } from '../lib/actions';

interface LivePricesProps {
  holdings: Holding[];
  quotes: Quote[] | undefined;
  isLoading: boolean;
  isFetching: boolean;
  isError: boolean;
  onReload: () => void;
}

function formatPrice(quote: Quote | undefined): string {
  if (!quote || !quote.available || quote.price === null) return 'Unavailable';
  const num = Number(quote.price);
  if (Number.isNaN(num)) return quote.price;
  const formatted = num.toLocaleString('en-CA', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
  return quote.currency ? `${formatted} ${quote.currency}` : formatted;
}

export default function LivePrices({
  holdings,
  quotes,
  isLoading,
  isFetching,
  isError,
  onReload,
}: LivePricesProps) {
  const bySymbol = new Map((quotes ?? []).map((q) => [q.symbol, q]));

  let liveTotal = 0;
  let hasAnyValue = false;
  let anyUnavailable = false;

  const rows = holdings.map((holding) => {
    const symbol = tickerToMarketSymbol(holding.ticker);
    const quote = bySymbol.get(symbol);
    const available = quote?.available && quote.price !== null;

    let value: string | null = null;
    if (available && quote) {
      const v = Number(holding.shareBalance) * Number(quote.price);
      if (!Number.isNaN(v)) {
        value = String(v);
        liveTotal += v;
        hasAnyValue = true;
      }
    } else {
      anyUnavailable = true;
    }

    return { holding, symbol, quote, value };
  });

  const asOf = (quotes ?? []).find((q) => q.available)?.asOf ?? null;

  return (
    <div className="card" style={{ marginBottom: '1.25rem' }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'baseline',
          justifyContent: 'space-between',
          gap: '1rem',
          flexWrap: 'wrap',
        }}
      >
        <div>
          <p className="card-title" style={{ marginBottom: '0.25rem' }}>
            Live prices
          </p>
          <p style={{ margin: 0, fontSize: '0.75rem', color: 'var(--text-muted)' }}>
            Delayed market data · display only
            {asOf ? ` · as of ${new Date(asOf).toLocaleTimeString('en-CA')}` : ''}
          </p>
        </div>
        <button type="button" className="btn btn-ghost" onClick={onReload} disabled={isFetching}>
          {isFetching ? 'Reloading…' : 'Reload prices'}
        </button>
      </div>

      {isError && (
        <div className="banner banner-warn" style={{ marginTop: '1rem' }}>
          Could not fetch live prices. Try reloading.
        </div>
      )}

      {isLoading ? (
        <p style={{ color: 'var(--text-muted)', margin: '1rem 0 0' }}>Loading live prices…</p>
      ) : (
        <div className="table-wrap" style={{ marginTop: '0.75rem' }}>
          <table>
            <thead>
              <tr>
                <th>Security</th>
                <th style={{ textAlign: 'right' }}>Live price</th>
                <th style={{ textAlign: 'right' }}>Live value</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(({ holding, symbol, quote, value }) => {
                const available = quote?.available && quote.price !== null;
                return (
                  <tr key={holding.securityId}>
                    <td>
                      <div className="ticker">{holding.ticker}</div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                        {holding.name} · {symbol}
                      </div>
                    </td>
                    <td
                      style={{
                        textAlign: 'right',
                        color: available ? undefined : 'var(--text-faint)',
                      }}
                    >
                      {formatPrice(quote)}
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      {value !== null ? formatMoney(value) : '—'}
                    </td>
                  </tr>
                );
              })}
            </tbody>
            {hasAnyValue && (
              <tfoot>
                <tr>
                  <td style={{ fontWeight: 600 }}>
                    Live total{anyUnavailable ? ' (available only)' : ''}
                  </td>
                  <td />
                  <td style={{ textAlign: 'right', fontWeight: 600 }}>
                    {formatMoney(String(liveTotal))}
                  </td>
                </tr>
              </tfoot>
            )}
          </table>
        </div>
      )}
    </div>
  );
}
