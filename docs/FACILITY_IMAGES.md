# Facility image gallery (FR-07 extension)

Facilities support optional multiple JPEG/PNG images. Admins choose a thumbnail in upload previews, add images, select an existing thumbnail, replace images, and delete individual images inside the existing facility editor. Image-only forms leave facility metadata unchanged. HTMX refreshes only the image section; ordinary multipart POST forms also work without JavaScript. The public detail page starts with the designated thumbnail and provides an image strip. Selecting a gallery image only changes the visitor's preview. Catalogue cards use the designated thumbnail. Empty galleries and missing files render a placeholder.

## Database and transactions

`V20261011010000__add_facility_images.sql` creates `facility_images` with a facility foreign key, UUID storage identifier, thumbnail flag, display order, and upload timestamp. `Facility.images` maps the inverse one-to-many relationship; no thumbnail path is duplicated on the facility. There is no cascading deletion into facilities or unrelated records.

MySQL's generated `thumbnail_facility_id` column contains the facility ID only for selected thumbnails. Its unique constraint permits multiple unselected images but at most one thumbnail per facility. Each service mutation first acquires the existing facility pessimistic row lock. Thumbnail changes clear and flush the previous selection before setting the new one. Deleting the thumbnail selects the first remaining image in display order. Replacements keep the same ID, order, and selection. Additional uploads preserve the thumbnail unless explicitly selected.

Uploads use the existing report-photo validation/storage implementation extracted into `LocalImageStorage`, shared by reports and facilities. Proposal documents retain their existing separate document storage. New image files register rollback cleanup immediately after writing. Old files are deleted only after database commit, so rollback retains the original. Partial writes are cleaned up. Ordinary filesystem I/O failures surface as errors. The filesystem and database are not a distributed transaction: an abrupt process crash between commit and cleanup or a persistent deletion failure may require reconciliation of disk files against database identifiers.

## Configuration and deployment

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `PRISM_FACILITY_STORAGE` | `./storage/facilities` | Facility image directory |
| `PRISM_FACILITY_IMAGE_MAX_BYTES` | `5242880` | Individual image limit (5 MiB) |
| `PRISM_FACILITY_IMAGE_MAX_COUNT` | `20` | Maximum images per facility |
| `PRISM_MULTIPART_MAX_FILE_SIZE` | `10MB` | Global servlet file limit; preserves proposal/report support |
| `PRISM_MULTIPART_MAX_REQUEST_SIZE` | `110MB` | Global multipart request limit |

Use an absolute writable directory in deployment. For Docker, set `PRISM_FACILITY_STORAGE=/data/facilities` and mount a persistent host directory or named volume at `/data/facilities`. For multiple application instances, mount the same persistent storage on each instance. There are no existing Docker manifests in this repository to update. `storage/` and `uploads/` are already ignored by Git. Back up the database and upload directory together. Keep the servlet limits consistent with the configured image size/count; the global file limit also applies to reports and proposals.

Images are retrieved through `GET /facilities/{facilityId}/images/{imageId}`. URLs expose database IDs only. Both IDs must match the image's ownership. Missing records return 404; missing files for valid records serve the static SVG placeholder without caching. Replaced images keep the same URL and use `Cache-Control: no-cache` so the browser revalidates them.

## Security and validation

Existing security rules already restrict `/admin/**` to Admins and permit public `/facilities/**` reads. CSRF remains enabled and is included in ordinary Thymeleaf forms and HTMX requests. The service additionally verifies the requesting user's role and scopes every existing-image lookup to its facility. The new endpoints cannot select, replace, or delete a different facility's image.

Actual image content is decoded with Java ImageIO; extensions and client MIME types are not trusted. Validation rejects empty, unsupported, malformed, oversized, and greater-than-20-megapixel images. The bounded stream read enforces the limit independently of multipart metadata. Filenames use server-generated UUIDs and the detected JPEG/PNG extension. Loading rejects traversal, absolute paths, directories, and symlinks. Image binaries are never stored in MySQL and directories are never served as static resources.

## Decisions

- JPEG and PNG are supported using the JDK's decoders. WebP is intentionally unsupported to avoid a new decoder dependency.
- Invalid upload batches are atomic: an invalid image rolls back all images in that submission, and facility creation also rolls back. Error messages identify the invalid image's index. An unnamed empty browser part means no optional selection; a named empty upload is rejected.
- Submitted thumbnail indices are zero-based positions in the submitted files and must be in range. Without a selection, the first image becomes the thumbnail only when the facility has no existing thumbnail.
- Galleries follow upload order. Removing an image may leave gaps; replacements retain their original position. Manual reordering is outside the requested scope.
- No reservation, blockage, account, or existing facility CRUD business rules were changed.

## Files changed

Modified:

- `.env.example`
- `.gitignore` (root upload directories remain ignored; Java storage packages are no longer ignored)
- `src/main/java/com/github/kafeyangasli/prism/feature/facility/controller/AdminFacilityController.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/facility/model/Facility.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/report/service/ReportPhotoStorage.java`
- `src/main/resources/application.yaml`
- `src/main/resources/templates/admin/facilities.html`
- `src/main/resources/templates/facilities/detail.html`
- `src/main/resources/templates/facilities/list.html`
- `src/main/resources/templates/fragments/layout.html`

Added:

- `docs/FACILITY_IMAGES.md`
- `src/main/java/com/github/kafeyangasli/prism/feature/facility/controller/FacilityImageController.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/facility/controller/FacilityImageExceptionHandler.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/facility/model/FacilityImage.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/facility/repository/FacilityImageRepository.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/facility/service/FacilityImageService.java`
- `src/main/java/com/github/kafeyangasli/prism/feature/facility/service/FacilityImageStorage.java`
- `src/main/java/com/github/kafeyangasli/prism/shared/storage/LocalImageStorage.java`
- `src/main/resources/db/migration/V20261011010000__add_facility_images.sql`
- `src/main/resources/static/css/facility-images.css`
- `src/main/resources/static/images/facility-placeholder.svg`
- `src/main/resources/static/js/facility-images.js`
- `src/main/resources/templates/admin/facility-images.html`
- `src/test/java/com/github/kafeyangasli/prism/feature/facility/FacilityImageIntegrationTest.java`
- `src/test/java/com/github/kafeyangasli/prism/feature/facility/FacilityImageMigrationContractTest.java`
- `src/test/java/com/github/kafeyangasli/prism/feature/facility/FacilityImageStorageTest.java`

## Validation

Feature integration tests cover creation with/without images, explicit/default thumbnails, append behavior, thumbnail changes, replacement, deletion to an empty gallery, invalid batches, size/count limits, role and CSRF restrictions, foreign image IDs, concurrent selection, rollback cleanup, public gallery/cards, missing-file placeholders, CRUD preservation, and multipart/HTMX endpoints. Storage tests cover content detection, malformed and unsupported formats, traversal, generated identifiers, and persistence across storage reinitialization. Migration tests exercise the new SQL in H2's MySQL compatibility mode with only the `STORED` keyword adapted; production MySQL execution still requires a deployment database.

The ordinary Maven test build has an existing compilation error in `ProposalValidationIntegrationTest:196`, which calls removed `Facility.setType(String)`. This was reproduced using a clean archive of HEAD. A temporary verification POM excludes only that uncompilable test; project build configuration and old tests remain unchanged. Mockito was loaded as a startup Java agent because its dynamic attachment stalled on the local Java 26 runtime.

- Final verification suite with only the uncompilable old test excluded: **196 tests, 156 passed, 40 errors, 0 assertion failures**. The failing test identifiers exactly match the clean HEAD baseline (**177 tests, 137 passed, 40 errors**); there are no newly introduced failing cases.
- All **19 new image tests** passed (17 integration tests, one migration contract test, one storage test). Existing facility CRUD tests and report-storage tests passed.
- `npm run build`: passed. `node --check src/main/resources/static/js/facility-images.js`: passed. The tracked generated CSS was restored to its original content after verification because the feature's styles are provided in its dedicated stylesheet.
- Maven `package -Dmaven.test.skip=true`: passed and produced the Spring Boot executable JAR. Tests were skipped for packaging because of the confirmed pre-existing test compilation issue; they were exercised separately as described above. Missing packaging dependencies required Maven cache/network access.
- `git diff --check`: passed. No commits were created.

Logs remain in ignored `target/`: `feature-tests.log` (ordinary test compilation failure), `baseline-tests.log`, `baseline-compatible-tests.log`, `compatible-tests.log`, `focused-tests.log`, `frontend-build.log`, and `package-build.log`. The temporary verification POM is retained under `target/verification-pom.xml` as a reference only. To repeat it from the repository root, copy it to a temporary root POM before invoking Maven with `-f`; its relative paths assume that location.

Recommended commit message: `feat(facilities): add image galleries and thumbnail management`
