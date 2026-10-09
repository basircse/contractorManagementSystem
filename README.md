# CCMS — Civil Contractor Management System

Spring Boot 3.3 (Java 21) + MySQL 8 API, Vue 3 + PrimeVue web app. Bangla is the default
language (Bangla digits, ৳, lakh grouping); English is one click away.

## Run locally

Prerequisites: JDK 21, Maven 3.9, Node 22, Docker.

```bash
docker compose up -d                      # MySQL 8 on localhost:3307
```

```bash
cd backend && mvn spring-boot:run         # API on :8080, Flyway creates the schema
```

Maven must run on JDK 21 — set `JAVA_HOME` to a JDK 21 install if `mvn -v` shows another version.

```bash
cd frontend && npm install && npm run dev # http://localhost:5173 (proxies /api to :8080)
```

Optional demo data (a contractor with sites, 20 workers, two weeks of attendance, a quotation →
work order with monthly instalments, bills and receipts, suppliers, purchases, hired shuttering,
sub-contracts and site expenses). Safe to re-run:

```bash
node scripts/seed-demo.mjs
```

Dev logins: `admin` / `Admin@12345` (bootstrap admin, from `application.yml`) and, after seeding,
`demo` / `Demo@12345`. Override `ADMIN_PASSWORD`, `JWT_SECRET` and `DB_*` in every non-dev environment.

API docs: http://localhost:8080/swagger-ui.html

Running a second copy side by side (e.g. for testing): start the API with `SERVER_PORT=8081` and
`CORS_ORIGINS=http://localhost:5173,http://localhost:5174`, and the web app with
`API_PROXY=http://localhost:8081 npm run dev -- --port 5174`. The seed script takes `API=http://localhost:8081`.

## Roles

| Role | Can do |
| --- | --- |
| **Admin** (platform) | Create / edit contractors, block & unblock them (takes effect immediately, even for open sessions), reset passwords, open any contractor's business **read-only** ("ব্যবসা দেখুন"). |
| **Contractor** | Everything inside their own business: project structure, labour, wage rates, daily attendance, wage payments, quotations, work orders, client bills & collections, third parties, purchases, hired items, sub-contracts, site expenses, reports. |

## Architecture notes

- **Multi-tenant from day one (SaaS-ready).** Each contractor is a tenant; every business table has
  `contractor_id`, populated and filtered by Hibernate `@TenantId`. Native report SQL binds the tenant
  explicitly. `contractor.plan` is reserved for subscription plans.
- **Cost ledger (`cost_entry`).** Every cost is posted to one append-only ledger: wages (rate
  snapshotted), material purchases (per material), rent of hired items, sub-contract bills and site
  expenses — each tagged to site / building / floor / unit, an optional work item and the third party.
  Edits post reversal rows instead of updating history, so reports are plain sums and the trail is auditable.
- **Income.** Quotation (BOQ) → work order (lines, retention %, discount) → billing plan (milestones or
  weekly / monthly instalments) → client bills (by milestone, by measured quantity against work-order
  lines, or free lines; net = gross − retention − VAT/AIT) → receipts against a bill or as an advance.
  Only submitted bills count as income; printed bills show previous / cumulative billing and the amount in words.
- **Payables.** What is owed to a party = its ledger charges − party payments; the statement shows a running balance.
- **Hired items.** Rent = quantity still out × rate per day × days, posted when charged (e.g. month end)
  and when items are returned (partial returns supported, last entry can be undone). Rate 0 = borrowed.
- **Wage formula** (`WageCalculator`): `dayFraction × (dailyRate + skillAllowance) + otHours × otHourlyRate`.
  OT hours are entered manually per work slice. Site-specific rate cards override general ones.
- **Admin read-only** is enforced server-side: admin may only `GET /api/app/**`, with the
  `X-Contractor-Id` header selecting the business.

## Status

Done: auth, admin contractor management, tenancy, project hierarchy, work-item & material catalogs,
labour & rate cards, item-tagged attendance, wage payments/advances & dues, quotations, work orders,
billing plans, client bills & collections, third parties & payables, material purchases, hired /
borrowed items, sub-contracts, site expenses, item-wise cost (by source), profit & loss by site and
work item, labour deployment history, dashboard (income, receivables, overdue, payables).

Next: offline PWA attendance, SaaS billing, native app.
