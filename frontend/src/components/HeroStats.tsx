import type { DashboardData } from '../api/types';
import { formatGainLoss, formatMoney, formatPercent } from '../lib/actions';

interface HeroStatsProps {
  dashboard: DashboardData;
}

export default function HeroStats({ dashboard }: HeroStatsProps) {
  const today = dashboard.todaysReturn;
  const allTime = dashboard.allTimeReturn;

  const todayAmount = formatGainLoss(today.amount);
  const todayPct = formatPercent(today.pct);
  const allTimeAmount = formatGainLoss(allTime.amount);
  const allTimePct = formatPercent(allTime.pct);

  return (
    <div className="grid-3" style={{ marginBottom: '1.25rem' }}>
      <div className="card">
        <p className="card-title">Portfolio value</p>
        <p className="card-value">{formatMoney(dashboard.portfolioValue)}</p>
        <p className="card-meta">
          {dashboard.asOfDate ? `Manual prices · ${dashboard.asOfDate}` : 'No prices recorded yet'}
        </p>
      </div>

      <div className="card">
        <p className="card-title">Today's return</p>
        {today.available ? (
          <>
            <p className={`card-value ${todayAmount.className}`}>
              {todayAmount.text}{' '}
              <span style={{ fontSize: '1rem' }}>({todayPct.text})</span>
            </p>
            <p className="card-meta">
              {today.basisDate ? `vs snapshot ${today.basisDate}` : 'vs prior snapshot'}
            </p>
          </>
        ) : (
          <>
            <p className="card-value" style={{ color: 'var(--text-muted)' }}>
              —
            </p>
            <p className="card-meta">Needs a prior portfolio snapshot</p>
          </>
        )}
      </div>

      <div className="card">
        <p className="card-title">All-time return</p>
        {allTime.available ? (
          <>
            <p className={`card-value ${allTimeAmount.className}`}>
              {allTimeAmount.text}{' '}
              <span style={{ fontSize: '1rem' }}>({allTimePct.text})</span>
            </p>
            <p className="card-meta">Incl. realized gains &amp; dividends</p>
          </>
        ) : (
          <>
            <p className="card-value" style={{ color: 'var(--text-muted)' }}>
              —
            </p>
            <p className="card-meta">Enter prices to see return</p>
          </>
        )}
      </div>
    </div>
  );
}
