package com.github.kafeyangasli.prism.feature.user;

import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import java.nio.file.*;
import java.sql.DriverManager;
import static org.assertj.core.api.Assertions.*;

class AvatarMigrationContractTest {
    @Test void migrationKeepsExistingAccountsAndSupportsAnOptionalSingleStorageReference() throws Exception {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/V20261011020000__add_user_profile_picture.sql"));
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:avatar-migration;MODE=MySQL", "sa", "")) {
            var statement = connection.createStatement();
            statement.execute("CREATE TABLE users(id BIGINT PRIMARY KEY, name VARCHAR(120))");
            statement.execute("INSERT INTO users VALUES(1, 'Existing')");
            RunScript.execute(connection, new StringReader(sql));
            try (var rows = statement.executeQuery("SELECT name, profile_picture_path FROM users WHERE id=1")) {
                rows.next(); assertThat(rows.getString(1)).isEqualTo("Existing"); assertThat(rows.getString(2)).isNull();
            }
            statement.execute("UPDATE users SET profile_picture_path='generated.png' WHERE id=1");
            statement.execute("UPDATE users SET profile_picture_path=NULL WHERE id=1");
            assertThat(sql.toLowerCase()).doesNotContain("drop", "delete", "create table");
        }
    }
}
