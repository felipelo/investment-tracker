import { useState } from 'react';
import type { DashboardData, PeriodReturn, ReturnFigure } from '../api/types';
import { formatGainLoss, formatPercent } from '../lib/actions';

interface ReturnBreakdownTableProps {
  dashboard: DashboardData;
}

interface Column {
  label: string;
  price: string | null;
  pricePct: string | null;
  dividend: string | null;
  dividendPct: string | null;
  total: string | null;
  pct: string | null;
  available: boolean;
  isAllTime: boolean;
}

function buildColumns(
  periodReturns: PeriodReturn[],
  todaysReturn: ReturnFigure | undefined,
  allTimePrice: ReturnFigure,
  allTimeDividend: ReturnFigure,
  allTimeTotal: { amount: string | null; pct: string | null; available: boolean },
): Column[] {
  const today = periodReturns.find((period) => period.label === 'Today');
  const remainingPeriods = periodReturns.filter((period) => period.label !== 'Today');
  const toColumn = (period: PeriodReturn): Column => ({
    label: period.label,
    price: period.priceAmount,
    pricePct: period.pricePct,
    dividend: period.dividendAmount,
    dividendPct: period.dividendPct,
    total: period.amount,
    pct: period.pct,
    available: period.available,
    isAllTime: false,
  });
  const todayColumn: Column = today
    ? toColumn(today)
    : {
        label: 'Today',
        price: todaysReturn?.amount ?? null,
        pricePct: todaysReturn?.pct ?? null,
        dividend: null,
        dividendPct: null,
        total: todaysReturn?.amount ?? null,
        pct: todaysReturn?.pct ?? null,
        available: todaysReturn?.available ?? false,
        isAllTime: false,
      };

  return [
    todayColumn,
    ...remainingPeriods.map(toColumn),
    {
      label: 'All-time',
      price: allTimePrice.amount,
      pricePct: allTimePrice.pct,
      dividend: allTimeDividend.amount,
      dividendPct: allTimeDividend.pct,
      total: allTimeTotal.amount,
      pct: allTimeTotal.pct,
      available: allTimeTotal.available,
      isAllTime: true,
    },
  ];
}

function Amount({ value, available }: { value: string | null; available: boolean }) {
  if (!available) {
    return <span style={{ color: 'var(--text-faint)' }}>—</span>;
  }
  const formatted = formatGainLoss(value);
  return <span className={formatted.className}>{formatted.text}</span>;
}

function AmountWithPct({
  value,
  pct,
  available,
}: {
  value: string | null;
  pct: string | null;
  available: boolean;
}) {
  const pctFormatted = formatPercent(pct);
  return (
    <>
      <Amount value={value} available={available} />
      {available && pct !== null && <span className="cell-pct">{pctFormatted.text}</span>}
    </>
  );
}

const numClass = (isAllTime: boolean) => (isAllTime ? 'num mono col-alltime' : 'num mono');

export default function ReturnBreakdownTable({ dashboard }: ReturnBreakdownTableProps) {
  const [expanded, setExpanded] = useState<{ price: boolean; dividend: boolean }>({
    price: true,
    dividend: false,
  });

  const columns = buildColumns(
    dashboard.periodReturns,
    dashboard.todaysReturn,
    dashboard.priceReturn,
    dashboard.dividendReturn,
    dashboard.allTimeReturn,
  );

  const holdingColumns = dashboard.holdingBreakdowns.map((holding) => ({
    holding,
    columns: buildColumns(
      holding.periodReturns,
      undefined,
      holding.priceReturn,
      holding.dividendReturn,
      // Sub-rows only surface the price or dividend cell, so the total column is unused here.
      { amount: null, pct: null, available: false },
    ),
  }));

  const toggle = (row: 'price' | 'dividend') =>
    setExpanded((prev) => ({ ...prev, [row]: !prev[row] }));

  const hasHoldings = dashboard.holdingBreakdowns.length > 0;

  const renderSubRows = (metric: 'price' | 'dividend') =>
    holdingColumns.map(({ holding, columns: cols }) => (
      <tr key={`${metric}-${holding.securityId}`} className="row-sub">
        <td className="row-label">
          <span className="ticker">{holding.ticker}</span>
        </td>
        {cols.map((column) => (
          <td key={column.label} className={numClass(column.isAllTime)}>
            <AmountWithPct
              value={metric === 'price' ? column.price : column.dividend}
              pct={metric === 'price' ? column.pricePct : column.dividendPct}
              available={metric === 'price' ? column.price !== null : column.dividend !== null}
            />
          </td>
        ))}
      </tr>
    ));

  return (
    <div className="card">
      <p className="card-title">Returns — price vs dividends</p>
      <div className="table-wrap">
        <table className="breakdown-matrix">
          <thead>
            <tr>
              <th></th>
              {columns.map((column) => (
                <th key={column.label} className={column.isAllTime ? 'num col-alltime' : 'num'}>
                  {column.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            <tr>
              <td className="row-label">
                <button
                  type="button"
                  className="row-toggle"
                  onClick={() => toggle('price')}
                  aria-expanded={expanded.price}
                  disabled={!hasHoldings}
                >
                  <span className={`row-caret${expanded.price ? ' open' : ''}`} aria-hidden="true">
                    ▸
                  </span>
                  <span className="legend-dot" style={{ background: 'var(--sage)' }} /> Price return
                </button>
              </td>
              {columns.map((column) => (
                <td key={column.label} className={numClass(column.isAllTime)}>
                  <AmountWithPct
                    value={column.price}
                    pct={column.pricePct}
                    available={column.price !== null}
                  />
                </td>
              ))}
            </tr>
            {expanded.price && renderSubRows('price')}
            <tr>
              <td className="row-label">
                <button
                  type="button"
                  className="row-toggle"
                  onClick={() => toggle('dividend')}
                  aria-expanded={expanded.dividend}
                  disabled={!hasHoldings}
                >
                  <span
                    className={`row-caret${expanded.dividend ? ' open' : ''}`}
                    aria-hidden="true"
                  >
                    ▸
                  </span>
                  <span className="legend-dot" style={{ background: 'var(--lavender)' }} /> Dividend
                  return
                </button>
              </td>
              {columns.map((column) => (
                <td key={column.label} className={numClass(column.isAllTime)}>
                  <AmountWithPct
                    value={column.dividend}
                    pct={column.dividendPct}
                    available={column.dividend !== null}
                  />
                </td>
              ))}
            </tr>
            {expanded.dividend && renderSubRows('dividend')}
            <tr className="row-total">
              <td className="row-label">Total return</td>
              {columns.map((column) => (
                <td key={column.label} className={numClass(column.isAllTime)}>
                  <AmountWithPct
                    value={column.total}
                    pct={column.pct}
                    available={column.total !== null}
                  />
                </td>
              ))}
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}
