package com.github.kafeyangasli.prism.feature.facility;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FacilityTypeMigrationContractTest {

    @Test
    void migrationBackfillsLegacyValuesBeforeRemovingTextColumn() throws IOException {
        String resource = "/db/migration/V20260930151610__add_facility_types.sql";
        String sql;
        try (var stream = getClass().getResourceAsStream(resource)) {
            assertTrue(stream != null, "Facility type migration must exist");
            sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
        }

        int legacyRead = sql.indexOf("from facilities");
        int backfill = sql.indexOf("set f.facility_type_id = ft.id");
        int verification = sql.indexOf("facility_type_id is null");
        int foreignKey = sql.indexOf("foreign key (facility_type_id)");
        int notNull = sql.indexOf("modify column facility_type_id bigint not null");
        int oldColumnDrop = sql.indexOf("drop column type");

        assertTrue(legacyRead >= 0);
        assertTrue(backfill > legacyRead);
        assertTrue(verification > backfill);
        assertTrue(foreignKey > verification);
        assertTrue(notNull > foreignKey);
        assertTrue(oldColumnDrop > notNull);
    }
}
