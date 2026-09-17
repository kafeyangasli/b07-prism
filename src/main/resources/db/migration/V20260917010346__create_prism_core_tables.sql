-- PRISM SRS v2 database design.
CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(320) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL,
    account_status VARCHAR(20) NOT NULL,
    verified_by BIGINT NULL,
    verified_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT fk_users_verified_by FOREIGN KEY (verified_by) REFERENCES users (id),
    CONSTRAINT ck_users_role CHECK (role IN ('ADMIN', 'PETUGAS', 'PENGGUNA')),
    CONSTRAINT ck_account_status CHECK (account_status IN ('PENDING', 'ACTIVE', 'INACTIVE', 'REJECTED')),

    INDEX idx_users_role (role),
    INDEX idx_users_account_status (account_status)
);

CREATE TABLE facilities (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL,
    name VARCHAR(150) NOT NULL,
    type VARCHAR(80) NOT NULL,
    location VARCHAR(150) NOT NULL,
    capacity INT NOT NULL,
    description VARCHAR(2000) NULL,
    administrative_status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT pk_facilities PRIMARY KEY (id),
    CONSTRAINT uk_facilities_code UNIQUE (code),
    CONSTRAINT ck_facilities_capacity_positive CHECK (capacity > 0),
    CONSTRAINT ck_facilities_administrative_status CHECK (administrative_status IN ('ACTIVE', 'INACTIVE')),

    INDEX idx_facilities_type_location (type, location),
    INDEX idx_facilities_administrative_status (administrative_status)
);

CREATE TABLE blockage_types (
    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(80) NOT NULL,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(2000) NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT pk_blockage_types PRIMARY KEY (id),
    CONSTRAINT uk_blockage_types_code UNIQUE (code),

    INDEX idx_blockage_types_active (is_active)
);

CREATE TABLE reservations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    facility_id BIGINT NOT NULL,
    start_at DATETIME(6) NOT NULL,
    end_at DATETIME(6) NOT NULL,
    purpose VARCHAR(1000) NOT NULL,
    proposal_path VARCHAR(1000) NULL,
    proposal_validated_at DATETIME(6) NULL,
    proposal_validated_by BIGINT NULL,
    status VARCHAR(20) NOT NULL,
    expires_at DATETIME(6) NULL,
    processed_by BIGINT NULL,
    processed_at DATETIME(6) NULL,
    reason_code VARCHAR(80) NULL,
    reason_detail VARCHAR(2000) NULL,
    cancelled_by BIGINT NULL,
    cancelled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT pk_reservations PRIMARY KEY (id),
    CONSTRAINT fk_reservations_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_reservations_facility FOREIGN KEY (facility_id) REFERENCES facilities (id),
    CONSTRAINT fk_reservations_proposal_validator FOREIGN KEY (proposal_validated_by) REFERENCES users (id),
    CONSTRAINT fk_reservations_processor FOREIGN KEY (processed_by) REFERENCES users (id),
    CONSTRAINT fk_reservations_canceller FOREIGN KEY (cancelled_by) REFERENCES users (id),
    CONSTRAINT ck_reservations_time CHECK (start_at < end_at),
    CONSTRAINT ck_reservations_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED', 'COMPLETED')),

    INDEX idx_reservations_facility_status_time (facility_id, status, start_at, end_at),
    INDEX idx_reservations_user_status_end (user_id, status, end_at),
    INDEX idx_reservations_status_expiry (status, expires_at)
);

CREATE TABLE reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    facility_id BIGINT NOT NULL,
    category VARCHAR(80) NOT NULL,
    description VARCHAR(2000) NOT NULL,
    photo_path VARCHAR(1000) NULL,
    status VARCHAR(20) NOT NULL,
    resolution_note VARCHAR(2000) NULL,
    handled_by BIGINT NULL,
    handled_at DATETIME(6) NULL,
    resolved_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT pk_reports PRIMARY KEY (id),
    CONSTRAINT fk_reports_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_reports_facility FOREIGN KEY (facility_id) REFERENCES facilities (id),
    CONSTRAINT fk_reports_handler FOREIGN KEY (handled_by) REFERENCES users (id),
    CONSTRAINT ck_reports_status CHECK (status IN ('NEW', 'IN_PROGRESS', 'RESOLVED', 'REJECTED')),

    INDEX idx_reports_facility_status_created (facility_id, status, created_at),
    INDEX idx_reports_user_created (user_id, created_at)
);

CREATE TABLE facility_blockages (
    id BIGINT NOT NULL AUTO_INCREMENT,
    facility_id BIGINT NOT NULL,
    blockage_type_id BIGINT NOT NULL,
    report_id BIGINT NULL,
    start_at DATETIME(6) NOT NULL,
    planned_end_at DATETIME(6) NULL,
    actual_end_at DATETIME(6) NULL,
    status VARCHAR(20) NOT NULL,
    public_reason VARCHAR(1000) NOT NULL,
    internal_note VARCHAR(2000) NULL,
    created_by BIGINT NOT NULL,
    ended_by BIGINT NULL,
    early_completion_reason VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,

    CONSTRAINT pk_facility_blockages PRIMARY KEY (id),
    CONSTRAINT fk_facility_blockages_facility FOREIGN KEY (facility_id) REFERENCES facilities (id),
    CONSTRAINT fk_facility_blockages_type FOREIGN KEY (blockage_type_id) REFERENCES blockage_types (id),
    CONSTRAINT fk_facility_blockages_report FOREIGN KEY (report_id) REFERENCES reports (id),
    CONSTRAINT fk_facility_blockages_creator FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT fk_facility_blockages_ender FOREIGN KEY (ended_by) REFERENCES users (id),
    CONSTRAINT ck_facility_blockages_time CHECK (planned_end_at IS NULL OR start_at < planned_end_at),
    CONSTRAINT ck_facility_blockages_status CHECK (status IN ('SCHEDULED', 'ACTIVE', 'CANCELLED', 'COMPLETED')),

    INDEX idx_facility_blockages_facility_status_time (facility_id, status, start_at, planned_end_at)
);
