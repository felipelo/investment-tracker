import type { DividendSummary } from '../api/types';
import { formatMoney } from '../lib/actions';

interface DividendsChartProps {
  summary: DividendSummary;
  year: number;
  availableYears: number[];
  onYearChange: (year: number) => void;
}

const MONTH_LABELS = ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'];

const VIEW_WIDTH = 400;
const BASELINE_Y = 120;
const MAX_BAR_HEIGHT = 100;
const BAR_WIDTH = 18;
const PAD_X = 16;

function yearOptions(year: number, availableYears: number[]): number[] {
  const set = new Set<number>(availableYears);
  set.add(year);
  set.add(new Date().getFullYear());
  return [...set].sort((a, b) => b - a);
}

export default function DividendsChart({
  summary,
  year,
  availableYears,
  onYearChange,
}: DividendsChartProps) {
  const months = summary.months.map(Number);
  const cumulative = summary.cumulative.map(Number);
  const maxMonth = Math.max(...months, 0);
  const maxCumulative = Math.max(...cumulative, 0);
  const slot = (VIEW_WIDTH - PAD_X * 2) / 12;

  const isCurrentYear = year === new Date().getFullYear();
  const currentMonthIndex = new Date().getMonth();

  const barCenterX = (index: number) => PAD_X + index * slot + slot / 2;

  const linePoints = cumulative
    .map((value, index) => {
      const x = barCenterX(index);
      const y = maxCumulative > 0 ? BASELINE_Y - (value / maxCumulative) * MAX_BAR_HEIGHT : BASELINE_Y;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(' ');

  function barFill(index: number): string {
    if (isCurrentYear && index > currentMonthIndex) return 'var(--bg-subtle)';
    if (isCurrentYear && index === currentMonthIndex) return 'var(--sage-deep)';
    return 'var(--sage)';
  }

  return (
    <div className="card">
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '1rem',
        }}
      >
        <p className="card-title" style={{ margin: 0 }}>
          Dividends · {year}
        </p>
        <select
          value={year}
          onChange={(e) => onYearChange(Number(e.target.value))}
          style={{
            fontFamily: 'var(--font)',
            fontSize: '0.8125rem',
            padding: '0.25rem 0.5rem',
            border: '1px solid var(--border)',
            borderRadius: '6px',
            background: 'var(--bg)',
          }}
        >
          {yearOptions(year, availableYears).map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
      </div>

      <svg viewBox="0 0 400 140" width="100%" height="140" aria-label="Monthly dividends">
        {months.map((value, index) => {
          const height = maxMonth > 0 ? (value / maxMonth) * MAX_BAR_HEIGHT : 0;
          const x = barCenterX(index) - BAR_WIDTH / 2;
          const isFuture = isCurrentYear && index > currentMonthIndex;
          return (
            <rect
              key={index}
              x={x}
              y={BASELINE_Y - height}
              width={BAR_WIDTH}
              height={height}
              rx="3"
              fill={barFill(index)}
              opacity={isFuture ? 0.6 : 1}
            />
          );
        })}

        {maxCumulative > 0 && (
          <polyline
            points={linePoints}
            fill="none"
            stroke="var(--lavender-deep)"
            strokeWidth="2"
            strokeLinecap="round"
          />
        )}

        {MONTH_LABELS.map((label, index) => (
          <text
            key={index}
            x={barCenterX(index)}
            y="135"
            fontSize="9"
            fill="var(--text-muted)"
            textAnchor="middle"
            fontFamily="DM Sans"
          >
            {label}
          </text>
        ))}
      </svg>

      <div
        style={{
          display: 'flex',
          gap: '1.5rem',
          marginTop: '0.5rem',
          fontSize: '0.75rem',
          color: 'var(--text-muted)',
        }}
      >
        <span>
          <span
            style={{
              display: 'inline-block',
              width: 10,
              height: 10,
              background: 'var(--sage)',
              borderRadius: 2,
              verticalAlign: 'middle',
              marginRight: 4,
            }}
          />{' '}
          Monthly
        </span>
        <span>
          <span
            style={{
              display: 'inline-block',
              width: 16,
              height: 2,
              background: 'var(--lavender-deep)',
              verticalAlign: 'middle',
              marginRight: 4,
            }}
          />{' '}
          Cumulative
        </span>
        <span style={{ marginLeft: 'auto' }}>
          YTD <strong style={{ color: 'var(--text)' }}>{formatMoney(summary.ytdTotal)}</strong>
        </span>
      </div>
    </div>
  );
}
