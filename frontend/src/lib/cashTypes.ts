import type { CashPurpose, CashTransactionType } from '../api/types';

export interface CashTypeMeta {
  value: CashTransactionType;
  label: string;
  tagClass: string;
  requiresCounterparty: boolean;
}

// Cash transaction type -> display label and tag color, matching mock/cash-transactions.html.
export const CASH_TYPES: CashTypeMeta[] = [
  { value: 'DEPOSIT', label: 'Deposit', tagClass: 'tag-sage', requiresCounterparty: false },
  { value: 'WITHDRAWAL', label: 'Withdrawal', tagClass: '', requiresCounterparty: false },
  { value: 'TRANSFER', label: 'Transfer', tagClass: 'tag-sky', requiresCounterparty: true },
  { value: 'HELOC_DRAW', label: 'HELOC Draw', tagClass: 'tag-lavender', requiresCounterparty: true },
  {
    value: 'HELOC_REPAYMENT',
    label: 'HELOC Repayment',
    tagClass: '',
    requiresCounterparty: true,
  },
  {
    value: 'INTEREST_CHARGE',
    label: 'Interest Charge',
    tagClass: 'tag-peach',
    requiresCounterparty: false,
  },
  {
    value: 'INTEREST_PAYMENT',
    label: 'Interest Payment',
    tagClass: '',
    requiresCounterparty: false,
  },
  { value: 'FEE', label: 'Fee', tagClass: 'tag-sky', requiresCounterparty: false },
];

const CASH_TYPE_MAP = new Map(CASH_TYPES.map((t) => [t.value, t]));

export function cashTypeMeta(type: CashTransactionType): CashTypeMeta {
  return CASH_TYPE_MAP.get(type) ?? CASH_TYPES[0];
}

export function requiresCounterparty(type: CashTransactionType): boolean {
  return cashTypeMeta(type).requiresCounterparty;
}

export interface PurposeMeta {
  value: CashPurpose;
  label: string;
  tagClass: string;
}

export const PURPOSES: PurposeMeta[] = [
  { value: 'INVESTMENT', label: 'Investment', tagClass: 'tag-sage' },
  { value: 'PERSONAL', label: 'Personal', tagClass: 'tag-butter' },
];

const PURPOSE_MAP = new Map(PURPOSES.map((p) => [p.value, p]));

export function purposeMeta(purpose: CashPurpose): PurposeMeta {
  return PURPOSE_MAP.get(purpose) ?? PURPOSES[0];
}
