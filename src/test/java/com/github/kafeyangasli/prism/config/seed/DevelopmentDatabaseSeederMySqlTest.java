package com.github.kafeyangasli.prism.config.seed;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import java.sql.DriverManager;
import java.util.UUID;

/** Reuses the full seed/login/repeat/rollback assertions against real Flyway migrations. */
@EnabledIfEnvironmentVariable(named = "PRISM_TEST_MYSQL_URL", matches = "jdbc:mysql:.*")
@SpringBootTest(properties = {
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate"})
class DevelopmentDatabaseSeederMySqlTest extends DevelopmentDatabaseSeederIntegrationTest {
    private static final String SCHEMA = "prism_seed_" + UUID.randomUUID().toString().replace("-", "");
    private static String rootUrl;
    private static String username;
    private static String password;
    private static boolean created;

    @DynamicPropertySource
    static void freshDatabase(DynamicPropertyRegistry registry) throws Exception {
        String base = System.getenv("PRISM_TEST_MYSQL_URL");
        String query = base.contains("?") ? base.substring(base.indexOf('?')) : "";
        String server = base.split("\\?", 2)[0];
        server = server.substring(0, server.lastIndexOf('/') + 1);
        rootUrl = server + query;
        username = System.getenv().getOrDefault("PRISM_TEST_MYSQL_USERNAME", "root");
        password = System.getenv().getOrDefault("PRISM_TEST_MYSQL_PASSWORD", "");
        try (var connection = DriverManager.getConnection(rootUrl, username, password);
             var sql = connection.createStatement()) {
            sql.execute("CREATE DATABASE " + SCHEMA + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            created = true;
        }
        String url = server + SCHEMA + query;
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
    }

    @AfterAll
    static void removeOnlyTestDatabase() throws Exception {
        if (!created) return;
        try (var connection = DriverManager.getConnection(rootUrl, username, password);
             var sql = connection.createStatement()) {
            sql.execute("DROP DATABASE " + SCHEMA);
        }
    }
}
