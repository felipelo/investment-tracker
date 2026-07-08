import { useState } from 'react';
import type { FormEvent } from 'react';
import { useCreateAccount, useUpdateAccount } from '../api/hooks';
import { ApiError } from '../api/client';
import type { Account, CreateAccount, UpdateAccount } from '../api/types';

const ACCOUNT_TYPES = ['Chequing', 'Investment (cash)', 'Margin', 'HELOC', 'Other'] as const;
const CREDIT_LINE_TYPES = ['HELOC', 'Margin'];

export function hasCreditLine(type: string | null): boolean {
  return type != null && CREDIT_LINE_TYPES.includes(type);
}

interface AccountFormProps {
  portfolioId: number;
  account?: Account | null;
  onSaved?: (account: Account) => void;
  onCancel?: () => void;
  cancelLabel?: string;
}

function toFormAmount(value: string | number | null | undefined, fallback = ''): string {
  if (value == null || value === '') return fallback;
  return String(value);
}

function parseOptionalAmount(value: string): number | null {
  const trimmed = value.trim();
  if (trimmed === '') return null;
  const num = Number(trimmed);
  return Number.isNaN(num) ? null : num;
}

function buildBody(
  label: string,
  type: string,
  institution: string,
  currency: string,
  openingBalance: string,
  openingBalanceDate: string,
  creditLimit: string,
  interestRate: string
): UpdateAccount {
  const body: UpdateAccount = {
    label,
    type,
    institution: institution.trim() === '' ? null : institution,
    currency,
    openingBalance: openingBalance.trim() === '' ? 0 : Number(openingBalance),
    openingBalanceDate: openingBalanceDate.trim() === '' ? null : openingBalanceDate,
  };

  if (hasCreditLine(type)) {
    body.creditLimit = parseOptionalAmount(creditLimit);
    body.interestRate = parseOptionalAmount(interestRate);
  }

  return body;
}

export default function AccountForm({
  portfolioId,
  account,
  onSaved,
  onCancel,
  cancelLabel = 'Cancel',
}: AccountFormProps) {
  const isEdit = account != null;
  const createAccount = useCreateAccount();
  const updateAccount = useUpdateAccount();
  const pending = createAccount.isPending || updateAccount.isPending;

  const [label, setLabel] = useState(account?.label ?? '');
  const [type, setType] = useState(account?.type ?? '');
  const [institution, setInstitution] = useState(account?.institution ?? '');
  const [currency, setCurrency] = useState(account?.currency ?? 'CAD');
  const [openingBalance, setOpeningBalance] = useState(toFormAmount(account?.openingBalance, '0'));
  const [openingBalanceDate, setOpeningBalanceDate] = useState(account?.openingBalanceDate ?? '');
  const [creditLimit, setCreditLimit] = useState(toFormAmount(account?.creditLimit));
  const [interestRate, setInterestRate] = useState(toFormAmount(account?.interestRate));
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setFieldErrors({});

    const body = buildBody(
      label,
      type,
      institution,
      currency,
      openingBalance,
      openingBalanceDate,
      creditLimit,
      interestRate
    );

    try {
      const saved = isEdit
        ? await updateAccount.mutateAsync({ id: account!.id, body })
        : await createAccount.mutateAsync({ ...body, portfolioId } as CreateAccount);
      onSaved?.(saved);
    } catch (error) {
      if (error instanceof ApiError) {
        setFieldErrors(error.fieldErrors);
      }
    }
  }

  const activeMutation = isEdit ? updateAccount : createAccount;
  const generalError =
    activeMutation.isError &&
    activeMutation.error instanceof ApiError &&
    Object.keys(activeMutation.error.fieldErrors).length === 0
      ? activeMutation.error.message
      : null;

  return (
    <form onSubmit={handleSubmit}>
      {generalError && (
        <div className="banner banner-warn" style={{ marginBottom: '1rem' }}>
          {generalError}
        </div>
      )}

      <div className="form-grid">
        <div className="form-group">
          <label htmlFor="account-label">Label</label>
          <input
            id="account-label"
            type="text"
            value={label}
            onChange={(e) => setLabel(e.target.value)}
            required
          />
          <FieldError message={fieldErrors.label} />
        </div>

        <div className="form-group">
          <label htmlFor="account-type">Type</label>
          <select id="account-type" value={type} onChange={(e) => setType(e.target.value)} required>
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
          <label htmlFor="account-institution">Institution</label>
          <input
            id="account-institution"
            type="text"
            value={institution}
            onChange={(e) => setInstitution(e.target.value)}
          />
          <FieldError message={fieldErrors.institution} />
        </div>

        <div className="form-group">
          <label htmlFor="account-currency">Currency</label>
          <input
            id="account-currency"
            type="text"
            value={currency}
            onChange={(e) => setCurrency(e.target.value)}
            maxLength={3}
            required
          />
          <FieldError message={fieldErrors.currency} />
        </div>

        <div className="form-group">
          <label htmlFor="account-opening">Opening balance</label>
          <input
            id="account-opening"
            type="text"
            inputMode="decimal"
            value={openingBalance}
            onChange={(e) => setOpeningBalance(e.target.value)}
          />
          <FieldError message={fieldErrors.openingBalance} />
        </div>

        <div className="form-group">
          <label htmlFor="account-opening-date">Opening balance date</label>
          <input
            id="account-opening-date"
            type="date"
            value={openingBalanceDate}
            onChange={(e) => setOpeningBalanceDate(e.target.value)}
          />
          <FieldError message={fieldErrors.openingBalanceDate} />
        </div>
      </div>

      {hasCreditLine(type) && (
        <>
          <div className="banner banner-info" style={{ margin: '1rem 0 0' }}>
            <span>Credit line details</span>
          </div>

          <div className="form-grid" style={{ marginTop: '1rem' }}>
            <div className="form-group">
              <label htmlFor="account-limit">Credit limit</label>
              <input
                id="account-limit"
                type="text"
                inputMode="decimal"
                value={creditLimit}
                onChange={(e) => setCreditLimit(e.target.value)}
              />
              <FieldError message={fieldErrors.creditLimit} />
            </div>

            <div className="form-group">
              <label htmlFor="account-rate">Interest rate (%)</label>
              <input
                id="account-rate"
                type="text"
                inputMode="decimal"
                value={interestRate}
                onChange={(e) => setInterestRate(e.target.value)}
              />
              <FieldError message={fieldErrors.interestRate} />
            </div>
          </div>
        </>
      )}

      <div style={{ display: 'flex', gap: '0.75rem', marginTop: '1.25rem' }}>
        <button type="submit" className="btn btn-primary" disabled={pending}>
          {pending ? 'Saving…' : isEdit ? 'Save changes' : 'Add account'}
        </button>
        {onCancel && (
          <button type="button" className="btn btn-ghost" onClick={onCancel}>
            {cancelLabel}
          </button>
        )}
      </div>
    </form>
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
