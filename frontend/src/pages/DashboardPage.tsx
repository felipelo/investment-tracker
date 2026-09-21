import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  useCashFlowOutlook,
  useDashboard,
  useDividendSummary,
  useHoldings,
  useQuotes,
} from '../api/hooks';
import { usePortfolioContext } from '../context/PortfolioContext';
import { tickerToMarketSymbol } from '../lib/symbols';
import CashFlowOutlook from '../components/CashFlowOutlook';
import HeroStats from '../components/HeroStats';
import ReturnBreakdownTable from '../components/ReturnBreakdownTable';
import AllocationDonut from '../components/AllocationDonut';
import DividendsChart from '../components/DividendsChart';

function trailingTwelveMonthTotal(
  currentYearMonths: string[] | undefined,
  priorYearMonths: string[] | undefined,
  currentMonth: number,
): number | null {
  if (!currentYearMonths || !priorYearMonths) return null;
  if (currentYearMonths.length < 12 || priorYearMonths.length < 12) return null;
  let total = 0;
  for (let i = 0; i <= currentMonth; i++) {
    total += Number(currentYearMonths[i]);
  }
  for (let i = currentMonth + 1; i < 12; i++) {
    total += Number(priorYearMonths[i]);
  }
  return total;
}

export default function DashboardPage() {
  const [searchParams] = useSearchParams();
  const isOverall = searchParams.get('view') === 'all';
  const { portfolios, activePortfolioId, activePortfolio } = usePortfolioContext();
  const dashboard = useDashboard(activePortfolioId, isOverall);
  const [year, setYear] = useState<number | null>(null);
  const dividendSummary = useDividendSummary(activePortfolioId, year, isOverall);
  const currentYear = new Date().getFullYear();
  const ttmCurrentYear = useDividendSummary(activePortfolioId, currentYear, isOverall);
  const ttmPriorYear = useDividendSummary(activePortfolioId, currentYear - 1, isOverall);
  const cashFlowOutlook = useCashFlowOutlook(activePortfolioId, isOverall);

  const holdings = useHoldings(isOverall ? null : activePortfolioId);
  const holdingData = useMemo(() => holdings.data ?? [], [holdings.data]);
  const symbols = useMemo(
    () => Array.from(new Set(holdingData.map((h) => tickerToMarketSymbol(h.ticker)))),
    [holdingData],
  );
  const quotes = useQuotes(symbols);

  const data = dashboard.data;
  const yieldToCostPct = useMemo(() => {
    const invested = data != null ? Number(data.invested) : NaN;
    const ttm = trailingTwelveMonthTotal(
      ttmCurrentYear.data?.months,
      ttmPriorYear.data?.months,
      new Date().getMonth(),
    );
    if (ttm === null || !Number.isFinite(invested) || invested <= 0) return null;
    return (ttm / invested) * 100;
  }, [data, ttmCurrentYear.data, ttmPriorYear.data]);
  const noReturns =
    data != null &&
    !data.todaysReturn.available &&
    data.periodReturns.every((p) => !p.available);
  const hasMixedCurrencies = new Set(portfolios.map((portfolio) => portfolio.baseCurrency)).size > 1;

  return (
    <>
      <header className="page-header">
        <div>
          <h1 className="page-title">Dashboard</h1>
          <p className="page-subtitle">
            {isOverall ? 'All portfolios' : activePortfolio?.name ?? 'Portfolio overview'}
            {data?.asOfDate ? ` · snapshot ${data.asOfDate}` : ''}
          </p>
        </div>
        {holdingData.length > 0 && (
          <button
            type="button"
            className="btn btn-ghost"
            onClick={() => {
              void quotes.refetch();
            }}
            disabled={quotes.isFetching}
          >
            {quotes.isFetching ? 'Reloading…' : 'Reload prices'}
          </button>
        )}
      </header>

      {!isOverall && activePortfolioId === null && (
        <div className="card">
          <p style={{ color: 'var(--text-muted)', margin: 0 }}>
            No portfolio selected. <Link to="/portfolios">Create a portfolio</Link> to get
            started.
          </p>
        </div>
      )}

      {(isOverall || activePortfolioId !== null) && dashboard.isPending && (
        <div className="card">
          <p style={{ color: 'var(--text-muted)', margin: 0 }}>Loading…</p>
        </div>
      )}

      {dashboard.isError && (
        <div className="card">
          <p className="negative" style={{ margin: 0 }}>
            Could not load the dashboard.
          </p>
        </div>
      )}

      {data && (
        <>
          {isOverall && hasMixedCurrencies && (
            <div className="banner banner-info" style={{ marginBottom: '1.25rem' }}>
              Overall totals combine portfolio currencies without conversion.
            </div>
          )}

          {data.portfolioValue === null && (
            <div className="banner banner-info" style={{ marginBottom: '1.25rem' }}>
              No prices recorded yet.{' '}
              <Link to="/holdings">Update prices</Link> to see portfolio value and returns.
            </div>
          )}

          {data.portfolioValue !== null && noReturns && (
            <div className="banner banner-info" style={{ marginBottom: '1.25rem' }}>
              Record price snapshots over time (via Holdings → Update prices) to see today's and
              period returns.
            </div>
          )}

          <HeroStats dashboard={data} />

          {cashFlowOutlook.data && <CashFlowOutlook outlook={cashFlowOutlook.data} />}

          <div className="grid-dashboard">
            <AllocationDonut allocation={data.allocation} />
            {dividendSummary.data ? (
              <DividendsChart
                summary={dividendSummary.data}
                year={dividendSummary.data.year}
                availableYears={dividendSummary.data.availableYears}
                onYearChange={setYear}
                yieldToCostPct={yieldToCostPct}
              />
            ) : (
              <div className="card">
                <p className="card-title">Dividends</p>
                <p style={{ color: 'var(--text-muted)', margin: 0 }}>Loading…</p>
              </div>
            )}
          </div>

          <div style={{ marginTop: '1.25rem' }}>
            <ReturnBreakdownTable dashboard={data} />
          </div>
        </>
      )}
    </>
  );
}
