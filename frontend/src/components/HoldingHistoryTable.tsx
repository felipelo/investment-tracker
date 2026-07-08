import { useHoldingHistory } from '../api/hooks';
import { actionMeta, formatGainLoss, formatMoney, formatNumber, formatPricePerShare } from '../lib/actions';

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

  const hasSuperficialLoss =
    history.isSuccess && history.data.some((row) => row.superficialLossFlag);

  return (
    <div className="card" style={{ marginTop: '1.25rem' }}>
      <p className="card-title">{ticker} — transaction history</p>

      {hasSuperficialLoss && (
        <div className="banner banner-warn" style={{ marginBottom: '1rem' }}>
          Review: possible superficial loss. A loss-generating sale has a buy of the
          same security within 30 days. Enter a denied loss adjustment on the sale if
          the CRA superficial loss rule applies. This is a record-keeping aid, not tax
          advice.
        </div>
      )}

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
                <th style={{ textAlign: 'right' }}>Denied loss</th>
                <th style={{ textAlign: 'right' }}>Total ACB</th>
                <th style={{ textAlign: 'right' }}>ACB / share</th>
                <th>Flag</th>
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
                      {formatPricePerShare(row.pricePerShare)}
                    </td>
                    <td
                      className={`mono ${acbChange.className}`}
                      style={{ textAlign: 'right' }}
                    >
                      {acbChange.text}
                    </td>
                    <td className="mono" style={{ textAlign: 'right' }}>
                      {formatMoney(row.deniedLossAdjustment)}
                    </td>
                    <td className="mono" style={{ textAlign: 'right' }}>
                      {formatMoney(row.totalAcb)}
                    </td>
                    <td className="mono" style={{ textAlign: 'right' }}>
                      {formatMoney(row.acbPerShare)}
                    </td>
                    <td>
                      {row.superficialLossFlag && (
                        <span className="tag tag-butter" title="Possible superficial loss">
                          Superficial loss?
                        </span>
                      )}
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
