import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type {
  Account,
  CashTransaction,
  CreateAccount,
  CreateCashTransaction,
  UpdateAccount,
  CreateDividend,
  CreatePortfolio,
  CreatePortfolioSnapshot,
  CreatePriceSnapshots,
  CreateSecurity,
  CreateSecurityTransaction,
  CreateSmithManeuverFlow,
  DashboardData,
  Dividend,
  DividendSummary,
  Holding,
  HoldingHistoryRow,
  Portfolio,
  PortfolioSnapshot,
  PriceSnapshot,
  Quote,
  Security,
  SecurityTransaction,
  SmithManeuverData,
  SmithManeuverFlow,
  TransactionFilters,
  UpdatePortfolio,
} from './types';

const keys = {
  securities: ['securities'] as const,
  accounts: ['accounts'] as const,
  portfolios: ['portfolios'] as const,
  holdings: (portfolioId: number) => ['holdings', portfolioId] as const,
  holdingHistory: (portfolioId: number, securityId: number) =>
    ['holdings', portfolioId, securityId, 'history'] as const,
  transactions: (filters?: TransactionFilters) =>
    ['security-transactions', filters ?? {}] as const,
  dashboard: (portfolioId: number) => ['dashboard', portfolioId] as const,
  dividends: (portfolioId: number) => ['dividends', portfolioId] as const,
  dividendSummary: (portfolioId: number, year: number | null) =>
    ['dividends', 'summary', portfolioId, year ?? 'latest'] as const,
  portfolioSnapshots: (portfolioId: number) => ['portfolio-snapshots', portfolioId] as const,
  cashTransactions: (portfolioId: number) => ['cash-transactions', portfolioId] as const,
  smithManeuver: (portfolioId: number) => ['smith-maneuver', portfolioId] as const,
  quotes: (symbols: string) => ['quotes', symbols] as const,
};

export function useSecurities() {
  return useQuery({
    queryKey: keys.securities,
    queryFn: () => api.get<Security[]>('/securities'),
  });
}

export function useAccounts(portfolioId?: number | null) {
  return useQuery({
    queryKey: [...keys.accounts, portfolioId ?? null] as const,
    queryFn: () =>
      api.get<Account[]>('/accounts', portfolioId != null ? { portfolioId } : undefined),
  });
}

export function usePortfolios() {
  return useQuery({
    queryKey: keys.portfolios,
    queryFn: () => api.get<Portfolio[]>('/portfolios'),
  });
}

export function useTransactions(filters?: TransactionFilters) {
  return useQuery({
    queryKey: keys.transactions(filters),
    queryFn: () =>
      api.get<SecurityTransaction[]>(
        '/security-transactions',
        filters as Record<string, string | number | undefined> | undefined,
      ),
  });
}

export function useTransaction(id: number | null) {
  return useQuery({
    queryKey: ['security-transactions', 'detail', id] as const,
    queryFn: () => api.get<SecurityTransaction>(`/security-transactions/${id}`),
    enabled: id !== null,
  });
}

export function useHoldings(portfolioId: number | null) {
  return useQuery({
    queryKey: portfolioId !== null ? keys.holdings(portfolioId) : ['holdings', 'none'],
    queryFn: () => api.get<Holding[]>('/holdings', { portfolioId: portfolioId! }),
    enabled: portfolioId !== null,
  });
}

export function useHoldingHistory(portfolioId: number | null, securityId: number | null) {
  return useQuery({
    queryKey:
      portfolioId !== null && securityId !== null
        ? keys.holdingHistory(portfolioId, securityId)
        : ['holdings', 'history', 'none'],
    queryFn: () =>
      api.get<HoldingHistoryRow[]>(`/holdings/${securityId}/history`, { portfolioId: portfolioId! }),
    enabled: portfolioId !== null && securityId !== null,
  });
}

export function useCreatePriceSnapshots() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreatePriceSnapshots) =>
      api.post<PriceSnapshot[]>('/price-snapshots', body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['holdings'] });
      queryClient.invalidateQueries({ queryKey: keys.portfolios });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      queryClient.invalidateQueries({ queryKey: ['portfolio-snapshots'] });
    },
  });
}

export function useQuotes(symbols: string[]) {
  const joined = symbols.join(',');
  return useQuery({
    queryKey: keys.quotes(joined),
    queryFn: () => api.get<Quote[]>('/quotes', { symbols: joined }),
    enabled: symbols.length > 0,
    staleTime: 60_000,
  });
}

export function useDashboard(portfolioId: number | null) {
  return useQuery({
    queryKey: portfolioId !== null ? keys.dashboard(portfolioId) : ['dashboard', 'none'],
    queryFn: () => api.get<DashboardData>(`/portfolios/${portfolioId}/dashboard`),
    enabled: portfolioId !== null,
  });
}

export function useDividends(portfolioId: number | null) {
  return useQuery({
    queryKey: portfolioId !== null ? keys.dividends(portfolioId) : ['dividends', 'none'],
    queryFn: () => api.get<Dividend[]>('/dividends', { portfolioId: portfolioId! }),
    enabled: portfolioId !== null,
  });
}

export function useDividendSummary(portfolioId: number | null, year: number | null) {
  return useQuery({
    queryKey:
      portfolioId !== null
        ? keys.dividendSummary(portfolioId, year)
        : ['dividends', 'summary', 'none'],
    queryFn: () =>
      api.get<DividendSummary>(
        `/portfolios/${portfolioId}/dividends/summary`,
        year !== null ? { year } : undefined,
      ),
    enabled: portfolioId !== null,
  });
}

export function useCreateDividend() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateDividend) => api.post<Dividend>('/dividends', body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dividends'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function useUpdateDividend() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: CreateDividend }) =>
      api.put<Dividend>(`/dividends/${id}`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dividends'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function useDeleteDividend() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.del(`/dividends/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['dividends'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function useCashTransactions(portfolioId: number | null) {
  return useQuery({
    queryKey:
      portfolioId !== null ? keys.cashTransactions(portfolioId) : ['cash-transactions', 'none'],
    queryFn: () => api.get<CashTransaction[]>('/cash-transactions', { portfolioId: portfolioId! }),
    enabled: portfolioId !== null,
  });
}

function invalidateCashTransactionViews(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: ['cash-transactions'] });
  queryClient.invalidateQueries({ queryKey: keys.accounts });
  queryClient.invalidateQueries({ queryKey: ['dashboard'] });
}

export function useCreateCashTransaction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateCashTransaction) =>
      api.post<CashTransaction>('/cash-transactions', body),
    onSuccess: () => invalidateCashTransactionViews(queryClient),
  });
}

export function useUpdateCashTransaction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: CreateCashTransaction }) =>
      api.put<CashTransaction>(`/cash-transactions/${id}`, body),
    onSuccess: () => invalidateCashTransactionViews(queryClient),
  });
}

export function useDeleteCashTransaction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.del(`/cash-transactions/${id}`),
    onSuccess: () => invalidateCashTransactionViews(queryClient),
  });
}

export function useSmithManeuver(portfolioId: number | null) {
  return useQuery({
    queryKey:
      portfolioId !== null ? keys.smithManeuver(portfolioId) : ['smith-maneuver', 'none'],
    queryFn: () => api.get<SmithManeuverData>(`/portfolios/${portfolioId}/smith-maneuver`),
    enabled: portfolioId !== null,
  });
}

function invalidateSmithManeuverViews(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: ['smith-maneuver'] });
}

export function useCreateFlow() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateSmithManeuverFlow) =>
      api.post<SmithManeuverFlow>('/smith-maneuver-flows', body),
    onSuccess: () => invalidateSmithManeuverViews(queryClient),
  });
}

export function useUpdateFlow() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: CreateSmithManeuverFlow }) =>
      api.put<SmithManeuverFlow>(`/smith-maneuver-flows/${id}`, body),
    onSuccess: () => invalidateSmithManeuverViews(queryClient),
  });
}

export function useDeleteFlow() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.del(`/smith-maneuver-flows/${id}`),
    onSuccess: () => invalidateSmithManeuverViews(queryClient),
  });
}

export function usePortfolioSnapshots(portfolioId: number | null) {
  return useQuery({
    queryKey:
      portfolioId !== null ? keys.portfolioSnapshots(portfolioId) : ['portfolio-snapshots', 'none'],
    queryFn: () => api.get<PortfolioSnapshot[]>(`/portfolios/${portfolioId}/snapshots`),
    enabled: portfolioId !== null,
  });
}

export function useCreatePortfolioSnapshot(portfolioId: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreatePortfolioSnapshot) =>
      api.post<PortfolioSnapshot>(`/portfolios/${portfolioId}/snapshots`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: keys.portfolioSnapshots(portfolioId) });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function useCreatePortfolio() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreatePortfolio) => api.post<Portfolio>('/portfolios', body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: keys.portfolios });
    },
  });
}

export function useUpdatePortfolio() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: UpdatePortfolio }) =>
      api.put<Portfolio>(`/portfolios/${id}`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: keys.portfolios });
    },
  });
}

export function useDeletePortfolio() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.del(`/portfolios/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: keys.portfolios });
    },
  });
}

function invalidateTransactionViews(queryClient: ReturnType<typeof useQueryClient>) {
  queryClient.invalidateQueries({ queryKey: ['security-transactions'] });
  queryClient.invalidateQueries({ queryKey: ['holdings'] });
  queryClient.invalidateQueries({ queryKey: keys.portfolios });
  queryClient.invalidateQueries({ queryKey: ['dashboard'] });
}

export function useCreateTransaction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateSecurityTransaction) =>
      api.post<SecurityTransaction>('/security-transactions', body),
    onSuccess: () => invalidateTransactionViews(queryClient),
  });
}

export function useUpdateTransaction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: CreateSecurityTransaction }) =>
      api.put<SecurityTransaction>(`/security-transactions/${id}`, body),
    onSuccess: () => invalidateTransactionViews(queryClient),
  });
}

export function useDeleteTransaction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.del(`/security-transactions/${id}`),
    onSuccess: () => invalidateTransactionViews(queryClient),
  });
}

export function useCreateSecurity() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateSecurity) => api.post<Security>('/securities', body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: keys.securities });
    },
  });
}

export function useCreateAccount() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateAccount) => api.post<Account>('/accounts', body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: keys.accounts });
    },
  });
}

export function useUpdateAccount() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: UpdateAccount }) =>
      api.put<Account>(`/accounts/${id}`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: keys.accounts });
    },
  });
}

export function useDeleteAccount() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.del(`/accounts/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: keys.accounts });
    },
  });
}
