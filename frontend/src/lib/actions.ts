import type { Action } from '../api/types';

export interface ActionMeta {
  value: Action;
  label: string;
  tagClass: string;
}

// Action -> display label and tag color, matching DESIGN.md section 6.2.
export const ACTIONS: ActionMeta[] = [
  { value: 'BUY', label: 'Buy', tagClass: 'tag-sage' },
  { value: 'SELL', label: 'Sell', tagClass: 'tag-peach' },
  { value: 'RETURN_OF_CAPITAL', label: 'Return of Capital', tagClass: 'tag-butter' },
  {
    value: 'REINVESTED_DISTRIBUTION',
    label: 'Reinvested Distribution',
    tagClass: 'tag-lavender',
  },
  { value: 'SPLIT', label: 'Split', tagClass: 'tag-sky' },
];

const ACTION_MAP = new Map(ACTIONS.map((a) => [a.value, a]));

export function actionMeta(action: Action): ActionMeta {
  return ACTION_MAP.get(action) ?? ACTIONS[0];
}

export type ActionFieldGroup = 'trade' | 'cash' | 'split';

export function fieldGroupFor(action: Action): ActionFieldGroup {
  switch (action) {
    case 'BUY':
    case 'SELL':
      return 'trade';
    case 'RETURN_OF_CAPITAL':
    case 'REINVESTED_DISTRIBUTION':
      return 'cash';
    case 'SPLIT':
      return 'split';
  }
}

const currencyFormatter = new Intl.NumberFormat('en-CA', {
  style: 'currency',
  currency: 'CAD',
});

export function formatMoney(value: string | null): string {
  if (value === null || value === '') return '—';
  const num = Number(value);
  return Number.isNaN(num) ? value : currencyFormatter.format(num);
}

export function formatNumber(value: string | null): string {
  if (value === null || value === '') return '—';
  const num = Number(value);
  return Number.isNaN(num) ? value : num.toLocaleString('en-CA');
}
