# Account Settings

Profile picture management is described in [PROFILE_PICTURES.md](PROFILE_PICTURES.md).
Its new nullable avatar reference extends the original account implementation
below without changing the password or deactivation rules.

Authenticated `PENGGUNA`, `PETUGAS`, and `ADMIN` accounts can open `/account`
from the sidebar, update their display name, and change their password.
`PENGGUNA` is PRISM's existing name for the task's regular `USER` role.

The name is stripped of surrounding whitespace and limited to 120 characters,
matching `users.name`. Email, role, and account status are displayed as text.
The profile binder permits only `name`; the service changes only that field.
There are no account identifiers in the self-service API. Each service operation
resolves the account from Spring Security's authenticated email.

## Passwords and sessions

`GET /account/password` opens a dedicated form. `POST /account/password` verifies
the current password through the existing BCrypt `PasswordEncoder`, checks
confirmation, and rejects reuse of the current password before encoding a new
one. PRISM's registration workflow previously required only a nonblank password.
For this feature, new passwords require at least eight characters and at most
72 UTF-8 bytes, consistent with BCrypt's input limit. Passwords are not trimmed.
The registration/admin password policies are unchanged.

Password DTOs remain local to the controller/service. Passwords and rejected
password values never enter the view model, flash attributes, or redirects.
Error pages render empty password inputs and retain only safe inputs.

There is no existing session registry. A successful password change ends the
current session with Spring Security's `SecurityContextLogoutHandler` and sends
the user to `/login?passwordChanged`. **Other sessions are not selectively
invalidated by a password change.** The UI makes no such claim.

Production authentication still uses `CustomUserDetailsService`, now returning
`PrismUserDetails`, a subclass of the existing Spring Security user principal
carrying the persistent account ID. `AccountStatusFilter` checks account status
with a scalar query on every authenticated request from that principal. If the
account is no longer active, it clears authentication, invalidates that session,
and redirects to `/login?inactive`. This includes administrative deactivation.
Other sessions of a deactivated account are terminated on their next request;
there is no proactive session broadcast or guarantee about requests already
executing when deactivation commits. Login itself already rejects inactive
accounts. CSRF protection and the existing login/logout workflow remain enabled.

## Self-deactivation and transaction coordination

`GET /account/delete` shows consequences and a confirmation form requiring both
a checkbox and the current password. It is available only to `PENGGUNA`.
Spring Security, the controller, and the persistent-role check in the service
independently restrict privileged accounts. The service checks `ACTIVE` under
the existing pessimistic user-row locking strategy.

Deletion uses a transaction to resolve and lock the authenticated account by its
normalized, unique email, verify its password, run the existing
`countActiveApproved` query with `LocalDateTime.now(applicationClock)`, and change
its status to `INACTIVE`. The predicate is exactly `status = APPROVED` and
`end_at > now`, including ongoing reservations. No other reservation status
blocks this operation, and an approved reservation ending exactly at `now`
does not block it. The application clock remains Asia/Jakarta.

Deletion never locks facilities or reservations after the user lock. Approval
keeps its established facility → user → reservation lock order, and now checks
the requesting account is active while holding its user-row lock. If approval
commits first, deletion sees the approved reservation and fails; if deletion
commits first, approval sees an inactive user and fails. Directly resolving the
locked account avoids loading pre-lock account state or starting a pre-lock
consistent-read snapshot in the self-service transaction.

No user row, reservation, report, or audit relationship is removed, and no
reservation is automatically cancelled. Administrative reactivation continues
to use the existing workflow. No database migration or new profile fields are
needed. Concurrent self-service changes serialize on the same user row;
password verification uses the hash read under that lock. Persistence and
transaction failures receive generic retry feedback without database details.

## Changed files

Created:

- `src/main/java/com/github/kafeyangasli/prism/feature/user/controller/AccountSettingsController.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/user/dto/AccountProfileForm.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/user/dto/AccountPasswordForm.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/user/dto/AccountDeletionForm.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/user/dto/AccountSettingsView.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/user/service/AccountSettingsService.java`
- `src/main/java/com/github/kafeyangasli/prism/security/PrismUserDetails.java`
- `src/main/java/com/github/kafeyangasli/prism/security/AccountStatusFilter.java`
- `src/main/resources/templates/account/settings.html`
- `src/main/resources/templates/account/password.html`
- `src/main/resources/templates/account/delete.html`
- `src/test/java/com/github/kafeyangasli/prism/feature/user/AccountSettingsIntegrationTest.java`
- `src/test/java/com/github/kafeyangasli/prism/feature/user/AccountDeactivationConcurrencyIntegrationTest.java`
- `src/test/java/com/github/kafeyangasli/prism/feature/user/AccountSettingsControllerTest.java`
- `docs/ACCOUNT_SETTINGS.md`

Modified:

- `README.md` (feature documentation link)
- `src/main/java/com/github/kafeyangasli/prism/feature/user/repository/UserRepository.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/reservation/service/ReservationProcessingService.java`
- `src/main/java/com/github/kafeyangasli/prism/security/CustomUserDetailsService.java`
- `src/main/java/com/github/kafeyangasli/prism/security/SecurityConfig.java`
- `src/main/resources/templates/fragments/navigation.html`
- `src/main/resources/templates/auth/login.html`
- `src/main/resources/static/css/app.css` (generated by the existing frontend build)
- `src/test/java/com/github/kafeyangasli/prism/feature/reservation/ProposalValidationLockOrderTest.java` (active-account mock for the new approval guard)
- `src/test/java/com/github/kafeyangasli/prism/feature/reservation/ProposalValidationIntegrationTest.java` (repair a pre-existing obsolete facility API call blocking test compilation)
- `src/test/java/com/github/kafeyangasli/prism/UiRenderingIntegrationTest.java` (replace the account placeholder expectations with the enabled navigation link)

## Validation coverage

`AccountSettingsIntegrationTest` exercises real Thymeleaf rendering and Spring
Security filters, all-role profile updates, trimming, blank/oversized names,
injected IDs/protected fields, encoded password changes, failed password
validation without mutation, current-session termination, confirmation and
password checks for deletion, both privileged roles, future/ongoing approved
reservations, other statuses, the exact end-time boundary, historical/audit
preservation, inactive login rejection, other-session status revalidation,
anonymous requests, and CSRF.

`AccountDeactivationConcurrencyIntegrationTest` runs approval and deactivation
in separate threads/transactions five times and covers both sequential winning
orders. Unexpected persistence/lock errors fail these tests rather than being
counted as expected business rejections. `AccountSettingsControllerTest` checks
safe persistence-failure feedback and retention of the session on failure.
`ProposalValidationLockOrderTest` verifies the existing approval lock order.

Tests use the repository's H2 convention. Production MySQL-specific locking has
not been exercised by this test environment.

## Executed checks and results

- `npm.cmd run build`: passed, including Tailwind CSS and HTMX asset generation.
- `mvnw.cmd -DskipTests compile`: passed.
- Full `mvnw.cmd test` with the Mockito agent option below: 245 tests,
  zero assertion failures, 61 errors. All 47 new tests present at that run and
  the existing approval lock-order test passed. The 61 failing testcase names
  exactly matched the baseline copy (comparison of Surefire XML results).
- Final selected-test `mvnw.cmd package`: **50 tests passed, zero failures,
  zero errors**, and the executable Spring Boot JAR was built successfully.
  This includes all **49 new tests** after adding the session-fallback and
  deletion-identifier checks, plus `ProposalValidationLockOrderTest`.
- `git diff --check`: passed.

The untouched `HEAD` baseline initially failed test compilation because
`ProposalValidationIntegrationTest` called the removed `Facility.setType` API.
After applying only the same fixture API repair in an ignored baseline copy,
198 tests ran with 61 errors: 43 cases use unsaved transient `FacilityType`
fixtures, and 18 report/blockage authorization cases cannot load their context
because its `BlockageService` dependency requires an `EntityManager` bean that
the test configuration does not provide. These existing failures remain outside
the account feature's scope. The full suite is **not green**.

The installed runtime is Java 26.0.2.1; compilation still targets Java 25 as
specified in `pom.xml`. The default Java launcher / Mockito dynamic attachment
stalled in this environment. Commands were run with `JAVA_HOME` pointing to
`C:/Program Files/Java/jdk-26.0.2.1` and its `bin` directory prepended to `PATH`.
Mockito was loaded explicitly for tests, without changing project dependencies
or test configuration:

```powershell
$mockitoAgentOption = '-DargLine=-javaagent:C:/Users/41hertz/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
.\mvnw.cmd $mockitoAgentOption test
.\mvnw.cmd $mockitoAgentOption '-Dtest=AccountSettingsIntegrationTest,AccountSettingsControllerTest,AccountDeactivationConcurrencyIntegrationTest,ProposalValidationLockOrderTest' package
```

Logs are in ignored build artifacts:
`target/account-baseline-tests.log`,
`target/account-baseline-after-fixture-fix.log`,
`target/account-full-tests.log`,
`target/account-feature-build.log`, and
`target/account-frontend-build.log`.

Recommended commit message: `feat(account): add secure self-service account settings`

No commit was created.
