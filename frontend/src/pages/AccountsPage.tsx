import { useEffect, useMemo, useState } from 'react';
import { useAccounts, useDeleteAccount } from '../api/hooks';
import { ApiError } from '../api/client';
import type { Account } from '../api/types';
import { formatMoney } from '../lib/actions';
import { usePortfolioContext } from '../context/PortfolioContext';
import AccountForm, { hasCreditLine } from '../components/AccountForm';

const ACCOUNT_TYPE_TAG: Record<string, string> = {
  Chequing: 'tag-sky',
  'Investment (cash)': 'tag-peach',
  Margin: 'tag-sage',
  HELOC: 'tag-lavender',
  Other: 'tag-butter',
};

function typeTagClass(type: string | null): string {
  return (type && ACCOUNT_TYPE_TAG[type]) || 'tag-sage';
}

function formatRate(value: string | null): string {
  if (value === null || value === '') return '—';
  const num = Number(value);
  return Number.isNaN(num) ? value : `${num}%`;
}

function creditDrawn(account: Account): number {
  const balance = Number(account.currentBalance);
  return Number.isNaN(balance) ? 0 : Math.max(0, balance);
}

function creditAvailable(account: Account): string | null {
  if (account.creditLimit === null) return null;
  const limit = Number(account.creditLimit);
  if (Number.isNaN(limit)) return null;
  return String(Math.max(0, limit - creditDrawn(account)));
}

type PanelMode = { kind: 'closed' } | { kind: 'create' } | { kind: 'edit'; id: number };

export default function AccountsPage() {
  const { activePortfolioId, activePortfolio } = usePortfolioContext();
  const accounts = useAccounts(activePortfolioId);
  const deleteAccount = useDeleteAccount();

  const [panel, setPanel] = useState<PanelMode>({ kind: 'closed' });
  const [deleteError, setDeleteError] = useState<string | null>(null);

  const list = useMemo(() => accounts.data ?? [], [accounts.data]);

  const selectedAccount =
    panel.kind === 'edit' ? list.find((account) => account.id === panel.id) ?? null : null;

  // Close the panel if the selected account disappears (deleted or portfolio switch).
  useEffect(() => {
    if (panel.kind === 'edit' && !list.some((account) => account.id === panel.id)) {
      setPanel({ kind: 'closed' });
    }
  }, [list, panel]);

  const subtitle = activePortfolio
    ? `${activePortfolio.name} · cash & securities containers`
    : 'Cash & securities containers';

  async function handleDelete(account: Account) {
    setDeleteError(null);
    if (!window.confirm(`Delete account "${account.label}"?`)) return;
    try {
      await deleteAccount.mutateAsync(account.id);
    } catch (error) {
      if (error instanceof ApiError) {
        setDeleteError(error.fieldErrors.account ?? error.message);
      }
    }
  }

  return (
    <>
      <header className="page-header">
        <div>
          <h1 className="page-title">Accounts</h1>
          <p className="page-subtitle">{subtitle}</p>
        </div>
        <button
          className="btn btn-primary"
          onClick={() => setPanel({ kind: 'create' })}
          disabled={activePortfolioId === null}
        >
          New account
        </button>
      </header>

      {accounts.isPending && (
        <div className="card">
          <p style={{ color: 'var(--text-muted)', margin: 0 }}>Loading…</p>
        </div>
      )}

      {accounts.isError && (
        <div className="card">
          <p className="negative" style={{ margin: 0 }}>
            Could not load accounts.
          </p>
        </div>
      )}

      {deleteError && (
        <div className="banner banner-warn" style={{ marginBottom: '1.25rem' }}>
          {deleteError}
        </div>
      )}

      {!accounts.isPending && !accounts.isError && list.length === 0 && (
        <div className="card">
          <p style={{ color: 'var(--text-muted)', margin: 0 }}>
            No accounts yet. Create one to get started.
          </p>
        </div>
      )}

      {list.length > 0 && (
        <div className="card">
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Account</th>
                  <th>Type</th>
                  <th>Currency</th>
                  <th>Opening balance</th>
                  <th>Current balance</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {list.map((account) => {
                  const isSelected = panel.kind === 'edit' && panel.id === account.id;
                  return (
                    <tr
                      key={account.id}
                      onClick={() => setPanel({ kind: 'edit', id: account.id })}
                      style={{
                        cursor: 'pointer',
                        background: isSelected ? 'var(--bg-subtle)' : undefined,
                      }}
                    >
                      <td>
                        <div className="ticker">{account.label}</div>
                        {account.institution && (
                          <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>
                            {account.institution}
                          </div>
                        )}
                      </td>
                      <td>
                        <span className={`tag ${typeTagClass(account.type)}`}>
                          {account.type ?? 'Other'}
                        </span>
                      </td>
                      <td className="mono">{account.currency}</td>
                      <td className="mono">{formatMoney(account.openingBalance)}</td>
                      <td className="mono">{formatMoney(account.currentBalance)}</td>
                      <td style={{ textAlign: 'right', whiteSpace: 'nowrap' }}>
                        <button
                          className="btn btn-ghost"
                          style={{ padding: '0.375rem 0.75rem' }}
                          onClick={(e) => {
                            e.stopPropagation();
                            handleDelete(account);
                          }}
                        >
                          Delete
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {panel.kind !== 'closed' && activePortfolioId !== null && (
        <div
          className={hasCreditLine(selectedAccount?.type ?? null) ? 'grid-2' : undefined}
          style={{ marginTop: '1.25rem', alignItems: 'start' }}
        >
          {selectedAccount && hasCreditLine(selectedAccount.type) && (
            <CreditLineCard account={selectedAccount} />
          )}

          <div className="card">
            <p className="card-title">
              {panel.kind === 'edit'
                ? `Edit ${selectedAccount?.label ?? 'account'}`
                : 'New account'}
            </p>
            <AccountForm
              key={panel.kind === 'edit' ? panel.id : 'new'}
              portfolioId={activePortfolioId}
              account={selectedAccount}
              onSaved={(saved) => setPanel({ kind: 'edit', id: saved.id })}
              onCancel={() => setPanel({ kind: 'closed' })}
            />
          </div>
        </div>
      )}
    </>
  );
}

function CreditLineCard({ account }: { account: Account }) {
  const available = creditAvailable(account);

  return (
    <div className="card">
      <p className="card-title">{account.label} — credit line</p>
      <div style={{ display: 'flex', gap: '2rem', flexWrap: 'wrap' }}>
        <Metric label="Credit limit" value={formatMoney(account.creditLimit)} />
        <Metric label="Interest rate" value={formatRate(account.interestRate)} />
        <Metric label="Drawn" value={formatMoney(String(creditDrawn(account)))} />
        <Metric
          label="Available"
          value={available === null ? '—' : formatMoney(available)}
          valueClass={available !== null ? 'positive' : ''}
        />
      </div>
      <p className="card-meta">
        HELOC credit limit &amp; interest rate feed the Smith Maneuver deductible-interest
        estimate.
      </p>
    </div>
  );
}

function Metric({
  label,
  value,
  valueClass = '',
}: {
  label: string;
  value: string;
  valueClass?: string;
}) {
  return (
    <div>
      <div
        style={{
          fontSize: '0.6875rem',
          fontWeight: 600,
          textTransform: 'uppercase',
          color: 'var(--text-muted)',
        }}
      >
        {label}
      </div>
      <div className={`mono ${valueClass}`} style={{ fontSize: '1.25rem', fontWeight: 600 }}>
        {value}
      </div>
    </div>
  );
}
