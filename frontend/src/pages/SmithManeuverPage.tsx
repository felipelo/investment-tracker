import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useSmithManeuver } from '../api/hooks';
import type { SmithManeuverFlow } from '../api/types';
import { formatMoney } from '../lib/actions';
import { usePortfolioContext } from '../context/PortfolioContext';
import FlowChain from '../components/FlowChain';
import HelocAccountsTable from '../components/HelocAccountsTable';
import FlowFormModal from '../components/FlowFormModal';

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

function monthLabel(date: string): string {
  const parsed = new Date(`${date}T00:00:00`);
  if (Number.isNaN(parsed.getTime())) return date;
  return parsed.toLocaleDateString('en-CA', { month: 'long', year: 'numeric' });
}

export default function SmithManeuverPage() {
  const { portfolios, activePortfolioId, activePortfolio, setActivePortfolioId } =
    usePortfolioContext();
  const smithManeuver = useSmithManeuver(activePortfolioId);

  const [showModal, setShowModal] = useState(false);
  const [editingFlow, setEditingFlow] = useState<SmithManeuverFlow | null>(null);

  function openNew() {
    setEditingFlow(null);
    setShowModal(true);
  }

  function openEdit(flow: SmithManeuverFlow) {
    setEditingFlow(flow);
    setShowModal(true);
  }

  const data = smithManeuver.data;
  const latestInterest = data?.interestLog[0] ?? null;

  return (
    <>
      <header className="page-header">
        <div>
          <h1 className="page-title">Smith Maneuver</h1>
          <p className="page-subtitle">
            {activePortfolio
              ? `${activePortfolio.name} · borrow-to-invest tracing — HELOC deductible interest`
              : 'Borrow-to-invest tracing · HELOC deductible interest'}
          </p>
        </div>
        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
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
          {activePortfolioId !== null && (
            <button type="button" className="btn btn-primary" onClick={openNew}>
              New flow
            </button>
          )}
        </div>
      </header>

      {activePortfolioId === null && (
        <div className="card">
          <p style={{ color: 'var(--text-muted)', margin: 0 }}>
            No portfolio selected. <Link to="/portfolios">Create a portfolio</Link> to get started.
          </p>
        </div>
      )}

      {activePortfolioId !== null && (
        <>
          <div className="banner banner-info">
            <span>ℹ</span>
            <span>
              Record-keeping aid only — not tax advice. Confirm figures against CRA rules.
            </span>
          </div>

          {smithManeuver.isPending && (
            <div className="card">
              <p style={{ color: 'var(--text-muted)', margin: 0 }}>Loading…</p>
            </div>
          )}

          {smithManeuver.isError && (
            <div className="banner banner-warn">Could not load Smith Maneuver data.</div>
          )}

          {data && (
            <>
              <div className="grid-2" style={{ marginBottom: '1.25rem' }}>
                <div className="card">
                  <p className="card-title">Investment-use balance</p>
                  <p className="card-value">{formatMoney(data.investmentUseBalance)}</p>
                  <p className="card-meta">Traced HELOC principal for investment</p>
                </div>
                <div className="card">
                  {latestInterest ? (
                    <>
                      <p className="card-title">
                        {monthLabel(latestInterest.date)} interest · deductible est.
                      </p>
                      <p className="card-value card-value-sm">
                        {formatMoney(latestInterest.deductibleEstimate)}{' '}
                        <span style={{ fontSize: '0.875rem', color: 'var(--text-muted)' }}>
                          of {formatMoney(latestInterest.amount)} total
                        </span>
                      </p>
                      <p className="card-meta">
                        {deductiblePctLabel(
                          latestInterest.deductibleEstimate,
                          latestInterest.amount
                        )}
                      </p>
                    </>
                  ) : (
                    <>
                      <p className="card-title">Interest · deductible est.</p>
                      <p className="card-value card-value-sm">—</p>
                      <p className="card-meta">No HELOC interest logged yet</p>
                    </>
                  )}
                </div>
              </div>

              {data.helocAccounts.length === 0 && data.flows.length === 0 && (
                <div className="banner banner-info">
                  No HELOC accounts yet. <Link to="/accounts">Add a HELOC account</Link> and record a
                  HELOC draw to start tracing.
                </div>
              )}

              {data.flows.map((flow) => (
                <FlowChain key={flow.id} flow={flow} onEdit={openEdit} />
              ))}

              {data.flows.length === 0 && data.helocAccounts.length > 0 && (
                <div className="card" style={{ marginBottom: '1.25rem' }}>
                  <p style={{ color: 'var(--text-muted)', margin: 0 }}>
                    No flows yet. Use <strong>New flow</strong> to chain a HELOC draw through to a
                    purchase.
                  </p>
                </div>
              )}

              {data.warnings.map((warning, index) => (
                <div key={index} className="banner banner-warn" style={{ marginBottom: '1.25rem' }}>
                  <span>⚠</span>
                  <div>
                    <strong>{warning.title}</strong>
                    <br />
                    {warning.detail}
                  </div>
                </div>
              ))}

              {data.helocAccounts.length > 0 && (
                <HelocAccountsTable accounts={data.helocAccounts} />
              )}

              {data.interestLog.length > 0 && (
                <div className="card" style={{ marginTop: '1.25rem' }}>
                  <p className="card-title">Interest log</p>
                  <div className="table-wrap">
                    <table>
                      <thead>
                        <tr>
                          <th>Date</th>
                          <th>Type</th>
                          <th>Amount</th>
                          <th>Deductible est.</th>
                        </tr>
                      </thead>
                      <tbody>
                        {data.interestLog.map((entry) => (
                          <tr key={entry.id}>
                            <td className="mono">{entry.date}</td>
                            <td>{entry.type}</td>
                            <td className="mono">{formatMoney(entry.amount)}</td>
                            <td className="mono positive">
                              {formatMoney(entry.deductibleEstimate)}
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              )}
            </>
          )}
        </>
      )}

      {showModal && activePortfolioId !== null && (
        <FlowFormModal
          portfolioId={activePortfolioId}
          flow={editingFlow}
          onClose={() => setShowModal(false)}
        />
      )}
    </>
  );
}

function deductiblePctLabel(deductible: string, total: string): string {
  const d = Number(deductible);
  const t = Number(total);
  if (Number.isNaN(d) || Number.isNaN(t) || t === 0) {
    return 'Attributable to traced investment use';
  }
  const pct = (d / t) * 100;
  return `${pct.toFixed(1)}% attributable to traced investment use`;
}
