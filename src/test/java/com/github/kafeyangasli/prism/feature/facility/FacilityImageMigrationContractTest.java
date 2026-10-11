package com.github.kafeyangasli.prism.feature.facility;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class FacilityImageMigrationContractTest {
    @Test void migrationEnforcesOwnershipAndOneThumbnailWhileAllowingManyOtherImages() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V20261011010000__add_facility_images.sql"));
        assertThat(sql).contains("GENERATED ALWAYS AS", "UNIQUE (thumbnail_facility_id)", "REFERENCES facilities (id)");
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:facility-image-migration;MODE=MySQL", "sa", "")) {
            var statement = connection.createStatement();
            statement.execute("CREATE TABLE facilities (id BIGINT PRIMARY KEY)");
            statement.execute("INSERT INTO facilities VALUES (1), (2)");
            // H2's equivalent generated columns omit MySQL's STORED keyword.
            RunScript.execute(connection, new StringReader(sql.replace(") STORED", ")")));
            statement.execute("INSERT INTO facility_images(facility_id,storage_path,is_thumbnail,display_order,created_at) VALUES (1,'a.png',TRUE,0,CURRENT_TIMESTAMP),(1,'b.png',FALSE,1,CURRENT_TIMESTAMP),(1,'c.png',FALSE,2,CURRENT_TIMESTAMP),(2,'d.png',TRUE,0,CURRENT_TIMESTAMP)");
            assertThatThrownBy(() -> statement.execute("UPDATE facility_images SET is_thumbnail=TRUE WHERE storage_path='b.png'"))
                    .isInstanceOf(java.sql.SQLException.class);
            assertThatThrownBy(() -> statement.execute("UPDATE facility_images SET facility_id=999 WHERE storage_path='b.png'"))
                    .isInstanceOf(java.sql.SQLException.class);
            statement.execute("DELETE FROM facility_images WHERE storage_path='a.png'");
            statement.execute("UPDATE facility_images SET is_thumbnail=TRUE WHERE storage_path='b.png'");
            try (var rows = statement.executeQuery("SELECT COUNT(*) FROM facilities")) {
                rows.next(); assertThat(rows.getInt(1)).isEqualTo(2);
            }
        }
    }
}
