-- Insert missing SRS baseline types without changing existing configuration.
INSERT INTO blockage_types (code, name, description, is_active, created_at, updated_at)
SELECT 'REPAIR', 'Perbaikan', 'Blokir fasilitas untuk menangani laporan kerusakan.', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM blockage_types WHERE UPPER(code) = 'REPAIR');

INSERT INTO blockage_types (code, name, description, is_active, created_at, updated_at)
SELECT 'PLANNED_MAINTENANCE', 'Pemeliharaan Terencana', 'Blokir fasilitas untuk pemeliharaan yang telah dijadwalkan.', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM blockage_types WHERE UPPER(code) = 'PLANNED_MAINTENANCE');

INSERT INTO blockage_types (code, name, description, is_active, created_at, updated_at)
SELECT 'FORCE_MAJEURE', 'Keadaan Kahar', 'Blokir fasilitas akibat keadaan kahar atau kejadian di luar kendali.', TRUE, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
WHERE NOT EXISTS (SELECT 1 FROM blockage_types WHERE UPPER(code) = 'FORCE_MAJEURE');
