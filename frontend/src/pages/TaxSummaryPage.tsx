import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useTaxSummary } from '../api/hooks';
import { usePortfolioContext } from '../context/PortfolioContext';
import { formatGainLoss, formatMoney } from '../lib/actions';
import { exportTaxSummaryCsv, exportTaxSummaryJson } from '../lib/exportTax';
import type { TaxSummary } from '../api/types';

function isEmpty(summary: TaxSummary): boolean {
  return (
    summary.realizedGains.rows.length === 0 &&
    summary.dividends.rows.length === 0 &&
    summary.interest.months.length === 0
  );
}

export default function TaxSummaryPage() {
  const { activePortfolioId, activePortfolio } = usePortfolioContext();
  const [year, setYear] = useState<number | null>(null);
  const taxSummary = useTaxSummary(activePortfolioId, year);
  const data = taxSummary.data;
  const portfolioName = activePortfolio?.name ?? 'portfolio';

  const realizedGain = data ? formatGainLoss(data.realizedGains.total.gainLoss) : null;

  return (
    <>
      <header className="page-header">
        <div>
          <h1 className="page-title">Tax summary</h1>
          <p className="page-subtitle">
            {data ? `Tax year ${data.year}` : 'Tax year export view'}
            {activePortfolio ? ` · ${activePortfolio.name}` : ''}
          </p>
        </div>
      </header>

      {activePortfolioId !== null && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginBottom: '1.25rem',
          }}
        >
          {data && data.availableYears.length > 0 ? (
            <div className="portfolio-switcher">
              <span className="tag tag-butter">Tax year</span>
              <select
                value={data.year}
                onChange={(e) => setYear(Number(e.target.value))}
                aria-label="Tax year"
                style={{
                  fontFamily: 'var(--font)',
                  fontSize: '0.875rem',
                  border: 'none',
                  background: 'transparent',
                  cursor: 'pointer',
                }}
              >
                {data.availableYears.map((y) => (
                  <option key={y} value={y}>
                    {y}
                  </option>
                ))}
              </select>
            </div>
          ) : (
            <span />
          )}
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <button
              type="button"
              className="btn btn-ghost"
              disabled={!data}
              onClick={() => data && exportTaxSummaryJson(data, portfolioName)}
            >
              Export JSON
            </button>
            <button
              type="button"
              className="btn btn-primary"
              disabled={!data}
              onClick={() => data && exportTaxSummaryCsv(data, portfolioName)}
            >
              Export CSV
            </button>
          </div>
        </div>
      )}

      {activePortfolioId === null && (
        <div className="card">
          <p style={{ color: 'var(--text-muted)', margin: 0 }}>
            No portfolio selected. <Link to="/portfolios">Create a portfolio</Link> to get
            started.
          </p>
        </div>
      )}

      {activePortfolioId !== null && taxSummary.isPending && (
        <div className="card">
          <p style={{ color: 'var(--text-muted)', margin: 0 }}>Loading…</p>
        </div>
      )}

      {taxSummary.isError && (
        <div className="card">
          <p className="negative" style={{ margin: 0 }}>
            Could not load the tax summary.
          </p>
        </div>
      )}

      {data && (
        <>
          {isEmpty(data) && (
            <div className="banner banner-info" style={{ marginBottom: '1.25rem' }}>
              No taxable activity recorded for {data.year}. Record trades, dividends or HELOC
              interest to populate this summary.
            </div>
          )}

          <div className="grid-3" style={{ marginBottom: '1.25rem' }}>
            <div className="card">
              <p className="card-title">Realized capital gains</p>
              <p className={`card-value ${realizedGain?.className ?? ''}`}>
                {realizedGain?.text ?? '—'}
              </p>
            </div>
            <div className="card">
              <p className="card-title">Dividend income (net)</p>
              <p className="card-value">{formatMoney(data.dividends.total.net)}</p>
              <p className="card-meta">
                Gross {formatMoney(data.dividends.total.gross)} · WH{' '}
                {formatMoney(data.dividends.total.withholding)}
              </p>
            </div>
            <div className="card">
              <p className="card-title">Deductible interest est.</p>
              <p className="card-value">{formatMoney(data.interest.ytd.deductibleEstimate)}</p>
              <p className="card-meta">Smith Maneuver traced portion</p>
            </div>
          </div>

          <div className="card" style={{ marginBottom: '1.25rem' }}>
            <p className="card-title">Realized gains &amp; losses by security</p>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>Security</th>
                    <th>Dispositions</th>
                    <th>Proceeds</th>
                    <th>ACB disposed</th>
                    <th>Gain / loss</th>
                  </tr>
                </thead>
                <tbody>
                  {data.realizedGains.rows.length === 0 && (
                    <tr>
                      <td colSpan={5} style={{ color: 'var(--text-muted)' }}>
                        No dispositions in {data.year}.
                      </td>
                    </tr>
                  )}
                  {data.realizedGains.rows.map((row) => {
                    const gl = formatGainLoss(row.gainLoss);
                    return (
                      <tr key={row.securityId ?? row.ticker}>
                        <td className="ticker">{row.ticker}</td>
                        <td className="mono">{row.dispositions}</td>
                        <td className="mono">{formatMoney(row.proceeds)}</td>
                        <td className="mono">{formatMoney(row.acbDisposed)}</td>
                        <td className={`mono ${gl.className}`}>{gl.text}</td>
                      </tr>
                    );
                  })}
                  {data.realizedGains.rows.length > 0 && (
                    <tr style={{ fontWeight: 600 }}>
                      <td>Total</td>
                      <td className="mono">{data.realizedGains.total.dispositions}</td>
                      <td className="mono">{formatMoney(data.realizedGains.total.proceeds)}</td>
                      <td className="mono">{formatMoney(data.realizedGains.total.acbDisposed)}</td>
                      <td className={`mono ${realizedGain?.className ?? ''}`}>
                        {realizedGain?.text ?? '—'}
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>

          <div className="grid-2">
            <div className="card">
              <p className="card-title">Dividends · {data.year}</p>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Security</th>
                      <th>Gross</th>
                      <th>WH</th>
                      <th>Net</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.dividends.rows.length === 0 && (
                      <tr>
                        <td colSpan={4} style={{ color: 'var(--text-muted)' }}>
                          No dividends in {data.year}.
                        </td>
                      </tr>
                    )}
                    {data.dividends.rows.map((row) => (
                      <tr key={row.securityId ?? row.ticker}>
                        <td className="ticker">{row.ticker}</td>
                        <td className="mono">{formatMoney(row.gross)}</td>
                        <td className="mono">{formatMoney(row.withholding)}</td>
                        <td className="mono">{formatMoney(row.net)}</td>
                      </tr>
                    ))}
                    {data.dividends.rows.length > 0 && (
                      <tr style={{ fontWeight: 600 }}>
                        <td>Total</td>
                        <td className="mono">{formatMoney(data.dividends.total.gross)}</td>
                        <td className="mono">{formatMoney(data.dividends.total.withholding)}</td>
                        <td className="mono">{formatMoney(data.dividends.total.net)}</td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            <div className="card">
              <p className="card-title">Smith Maneuver · interest summary</p>
              <div className="table-wrap">
                <table>
                  <thead>
                    <tr>
                      <th>Month</th>
                      <th>Charged</th>
                      <th>Deductible est.</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.interest.months.length === 0 && (
                      <tr>
                        <td colSpan={3} style={{ color: 'var(--text-muted)' }}>
                          No HELOC interest logged in {data.year}.
                        </td>
                      </tr>
                    )}
                    {data.interest.months.map((row) => (
                      <tr key={row.month}>
                        <td>{row.month}</td>
                        <td className="mono">{formatMoney(row.charged)}</td>
                        <td className="mono">{formatMoney(row.deductibleEstimate)}</td>
                      </tr>
                    ))}
                    {data.interest.months.length > 0 && (
                      <tr style={{ fontWeight: 600 }}>
                        <td>YTD</td>
                        <td className="mono">{formatMoney(data.interest.ytd.charged)}</td>
                        <td className="mono">
                          {formatMoney(data.interest.ytd.deductibleEstimate)}
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>
          </div>

          <p className="card-meta" style={{ marginTop: '1.25rem' }}>
            A record-keeping and estimation aid, not tax advice. Confirm figures against CRA rules
            and your own circumstances.
          </p>
        </>
      )}
    </>
  );
}
