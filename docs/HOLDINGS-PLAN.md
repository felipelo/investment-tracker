# Holdings Page — Implementation Plan

Version: 1.0  
Status: Approved for implementation  
Date: 2026-06-16

Companion documents:

- Visual spec: [`mock/holdings.html`](../mock/holdings.html), [`DESIGN.md`](../DESIGN.md)
- Functional requirements: [`REQUIREMENTS.md`](../REQUIREMENTS.md) (§4.2 interim rules, §6 ACB, §9 deferred items)
- Prior vertical slice: Record Trade (`frontend/src/pages/RecordTradePage.tsx`)

This is the second vertical slice after Record Trade. It delivers the Holdings screen: current positions with ACB, manual prices, unrealized gain/loss, and per-security transaction history.

---

## 1. Screen overview

The mock defines two main regions plus a header action.

| Region | Content |
|--------|---------|
| **Page header** | Title “Holdings”, subtitle “Current positions · ACB per security”, primary button **Update prices** |
| **Positions table** | Security, Shares, ACB/share, Total ACB, Price, Market value, Unrealized — plus a **Cash** row (stub in v1) |
| **Drill-down card** | Selected security’s full transaction history with running ACB columns and action tags |

```
┌─────────────────────────────────────────────────────────────┐
│ Holdings                              [ Update prices ]     │
│ Current positions · ACB per security                        │
├─────────────────────────────────────────────────────────────┤
│ Positions table (click row to select)                       │
│   TSE:XEI  …  shares  ACB  price  market value  unrealized  │
│   Cash     …  coming soon (stub)                            │
│   Prices as of YYYY-MM-DD                    (.card-meta)   │
├─────────────────────────────────────────────────────────────┤
│ TSE:XEI — transaction history                               │
│   Date  Action  Shares  Price  ACB Δ  Total ACB  ACB/sh …  │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Resolved decisions

These were confirmed before implementation. Interim rules are also captured in [REQUIREMENTS.md §4.2](../REQUIREMENTS.md).

| # | Topic | Decision |
|---|-------|----------|
| 1 | Zero share balance | **Hide** — fully disposed securities do not appear in the holdings list |
| 2 | Price date display | **Yes** — show “as of {date}” in a `.card-meta` footnote under the positions table |
| 3 | Price update API | **One POST with an array** of `{ securityId, date, price }` entries |
| 4 | Portfolio scoping | **Global aggregation now** — all transactions in one implicit book; portfolio entity and switcher deferred ([REQUIREMENTS.md §9](../REQUIREMENTS.md)) |
| 5 | Cash row | **Stub “Coming soon”** in the UI until cash transactions and account balances exist ([REQUIREMENTS.md §9](../REQUIREMENTS.md)) |

---

## 3. Scope

### In scope (Holdings v1)

- Positions table for securities with non-zero share balance
- Running ACB per security ([REQUIREMENTS.md §6](../REQUIREMENTS.md))
- Manual price per security → market value and unrealized gain/loss
- Row selection → transaction history with computed ACB columns
- “Update prices” modal (batch entry)
- Loading, empty, and missing-price states
- Cash row UI stub

### Deferred (do not block this slice)

| Item | When |
|------|------|
| Portfolio entity + switcher | Portfolios slice — refactor holdings queries to filter by `portfolio_id` |
| Cash row with real balance | After cash transactions and account balance derivation |
| Superficial loss flags in UI | Tax summary slice (engine may compute; UI optional) |
| Sell validation (cannot go negative) | Transaction service hardening |
| Denied-loss adjustments | Extra DB column + engine input |
| Record Trade “Computed preview” wiring | Optional polish after engine exists (same history endpoint) |

### Assumption

Until Portfolios exist, holdings aggregate by `security_id` across all accounts and transactions. ACB is per security globally, not per portfolio.

---

## 4. Current codebase gap

| Layer | Exists today | Holdings needs |
|-------|--------------|----------------|
| **Backend** | `Security`, `Account`, `SecurityTransaction` CRUD | ACB engine, `price_snapshot` table, holdings API |
| **Frontend** | `/holdings` → `PlaceholderPage` | `HoldingsPage` + components wired to new APIs |
| **Shared** | Action tags, formatters in `lib/actions.ts` | Gain/loss formatter, holdings hooks |

Record Trade already notes that ACB preview awaits the backend engine (`RecordTradePage.tsx` computed preview card). Build the engine once; reuse on Holdings, then Tax summary and Dashboard.

---

## 5. Backend plan

### 5.1 ACB engine (pure module)

**Package:** `com.investmenttracker.acb`

```
AcbEngine.compute(List<SecurityTransactionInput>) → List<ComputedTransactionRow>
AcbEngine.summarize(rows) → HoldingSummary
```

**Input:** transactions for one security, ordered by date ASC, then id ASC as tie-break.

**Per-row computed fields** ([REQUIREMENTS.md §6.1–6.2](../REQUIREMENTS.md)):

| Field | Rule |
|-------|------|
| `shareChange` | Buy +shares; Sell −shares; Split × ratio; ROC / Reinv 0 |
| `shareBalance` | Running sum of share changes |
| `acbChange` | Buy: +(shares × price + commission); Sell: −(shares × prior ACB/share); ROC: −cash; Reinv: +cash; Split: 0 |
| `totalAcb` | Running sum of ACB changes, floored at 0 |
| `acbPerShare` | totalAcb ÷ shareBalance (0 when no shares) |
| `proceeds` | Sell only: shares × price − commission |
| `capitalGainLoss` | Sell only: proceeds − (shares × prior ACB/share) |

**Requirements:**

- Pure functions, `BigDecimal`, no Spring dependencies
- Deterministic — same inputs always produce same outputs
- Unit tests with cases from mock XEI history and `acb-tracker` workbook

**Mock XEI acceptance target** (once matching transactions are entered):

- Share balance: 1,420
- Total ACB: ~$45,639
- ACB/share: ~$32.14

### 5.2 Price snapshot persistence

**Liquibase:** `005-create-price-snapshot.yaml`

```sql
price_snapshot (
  id              BIGINT PK,
  security_id     BIGINT FK → security(id),
  snapshot_date   DATE NOT NULL,
  price           NUMERIC(18,4) NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL,
  UNIQUE (security_id, snapshot_date)
)
```

V1 uses the **latest snapshot per security** for market value. Snapshots are manual, append-only entries.

### 5.3 API endpoints

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/v1/holdings` | Current positions + latest price + unrealized |
| `GET` | `/api/v1/holdings/{securityId}/history` | Full computed transaction history for one security |
| `GET` | `/api/v1/price-snapshots` | List snapshots (optional `securityId` filter) |
| `POST` | `/api/v1/price-snapshots` | Batch create — body is an **array** of snapshots |

**`GET /holdings` response:**

```typescript
interface Holding {
  securityId: number;
  ticker: string;
  name: string;
  shareBalance: string;         // e.g. "1420.000000"
  acbPerShare: string;          // e.g. "32.14"
  totalAcb: string;             // e.g. "45639.00"
  latestPrice: string | null;   // null → market value unavailable
  priceDate: string | null;     // ISO date of latest snapshot
  marketValue: string | null;   // shareBalance × latestPrice
  unrealizedGainLoss: string | null;  // marketValue − totalAcb
}
```

- Exclude securities where `shareBalance == 0`
- Sort by ticker (or name) for stable table order

**`GET /holdings/{securityId}/history` response:**

```typescript
interface HoldingHistoryRow {
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
```

**`POST /price-snapshots` request:**

```typescript
interface CreatePriceSnapshotsRequest {
  snapshots: Array<{
    securityId: number;
    date: string;       // ISO YYYY-MM-DD, default today in UI
    price: string;      // decimal string
  }>;
}
```

Response: array of created snapshot records (or 201 with list). Upsert on `(security_id, snapshot_date)` is acceptable if the same date is re-submitted.

### 5.4 Service layer

```
HoldingService
├── listHoldings()
│     → load all transactions, group by security_id
│     → run AcbEngine per group, keep shareBalance > 0
│     → join latest price_snapshot per security
│     → compute marketValue, unrealizedGainLoss
└── getHistory(securityId)
      → load transactions for security, run AcbEngine

PriceSnapshotService
├── list(securityId?)
├── createBatch(snapshots)
└── getLatestBySecurity(securityId)
```

Holdings are **derived on read** — no `holding` table in v1. Transaction CRUD already exists; holdings API always reflects current data.

### 5.5 Tests

| Test class | Cases |
|------------|-------|
| `AcbEngineTest` | Buy, Sell, ROC, Reinvested Distribution, Split; full XEI sequence from mock |
| `HoldingServiceTest` / controller integration | Seed transactions → `GET /holdings` returns expected balances |
| Edge cases | Zero shares after sell, ACB floor at 0, split 2:1 |

---

## 6. Frontend plan

### 6.1 New files

```
frontend/src/
├── pages/HoldingsPage.tsx
├── components/
│   ├── HoldingsTable.tsx
│   ├── HoldingHistoryTable.tsx
│   └── UpdatePricesModal.tsx
├── api/
│   ├── types.ts          # + Holding, HoldingHistoryRow, PriceSnapshot, batch request
│   └── hooks.ts          # + useHoldings, useHoldingHistory, useCreatePriceSnapshots
└── lib/
    └── actions.ts        # + formatGainLoss (sign, .positive / .negative)
```

### 6.2 `HoldingsPage`

Mirror mock layout and [DESIGN.md §5.2](../DESIGN.md):

- `.page-header` with title, subtitle, `.btn-primary` “Update prices”
- `HoldingsTable` in a `.card`
- `HoldingHistoryTable` in a second `.card` when a security is selected
- Default selection: first holding row on load (matches mock showing XEI history)

### 6.3 `HoldingsTable`

| Column | Source | Formatting |
|--------|--------|------------|
| Security | `ticker` + `name` | `.ticker` + muted subtitle |
| Shares | `shareBalance` | `.mono`, `formatNumber` |
| ACB/share, Total ACB | computed from API | `.mono`, `formatMoney` |
| Price | `latestPrice` | `formatMoney` or “—” |
| Market value | `marketValue` | `formatMoney` or muted “No price” |
| Unrealized | `unrealizedGainLoss` | `.mono`, `formatGainLoss` (+/−, `.positive` / `.negative`) |

**Cash stub row** (last row, not selectable):

- Ticker: “Cash”
- Subtitle: “Account balances” (or similar)
- Shares / ACB / Price / Unrealized: “—”
- Market value: muted “Coming soon”

**Footer:** `.card-meta` — e.g. “Prices as of 2026-06-16” using the latest `priceDate` across displayed holdings (or “No prices recorded” if none).

**Interaction:** row click selects security; highlight with `var(--bg-subtle)` (same pattern as `RecentTransactions.tsx`).

### 6.4 `HoldingHistoryTable`

Reuse patterns from `RecentTransactions.tsx`:

- Action tags via `actionMeta()` from `lib/actions.ts`
- Columns: Date, Action, Shares, Price, ACB change, Total ACB, ACB/share, Notes
- ACB change: signed money (`+$15,610`, `−$420`)
- Card title: `{ticker} — transaction history`

### 6.5 `UpdatePricesModal`

- Lists current holdings with a price input per security
- Pre-fills `latestPrice` when present
- Shared date field (default today) applied to all entries, or per-row date if simpler
- Submit → `POST /price-snapshots` with `{ snapshots: [...] }`
- On success: close modal, invalidate `holdings` query

No new UI library — plain modal with existing design tokens.

### 6.6 States

| State | UI |
|-------|-----|
| Loading | Muted “Loading…” in card |
| No positions | “No holdings yet. Record a trade to get started.” + link to `/record-trade` |
| Missing prices | “—” for Price / Market / Unrealized; optional `.banner-info`: “Enter prices to see market value and unrealized gain.” |
| API error | Red message in card |

### 6.7 Routing and cache invalidation

**`App.tsx`:** replace `PlaceholderPage` on `/holdings` with `HoldingsPage`.

**Invalidate `holdings`** when transactions are created, updated, or deleted:

```typescript
// extend useCreateTransaction / useUpdateTransaction / useDeleteTransaction onSuccess
queryClient.invalidateQueries({ queryKey: ['holdings'] });
```

---

## 7. Data flow

```
HoldingsPage
    │
    ├─ GET /holdings ──────────────────► HoldingService
    │                                        ├─ group transactions by security
    │                                        ├─ AcbEngine.summarize()
    │                                        └─ join latest price_snapshot
    │
    ├─ GET /holdings/{id}/history ─────► AcbEngine.compute(full history)
    │
    └─ POST /price-snapshots (batch) ──► PriceSnapshotService → refetch holdings
```

---

## 8. Implementation phases

Execute in order — backend before frontend.

| Phase | Work | Verify |
|-------|------|--------|
| **A** | `AcbEngine` + unit tests | XEI mock sequence matches expected ACB/share |
| **B** | `HoldingService` + `HoldingController` + DTOs | Integration test: seeded txns → correct `GET /holdings` |
| **C** | `price_snapshot` migration + `PriceSnapshotService` + batch POST | Market value and unrealized populate after price entry |
| **D** | Frontend: types, hooks, `HoldingsTable`, `HoldingHistoryTable`, `HoldingsPage`, `UpdatePricesModal` | Screen matches mock layout and DESIGN tokens |
| **E** | Polish: query invalidation, `formatGainLoss`, cash stub, `.card-meta` date, manual QA | Success criteria checklist below |

**Rough estimate:** ~4 days focused work.

---

## 9. Success criteria

- [ ] Positions table matches [`mock/holdings.html`](../mock/holdings.html) structure and [DESIGN.md](../DESIGN.md) tokens (`.card`, `.table-wrap`, `.mono`, `.positive` / `.negative`)
- [ ] Securities with zero share balance are not listed
- [ ] XEI sample data produces ACB/share ≈ $32.14 and total ACB ≈ $45,639
- [ ] Row click shows transaction history with running ACB columns
- [ ] “Update prices” persists via batch POST and refreshes market value / unrealized
- [ ] `.card-meta` shows “Prices as of {date}” when snapshots exist
- [ ] Missing price shows “—”, not a misleading zero
- [ ] Cash row shows “Coming soon” stub
- [ ] `AcbEngine` has unit tests independent of Spring
- [ ] No portfolio switcher required in this slice

---

## 10. Future dependencies

| Later slice | Builds on Holdings |
|-------------|-------------------|
| Record Trade computed preview | `GET /holdings/{id}/history` (last row) |
| Dashboard allocation | `GET /holdings` + market values |
| Tax summary | ACB engine + `capitalGainLoss` on sells |
| Portfolios | Add `portfolio_id` to accounts/transactions; filter holdings queries |
| Cash row (real data) | Cash transactions + account balance service |

**Refactor note:** when Portfolio management ships, replace global aggregation with portfolio-scoped queries and add the header portfolio switcher per [DESIGN.md §5.2](../DESIGN.md).

---

## 11. Changelog

| Version | Date | Notes |
|---------|------|-------|
| 1.0 | 2026-06-16 | Initial plan; open questions resolved; REQUIREMENTS.md §4.2 and §9 updated |
