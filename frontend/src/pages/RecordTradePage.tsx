import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  useAccounts,
  useCreateTransaction,
  useDeleteTransaction,
  useSecurities,
  useTransaction,
  useUpdateTransaction,
} from '../api/hooks';
import { ApiError } from '../api/client';
import type { Action, CreateSecurityTransaction, SecurityTransaction } from '../api/types';
import { ACTIONS, actionMeta, fieldGroupFor } from '../lib/actions';
import { usePortfolioContext } from '../context/PortfolioContext';
import AddSecurityModal from '../components/AddSecurityModal';
import AccountFormModal from '../components/AccountFormModal';
import RecentTransactions from '../components/RecentTransactions';

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

interface FormState {
  date: string;
  securityId: string;
  accountId: string;
  action: Action;
  shares: string;
  pricePerShare: string;
  commission: string;
  cashAmount: string;
  splitRatio: string;
  deniedLossAdjustment: string;
  notes: string;
}

const initialState: FormState = {
  date: today(),
  securityId: '',
  accountId: '',
  action: 'BUY',
  shares: '',
  pricePerShare: '',
  commission: '',
  cashAmount: '',
  splitRatio: '',
  deniedLossAdjustment: '',
  notes: '',
};

export default function RecordTradePage() {
  const { activePortfolioId, activePortfolio } = usePortfolioContext();
  const securities = useSecurities();
  const accounts = useAccounts(activePortfolioId);
  const createTransaction = useCreateTransaction();
  const updateTransaction = useUpdateTransaction();
  const deleteTransaction = useDeleteTransaction();

  const [searchParams, setSearchParams] = useSearchParams();
  const editParam = searchParams.get('edit');
  const editId = editParam !== null && /^\d+$/.test(editParam) ? Number(editParam) : null;
  const editTransaction = useTransaction(editId);

  const [form, setForm] = useState<FormState>(initialState);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [saved, setSaved] = useState(false);
  const [showAddSecurity, setShowAddSecurity] = useState(false);
  const [showAddAccount, setShowAddAccount] = useState(false);

  const group = fieldGroupFor(form.action);

  function update<K extends keyof FormState>(key: K, value: FormState[K]) {
    setForm((prev) => ({ ...prev, [key]: value }));
    setSaved(false);
  }

  function resetForm() {
    setForm(initialState);
    setEditingId(null);
    setFieldErrors({});
    setSaved(false);
  }

  function loadForEdit(tx: SecurityTransaction) {
    if (tx.id === editingId) {
      resetForm();
      return;
    }
    setForm({
      date: tx.date,
      securityId: String(tx.securityId),
      accountId: tx.accountId !== null ? String(tx.accountId) : '',
      action: tx.action,
      shares: tx.shares ?? '',
      pricePerShare: tx.pricePerShare ?? '',
      commission: tx.commission ?? '',
      cashAmount: tx.cashAmount ?? '',
      splitRatio: tx.splitRatio ?? '',
      deniedLossAdjustment: tx.deniedLossAdjustment ?? '',
      notes: tx.notes ?? '',
    });
    setEditingId(tx.id);
    setFieldErrors({});
    setSaved(false);
  }

  useEffect(() => {
    if (editId === null || editingId === editId || !editTransaction.data) return;
    loadForEdit(editTransaction.data);
    setSearchParams((params) => {
      params.delete('edit');
      return params;
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [editId, editTransaction.data]);

  async function handleDelete() {
    if (editingId === null) return;
    if (!window.confirm('Delete this transaction? This cannot be undone.')) return;
    try {
      await deleteTransaction.mutateAsync(editingId);
      resetForm();
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors);
      }
    }
  }

  function buildPayload(): CreateSecurityTransaction {
    const base: CreateSecurityTransaction = {
      date: form.date,
      securityId: Number(form.securityId),
      action: form.action,
      accountId: Number(form.accountId),
      notes: form.notes || null,
    };
    if (group === 'trade') {
      base.shares = form.shares || null;
      base.pricePerShare = form.pricePerShare || null;
      base.commission = form.commission || '0';
      if (form.action === 'SELL') {
        base.deniedLossAdjustment = form.deniedLossAdjustment || null;
      }
    } else if (group === 'cash') {
      base.cashAmount = form.cashAmount || null;
    } else if (group === 'split') {
      base.splitRatio = form.splitRatio || null;
    }
    return base;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFieldErrors({});
    setSaved(false);
    try {
      if (editingId !== null) {
        await updateTransaction.mutateAsync({ id: editingId, body: buildPayload() });
        resetForm();
        setSaved(true);
      } else {
        await createTransaction.mutateAsync(buildPayload());
        setSaved(true);
        setForm((prev) => ({ ...initialState, date: prev.date, action: prev.action }));
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

  return (
    <>
      <header className="page-header">
        <div>
          <h1 className="page-title">Record trade</h1>
          <p className="page-subtitle">
            {activePortfolio ? `${activePortfolio.name} · ` : ''}Security transaction · acb-tracker
            action model
          </p>
        </div>
      </header>

      {editingId !== null && (
        <div className="banner banner-info" style={{ maxWidth: 640 }}>
          Editing transaction #{editingId}.
        </div>
      )}

      {saved && (
        <div className="banner banner-info" style={{ maxWidth: 640 }}>
          Transaction saved.
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
              <label>Date</label>
              <input
                type="date"
                value={form.date}
                onChange={(e) => update('date', e.target.value)}
              />
              <FieldError message={fieldErrors.date} />
            </div>

            <div className="form-group">
              <label>Security</label>
              <div className="inline-row">
                <select
                  value={form.securityId}
                  onChange={(e) => update('securityId', e.target.value)}
                  disabled={securities.isPending}
                >
                  <option value="">
                    {securities.isPending ? 'Loading…' : 'Select a security'}
                  </option>
                  {(securities.data ?? []).map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.ticker} — {s.name}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  className="btn btn-ghost"
                  style={{ padding: '0.5rem 0.75rem', flexShrink: 0 }}
                  aria-label="Add security"
                  onClick={() => setShowAddSecurity(true)}
                >
                  +
                </button>
              </div>
              <FieldError message={fieldErrors.securityId} />
            </div>

            <div className="form-group" style={{ gridColumn: 'span 2' }}>
              <label>Action</label>
              <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
                {ACTIONS.map((a) => {
                  const selected = form.action === a.value;
                  return (
                    <span
                      key={a.value}
                      role="button"
                      tabIndex={0}
                      onClick={() => update('action', a.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          e.preventDefault();
                          update('action', a.value);
                        }
                      }}
                      className={`tag ${selected ? a.tagClass : ''}`}
                      style={{
                        padding: '0.5rem 0.875rem',
                        cursor: 'pointer',
                        background: selected ? undefined : 'var(--bg-subtle)',
                      }}
                    >
                      {a.label}
                    </span>
                  );
                })}
              </div>
            </div>

            {group === 'trade' && (
              <>
                <div className="form-group">
                  <label>Shares</label>
                  <input
                    type="number"
                    min="0"
                    step="any"
                    value={form.shares}
                    onChange={(e) => update('shares', e.target.value)}
                  />
                  <FieldError message={fieldErrors.shares} />
                </div>
                <div className="form-group">
                  <label>Price per share</label>
                  <input
                    type="number"
                    min="0"
                    step="0.0001"
                    inputMode="decimal"
                    value={form.pricePerShare}
                    onChange={(e) => update('pricePerShare', e.target.value)}
                  />
                  <FieldError message={fieldErrors.pricePerShare} />
                </div>
                <div className="form-group">
                  <label>Commission</label>
                  <input
                    type="number"
                    min="0"
                    step="0.01"
                    value={form.commission}
                    onChange={(e) => update('commission', e.target.value)}
                  />
                  <FieldError message={fieldErrors.commission} />
                </div>
                {form.action === 'SELL' && (
                  <div className="form-group">
                    <label>Denied loss adjustment</label>
                    <input
                      type="number"
                      min="0"
                      step="0.01"
                      placeholder="Superficial loss add-back"
                      value={form.deniedLossAdjustment}
                      onChange={(e) => update('deniedLossAdjustment', e.target.value)}
                    />
                    <FieldError message={fieldErrors.deniedLossAdjustment} />
                  </div>
                )}
              </>
            )}

            {group === 'cash' && (
              <div className="form-group">
                <label>Cash amount</label>
                <input
                  type="number"
                  min="0"
                  step="0.01"
                  value={form.cashAmount}
                  onChange={(e) => update('cashAmount', e.target.value)}
                />
                <FieldError message={fieldErrors.cashAmount} />
              </div>
            )}

            {group === 'split' && (
              <div className="form-group">
                <label>Split ratio</label>
                <input
                  type="number"
                  min="0"
                  step="any"
                  placeholder="e.g. 2 for 2:1"
                  value={form.splitRatio}
                  onChange={(e) => update('splitRatio', e.target.value)}
                />
                <FieldError message={fieldErrors.splitRatio} />
              </div>
            )}

            <div className="form-group">
              <label>Account</label>
              <div className="inline-row">
                <select
                  value={form.accountId}
                  onChange={(e) => update('accountId', e.target.value)}
                  disabled={accounts.isPending}
                  required
                >
                  <option value="">
                    {accounts.isPending ? 'Loading…' : 'Select an account'}
                  </option>
                  {(accounts.data ?? []).map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.label}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  className="btn btn-ghost"
                  style={{ padding: '0.5rem 0.75rem', flexShrink: 0 }}
                  aria-label="Add account"
                  onClick={() => setShowAddAccount(true)}
                  disabled={activePortfolioId === null}
                >
                  +
                </button>
              </div>
              <FieldError message={fieldErrors.accountId} />
            </div>

            </div>

            <div className="dividends-form-sidebar">
              <div className="form-group">
                <label>Notes</label>
                <textarea
                  rows={4}
                  placeholder="Audit trail…"
                  value={form.notes}
                  onChange={(e) => update('notes', e.target.value)}
                />
              </div>

              <div
                className="card"
                style={{ background: 'var(--bg)', boxShadow: 'none' }}
              >
                <p className="card-title">Computed preview</p>
                <p style={{ margin: 0, color: 'var(--text-muted)', fontSize: '0.875rem' }}>
                  ACB calculations (ACB change, total ACB, share balance, ACB/share)
                  will appear here once the backend ACB engine is implemented.
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
            <button
              type="button"
              className="btn btn-ghost"
              onClick={resetForm}
            >
              {editingId !== null ? 'Cancel edit' : 'Reset'}
            </button>
            <span style={{ alignSelf: 'center', color: 'var(--text-muted)', fontSize: '0.8125rem' }}>
              {actionMeta(form.action).label}
            </span>
          </div>
        </form>
      </div>

      <RecentTransactions
        portfolioId={activePortfolioId}
        selectedId={editingId}
        onSelect={loadForEdit}
      />

      {showAddSecurity && (
        <AddSecurityModal
          onClose={() => setShowAddSecurity(false)}
          onCreated={(security) => update('securityId', String(security.id))}
        />
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
