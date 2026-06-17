import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useCreatePortfolio, useUpdatePortfolio } from '../api/hooks';
import { ApiError } from '../api/client';
import type { CreatePortfolio, Portfolio } from '../api/types';

const PORTFOLIO_TYPES = ['Taxable', 'TFSA', 'RRSP', 'Smith Maneuver', 'Other'] as const;

interface PortfolioFormModalProps {
  portfolio?: Portfolio | null;
  onClose: () => void;
  onSaved?: (portfolio: Portfolio) => void;
}

export default function PortfolioFormModal({ portfolio, onClose, onSaved }: PortfolioFormModalProps) {
  const isEdit = portfolio != null;
  const createPortfolio = useCreatePortfolio();
  const updatePortfolio = useUpdatePortfolio();
  const pending = createPortfolio.isPending || updatePortfolio.isPending;

  const [name, setName] = useState(portfolio?.name ?? '');
  const [description, setDescription] = useState(portfolio?.description ?? '');
  const [type, setType] = useState(portfolio?.type ?? '');
  const [currency, setCurrency] = useState(portfolio?.baseCurrency ?? 'CAD');
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

    const body: CreatePortfolio = {
      name,
      description: description.trim() === '' ? null : description,
      baseCurrency: currency,
      type: type === '' ? null : type,
    };

    try {
      const saved = isEdit
        ? await updatePortfolio.mutateAsync({ id: portfolio!.id, body })
        : await createPortfolio.mutateAsync(body);
      onSaved?.(saved);
      onClose();
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors);
      }
    }
  }

  const activeMutation = isEdit ? updatePortfolio : createPortfolio;
  const generalError =
    activeMutation.isError &&
    activeMutation.error instanceof ApiError &&
    Object.keys(activeMutation.error.fieldErrors).length === 0
      ? activeMutation.error.message
      : null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal card" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <h2 style={{ margin: '0 0 1rem', fontSize: '1.125rem', fontWeight: 600 }}>
          {isEdit ? 'Edit portfolio' : 'New portfolio'}
        </h2>

        {generalError && (
          <div className="banner banner-warn" style={{ marginBottom: '1rem' }}>
            {generalError}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="portfolio-name">Name</label>
            <input
              id="portfolio-name"
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              autoFocus
            />
            <FieldError message={fieldErrors.name} />
          </div>

          <div className="form-group">
            <label htmlFor="portfolio-description">Description</label>
            <input
              id="portfolio-description"
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
            />
            <FieldError message={fieldErrors.description} />
          </div>

          <div className="form-group">
            <label htmlFor="portfolio-type">Type</label>
            <select id="portfolio-type" value={type} onChange={(e) => setType(e.target.value)}>
              <option value="">—</option>
              {PORTFOLIO_TYPES.map((option) => (
                <option key={option} value={option}>
                  {option}
                </option>
              ))}
            </select>
            <FieldError message={fieldErrors.type} />
          </div>

          <div className="form-group">
            <label htmlFor="portfolio-currency">Base currency</label>
            <input
              id="portfolio-currency"
              type="text"
              value={currency}
              onChange={(e) => setCurrency(e.target.value)}
              maxLength={3}
              required
            />
            <FieldError message={fieldErrors.baseCurrency} />
          </div>

          <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.25rem' }}>
            <button type="submit" className="btn btn-primary" disabled={pending}>
              {pending ? 'Saving…' : isEdit ? 'Save changes' : 'Create portfolio'}
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
