import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { usePortfolioContext } from '../context/PortfolioContext';
import { useDeletePortfolio } from '../api/hooks';
import { ApiError } from '../api/client';
import { formatMoney, formatGainLoss, formatPercent } from '../lib/actions';
import type { Portfolio } from '../api/types';
import PortfolioFormModal from '../components/PortfolioFormModal';

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

export default function PortfoliosPage() {
  const { portfolios, activePortfolioId, setActivePortfolioId, isPending, isError } =
    usePortfolioContext();
  const navigate = useNavigate();
  const deletePortfolio = useDeletePortfolio();

  const [showCreate, setShowCreate] = useState(false);
  const [editing, setEditing] = useState<Portfolio | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  async function handleDelete(portfolio: Portfolio) {
    setDeleteError(null);
    if (!window.confirm(`Delete portfolio "${portfolio.name}"?`)) return;
    try {
      await deletePortfolio.mutateAsync(portfolio.id);
    } catch (error) {
      if (error instanceof ApiError) {
        setDeleteError(error.fieldErrors.portfolio ?? error.message);
      }
    }
  }

  return (
    <>
      <header className="page-header">
        <div>
          <h1 className="page-title">Portfolios</h1>
          <p className="page-subtitle">Switch active context for dashboard &amp; reports</p>
        </div>
        <button className="btn btn-primary" onClick={() => setShowCreate(true)}>
          New portfolio
        </button>
      </header>

      {isPending && (
        <div className="card">
          <p style={{ color: 'var(--text-muted)', margin: 0 }}>Loading…</p>
        </div>
      )}

      {isError && (
        <div className="card">
          <p className="negative" style={{ margin: 0 }}>
            Could not load portfolios.
          </p>
        </div>
      )}

      {deleteError && (
        <div className="banner banner-warn" style={{ marginBottom: '1.25rem' }}>
          {deleteError}
        </div>
      )}

      {!isPending && !isError && portfolios.length === 0 && (
        <div className="card">
          <p style={{ color: 'var(--text-muted)', margin: 0 }}>
            No portfolios yet. Create one to get started.
          </p>
        </div>
      )}

      {portfolios.length > 0 && (
        <div className="grid-2">
          {portfolios.map((portfolio) => {
            const isActive = portfolio.id === activePortfolioId;
            const ret = formatGainLoss(portfolio.returnAmount);
            const retPct = formatPercent(portfolio.returnPct);
            return (
              <div
                key={portfolio.id}
                className="card"
                style={{
                  position: 'relative',
                  border: isActive ? '2px solid var(--sage)' : undefined,
                }}
              >
                {isActive && (
                  <span
                    className="tag tag-sage"
                    style={{ position: 'absolute', top: '1rem', right: '1rem' }}
                  >
                    Active
                  </span>
                )}
                <span className={`tag ${typeTagClass(portfolio.type)}`} style={{ marginBottom: '0.75rem' }}>
                  {portfolio.type ?? 'Other'}
                </span>
                <h2 style={{ fontSize: '1.125rem', margin: '0 0 0.25rem', letterSpacing: '-0.02em' }}>
                  {portfolio.name}
                </h2>
                <p style={{ margin: '0 0 1rem', fontSize: '0.875rem', color: 'var(--text-muted)' }}>
                  {portfolio.description ?? '—'}
                </p>

                <div style={{ display: 'flex', gap: '2rem', flexWrap: 'wrap' }}>
                  <Metric label="Invested" value={formatMoney(portfolio.invested)} />
                  <Metric label="Value" value={formatMoney(portfolio.marketValue)} />
                  <Metric
                    label="Return"
                    value={portfolio.returnAmount === null ? '—' : ret.text}
                    valueClass={ret.className}
                    sub={portfolio.returnPct === null ? undefined : retPct.text}
                    subClass={retPct.className}
                  />
                  <Metric label="Holdings" value={String(portfolio.holdingsCount)} mono={false} />
                </div>

                <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.25rem', flexWrap: 'wrap' }}>
                  {isActive ? (
                    <button className="btn btn-ghost" onClick={() => navigate('/holdings')}>
                      Open holdings →
                    </button>
                  ) : (
                    <button className="btn btn-ghost" onClick={() => setActivePortfolioId(portfolio.id)}>
                      Switch to this
                    </button>
                  )}
                  <button className="btn btn-ghost" onClick={() => setEditing(portfolio)}>
                    Edit
                  </button>
                  <button className="btn btn-ghost" onClick={() => handleDelete(portfolio)}>
                    Delete
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {showCreate && <PortfolioFormModal onClose={() => setShowCreate(false)} />}
      {editing && (
        <PortfolioFormModal portfolio={editing} onClose={() => setEditing(null)} />
      )}
    </>
  );
}

interface MetricProps {
  label: string;
  value: string;
  valueClass?: string;
  sub?: string;
  subClass?: string;
  mono?: boolean;
}

function Metric({ label, value, valueClass = '', sub, subClass = '', mono = true }: MetricProps) {
  return (
    <div>
      <div
        style={{
          fontSize: '0.6875rem',
          fontWeight: 600,
          textTransform: 'uppercase',
          color: 'var(--text-muted)',
        }}
      >
        {label}
      </div>
      <div
        className={`${mono ? 'mono ' : ''}${valueClass}`}
        style={{ fontSize: '1.25rem', fontWeight: 600 }}
      >
        {value}
      </div>
      {sub && (
        <div className={`mono ${subClass}`} style={{ fontSize: '0.8125rem', fontWeight: 600 }}>
          {sub}
        </div>
      )}
    </div>
  );
}
