import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useDashboard, useDividendSummary, useHoldings, useQuotes } from '../api/hooks';
import { usePortfolioContext } from '../context/PortfolioContext';
import { tickerToMarketSymbol } from '../lib/symbols';
import HeroStats from '../components/HeroStats';
import PeriodReturns from '../components/PeriodReturns';
import AllocationDonut from '../components/AllocationDonut';
import DividendsChart from '../components/DividendsChart';
import LivePrices from '../components/LivePrices';

const TYPE_TAG: Record<string, string> = {
  Taxable: 'tag-peach',
  TFSA: 'tag-sky',
  RRSP: 'tag-butter',
  'Smith Maneuver': 'tag-lavender',
  Other: 'tag-sage',
};

function typeTagClass(type: string | null): string {
  return (type && TYPE_TAG[type]) || 'tag-sage';
}

export default function DashboardPage() {
  const { portfolios, activePortfolioId, activePortfolio, setActivePortfolioId } =
    usePortfolioContext();
  const dashboard = useDashboard(activePortfolioId);
  const [year, setYear] = useState<number | null>(null);
  const dividendSummary = useDividendSummary(activePortfolioId, year);

  const holdings = useHoldings(activePortfolioId);
  const holdingData = useMemo(() => holdings.data ?? [], [holdings.data]);
  const symbols = useMemo(
    () => Array.from(new Set(holdingData.map((h) => tickerToMarketSymbol(h.ticker)))),
    [holdingData],
  );
  const quotes = useQuotes(symbols);

  const data = dashboard.data;
  const noReturns =
    data != null &&
    !data.todaysReturn.available &&
    data.periodReturns.every((p) => !p.available);

  return (
    <>
      <header className="page-header">
        <div>
          <h1 className="page-title">Dashboard</h1>
          <p className="page-subtitle">
            {activePortfolio ? activePortfolio.name : 'Portfolio overview'}
            {data?.asOfDate ? ` · snapshot ${data.asOfDate}` : ''}
          </p>
        </div>
        {activePortfolio && (
          <div className="portfolio-switcher">
              <span className={`tag ${typeTagClass(activePortfolio.type)}`}>
                {activePortfolio.type ?? 'Other'}
              </span>
              <select
                value={activePortfolioId ?? ''}
                onChange={(e) => setActivePortfolioId(Number(e.target.value))}
                style={{
                  fontFamily: 'var(--font)',
                  fontSize: '0.875rem',
                  border: 'none',
                  background: 'transparent',
                  cursor: 'pointer',
                }}
              >
                {portfolios.map((portfolio) => (
                  <option key={portfolio.id} value={portfolio.id}>
                    {portfolio.name}
                  </option>
                ))}
              </select>
          </div>
        )}
      </header>

      {activePortfolioId === null && (
        <div className="card">
          <p style={{ color: 'var(--text-muted)', margin: 0 }}>
            No portfolio selected. <Link to="/portfolios">Create a portfolio</Link> to get
            started.
          </p>
        </div>
      )}

      {activePortfolioId !== null && dashboard.isPending && (
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
          <PeriodReturns periodReturns={data.periodReturns} />

          {holdingData.length > 0 && (
            <LivePrices
              holdings={holdingData}
              quotes={quotes.data}
              isLoading={quotes.isPending}
              isFetching={quotes.isFetching}
              isError={quotes.isError}
              onReload={() => {
                void quotes.refetch();
              }}
            />
          )}

          <div className="grid-dashboard">
            <AllocationDonut allocation={data.allocation} />
            {dividendSummary.data ? (
              <DividendsChart
                summary={dividendSummary.data}
                year={dividendSummary.data.year}
                availableYears={dividendSummary.data.availableYears}
                onYearChange={setYear}
              />
            ) : (
              <div className="card">
                <p className="card-title">Dividends</p>
                <p style={{ color: 'var(--text-muted)', margin: 0 }}>Loading…</p>
              </div>
            )}
          </div>
        </>
      )}
    </>
  );
}
