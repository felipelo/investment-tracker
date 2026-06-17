import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useHoldings } from '../api/hooks';
import { usePortfolioContext } from '../context/PortfolioContext';
import HoldingsTable from '../components/HoldingsTable';
import HoldingHistoryTable from '../components/HoldingHistoryTable';
import UpdatePricesModal from '../components/UpdatePricesModal';

export default function HoldingsPage() {
  const { activePortfolioId, activePortfolio } = usePortfolioContext();
  const holdings = useHoldings(activePortfolioId);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [showUpdatePrices, setShowUpdatePrices] = useState(false);

  const data = holdings.data ?? [];
  const hasHoldings = data.length > 0;
  const missingPrices = hasHoldings && data.some((h) => h.marketValue === null);

  useEffect(() => {
    if (selectedId === null && data.length > 0) {
      setSelectedId(data[0].securityId);
    }
  }, [data, selectedId]);

  const selected = data.find((h) => h.securityId === selectedId) ?? null;

  return (
    <>
      <header className="page-header">
        <div>
          <h1 className="page-title">Holdings</h1>
          <p className="page-subtitle">
            {activePortfolio ? `${activePortfolio.name} · ` : ''}Current positions · ACB per security
          </p>
        </div>
        <button
          className="btn btn-primary"
          onClick={() => setShowUpdatePrices(true)}
          disabled={!hasHoldings}
        >
          Update prices
        </button>
      </header>

      {activePortfolioId === null && (
        <div className="card">
          <p style={{ color: 'var(--text-muted)', margin: 0 }}>
            No portfolio selected. <Link to="/portfolios">Create a portfolio</Link> to get
            started.
          </p>
        </div>
      )}

      {activePortfolioId !== null && holdings.isPending && (
        <div className="card">
          <p style={{ color: 'var(--text-muted)', margin: 0 }}>Loading…</p>
        </div>
      )}

      {holdings.isError && (
        <div className="card">
          <p className="negative" style={{ margin: 0 }}>
            Could not load holdings.
          </p>
        </div>
      )}

      {holdings.isSuccess && !hasHoldings && (
        <div className="card">
          <p style={{ color: 'var(--text-muted)', margin: 0 }}>
            No holdings yet. <Link to="/record-trade">Record a trade</Link> to get
            started.
          </p>
        </div>
      )}

      {hasHoldings && (
        <>
          {missingPrices && (
            <div className="banner banner-info" style={{ marginBottom: '1.25rem' }}>
              Enter prices to see market value and unrealized gain.
            </div>
          )}

          <HoldingsTable
            holdings={data}
            selectedSecurityId={selectedId}
            onSelect={setSelectedId}
          />

          {selected && (
            <HoldingHistoryTable
              portfolioId={activePortfolioId}
              securityId={selected.securityId}
              ticker={selected.ticker}
            />
          )}
        </>
      )}

      {showUpdatePrices && (
        <UpdatePricesModal holdings={data} onClose={() => setShowUpdatePrices(false)} />
      )}
    </>
  );
}
