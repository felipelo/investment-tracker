import { useEffect, useMemo, useState } from 'react';
import { useDividends } from '../api/hooks';
import { formatMoney } from '../lib/actions';
import type { Dividend } from '../api/types';

interface DividendsListProps {
  portfolioId: number;
  selectedId?: number | null;
  onSelect?: (dividend: Dividend) => void;
}

function sumCents(values: string[]): number {
  return values.reduce((acc, value) => acc + Math.round(Number(value) * 100), 0);
}

export default function DividendsList({ portfolioId, selectedId, onSelect }: DividendsListProps) {
  const dividends = useDividends(portfolioId);
  const [securityFilter, setSecurityFilter] = useState('');

  const list = dividends.data;

  const securityOptions = useMemo(() => {
    if (!list) return [];
    const seen = new Map<number, string>();
    for (const dividend of list) {
      if (!seen.has(dividend.securityId)) seen.set(dividend.securityId, dividend.ticker);
    }
    return [...seen.entries()]
      .map(([id, ticker]) => ({ id, ticker }))
      .sort((a, b) => a.ticker.localeCompare(b.ticker));
  }, [list]);

  useEffect(() => {
    if (securityFilter !== '' && !securityOptions.some((s) => String(s.id) === securityFilter)) {
      setSecurityFilter('');
    }
  }, [securityFilter, securityOptions]);

  const filtered = useMemo(() => {
    if (!list) return [];
    if (securityFilter === '') return list;
    return list.filter((dividend) => String(dividend.securityId) === securityFilter);
  }, [list, securityFilter]);

  const totals = useMemo(() => {
    const grossCents = sumCents(filtered.map((dividend) => dividend.grossAmount));
    const netCents = sumCents(filtered.map((dividend) => dividend.netAmount));
    return { gross: grossCents / 100, net: netCents / 100 };
  }, [filtered]);

  const hasDividends = dividends.isSuccess && (list?.length ?? 0) > 0;

  return (
    <div className="card" style={{ marginTop: '1.5rem' }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '0.75rem',
          marginBottom: '1rem',
        }}
      >
        <p className="card-title" style={{ margin: 0 }}>
          All dividends
        </p>
        {hasDividends && (
          <div className="portfolio-switcher">
            <span className="tag tag-sage">Security</span>
            <select
              aria-label="Filter by security"
              value={securityFilter}
              onChange={(event) => setSecurityFilter(event.target.value)}
            >
              <option value="">All securities</option>
              {securityOptions.map((security) => (
                <option key={security.id} value={security.id}>
                  {security.ticker}
                </option>
              ))}
            </select>
          </div>
        )}
      </div>

      {dividends.isPending && (
        <p style={{ color: 'var(--text-muted)', margin: 0 }}>Loading…</p>
      )}

      {dividends.isError && (
        <p className="negative" style={{ margin: 0 }}>
          Could not load dividends.
        </p>
      )}

      {dividends.isSuccess && (list?.length ?? 0) === 0 && (
        <p style={{ color: 'var(--text-muted)', margin: 0 }}>
          No dividends recorded yet.
        </p>
      )}

      {hasDividends && filtered.length === 0 && (
        <p style={{ color: 'var(--text-muted)', margin: 0 }}>
          No dividends for this security.
        </p>
      )}

      {hasDividends && filtered.length > 0 && (
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
              {filtered.map((dividend) => {
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
            <tfoot>
              <tr>
                <td style={{ fontWeight: 600 }}>Total</td>
                <td />
                <td />
                <td className="mono" style={{ textAlign: 'right', fontWeight: 600 }}>
                  {formatMoney(String(totals.gross))}
                </td>
                <td />
                <td className="mono" style={{ textAlign: 'right', fontWeight: 600 }}>
                  {formatMoney(String(totals.net))}
                </td>
                <td />
                <td />
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </div>
  );
}
