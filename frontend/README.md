# Investment Tracker — Frontend

A single-page application (SPA) that consumes the Spring Boot REST API in
[`../backend`](../backend) and renders the screens described in
[`../DESIGN.md`](../DESIGN.md).

This is the first working **vertical slice**: the app shell, navigation, the
typed API layer, and one fully wired screen — **Record trade**. Other screens
exist as placeholders for now.

---

## Stack decisions and rationale

The stack was chosen to be approachable for someone who is not a daily frontend
developer, while staying modern and well-supported.

| Choice | Why |
|--------|-----|
| **[Vite](https://vite.dev/)** | Fast dev server and build. The current default for new React apps (replaces Create React App / manual Webpack). |
| **React + TypeScript** | Most popular UI framework, so tutorials and AI assistance are abundant. TypeScript types our API models and catches mistakes early. |
| **[TanStack Query](https://tanstack.com/query)** | Handles API calls, caching, and loading/error states with minimal boilerplate, instead of hand-rolling `useEffect` + `fetch` everywhere. |
| **[React Router](https://reactrouter.com/)** | Client-side routing for the sidebar navigation. |
| **Plain CSS via `identity.css`** | We reuse the existing design system (`mock/css/identity.css`) verbatim as a global stylesheet. This reproduces the agreed look exactly and keeps the learning curve low. Tailwind or a component library can be adopted later if desired. |

No authentication: the app is single-user and local, matching the backend
(`REQUIREMENTS.md` §7).

---

## Architecture

The SPA talks to the backend REST API. In development, Vite proxies any request
starting with `/api` to the backend on port 8080, so the browser sees a single
origin and we avoid CORS (the backend has no CORS config today).

```
React SPA (Vite :5173)  --/api/v1/*-->  Vite dev proxy  -->  Spring Boot (:8080)  -->  Postgres
```

The proxy is configured in [`vite.config.ts`](vite.config.ts).

---

## Project structure

```
frontend/
├── index.html              # HTML entry point
├── vite.config.ts          # Vite config + /api dev proxy
├── src/
│   ├── main.tsx            # App bootstrap (React Query + Router providers)
│   ├── App.tsx             # Route definitions
│   ├── api/
│   │   ├── types.ts        # TypeScript mirrors of backend DTOs
│   │   ├── client.ts       # fetch wrapper + RFC 7807 ProblemDetail parsing
│   │   └── hooks.ts        # TanStack Query hooks (securities, accounts, transactions)
│   ├── components/
│   │   ├── AppShell.tsx        # Sidebar + main layout
│   │   └── RecentTransactions.tsx
│   ├── pages/
│   │   ├── RecordTradePage.tsx # The built screen
│   │   └── PlaceholderPage.tsx # "Coming soon" for unbuilt screens
│   ├── lib/
│   │   └── actions.ts      # Action metadata, tag colors, formatters
│   └── styles/
│       └── identity.css    # Design system (copied from mock/css/identity.css)
```

---

## Running it

The backend must be running first, since the frontend proxies to it.

1. **Start the backend** (from the repo root):

   ```bash
   cd backend
   docker compose up -d                                   # Postgres on :5432
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=local # API on :8080
   ```

2. **Start the frontend** (from this directory):

   ```bash
   npm install        # first time only
   npm run dev
   ```

   Open <http://localhost:5173>. It redirects to **Record trade**.

### Other scripts

- `npm run build` — type-check and produce a production build in `dist/`.
- `npm run preview` — serve the production build locally.

---

## Scope status

**Built in this slice**

- App shell with sidebar navigation and routing.
- Typed API client with validation-error parsing.
- **Record trade** screen: live security/account dropdowns, action-conditional
  fields (Buy/Sell, Return of Capital, Reinvested Distribution, Split), submit
  via `POST /api/v1/security-transactions` with inline field validation, and a
  recent-transactions table that refreshes on save.

**Out of scope for now**

- Dashboard, Holdings, Portfolios, Smith Maneuver, and Tax summary screens
  (placeholders only).
- The "Computed preview" ACB card on Record trade — the backend does not compute
  ACB yet, so it shows a note instead of numbers.
- Portfolio switcher, charts, authentication, and production deployment config.
