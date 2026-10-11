package com.github.kafeyangasli.prism.feature.reservation;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Isolated schemas verify actual Flyway upgrades of legacy records. */
class EquipmentMigrationMySqlIT {
    String schema, url, username, password;
    Connection connection;

    @BeforeEach void legacyDatabase() throws Exception {
        String base = System.getenv("PRISM_TEST_MYSQL_URL");
        if (base == null || !base.startsWith("jdbc:mysql:")) throw new IllegalStateException("Disposable MySQL URL required");
        String query = base.contains("?") ? base.substring(base.indexOf('?')) : "";
        base = base.split("\\?", 2)[0];
        base = base.substring(0, base.lastIndexOf('/') + 1);
        schema = "prism_equipment_migration_" + UUID.randomUUID().toString().replace("-", "");
        username = System.getenv().getOrDefault("PRISM_TEST_MYSQL_USERNAME", "root");
        password = System.getenv().getOrDefault("PRISM_TEST_MYSQL_PASSWORD", "");
        try (var root = DriverManager.getConnection(base + query, username, password); var sql = root.createStatement()) {
            sql.execute("CREATE DATABASE " + schema);
        }
        url = base + schema + query;
        migrate("20260917010346");
        connection = DriverManager.getConnection(url, username, password);
        execute("INSERT INTO users(id,name,email,password_hash,role,account_status,created_at,updated_at) VALUES(1,'Legacy user','legacy@example.test','hash','PENGGUNA','ACTIVE',NOW(),NOW())");
        execute("INSERT INTO facilities(id,code,name,type,location,capacity,administrative_status,created_at,updated_at) VALUES(1,'MIC','Microphone','Alat','Floor',20,'ACTIVE',NOW(),NOW()),(2,'ROOM','Classroom','Kelas','Floor',20,'ACTIVE',NOW(),NOW())");
        execute("INSERT INTO reservations(id,user_id,facility_id,start_at,end_at,purpose,status,expires_at,created_at,updated_at) VALUES(1,1,1,'2099-01-08 07:00:00','2099-01-08 08:00:00','Legacy A','APPROVED','2099-01-08 07:00:00',NOW(),NOW()),(2,1,1,'2099-01-08 08:00:00','2099-01-08 09:00:00','Legacy B','PENDING','2099-01-08 08:00:00',NOW(),NOW()),(3,1,2,'2099-01-08 07:00:00','2099-01-08 08:00:00','Legacy room','APPROVED','2099-01-08 07:00:00',NOW(),NOW())");
        migrate("20261011050000");
    }

    @AfterEach void cleanup() throws Exception {
        if (connection != null) connection.close();
        if (schema != null && schema.matches("prism_equipment_migration_[a-f0-9]{32}")) {
            try (var c = DriverManager.getConnection(url, username, password); var sql = c.createStatement()) {
                sql.execute("DROP DATABASE " + schema);
            }
        }
    }

    Flyway flyway(String target) {
        return Flyway.configure().dataSource(url, username, password).locations("classpath:db/migration").target(target).load();
    }
    void migrate(String target) { flyway(target).migrate(); }
    void execute(String statement) throws Exception { try (var sql = connection.createStatement()) { sql.execute(statement); } }
    long count(String query) throws Exception {
        try (var sql = connection.createStatement(); var result = sql.executeQuery(query)) { result.next(); return result.getLong(1); }
    }
    void backfill() throws Exception {
        execute("INSERT INTO equipment_stock_backfill VALUES(1,10)");
        execute("INSERT INTO equipment_quantity_backfill VALUES(1,5),(2,6)");
    }

    @Test void missingStockAndQuantitiesFailBeforeConversionThenRecoverWithoutDataLoss() throws Exception {
        assertThatThrownBy(() -> migrate("20261011060000")).hasStackTraceContaining("Legacy Alat stock missing");
        assertThat(count("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='facilities' AND column_name='total_stock'")).isZero();
        flyway("20261011060000").repair();
        execute("INSERT INTO equipment_stock_backfill VALUES(1,10)");
        assertThatThrownBy(() -> migrate("20261011060000")).hasStackTraceContaining("Legacy Alat quantities missing/invalid");
        flyway("20261011060000").repair();
        execute("INSERT INTO equipment_quantity_backfill VALUES(1,5),(2,6)");
        migrate("20261011060000");
        assertThat(count("SELECT COUNT(*) FROM reservations")).isEqualTo(3);
        assertThat(count("SELECT COUNT(*) FROM reservations WHERE (id=1 AND quantity=5 AND status='APPROVED') OR (id=2 AND quantity=6 AND status='PENDING') OR (id=3 AND quantity IS NULL AND status='APPROVED')")).isEqualTo(3);
        assertThat(count("SELECT total_stock FROM facilities WHERE id=1")).isEqualTo(10);
        assertThat(count("SELECT quantity_based FROM facility_types t JOIN facilities f ON t.id=f.facility_type_id WHERE f.id=1")).isEqualTo(1);
        assertThat(count("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('equipment_stock_backfill','equipment_quantity_backfill')")).isZero();
    }

    @Test void invalidConcurrentBackfillFailsWithHalfOpenBoundarySupport() throws Exception {
        backfill();
        execute("UPDATE reservations SET start_at='2099-01-08 07:30:00' WHERE id=2");
        assertThatThrownBy(() -> migrate("20261011060000")).hasStackTraceContaining("concurrent allocations exceed");
        flyway("20261011060000").repair();
        execute("UPDATE reservations SET start_at='2099-01-08 08:00:00' WHERE id=2");
        migrate("20261011060000");
        assertThat(count("SELECT COUNT(*) FROM reservations")).isEqualTo(3);
    }

    @Test void positiveDatabaseConstraintsAndOrdinaryDataSurviveUpgrade() throws Exception {
        backfill();
        execute("INSERT INTO reservations(id,user_id,facility_id,start_at,end_at,purpose,status,created_at,updated_at) VALUES(4,1,1,'2000-01-08 07:00:00','2000-01-08 08:00:00','Historical larger inventory','COMPLETED',NOW(),NOW())");
        execute("INSERT INTO equipment_quantity_backfill VALUES(4,25)");
        migrate("20261011060000");
        assertThat(count("SELECT quantity FROM reservations WHERE id=4")).isEqualTo(25);
        assertThat(count("SELECT total_stock FROM facilities WHERE id=1")).isEqualTo(10);
        assertThatThrownBy(() -> execute("UPDATE facilities SET total_stock=0 WHERE id=1")).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("UPDATE reservations SET quantity=-1 WHERE id=1")).isInstanceOf(SQLException.class);
        assertThat(count("SELECT COUNT(*) FROM facilities WHERE id=2 AND total_stock IS NULL")).isEqualTo(1);
    }
}
