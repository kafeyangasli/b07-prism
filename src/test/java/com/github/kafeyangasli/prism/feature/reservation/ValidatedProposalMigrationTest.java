package com.github.kafeyangasli.prism.feature.reservation;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.assertj.core.api.Assertions.*;

class ValidatedProposalMigrationTest {
    @Test void migrationPreservesHistoricalDecisionsAndConstrainsNewValidationEvidence() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:proposal-migration;MODE=MySQL");
             var sql = connection.createStatement()) {
            sql.execute("CREATE TABLE reservations (id BIGINT PRIMARY KEY, proposal_path VARCHAR(1000), proposal_validated_at TIMESTAMP, proposal_validated_by BIGINT)");
            sql.execute("INSERT INTO reservations VALUES (1, 'original.pdf', CURRENT_TIMESTAMP, 10), (2, 'optional.pdf', NULL, NULL), (3, NULL, CURRENT_TIMESTAMP, 10), (4, NULL, NULL, NULL), (5, 'partial.pdf', CURRENT_TIMESTAMP, NULL)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource(
                    "db/migration/V20261011040000__add_validated_proposal_document.sql"));
            try (var rows = sql.executeQuery("SELECT * FROM reservations ORDER BY id")) {
                for (int id = 1; id <= 5; id++) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong("id")).isEqualTo(id);
                    assertThat(rows.getString("validated_proposal_path")).isNull();
                    assertThat(rows.getBoolean("proposal_validation_legacy")).isEqualTo(id == 1 || id == 3);
                }
            }
            assertThatThrownBy(() -> sql.execute("UPDATE reservations SET proposal_validated_at=CURRENT_TIMESTAMP, proposal_validated_by=10 WHERE id=2"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> sql.execute("UPDATE reservations SET validated_proposal_path='validated.pdf' WHERE id=4"))
                    .isInstanceOf(SQLException.class);
            sql.execute("UPDATE reservations SET proposal_validated_at=CURRENT_TIMESTAMP, proposal_validated_by=10, validated_proposal_path='validated.pdf' WHERE id=2");
            assertThatThrownBy(() -> sql.execute("UPDATE reservations SET proposal_path=NULL WHERE id=2"))
                    .isInstanceOf(SQLException.class);
        }
    }
}
