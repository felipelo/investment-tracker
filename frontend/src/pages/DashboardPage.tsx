import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useDashboard, useDividendSummary, useHoldings, useQuotes } from '../api/hooks';
import { usePortfolioContext } from '../context/PortfolioContext';
import { tickerToMarketSymbol } from '../lib/symbols';
import HeroStats from '../components/HeroStats';
import ReturnBreakdownTable from '../components/ReturnBreakdownTable';
import AllocationDonut from '../components/AllocationDonut';
import DividendsChart from '../components/DividendsChart';

export default function DashboardPage() {
  const [searchParams] = useSearchParams();
  const isOverall = searchParams.get('view') === 'all';
  const { portfolios, activePortfolioId, activePortfolio } = usePortfolioContext();
  const dashboard = useDashboard(activePortfolioId, isOverall);
  const [year, setYear] = useState<number | null>(null);
  const dividendSummary = useDividendSummary(activePortfolioId, year, isOverall);

  const holdings = useHoldings(isOverall ? null : activePortfolioId);
  const holdingData = useMemo(() => holdings.data ?? [], [holdings.data]);
  const symbols = useMemo(
    () => Array.from(new Set(holdingData.map((h) => tickerToMarketSymbol(h.ticker)))),
    [holdingData],
  );
  const quotes = useQuotes(symbols);

  // #region agent log
  fetch('http://127.0.0.1:7878/ingest/686539c7-4571-47cb-8627-4f7f46385d72',{method:'POST',headers:{'Content-Type':'application/json','X-Debug-Session-Id':'bff96f'},body:JSON.stringify({sessionId:'bff96f',location:'DashboardPage.tsx:render',message:'quotes state on render',data:{holdingCount:holdingData.length,symbolCount:symbols.length,symbols,isFetching:quotes.isFetching,isPending:quotes.isPending,isError:quotes.isError,hasData:quotes.data!=null,dataUpdatedAt:quotes.dataUpdatedAt},timestamp:Date.now(),hypothesisId:'H2'})}).catch(()=>{});
  // #endregion

  const data = dashboard.data;
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
              // #region agent log
              fetch('http://127.0.0.1:7878/ingest/686539c7-4571-47cb-8627-4f7f46385d72',{method:'POST',headers:{'Content-Type':'application/json','X-Debug-Session-Id':'bff96f'},body:JSON.stringify({sessionId:'bff96f',location:'DashboardPage.tsx:reloadClick',message:'Reload prices clicked',data:{symbolCount:symbols.length,isFetching:quotes.isFetching,isPending:quotes.isPending,isError:quotes.isError},timestamp:Date.now(),hypothesisId:'H1'})}).catch(()=>{});
              // #endregion
              void quotes.refetch().then((result) => {
                // #region agent log
                fetch('http://127.0.0.1:7878/ingest/686539c7-4571-47cb-8627-4f7f46385d72',{method:'POST',headers:{'Content-Type':'application/json','X-Debug-Session-Id':'bff96f'},body:JSON.stringify({sessionId:'bff96f',location:'DashboardPage.tsx:reloadClick:done',message:'refetch completed',data:{status:result.status,isError:result.isError,isFetching:result.isFetching,dataUpdatedAt:result.dataUpdatedAt,quoteCount:result.data?.length??0},timestamp:Date.now(),hypothesisId:'H3'})}).catch(()=>{});
                // #endregion
              }).catch((err) => {
                // #region agent log
                fetch('http://127.0.0.1:7878/ingest/686539c7-4571-47cb-8627-4f7f46385d72',{method:'POST',headers:{'Content-Type':'application/json','X-Debug-Session-Id':'bff96f'},body:JSON.stringify({sessionId:'bff96f',location:'DashboardPage.tsx:reloadClick:error',message:'refetch rejected',data:{error:String(err)},timestamp:Date.now(),hypothesisId:'H5'})}).catch(()=>{});
                // #endregion
              });
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

          <div style={{ marginTop: '1.25rem' }}>
            <ReturnBreakdownTable dashboard={data} />
          </div>
        </>
      )}
    </>
  );
}
