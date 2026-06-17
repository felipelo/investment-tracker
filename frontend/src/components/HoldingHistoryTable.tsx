import { useHoldingHistory } from '../api/hooks';
import { actionMeta, formatGainLoss, formatMoney, formatNumber } from '../lib/actions';

interface HoldingHistoryTableProps {
  portfolioId: number | null;
  securityId: number;
  ticker: string;
}

export default function HoldingHistoryTable({
  portfolioId,
  securityId,
  ticker,
}: HoldingHistoryTableProps) {
  const history = useHoldingHistory(portfolioId, securityId);

  return (
    <div className="card" style={{ marginTop: '1.25rem' }}>
      <p className="card-title">{ticker} — transaction history</p>

      {history.isPending && (
        <p style={{ color: 'var(--text-muted)', margin: 0 }}>Loading…</p>
      )}

      {history.isError && (
        <p className="negative" style={{ margin: 0 }}>
          Could not load transaction history.
        </p>
      )}

      {history.isSuccess && history.data.length === 0 && (
        <p style={{ color: 'var(--text-muted)', margin: 0 }}>
          No transactions recorded for this security.
        </p>
      )}

      {history.isSuccess && history.data.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>Action</th>
                <th style={{ textAlign: 'right' }}>Shares</th>
                <th style={{ textAlign: 'right' }}>Price</th>
                <th style={{ textAlign: 'right' }}>ACB change</th>
                <th style={{ textAlign: 'right' }}>Total ACB</th>
                <th style={{ textAlign: 'right' }}>ACB / share</th>
                <th>Notes</th>
              </tr>
            </thead>
            <tbody>
              {history.data.map((row) => {
                const meta = actionMeta(row.action);
                const acbChange = formatGainLoss(row.acbChange);
                return (
                  <tr key={row.transactionId}>
                    <td className="mono">{row.date}</td>
                    <td>
                      <span className={`tag ${meta.tagClass}`}>{meta.label}</span>
                    </td>
                    <td className="mono" style={{ textAlign: 'right' }}>
                      {formatNumber(row.shares)}
                    </td>
                    <td className="mono" style={{ textAlign: 'right' }}>
                      {formatMoney(row.pricePerShare)}
                    </td>
                    <td
                      className={`mono ${acbChange.className}`}
                      style={{ textAlign: 'right' }}
                    >
                      {acbChange.text}
                    </td>
                    <td className="mono" style={{ textAlign: 'right' }}>
                      {formatMoney(row.totalAcb)}
                    </td>
                    <td className="mono" style={{ textAlign: 'right' }}>
                      {formatMoney(row.acbPerShare)}
                    </td>
                    <td>{row.notes ?? ''}</td>
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
