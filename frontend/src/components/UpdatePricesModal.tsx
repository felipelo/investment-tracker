import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { useCreatePriceSnapshots } from '../api/hooks';
import { ApiError } from '../api/client';
import type { CreatePriceSnapshot, Holding } from '../api/types';

interface UpdatePricesModalProps {
  holdings: Holding[];
  onClose: () => void;
}

function today(): string {
  return new Date().toISOString().slice(0, 10);
}

export default function UpdatePricesModal({ holdings, onClose }: UpdatePricesModalProps) {
  const createPrices = useCreatePriceSnapshots();

  const [date, setDate] = useState(today());
  const [prices, setPrices] = useState<Record<number, string>>(() =>
    Object.fromEntries(
      holdings.map((h) => [h.securityId, h.latestPrice ?? '']),
    ),
  );

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();

    const snapshots: CreatePriceSnapshot[] = holdings
      .map((h) => ({ securityId: h.securityId, date, price: prices[h.securityId]?.trim() ?? '' }))
      .filter((s) => s.price !== '');

    if (snapshots.length === 0) {
      onClose();
      return;
    }

    try {
      await createPrices.mutateAsync({ snapshots });
      onClose();
    } catch (error) {
      if (!(error instanceof ApiError)) throw error;
    }
  }

  const generalError =
    createPrices.isError && createPrices.error instanceof ApiError
      ? createPrices.error.message
      : null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal card" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <h2 style={{ margin: '0 0 1rem', fontSize: '1.125rem', fontWeight: 600 }}>
          Update prices
        </h2>

        {generalError && (
          <div className="banner banner-warn" style={{ marginBottom: '1rem' }}>
            {generalError}
          </div>
        )}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="update-prices-date">As of date</label>
            <input
              id="update-prices-date"
              type="date"
              value={date}
              onChange={(e) => setDate(e.target.value)}
              required
            />
          </div>

          <div className="table-wrap" style={{ marginTop: '0.5rem' }}>
            <table>
              <thead>
                <tr>
                  <th>Security</th>
                  <th style={{ textAlign: 'right' }}>Price</th>
                </tr>
              </thead>
              <tbody>
                {holdings.map((holding) => (
                  <tr key={holding.securityId}>
                    <td>
                      <div className="ticker">{holding.ticker}</div>
                      <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                        {holding.name}
                      </div>
                    </td>
                    <td style={{ textAlign: 'right' }}>
                      <input
                        type="number"
                        step="0.0001"
                        min="0"
                        inputMode="decimal"
                        value={prices[holding.securityId] ?? ''}
                        onChange={(e) =>
                          setPrices((prev) => ({
                            ...prev,
                            [holding.securityId]: e.target.value,
                          }))
                        }
                        style={{ maxWidth: '8rem', textAlign: 'right' }}
                      />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.25rem' }}>
            <button type="submit" className="btn btn-primary" disabled={createPrices.isPending}>
              {createPrices.isPending ? 'Saving…' : 'Save prices'}
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
