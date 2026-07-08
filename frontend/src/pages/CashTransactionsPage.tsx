import { useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { Link } from 'react-router-dom';
import {
  useAccounts,
  useCreateCashTransaction,
  useDeleteCashTransaction,
  useUpdateCashTransaction,
} from '../api/hooks';
import { ApiError } from '../api/client';
import type {
  CashPurpose,
  CashTransaction,
  CashTransactionType,
  CreateCashTransaction,
} from '../api/types';
import { formatGainLoss } from '../lib/actions';
import { CASH_TYPES, PURPOSES, requiresCounterparty } from '../lib/cashTypes';
import { usePortfolioContext } from '../context/PortfolioContext';
import AccountFormModal from '../components/AccountFormModal';
import CashTransactionsList from '../components/CashTransactionsList';

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
  date: string;
  accountId: string;
  type: CashTransactionType;
  amount: string;
  purpose: CashPurpose | '';
  counterpartyAccountId: string;
  notes: string;
}

const initialState: FormState = {
  date: today(),
  accountId: '',
  type: 'DEPOSIT',
  amount: '',
  purpose: '',
  counterpartyAccountId: '',
  notes: '',
};

// Mirrors the backend sign convention for single-leg types so the preview matches what is stored.
function signedAmount(type: CashTransactionType, amount: string): string | null {
  if (amount.trim() === '') return null;
  const num = Number(amount);
  if (Number.isNaN(num)) return null;
  switch (type) {
    case 'WITHDRAWAL':
    case 'FEE':
    case 'INTEREST_CHARGE':
    case 'INTEREST_PAYMENT':
      return String(-Math.abs(num));
    default:
      return String(Math.abs(num));
  }
}

export default function CashTransactionsPage() {
  const { portfolios, activePortfolioId, activePortfolio, setActivePortfolioId } =
    usePortfolioContext();
  const accounts = useAccounts(activePortfolioId);
  const createTransaction = useCreateCashTransaction();
  const updateTransaction = useUpdateCashTransaction();
  const deleteTransaction = useDeleteCashTransaction();

  const [form, setForm] = useState<FormState>(initialState);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [saved, setSaved] = useState(false);
  const [showAddAccount, setShowAddAccount] = useState(false);

  const needsCounterparty = requiresCounterparty(form.type);

  const accountLabel = useMemo(() => {
    const map = new Map((accounts.data ?? []).map((a) => [String(a.id), a.label]));
    return (id: string) => map.get(id) ?? '—';
  }, [accounts.data]);

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
    setSaved(false);
  }

  function selectType(type: CashTransactionType) {
    setForm((prev) => ({
      ...prev,
      type,
      counterpartyAccountId: requiresCounterparty(type) ? prev.counterpartyAccountId : '',
    }));
    setSaved(false);
  }

  function resetForm() {
    setForm(initialState);
    setEditingId(null);
    setFieldErrors({});
    setSaved(false);
  }

  function loadForEdit(transaction: CashTransaction) {
    if (transaction.source !== 'CASH' || transaction.id === null) {
      return;
    }
    if (transaction.id === editingId) {
      resetForm();
      return;
    }
    setForm({
      date: transaction.date,
      accountId: String(transaction.accountId),
      type: transaction.type ?? initialState.type,
      amount: String(Math.abs(Number(transaction.amount))),
      purpose: transaction.purpose ?? '',
      counterpartyAccountId:
        transaction.counterpartyAccountId !== null
          ? String(transaction.counterpartyAccountId)
          : '',
      notes: transaction.notes ?? '',
    });
    setEditingId(transaction.id);
    setFieldErrors({});
    setSaved(false);
  }

  function buildPayload(): CreateCashTransaction {
    return {
      accountId: Number(form.accountId),
      type: form.type,
      date: form.date,
      amount: form.amount,
      purpose: form.purpose === '' ? null : form.purpose,
      counterpartyAccountId: needsCounterparty ? Number(form.counterpartyAccountId) : null,
      notes: form.notes.trim() === '' ? null : form.notes,
    };
  }

  async function handleDelete() {
    if (editingId === null) return;
    if (!window.confirm('Delete this cash transaction? This cannot be undone.')) return;
    try {
      await deleteTransaction.mutateAsync(editingId);
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
        await updateTransaction.mutateAsync({ id: editingId, body });
        resetForm();
        setSaved(true);
      } else {
        await createTransaction.mutateAsync(body);
        setSaved(true);
        setForm((prev) => ({ ...initialState, date: prev.date }));
      }
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors);
      }
    }
  }

  const activeMutation = editingId !== null ? updateTransaction : createTransaction;
  const generalError =
    activeMutation.isError &&
    activeMutation.error instanceof ApiError &&
    Object.keys(activeMutation.error.fieldErrors).length === 0
      ? activeMutation.error.message
      : null;

  const previewSigned = signedAmount(form.type, form.amount);

  return (
    <>
      <header className="page-header">
        <div>
          <h1 className="page-title">Cash transactions</h1>
          <p className="page-subtitle">
            {activePortfolio
              ? `${activePortfolio.name} · money flow — deposits, transfers, HELOC, interest`
              : 'Money flow — deposits, transfers, HELOC, interest'}
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
            No portfolio selected. <Link to="/portfolios">Create a portfolio</Link> to get started.
          </p>
        </div>
      )}

      {activePortfolioId !== null && (
        <>
          {editingId !== null && (
            <div className="banner banner-info">Editing cash transaction #{editingId}.</div>
          )}

          {saved && <div className="banner banner-info">Cash transaction saved.</div>}

          {generalError && <div className="banner banner-warn">{generalError}</div>}

          <div className="card">
            <form onSubmit={handleSubmit}>
              <div className="dividends-form-layout">
                <div className="form-grid">
                  <div className="form-group">
                    <label htmlFor="cash-date">Date</label>
                    <input
                      id="cash-date"
                      type="date"
                      value={form.date}
                      onChange={(e) => update('date', e.target.value)}
                      required
                    />
                    <FieldError message={fieldErrors.date} />
                  </div>

                  <div className="form-group">
                    <label htmlFor="cash-account">Account</label>
                    <div className="inline-row">
                      <select
                        id="cash-account"
                        value={form.accountId}
                        onChange={(e) => update('accountId', e.target.value)}
                        disabled={accounts.isPending}
                        required
                      >
                        <option value="" disabled>
                          {accounts.isPending ? 'Loading…' : 'Select an account'}
                        </option>
                        {(accounts.data ?? []).map((account) => (
                          <option key={account.id} value={account.id}>
                            {account.label}
                          </option>
                        ))}
                      </select>
                      <button
                        type="button"
                        className="btn btn-ghost"
                        onClick={() => setShowAddAccount(true)}
                        title="Add account"
                      >
                        +
                      </button>
                    </div>
                    <FieldError message={fieldErrors.accountId} />
                  </div>

                  <div className="form-group" style={{ gridColumn: 'span 2' }}>
                    <label>Type</label>
                    <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                      {CASH_TYPES.map((t) => {
                        const selected = form.type === t.value;
                        return (
                          <span
                            key={t.value}
                            role="button"
                            tabIndex={0}
                            onClick={() => selectType(t.value)}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter' || e.key === ' ') {
                                e.preventDefault();
                                selectType(t.value);
                              }
                            }}
                            className={`tag ${selected ? t.tagClass : ''}`}
                            style={{
                              padding: '0.5rem 0.875rem',
                              cursor: 'pointer',
                              background: selected ? undefined : 'var(--bg-subtle)',
                            }}
                          >
                            {t.label}
                          </span>
                        );
                      })}
                    </div>
                  </div>

                  <div className="form-group">
                    <label htmlFor="cash-amount">Amount</label>
                    <input
                      id="cash-amount"
                      type="number"
                      step="0.0001"
                      min="0"
                      inputMode="decimal"
                      value={form.amount}
                      onChange={(e) => update('amount', e.target.value)}
                      required
                    />
                    <FieldError message={fieldErrors.amount} />
                  </div>

                  {needsCounterparty && (
                    <div className="form-group">
                      <label htmlFor="cash-counterparty">Counterparty account</label>
                      <select
                        id="cash-counterparty"
                        value={form.counterpartyAccountId}
                        onChange={(e) => update('counterpartyAccountId', e.target.value)}
                        required
                      >
                        <option value="" disabled>
                          Select an account
                        </option>
                        {(accounts.data ?? [])
                          .filter((account) => String(account.id) !== form.accountId)
                          .map((account) => (
                            <option key={account.id} value={account.id}>
                              {account.label}
                            </option>
                          ))}
                      </select>
                      <FieldError message={fieldErrors.counterpartyAccountId} />
                    </div>
                  )}
                </div>

                <div className="dividends-form-sidebar">
                  <div className="form-group">
                    <label>Purpose</label>
                    <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                      {PURPOSES.map((p) => {
                        const selected = form.purpose === p.value;
                        return (
                          <span
                            key={p.value}
                            role="button"
                            tabIndex={0}
                            onClick={() => update('purpose', selected ? '' : p.value)}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter' || e.key === ' ') {
                                e.preventDefault();
                                update('purpose', selected ? '' : p.value);
                              }
                            }}
                            className={`tag ${selected ? p.tagClass : ''}`}
                            style={{
                              padding: '0.5rem 0.875rem',
                              cursor: 'pointer',
                              background: selected ? undefined : 'var(--bg-subtle)',
                            }}
                          >
                            {p.label}
                          </span>
                        );
                      })}
                    </div>
                  </div>

                  <div className="form-group">
                    <label htmlFor="cash-notes">Notes</label>
                    <input
                      id="cash-notes"
                      type="text"
                      placeholder="Optional note…"
                      value={form.notes}
                      onChange={(e) => update('notes', e.target.value)}
                    />
                  </div>

                  <div className="card" style={{ background: 'var(--bg)', boxShadow: 'none' }}>
                    <p className="card-title">{needsCounterparty ? 'Double-entry preview' : 'Preview'}</p>
                    {needsCounterparty ? (
                      <div className="grid-2" style={{ fontSize: '0.875rem' }}>
                        <div>
                          <span style={{ color: 'var(--text-muted)' }}>
                            {form.accountId ? accountLabel(form.accountId) : 'Source'}
                          </span>
                          <br />
                          <strong className="mono negative">
                            {form.amount ? formatGainLoss(String(-Math.abs(Number(form.amount)))).text : '—'}
                          </strong>
                        </div>
                        <div>
                          <span style={{ color: 'var(--text-muted)' }}>
                            {form.counterpartyAccountId
                              ? accountLabel(form.counterpartyAccountId)
                              : 'Counterparty'}
                          </span>
                          <br />
                          <strong className="mono positive">
                            {form.amount ? formatGainLoss(String(Math.abs(Number(form.amount)))).text : '—'}
                          </strong>
                        </div>
                      </div>
                    ) : (
                      <p style={{ margin: 0 }}>
                        <strong
                          className={`mono ${previewSigned ? formatGainLoss(previewSigned).className : ''}`}
                          style={{ fontSize: '1.125rem' }}
                        >
                          {previewSigned ? formatGainLoss(previewSigned).text : '—'}
                        </strong>
                        <span style={{ color: 'var(--text-muted)', fontSize: '0.875rem' }}>
                          {' '}
                          applied to {form.accountId ? accountLabel(form.accountId) : 'the account'}
                        </span>
                      </p>
                    )}
                  </div>
                </div>
              </div>

              <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.5rem' }}>
                <button type="submit" className="btn btn-primary" disabled={activeMutation.isPending}>
                  {editingId !== null
                    ? updateTransaction.isPending
                      ? 'Updating…'
                      : 'Update transaction'
                    : createTransaction.isPending
                      ? 'Saving…'
                      : 'Save transaction'}
                </button>
                {editingId !== null && (
                  <button
                    type="button"
                    className="btn btn-danger"
                    onClick={handleDelete}
                    disabled={deleteTransaction.isPending}
                  >
                    {deleteTransaction.isPending ? 'Deleting…' : 'Delete'}
                  </button>
                )}
                <button type="button" className="btn btn-ghost" onClick={resetForm}>
                  {editingId !== null ? 'Cancel edit' : 'Reset'}
                </button>
              </div>
            </form>
          </div>

          <CashTransactionsList
            portfolioId={activePortfolioId}
            selectedId={editingId}
            onSelect={loadForEdit}
          />
        </>
      )}

      {showAddAccount && activePortfolioId !== null && (
        <AccountFormModal
          portfolioId={activePortfolioId}
          onClose={() => setShowAddAccount(false)}
          onSaved={(account) => update('accountId', String(account.id))}
        />
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
