import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import {
  useAccounts,
  useCashTransactions,
  useCreateFlow,
  useDeleteFlow,
  useSecurities,
  useTransactions,
  useUpdateFlow,
} from '../api/hooks';
import { ApiError } from '../api/client';
import type {
  CashTransaction,
  CashTransactionType,
  CreateSmithManeuverFlow,
  SmithManeuverFlow,
} from '../api/types';
import { formatMoney } from '../lib/actions';
import { cashTypeMeta } from '../lib/cashTypes';

type CashLeg = CashTransaction & { id: number; type: CashTransactionType };

interface FlowFormModalProps {
  portfolioId: number;
  flow?: SmithManeuverFlow | null;
  onClose: () => void;
}

export default function FlowFormModal({ portfolioId, flow, onClose }: FlowFormModalProps) {
  const isEdit = flow != null;
  const accounts = useAccounts(portfolioId);
  const cashTransactions = useCashTransactions(portfolioId);
  const securityTransactions = useTransactions({ portfolioId });
  const securities = useSecurities();
  const createFlow = useCreateFlow();
  const updateFlow = useUpdateFlow();
  const deleteFlow = useDeleteFlow();

  const [helocAccountId, setHelocAccountId] = useState(
    flow ? String(flow.helocAccountId) : ''
  );
  const [label, setLabel] = useState(flow?.label ?? '');
  const [investmentUseAmount, setInvestmentUseAmount] = useState(
    flow?.investmentUseAmount ?? ''
  );
  const [selectedCashIds, setSelectedCashIds] = useState<number[]>(
    flow
      ? flow.steps
          .filter((s) => s.cashTransactionId !== null)
          .map((s) => s.cashTransactionId as number)
      : []
  );
  const [securityTransactionId, setSecurityTransactionId] = useState(() => {
    const buyStep = flow?.steps.find((s) => s.securityTransactionId !== null);
    return buyStep ? String(buyStep.securityTransactionId) : '';
  });
  const [notes, setNotes] = useState(flow?.notes ?? '');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  const helocAccounts = useMemo(
    () => (accounts.data ?? []).filter((a) => a.type === 'HELOC'),
    [accounts.data]
  );

  const tickerById = useMemo(() => {
    const map = new Map<number, string>();
    for (const s of securities.data ?? []) map.set(s.id, s.ticker);
    return map;
  }, [securities.data]);

  const buyTransactions = useMemo(
    () => (securityTransactions.data ?? []).filter((t) => t.action === 'BUY'),
    [securityTransactions.data]
  );

  function toggleCash(id: number) {
    setSelectedCashIds((prev) =>
      prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]
    );
  }

  function buildPayload(): CreateSmithManeuverFlow {
    return {
      portfolioId,
      helocAccountId: Number(helocAccountId),
      label: label.trim() === '' ? null : label.trim(),
      investmentUseAmount: investmentUseAmount.trim() === '' ? '0' : investmentUseAmount,
      cashTransactionIds: selectedCashIds,
      securityTransactionId: securityTransactionId === '' ? null : Number(securityTransactionId),
      notes: notes.trim() === '' ? null : notes,
    };
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFieldErrors({});
    try {
      const body = buildPayload();
      if (isEdit) {
        await updateFlow.mutateAsync({ id: flow!.id, body });
      } else {
        await createFlow.mutateAsync(body);
      }
      onClose();
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors);
      }
    }
  }

  async function handleDelete() {
    if (!isEdit) return;
    if (!window.confirm('Delete this flow? This cannot be undone.')) return;
    try {
      await deleteFlow.mutateAsync(flow!.id);
      onClose();
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors);
      }
    }
  }

  const pending = createFlow.isPending || updateFlow.isPending;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="modal card"
        onClick={(e) => e.stopPropagation()}
        role="dialog"
        aria-modal="true"
        style={{ maxWidth: '640px' }}
      >
        <h2 style={{ margin: '0 0 1rem', fontSize: '1.125rem', fontWeight: 600 }}>
          {isEdit ? 'Edit flow' : 'New flow'}
        </h2>

        <form onSubmit={handleSubmit}>
          <div className="form-grid">
            <div className="form-group">
              <label htmlFor="flow-heloc">Source HELOC</label>
              <select
                id="flow-heloc"
                value={helocAccountId}
                onChange={(e) => setHelocAccountId(e.target.value)}
                required
              >
                <option value="" disabled>
                  {helocAccounts.length === 0 ? 'No HELOC accounts' : 'Select a HELOC account'}
                </option>
                {helocAccounts.map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.label}
                  </option>
                ))}
              </select>
              <FieldError message={fieldErrors.helocAccountId} />
            </div>

            <div className="form-group">
              <label htmlFor="flow-amount">Investment-use amount</label>
              <input
                id="flow-amount"
                type="number"
                step="0.0001"
                min="0"
                inputMode="decimal"
                value={investmentUseAmount}
                onChange={(e) => setInvestmentUseAmount(e.target.value)}
                required
              />
              <FieldError message={fieldErrors.investmentUseAmount} />
            </div>

            <div className="form-group" style={{ gridColumn: 'span 2' }}>
              <label htmlFor="flow-label">Label</label>
              <input
                id="flow-label"
                type="text"
                placeholder="e.g. Flow #2024-03 — TSE:XEI purchase"
                value={label}
                onChange={(e) => setLabel(e.target.value)}
              />
            </div>
          </div>

          <div className="form-group" style={{ marginTop: '1rem' }}>
            <label>Chain — pick cash legs in order (draw → transfers)</label>
            <div
              style={{
                maxHeight: '200px',
                overflowY: 'auto',
                border: '1px solid var(--border-strong)',
                borderRadius: 'var(--radius-sm)',
              }}
            >
              {(cashTransactions.data ?? []).length === 0 && (
                <p style={{ padding: '0.75rem', margin: 0, color: 'var(--text-muted)' }}>
                  No cash transactions in this portfolio yet.
                </p>
              )}
              {(cashTransactions.data ?? [])
                .filter((tx): tx is CashLeg => tx.source === 'CASH' && tx.id !== null)
                .map((tx) => {
                const order = selectedCashIds.indexOf(tx.id);
                const selected = order !== -1;
                return (
                  <div
                    key={tx.id}
                    role="button"
                    tabIndex={0}
                    onClick={() => toggleCash(tx.id)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter' || e.key === ' ') {
                        e.preventDefault();
                        toggleCash(tx.id);
                      }
                    }}
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '0.625rem',
                      padding: '0.5rem 0.75rem',
                      cursor: 'pointer',
                      background: selected ? 'var(--sage)' : 'transparent',
                      borderBottom: '1px solid var(--border)',
                      fontSize: '0.8125rem',
                    }}
                  >
                    <span
                      className="mono"
                      style={{
                        minWidth: '1.25rem',
                        fontWeight: 600,
                        color: selected ? 'var(--text)' : 'var(--text-faint)',
                      }}
                    >
                      {selected ? order + 1 : '·'}
                    </span>
                    <span style={{ flex: 1 }}>
                      <span className="mono">{tx.date}</span> · {cashTypeMeta(tx.type).label}
                      {tx.counterpartyAccountLabel
                        ? ` · ${tx.accountLabel} → ${tx.counterpartyAccountLabel}`
                        : ` · ${tx.accountLabel}`}
                    </span>
                    <span className="mono">{formatMoney(String(Math.abs(Number(tx.amount))))}</span>
                  </div>
                );
              })}
            </div>
            <FieldError message={fieldErrors.cashTransactionIds} />
          </div>

          <div className="form-group" style={{ marginTop: '1rem' }}>
            <label htmlFor="flow-buy">Terminal buy (optional)</label>
            <select
              id="flow-buy"
              value={securityTransactionId}
              onChange={(e) => setSecurityTransactionId(e.target.value)}
            >
              <option value="">No purchase linked</option>
              {buyTransactions.map((tx) => (
                <option key={tx.id} value={tx.id}>
                  {tx.date} · {tickerById.get(tx.securityId) ?? `#${tx.securityId}`}
                  {tx.shares && tx.pricePerShare ? ` · ${tx.shares} @ ${tx.pricePerShare}` : ''}
                </option>
              ))}
            </select>
            <FieldError message={fieldErrors.securityTransactionId} />
          </div>

          <div className="form-group" style={{ marginTop: '1rem' }}>
            <label htmlFor="flow-notes">Notes</label>
            <input
              id="flow-notes"
              type="text"
              placeholder="Optional note…"
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
            />
          </div>

          <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.25rem' }}>
            <button type="submit" className="btn btn-primary" disabled={pending}>
              {pending ? 'Saving…' : isEdit ? 'Save changes' : 'Create flow'}
            </button>
            {isEdit && (
              <button
                type="button"
                className="btn btn-danger"
                onClick={handleDelete}
                disabled={deleteFlow.isPending}
              >
                {deleteFlow.isPending ? 'Deleting…' : 'Delete'}
              </button>
            )}
            <button type="button" className="btn btn-ghost" onClick={onClose}>
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
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
