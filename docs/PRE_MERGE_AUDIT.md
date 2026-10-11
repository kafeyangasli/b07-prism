# PRISM Pre-Merge Audit

Audit date: 2026-10-11, Asia/Bangkok. Application business timezone: Asia/Jakarta.
Base commit: `fb1d13ddd06b22098508f435a063e88c74f012b3`, branch `chore/pre-main-merge`.

## 1. Executive Summary

**Overall status: CONDITIONAL.** The repaired source passes clean Maven verification,
including real MySQL migrations and concurrency tests, in a separate build directory.
No confirmed application defect remains a merge blocker. A clean CI/checkout build
remains the merge gate because compiled classes changed during verification in this
desktop checkout's shared `target` directory.

The initial build ran 411 tests: one failure, no errors, one opt-in MySQL test skipped.
The failure was a stale seed-data assertion, rather than incorrect seed parsing.
Corrections address inconsistent business time, blockage reconciliation lock cycles,
upload rollback/partial-write cleanup, unsafe cleanup after an unknown transaction
outcome, and unhandled BCrypt length errors. Existing reservation, proposal, equipment,
report, authentication, and facility rules are preserved.

The complete isolated run executed **426 Surefire tests and 47 Failsafe MySQL tests,
with zero failures, errors, or skips**. Final confirmation after restoring an
investigated-but-unnecessary report-query change is recorded in the verification
evidence below.
Final confirmation finished at **19:18:33 UTC+07:00**, exit code 0: 426 + 47 tests,
all passing. The executable JAR is `target/audit-workspace/target/prism-0.0.1-SNAPSHOT.jar`.

Pre-existing edits to `.env.example` and `README.md`, and the untracked `SourceCode.zip`,
were retained. The audit additionally documents the POSIX wrapper permission step in
README. No push, merge, deployment, commit, or history rewrite was performed. No
existing migration, dependency, authentication framework, or package layout was changed.

## 2. Repository Architecture

Observed stack:

| Component | Version / configuration |
| --- | --- |
| Java | Maven release target 25; verification runtime Oracle JDK 26.0.2.1 |
| Maven / wrapper | Maven 3.9.16; wrapper 3.3.4, `only-script` distribution |
| Spring Boot / Framework | 4.1.1 / 7.0.9 |
| Spring Security | 7.1.1, form login and server-side sessions, BCrypt |
| Persistence | Spring Data JPA, Hibernate 7.4.5.Final, schema validation |
| Database / driver | MySQL; actual test server 9.6.0 / Connector/J 9.7.0 |
| Migrations | Flyway 12.4.0; 10 timestamp-versioned MySQL migrations |
| Other libraries | PDFBox 3.0.8, TwelveMonkeys WebP 3.13.1, Lombok 1.18.46 |
| Tests | JUnit Jupiter 6.0.3, Mockito 5.23.0, H2 2.4.240, MockMvc |
| Views / assets | Thymeleaf, Tailwind 4.2.1, HTMX 2.0.10, local npm asset build |

`PrismApplication` starts Spring Boot, enables scheduling, and reconciles reservation
lifecycle on startup. The application uses feature packages with controllers, services,
entities/DTOs, and Spring Data repositories: `user`, `facility`, `reservation`, `report`,
`blockage`, and `administration`. Security, time configuration, development seeding,
exceptions, and disk storage are shared support code. Views are server-rendered with
HTMX enhancements; a few report/blockage operations expose DTO-based JSON endpoints.

Important execution paths and established invariants:

- Registration creates a pending Pengguna account. Admin verification activates it.
  Existing sessions are checked against persisted account status. Account settings
  derive ownership from authentication; self-deactivation serializes on the user row
  and refuses unfinished approved reservations.
- Submission locks Facility then User, checks active state, half-hour slots, one-day
  intervals, 07:00–20:00 hours, submission lead time and horizon, proposal requirements,
  and current availability. Aula retains full-day rules. Pending equipment requests
  allocate stock; ordinary pending requests can compete until approval.
- Approval uses READ_COMMITTED and Facility → User → Reservation locks. It rechecks
  deadlines, active account/facility, proposal evidence, blockages, allocation/conflict,
  and the seven-active-approved limit. Exclusive-facility approval requires confirmation
  before rejecting overlapping pending requests; equipment approval has no such cascade.
- Equipment uses a half-open interval sweep and the peak simultaneous allocation,
  with long arithmetic. Stock changes lock the facility and cannot reduce inventory
  below future/current live allocations. Expired pending allocations are excluded by
  the query even before scheduled reconciliation runs.
- Blockage creation/extension locks Facility, binds confirmation to the actor/request
  and observed impact, and atomically cancels/rejects affected reservations. Repair
  blockages require a report for the same facility. Early completion does not restore
  reservations. Reservation and blockage schedulers now process individual candidates
  in separate transactions and recheck state/time after locks.
- Reports enforce owner access, reservation-derived eligibility, and allowed staff
  transitions. Administrative recaps combine reservation intervals, blockage intervals,
  and facility status history; exports produce CSV/XLSX/PDF.
- Private proposals, report photos, and avatars use authenticated controller/service
  access. Facility gallery images are deliberately public. Images are decoded and
  size/dimension checked; validated PDFs are parsed. Storage identifiers and retrieval
  checks prevent client-selected filesystem paths.

Runtime requires `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`.
Spring does not automatically read `.env`. Storage paths require writable persistent
directories. The npm build is separate from Maven and is documented before startup.
The development seeder is enabled by default, disabled by `prod`/`production` profiles,
and can be explicitly disabled with `PRISM_SEED_ENABLED=false`. Production must use
that documented configuration to avoid known demo accounts.

## 3. Changes Applied

Categories: **A** confirmed defect, **B** investigated/ambiguous behavior,
**C** maintainability/documentation concern.

### A1 — Stale seed assertion (P0 build)

- Files: `src/test/java/.../config/seed/FacilitySeedSourceTest.java`.
- Evidence: initial `clean verify` failed because F019's description was asserted null.
  `initial_data/19. Acintya Prasada.txt` contains a quoted description, which the parser
  correctly preserves; other parser tests explicitly require description preservation.
- Correction: assert the committed description. Seed records and parser are unchanged.
- Verification: all seven seed parser tests pass; fresh MySQL seeding/login/idempotency
  and rollback coverage also passes.

### A2 — Inconsistent authoritative time (P1)

- Files: `BlockageService`, `ReportService`, `FacilityServiceImpl`, `Facility`,
  `DevelopmentDatabaseSeeder`; corresponding blockage, report, and facility tests.
- Evidence: blockage activation/completion and early completion used host-local time;
  preview expiry used direct `Instant.now()`. Reservations used the injected Jakarta
  clock. Fixed-clock regressions failed at start/end boundaries, early completion,
  preview expiration, and report action timestamps. Facility status history, used for
  recap capacity, independently sampled a system clock.
- Correction: use the application clock for those operations and explicit facility
  creation/status-history events. Read lifecycle time after locking. Persistence
  `created_at`/`updated_at` callbacks remain audit timestamps; compatibility entity
  setters retain their existing Jakarta fallback for non-service callers.
- Verification: exact start/end, fifteen-minute expiry, early completion, report action,
  facility history, and overdue scheduled-blockage tests pass. Existing reservation
  deadline/cancellation/report-window tests remain passing.

### A3 — Multi-facility lifecycle lock cycle (P0 concurrency)

- Files: `BlockageService`, `FacilityBlockageRepository`, `BlockageServiceTest`,
  `MySqlConcurrencyIT`.
- Evidence: one reconciliation transaction held all scheduled-facility locks, then
  acquired active-facility locks, with both lists ordered by start time. Different
  candidate snapshots can reverse facility acquisition order. For example, a worker
  holding scheduled A later needs active B, while a second worker sees a newly inserted
  earlier scheduled B and holds B while waiting for A. Transactions can form A → B /
  B → A waits. Existing reservation reconciliation already avoids this pattern.
- Correction: scalar candidate routing; one REQUIRES_NEW/READ_COMMITTED transaction
  per blockage; Facility → Blockage locking; refreshed status and clock. An overdue
  scheduled blockage still activates and completes in one reconciliation.
- Verification: fixed-clock unit cases and simultaneous real-MySQL reconciliation
  pass, counting each transition once. Existing approval-versus-blockage, simultaneous
  approval, user-limit, cancellation, equipment and account-deactivation races pass.

### A4 — Upload files survived rollback / partial reads (P1 integrity)

- Files: `ReservationSubmissionService`, `ProposalStorageService`, `ReportService`;
  `ValidatedProposalTransactionIntegrationTest`, `ValidatedProposalStorageTest`,
  `ReportWorkflowIntegrationTest`, `ReportServiceTest`, `ReportBlockageAuthorizationTest`.
- Evidence: original proposal storage had no transaction completion callback; report
  creation only caught synchronous repository errors. An outer rollback left a private
  photo behind in the new regression. Original `Files.copy` could leave a partial file
  on input failure and trusted reported size instead of bounded actual reads.
- Correction: transaction-aware original proposal storage; transactional report creation
  using the existing image-storage synchronization; rollback cleanup at completion;
  bounded original reads, CREATE_NEW writes, and partial-output cleanup. Accepted
  original formats and the 10 MiB limit are preserved.
- Verification: original proposal database failure at commit leaves neither a reservation
  nor a new file; report rollback removes the new photo; read failure and misleading
  upload size leave no file; commit/rollback/unknown-outcome cases pass. The authorization
  fixture now provides an H2 transaction manager and transaction interception because
  its imported real upload services require synchronization; all 18 authorization cases
  pass. Repository mocks and authorization assertions remain intact.

### A5 — Unknown transaction outcome deleted potentially referenced images (P1)

- Files: `LocalImageStorage`, `FacilityImageStorageTest`.
- Evidence: cleanup ran for every outcome other than COMMITTED, including UNKNOWN.
  A connection failure can leave commit outcome unknown; deleting the image can break
  a committed database reference. Validated proposal storage already retained files
  for unknown outcomes.
- Correction: remove new images only on confirmed ROLLED_BACK; retain the existing
  after-commit policy for deleting replaced images.
- Verification: parameterized commit/rollback/unknown-outcome tests pass; avatar,
  facility gallery, seed-image, and report workflow tests pass.

### A6 — Unhandled BCrypt password size (P1 validation)

- Files: `UserServiceImpl`, `UserServiceTest`, `docs/ACCOUNT_SETTINGS.md`.
- Evidence: registration/admin creation passed passwords above 72 UTF-8 bytes directly
  to BCrypt, which threw an uncaught `IllegalArgumentException`. Regression cases
  reproduced this with 73 ASCII bytes and 37 two-byte characters. Account password
  changes already had the appropriate byte limit.
- Correction: return the existing BusinessRuleException validation path before encoding.
  No new minimum password policy or authentication mechanism is introduced.
- Verification: both rejected registration/admin-creation cases leave no new account;
  existing registration, verification, login rejection and password-setting tests pass.

### C1 — Factual documentation and audit coverage (P2)

- Files: README, `docs/ACCOUNT_SETTINGS.md`, `docs/EQUIPMENT_RESERVATIONS.md`,
  `docs/VALIDATED_PROPOSALS.md`, this report.
- Correction: explain POSIX wrapper permissions (`mvnw` is tracked as mode 100644),
  BCrypt byte limits, original-upload cleanup, and blockage transaction behavior.
  README's pre-existing setup edits were retained. No Git index mode/staging was changed.
- Verification: instructions were compared with configuration, services, wrapper and
  observed Windows/npm/MySQL execution. Unix execution itself was not tested.

### B1 — Report locking investigated; original implementation preserved

The report locking fetch graph was suspected of taking unrelated facility locks.
The new MySQL regression holds a facility row locked while a report status update
commits. It passed both with the investigated alternative and with the **original**
fetch graph restored in a separate negative-control copy. No defect was established;
`ReportRepository` is unchanged. The focused concurrency regression remains as coverage.

## 4. Verification Matrix

| Verification | Result | Evidence |
| --- | --- | --- |
| Maven Wrapper | PASS | Windows `mvnw.cmd` resolved Maven 3.9.16; only-script wrapper is complete without a wrapper JAR. POSIX permission requirement documented. |
| Compilation | PASS | 129 production and 54 test Java files compiled with release 25; executable Boot JAR packaged by isolated `clean verify -Pmysql-it`. Shared-output checkout caveat below. |
| Unit tests | PASS | Seed parsing, interval sweep/overlap, validation, storage, exports, service transition and clock-boundary cases; included in 426 passing Surefire tests. |
| Integration tests | PASS | 426 Surefire tests total, plus 47 Failsafe tests; no failures/errors/skips. Includes MockMvc, H2, account/proposal/upload rollback, rendering and real MySQL workflows. |
| Concurrency tests | PASS | Seven `MySqlConcurrencyIT` cases and 37 equipment MySQL cases; H2 approval/account/proposal races; new lifecycle double-count and original report-lock regression. |
| Database migrations | PASS | All ten Flyway migrations applied to fresh MySQL 9.6 schemas; Hibernate validated mappings. Three legacy equipment upgrade/backfill/constraint cases passed. Existing SQL unchanged. |
| Security review | PASS | Scoped source review and passing role/ownership/CSRF/private-file/path/validation tests. BCrypt, account-status filter and DTO exposure reviewed; tracked current-source key/token scan found no real secrets. This is not a penetration test or historical-secret/CVE audit. |
| Time consistency | PASS | Authoritative service decisions and facility capacity history use injected Jakarta time. Persistence audit callbacks deliberately remain separate. Fixed boundary regressions pass. |
| Frontend build | PASS | `npm run build` exited 0 using installed dependencies, generated CSS and copied HTMX. No tracked frontend asset diff. |
| Diff hygiene | PASS | `git diff --check`; source-copy hash comparison: 311 files, zero mismatches. No migrations/dependencies/index/history changes; runtime artifacts/logs confined to ignored `target`. |

### Commands and actual outcomes

1. `.\mvnw.cmd clean verify` — sandbox attempt stalled with no build output and was
   interrupted. Host-access run: **FAIL**, 411 tests, one stale seed assertion, one skip.
2. `.\mvnw.cmd "-Dtest=BlockageServiceTest,ReportServiceTest,ValidatedProposalTransactionIntegrationTest,ReportWorkflowIntegrationTest,UserServiceTest" test`
   — pre-fix reproduction: **FAIL**, 47 tests, nine failures and four class-discovery
   errors. Those discovery errors were not treated as proof of a proposal defect.
3. `.\mvnw.cmd clean "-Dtest=FacilitySeedSourceTest,BlockageServiceTest,ReportServiceTest,ValidatedProposalTransactionIntegrationTest,ReportWorkflowIntegrationTest,UserServiceTest" test`
   — **PASS**, 55 tests after the targeted fixes.
4. Checkout `.\mvnw.cmd clean verify -Pmysql-it` — **FAIL**, class-discovery/entity-scan
   errors. `Facility.class` was rewritten after the compiler phase while tests ran.
   IntelliJ and other Java processes were active; external output interference is an
   inference from these observations, not a proven attribution to a particular process.
5. Exact-copy `clean verify -Pmysql-it` — first isolated run exposed the authorization
   fixture's missing transaction manager: 426 tests, 18 fixture errors. After the fixture
   correction, its focused run passed all 18 tests.
6. Exact-copy `.\mvnw.cmd clean verify -Pmysql-it` — **PASS**, 426 Surefire and 47
   Failsafe tests, zero failures/errors/skips; packaged JAR. The full run was repeated
   after preserving the original report fetch graph.
7. Original-graph negative control: `.\mvnw.cmd clean verify -Pmysql-it "-Dtest=FacilitySeedSourceTest" "-Dit.test=MySqlConcurrencyIT#reportStatusUpdateDoesNotTakeFacilityLocksInReverseOrder"`
   — **PASS**, seven parser tests and the one MySQL lock test; no report-query fix warranted.
8. `npm run build` — **PASS**, exit 0. `mvnw.cmd dependency:tree` — **PASS**, resolved
   versions recorded. `git diff --check` — **PASS**.
9. Final `.\mvnw.cmd "-Dtest=ReportServiceTest" test` — **PASS**, 17 tests at 19:24:23
   UTC+07:00, after making the old-action fixture relative to its fixed clock. Production
   source is unchanged from the complete confirmation; final Java-source comparison
   with the verification copy found zero mismatches.

Local build output/logs are retained under `target/audit-workspace`,
`target/audit-lock-control`, and `target/audit-logs` for inspection and are not merge
artifacts. The temporary runner `target/run-audit-verify.ps1` reads local credentials
without printing them; credentials were never embedded in source or command arguments.
Fresh `prism_pre_merge_audit_<UUID>` schemas were created and removed; application data
was not used as test data. Seed/migration tests also created and removed their own
uniquely named schemas.
Cleanup verification found **zero remaining schemas** among the 13 audit schema names
recorded in the logs.

### Reproduction

Use a checkout with no other process writing its Maven output directory. Build frontend
assets as documented, then run:

```powershell
.\mvnw.cmd clean verify
$env:PRISM_TEST_MYSQL_URL = 'jdbc:mysql://localhost:3306/disposable_test_database'
$env:PRISM_TEST_MYSQL_USERNAME = 'test_user'
$env:PRISM_TEST_MYSQL_PASSWORD = '<local test credential>'
.\mvnw.cmd clean verify -Pmysql-it
```

The MySQL account needs permission to create/drop the isolated schemas used by migration
and seeder tests. The URL's database must itself be disposable: workflow tests delete
test records. Never point these tests at an application database. The isolated audit
copy included source, tests, wrapper, seed data, scripts, documentation, build files
and generated frontend assets; SHA-256 comparison against the working source found
zero mismatches before final verification.

## 5. Unresolved Findings

### Merge-blocking defects / conditions

- No confirmed application defect remains unresolved.
- **Build environment condition:** obtain a passing normal checkout/CI `clean verify`
  with no concurrent writer to `target` before merging. The exact-source isolated build
  passed; the shared desktop-output run was not reliable. No unrelated application or
  IDE process was stopped to obtain a result.

### Unverified behavior

- Exact JDK 25 runtime, MySQL 8 deployment baseline, Unix wrapper execution, clustered
  deployment, crash/disk-loss recovery and load-scale performance were not exercised.
  Verification used release-25 bytecode on JDK 26.0.2.1 and MySQL 9.6.0. Flyway warned
  that 9.6 is newer than its latest verified server version (9.4); actual migrations and
  tests nevertheless passed. Validate the intended deployment versions in CI/staging.
- No fresh uncached dependency installation, dependency advisory scan, complete Git
  history secret scan, or browser visual audit was performed. The documented asset
  build and server-rendered view tests passed.
- Filesystem/DB operations remain separate resources. Rollback cleanup is tested;
  crashes, unavailable disks and unknown commit outcomes may require orphan maintenance.

### Ambiguous business requirements (B; preserved)

- Recap capacity counts weekday operating slots, while submission permits weekends.
  Equipment recaps count occupied time slots, rather than unit utilization. Confirm
  intended metric semantics with maintainers before changing calculations.
- Whether Admin deactivation should follow the user's stricter self-deactivation rule,
  prevent deactivating the final Admin, or invalidate every session on a password change
  is not established. Existing role-specific behavior is retained; password session
  limitations are already documented.
- Original proposal validation is extension-based for PDF/DOC/DOCX; validated PDFs have
  content parsing but no digital-signature authenticity check. No new document or
  signature policy was inferred.

### Nonblocking technical debt (C)

- Blockage confirmations are process-local, expire after fifteen minutes and are bound
  to actor/operation/impact. Multi-node routing/restart behavior requires deployment
  decisions; no shared token system was introduced.
- Entity audit timestamps still use system-local `LocalDateTime`; normalize deployment
  JVM time consistently if audit dates are compared/displayed alongside Jakarta business
  time. Those callbacks were not replaced as speculative persistence redesign.
- Some administrative DTO/service input bounds and duplicate-key error presentation
  could be made more uniform. Database constraints retain integrity; exhaustive hostile
  input coverage is outside the demonstrated fixes.
- Open-in-view remains enabled, some controllers directly use repositories, and
  `ProposalStorageService` remains in the exception package. Moving these components
  would exceed a minimal correctness audit.
- Existing deprecation/unchecked warnings remain. Known development credentials are
  deliberate seed fixtures; production profile/seed disabling remains essential.

## 6. Final Recommendation

**Conditionally safe to merge after the normal clean CI build gate passes.** The source
has been independently traced, confirmed defects repaired with focused regression
coverage, and migrations, rollback behavior, authorization, availability, equipment,
time boundaries and concurrent workflows verified against H2 and actual MySQL.
The isolated verification provides reproducible evidence for the repaired source; the
remaining merge condition concerns shared build-output interference in this desktop
environment. Deployment-version and operational limitations above remain explicit.
