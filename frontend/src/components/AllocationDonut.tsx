import type { AllocationSlice } from '../api/types';

interface AllocationDonutProps {
  allocation: AllocationSlice[];
}

// Deep-pastel chart series order (DESIGN.md section 2.2 / 6.9).
const SERIES_COLORS = [
  'var(--sage-deep)',
  'var(--lavender-deep)',
  'var(--peach-deep)',
  'var(--sky-deep)',
  'var(--butter-deep)',
  'var(--blush-deep)',
];

const RADIUS = 50;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS;

export default function AllocationDonut({ allocation }: AllocationDonutProps) {
  let offset = 0;
  const segments = allocation.map((slice, index) => {
    const pct = Number(slice.pct);
    const length = (pct / 100) * CIRCUMFERENCE;
    const segment = {
      ticker: slice.ticker,
      pct,
      color: SERIES_COLORS[index % SERIES_COLORS.length],
      dashArray: `${length} ${CIRCUMFERENCE - length}`,
      dashOffset: -offset,
    };
    offset += length;
    return segment;
  });

  return (
    <div className="card">
      <p className="card-title">Allocation</p>
      {allocation.length === 0 ? (
        <p style={{ color: 'var(--text-muted)', margin: 0 }}>
          Enter prices to see allocation by holding.
        </p>
      ) : (
        <div style={{ display: 'flex', gap: '2rem', alignItems: 'center', flexWrap: 'wrap' }}>
          <svg viewBox="0 0 120 120" width="160" height="160" aria-label="Allocation chart">
            <circle
              cx="60"
              cy="60"
              r={RADIUS}
              fill="none"
              stroke="var(--bg-subtle)"
              strokeWidth="20"
            />
            {segments.map((segment) => (
              <circle
                key={segment.ticker}
                cx="60"
                cy="60"
                r={RADIUS}
                fill="none"
                stroke={segment.color}
                strokeWidth="20"
                strokeDasharray={segment.dashArray}
                strokeDashoffset={segment.dashOffset}
                transform="rotate(-90 60 60)"
              />
            ))}
          </svg>
          <div className="legend">
            {segments.map((segment) => (
              <div className="legend-item" key={segment.ticker}>
                <span className="legend-dot" style={{ background: segment.color }} />{' '}
                {segment.ticker} · {segment.pct.toFixed(1)}%
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
