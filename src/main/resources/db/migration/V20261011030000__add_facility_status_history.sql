CREATE TABLE facility_status_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    facility_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    effective_at DATETIME(6) NOT NULL,
    at_creation BOOLEAN NOT NULL,
    CONSTRAINT fk_facility_status_facility FOREIGN KEY (facility_id) REFERENCES facilities(id),
    CONSTRAINT ck_facility_status_history CHECK (status IN ('ACTIVE', 'INACTIVE')),
    INDEX idx_facility_status_time (facility_id, effective_at)
);

-- Observe existing status now. created_at/updated_at do not establish earlier status.
-- Dates before this observation remain historically indeterminate.
INSERT INTO facility_status_history (facility_id, status, effective_at, at_creation)
SELECT id, administrative_status, CONVERT_TZ(UTC_TIMESTAMP(6), '+00:00', '+07:00'), FALSE FROM facilities;
