# Confirmed Business Rules — Implementation and Verification

Date: 2026-10-11, Asia/Bangkok. Base commit: `8eb549874667f2d35307d05620bdc48eed0ea5d7`.

**Result: PASS.** All three maintainer-confirmed rules are implemented. Final clean
Maven verification executed **452 standard tests and 49 real-MySQL integration tests**,
with zero failures, errors, or skips, and packaged the application JAR. No database
migration, dependency, or authentication-framework replacement was required.

Reference: `PRISM 2.1.1.pdf`, pages 7–10 and the reservation requirements. The user’s
BR-01/BR-02/BR-03 correspond to SRS BR-31/BR-34/BR-35 respectively. Only the requested
three-rule scope was implemented; other SRS features were not added.

## Changes

### BR-01 — Weekday-only reservations

- `OperatingDays` centralizes Monday–Friday checks without a public-holiday calendar.
  Half-open availability intervals ending at Saturday midnight do not include Saturday;
  intervals spanning actual weekend time are unavailable.
- `ReservationSubmissionService` rejects Saturday/Sunday start dates for every facility
  type, including Alat and Aula. The existing single-date, slot, deadline, quantity,
  proposal, and operating-hour rules remain in place.
- `ReservationAvailabilityService`, slot DTOs, facility interval search and equipment
  availability mark weekends unavailable. All 26 daily slots are disabled, equipment
  availability is zero, and stale selected start/end values are cleared. Public and
  authenticated interfaces explain the weekday restriction. The existing CSS bundle
  was rebuilt for the new disabled `NON_OPERATING` state.
- `AdministrationReportService` uses the same weekday definition for available operating
  slots. Friday through Monday yields 52 bookable half-hour slots; Saturday/Sunday alone
  yields zero. Legacy weekend reservation history is retained, including its occupied
  time in historical reports. No retroactive reservation rewrite was introduced; existing
  lifecycle and explicit management workflows remain available.

Tests: `WeekdayReservationIntegrationTest` covers Friday/Saturday/Sunday/Monday,
December 25 as a permitted weekday, rooms/equipment/Aula, public and authenticated
availability, empty weekend capacity, and retained legacy records. `OperatingDaysTest`
covers seven interval/weekday boundaries. Positive proposal/availability/UI fixtures
were moved to eligible weekdays instead of weakening their assertions.

### BR-02 — Final active Admin protection

- `UserServiceImpl` rejects deactivation of an ACTIVE Admin when only one ACTIVE Admin
  remains, regardless of actor/target identity. INACTIVE Admins do not count.
- Admin IDs are read in ascending order and each existing Admin row is locked in that
  order before the target is changed. Verification, rejection and activation of Admin
  targets share the same ordering. The invariant is serialized by database locks across
  application instances; it does not depend on a JVM-only mutex.
- Preloaded user entities are detached before authoritative pessimistic locking queries.
  The active count comes from those current locked entities, rather than an ordinary
  count/refresh query using an older MySQL snapshot. The operation remains part of its
  caller’s transaction; no independently committed deactivation was introduced.

Tests: `AdminDeactivationConcurrencyIntegrationTest` covers last-Admin rejection,
inactive Admins, a different actor/target, permitted deactivation when another Admin
remains, the administrative endpoint, and simultaneous self/cross deactivation at
READ_COMMITTED. `MySqlConcurrencyIT` additionally preloads entities and a
REPEATABLE_READ snapshot in both callers before racing self/cross deactivations.
Exactly one operation succeeds and one ACTIVE Admin remains.

An initial implementation using locked managed entities followed by refreshes failed
the real-MySQL old-snapshot races: both deactivations could succeed. Those regressions
were retained, and the final fresh locking reads passed them. H2 REPEATABLE_READ also
produced transaction-abort errors in the first fixture; H2 races now exercise the
application’s READ_COMMITTED isolation, while MySQL tests explicitly verify the older
snapshot case.

### BR-03 — Password-change session revocation

- `PrismUserDetails` retains a one-way SHA-256 fingerprint of the salted password hash
  captured at login. It survives Spring Security’s normal credential erasure and
  serialized session storage; it is not exposed through a getter or view model.
- The existing `AccountStatusFilter` reads current account status and password hash on
  each authenticated request using an uncached scalar query. A hash mismatch clears
  authentication, invalidates that session and redirects to `/login?passwordChanged`.
  Other account-status revocation still redirects to `/login?inactive`.
- Every committed password-hash replacement invalidates all older login fingerprints,
  independent of the session/device or the code that wrote the hash. BCrypt resets to
  the same plaintext also revoke sessions because a new salted hash is created.
  Existing current-session logout and fresh form login are preserved. Failed or rolled-back
  changes leave sessions valid; other accounts are unaffected.
- There is no administrative password-reset endpoint in the repository, and none was
  invented. Reset coverage exercises a committed replacement of the stored hash, which
  is the persistence action such a reset would perform. The request-time enforcement
  automatically applies to that action.

Tests: `PasswordSessionRevocationIntegrationTest` uses real form login and protected
requests from multiple sessions, a serialized/deserialized cached SecurityContext,
credential erasure, same/different-password resets, unaffected accounts, invalid current
passwords, rolled-back changes and fresh authentication. The previous account-settings
test expecting another session to remain active was updated to require revocation.
Test principal helpers now capture the fixture account’s actual stored hash.

README, account/session documentation and password/reservation UI text were updated.
No schema/data migration or password-policy expansion was introduced.

## Verification

| Check | Result | Evidence |
| --- | --- | --- |
| Frontend build | PASS | `npm run build`, exit 0; existing CSS/HTMX build pipeline. |
| Focused regressions | PASS | Weekend/session cases passed; corrected account/Admin/interval run passed 57 tests. Final Admin endpoint/race run passed 6 H2 and 2 MySQL tests. |
| Complete Maven build | PASS | `mvnw.cmd clean verify -Pmysql-it`, exit 0; finished 20:28:00 UTC+07:00. |
| Standard tests | PASS | 452 Surefire tests, zero failures/errors/skips, including all new tests and existing account/reservation/rendering suites. |
| MySQL tests | PASS | 49 Failsafe tests: 3 equipment migration tests, 37 equipment workflow/security/race tests, 9 concurrency tests. |
| Database mappings/migrations | PASS | Existing ten Flyway migrations and Hibernate validation on disposable MySQL 9.6 schemas. No migration files changed. |
| Diff/source verification | PASS | `git diff --check`; SHA-256 comparison of 317 source/build/seed/documentation files against the verified copy found zero mismatches. |

The focused commands were:

```powershell
.\mvnw.cmd clean "-Dtest=WeekdayReservationIntegrationTest,AdminDeactivationConcurrencyIntegrationTest,PasswordSessionRevocationIntegrationTest,ReservationAvailabilityIntegrationTest,ReservationSubmissionProposalTest,AccountSettingsIntegrationTest,ReportBlockageAuthorizationTest" test
.\mvnw.cmd "-Dtest=AdminDeactivationConcurrencyIntegrationTest,AccountSettingsIntegrationTest,OperatingDaysTest" test
.\mvnw.cmd clean verify -Pmysql-it "-Dtest=AdminDeactivationConcurrencyIntegrationTest" "-Dit.test=MySqlConcurrencyIT#finalAdminIsProtectedFromConcurrentSelfAndCrossDeactivationWithOldSnapshots"
```

The first focused run identified an outdated other-session expectation and H2 snapshot
fixture errors. Compilation also caught and corrected a test import ambiguity. The
complete initial run found the genuine MySQL race described above. Final verification
re-ran the complete available suite after fixing it; no tests were disabled or failures
suppressed.

The complete command ran in `target/confirmed-rules-workspace`, an exact source copy,
to avoid another process rewriting the desktop checkout’s shared Maven outputs (observed
in the preceding audit). Logs are under `target/business-rules-*.log`. The packaged JAR
is `target/confirmed-rules-workspace/target/prism-0.0.1-SNAPSHOT.jar`.

Fresh `prism_business_rules_<UUID>` schemas were used and removed. Migration/seeder tests
also removed their own uniquely named schemas. Existing application data was not used
as test data. The local runner reads existing configuration without embedding or printing
credentials. The supplied PDF and pre-existing ZIP files were left untouched.

For reproduction in an independent checkout, build assets, then run:

```powershell
npm run build
.\mvnw.cmd clean verify
# Configure a disposable MySQL database and credentials locally:
$env:PRISM_TEST_MYSQL_URL = 'jdbc:mysql://localhost:3306/disposable_test_database'
$env:PRISM_TEST_MYSQL_USERNAME = 'test_user'
$env:PRISM_TEST_MYSQL_PASSWORD = '<local test credential>'
.\mvnw.cmd clean verify -Pmysql-it
```

The MySQL test account needs create/drop-schema permissions. Never point workflow tests
at an application database: they delete their test records.

## Verification limits

No available test suite remains blocked. Verification used Java 26.0.2.1 compiling
release-25 bytecode, H2 2.4.240 and MySQL 9.6.0. An exact JDK 25/MySQL 8 deployment
runtime, Unix wrapper execution, a live external distributed session-cache backend,
multi-node load tests and browser visual QA were not exercised. Cached session data was
tested by serialization round-trip and protected HTTP requests; the source enforces
current database credentials regardless of the session snapshot. Flyway warned that
MySQL 9.6 is newer than its latest verified server version, but actual migration and
workflow tests passed. No push, commit, merge or deployment was performed.
