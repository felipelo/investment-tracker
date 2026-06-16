import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useCreateAccount } from '../api/hooks';
import { ApiError } from '../api/client';
import type { Account, CreateAccount } from '../api/types';

const ACCOUNT_TYPES = ['Chequing', 'Investment (cash)', 'Margin', 'HELOC', 'Other'] as const;

interface AddAccountModalProps {
  onClose: () => void;
  onCreated: (account: Account) => void;
}

export default function AddAccountModal({ onClose, onCreated }: AddAccountModalProps) {
  const createAccount = useCreateAccount();

  const [label, setLabel] = useState('');
  const [type, setType] = useState('');
  const [currency, setCurrency] = useState('CAD');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFieldErrors({});

    const body: CreateAccount = {
      label,
      type,
      currency,
    };

    try {
      const created = await createAccount.mutateAsync(body);
      onCreated(created);
      onClose();
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors);
      }
    }
  }

  const generalError =
    createAccount.isError &&
    createAccount.error instanceof ApiError &&
    Object.keys(createAccount.error.fieldErrors).length === 0
      ? createAccount.error.message
      : null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal card" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <h2 style={{ margin: '0 0 1rem', fontSize: '1.125rem', fontWeight: 600 }}>Add account</h2>

        {generalError && (
          <div className="banner banner-warn" style={{ marginBottom: '1rem' }}>
            {generalError}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="add-account-label">Label</label>
            <input
              id="add-account-label"
              type="text"
              value={label}
              onChange={(e) => setLabel(e.target.value)}
              required
              autoFocus
            />
            <FieldError message={fieldErrors.label} />
          </div>

          <div className="form-group">
            <label htmlFor="add-account-type">Type</label>
            <select
              id="add-account-type"
              value={type}
              onChange={(e) => setType(e.target.value)}
              required
            >
              <option value="">—</option>
              {ACCOUNT_TYPES.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
            <FieldError message={fieldErrors.type} />
          </div>

          <div className="form-group">
            <label htmlFor="add-account-currency">Currency</label>
            <input
              id="add-account-currency"
              type="text"
              value={currency}
              onChange={(e) => setCurrency(e.target.value)}
              maxLength={3}
              required
            />
            <FieldError message={fieldErrors.currency} />
          </div>

          <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.25rem' }}>
            <button type="submit" className="btn btn-primary" disabled={createAccount.isPending}>
              {createAccount.isPending ? 'Saving…' : 'Add account'}
            </button>
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
