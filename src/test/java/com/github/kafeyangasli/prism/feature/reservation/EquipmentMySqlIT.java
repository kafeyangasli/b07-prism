package com.github.kafeyangasli.prism.feature.reservation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

/** Runs the equipment workflow/security/race suite with Flyway and actual MySQL/InnoDB. */
@SpringBootTest(properties = {"spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
        "spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true"})
class EquipmentMySqlIT extends EquipmentReservationIntegrationTest {
    @DynamicPropertySource static void mysql(DynamicPropertyRegistry registry) {
        String url=System.getenv("PRISM_TEST_MYSQL_URL");
        if(url==null || !url.startsWith("jdbc:mysql:")) throw new IllegalStateException("PRISM_TEST_MYSQL_URL must name a disposable MySQL database");
        registry.add("spring.datasource.url",()->url);
        registry.add("spring.datasource.username",()->System.getenv().getOrDefault("PRISM_TEST_MYSQL_USERNAME","root"));
        registry.add("spring.datasource.password",()->System.getenv().getOrDefault("PRISM_TEST_MYSQL_PASSWORD",""));
    }
    @Autowired JdbcTemplate jdbc;
    @Test void actualMySqlInnoDbWithDefaultRepeatableRead() {
        assertThat(jdbc.queryForObject("SELECT VERSION()",String.class)).doesNotContainIgnoringCase("MariaDB");
        assertThat(jdbc.queryForObject("SELECT @@transaction_isolation",String.class)).isEqualTo("REPEATABLE-READ");
        assertThat(jdbc.queryForObject("SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='reservations'",String.class)).isEqualTo("InnoDB");
    }
}
