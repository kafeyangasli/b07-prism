-- Migration-only staging. Existing Alat inventory/quantities must be supplied by an operator.
-- On a new database these stay empty and the next migration completes automatically.
CREATE TABLE equipment_stock_backfill (
    facility_id BIGINT NOT NULL PRIMARY KEY,
    total_stock INT NOT NULL,
    CONSTRAINT fk_equipment_stock_backfill FOREIGN KEY (facility_id) REFERENCES facilities(id),
    CONSTRAINT ck_equipment_stock_backfill CHECK (total_stock > 0)
);
CREATE TABLE equipment_quantity_backfill (
    reservation_id BIGINT NOT NULL PRIMARY KEY,
    quantity INT NOT NULL,
    CONSTRAINT fk_equipment_quantity_backfill FOREIGN KEY (reservation_id) REFERENCES reservations(id),
    CONSTRAINT ck_equipment_quantity_backfill CHECK (quantity > 0)
);
