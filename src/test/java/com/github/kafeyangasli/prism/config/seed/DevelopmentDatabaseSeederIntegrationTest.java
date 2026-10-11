package com.github.kafeyangasli.prism.config.seed;

import com.github.kafeyangasli.prism.feature.facility.model.*;
import com.github.kafeyangasli.prism.feature.facility.repository.*;
import com.github.kafeyangasli.prism.feature.user.model.*;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:development-seed;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "prism.seed.enabled=true"})
@ActiveProfiles("dev")
@AutoConfigureMockMvc
class DevelopmentDatabaseSeederIntegrationTest {
    @Autowired DevelopmentDatabaseSeeder seeder;
    @Autowired FacilityRepository facilities;
    @Autowired FacilityTypeRepository types;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired MockMvc mvc;
    @Autowired Clock clock;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @TempDir Path directory;

    @Test void freshStartupSeedsAllFacilitiesAndAuthenticatableVerifiedAccountsAndRepeatsSafely() throws Exception {
        assertThat(facilities.count()).isEqualTo(18);
        assertThat(types.count()).isEqualTo(7);
        assertThat(users.count()).isEqualTo(4);
        for (var entry : FacilitySeedSource.read(Path.of("initial_data"))) {
            var facility = facilities.findByCodeIgnoreCase(entry.code()).orElseThrow();
            assertThat(facility.getName()).isEqualTo(entry.name());
            assertThat(facility.getDescription()).isEqualTo(entry.description());
            assertThat(facility.getCapacity()).isEqualTo(entry.capacity());
            assertThat(facility.getTotalStock()).isEqualTo(entry.stock());
            assertThat(facility.getLocation()).isEqualTo("Universitas Diponegoro");
            assertThat(facility.getAdministrativeStatus()).isEqualTo(AdministrativeStatus.ACTIVE);
            assertThat(types.findByCodeIgnoreCase(entry.typeCode()).orElseThrow().isQuantityBased())
                    .isEqualTo(entry.stock() != null);
        }
        Map<String, Role> roles = Map.of("pengguna1", Role.PENGGUNA, "pengguna2", Role.PENGGUNA,
                "petugas", Role.PETUGAS, "admin", Role.ADMIN);
        for (var entry : roles.entrySet()) {
            String email = entry.getKey() + "@university.ac.id";
            String password = entry.getValue().name().toLowerCase() + "_prism";
            User user = users.findByEmailIgnoreCase(email).orElseThrow();
            assertThat(user.getRole()).isEqualTo(entry.getValue());
            assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
            assertThat(user.getVerifiedAt()).isNotNull();
            if (user.getRole() != Role.ADMIN) assertThat(user.getVerifiedBy().getId())
                    .isEqualTo(users.findByEmailIgnoreCase("admin@university.ac.id").orElseThrow().getId());
            assertThat(user.getPasswordHash()).isNotEqualTo(password);
            assertThat(encoder.matches(password, user.getPasswordHash())).isTrue();
            mvc.perform(formLogin().user(email).password(password)).andExpect(authenticated().withUsername(email)
                    .withRoles(entry.getValue().name()));
        }
        User edited = users.findByEmailIgnoreCase("pengguna1@university.ac.id").orElseThrow();
        String oldHash = edited.getPasswordHash();
        edited.setAccountStatus(AccountStatus.INACTIVE);
        users.save(edited);
        Facility editedFacility = facilities.findById(facilities.findByCodeIgnoreCase("F001").orElseThrow().getId()).orElseThrow();
        editedFacility.setDescription("Edited by administrator");
        facilities.save(editedFacility);
        users.save(new User("Existing", "existing@example.test", encoder.encode("custom"), Role.PENGGUNA, AccountStatus.ACTIVE));
        seeder.run(new DefaultApplicationArguments());
        assertThat(facilities.count()).isEqualTo(18);
        assertThat(types.count()).isEqualTo(7);
        assertThat(users.count()).isEqualTo(5);
        assertThat(users.findByEmailIgnoreCase(edited.getEmail()).orElseThrow().getPasswordHash()).isEqualTo(oldHash);
        assertThat(users.findByEmailIgnoreCase(edited.getEmail()).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(facilities.findByCodeIgnoreCase("F001").orElseThrow().getDescription()).isEqualTo("Edited by administrator");

        // A missing description still inserts; the existing type is reused even with a legacy code.
        FacilityType legacy = types.findByCodeIgnoreCase("RUANG").orElseThrow();
        jdbc.update("UPDATE facility_types SET code = 'RUANG-0000000001' WHERE id = ?", legacy.getId());
        Files.writeString(directory.resolve("missing.txt"), "F019,Ruang Baru,20");
        Files.writeString(directory.resolve("multiline.txt"), "F022,Ruang Deskripsi,\"Baris pertama, kedua\n  baris ketiga\",20");
        runSource(directory);
        Facility missing = facilities.findByCodeIgnoreCase("F019").orElseThrow();
        assertThat(missing.getDescription()).isNull();
        assertThat(facilities.findById(missing.getId()).orElseThrow().getFacilityType().getId()).isEqualTo(legacy.getId());
        assertThat(facilities.findByCodeIgnoreCase("F022").orElseThrow().getDescription())
                .isEqualTo("Baris pertama, kedua\n  baris ketiga");
        assertThat(types.count()).isEqualTo(7);

        // A conflict after an earlier insertion must roll back that insertion too.
        jdbc.update("UPDATE facility_types SET quantity_based = TRUE WHERE id = ?", legacy.getId());
        Files.writeString(directory.resolve("a.txt"), "F020,Aula Baru,20");
        Files.writeString(directory.resolve("b.txt"), "F021,Ruang Konflik,20");
        assertThatThrownBy(() -> runSource(directory)).hasMessageContaining("F021").hasMessageContaining("incompatible");
        assertThat(facilities.existsByCodeIgnoreCase("F020")).isFalse();
        assertThat(facilities.count()).isEqualTo(20);
    }

    private void runSource(Path source) {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            try {
                new DevelopmentDatabaseSeeder(facilities, types, users, encoder, clock, source.toString())
                        .run(new DefaultApplicationArguments());
            } catch (java.io.IOException ex) {
                throw new java.io.UncheckedIOException(ex);
            }
        });
    }
}
