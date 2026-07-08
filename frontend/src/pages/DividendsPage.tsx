import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  useAccounts,
  useCreateDividend,
  useDeleteDividend,
  useDividends,
  useSecurities,
  useUpdateDividend,
} from '../api/hooks';
import { ApiError } from '../api/client';
import type { CreateDividend, Dividend } from '../api/types';
import { formatMoney } from '../lib/actions';
import { usePortfolioContext } from '../context/PortfolioContext';
import DividendsList from '../components/DividendsList';

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

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

interface FormState {
  paymentDate: string;
  securityId: string;
  accountId: string;
  grossAmount: string;
  withholdingTax: string;
  drip: boolean;
  notes: string;
}

const initialState: FormState = {
  paymentDate: today(),
  securityId: '',
  accountId: '',
  grossAmount: '',
  withholdingTax: '',
  drip: false,
  notes: '',
};

function computeNet(gross: string, withholding: string): string | null {
  if (String(gross).trim() === '') return null;
  const grossNum = Number(gross);
  const withholdingNum = String(withholding).trim() === '' ? 0 : Number(withholding);
  if (Number.isNaN(grossNum) || Number.isNaN(withholdingNum)) return null;
  return String(grossNum - withholdingNum);
}

export default function DividendsPage() {
  const { portfolios, activePortfolioId, activePortfolio, setActivePortfolioId } =
    usePortfolioContext();
  const securities = useSecurities();
  const accounts = useAccounts(activePortfolioId);
  const dividends = useDividends(activePortfolioId);
  const createDividend = useCreateDividend();
  const updateDividend = useUpdateDividend();
  const deleteDividend = useDeleteDividend();

  const [searchParams, setSearchParams] = useSearchParams();
  const editParam = searchParams.get('edit');
  const editId = editParam !== null && /^\d+$/.test(editParam) ? Number(editParam) : null;

  const [form, setForm] = useState<FormState>(initialState);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [saved, setSaved] = useState(false);

  const netPreview = useMemo(
    () => computeNet(form.grossAmount, form.withholdingTax),
    [form.grossAmount, form.withholdingTax],
  );

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
    setSaved(false);
  }

  function handleSecurityChange(securityId: string) {
    setForm((prev) => {
      const next = { ...prev, securityId };
      if (editingId === null && securityId !== '') {
        const latest = dividends.data?.find((d) => String(d.securityId) === securityId);
        next.grossAmount = latest ? String(latest.grossAmount) : '';
      }
      return next;
    });
    setSaved(false);
  }

  function resetForm() {
    setForm(initialState);
    setEditingId(null);
    setFieldErrors({});
    setSaved(false);
  }

  function loadForEdit(dividend: Dividend) {
    if (dividend.id === editingId) {
      resetForm();
      return;
    }
    setForm({
      paymentDate: dividend.paymentDate,
      securityId: String(dividend.securityId),
      accountId: dividend.accountId !== null ? String(dividend.accountId) : '',
      grossAmount: String(dividend.grossAmount),
      withholdingTax: String(dividend.withholdingTax),
      drip: dividend.drip,
      notes: dividend.notes ?? '',
    });
    setEditingId(dividend.id);
    setFieldErrors({});
    setSaved(false);
  }

  useEffect(() => {
    if (editId === null || editingId === editId || !dividends.data) return;
    const target = dividends.data.find((d) => d.id === editId);
    if (!target) return;
    loadForEdit(target);
    setSearchParams((params) => {
      params.delete('edit');
      return params;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editId, dividends.data]);

  function buildPayload(): CreateDividend {
    if (activePortfolioId === null) {
      throw new Error('No portfolio selected');
    }
    return {
      portfolioId: activePortfolioId,
      securityId: Number(form.securityId),
      accountId: form.accountId === '' ? null : Number(form.accountId),
      paymentDate: form.paymentDate,
      grossAmount: form.grossAmount,
      withholdingTax: form.withholdingTax.trim() === '' ? null : form.withholdingTax,
      drip: form.drip,
      notes: form.notes.trim() === '' ? null : form.notes,
    };
  }

  async function handleDelete() {
    if (editingId === null) return;
    if (!window.confirm('Delete this dividend? This cannot be undone.')) return;
    try {
      await deleteDividend.mutateAsync(editingId);
      resetForm();
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors);
      }
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (activePortfolioId === null) return;
    setFieldErrors({});
    setSaved(false);
    try {
      const body = buildPayload();
      if (editingId !== null) {
        await updateDividend.mutateAsync({ id: editingId, body });
        resetForm();
        setSaved(true);
      } else {
        await createDividend.mutateAsync(body);
        setSaved(true);
        setForm((prev) => ({ ...initialState, paymentDate: prev.paymentDate }));
      }
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors);
      }
    }
  }

  const activeMutation = editingId !== null ? updateDividend : createDividend;
  const generalError =
    activeMutation.isError &&
    activeMutation.error instanceof ApiError &&
    Object.keys(activeMutation.error.fieldErrors).length === 0
      ? activeMutation.error.message
      : null;

  return (
    <>
      <header className="page-header">
        <div>
          <h1 className="page-title">Dividends</h1>
          <p className="page-subtitle">
            {activePortfolio
              ? `${activePortfolio.name} · add, edit and delete dividend income`
              : 'Add, edit and delete dividend income'}
          </p>
        </div>
        {activePortfolio && (
          <div className="portfolio-switcher">
            <span className={`tag ${typeTagClass(activePortfolio.type)}`}>
              {activePortfolio.type ?? 'Other'}
            </span>
            <select
              value={activePortfolioId ?? ''}
              onChange={(e) => {
                resetForm();
                setActivePortfolioId(Number(e.target.value));
              }}
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

      {activePortfolioId !== null && (
        <>
          {editingId !== null && (
            <div className="banner banner-info" style={{ maxWidth: 640 }}>
              Editing dividend #{editingId}. Currency defaults to the portfolio base currency (
              {activePortfolio?.baseCurrency ?? 'CAD'}).
            </div>
          )}

          {saved && (
            <div className="banner banner-info" style={{ maxWidth: 640 }}>
              Dividend saved.
            </div>
          )}

          {generalError && (
            <div className="banner banner-warn" style={{ maxWidth: 640 }}>
              {generalError}
            </div>
          )}

          <div className="card">
            <form onSubmit={handleSubmit}>
              <div className="dividends-form-layout">
                <div className="form-grid">
                  <div className="form-group">
                    <label htmlFor="dividend-date">Payment date</label>
                    <input
                      id="dividend-date"
                      type="date"
                      value={form.paymentDate}
                      onChange={(e) => update('paymentDate', e.target.value)}
                      required
                    />
                    <FieldError message={fieldErrors.paymentDate} />
                  </div>

                  <div className="form-group">
                    <label htmlFor="dividend-security">Security</label>
                    <select
                      id="dividend-security"
                      value={form.securityId}
                      onChange={(e) => handleSecurityChange(e.target.value)}
                      disabled={securities.isPending}
                      required
                    >
                      <option value="" disabled>
                        {securities.isPending ? 'Loading…' : 'Select a security'}
                      </option>
                      {(securities.data ?? []).map((security) => (
                        <option key={security.id} value={security.id}>
                          {security.ticker} — {security.name}
                        </option>
                      ))}
                    </select>
                    <FieldError message={fieldErrors.securityId} />
                  </div>

                  <div className="form-group">
                    <label htmlFor="dividend-account">Deposit account</label>
                    <select
                      id="dividend-account"
                      value={form.accountId}
                      onChange={(e) => update('accountId', e.target.value)}
                      disabled={accounts.isPending}
                    >
                      <option value="">None (informational only)</option>
                      {(accounts.data ?? []).map((account) => (
                        <option key={account.id} value={account.id}>
                          {account.label}
                        </option>
                      ))}
                    </select>
                    <FieldError message={fieldErrors.accountId} />
                  </div>

                  <div className="form-group">
                    <label htmlFor="dividend-gross">Gross amount</label>
                    <input
                      id="dividend-gross"
                      type="number"
                      step="0.0001"
                      min="0"
                      inputMode="decimal"
                      value={form.grossAmount}
                      onChange={(e) => update('grossAmount', e.target.value)}
                      required
                    />
                    <FieldError message={fieldErrors.grossAmount} />
                  </div>

                  <div className="form-group">
                    <label htmlFor="dividend-withholding">Withholding tax</label>
                    <input
                      id="dividend-withholding"
                      type="number"
                      step="0.0001"
                      min="0"
                      inputMode="decimal"
                      value={form.withholdingTax}
                      onChange={(e) => update('withholdingTax', e.target.value)}
                    />
                    <FieldError message={fieldErrors.withholdingTax} />
                  </div>

                  <div className="form-group" style={{ gridColumn: 'span 2' }}>
                    <label htmlFor="dividend-drip" className="checkbox-label">
                      <input
                        id="dividend-drip"
                        type="checkbox"
                        checked={form.drip}
                        onChange={(e) => update('drip', e.target.checked)}
                      />
                      Reinvested (DRIP)
                    </label>
                  </div>
                </div>

                <div className="dividends-form-sidebar">
                  <div className="form-group">
                    <label htmlFor="dividend-notes">Notes</label>
                    <input
                      id="dividend-notes"
                      type="text"
                      placeholder="Optional note…"
                      value={form.notes}
                      onChange={(e) => update('notes', e.target.value)}
                    />
                  </div>

                  <div
                    className="card"
                    style={{ background: 'var(--bg)', boxShadow: 'none' }}
                  >
                    <p className="card-title">Net amount</p>
                    <p style={{ margin: 0 }}>
                      <strong className="mono" style={{ fontSize: '1.125rem' }}>
                        {netPreview !== null ? formatMoney(netPreview) : '—'}
                      </strong>
                      <span style={{ color: 'var(--text-muted)', fontSize: '0.875rem' }}>
                        {' '}
                        = gross − withholding tax
                      </span>
                    </p>
                  </div>
                </div>
              </div>

              <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.5rem' }}>
                <button
                  type="submit"
                  className="btn btn-primary"
                  disabled={activeMutation.isPending}
                >
                  {editingId !== null
                    ? updateDividend.isPending
                      ? 'Updating…'
                      : 'Update dividend'
                    : createDividend.isPending
                      ? 'Saving…'
                      : 'Save dividend'}
                </button>
                {editingId !== null && (
                  <button
                    type="button"
                    className="btn btn-danger"
                    onClick={handleDelete}
                    disabled={deleteDividend.isPending}
                  >
                    {deleteDividend.isPending ? 'Deleting…' : 'Delete'}
                  </button>
                )}
                <button type="button" className="btn btn-ghost" onClick={resetForm}>
                  {editingId !== null ? 'Cancel edit' : 'Reset'}
                </button>
              </div>
            </form>
          </div>

          <DividendsList
            portfolioId={activePortfolioId}
            selectedId={editingId}
            onSelect={loadForEdit}
          />
        </>
      )}
    </>
  );
}

function FieldError({ message }: { message?: string }) {
  if (!message) return null;
  return (
    <p className="negative" style={{ margin: '0.375rem 0 0', fontSize: '0.75rem' }}>
      {message}
    </p>
  );
}
