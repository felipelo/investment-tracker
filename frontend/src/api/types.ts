// TypeScript mirrors of the backend DTOs (com.investmenttracker.web.dto).
// BigDecimal / LocalDate fields are represented as strings to preserve
// precision and match the JSON the API emits and accepts.

export type Action =
  | 'BUY'
  | 'SELL'
  | 'RETURN_OF_CAPITAL'
  | 'REINVESTED_DISTRIBUTION'
  | 'SPLIT';

export interface Security {
  id: number;
  ticker: string;
  name: string;
  assetClass: string | null;
  currency: string;
}

export interface CreateSecurity {
  ticker: string;
  name: string;
  assetClass?: string | null;
  currency: string;
}

export interface Account {
  id: number;
  label: string;
  type: string | null;
  currency: string;
}

export interface CreateAccount {
  label: string;
  type: string;
  currency: string;
}

export interface SecurityTransaction {
  id: number;
  date: string;
  securityId: number;
  accountId: number | null;
  action: Action;
  shares: string | null;
  pricePerShare: string | null;
  commission: string | null;
  cashAmount: string | null;
  splitRatio: string | null;
  notes: string | null;
  createdAt: string;
}

export interface CreateSecurityTransaction {
  date: string;
  securityId: number;
  accountId?: number | null;
  action: Action;
  shares?: string | null;
  pricePerShare?: string | null;
  commission?: string | null;
  cashAmount?: string | null;
  splitRatio?: string | null;
  notes?: string | null;
}

export interface TransactionFilters {
  securityId?: number;
  accountId?: number;
  from?: string;
  to?: string;
}
