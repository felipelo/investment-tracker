import { useAccounts, useSecurities, useTransactions } from '../api/hooks';
import { actionMeta, formatMoney, formatNumber, formatPricePerShare } from '../lib/actions';
import type { SecurityTransaction } from '../api/types';

interface RecentTransactionsProps {
  portfolioId: number | null;
  selectedId?: number | null;
  onSelect?: (tx: SecurityTransaction) => void;
}

export default function RecentTransactions({
  portfolioId,
  selectedId,
  onSelect,
}: RecentTransactionsProps) {
  const transactions = useTransactions(portfolioId === null ? null : { portfolioId });
  const securities = useSecurities();
  const accounts = useAccounts(portfolioId);

  const securityById = new Map(
    (securities.data ?? []).map((s) => [s.id, s.ticker]),
  );
  const accountById = new Map(
    (accounts.data ?? []).map((a) => [a.id, a.label]),
  );

  return (
    <div className="card" style={{ marginTop: '1.5rem' }}>
      <p className="card-title">Recent transactions</p>

      {transactions.isPending && (
        <p style={{ color: 'var(--text-muted)', margin: 0 }}>Loading…</p>
      )}

      {transactions.isError && (
        <p className="negative" style={{ margin: 0 }}>
          Could not load transactions.
        </p>
      )}

      {transactions.isSuccess && transactions.data.length === 0 && (
        <p style={{ color: 'var(--text-muted)', margin: 0 }}>
          No transactions recorded yet.
        </p>
      )}

      {transactions.isSuccess && transactions.data.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>Security</th>
                <th>Action</th>
                <th>Account</th>
                <th style={{ textAlign: 'right' }}>Shares</th>
                <th style={{ textAlign: 'right' }}>Price</th>
                <th style={{ textAlign: 'right' }}>Cash</th>
              </tr>
            </thead>
            <tbody>
              {transactions.data.map((tx) => {
                const meta = actionMeta(tx.action);
                const selected = tx.id === selectedId;
                return (
                  <tr
                    key={tx.id}
                    onClick={() => onSelect?.(tx)}
                    style={{
                      cursor: onSelect ? 'pointer' : undefined,
                      background: selected ? 'var(--bg-subtle)' : undefined,
                    }}
                  >
                    <td className="mono">{tx.date}</td>
                    <td className="ticker">
                      {securityById.get(tx.securityId) ?? `#${tx.securityId}`}
                    </td>
                    <td>
                      <span className={`tag ${meta.tagClass}`}>{meta.label}</span>
                    </td>
                    <td>
                      {tx.accountId !== null
                        ? accountById.get(tx.accountId) ?? `#${tx.accountId}`
                        : '—'}
                    </td>
                    <td className="mono" style={{ textAlign: 'right' }}>
                      {formatNumber(tx.shares)}
                    </td>
                    <td className="mono" style={{ textAlign: 'right' }}>
                      {formatPricePerShare(tx.pricePerShare)}
                    </td>
                    <td className="mono" style={{ textAlign: 'right' }}>
                      {formatMoney(tx.cashAmount)}
                    </td>
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
