# Validated proposal PDFs

## Workflow and SRS traceability

Petugas and Admin review the original proposal and submit an officially validated PDF
in the same multipart operation as verification. The reservation remains PENDING until
separately approved. New verification requires a readable original document, a nonempty
valid PDF within the configured size limit, and a complete staff/timestamp audit. Repeated
verification is rejected to preserve the approved document and original audit.

Approval checks the reservation's post-lock state in the service layer:

| Required | Submitted | Approval prerequisite |
| --- | --- | --- |
| Yes | Yes | Proposal validated |
| Yes | No | Submission required; approval blocked |
| No | Yes | Proposal validated |
| No | No | No proposal prerequisite |

The SRS 2.0.1 requirements extended are FR-11 (signed proposal submission and staff
validation), FR-14 (owner history/detail), FR-15 (staff/Admin processing), and FR-29
(staff/Admin queue). BR-06/BR-07 remain the mandatory-submission rules; BR-07 is extended
to every submitted proposal and to validated PDF evidence. BR-11/BR-29, FR-16/FR-17,
and NFR-02/03/04/05/07/08/11 govern retained locking, authorization, private documents,
validation, transactions, service-layer rules, and audit. The SRS traceability table has
older numbering; these IDs follow the functional requirement tables themselves.

Scheduling conflicts, blockages, active account/facility checks, pending deadlines,
cascade confirmation, and the seven-active-reservation limit are preserved.

## Database and historical compatibility

`V20261011040000__add_validated_proposal_document.sql` adds:

- `validated_proposal_path VARCHAR(1000) NULL`: the separately stored approved PDF.
- `proposal_validation_legacy BOOLEAN NOT NULL DEFAULT FALSE`: an explicit compatibility
  flag backfilled only for pre-migration records with complete validator/timestamp audits.

The existing `proposal_validated_by` and `proposal_validated_at` fields remain the audit.
Checks require uploaded document references to have an original proposal and complete
audit, and new complete validation audits to have an uploaded reference. Entity lifecycle
checks provide the same safeguards for JPA writes. No prior migration or foreign key changes.

Historical audits and reservation statuses remain unchanged. Legacy validations can satisfy
approval only when an original proposal reference exists. No validated PDFs are synthesized;
legacy records show an explicit availability limitation and return 404 for the new document
endpoint. Incomplete historical audits remain unverified. Historical records with a complete
audit but no original retain their audit without being treated as a validated proposal.
Application endpoints cannot set the compatibility flag; all new reservations default to false.

## Storage, transactions, and access

Both versions use persistent `PRISM_PROPOSAL_STORAGE` (default `./storage/proposals`).
Original storage and accepted original formats (PDF/DOC/DOCX) are preserved. Validated
copies use independent `validated-<UUID>.pdf` names and `CREATE_NEW`, preventing overwrite
or client-controlled paths. The exact PDF bytes are stored without modification. The new
upload checks the extension, actual `%PDF-` header, and readability/pages using the existing
PDFBox dependency. It does not inspect approval stamps or authenticate digital signatures.

Original submissions bound actual stream reads to 10 MiB, remove partial writes, and
register cleanup for confirmed transaction rollback, including failures at commit.
Original documents retain extension-based format checks; validated PDFs additionally
undergo the content checks described above.

Validated uploads use `PRISM_MULTIPART_MAX_FILE_SIZE` (default 10 MB), including bounded
stream reading independent of the client-reported size. `PRISM_MULTIPART_MAX_REQUEST_SIZE`
also remains in effect. Runtime storage is already excluded by `.gitignore`; MySQL stores
references, not binaries. Configure a persistent volume/path in deployed environments.

Verification acquires Facility -> User -> Reservation locks, then checks and updates the
reservation. Approval preserves its existing lock order and READ_COMMITTED transaction.
Verification flushes inside the transaction and registers an after-completion cleanup for
confirmed rollback, including a later outer-transaction commit failure. Partial write failures
are cleaned up. Unknown commit outcomes keep the file rather than risk deleting a committed
reference; filesystem cleanup failures are logged for storage maintenance. An abrupt process
or disk failure can still require orphan reconciliation because filesystem and MySQL writes
are not a distributed transaction.

`GET /documents/reservations/{id}/validated-proposal` serves authenticated downloads
for the active owner, Petugas, and Admin. The service derives the caller from the security
context and enforces ownership for regular users. Unauthorized identifiers and unavailable
documents return 404. Responses use a generic attachment filename, `application/pdf`,
`Cache-Control: no-store`, and `X-Content-Type-Options: nosniff`. Documents remain accessible
after approval. Original download endpoints retain their existing ownership/staff rules.
No public storage route is introduced. Staff verification remains protected at controller and
service levels, with the persisted staff role checked and CSRF enabled.

## Interface and assumptions

The staff dashboard and reservation queue show required/voluntary proposals, an original
download, a required validated PDF field, validation action, and approved PDF download.
Approval buttons reflect the same prerequisite as the service. Browser checks provide early
feedback for empty files, extension, size, and header; authoritative parsing stays on the server.
The owner detail page distinguishes requirement, submission, validation, legacy compatibility,
and document availability. Optional reservations with no proposal show no pending validation.

The repository has no independent proposal rejection status or proposal resubmission route.
Its existing rejection operation rejects the reservation, requires a reason, and records
processor/time. This established behavior is preserved without requiring a PDF. Upload or
verification failure never triggers reservation rejection. A rejected reservation cannot be
approved or verified; the user submits a new reservation under the existing workflow.

Mandatory submission continues to mean Aula or duration of at least six hours; there is no
separate reservation-type proposal requirement in the repository. Existing WIB clock/audit
conventions are retained. No new dependencies, stamp generation, PDF editing, signature
authentication, replacement-original route, or automatic optional-proposal validation is added.

## Files changed

Created:

- `src/main/java/com/github/kafeyangasli/prism/feature/reservation/controller/ProposalDocumentController.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/reservation/service/ProposalDocumentService.java`
- `src/main/resources/db/migration/V20261011040000__add_validated_proposal_document.sql`
- `src/test/java/com/github/kafeyangasli/prism/feature/reservation/ValidatedProposalMigrationTest.java`
- `src/test/java/com/github/kafeyangasli/prism/feature/reservation/ValidatedProposalStorageTest.java`
- `src/test/java/com/github/kafeyangasli/prism/feature/reservation/ValidatedProposalTransactionIntegrationTest.java`
- `src/test/java/com/github/kafeyangasli/prism/feature/reservation/ReservationProcessingControllerUploadTest.java`
- `src/test/java/com/github/kafeyangasli/prism/support/ProposalPdf.java`
- `docs/VALIDATED_PROPOSALS.md`

Modified:

- `README.md`
- `src/main/java/com/github/kafeyangasli/prism/feature/administration/controller/StaffDashboardController.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/administration/dto/PendingReservationRow.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/administration/service/StaffDashboardService.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/reservation/controller/ReservationProcessingController.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/reservation/model/Reservation.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/reservation/service/ReservationProcessingService.java`
- `src/main/java/com/github/kafeyangasli/prism/shared/exception/storage/ProposalStorageService.java`
- `src/main/resources/static/js/app.js`
- `src/main/resources/templates/reservations/detail.html`
- `src/main/resources/templates/staff/dashboard.html`
- `src/main/resources/templates/staff/reservation-actions.html`
- `src/main/resources/templates/staff/reservations.html`
- `src/test/java/com/github/kafeyangasli/prism/UiRenderingIntegrationTest.java`
- `src/test/java/com/github/kafeyangasli/prism/feature/reservation/ProposalValidationIntegrationTest.java`
- `src/test/java/com/github/kafeyangasli/prism/feature/reservation/ProposalValidationLockOrderTest.java`
- `src/test/java/com/github/kafeyangasli/prism/feature/reservation/ReservationProcessingServiceIntegrationTest.java`

## Test coverage

Tests cover the required/optional/submitted approval matrix, missing/empty/oversized/spoofed
PDFs, content parsing, missing/unreadable originals, original preservation, correct reservation
association, owner/staff/Admin downloads, other-user and anonymous denial, regular-user mutation
denial, CSRF, historical compatibility and schema constraints, failed reads/writes, actual database
commit failure cleanup, friendly controller errors, and concurrency with committed/rolled-back
verification. Existing approval ordering and conflict/blockage/limit tests remain in the suite.

The database failure test uses a real persistence constraint violation after successful verification
inside an outer transaction. Concurrent approval tests hold verification open, assert that approval
waits, then check both successful commit and rollback outcomes. Migration SQL is tested on H2 in
MySQL mode. Real MySQL integration uses the repository's existing opt-in `mysql-it` profile and
requires a configured disposable `PRISM_TEST_MYSQL_URL` database.

## Verification results

Final command: `mvn -o clean verify`, using the cached Apache Maven 3.9.16 distribution
and JDK 26.0.2.1 with the repository's Java 25 compilation target. Result: **BUILD SUCCESS**,
333 tests, 0 failures, 0 errors, 0 skipped; executable Spring Boot JAR packaged successfully.
The suite includes 27 additional cases: 14 in the expanded proposal integration tests and
13 across the new storage, transaction, migration, and controller tests.

`node --check src/main/resources/static/js/app.js` passed. A Node VM check of the new
client-side handler passed seven valid/invalid/empty/oversized/cleared-selection cases.
`git diff --check` passed. The final Maven log is `target/build-verification.log`; individual
test results are under `target/surefire-reports` (both ignored build outputs).

Earlier test runs identified template attribute restrictions, UI assertions/fixtures affected
by the changed approval rule, Windows test-stream cleanup, and H2 ALTER syntax differences;
all were corrected. An intermediate non-clean run also encountered a missing compiled test
class; the final clean run completed the entire suite successfully. The wrapper test attempt
was stopped; the final run used the already cached Maven distribution directly in offline mode.

The opt-in `mysql-it` profile was not run because `PRISM_TEST_MYSQL_URL` is not configured.
Migration and concurrency validation here use H2, including MySQL mode for migration SQL;
this does not substitute for validation against a disposable production-version MySQL database.
No commit was created. The existing untracked SRS PDF and remediation notes were left untouched.

## Recommended commit

`feat(reservations): require validated PDF uploads for proposal verification`
