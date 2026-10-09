# CCMS: Implementation Plan

Civil Contractor Management System. A Vue 3 web app and a Spring Boot REST API. The API is the only backend, so Android/iOS apps can be added later without backend rework.

---

## 1. Guiding decisions

| Decision | Choice | Why |
|---|---|---|
| Architecture | **Modular monolith**: one Spring Boot app with strict package-per-domain modules | Simple to deploy and run. Module boundaries are enforced (Spring Modulith), so a module can be split out later if needed. |
| API style | REST + JSON, versioned `/api/v1`, documented by **OpenAPI 3** (springdoc) | Web and mobile clients are generated from the same spec, so no hand-written HTTP code. |
| Auth | **Stateless JWT** (short-lived access token + rotating refresh token) | Works the same for browser, Android and iOS. No server sessions or cookies. |
| DB | **PostgreSQL 16** + **Flyway** migrations | Strong aggregate queries for reports, `NUMERIC` for money, row-level constraints. |
| Money | `NUMERIC(14,2)` / `BigDecimal` everywhere, currency set per company | No floating-point rounding errors in financial math. |
| Cost model | **Immutable cost ledger** (labour, vendor, material, all tagged by location and work item) | One table drives every report (FR-3.x, FR-6.x) and meets the auditability requirement. |
| Offline readiness | Client-generated **UUID ids** + **idempotency keys** + `updatedSince` sync endpoints, all from day one | Required later for offline-first mobile attendance. Retrofitting it later is expensive. |
| Tenancy | `company_id` on every business table from day one (single tenant at first) | Cheap now. Needed if this becomes SaaS for multiple contractors. |

### Tech stack

**Backend:** Java 21, Spring Boot 3.x, Spring Web, Spring Data JPA (Hibernate), Spring Security + OAuth2 Resource Server (JWT), Spring Modulith, Flyway, MapStruct, Jakarta Validation, springdoc-openapi, JasperReports or OpenPDF (PDF bills/quotations), Apache POI (Excel export), Testcontainers + JUnit 5, ArchUnit.

**Web:** Vue 3 (Composition API, `<script setup>`), TypeScript, Vite, Pinia, Vue Router, **PrimeVue** (data tables, forms, tree tables for the hierarchy), ECharts (dashboard), vue-i18n, VeeValidate + Zod, `openapi-typescript` + `openapi-fetch` (generated typed client), Vitest + Playwright.

**Infra:** Docker Compose (postgres, api, web/nginx) for development. GitHub Actions CI (build, test, OpenAPI diff check). S3-compatible storage (MinIO in dev) for attachments such as bill scans and site photos.

**Later, mobile:** Kotlin (Android) and Swift (iOS), or Flutter/KMP, using clients generated from the same OpenAPI spec. Local SQLite store with the sync endpoints below.

---

## 2. Repository layout

```
ContractorMS/
├── backend/                     # Spring Boot (Gradle Kotlin DSL)
│   └── src/main/java/com/ccms/
│       ├── shared/              # base entity, money, audit, errors, security utils
│       ├── identity/            # users, roles, site assignments, auth
│       ├── project/             # client, site, building, floor, unit
│       ├── catalog/             # work items (activities), UoM, skill tiers
│       ├── labour/              # labour profiles, rate cards, attendance, wages
│       ├── vendor/              # vendors, RFQ/bids, subcontracts, vendor bills
│       ├── shuttering/          # shuttering stock, dispatch/return, rental
│       ├── material/            # material purchase/issue (minimal, feeds FR-6.1)
│       ├── commercial/          # quotation/BOQ, work orders, client bills, receipts
│       ├── costing/             # cost ledger and allocation engine
│       ├── reporting/           # item-wise, deployment, est-vs-actual, P&L, dashboard
│       └── audit/               # append-only audit trail
├── web/                         # Vue 3 + Vite
│   └── src/{api,stores,modules/<domain>,components,layouts,router,i18n}
├── docs/                        # SRS, this plan, ADRs, ERD
└── docker-compose.yml
```

Each backend module has `api` (controllers + DTOs), `application` (services/use cases), `domain` (entities, rules) and `infrastructure` (repositories). Modules talk to each other only through public service interfaces or domain events. ArchUnit/Modulith tests enforce this.

---

## 3. Domain model (core tables)

### 3.1 Hierarchy and catalog
- `client`, `site (client_id)`, `building (site_id)`, `floor (building_id)`, `unit (floor_id)`. All have `status` (ACTIVE/ARCHIVED) and soft archive (FR-1.1).
- `work_item`: code, name, category, default UoM (sqft, m³, rft, nos). Seeded with: Piling/Foundation, Column Casting, Floor/Slab/Roof Casting, Brickwork, Plastering (Int), Plastering (Ext), Shuttering & Formwork, Tiles & Flooring, Painting & Finishing (FR-1.2). Admin-extendable.
- `work_task`: optional planned task = work item at a location, with planned qty, progress %, status. Drives milestone billing (FR-5.3) and client progress views.

### 3.2 Location tagging (FR-1.3)
Every cost-bearing row uses the same **location tag** embeddable:

```
site_id (required) | building_id? | floor_id? | unit_id? | work_item_id?
```

The tag is stored denormalized (all ancestor ids filled in) and validated for consistency on write. A cost with only `site_id` is site overhead. A cost with `unit_id + work_item_id` is execution cost. Any report can roll up at any level with a plain `GROUP BY`.

### 3.3 Labour (FR-2.x)
- `skill_tier`: HELPER, MASON, JUNIOR, SENIOR, EXPERT (a table, not an enum, so tiers can be configured).
- `labour`: name, phone, NID, skill tier, home site, status, photo, bank/mobile-wallet info (encrypted column).
- `rate_card`: skill_tier, `site_id?` (null = company default), daily_rate, ot_hourly_rate, skill_allowance, `effective_from/to`. The rate is resolved from the most specific match: labour override → site + tier → default + tier.
- `attendance` (header): labour_id, work_date, status (PRESENT/HALF/ABSENT/LEAVE), ot_hours, recorded_by, client UUID, device timestamp.
- `attendance_allocation`: attendance_id, **location tag + work_item_id**, `fraction` (0.25/0.5/1.0…), optional `ot_hours` tagged to the item.
  - Constraint: the sum of fractions per labour per date must be ≤ 1.0 **across all sites**. This blocks double-booking a worker at two sites.
- `wage_period`, `wage_sheet`, `wage_payment`, `labour_advance`: for wages paid, advances, and cross-site aggregation by the accountant.

### 3.4 Costing engine (FR-2.4)
When attendance is approved (or saved, depending on configuration), each allocation posts a row to `cost_ledger`:

```
amount = fraction × (daily_rate + skill_allowance)
       + OT_share
OT_share = item-tagged ot_hours × ot_rate, else (total ot_hours × ot_rate × fraction)  -- pro-rata default
```

`cost_ledger` columns: id, company_id, entry_date, **cost_type** (LABOUR / VENDOR / MATERIAL / SHUTTERING_RENTAL / OVERHEAD), location tag, work_item_id, amount, qty, **source_type + source_id**, rate snapshot (JSON), reversal_of_id, created_by, created_at.

- **Immutable:** no UPDATE or DELETE (enforced by DB grants and a trigger). A correction (wage adjustment, reallocating cost to another item) = reversal entry + new entry. This is the audit trail for reallocations required in §4.
- Rate values are **snapshotted** when posted, so a later rate-card change never silently rewrites history. A rate change can be applied to past dates only through an explicit "re-rate" action, which posts the adjustments.

### 3.5 Vendors and shuttering (FR-4.x)
- `vendor`: type (SUBCONTRACTOR / SUPPLIER), service categories (Column/Floor/Roof casting, Shuttering-Wood/Bamboo/Steel…).
- `rfq` → `vendor_bid` → award → `subcontract` (scope lines tagged to location + work item, rate, qty).
- `vendor_bill` + `vendor_bill_line` (tagged) → posts VENDOR cost; `vendor_payment`.
- Shuttering: `shuttering_material`, `shuttering_txn` (DISPATCH / RETURN / DAMAGE / LOSS, qty, site, vendor, linked casting `work_task`), rental rate per unit-day. On-site balance = SUM of txns. A rental accrual job posts SHUTTERING_RENTAL cost to the linked casting task. Damage charges post on return.

### 3.6 Commercial (FR-5.x)
- `quotation` (client, site, status: DRAFT → SENT → REVISED → ACCEPTED/REJECTED) with **`quotation_version`**. Each negotiation round is a new immutable version (FR-5.2 negotiation loop).
- `boq_line`: work_item, location scope, qty, UoM, client rate, plus **estimated cost split** (labour / material / vendor). The split is what makes the estimated-vs-actual report (FR-3.2) possible.
- `work_order`: created from the accepted version (lines copied and frozen).
- `billing_milestone`: tied to WO lines / `work_task` completion (e.g. "Piling complete – 20%").
- `client_bill` (type: MILESTONE / TIME_BASED / RUNNING_ACCOUNT) + `client_bill_line` (tagged to work item).
- `payment_receipt` + `receipt_allocation` (receipt → bill lines). "Collected per item" for FR-6.1 comes from these allocations.

### 3.7 Audit and identity
- `app_user`, `role`, `user_site_assignment` (site managers only see their sites), `client_user ↔ client`, `vendor_user ↔ vendor`.
- `audit_log` (append-only): who, when, entity, id, action, before/after JSON, IP/device. Written by a Hibernate event listener and kept separate from the cost ledger.

---

## 4. Security / RBAC

| Role | Access |
|---|---|
| ADMIN (owner) | Everything, including rates, P&L, quotation approval, configuration |
| SITE_MANAGER | Assigned sites only: hierarchy (read), attendance + tagging, task progress, material/shuttering receipts. **Cannot see** wage rates, P&L or client rates. |
| ACCOUNTANT | Quotations, WOs, bills, receipts, vendor bills, wage sheets, cost audit. All sites. |
| CLIENT (portal) | Own sites only: progress, quotations (accept/reject), WOs, bills |
| VENDOR (portal) | Own RFQs/bids, own subcontracts and bills |

- Method-level `@PreAuthorize` with permission strings (e.g. `attendance:write`) mapped from roles, plus **data scoping** (site/client/vendor) applied in a central repository filter. Hiding a field in the UI is not enough.
- DTOs for SITE_MANAGER exclude monetary fields at the serializer level.
- "End-to-end encryption for financial records" will be implemented as: TLS 1.2+ everywhere, encrypted DB storage/backups, and column-level AES-GCM encryption for bank/NID fields. True client-side E2E would make server-side reports impossible, so this needs confirmation (see §9).
- Password hashing with BCrypt/Argon2, login rate limiting, refresh token rotation and revocation.

---

## 5. API design conventions (mobile-ready)

- Resource paths: `/api/v1/sites/{id}/buildings`, `/api/v1/attendance`, `/api/v1/reports/item-cost?siteId=&buildingId=&floorId=&from=&to=&groupBy=workItem`.
- Pagination `?page=&size=&sort=`, RFC 7807 `problem+json` errors, ISO-8601 dates, money as strings.
- **Sync for offline mobile:**
  - `GET /api/v1/sync/reference?updatedSince=` returns labour, sites/hierarchy, work items and assignments changed since the given time, including tombstones for deletions.
  - `POST /api/v1/sync/attendance/batch` takes client UUIDs + `Idempotency-Key`. It is safe to retry and returns per-record results and conflicts.
- Optimistic locking (`version` column + `If-Match`/ETag) on editable resources.
- **"3-click" tagging endpoint:** `POST /api/v1/attendance/quick` with `{date, locationTag, workItemId, labourIds[], fraction}` lets the UI assign a whole gang to one activity in a single action (Workflow A step 2).

---

## 6. Web app (Vue) structure

- **Layout:** sidebar per role, a global **site/building/floor context picker** (persisted in Pinia) used by every screen, light/dark theme, i18n (English + local language).
- **Modules / screens:**
  1. *Setup:* Clients → Sites → Buildings → Floors → Units (tree table + drawers), Work Item master, Skill tiers, Rate cards, Users and site assignments.
  2. *Labour:* Labour list/profile, **Daily Attendance board** (pick location → pick activity → multi-select workers → fraction → save; copy-yesterday shortcut), approvals, wage sheets, payments/advances.
  3. *Vendors:* Vendor registry, RFQ/bids comparison, subcontracts, vendor bills, shuttering stock (dispatch/return/balance by site).
  4. *Commercial:* Quotation builder (BOQ grid with cost split, version diff, PDF), Work orders, milestones, client bills (PDF), receipts and allocation.
  5. *Reports:* Item-wise labour cost, Estimated vs Actual, Labour deployment history, Item P&L. All support drill-down, CSV/Excel/PDF export.
  6. *Dashboard:* budget vs actual by site/building/floor/item, top-overrun items, cash in vs cash out, labour headcount today.
  7. *Portals:* Client (progress, quotations, bills) and Vendor (RFQs, bids, bills). Same SPA, separate route trees and layouts.

---

## 7. Reports (how they compute)

| Report | Source |
|---|---|
| FR-3.1 Item-wise labour cost | `cost_ledger WHERE cost_type=LABOUR`, grouped by work_item, filtered by location tag and date range |
| FR-3.2 Estimated vs actual | `boq_line` estimated labour (WO version) vs ledger LABOUR actual, joined on work_item + location; variance and % |
| FR-3.3 Deployment history | `attendance` + `attendance_allocation` + ledger per labour: timeline of site/item/days/wages earned |
| FR-6.1 Item net profit | collected per item (`receipt_allocation`) − ledger (LABOUR + VENDOR + MATERIAL + SHUTTERING) per item |
| FR-6.2 Dashboard | The same queries aggregated. Use a nightly or on-demand **materialized view / summary table** (`cost_daily_summary`) if live queries exceed the 2 s target. |

Indexes: `cost_ledger (company_id, site_id, work_item_id, entry_date)`, plus building/floor variants. Load test with ~2M ledger rows to confirm the 2 s target.

---

## 8. Delivery phases

Each phase ends with a working, demo-able increment (backend + web + tests).

| # | Phase | Scope | Est. |
|---|---|---|---|
| 0 | **Foundation** | Monorepo, Docker Compose, CI, Spring Boot skeleton, Flyway baseline, auth (JWT login/refresh), users/roles, audit log, error model, OpenAPI → TS client generation, Vue shell (layout, login, routing guards, i18n) | 1.5 wk |
| 1 | **Hierarchy & catalog** | FR-1.1, FR-1.2, location-tag embeddable + validation, site assignments, context picker UI | 1.5 wk |
| 2 | **Labour & attendance** | FR-2.1–2.3: labour profiles, skill tiers, rate cards with resolution, attendance with multi-item allocation, quick-tag endpoint, cross-site ≤1.0 rule, approval flow | 2.5 wk |
| 3 | **Costing engine + first reports** | FR-2.4 ledger posting, reversals/re-rate, FR-3.1 item-wise report, FR-3.3 deployment history, exports. **Workflow A complete end-to-end.** | 2 wk |
| 4 | **Wages** | Wage periods, wage sheets aggregated across sites, advances, payments | 1 wk |
| 5 | **Vendors & shuttering** | FR-4.1–4.3: vendors, subcontracts, vendor bills → ledger, shuttering dispatch/return/balance, rental accrual, damage | 2.5 wk |
| 6 | **Materials (minimal)** | Material purchase/issue tagged to items → MATERIAL ledger (needed by FR-6.1) | 1 wk |
| 7 | **Commercial** | FR-5.1–5.3: quotation/BOQ with versions, PDF, WO conversion, tasks/milestones, client bills, receipts + allocation; FR-3.2 est-vs-actual | 3 wk |
| 8 | **Profitability & dashboard** | FR-6.1 item P&L, FR-6.2 dashboard, summary tables, perf tuning | 1.5 wk |
| 9 | **External portals** | Client portal, vendor portal (RFQ/bids), invitations, scoping tests | 1.5 wk |
| 10 | **Hardening & mobile readiness** | Sync endpoints, idempotency, security review, load tests, backups, monitoring (Actuator + Prometheus/Grafana), 99.5% deployment setup | 1.5 wk |

≈ 19–20 weeks for one full-stack developer. About 11–12 weeks with two developers (backend/frontend in parallel after Phase 0). The mobile app starts after Phase 10, reusing the API as-is.

### Testing strategy
- Unit tests for the rate-resolution and cost-allocation formulas (largest set: fractions, OT pro-rata, rounding, reversals).
- Integration tests on real PostgreSQL (Testcontainers) for the ledger, constraints and report queries.
- API contract: an OpenAPI diff in CI fails on breaking changes to `/v1`, which protects future mobile clients.
- RBAC/data-scoping tests per role (a site manager cannot read another site; a client cannot see costs).
- Playwright E2E for Workflow A and the quotation → WO → bill → receipt flow.

---

## 9. Open questions (defaults assumed until answered)

1. **Currency & locale:** single currency per company (default **BDT**? the SRS uses `$`). Language: English + Bangla?
2. **Tenancy:** for one contractor company, or a SaaS for many contractors? (Plan keeps `company_id` either way.)
3. **Attendance granularity:** fractions of a day (0.25/0.5/1) or actual hours? Default: fractions + OT hours.
4. **When cost posts:** on save, or only after site-manager/admin **approval**? Default: on approval.
5. **Materials:** the SRS only mentions material cost in FR-6.1. Minimal purchase/issue tracking is planned. Is full inventory/stores needed?
6. **Wage payment cycle:** daily, weekly or monthly? Paid cash, bank or mobile wallet (bKash/Nagad)?
7. **Encryption:** is TLS + encryption at rest + column encryption acceptable for "end-to-end encryption"?
8. **Hosting:** cloud (AWS/Azure/DO) or on-premise server?
9. **Units vs Floors:** the hierarchy diagram goes to Unit, but the ER diagram links Floor → Task. Plan: tasks can attach at any level (floor or unit).
