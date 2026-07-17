import { useRef, useState } from 'react';
import type { DividendSummary } from '../api/types';
import { formatMoney } from '../lib/actions';

interface DividendsChartProps {
  summary: DividendSummary;
  year: number;
  availableYears: number[];
  onYearChange: (year: number) => void;
}

const MONTH_LABELS = ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'];

// Deep-pastel chart series order, matching AllocationDonut.
// ponytail: only 6 colors, so a 7th+ dividend payer reuses a color; add more --*-deep tokens if that becomes common.
const SERIES_COLORS = [
  'var(--sage-deep)',
  'var(--lavender-deep)',
  'var(--peach-deep)',
  'var(--sky-deep)',
  'var(--butter-deep)',
  'var(--blush-deep)',
];

const VIEW_WIDTH = 560;
const VIEW_HEIGHT = 160;
const BASELINE_Y = 130;
const MAX_BAR_HEIGHT = 100;
const BAR_WIDTH = 26;
const PAD_X = 20;
const MONTH_LABEL_Y = 154;
const BAR_LABEL_FONT_SIZE = 10;
const MONTH_LABEL_FONT_SIZE = 10;

const barLabelFormatter = new Intl.NumberFormat('en-CA', {
  style: 'currency',
  currency: 'CAD',
  maximumFractionDigits: 0,
});

interface HoldingLegend {
  securityId: number;
  ticker: string;
}

// Distinct dividend payers for the year, ordered by total paid desc, so color
// assignment stays stable and the biggest payer takes the first palette color.
function orderedHoldings(breakdown: DividendSummary['breakdown']): HoldingLegend[] {
  const totals = new Map<number, { ticker: string; total: number }>();
  for (const month of breakdown) {
    for (const slice of month) {
      const existing = totals.get(slice.securityId);
      const amount = Number(slice.amount);
      if (existing) {
        existing.total += amount;
      } else {
        totals.set(slice.securityId, { ticker: slice.ticker, total: amount });
      }
    }
  }
  return [...totals.entries()]
    .sort((a, b) => b[1].total - a[1].total)
    .map(([securityId, value]) => ({ securityId, ticker: value.ticker }));
}

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

  const holdings = orderedHoldings(summary.breakdown);
  const holdingColor = new Map<number, string>(
    holdings.map((holding, index) => [holding.securityId, SERIES_COLORS[index % SERIES_COLORS.length]]),
  );

  const chartRef = useRef<HTMLDivElement>(null);
  const [tooltip, setTooltip] = useState<{ label: string; left: number; top: number } | null>(null);

  function showTooltip(event: React.MouseEvent, label: string) {
    const bounds = chartRef.current?.getBoundingClientRect();
    if (!bounds) return;
    setTooltip({ label, left: event.clientX - bounds.left, top: event.clientY - bounds.top });
  }

  const linePoints = cumulative
    .map((value, index) => {
      const x = barCenterX(index);
      const y = maxCumulative > 0 ? BASELINE_Y - (value / maxCumulative) * MAX_BAR_HEIGHT : BASELINE_Y;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(' ');

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

      <div ref={chartRef} style={{ position: 'relative' }}>
      <svg
        viewBox={`0 0 ${VIEW_WIDTH} ${VIEW_HEIGHT}`}
        width="100%"
        style={{ display: 'block', height: 'auto', maxWidth: VIEW_WIDTH }}
        aria-label="Monthly dividends"
      >
        {months.map((value, index) => {
          const height = maxMonth > 0 ? (value / maxMonth) * MAX_BAR_HEIGHT : 0;
          const x = barCenterX(index) - BAR_WIDTH / 2;
          const isFuture = isCurrentYear && index > currentMonthIndex;
          const isCurrent = isCurrentYear && index === currentMonthIndex;
          const barTop = BASELINE_Y - height;
          // Slices arrive sorted by amount desc, so stacking from the baseline up
          // puts the biggest payer at the bottom and the smallest on top.
          let segmentBottom = BASELINE_Y;
          return (
            <g key={index}>
              {summary.breakdown[index]?.map((slice) => {
                const amount = Number(slice.amount);
                const segmentHeight = maxMonth > 0 ? (amount / maxMonth) * MAX_BAR_HEIGHT : 0;
                const segmentTop = segmentBottom - segmentHeight;
                segmentBottom = segmentTop;
                const code = slice.ticker.includes(':') ? slice.ticker.split(':').pop()! : slice.ticker;
                const label = `${code} — ${formatMoney(slice.amount)}`;
                return (
                  <rect
                    key={slice.securityId}
                    x={x}
                    y={segmentTop}
                    width={BAR_WIDTH}
                    height={segmentHeight}
                    fill={holdingColor.get(slice.securityId) ?? 'var(--sage)'}
                    style={{ cursor: 'pointer' }}
                    onMouseEnter={(e) => showTooltip(e, label)}
                    onMouseMove={(e) => showTooltip(e, label)}
                    onMouseLeave={() => setTooltip(null)}
                  />
                );
              })}
              {value > 0 && !isFuture && (
                <text
                  x={barCenterX(index)}
                  y={barTop - 5}
                  fontSize={BAR_LABEL_FONT_SIZE}
                  fill={isCurrent ? 'var(--text)' : 'var(--text-muted)'}
                  fontWeight={isCurrent ? 600 : undefined}
                  textAnchor="middle"
                  fontFamily="DM Sans"
                >
                  {barLabelFormatter.format(value)}
                </text>
              )}
            </g>
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
            y={MONTH_LABEL_Y}
            fontSize={MONTH_LABEL_FONT_SIZE}
            fill="var(--text-muted)"
            textAnchor="middle"
            fontFamily="DM Sans"
          >
            {label}
          </text>
        ))}
      </svg>
        {tooltip && (
          <div
            style={{
              position: 'absolute',
              left: tooltip.left,
              top: tooltip.top,
              transform: 'translate(-50%, -130%)',
              pointerEvents: 'none',
              whiteSpace: 'nowrap',
              background: 'var(--text)',
              color: 'var(--bg)',
              fontSize: '0.75rem',
              padding: '0.25rem 0.5rem',
              borderRadius: '6px',
              zIndex: 1,
            }}
          >
            {tooltip.label}
          </div>
        )}
      </div>

      <div
        style={{
          display: 'flex',
          gap: '1rem 1.5rem',
          marginTop: '0.5rem',
          fontSize: '0.75rem',
          color: 'var(--text-muted)',
          flexWrap: 'wrap',
          alignItems: 'center',
        }}
      >
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
