import type { Holding } from '../api/types';
import { formatGainLoss, formatMoney, formatNumber } from '../lib/actions';

interface HoldingsTableProps {
  holdings: Holding[];
  selectedSecurityId: number | null;
  onSelect: (securityId: number) => void;
}

function latestPriceDate(holdings: Holding[]): string | null {
  return holdings.reduce<string | null>((latest, holding) => {
    if (!holding.priceDate) return latest;
    if (!latest || holding.priceDate > latest) return holding.priceDate;
    return latest;
  }, null);
}

export default function HoldingsTable({
  holdings,
  selectedSecurityId,
  onSelect,
}: HoldingsTableProps) {
  const priceDate = latestPriceDate(holdings);

  return (
    <div className="card">
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Security</th>
              <th style={{ textAlign: 'right' }}>Shares</th>
              <th style={{ textAlign: 'right' }}>ACB / share</th>
              <th style={{ textAlign: 'right' }}>Total ACB</th>
              <th style={{ textAlign: 'right' }}>Price</th>
              <th style={{ textAlign: 'right' }}>Market value</th>
              <th style={{ textAlign: 'right' }}>Unrealized</th>
            </tr>
          </thead>
          <tbody>
            {holdings.map((holding) => {
              const selected = holding.securityId === selectedSecurityId;
              const unrealized = formatGainLoss(holding.unrealizedGainLoss);
              return (
                <tr
                  key={holding.securityId}
                  onClick={() => onSelect(holding.securityId)}
                  style={{
                    cursor: 'pointer',
                    background: selected ? 'var(--bg-subtle)' : undefined,
                  }}
                >
                  <td>
                    <div className="ticker">{holding.ticker}</div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                      {holding.name}
                    </div>
                  </td>
                  <td className="mono" style={{ textAlign: 'right' }}>
                    {formatNumber(holding.shareBalance)}
                  </td>
                  <td className="mono" style={{ textAlign: 'right' }}>
                    {formatMoney(holding.acbPerShare)}
                  </td>
                  <td className="mono" style={{ textAlign: 'right' }}>
                    {formatMoney(holding.totalAcb)}
                  </td>
                  <td className="mono" style={{ textAlign: 'right' }}>
                    {formatMoney(holding.latestPrice)}
                  </td>
                  <td className="mono" style={{ textAlign: 'right' }}>
                    {holding.marketValue !== null ? (
                      formatMoney(holding.marketValue)
                    ) : (
                      <span style={{ color: 'var(--text-muted)' }}>No price</span>
                    )}
                  </td>
                  <td
                    className={`mono ${unrealized.className}`}
                    style={{ textAlign: 'right' }}
                  >
                    {unrealized.text}
                  </td>
                </tr>
              );
            })}

            <tr style={{ opacity: 0.7 }}>
              <td>
                <div className="ticker">Cash</div>
                <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                  Account balances
                </div>
              </td>
              <td className="mono" style={{ textAlign: 'right' }}>—</td>
              <td className="mono" style={{ textAlign: 'right' }}>—</td>
              <td className="mono" style={{ textAlign: 'right' }}>—</td>
              <td className="mono" style={{ textAlign: 'right' }}>—</td>
              <td style={{ textAlign: 'right', color: 'var(--text-muted)' }}>
                Coming soon
              </td>
              <td className="mono" style={{ textAlign: 'right' }}>—</td>
            </tr>
          </tbody>
        </table>
      </div>

      <p className="card-meta">
        {priceDate ? `Prices as of ${priceDate}` : 'No prices recorded yet'}
      </p>
    </div>
  );
}
