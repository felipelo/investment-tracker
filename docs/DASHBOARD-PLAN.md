# Dashboard Page — Implementation Plan

Version: 1.0
Status: Approved for implementation
Date: 2026-06-17

Companion documents:

- Visual spec: [`mock/dashboard.html`](../mock/dashboard.html), [`DESIGN.md`](../DESIGN.md) (§5 layout, §6.8 period cards, §6.9 charts)
- Functional requirements: [`REQUIREMENTS.md`](../REQUIREMENTS.md) (§5 dashboard, §3.11 snapshots, §3.9 dividends, §6.2 derived values)
- Prior vertical slice: Holdings ([`docs/HOLDINGS-PLAN.md`](./HOLDINGS-PLAN.md))

This is the next vertical slice after Holdings. It delivers the per-portfolio Dashboard: allocation donut, hero stats (value, today, all-time), period-return cards, and a monthly dividends chart. It adds two new domain concepts the dashboard depends on — `portfolio_snapshot` (historical value) and `Dividend` (income).

---

## 1. Screen overview

The mock defines four widget regions plus the standard header.

| Region | Content |
|--------|---------|
| **Page header** | Title "Dashboard", subtitle (portfolio + snapshot date), portfolio switcher |
| **Hero stats** (`.grid-3`) | Portfolio value · Today's return · All-time return |
| **Period returns** (`.grid-4`) | 5 Days · One Month · Six Month · One Year cards |
| **Allocation** (`.grid-dashboard` left) | Donut by ticker + legend |
| **Dividends** (`.grid-dashboard` right) | Monthly bars + cumulative line + year selector |

---

## 2. Widget → data source

| Widget | Source |
|--------|--------|
| Allocation donut | `GET /holdings` `marketValue` per ticker |
| Portfolio value | `PortfolioMetrics.marketValue` |
| All-time return | `PortfolioMetrics.returnAmount + net dividends`, % over `invested` |
| Today's return | `currentMV − most recent prior portfolio_snapshot` |
| Period cards | `currentMV − nearest portfolio_snapshot ≤ (today − window)` |
| Dividends chart | `Dividend` rows aggregated by month for the selected year |

`PortfolioMetrics.returnAmount` already equals unrealized + realized capital gain (see [`HoldingSummary`](../backend/src/main/java/com/investmenttracker/acb/HoldingSummary.java)), so all-time return only needs dividends added.

---

## 3. Confirmed decisions

| # | Topic | Decision |
|---|-------|----------|
| 1 | Historical value | First-class `portfolio_snapshot` table (REQUIREMENTS §3.11). Auto-captured on every price update (current MV stamped at the batch date) **and** manually creatable. |
| 2 | Missing snapshot | Return widgets show "unavailable" via `.banner-info`; never a misleading zero (REQUIREMENTS §5.1). |
| 3 | Dividends | Build the `Dividend` entity (table + CRUD API + Record-dividend modal) so the chart and all-time return are real. |
| 4 | Dividend edits | Create / list / delete only this slice; edit deferred. |

---

## 4. Backend plan

### 4.1 portfolio_snapshot (Phase A)

- Migration `009-create-portfolio-snapshot.yaml`: `portfolio_snapshot (id, portfolio_id FK, snapshot_date DATE, market_value NUMERIC(18,4), created_at)`, unique `(portfolio_id, snapshot_date)`.
- `PortfolioSnapshot` entity, `PortfolioSnapshotRepository`, `PortfolioSnapshotService` (`list`, manual `create` upsert, `captureForSecurities`).
- Auto-capture hook inside `PriceSnapshotService.createBatch`: recompute MV for each affected portfolio and upsert a snapshot at the batch's max date.
- Endpoints: `GET/POST /api/v1/portfolios/{id}/snapshots`.

### 4.2 Dividend (Phase B)

- Migration `010-create-dividend.yaml`: `dividend (id, portfolio_id FK, security_id FK, payment_date DATE, gross_amount, withholding_tax, currency, drip, notes, created_at)`. Net = gross − withholding (derived).
- `Dividend` entity, `DividendRepository`, `DividendService` (`list`, `create`, `delete`, `summary`).
- DTOs `CreateDividendRequest`, `DividendResponse`.
- Endpoints: `GET /api/v1/dividends?portfolioId=`, `POST /api/v1/dividends`, `DELETE /api/v1/dividends/{id}`.

### 4.3 Dashboard aggregation (Phase C)

- `DashboardService` → `DashboardResponse` (allocation, portfolioValue, invested, allTimeReturn, todaysReturn, periodReturns[]).
- `DividendService.summary(portfolioId, year)` → months[12] net, cumulative[12], ytdTotal, availableYears[].
- Endpoints on `PortfolioController`: `GET /api/v1/portfolios/{id}/dashboard`, `GET /api/v1/portfolios/{id}/dividends/summary?year=`.

---

## 5. Frontend plan

### 5.1 New files

```
frontend/src/
├── pages/DashboardPage.tsx
├── components/
│   ├── HeroStats.tsx
│   ├── PeriodReturns.tsx
│   ├── AllocationDonut.tsx
│   ├── DividendsChart.tsx
│   └── RecordDividendModal.tsx
├── api/types.ts   # + DashboardData, Allocation, PeriodReturn, DividendSummary, Dividend, CreateDividend, PortfolioSnapshot
└── api/hooks.ts   # + useDashboard, useDividendSummary, useDividends, useCreateDividend, useDeleteDividend, usePortfolioSnapshots, useCreatePortfolioSnapshot
```

### 5.2 Charts

- **AllocationDonut**: SVG donut; `stroke-dasharray` from each pct; deep-pastel cycle (sage, lavender, peach, sky) per DESIGN §6.9; `.legend` + `.legend-dot`.
- **DividendsChart**: 12 month bars (`--sage`, current month `--sage-deep`, future `--bg-subtle`), cumulative polyline `--lavender-deep`, year `<select>`, YTD total.

### 5.3 States

| State | UI |
|-------|-----|
| No portfolio | Card with link to Portfolios |
| No holdings | Link to Record trade |
| Missing snapshot | `.banner-info`: record more price snapshots to see returns |
| Loading / error | Muted / red message in card |

---

## 6. Implementation phases

Execute in order — backend before frontend.

| Phase | Work | Verify |
|-------|------|--------|
| **A** | `portfolio_snapshot` migration + entity/repo/service + auto-capture + snapshot API | Price update creates a portfolio_snapshot; manual POST upserts |
| **B** | `dividend` migration + entity/repo/service + DTOs + CRUD API | Dividends persist; net derived; validation enforced |
| **C** | `DashboardService` + DTOs + endpoints; dividend summary | Seeded data → correct hero, period availability, allocation %, monthly buckets |
| **D** | Frontend types/hooks; `DashboardPage` + 4 widgets; route + switcher | Screen matches mock and DESIGN tokens |
| **E** | `RecordDividendModal` + invalidation + states + QA | All success criteria below |

---

## 7. Success criteria

- [ ] Dashboard matches [`mock/dashboard.html`](../mock/dashboard.html) structure and DESIGN tokens
- [ ] Allocation donut percentages sum to 100% across priced holdings
- [ ] All-time return includes realized gains + net dividends
- [ ] Today's and period returns compute from nearest stored portfolio snapshot; missing snapshots show a banner, not zero
- [ ] Dividends chart shows monthly net + cumulative line for the selected year
- [ ] Recording a dividend refreshes the chart and all-time return
- [ ] Updating prices auto-captures a portfolio snapshot
- [ ] Backend services covered by tests

---

## 8. Changelog

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | 2026-06-17 | Initial plan; decisions confirmed (portfolio_snapshot auto-capture, full Dividend entity) |
