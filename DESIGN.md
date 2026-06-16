# Investment Tracker — Visual Design

Version: 0.1  
Status: Established from HTML mock (`mock/`). Canonical reference for UI consistency.  
Companion: functional requirements in [REQUIREMENTS.md](./REQUIREMENTS.md).

This document describes the look and feel of the Investment Tracker. When adding screens, widgets, or components, follow these rules so the app stays cohesive. The CSS implementation lives in [`mock/css/identity.css`](mock/css/identity.css); update that file first, then reflect changes here.

---

## 1. Design principles

- **Pastel, modern, minimal** — soft colors on a warm neutral canvas; no heavy borders or loud accents.
- **Calm data density** — financial numbers are prominent; labels and chrome stay quiet.
- **Local & personal** — feels like a private desk tool, not a brokerage marketing site.
- **Readable at a glance** — gain/loss, tags, and key metrics use color sparingly and consistently.
- **Implementation-agnostic** — tokens and patterns map cleanly to CSS variables today and to a component library later (e.g. React + Tailwind).

---

## 2. Color system

All colors are defined as CSS custom properties in `identity.css`. Use the token names in code; hex values below are the reference.

### 2.1 Surfaces and text

| Token | Hex | Usage |
|-------|-----|--------|
| `--bg` | `#f7f5f2` | Page background (warm off-white) |
| `--bg-elevated` | `#ffffff` | Cards, sidebar, inputs on elevated surfaces |
| `--bg-subtle` | `#eeeae4` | Hover states, nested panels, inactive chips |
| `--text` | `#2e3338` | Primary body and headings |
| `--text-muted` | `#6b7280` | Subtitles, labels, table headers |
| `--text-faint` | `#9ca3af` | Nav section titles, de-emphasized hints |
| `--border` | `rgba(46, 51, 56, 0.08)` | Card and table borders |
| `--border-strong` | `rgba(46, 51, 56, 0.14)` | Input borders, ghost buttons |

### 2.2 Pastel accent palette

Each accent has a **light** (backgrounds, tags, fills) and **deep** (charts, primary actions, legend dots) variant.

| Name | Light token | Light hex | Deep token | Deep hex |
|------|-------------|-----------|------------|----------|
| Sage | `--sage` | `#b8d4c8` | `--sage-deep` | `#7ba69e` |
| Lavender | `--lavender` | `#d4c5e8` | `--lavender-deep` | `#9b87b8` |
| Peach | `--peach` | `#f5d5c8` | `--peach-deep` | `#d4a088` |
| Sky | `--sky` | `#c5d9e8` | `--sky-deep` | `#7a9eb8` |
| Butter | `--butter` | `#f5e6c8` | `--butter-deep` | `#c9b07a` |
| Blush | `--blush` | `#f0c8d4` | `--blush-deep` | `#c98a9e` |

**Rules:**

- Use **light** pastels for tags, nav active state, bar chart fills, and banner backgrounds.
- Use **deep** pastels for primary buttons, chart strokes, and legend markers.
- Do not introduce new accent hues without updating this table and `identity.css`.
- Prefer **sage** as the default “brand” accent (primary button, active nav, main chart series).

### 2.3 Semantic colors

| Token | Hex | Usage |
|-------|-----|--------|
| `--gain` | `#5a9a7a` | Positive returns, gains (text) |
| `--gain-bg` | `#e4f0ea` | Positive background pills |
| `--loss` | `#c47070` | Losses, negative returns (text) |
| `--loss-bg` | `#faeaea` | Negative background pills |
| `--warn` | `#c9a04a` | Warning emphasis |
| `--warn-bg` | `#faf3e0` | Warning banners, superficial-loss flags |

Apply via classes `.positive`, `.negative`, `.positive-bg`, `.negative-bg`, `.banner-warn`, `.banner-info`.

**Rules:**

- Gains and losses are **muted**, not neon green/red.
- Never use semantic red/green for non-financial decoration.
- Warnings use butter-toned background (`--warn-bg`), not harsh yellow.

### 2.4 Brand mark

The logo block (`.brand-mark`) uses a **135° gradient**: sage → lavender → peach. Text initials `IT` in `--text`. Size 36×36px, radius 10px.

---

## 3. Typography

| Role | Font | Size | Weight | Notes |
|------|------|------|--------|-------|
| Font family | DM Sans | — | — | Fallback: `system-ui`, `-apple-system`, sans-serif |
| Page title | — | `1.625rem` (26px) | 600 | `letter-spacing: -0.03em` |
| Page subtitle | — | `0.9375rem` (15px) | 400 | `--text-muted` |
| Card title (label) | — | `0.8125rem` (13px) | 600 | Uppercase optional; always muted |
| Card value (hero stat) | — | `1.75rem` (28px) | 600 | Tabular numbers where possible |
| Card value (secondary) | — | `1.25rem` (20px) | 600 | `.card-value-sm` |
| Body / table | — | `0.875rem` (14px) | 400–500 | Line height `1.5` |
| Nav link | — | `0.875rem` | 500 | |
| Nav section | — | `0.6875rem` (11px) | 600 | Uppercase, `letter-spacing: 0.06em` |
| Tag | — | `0.6875rem` | 600 | Pill shape |
| Form label | — | `0.75rem` | 600 | Uppercase, `letter-spacing: 0.04em` |
| Table header | — | `0.6875rem` | 600 | Uppercase, `letter-spacing: 0.05em` |

**Numeric data:** use `.mono` or `.ticker` — `font-variant-numeric: tabular-nums` so columns align.

---

## 4. Spacing, radius, and elevation

| Token | Value | Usage |
|-------|-------|--------|
| `--radius-sm` | `8px` | Buttons, inputs, tags, flow steps |
| `--radius` | `14px` | Cards |
| `--radius-lg` | `20px` | Reserved for modals / large panels |
| `--sidebar-w` | `240px` | Fixed sidebar width |
| Main padding | `2rem 2.5rem` | Content area (reduces to `1.25rem` on mobile) |
| Card padding | `1.25rem 1.5rem` | Standard card interior |
| Grid gap | `1.25rem` | Between cards (period cards: `1rem`) |

**Shadows:**

- `--shadow` — default card elevation (subtle, soft).
- `--shadow-hover` — hover on primary buttons and hub cards only; avoid on every interactive element.

**Borders:** prefer `1px solid var(--border)` on cards; stronger border only for inputs and ghost buttons.

---

## 5. Layout

### 5.1 App shell

Every authenticated-style screen uses:

```
.app-shell
├── .sidebar          (fixed 240px, white, right border)
│   ├── .sidebar-brand (+ .brand-mark, .brand-text, .brand-sub)
│   └── nav with .nav-section + .nav-link
└── .main             (flex grow, padded content)
    ├── .page-header   (title block + actions)
    └── page content (grids, cards, tables)
```

Reference: any file under `mock/*.html` except `index.html`.

### 5.2 Page header

- Left: `.page-title` + `.page-subtitle` (context: portfolio name, date, tax year).
- Right: primary actions (`.btn`) and/or `.portfolio-switcher` (tag + portfolio name + dropdown affordance).

### 5.3 Grids

| Class | Columns | Use |
|-------|---------|-----|
| `.grid-2` | 2 | Form pairs, side-by-side summaries |
| `.grid-3` | 3 | Hero stat row (portfolio value, today, all-time) |
| `.grid-4` | 4 | Period return cards |
| `.grid-dashboard` | 2 | Dashboard chart row (allocation + dividends) |

Breakpoints (in CSS):

- **≤1100px:** `.grid-4` → 2 columns; `.grid-dashboard` → 1 column.
- **≤768px:** sidebar stacks on top; all grids → 1 column.

---

## 6. Components

### 6.1 Cards (`.card`)

White surface, soft shadow, 14px radius. Structure:

1. `.card-title` — short label (muted).
2. Primary content (value, chart, table).
3. Optional `.card-meta` — footnote (data source, date).

Nested cards (e.g. computed preview inside a form) use `background: var(--bg)` and no extra shadow.

### 6.2 Tags (`.tag`, `.tag-*`)

Pill badges for portfolio types, transaction actions, status.

| Class | Color | Typical meaning |
|-------|-------|-----------------|
| `.tag-sage` | Sage | Buy, Active, Traced, primary status |
| `.tag-lavender` | Lavender | Smith Maneuver, Reinvested Distribution |
| `.tag-peach` | Peach | Taxable, warnings (with warn text color) |
| `.tag-sky` | TFSA, Split |
| `.tag-butter` | RRSP, Return of Capital |

Portfolio type → tag mapping (consistent across screens):

| Portfolio type | Tag class |
|----------------|-----------|
| Smith Maneuver | `tag-lavender` |
| TFSA | `tag-sky` |
| Taxable | `tag-peach` |
| RRSP | `tag-butter` |
| Other / generic active | `tag-sage` |

### 6.3 Buttons (`.btn`)

| Variant | Class | Appearance |
|---------|-------|------------|
| Primary | `.btn-primary` | `--sage-deep` background, white text |
| Ghost | `.btn-ghost` | Transparent, `--border-strong` border |

One primary action per header region. Secondary actions use ghost style.

### 6.4 Tables

Wrap in `.table-wrap` for horizontal scroll. Headers uppercase and muted. Row hover: `--bg` fill. Security symbols: `.ticker` (bold). Amounts: `.mono`.

### 6.5 Forms

- `.form-grid` — two columns; full-width fields span 2 columns.
- Labels: uppercase, muted, small.
- Inputs: `--bg` fill, `--radius-sm`, focus ring `2px solid var(--sage)`.

### 6.6 Banners (`.banner`)

| Class | Use |
|-------|-----|
| `.banner-info` | Disclaimers, neutral notices (sky background) |
| `.banner-warn` | Untraced funds, superficial loss review (warn background) |

Icon + text; left-aligned; full width above main content.

### 6.7 Smith Maneuver flow (`.flow-chain`)

Horizontal chain: `.flow-step` boxes joined by `.flow-arrow` (→). Active steps use `.flow-step.active` (sage fill). Labels: `.flow-label` (uppercase micro), amounts: `.flow-amount`.

### 6.8 Period return cards (`.period-card`)

Nested inside a parent `.card`. Top border 3px in accent color by position:

1. Sage — 5 Days  
2. Lavender — One Month  
3. Peach — Six Month  
4. Sky — One Year  

Center-aligned label + dollar value + percentage.

### 6.9 Charts (dashboard)

**Allocation donut:** stroke widths 20px on 120×120 viewBox; series colors use **deep** pastels in order: sage, lavender, peach, sky. Legend: `.legend` + `.legend-dot`.

**Dividends chart:** bars in `--sage` (current month: `--sage-deep`); cumulative line `--lavender-deep`; future/empty months: `--bg-subtle` at reduced opacity.

Chart colors for new series: cycle through deep pastels in table order (Section 2.2); do not reuse semantic gain/loss colors for chart segments.

---

## 7. Navigation

Sidebar sections (order matters):

1. **Overview** — Dashboard, Holdings, Portfolios  
2. **Records** — Record trade, Smith Maneuver, Tax summary  
3. **Mock** — All screens (hub link; omit in production)

Active route: `.nav-link.active` (sage background). Hover: `--bg-subtle`.

When adding a new screen, add a nav link in the same section across all mock pages (or extract a shared partial when moving to a templated stack).

---

## 8. Screen inventory (reference mocks)

| File | Purpose |
|------|---------|
| [`mock/index.html`](mock/index.html) | Hub + palette swatches |
| [`mock/dashboard.html`](mock/dashboard.html) | §5 dashboard widgets |
| [`mock/holdings.html`](mock/holdings.html) | Positions + ACB history |
| [`mock/transactions.html`](mock/transactions.html) | Trade entry form |
| [`mock/smith-maneuver.html`](mock/smith-maneuver.html) | Flow tracing + HELOC |
| [`mock/tax-summary.html`](mock/tax-summary.html) | Tax year export view |
| [`mock/portfolios.html`](mock/portfolios.html) | Portfolio list / switcher |

Open `mock/index.html` in a browser to preview the full set.

---

## 9. Checklist for new screens

1. Link `css/identity.css` (or port tokens to the app’s global styles).
2. Use `.app-shell` + sidebar + `.main` unless the screen is a standalone modal or wizard step.
3. Add `.page-header` with title, subtitle, and at most one `.btn-primary`.
4. Place content in `.card` containers; use existing grids before inventing new layouts.
5. Map portfolio types and transaction actions to the tag colors in Section 6.2.
6. Format money and percentages with `.mono`; color gains/losses with `.positive` / `.negative`.
7. Use `.banner-info` or `.banner-warn` for disclaimers and data warnings (e.g. missing snapshots per REQUIREMENTS §5.1).
8. Update this document if you add a new reusable component or token.

---

## 10. Out of scope (for now)

Documented here for awareness; not part of v0.1 visual spec:

- Dark mode (would need inverted surface tokens and adjusted pastels).
- Icon set (nav uses text only in mocks).
- Motion beyond subtle hover/active (no page transitions specified).
- Print styles and official CRA form layouts.

---

## 11. Changelog

| Version | Date | Notes |
|---------|------|-------|
| 0.1 | 2026-06-10 | Initial spec from HTML mock and `identity.css` |
