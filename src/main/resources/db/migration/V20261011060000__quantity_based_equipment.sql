-- Run V20261011050000 first and populate staging when legacy Alat records exist.
-- Preflight precedes all permanent DDL: no guessed quantities and no partial conversion on missing data.
DROP PROCEDURE IF EXISTS verify_equipment_backfill;
DELIMITER $$
CREATE PROCEDURE verify_equipment_backfill()
BEGIN
    IF EXISTS (
        SELECT 1 FROM facilities f JOIN facility_types t ON t.id = f.facility_type_id
        LEFT JOIN equipment_stock_backfill s ON s.facility_id = f.id
        WHERE (LOWER(TRIM(t.name)) = 'alat' OR UPPER(t.code) = 'ALAT' OR t.code REGEXP '^ALAT-[0-9]{10}$')
          AND s.facility_id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Legacy Alat stock missing: populate equipment_stock_backfill; see docs/EQUIPMENT_RESERVATIONS.md';
    END IF;
    IF EXISTS (
        SELECT 1 FROM reservations r JOIN facilities f ON f.id = r.facility_id
        JOIN facility_types t ON t.id = f.facility_type_id
        LEFT JOIN equipment_quantity_backfill q ON q.reservation_id = r.id
        JOIN equipment_stock_backfill s ON s.facility_id = f.id
        WHERE (LOWER(TRIM(t.name)) = 'alat' OR UPPER(t.code) = 'ALAT' OR t.code REGEXP '^ALAT-[0-9]{10}$')
          AND q.reservation_id IS NULL
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Legacy Alat quantities missing/invalid: populate equipment_quantity_backfill with verified quantities';
    END IF;
    -- Only live future allocations must fit current stock; preserve larger historical quantities.
    IF EXISTS (
        SELECT 1 FROM (
            SELECT facility_id, SUM(delta) OVER (PARTITION BY facility_id ORDER BY boundary_at) AS allocated
            FROM (
                SELECT facility_id, boundary_at, SUM(delta) AS delta FROM (
                    SELECT r.facility_id, GREATEST(r.start_at, UTC_TIMESTAMP() + INTERVAL 7 HOUR) AS boundary_at,
                           q.quantity AS delta
                    FROM reservations r JOIN equipment_quantity_backfill q ON q.reservation_id = r.id
                    WHERE r.end_at > UTC_TIMESTAMP() + INTERVAL 7 HOUR
                      AND (r.status = 'APPROVED' OR (r.status = 'PENDING' AND r.start_at > UTC_TIMESTAMP() + INTERVAL 7 HOUR
                           AND (r.expires_at IS NULL OR r.expires_at > UTC_TIMESTAMP() + INTERVAL 7 HOUR)))
                    UNION ALL
                    SELECT r.facility_id, r.end_at AS boundary_at, -q.quantity AS delta
                    FROM reservations r JOIN equipment_quantity_backfill q ON q.reservation_id = r.id
                    WHERE r.end_at > UTC_TIMESTAMP() + INTERVAL 7 HOUR
                      AND (r.status = 'APPROVED' OR (r.status = 'PENDING' AND r.start_at > UTC_TIMESTAMP() + INTERVAL 7 HOUR
                           AND (r.expires_at IS NULL OR r.expires_at > UTC_TIMESTAMP() + INTERVAL 7 HOUR)))
                ) events GROUP BY facility_id, boundary_at
            ) grouped_events
        ) peaks JOIN equipment_stock_backfill s ON s.facility_id = peaks.facility_id
        WHERE peaks.allocated > s.total_stock
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Legacy Alat concurrent allocations exceed verified stock; reconcile existing reservations before migration';
    END IF;
END$$
DELIMITER ;
CALL verify_equipment_backfill();
DROP PROCEDURE verify_equipment_backfill;

ALTER TABLE facility_types ADD COLUMN quantity_based BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE facility_types SET quantity_based = TRUE
WHERE LOWER(TRIM(name)) = 'alat' OR UPPER(code) = 'ALAT' OR code REGEXP '^ALAT-[0-9]{10}$';
ALTER TABLE facilities ADD COLUMN total_stock INT NULL,
    ADD CONSTRAINT ck_facilities_stock_positive CHECK (total_stock IS NULL OR total_stock > 0);
ALTER TABLE reservations ADD COLUMN quantity INT NULL,
    ADD CONSTRAINT ck_reservations_quantity_positive CHECK (quantity IS NULL OR quantity > 0);
UPDATE facilities f JOIN facility_types t ON t.id = f.facility_type_id
JOIN equipment_stock_backfill s ON s.facility_id = f.id
SET f.total_stock = s.total_stock WHERE t.quantity_based = TRUE;
UPDATE reservations r JOIN facilities f ON f.id = r.facility_id
JOIN facility_types t ON t.id = f.facility_type_id
JOIN equipment_quantity_backfill q ON q.reservation_id = r.id
SET r.quantity = q.quantity WHERE t.quantity_based = TRUE;
-- Existing indexes already cover facility/status/time and status/expiration; retain them.
DROP TABLE equipment_quantity_backfill;
DROP TABLE equipment_stock_backfill;
