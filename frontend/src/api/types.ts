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
  institution: string | null;
  currency: string;
  openingBalance: string;
  openingBalanceDate: string | null;
  creditLimit: string | null;
  interestRate: string | null;
  currentBalance: string;
}

export interface CreateAccount {
  portfolioId: number;
  label: string;
  type: string;
  institution?: string | null;
  currency?: string;
  openingBalance?: string | number;
  openingBalanceDate?: string | null;
  creditLimit?: string | number | null;
  interestRate?: string | number | null;
}

export interface UpdateAccount {
  label: string;
  type: string;
  institution?: string | null;
  currency?: string;
  openingBalance?: string | number;
  openingBalanceDate?: string | null;
  creditLimit?: string | number | null;
  interestRate?: string | number | null;
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
  deniedLossAdjustment: string | null;
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
  deniedLossAdjustment?: string | null;
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
  deniedLossAdjustment: string | null;
  totalAcb: string;
  acbPerShare: string;
  proceeds: string | null;
  capitalGainLoss: string | null;
  superficialLossFlag: boolean;
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

export interface Quote {
  symbol: string;
  price: string | null;
  currency: string | null;
  asOf: string;
  available: boolean;
}

export interface PortfolioSnapshot {
  id: number;
  portfolioId: number;
  date: string;
  marketValue: string;
  createdAt: string;
}

export interface CreatePortfolioSnapshot {
  date: string;
  marketValue: string;
}

export interface Dividend {
  id: number;
  portfolioId: number;
  securityId: number;
  ticker: string;
  name: string;
  accountId: number | null;
  accountLabel: string | null;
  paymentDate: string;
  grossAmount: string;
  withholdingTax: string;
  netAmount: string;
  currency: string;
  drip: boolean;
  notes: string | null;
  createdAt: string;
}

export interface CreateDividend {
  portfolioId: number;
  securityId: number;
  accountId?: number | null;
  paymentDate: string;
  grossAmount: string;
  withholdingTax?: string | null;
  currency?: string | null;
  drip?: boolean;
  notes?: string | null;
}

export interface DividendSummary {
  year: number;
  months: string[];
  cumulative: string[];
  ytdTotal: string;
  availableYears: number[];
}

export type CashTransactionType =
  | 'DEPOSIT'
  | 'WITHDRAWAL'
  | 'TRANSFER'
  | 'HELOC_DRAW'
  | 'HELOC_REPAYMENT'
  | 'INTEREST_CHARGE'
  | 'INTEREST_PAYMENT'
  | 'FEE';

export type CashPurpose = 'INVESTMENT' | 'PERSONAL';

export type LedgerSource = 'CASH' | 'TRADE' | 'DIVIDEND';

export interface CashTransaction {
  id: number | null;
  accountId: number;
  accountLabel: string;
  type: CashTransactionType | null;
  date: string;
  amount: string;
  purpose: CashPurpose | null;
  counterpartyAccountId: number | null;
  counterpartyAccountLabel: string | null;
  transferGroupId: string | null;
  notes: string | null;
  createdAt: string;
  balanceAfter: string | null;
  source: LedgerSource;
  securityTransactionId: number | null;
  securityTicker: string | null;
  tradeAction: 'BUY' | 'SELL' | null;
  dividendId: number | null;
}

export interface CreateCashTransaction {
  accountId: number;
  type: CashTransactionType;
  date: string;
  amount: string;
  purpose?: CashPurpose | null;
  counterpartyAccountId?: number | null;
  notes?: string | null;
}

export interface ReturnFigure {
  amount: string | null;
  pct: string | null;
  basisDate: string | null;
  available: boolean;
}

export interface PeriodReturn {
  label: string;
  amount: string | null;
  pct: string | null;
  available: boolean;
}

export interface AllocationSlice {
  securityId: number;
  ticker: string;
  name: string;
  marketValue: string;
  pct: string;
}

export interface DashboardData {
  portfolioValue: string | null;
  invested: string;
  asOfDate: string | null;
  todaysReturn: ReturnFigure;
  allTimeReturn: ReturnFigure;
  periodReturns: PeriodReturn[];
  allocation: AllocationSlice[];
}

export type FlowStatus = 'TRACED' | 'PARTIALLY_TRACED' | 'UNTRACED';

export interface FlowStep {
  order: number;
  kind: 'CASH' | 'SECURITY';
  stepLabel: string;
  amount: string | null;
  ticker: string | null;
  purpose: CashPurpose | null;
  detail: string | null;
  cashTransactionId: number | null;
  securityTransactionId: number | null;
}

export interface SmithManeuverFlow {
  id: number;
  portfolioId: number;
  helocAccountId: number;
  helocAccountLabel: string;
  label: string;
  investmentUseAmount: string;
  status: FlowStatus;
  notes: string | null;
  steps: FlowStep[];
  createdAt: string;
}

export interface HelocAccountSummary {
  id: number;
  label: string;
  balance: string;
  creditLimit: string | null;
  interestRate: string | null;
  investmentUseBalance: string;
  tracedPct: string;
  status: string;
}

export interface InterestEntry {
  id: number;
  date: string;
  type: string;
  amount: string;
  deductibleEstimate: string;
}

export interface SmithManeuverWarning {
  title: string;
  detail: string;
}

export interface SmithManeuverData {
  investmentUseBalance: string;
  flows: SmithManeuverFlow[];
  helocAccounts: HelocAccountSummary[];
  interestLog: InterestEntry[];
  warnings: SmithManeuverWarning[];
}

export interface CreateSmithManeuverFlow {
  portfolioId: number;
  helocAccountId: number;
  label?: string | null;
  investmentUseAmount: string;
  cashTransactionIds: number[];
  securityTransactionId?: number | null;
  notes?: string | null;
}
