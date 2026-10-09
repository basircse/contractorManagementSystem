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

Optional demo data (a contractor with sites, 20 workers and two weeks of attendance):

```bash
node scripts/seed-demo.mjs
```

Dev logins: `admin` / `Admin@12345` (bootstrap admin, from `application.yml`) and, after seeding,
`demo` / `Demo@12345`. Override `ADMIN_PASSWORD`, `JWT_SECRET` and `DB_*` in every non-dev environment.

API docs: http://localhost:8080/swagger-ui.html

## Roles

| Role | Can do |
| --- | --- |
| **Admin** (platform) | Create / edit contractors, block & unblock them (takes effect immediately, even for open sessions), reset passwords, open any contractor's business **read-only** ("ব্যবসা দেখুন"). |
| **Contractor** | Everything inside their own business: project structure, labour, wage rates, daily attendance, payments, reports. |

## Architecture notes

- **Multi-tenant from day one (SaaS-ready).** Each contractor is a tenant; every business table has
  `contractor_id`, populated and filtered by Hibernate `@TenantId`. Native report SQL binds the tenant
  explicitly. `contractor.plan` is reserved for subscription plans.
- **Cost ledger (`cost_entry`).** Attendance is posted to an append-only ledger with the wage rate
  snapshotted. Edits post reversal rows instead of updating history, so reports are plain sums and the
  trail is auditable. Vendor / material costs will post to the same ledger.
- **Wage formula** (`WageCalculator`): `dayFraction × (dailyRate + skillAllowance) + otHours × otHourlyRate`.
  OT hours are entered manually per work slice. Site-specific rate cards override general ones.
- **Admin read-only** is enforced server-side: admin may only `GET /api/app/**`, with the
  `X-Contractor-Id` header selecting the business.

## Status

Done (phases 0–3 of the plan): auth, admin contractor management, tenancy, project hierarchy,
work-item catalog, labour & rate cards, item-tagged attendance, wage payments/advances & dues,
item-wise cost report, labour deployment history, dashboard.

Next: vendors & shuttering (FR-4), quotation/BOQ → work order → client billing (FR-5),
profitability per item (FR-6), offline PWA attendance, SaaS billing, native app.
