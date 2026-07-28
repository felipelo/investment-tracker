import { Link } from 'react-router-dom';
import type { CashFlowOutlook as Outlook } from '../api/types';
import { formatGainLoss, formatMoney } from '../lib/actions';

interface CashFlowOutlookProps {
  outlook: Outlook;
}

// Geometry ported from mock/dashboard-cash-flow-outlook.html.
const VIEW_WIDTH = 520;
const VIEW_HEIGHT = 220;
const ZERO_Y = 130;
// Room above and below the zero line. Below is much tighter because the week labels sit there, so
// the two directions get their own budget: one shared scale would let a big outflow bar run past
// the labels and out of the viewBox.
const UP_ROOM = 106;
const DOWN_ROOM = 50;
const FIRST_CENTER_X = 73;
const WEEK_SPACING = 58;
const BAR_WIDTH = 22;
const AXIS_LEFT = 44;
const AXIS_RIGHT = 508;
const WEEK_LABEL_Y = 186;
const CAPTION_Y = 206;

const GRIDLINE_STEPS = [50, 100, 250, 500, 1000, 2500, 5000, 10_000, 25_000];

// Marker labels sit near the running-total line wherever it happens to run, so they punch a
// background-coloured halo through it rather than trying to dodge the dashes.
const HALO = {
  stroke: 'var(--bg-elevated)',
  strokeWidth: 3,
  paintOrder: 'stroke' as const,
} as const;

// Axis labels read better without cents, matching DividendsChart's bar labels.
const axisFormatter = new Intl.NumberFormat('en-CA', {
  style: 'currency',
  currency: 'CAD',
  maximumFractionDigits: 0,
});

// Local-time parse: `new Date('2026-07-31')` is read as UTC and renders as the 30th west of it.
function parseDate(date: string): Date {
  return new Date(`${date}T00:00:00`);
}

function formatDay(date: string): string {
  return parseDate(date).toLocaleDateString('en-CA', {
    weekday: 'short',
    month: 'short',
    day: 'numeric',
  });
}

function formatShortDay(date: string): string {
  return parseDate(date).toLocaleDateString('en-CA', { month: 'short', day: 'numeric' });
}

function formatFullDay(date: string): string {
  return parseDate(date).toLocaleDateString('en-CA', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });
}

/** The largest round number that still sits under the chart's top, or none if the scale is tiny. */
function gridlineValue(extent: number): number | null {
  const candidates = GRIDLINE_STEPS.filter((step) => step < extent);
  return candidates.length > 0 ? candidates[candidates.length - 1] : null;
}

function indexOfMinimum(values: number[]): number {
  return values.reduce((lowest, value, index) => (value < values[lowest] ? index : lowest), 0);
}

export default function CashFlowOutlook({ outlook }: CashFlowOutlookProps) {
  const horizon = `Next ${outlook.weeks.length} weeks · through ${formatFullDay(outlook.to)}`;

  if (outlook.events.length === 0) {
    return (
      <div className="card" style={{ marginBottom: '1.25rem' }}>
        <p className="card-title">Cash flow outlook</p>
        <div className="banner banner-info" style={{ marginBottom: 0 }}>
          Not enough history to project yet. Record at least two dividend payments for a holding in{' '}
          <Link to="/dividends">Dividends</Link>, and one month of fees or interest in{' '}
          <Link to="/cash-transactions">Cash transactions</Link>.
        </div>
      </div>
    );
  }

  const net = Number(outlook.net);
  const shortfall = net < 0;
  const lineColor = shortfall ? 'var(--loss)' : 'var(--lavender-deep)';

  const weeklyIn = outlook.weeks.map((week) => Number(week.in));
  const weeklyOut = outlook.weeks.map((week) => Number(week.out));
  const weeklyTotal = outlook.weeks.map((week) => Number(week.runningTotal));
  const maxUp = Math.max(...weeklyIn, ...weeklyTotal.map((total) => Math.max(total, 0)), 0);
  const maxDown = Math.max(...weeklyOut, ...weeklyTotal.map((total) => Math.max(-total, 0)), 0);
  const fitted = Math.min(
    maxUp > 0 ? UP_ROOM / maxUp : Number.POSITIVE_INFINITY,
    maxDown > 0 ? DOWN_ROOM / maxDown : Number.POSITIVE_INFINITY,
  );
  const scale = Number.isFinite(fitted) ? fitted : 0;
  const gridline = gridlineValue(maxUp);

  const centerX = (index: number) => FIRST_CENTER_X + index * WEEK_SPACING;
  const totalY = (index: number) => ZERO_Y - weeklyTotal[index] * scale;
  const linePoints = weeklyTotal
    .map((_, index) => `${centerX(index)},${totalY(index).toFixed(1)}`)
    .join(' ');

  const lowWeek = indexOfMinimum(weeklyTotal);
  const lastWeek = outlook.weeks.length - 1;
  // The last event of the tightest day, matching the backend's end-of-day low.
  const lowEvent = outlook.events.reduce(
    (found, event, index) => (event.date === outlook.lowestOn ? index : found),
    -1,
  );
  // Judged per day for the same reason the backend does: a dip between two events on one day is
  // the sort order showing through, not a day that ends short.
  const endOfDayEvents = outlook.events.filter(
    (event, index) =>
      index + 1 === outlook.events.length || outlook.events[index + 1].date !== event.date,
  );
  const firstNegative = endOfDayEvents.find((event) => Number(event.runningTotal) < 0);

  const moneyIn = formatGainLoss(outlook.moneyIn);
  const moneyOut = formatGainLoss(`-${outlook.moneyOut}`);
  const netTotal = formatGainLoss(outlook.net);

  return (
    <div className="card" style={{ marginBottom: '1.25rem' }}>
      <div className="cf-head">
        <div>
          <p className="card-title" style={{ margin: '0 0 0.125rem' }}>
            Cash flow outlook
          </p>
          <span className="est">{horizon}</span>
        </div>
        <div className="cf-totals">
          <span className="cf-total">
            <span className="cf-total-label">
              <span className="legend-dot" style={{ background: 'var(--sage)' }} /> In
            </span>
            <span className={`mono ${moneyIn.className}`}>{moneyIn.text}</span>
          </span>
          <span className="cf-total">
            <span className="cf-total-label">
              <span className="legend-dot" style={{ background: 'var(--peach)' }} /> Out
            </span>
            <span className={`mono ${moneyOut.className}`}>{moneyOut.text}</span>
          </span>
          <span className="cf-total cf-total-key">
            <span className="cf-total-label">{shortfall ? 'Shortfall' : 'Free to spend'}</span>
            <span className={`mono ${netTotal.className}`}>{netTotal.text}</span>
          </span>
        </div>
      </div>

      {shortfall && firstNegative && (
        <div className="banner banner-warn">
          Projected dividends stop covering fees and interest on{' '}
          <strong>{formatShortDay(firstNegative.date)}</strong>, and the gap reaches{' '}
          <strong>{formatMoney(String(Math.abs(net)))}</strong> by {formatShortDay(outlook.to)}.
        </div>
      )}

      {outlook.warnings.length > 0 && (
        <div className="banner banner-warn">
          {outlook.warnings.join(' ')} Actual income is likely higher than shown.
        </div>
      )}

      <div className="cf-split">
        <div>
          <svg
            viewBox={`0 0 ${VIEW_WIDTH} ${VIEW_HEIGHT}`}
            width="100%"
            style={{ display: 'block', height: 'auto' }}
            role="img"
            aria-label="Weekly dividends in versus fees and interest out, with a running total"
          >
            {gridline !== null && (
              <>
                <line
                  x1={AXIS_LEFT}
                  y1={ZERO_Y - gridline * scale}
                  x2={AXIS_RIGHT}
                  y2={ZERO_Y - gridline * scale}
                  stroke="var(--border)"
                  strokeWidth="1"
                />
                <text
                  x={AXIS_LEFT - 6}
                  y={ZERO_Y - gridline * scale + 3.5}
                  fontSize="9"
                  fill="var(--text-faint)"
                  textAnchor="end"
                  fontFamily="DM Sans"
                >
                  {axisFormatter.format(gridline)}
                </text>
              </>
            )}

            <line
              x1={AXIS_LEFT}
              y1={ZERO_Y}
              x2={AXIS_RIGHT}
              y2={ZERO_Y}
              stroke="var(--border-strong)"
              strokeWidth="1"
            />
            <text
              x={AXIS_LEFT - 6}
              y={ZERO_Y + 3.5}
              fontSize="9"
              fill="var(--text-faint)"
              textAnchor="end"
              fontFamily="DM Sans"
            >
              $0
            </text>

            {outlook.weeks.map((week, index) => (
              <g key={week.weekStart}>
                {weeklyIn[index] > 0 && (
                  <rect
                    x={centerX(index) - BAR_WIDTH / 2}
                    y={ZERO_Y - weeklyIn[index] * scale}
                    width={BAR_WIDTH}
                    height={weeklyIn[index] * scale}
                    rx="3"
                    fill="var(--sage)"
                  />
                )}
                {weeklyOut[index] > 0 && (
                  <rect
                    x={centerX(index) - BAR_WIDTH / 2}
                    y={ZERO_Y}
                    width={BAR_WIDTH}
                    height={weeklyOut[index] * scale}
                    rx="3"
                    fill="var(--peach)"
                  />
                )}
                <text
                  x={centerX(index)}
                  y={WEEK_LABEL_Y}
                  fontSize="9"
                  fill={index === 0 ? 'var(--text)' : 'var(--text-muted)'}
                  fontWeight={index === 0 ? 600 : undefined}
                  textAnchor="middle"
                  fontFamily="DM Sans"
                >
                  {formatShortDay(week.weekStart)}
                </text>
              </g>
            ))}

            {/* Dashed because every point is a projection, never a booked figure. */}
            <polyline
              points={linePoints}
              fill="none"
              stroke={lineColor}
              strokeWidth="2"
              strokeDasharray="5 4"
              strokeLinecap="round"
              strokeLinejoin="round"
            />

            {lowWeek !== lastWeek && (
              <>
                <circle
                  cx={centerX(lowWeek)}
                  cy={totalY(lowWeek)}
                  r="4.5"
                  fill="var(--bg-elevated)"
                  stroke={lineColor}
                  strokeWidth="2"
                />
                <text
                  x={centerX(lowWeek) + 7}
                  // Below the marker normally, but above it when the low is under the zero line,
                  // where below would land on the week labels.
                  y={totalY(lowWeek) + (weeklyTotal[lowWeek] < 0 ? -10 : 16.3)}
                  fontSize="9"
                  fill={lineColor}
                  fontWeight={600}
                  fontFamily="DM Sans"
                  {...HALO}
                >
                  low {formatMoney(String(weeklyTotal[lowWeek]))}
                </text>
              </>
            )}

            <circle cx={centerX(lastWeek)} cy={totalY(lastWeek)} r="4" fill={lineColor} />
            {/* Below the line, not above: above it collides with the dashes. */}
            <text
              x={centerX(lastWeek) - 5}
              y={totalY(lastWeek) + 16.8}
              fontSize="9"
              fill={lineColor}
              textAnchor="end"
              fontWeight={600}
              fontFamily="DM Sans"
              {...HALO}
            >
              {netTotal.text}
            </text>

            <text
              x={VIEW_WIDTH / 2}
              y={CAPTION_Y}
              fontSize="9"
              fill="var(--text-faint)"
              textAnchor="middle"
              fontFamily="DM Sans"
            >
              week beginning
            </text>
          </svg>

          <div className="cf-legend">
            <span>
              <span className="swatch-dot" style={{ background: 'var(--sage)' }} /> Dividends in
            </span>
            <span>
              <span className="swatch-dot" style={{ background: 'var(--peach)' }} /> Fees &amp;
              interest out
            </span>
            <span>
              <span className="swatch-line" /> Running total
            </span>
          </div>
        </div>

        <div>
          <div className="cf-schedule">
            <table className="cf-table">
              <thead>
                <tr>
                  <th>When</th>
                  <th>What</th>
                  <th className="num">Amount</th>
                  <th className="num">Running</th>
                </tr>
              </thead>
              <tbody>
                {outlook.events.map((event, index) => {
                  const amount = formatGainLoss(event.amount);
                  return (
                    <tr
                      key={`${event.date}-${event.label}-${index}`}
                      className={index === lowEvent ? 'row-low' : undefined}
                    >
                      <td className="mono nowrap">{formatDay(event.date)}</td>
                      <td>{event.label}</td>
                      <td className={`num mono ${amount.className}`}>{amount.text}</td>
                      <td className="num mono">{formatMoney(event.runningTotal)}</td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          <p className="card-meta" style={{ marginTop: '0.5rem' }}>
            {outlook.events.length} projected {outlook.events.length === 1 ? 'event' : 'events'} ·
            dates come from the cadence of what you have recorded, so read them as "around then".
            Amounts repeat the last recorded payment, so they ignore share-count changes.
          </p>
        </div>
      </div>
    </div>
  );
}
