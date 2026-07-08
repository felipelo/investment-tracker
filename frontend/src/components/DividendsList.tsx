import { useDividends } from '../api/hooks';
import { formatMoney } from '../lib/actions';
import type { Dividend } from '../api/types';

interface DividendsListProps {
  portfolioId: number;
  selectedId?: number | null;
  onSelect?: (dividend: Dividend) => void;
}

export default function DividendsList({ portfolioId, selectedId, onSelect }: DividendsListProps) {
  const dividends = useDividends(portfolioId);

  return (
    <div className="card" style={{ marginTop: '1.5rem' }}>
      <p className="card-title">All dividends</p>

      {dividends.isPending && (
        <p style={{ color: 'var(--text-muted)', margin: 0 }}>Loading…</p>
      )}

      {dividends.isError && (
        <p className="negative" style={{ margin: 0 }}>
          Could not load dividends.
        </p>
      )}

      {dividends.isSuccess && dividends.data.length === 0 && (
        <p style={{ color: 'var(--text-muted)', margin: 0 }}>
          No dividends recorded yet.
        </p>
      )}

      {dividends.isSuccess && dividends.data.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>Security</th>
                <th>Account</th>
                <th style={{ textAlign: 'right' }}>Gross</th>
                <th style={{ textAlign: 'right' }}>Withholding</th>
                <th style={{ textAlign: 'right' }}>Net</th>
                <th>DRIP</th>
                <th>Notes</th>
              </tr>
            </thead>
            <tbody>
              {dividends.data.map((dividend) => {
                const selected = dividend.id === selectedId;
                return (
                  <tr
                    key={dividend.id}
                    onClick={() => onSelect?.(dividend)}
                    style={{
                      cursor: onSelect ? 'pointer' : undefined,
                      background: selected ? 'var(--bg-subtle)' : undefined,
                    }}
                  >
                    <td className="mono">{dividend.paymentDate}</td>
                    <td className="ticker">{dividend.ticker}</td>
                    <td>{dividend.accountLabel ?? '—'}</td>
                    <td className="mono" style={{ textAlign: 'right' }}>
                      {formatMoney(dividend.grossAmount)}
                    </td>
                    <td className="mono" style={{ textAlign: 'right' }}>
                      {formatMoney(dividend.withholdingTax)}
                    </td>
                    <td className="mono" style={{ textAlign: 'right' }}>
                      {formatMoney(dividend.netAmount)}
                    </td>
                    <td>
                      {dividend.drip ? <span className="tag tag-sage">Yes</span> : '—'}
                    </td>
                    <td>{dividend.notes ?? ''}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
