# Quantity-based Alat reservations

Alat extends the existing Facility/Reservation workflow. Dynamically managed facility types have an immutable `quantity_based` flag, assigned when an Alat type is created. Display-name edits do not reclassify existing facilities. New Alat facilities require positive `totalStock`; non-Alat stock and reservation quantities remain null. Reservation history prevents switching a facility between quantity-based and exclusive semantics.

## Behavior and integration

- `EquipmentAvailabilityService` is the authoritative half-open interval sweep. Availability is stock minus the maximum simultaneously allocated quantity, not the sum of all intersecting reservations. Equal-time start/end events are grouped; adjacent reservations do not overlap. Counts use long arithmetic.
- PENDING requests allocate immediately until `expiresAt` or the reservation start; APPROVED requests allocate over their interval. Rejected/cancelled/expired/completed records are excluded. The database query evaluates expiration even before the existing scheduler updates the row.
- Submission and stock edits use transaction-local READ_COMMITTED plus pessimistic Facility locks. Submission then locks the requester. Approval, rejection, staff/user cancellation, proposal validation, and lifecycle transitions follow Facility -> User -> Reservation. Lifecycle candidates are scalar projections and each candidate is processed in its own transaction, avoiding stale entities and cross-facility lock cycles.
- Approval revalidates quantity while excluding the request's own pending allocation. It keeps manual approval, proposal verification, active-account checks, full blockage checks, and the seven-active-approved limit. Alat approval has no overlap rejection cascade. Ordinary facilities retain approved-only exclusivity and pending cascades.
- Stock changes reuse Admin facility CRUD and explicit DTOs. Safe reductions use the maximum future simultaneous allocation, including current approved use and unexpired pending requests. Total stock is never decremented by reservation processing.
- Full blockage creation/extension already locks Facility before inspecting and cancelling/rejecting reservations. All affected quantities are released by those existing status transitions; early completion never restores cancelled requests.
- Signed proposals are mandatory for Alat quantities > 5 OR duration >= 6 hours. Aula's existing full-day/proposal rules remain. Existing original/validated file handling, upload validation, immutable submissions, authorization, and audit evidence are reused.
- Thymeleaf/HTMX reuse the existing date picker and slot components. Equipment forms show quantity and dynamic interval availability; public slots show available/total and allocated units without requester identities. User detail/history and staff queues display quantities. Admin facility lists/edit forms show stock and link to allocation over time. CSRF remains enabled.

## Changed areas

| Area | Files / responsibility |
| --- | --- |
| Facility | `Facility`, `FacilityType`, `FacilityDto`, `FacilityRepository`, `FacilityServiceImpl`, `FacilityTypeService`: stock, stable type semantics, Admin updates, quantitative catalogue filtering |
| Reservation | `Reservation`, form/availability/slot DTOs, `ReservationRepository`, `EquipmentAvailabilityService`, submission/processing/lifecycle/availability services: allocation, proposals, serialization and releases |
| Controllers | Reservation/public availability controllers and `InventoryTransactionExceptionHandler`: controlled integer binding, privacy and recoverable transaction errors |
| Staff | `PendingReservationRow`, `StaffDashboardService`: quantity and equipment-safe approval previews |
| UI | Existing admin/facility/reservation/staff templates and `static/js/app.js`: stock, quantity, availability and proposal feedback |
| Database | Two new Flyway migrations; all existing migrations and reservation/time/expiry indexes are retained |
| Tests | Sweep unit tests, equipment workflow/MVC/security tests, real-MySQL workflow/race tests, and real-MySQL legacy migration tests |

## Deployment and existing Alat data

**Back up the database and stop application writers for the migration window. Do not guess inventory or historical quantities.** New databases and databases without existing Alat facilities migrate automatically. Existing Alat facilities require audited backfill values.

1. Run Flyway through target **20261011050000**, with the repository's migration location. This creates migration-only `equipment_stock_backfill` and `equipment_quantity_backfill` staging tables. Use Flyway's CLI or an equivalent dedicated migration runner, not application Hibernate schema generation.
2. Identify legacy Alat categories. Detection uses trimmed name `Alat`, code `ALAT`, or the older migration's `ALAT-<ten digits>` codes. Inspect unusual/custom historical categories and reconcile their intended semantics before proceeding.
3. Populate `equipment_stock_backfill(facility_id, total_stock)` with verified positive stock for **every** existing Alat facility. Populate `equipment_quantity_backfill(reservation_id, quantity)` with verified positive quantities for **every** reservation on those facilities, including historical records. Historical quantities may exceed today's stock; only live future allocations must fit current inventory. Staging has positive-value checks and existing foreign keys. Ordinary records need no values.
4. Run migration **20261011060000**. Before permanent schema changes, it rejects missing stock, missing/invalid quantities, or active concurrent allocations exceeding stock. Adjacent intervals are checked correctly. Resolve inconsistent allocations through an explicit approved business decision; the migration does not cancel or delete reservations itself.
5. It adds `quantity_based`, `total_stock`, and `quantity`, backfills only Alat, preserves IDs/statuses/proposal/audit data, and drops both staging tables. Start the updated application after successful migration and Hibernate schema validation.

Illustrative SQL (replace IDs and values with audited data):

```sql
INSERT INTO equipment_stock_backfill (facility_id, total_stock) VALUES (42, 20);
INSERT INTO equipment_quantity_backfill (reservation_id, quantity) VALUES (81, 5), (82, 6);
```

If a preflight fails, permanent conversion has not begun. Correct the staged values, inspect the failure, run Flyway `repair` to remove **only that failed migration entry**, then retry `migrate`. The preflight procedure is recreated safely on retry. Do not edit migration checksums or already-applied migrations. For any failure after DDL has started, inspect actual schema state and restore the backup or perform a reviewed recovery; MySQL DDL is not atomic across this entire migration.

Existing pending requests with newly mandatory proposals still must satisfy approval validation. Since original proposal uploads remain immutable, requests lacking a now-required proposal must be rejected/cancelled and submitted again with the signed document; the migration does not fabricate or attach evidence.

MySQL can enforce positivity in CHECK constraints but cannot express the cross-table Alat/null and temporal capacity rules as CHECKs. Those rules are enforced by service validation, entity callbacks, and the shared Facility locking convention. Direct SQL inventory writes must follow the same invariants and serialization.

## Reproducing verification

```powershell
npm run build
.\mvnw.cmd test
$env:PRISM_TEST_MYSQL_URL = 'jdbc:mysql://127.0.0.1:3312/prism_equipment_test'
$env:PRISM_TEST_MYSQL_USERNAME = 'root'
$env:PRISM_TEST_MYSQL_PASSWORD = ''
.\mvnw.cmd -Pmysql-it verify
```

The URL must identify a disposable MySQL database: workflow tests clean their fixtures. Migration tests create/drop uniquely named `prism_equipment_migration_*` schemas and need permission to do so. There is no H2 fallback in the MySQL profile. Coordinated submission tests force both scalar user-routing reads to complete before either can acquire Facility locks; unexpected SQL/deadlock/timeout errors fail the tests.

The profile also preserves the existing MySQL approval/blockage/snapshot regression tests. When already compiled, `-Pmysql-it dependency:properties failsafe:integration-test failsafe:verify` runs only the MySQL suites. This environment's `.cmd` Maven wrapper stalls, so verification uses the equivalent cached Maven Java launcher with Java 26 (compiling release 25).

## Verification results

| Verification | Result |
| --- | --- |
| Full standard regression suite | 380 tests passed, zero failures/errors/skips |
| Focused suite after stock conversion, historical backfill and blockage race fixes | 108 tests passed |
| Final packaging/rendering suite after type-name validation and help-text changes | 57 tests passed; executable Spring Boot JAR built successfully |
| Real MySQL 8.0.45 / InnoDB | 42 tests passed: 34 equipment workflow/security/race/engine tests, 3 migration upgrade/recovery tests, 5 existing concurrency regressions |
| Frontend | `npm run build` and JavaScript syntax check passed |
| Browser QA | 8 mobile/desktop equipment pages checked; no horizontal overflow or missing control labels; stock controls passed; no JavaScript errors |
| Repository | `git diff --check` passed |

The full run preceded the last three added edge-case tests; those and all affected reservation/facility/UI regressions passed in the subsequent focused runs. Final packaging also reran the equipment workflow and public/UI rendering tests. No failed feature checks remain. Generated logs, reports, UI snapshots and the JAR are under `target/`.

Local verification encountered the Maven `.cmd` launcher stall and transient missing generated class/template resources. The cached Java launcher and refreshed compiler/resources produced the successful final runs; application rules and tests were not weakened to bypass failures.

## Scope limits

This implements full stock availability and full facility blockages. Partial outages, per-unit identifiers, and inventory revision history are outside this feature. Existing administrative recaps continue reporting occupied **facility time slots**, rather than introducing a unit-weighted utilization metric with unavailable historical stock data. Allocation over time is available in the quantitative timetable.
