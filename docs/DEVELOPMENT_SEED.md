# Initial development database

From the repository root, configure the existing DATABASE_URL, DATABASE_USERNAME,
and DATABASE_PASSWORD environment variables for a development MySQL database, then run:

```powershell
.\mvnw.cmd spring-boot:run
```

Flyway migrates the schema first. The Spring-managed transactional
`DevelopmentDatabaseSeeder` then inserts missing records automatically, including on
the first startup of an empty database. It is enabled by default outside the `prod` and
`production` profiles. Set `PRISM_SEED_ENABLED=false` (or
`--prism.seed.enabled=false`) to disable it explicitly. Production deployments must use
one of those production profiles and should disable the setting as defense in depth.

The source defaults to `./initial_data` relative to the working directory. Override
`PRISM_SEED_DIRECTORY` or `prism.seed.directory` with an absolute path when running the
packaged application elsewhere. Missing/empty/unreadable sources fail startup clearly.
The directory must be supplied alongside the application; it is not copied into the
production JAR.

## Facility mapping

Every UTF-8 `.txt` file is one record. Code and name come from the first two comma-separated fields. For rooms
and grounds, the final comma-delimited number is capacity. Everything between name
and capacity is description, including embedded commas and newlines. The three-field
`code,name,capacity` form also works. Quoted descriptions support doubled quotes;
unbalanced description quotes are preserved as content rather than losing a facility.
BOM, surrounding whitespace, and CRLF are normalized, while internal text/indentation
is preserved. Blank or omitted descriptions become SQL NULL. Descriptions exceeding
the existing 2000-character limit fail instead of being silently truncated.

Equipment records end in `Stok tersedia: <positive integer>`; that label is moved
into `totalStock` and the preceding text remains the description. Equipment uses the
existing `Alat` quantity semantics. Its required capacity is one per equipment unit;
inventory is represented only by the explicitly supplied stock count.

Types are derived from the source names: Aula, Kelas, Ruang, Laboratorium, Lapangan
(Lapangan/GOR), Masjid, and Alat. Optional `<code>.typeCode` and `<code>.typeName`
entries in `facility-metadata.properties` explicitly classify names such as
Gedung Prof Soedarto and Acintya Prasada as Aula; both fields are required together.
Existing types are reused by code or name, including
types migrated under legacy codes. Missing types are created with stable uppercase
codes. An incompatible existing quantity-based type causes rollback, never an edit.
Unknown categories, missing/nonpositive/overflowing capacity or stock, duplicate
source codes, and overlong required fields fail with source context.

Locations come from UTF-8 `initial_data/facility-metadata.properties`, keyed as
`<code>.location`. Masjid FSM, laboratories B/C/D, classrooms A101/B201, and
Acintya Prasada use `FSM - UNDIP`; Ruang Rapat uses `Perpustakaan UNDIP`; all five
equipment records use `ICT - UNDIP`. Records without a location entry retain the
broad campus location `Universitas Diponegoro`. Blank or overlong supplied locations
fail clearly. All new facilities are administratively ACTIVE.

The original F001 hall is now Gedung Prof Soedarto (300 people), F004 Lapangan
Basket has capacity 100, and F019 adds Acintya Prasada with capacity 150 (supplied
for the development seed) and no invented description. The new hall's companion
photo comes from the official FSM UNDIP facilities page; attribution and the
original image link are recorded in `initial_data/IMAGE_SOURCES.md`.
These changes apply to newly inserted facilities. Existing seeded or administrator-edited
facilities/types are not renamed or relocated automatically; existing databases keep
their current values and gain only missing records.

## Companion images

The seeder matches each text file to a `.jpg`, `.jpeg`, or `.png` file with the same
basename, ignoring case (for example `1. aula_fakultas.txt` and
`1. aula_fakultas.jpg`). No companion is required; the facility is still inserted
without an image. Multiple matching companions fail clearly rather than choosing
one arbitrarily. Unmatched image files do not create facilities.

When the facility's gallery is empty, the image is validated and copied through
the existing `FacilityImageStorage` into `PRISM_FACILITY_STORAGE` (default
`./storage/facilities`). The storage system checks actual JPEG/PNG content, the
configured byte limit, and the existing dimension limit, then assigns a safe UUID
filename. The seeder creates its `FacilityImage` record with display order zero
and selects it as the thumbnail. The source file remains unchanged; images are
served through the existing `/facilities/{facilityId}/images/{imageId}` endpoint.

This also fills empty galleries on facilities created by the earlier seeder.
Any existing gallery is preserved, including user uploads, image ordering, and
the chosen thumbnail. Restarting does not copy the image again or add records.
If an administrator removes every image, the next enabled seed run fills that
empty gallery from its companion again. Existing records with missing stored
files are not overwritten automatically.

Invalid images fail with the source path and facility code. New files are registered
with the existing transaction cleanup: if any seed insert or validation fails,
all newly created database rows and image files are rolled back together.

## Accounts and repeat execution

| Email | Development password | Role |
| --- | --- | --- |
| pengguna1@university.ac.id | pengguna_prism | PENGGUNA |
| pengguna2@university.ac.id | pengguna_prism | PENGGUNA |
| petugas@university.ac.id | petugas_prism | PETUGAS |
| admin@university.ac.id | admin_prism | ADMIN |

New users are ACTIVE with a verification timestamp from the application's Clock.
The bootstrap admin has no prior verifier; other new users reference that admin.
Passwords are encoded using the injected production PasswordEncoder bean (BCrypt).
Authentication follows the existing security chain without changes.

Case-insensitive checks on facility code, type code/name, and email prevent repeated
inserts. Existing records are left intact, including passwords, verification, status,
and edited descriptions. Existing accounts occupying these emails are not reset, so
the documented credentials/status apply to newly created accounts on a fresh database.
All database writes are in one transaction; invalid input or insertion conflicts
leave no partial seed. Database unique keys remain the final guard against concurrent
startup races (a losing startup fails and can be restarted).

## Verification

Run `.\mvnw.cmd test` for parser, H2 startup/login/image serving/repeat/rollback, profile isolation,
and the existing regression tests. The opt-in real-MySQL test additionally runs all
Flyway migrations and Hibernate schema validation before the same assertions:

```powershell
$env:PRISM_TEST_MYSQL_URL = 'jdbc:mysql://localhost:3306/disposable_test_database'
$env:PRISM_TEST_MYSQL_USERNAME = 'test_user'
$env:PRISM_TEST_MYSQL_PASSWORD = 'test_password'
.\mvnw.cmd "-Dtest=DevelopmentDatabaseSeederMySqlTest" test
```

The MySQL test requires permission to create/drop databases. It creates a uniquely
named `prism_seed_<UUID>` schema and drops only that schema afterward; the database
named in the URL is not modified. Without PRISM_TEST_MYSQL_URL it is skipped.
