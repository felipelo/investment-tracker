import { useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCashTransactions } from '../api/hooks';
import { formatGainLoss, formatMoney } from '../lib/actions';
import { cashTypeMeta, purposeMeta } from '../lib/cashTypes';
import type { CashTransaction, CashPurpose, CashTransactionType } from '../api/types';

interface CashTransactionsListProps {
  portfolioId: number;
  selectedId?: number | null;
  onSelect?: (transaction: CashTransaction) => void;
}

export default function CashTransactionsList({
  portfolioId,
  selectedId,
  onSelect,
}: CashTransactionsListProps) {
  const transactions = useCashTransactions(portfolioId);
  const navigate = useNavigate();
  const [accountFilter, setAccountFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');
  const [purposeFilter, setPurposeFilter] = useState('');

  const accounts = useMemo(() => {
    const labels = new Set<string>();
    (transactions.data ?? []).forEach((t) => labels.add(t.accountLabel));
    return Array.from(labels).sort();
  }, [transactions.data]);

  const rows = useMemo(() => {
    return (transactions.data ?? []).filter((t) => {
      if (accountFilter && t.accountLabel !== accountFilter) return false;
      if (typeFilter) {
        if (typeFilter === 'TRADE' || typeFilter === 'DIVIDEND') {
          if (t.source !== typeFilter) return false;
        } else if (t.source !== 'CASH' || t.type !== typeFilter) {
          return false;
        }
      }
      if (purposeFilter && (t.purpose ?? '') !== purposeFilter) return false;
      return true;
    });
  }, [transactions.data, accountFilter, typeFilter, purposeFilter]);

  return (
    <div className="card" style={{ marginTop: '1.5rem' }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: '0.75rem',
          marginBottom: '1rem',
        }}
      >
        <p className="card-title" style={{ margin: 0 }}>
          Ledger
        </p>
        <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
          <select value={accountFilter} onChange={(e) => setAccountFilter(e.target.value)}>
            <option value="">All accounts</option>
            {accounts.map((label) => (
              <option key={label} value={label}>
                {label}
              </option>
            ))}
          </select>
          <select value={typeFilter} onChange={(e) => setTypeFilter(e.target.value)}>
            <option value="">All types</option>
            {(
              [
                'DEPOSIT',
                'WITHDRAWAL',
                'TRANSFER',
                'HELOC_DRAW',
                'HELOC_REPAYMENT',
                'INTEREST_CHARGE',
                'INTEREST_PAYMENT',
                'FEE',
              ] as CashTransactionType[]
            ).map((type) => (
              <option key={type} value={type}>
                {cashTypeMeta(type).label}
              </option>
            ))}
            <option value="TRADE">Trade</option>
            <option value="DIVIDEND">Dividend</option>
          </select>
          <select value={purposeFilter} onChange={(e) => setPurposeFilter(e.target.value)}>
            <option value="">All purposes</option>
            <option value="INVESTMENT">Investment</option>
            <option value="PERSONAL">Personal</option>
          </select>
        </div>
      </div>

      {transactions.isPending && <p style={{ color: 'var(--text-muted)', margin: 0 }}>Loading…</p>}

      {transactions.isError && (
        <p className="negative" style={{ margin: 0 }}>
          Could not load cash transactions.
        </p>
      )}

      {transactions.isSuccess && rows.length === 0 && (
        <p style={{ color: 'var(--text-muted)', margin: 0 }}>No cash transactions recorded yet.</p>
      )}

      {transactions.isSuccess && rows.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Date</th>
                <th>Account</th>
                <th>Type</th>
                <th>Counterparty</th>
                <th>Purpose</th>
                <th style={{ textAlign: 'right' }}>Amount</th>
                <th style={{ textAlign: 'right' }}>Balance</th>
                <th>Notes</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((transaction) => {
                const isTrade = transaction.source === 'TRADE';
                const isDividend = transaction.source === 'DIVIDEND';
                const isCash = transaction.source === 'CASH';
                const selected = isCash && transaction.id === selectedId;
                const amount = formatGainLoss(transaction.amount);
                const handleClick = () => {
                  if (isTrade) {
                    navigate(`/record-trade?edit=${transaction.securityTransactionId}`);
                  } else if (isDividend) {
                    navigate(`/dividends?edit=${transaction.dividendId}`);
                  } else {
                    onSelect?.(transaction);
                  }
                };
                const rowKey = `${transaction.source}-${
                  transaction.id ?? transaction.securityTransactionId ?? transaction.dividendId
                }`;
                return (
                  <tr
                    key={rowKey}
                    onClick={handleClick}
                    title={
                      isTrade
                        ? 'Open trade in Record trade'
                        : isDividend
                          ? 'Open dividend in Dividends'
                          : undefined
                    }
                    style={{
                      cursor: isTrade || isDividend || onSelect ? 'pointer' : undefined,
                      background: selected ? 'var(--bg-subtle)' : undefined,
                    }}
                  >
                    <td className="mono">{transaction.date}</td>
                    <td>{transaction.accountLabel}</td>
                    <td>{renderType(transaction)}</td>
                    <td>{transaction.counterpartyAccountLabel ?? '—'}</td>
                    <td>{renderPurpose(transaction.purpose)}</td>
                    <td className={`mono ${amount.className}`} style={{ textAlign: 'right' }}>
                      {amount.text}
                    </td>
                    <td className="mono" style={{ textAlign: 'right' }}>
                      {formatMoney(transaction.balanceAfter)}
                    </td>
                    <td>{transaction.notes ?? ''}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function renderType(transaction: CashTransaction) {
  if (transaction.source === 'TRADE') {
    const label = transaction.tradeAction === 'SELL' ? 'Sell' : 'Buy';
    return (
      <span className="tag tag-lavender">
        {label} {transaction.securityTicker ?? ''}
      </span>
    );
  }
  if (transaction.source === 'DIVIDEND') {
    return (
      <span className="tag tag-butter">Dividend {transaction.securityTicker ?? ''}</span>
    );
  }
  if (!transaction.type) return '—';
  const meta = cashTypeMeta(transaction.type);
  return <span className={`tag ${meta.tagClass}`}>{meta.label}</span>;
}

function renderPurpose(purpose: CashPurpose | null) {
  if (!purpose) return '—';
  const meta = purposeMeta(purpose);
  return <span className={`tag ${meta.tagClass}`}>{meta.label}</span>;
}
