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
  { value: 'RETURN_OF_CAPITAL', label: 'ROC', tagClass: 'tag-butter' },
  {
    value: 'REINVESTED_DISTRIBUTION',
    label: 'DRIP',
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

// Per-share trade prices can carry up to 4 decimals (e.g. 4.9734).
const pricePerShareFormatter = new Intl.NumberFormat('en-CA', {
  style: 'currency',
  currency: 'CAD',
  minimumFractionDigits: 2,
  maximumFractionDigits: 4,
});

export function formatPricePerShare(value: string | null): string {
  if (value === null || value === '') return '—';
  const num = Number(value);
  return Number.isNaN(num) ? value : pricePerShareFormatter.format(num);
}

export function formatNumber(value: string | null): string {
  if (value === null || value === '') return '—';
  const num = Number(value);
  return Number.isNaN(num) ? value : num.toLocaleString('en-CA');
}

export interface SignedMoney {
  text: string;
  className: 'positive' | 'negative' | '';
}

// Signed currency for gain/loss columns: "+$2,357" / "−$420" with the matching
// .positive / .negative color class (DESIGN.md section 2.3).
export function formatGainLoss(value: string | null): SignedMoney {
  if (value === null || value === '') return { text: '—', className: '' };
  const num = Number(value);
  if (Number.isNaN(num)) return { text: value, className: '' };

  const magnitude = currencyFormatter.format(Math.abs(num));
  if (num > 0) return { text: `+${magnitude}`, className: 'positive' };
  if (num < 0) return { text: `−${magnitude}`, className: 'negative' };
  return { text: magnitude, className: '' };
}

// Signed percentage for return columns: "+14.7%" / "−3.2%".
export function formatPercent(value: string | null): SignedMoney {
  if (value === null || value === '') return { text: '—', className: '' };
  const num = Number(value);
  if (Number.isNaN(num)) return { text: value, className: '' };

  const magnitude = `${Math.abs(num).toFixed(1)}%`;
  if (num > 0) return { text: `+${magnitude}`, className: 'positive' };
  if (num < 0) return { text: `−${magnitude}`, className: 'negative' };
  return { text: magnitude, className: '' };
}
