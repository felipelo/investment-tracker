import { useEffect } from 'react';
import type { Account } from '../api/types';
import AccountForm from './AccountForm';

interface AccountFormModalProps {
  portfolioId: number;
  account?: Account | null;
  onClose: () => void;
  onSaved?: (account: Account) => void;
}

export default function AccountFormModal({
  portfolioId,
  account,
  onClose,
  onSaved,
}: AccountFormModalProps) {
  const isEdit = account != null;

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onClose();
    }
    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal card" onClick={(e) => e.stopPropagation()} role="dialog" aria-modal="true">
        <h2 style={{ margin: '0 0 1rem', fontSize: '1.125rem', fontWeight: 600 }}>
          {isEdit ? 'Edit account' : 'New account'}
        </h2>

        <AccountForm
          portfolioId={portfolioId}
          account={account}
          onSaved={(saved) => {
            onSaved?.(saved);
            onClose();
          }}
          onCancel={onClose}
        />
      </div>
    </div>
  );
}
