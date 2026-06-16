import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from './client';
import type {
  Account,
  CreateAccount,
  CreateSecurity,
  CreateSecurityTransaction,
  Security,
  SecurityTransaction,
  TransactionFilters,
} from './types';

const keys = {
  securities: ['securities'] as const,
  accounts: ['accounts'] as const,
  transactions: (filters?: TransactionFilters) =>
    ['security-transactions', filters ?? {}] as const,
};

export function useSecurities() {
  return useQuery({
    queryKey: keys.securities,
    queryFn: () => api.get<Security[]>('/securities'),
  });
}

export function useAccounts() {
  return useQuery({
    queryKey: keys.accounts,
    queryFn: () => api.get<Account[]>('/accounts'),
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

export function useCreateTransaction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateSecurityTransaction) =>
      api.post<SecurityTransaction>('/security-transactions', body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['security-transactions'] });
    },
  });
}

export function useUpdateTransaction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, body }: { id: number; body: CreateSecurityTransaction }) =>
      api.put<SecurityTransaction>(`/security-transactions/${id}`, body),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['security-transactions'] });
    },
  });
}

export function useDeleteTransaction() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (id: number) => api.del(`/security-transactions/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['security-transactions'] });
    },
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
