import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useCreateSecurity } from '../api/hooks';
import { ApiError } from '../api/client';
import type { CreateSecurity, Security } from '../api/types';

const ASSET_CLASSES = ['Equity', 'ETF', 'Fixed Income', 'Cash', 'Other'] as const;

interface AddSecurityModalProps {
  onClose: () => void;
  onCreated: (security: Security) => void;
}

export default function AddSecurityModal({ onClose, onCreated }: AddSecurityModalProps) {
  const createSecurity = useCreateSecurity();

  const [ticker, setTicker] = useState('');
  const [name, setName] = useState('');
  const [assetClass, setAssetClass] = useState('');
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

    const body: CreateSecurity = {
      ticker,
      name,
      currency,
      assetClass: assetClass || null,
    };

    try {
      const created = await createSecurity.mutateAsync(body);
      onCreated(created);
      onClose();
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors);
      }
    }
  }

  const generalError =
    createSecurity.isError &&
    createSecurity.error instanceof ApiError &&
    Object.keys(createSecurity.error.fieldErrors).length === 0
      ? createSecurity.error.message
      : null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal card" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <h2 style={{ margin: '0 0 1rem', fontSize: '1.125rem', fontWeight: 600 }}>Add security</h2>

        {generalError && (
          <div className="banner banner-warn" style={{ marginBottom: '1rem' }}>
            {generalError}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="add-security-ticker">Ticker</label>
            <input
              id="add-security-ticker"
              type="text"
              value={ticker}
              onChange={(e) => setTicker(e.target.value)}
              required
              autoFocus
            />
            <FieldError message={fieldErrors.ticker} />
          </div>

          <div className="form-group">
            <label htmlFor="add-security-name">Name</label>
            <input
              id="add-security-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
            <FieldError message={fieldErrors.name} />
          </div>

          <div className="form-group">
            <label htmlFor="add-security-asset-class">Asset class</label>
            <select
              id="add-security-asset-class"
              value={assetClass}
              onChange={(e) => setAssetClass(e.target.value)}
            >
              <option value="">—</option>
              {ASSET_CLASSES.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
            <FieldError message={fieldErrors.assetClass} />
          </div>

          <div className="form-group">
            <label htmlFor="add-security-currency">Currency</label>
            <input
              id="add-security-currency"
              type="text"
              value={currency}
              onChange={(e) => setCurrency(e.target.value)}
              maxLength={3}
              required
            />
            <FieldError message={fieldErrors.currency} />
          </div>

          <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.25rem' }}>
            <button type="submit" className="btn btn-primary" disabled={createSecurity.isPending}>
              {createSecurity.isPending ? 'Saving…' : 'Add security'}
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
