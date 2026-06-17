import { createContext, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { usePortfolios } from '../api/hooks';
import type { Portfolio } from '../api/types';

const STORAGE_KEY = 'activePortfolioId';

interface PortfolioContextValue {
  portfolios: Portfolio[];
  activePortfolioId: number | null;
  activePortfolio: Portfolio | null;
  setActivePortfolioId: (id: number) => void;
  isPending: boolean;
  isError: boolean;
}

const PortfolioContext = createContext<PortfolioContextValue | null>(null);

function readStoredId(): number | null {
  const raw = localStorage.getItem(STORAGE_KEY);
  if (raw === null) return null;
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : null;
}

export function PortfolioProvider({ children }: { children: ReactNode }) {
  const portfoliosQuery = usePortfolios();
  const portfolios = useMemo(() => portfoliosQuery.data ?? [], [portfoliosQuery.data]);
  const [activePortfolioId, setActiveId] = useState<number | null>(() => readStoredId());

  useEffect(() => {
    if (portfolios.length === 0) return;
    const stillExists = activePortfolioId !== null && portfolios.some((p) => p.id === activePortfolioId);
    if (!stillExists) {
      setActiveId(portfolios[0].id);
    }
  }, [portfolios, activePortfolioId]);

  function setActivePortfolioId(id: number) {
    setActiveId(id);
    localStorage.setItem(STORAGE_KEY, String(id));
  }

  const value: PortfolioContextValue = {
    portfolios,
    activePortfolioId,
    activePortfolio: portfolios.find((p) => p.id === activePortfolioId) ?? null,
    setActivePortfolioId,
    isPending: portfoliosQuery.isPending,
    isError: portfoliosQuery.isError,
  };

  return <PortfolioContext.Provider value={value}>{children}</PortfolioContext.Provider>;
}

export function usePortfolioContext(): PortfolioContextValue {
  const context = useContext(PortfolioContext);
  if (context === null) {
    throw new Error('usePortfolioContext must be used within a PortfolioProvider');
  }
  return context;
}
