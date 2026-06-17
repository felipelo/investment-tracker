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
  portfolioId: number;
  label: string;
  type: string | null;
  currency: string;
}

export interface CreateAccount {
  portfolioId: number;
  label: string;
  type: string;
  currency: string;
}

export interface Portfolio {
  id: number;
  name: string;
  description: string | null;
  baseCurrency: string;
  type: string | null;
  invested: string;
  marketValue: string | null;
  returnAmount: string | null;
  returnPct: string | null;
  holdingsCount: number;
}

export interface CreatePortfolio {
  name: string;
  description?: string | null;
  baseCurrency?: string | null;
  type?: string | null;
}

export type UpdatePortfolio = CreatePortfolio;

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
  accountId: number;
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

export interface Holding {
  securityId: number;
  ticker: string;
  name: string;
  shareBalance: string;
  acbPerShare: string;
  totalAcb: string;
  latestPrice: string | null;
  priceDate: string | null;
  marketValue: string | null;
  unrealizedGainLoss: string | null;
}

export interface HoldingHistoryRow {
  transactionId: number;
  date: string;
  action: Action;
  shares: string | null;
  pricePerShare: string | null;
  cashAmount: string | null;
  splitRatio: string | null;
  notes: string | null;
  shareChange: string;
  shareBalance: string;
  acbChange: string;
  totalAcb: string;
  acbPerShare: string;
  proceeds: string | null;
  capitalGainLoss: string | null;
}

export interface PriceSnapshot {
  id: number;
  securityId: number;
  date: string;
  price: string;
  createdAt: string;
}

export interface CreatePriceSnapshot {
  securityId: number;
  date: string;
  price: string;
}

export interface CreatePriceSnapshots {
  snapshots: CreatePriceSnapshot[];
}
