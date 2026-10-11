CREATE TABLE facility_images (
    id BIGINT NOT NULL AUTO_INCREMENT,
    facility_id BIGINT NOT NULL,
    storage_path VARCHAR(100) NOT NULL,
    is_thumbnail BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    -- MySQL permits multiple NULLs in a unique index, but only one selected facility ID.
    thumbnail_facility_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN is_thumbnail THEN facility_id ELSE NULL END) STORED,
    CONSTRAINT pk_facility_images PRIMARY KEY (id),
    CONSTRAINT fk_facility_images_facility FOREIGN KEY (facility_id) REFERENCES facilities (id),
    CONSTRAINT uk_facility_images_thumbnail UNIQUE (thumbnail_facility_id),
    CONSTRAINT uk_facility_images_storage UNIQUE (storage_path),
    CONSTRAINT ck_facility_images_order CHECK (display_order >= 0),
    INDEX idx_facility_images_order (facility_id, display_order, id)
);
