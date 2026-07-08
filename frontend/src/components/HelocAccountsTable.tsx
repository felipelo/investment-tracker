import type { HelocAccountSummary } from '../api/types';
import { formatMoney } from '../lib/actions';

function statusTagClass(status: string): string {
  switch (status) {
    case 'Fully traced':
    case 'Mostly traced':
      return 'tag-sage';
    case 'Partially traced':
      return 'tag-butter';
    default:
      return 'tag-peach';
  }
}

function formatRate(rate: string | null): string {
  if (rate === null || rate === '') return '—';
  const num = Number(rate);
  return Number.isNaN(num) ? rate : `${num}%`;
}

function formatPct(pct: string): string {
  const num = Number(pct);
  return Number.isNaN(num) ? pct : `${num.toFixed(1)}%`;
}

export default function HelocAccountsTable({ accounts }: { accounts: HelocAccountSummary[] }) {
  return (
    <div className="card">
      <p className="card-title">HELOC accounts</p>
      <div className="table-wrap">
        <table>
          <thead>
            <tr>
              <th>Account</th>
              <th>Balance</th>
              <th>Limit</th>
              <th>Rate</th>
              <th>Traced %</th>
              <th>Status</th>
            </tr>
          </thead>
          <tbody>
            {accounts.map((account) => (
              <tr key={account.id}>
                <td>{account.label}</td>
                <td className="mono">{formatMoney(account.balance)}</td>
                <td className="mono">{formatMoney(account.creditLimit)}</td>
                <td className="mono">{formatRate(account.interestRate)}</td>
                <td className="mono">{formatPct(account.tracedPct)}</td>
                <td>
                  <span className={`tag ${statusTagClass(account.status)}`}>{account.status}</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
