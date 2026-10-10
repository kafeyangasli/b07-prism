# Profile pictures

All active `PENGGUNA` (the task's `USER`), `PETUGAS`, and `ADMIN` accounts can
upload, replace, and remove their own optional avatar from Account Settings.
The account page, desktop sidebar, and mobile account link render the same
shared circular avatar fragment. A native file input provides a JavaScript
object-URL preview before submission. The forms still work without JavaScript.
Removing an avatar restores the shared generic SVG placeholder.

## Persistence and ownership

Migration `V20261011020000__add_user_profile_picture.sql` adds one nullable
`VARCHAR(255)` column, `users.profile_picture_path`. Existing migrations are
unchanged. No raw image data or additional image entity is stored in MySQL.
The single column allows at most one current reference per account.

`POST /account/avatar` uploads or replaces the avatar, and
`POST /account/avatar/remove` removes it. Both resolve the account from the
existing authenticated security context, require an active persistent account,
and take the existing user-row lock. Client IDs, emails, roles, and storage
paths are ignored. The profile binder still permits only `name`; avatar changes
only modify the picture reference and the existing update timestamp. Existing
Spring Security role checks, CSRF protection, login, password management, and
soft deactivation remain in use.

`GET /account/avatar` serves only the authenticated account's picture. It has
no account ID or storage filename in its URL. Other accounts, including admins,
cannot retrieve someone else's avatar through this endpoint. The response uses
`Cache-Control: no-store`, `X-Content-Type-Options: nosniff`, a restrictive CSP,
and `image/png`, or `image/svg+xml` for the trusted static fallback. A missing
storage file returns the same fallback without exposing the stored path.
This implementation deliberately keeps avatar retrieval private; it does not
add avatars to other users' reservation, report, or audit records.

## Storage, validation, and cleanup

The existing `LocalImageStorage` implementation is reused. Avatar-specific
options accept actual decoded JPEG, PNG, and WebP content. WebP decoding uses
the [TwelveMonkeys ImageIO WebP plugin](https://github.com/haraldk/TwelveMonkeys)
at version 3.13.1, with no native OS library requirement or cloud storage.
Facility and report image storage keep their existing JPEG/PNG policies and
original-byte behavior. Their regression tests run alongside the avatar tests.

The input file's filename, extension, and MIME header do not authorize its
format. ImageIO identifies the reader, checks dimensions before decoding,
and actually decodes the image. Empty, unsupported, malformed, oversized,
and decoder-warning inputs are rejected. The shared 20-megapixel decoding
limit applies before allocating the full image. Avatars are re-encoded from
decoded pixels as PNG, removing uploaded metadata and trailing payloads. The
longest edge is bounded to 1024 pixels while preserving aspect ratio; CSS
supplies the circular crop. Multi-frame inputs use the first frame supported
by the decoder. The upload and normalized output must fit the configured byte
limit. This can reject a highly compressed input whose normalized image is
larger than a deliberately small configured limit.

The default limit is 5 MiB (5,242,880 bytes). Configure:

```text
PRISM_AVATAR_STORAGE=./storage/avatars
PRISM_AVATAR_MAX_BYTES=5242880
```

These map to `prism.storage.avatars` and `prism.avatars.max-bytes`. The shared
multipart limits also apply; if increasing the avatar limit beyond the global
limit, adjust `PRISM_MULTIPART_MAX_FILE_SIZE` / `PRISM_MULTIPART_MAX_REQUEST_SIZE`.
The configured limit is displayed in Account Settings.

Storage uses generated UUID `.png` names and exclusive file creation, never
user-supplied paths. Safe path resolution rejects traversal, absolute paths,
directories, symlinks, and alternate data streams on reads. Files live outside
the runtime classpath/static resources. The existing `/storage/` Git ignore
excludes the default directory. Point custom directories outside the checkout,
or ensure they are ignored. Keep this directory on persistent storage; all
application instances must share it if the application runs on multiple hosts.

Shared transaction synchronization stores the new file before changing the
reference, removes it on rollback, and removes the old file only after commit.
Facility image lifecycle methods now delegate to these shared helpers rather
than duplicating them. No facility or reservation business rules changed.
The user lock serializes concurrent avatar/profile/security changes and
coordinates with deactivation. An OS cleanup failure is logged and can leave
an unreachable orphan; it does not turn a committed update into an error or
delete a currently referenced file. A process crash between file creation and
transaction completion can also leave an orphan requiring storage maintenance.
There is no new background cleanup infrastructure.

## Deactivation retention

There is no existing avatar retention policy. Soft deactivation therefore
retains the stored file and reference, avoiding silent irreversible deletion.
It continues to retain the user, historical reservations, reports, and audit
relationships. Inactive accounts cannot access the avatar endpoint or Account
Settings, and production sessions are rejected by the existing status filter.
Avatar responses are not publicly addressable and prohibit browser caching.
Administrative reactivation restores access to the retained avatar.

## Files changed

Created:

- `src/main/java/com/github/kafeyangasli/prism/feature/user/controller/AvatarController.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/user/controller/AvatarExceptionHandler.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/user/service/AvatarStorage.java`
- `src/main/resources/db/migration/V20261011020000__add_user_profile_picture.sql`
- `src/main/resources/templates/fragments/avatar.html`
- `src/main/resources/static/images/avatar-placeholder.svg`
- `src/main/resources/static/css/avatar.css`
- `src/main/resources/static/js/avatar.js`
- `src/test/java/com/github/kafeyangasli/prism/feature/user/AvatarIntegrationTest.java`
- `src/test/java/com/github/kafeyangasli/prism/feature/user/AvatarStorageTest.java`
- `src/test/java/com/github/kafeyangasli/prism/feature/user/AvatarMigrationContractTest.java`
- `docs/PROFILE_PICTURES.md`

Modified:

- `pom.xml`
- `src/main/java/com/github/kafeyangasli/prism/shared/storage/LocalImageStorage.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/facility/service/FacilityImageService.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/user/model/User.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/user/dto/AccountSettingsView.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/user/service/AccountSettingsService.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/user/controller/AccountSettingsController.java`
- `src/main/resources/application.yaml`
- `src/main/resources/templates/account/settings.html`
- `src/main/resources/templates/fragments/layout.html`
- `src/main/resources/templates/fragments/navigation.html`
- `src/main/resources/static/css/app.css` (generated)
- `src/test/java/com/github/kafeyangasli/prism/feature/user/AccountSettingsControllerTest.java`
- `docs/ACCOUNT_SETTINGS.md`
- `README.md`

## Test coverage

Avatar integration tests cover each role's upload/replace/remove workflow,
typed private delivery, the shared fallback and preview markup, ID/path/field
tampering, failed validation retaining the previous picture, transaction
rollback cleanup, concurrent replacement, missing-file fallback, inactive
authorization, retained files after deactivation, unauthenticated requests,
CSRF, and protection against profile-form path injection.

Storage tests decode both lossy and lossless WebP plus JPEG/PNG fixtures,
verify normalized PNG output across storage-service restarts, enforce actual
stream size despite a misleading upload size, reject unsupported/malformed
files, strip trailing content, bound dimensions, and check safe load/delete
paths. The migration contract test verifies existing rows survive and the new
field defaults to null.

The existing account, facility image, and report photo tests are also run.
Tests use H2 in the repository's established convention; the migration contract
uses H2's MySQL mode. Production MySQL and filesystem/crash fault injection are
not covered by this environment.

## Executed checks

- `npm.cmd run build`: passed (CSS and HTMX assets).
- `node --check src/main/resources/static/js/avatar.js`: passed. Preview behavior
  is implemented with browser object URLs; no interactive browser test was run.
- `mvnw.cmd -o -DskipTests compile`: passed.
- Selected tests: **95 passed**, zero failures/errors. This includes all
  **23 new avatar tests**, the 49 existing account tests, 19 facility-image tests,
  and four report-photo tests.
- Full `mvnw.cmd -o test`: **289 tests**, zero assertion failures and 61 errors.
  The untouched baseline ran 266 tests with the same 61 error testcase names;
  Surefire XML comparison found no newly failing cases. These are existing
  unsaved `FacilityType` fixtures and the report/blockage authorization context
  lacking its required `EntityManager` bean. The full suite is not green.
- `mvnw.cmd -o -DskipTests package`: passed; the executable Spring Boot JAR was
  built after the selected tests passed. Packaging alone did not rerun tests.
- `git diff --check`: passed.

Commands used the installed Java 26 runtime with compilation targeting Java 25,
and explicitly loaded the cached Mockito agent as in the original account work.
Dependencies were resolved once before offline checks. Reproduction commands:

```powershell
$env:JAVA_HOME = 'C:/Program Files/Java/jdk-26.0.2.1'
$env:PATH = "$env:JAVA_HOME/bin;$env:PATH"
$mockitoAgentOption = '-DargLine=-javaagent:C:/Users/41hertz/.m2/repository/org/mockito/mockito-core/5.23.0/mockito-core-5.23.0.jar'
.\mvnw.cmd -o $mockitoAgentOption '-Dtest=Avatar*Test,Account*Test,FacilityImage*Test,ReportPhotoStorageTest' test
.\mvnw.cmd -o $mockitoAgentOption test
.\mvnw.cmd -o -DskipTests package
npm.cmd run build
```

Ignored build logs: `target/avatar-feature-tests.log`,
`target/avatar-full-tests.log`, `target/avatar-baseline-tests.log`,
`target/avatar-package.log`, and `target/avatar-frontend-build.log`.

No commit was created.
