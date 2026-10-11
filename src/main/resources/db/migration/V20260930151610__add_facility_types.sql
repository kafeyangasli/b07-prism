CREATE TABLE facility_types (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(2000) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT pk_facility_types PRIMARY KEY (id),
    CONSTRAINT uk_facility_types_code UNIQUE (code),
    CONSTRAINT uk_facility_types_name UNIQUE (name),

    INDEX idx_facility_types_active (is_active)
);

-- Preserve binary-distinct legacy values. Case/spacing collisions receive deterministic
-- suffixes so the new case-insensitive unique keys never merge legacy categories.
CREATE TEMPORARY TABLE legacy_facility_types AS
SELECT legacy_key,
       legacy_value,
       legacy_name,
       CASE
           WHEN name_sequence = 1 THEN LEFT(legacy_name, 150)
           ELSE CONCAT(LEFT(legacy_name, 147 - LENGTH(name_sequence)), ' (', name_sequence, ')')
       END AS migrated_name,
       CONCAT(LEFT(base_code, 69), '-', LPAD(global_sequence, 10, '0')) AS migrated_code
FROM (
    SELECT legacy_key,
           legacy_value,
           legacy_name,
           base_code,
           ROW_NUMBER() OVER (
               PARTITION BY LOWER(legacy_name)
               ORDER BY legacy_key
           ) AS name_sequence,
           ROW_NUMBER() OVER (
               ORDER BY legacy_key
           ) AS global_sequence
    FROM (
        SELECT legacy_key,
               legacy_value,
               legacy_name,
               COALESCE(NULLIF(TRIM(BOTH '-' FROM REGEXP_REPLACE(UPPER(legacy_name), '[^A-Z0-9]+', '-')), ''), 'TYPE') AS base_code
        FROM (
            SELECT DISTINCT CAST(type AS BINARY) AS legacy_key,
                   type AS legacy_value,
                   CASE
                       WHEN TRIM(type) = '' THEN 'Unspecified'
                       ELSE TRIM(type)
                   END AS legacy_name
            FROM facilities
        ) legacy_values
    ) normalized_values
) numbered_values;

INSERT INTO facility_types (code, name, description, is_active, created_at, updated_at)
SELECT migrated_code, migrated_name, NULL, TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM legacy_facility_types
ORDER BY BINARY legacy_name;

ALTER TABLE facilities
    ADD COLUMN facility_type_id BIGINT NULL AFTER name;

UPDATE facilities f
JOIN legacy_facility_types legacy
  ON legacy.legacy_key = BINARY f.type
JOIN facility_types ft
  ON ft.code = legacy.migrated_code
SET f.facility_type_id = ft.id;

-- Abort the migration rather than silently leaving an untyped facility.
DELIMITER $$
CREATE PROCEDURE verify_facility_type_backfill()
BEGIN
    IF EXISTS (SELECT 1 FROM facilities WHERE facility_type_id IS NULL) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Facility type backfill left untyped facilities';
    END IF;
END$$
DELIMITER ;
CALL verify_facility_type_backfill();
DROP PROCEDURE verify_facility_type_backfill;

ALTER TABLE facilities
    ADD CONSTRAINT fk_facilities_facility_type
        FOREIGN KEY (facility_type_id) REFERENCES facility_types (id);

ALTER TABLE facilities
    MODIFY COLUMN facility_type_id BIGINT NOT NULL;

DROP INDEX idx_facilities_type_location ON facilities;

CREATE INDEX idx_facilities_type_location
    ON facilities (facility_type_id, location);

ALTER TABLE facilities
    DROP COLUMN type;

DROP TEMPORARY TABLE legacy_facility_types;
