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

Every UTF-8 `.txt` file is one record. Images are companion assets and are not
imported by this database seeder; the existing facility image upload workflow remains
available. Code and name come from the first two comma-separated fields. For rooms
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

Types are derived from the source names: Aula, Kelas, Ruang, Laboratorium, Olahraga
(Lapangan/GOR), Masjid, and Alat. Existing types are reused by code or name, including
types migrated under legacy codes. Missing types are created with stable uppercase
codes. An incompatible existing quantity-based type causes rollback, never an edit.
Unknown categories, missing/nonpositive/overflowing capacity or stock, duplicate
source codes, and overlong required fields fail with source context.

Source records have no location. The broad location `Universitas Diponegoro` follows
the campus identified in README.md; no building/address is guessed. All new facilities
are administratively ACTIVE. These category, location, and per-unit capacity mappings
are the only facility assumptions.

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

Run `.\mvnw.cmd test` for parser, H2 startup/login/repeat/rollback, profile isolation,
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
