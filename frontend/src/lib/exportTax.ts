import type { TaxSummary } from '../api/types';

function triggerDownload(filename: string, mimeType: string, content: string) {
  const blob = new Blob([content], { type: mimeType });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

function slug(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

function baseName(portfolioName: string, year: number): string {
  const portfolio = slug(portfolioName) || 'portfolio';
  return `tax-summary-${portfolio}-${year}`;
}

export function exportTaxSummaryJson(summary: TaxSummary, portfolioName: string) {
  triggerDownload(
    `${baseName(portfolioName, summary.year)}.json`,
    'application/json',
    JSON.stringify(summary, null, 2),
  );
}

function escapeCsv(value: string | number | null): string {
  const text = value === null ? '' : String(value);
  if (/[",\n]/.test(text)) {
    return `"${text.replace(/"/g, '""')}"`;
  }
  return text;
}

function toRow(cells: (string | number | null)[]): string {
  return cells.map(escapeCsv).join(',');
}

export function exportTaxSummaryCsv(summary: TaxSummary, portfolioName: string) {
  const lines: string[] = [];

  lines.push(`Tax summary,${portfolioName},${summary.year}`);
  lines.push('');

  lines.push('Realized gains & losses by security');
  lines.push(toRow(['Security', 'Dispositions', 'Proceeds', 'ACB disposed', 'Gain / loss']));
  for (const row of summary.realizedGains.rows) {
    lines.push(toRow([row.ticker, row.dispositions, row.proceeds, row.acbDisposed, row.gainLoss]));
  }
  const rg = summary.realizedGains.total;
  lines.push(toRow(['Total', rg.dispositions, rg.proceeds, rg.acbDisposed, rg.gainLoss]));
  lines.push('');

  lines.push('Dividend income by security');
  lines.push(toRow(['Security', 'Gross', 'Withholding', 'Net']));
  for (const row of summary.dividends.rows) {
    lines.push(toRow([row.ticker, row.gross, row.withholding, row.net]));
  }
  const dv = summary.dividends.total;
  lines.push(toRow(['Total', dv.gross, dv.withholding, dv.net]));
  lines.push('');

  lines.push('Smith Maneuver interest summary');
  lines.push(toRow(['Month', 'Charged', 'Deductible est.']));
  for (const row of summary.interest.months) {
    lines.push(toRow([row.month, row.charged, row.deductibleEstimate]));
  }
  const it = summary.interest.ytd;
  lines.push(toRow([it.month, it.charged, it.deductibleEstimate]));

  triggerDownload(
    `${baseName(portfolioName, summary.year)}.csv`,
    'text/csv',
    lines.join('\n'),
  );
}
