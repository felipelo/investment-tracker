import type { PeriodReturn } from '../api/types';
import { formatGainLoss, formatPercent } from '../lib/actions';

interface PeriodReturnsProps {
  periodReturns: PeriodReturn[];
}

export default function PeriodReturns({ periodReturns }: PeriodReturnsProps) {
  return (
    <div className="card" style={{ marginBottom: '1.25rem' }}>
      <p className="card-title">Period returns</p>
      <div className="grid-4">
        {periodReturns.map((period) => {
          const amount = formatGainLoss(period.amount);
          const pct = formatPercent(period.pct);
          return (
            <div
              className="card period-card"
              key={period.label}
              style={{ boxShadow: 'none', border: '1px solid var(--border)' }}
            >
              <div className="period-label">{period.label}</div>
              {period.available ? (
                <>
                  <div className={`period-value ${amount.className}`}>{amount.text}</div>
                  <div className={`period-pct ${pct.className}`}>{pct.text}</div>
                </>
              ) : (
                <>
                  <div className="period-value" style={{ color: 'var(--text-muted)' }}>
                    —
                  </div>
                  <div className="period-pct" style={{ color: 'var(--text-faint)' }}>
                    No snapshot
                  </div>
                </>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
