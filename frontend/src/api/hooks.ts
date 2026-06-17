import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type {
  Account,
  CreateAccount,
  CreatePortfolio,
  CreatePriceSnapshots,
  CreateSecurity,
  CreateSecurityTransaction,
  Holding,
  HoldingHistoryRow,
  Portfolio,
  PriceSnapshot,
  Security,
  SecurityTransaction,
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
