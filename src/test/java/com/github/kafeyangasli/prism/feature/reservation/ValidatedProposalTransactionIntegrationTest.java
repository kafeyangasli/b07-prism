package com.github.kafeyangasli.prism.feature.reservation;

import com.github.kafeyangasli.prism.feature.facility.model.*;
import com.github.kafeyangasli.prism.feature.facility.repository.*;
import com.github.kafeyangasli.prism.feature.reservation.model.*;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationProcessingService;
import com.github.kafeyangasli.prism.feature.user.model.*;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.storage.ProposalStorageService;
import com.github.kafeyangasli.prism.support.WithPrismUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import static com.github.kafeyangasli.prism.support.ProposalPdf.upload;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:validated-transaction;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop", "prism.scheduler.reservation-lifecycle-ms=3600000"
})
@Import(ReservationProcessingServiceIntegrationTest.FixedClockConfiguration.class)
@WithPrismUser(username = "atomic-staff@test", roles = "PETUGAS")
class ValidatedProposalTransactionIntegrationTest {
    static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 22, 10, 0);
    @TempDir static Path directory;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("prism.storage.proposals", () -> directory.toString());
    }
    @Autowired ReservationProcessingService service;
    @Autowired com.github.kafeyangasli.prism.feature.reservation.service.ReservationSubmissionService submissions;
    @Autowired ReservationRepository reservations;
    @Autowired UserRepository users;
    @Autowired FacilityRepository facilities;
    @Autowired FacilityTypeRepository facilityTypes;
    @Autowired ProposalStorageService storage;
    @Autowired PlatformTransactionManager transactions;
    User staff;
    Reservation pending;

    @BeforeEach void seed() throws Exception {
        reservations.deleteAll();
        facilities.deleteAll();
        facilityTypes.deleteAll();
        users.deleteAll();
        staff = users.saveAndFlush(new User("Staff", "atomic-staff@test", "hash", Role.PETUGAS, AccountStatus.ACTIVE));
        User owner = users.saveAndFlush(new User("Owner", "atomic-owner@test", "hash", Role.PENGGUNA, AccountStatus.ACTIVE));
        var type = facilityTypes.saveAndFlush(new FacilityType("ATOMIC", "Kelas", null));
        var facility = facilities.saveAndFlush(new Facility("ATOMIC", "Room", type, "Floor", 20, null, AdministrativeStatus.ACTIVE));
        String original = storage.store(upload());
        // A voluntarily submitted proposal is just as authoritative a prerequisite.
        pending = reservations.saveAndFlush(new Reservation(owner, facility, NOW.plusDays(1), NOW.plusDays(1).plusHours(1),
                "Event", original, ReservationStatus.PENDING, NOW.plusHours(12)));
    }

    @Test void databaseFailureAtCommitRollsBackAuditAndReferenceAndCleansUploadedFile() throws Exception {
        Set<String> before = files();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var reservation = service.validateProposal(pending.getId(), staff.getId(), upload());
            assertThat(reservation.hasValidatedProposal()).isTrue();
            assertThat(directory.resolve(reservation.getValidatedProposalPath())).exists();
            // A real NOT NULL database failure after the validation flush, at outer commit.
            reservation.setPurpose(null);
        })).isInstanceOf(RuntimeException.class);
        assertPriorState();
        assertThat(files()).isEqualTo(before);
    }

    @Test void failedSubmissionCommitCleansOriginalProposal() throws Exception {
        Set<String> before = files();
        long count = reservations.count();
        var form = new com.github.kafeyangasli.prism.feature.reservation.dto.ReservationForm();
        form.setFacilityId(pending.getFacility().getId());
        form.setStartAt(NOW.plusDays(2).withHour(7).toString());
        form.setEndAt(NOW.plusDays(2).withHour(8).toString());
        form.setPurpose("Event");
        var transaction = new TransactionTemplate(transactions);
        transaction.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        assertThatThrownBy(() -> transaction.executeWithoutResult(tx -> {
            var reservation = submissions.submit("atomic-owner@test", form, upload());
            assertThat(directory.resolve(reservation.getProposalPath())).exists();
            reservation.setPurpose(null);
        })).isInstanceOf(RuntimeException.class);
        assertThat(reservations.count()).isEqualTo(count);
        assertThat(files()).isEqualTo(before);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void approvalWaitsForVerificationCommitAndCannotUseRolledBackEvidence(boolean rollback) throws Exception {
        Set<String> before = files();
        CountDownLatch verified = new CountDownLatch(1), release = new CountDownLatch(1), approving = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> verification = executor.submit(() -> {
                authenticate();
                try {
                    new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                        service.validateProposal(pending.getId(), staff.getId(), upload());
                        verified.countDown();
                        await(release);
                        if (rollback) tx.setRollbackOnly();
                    });
                } finally { verified.countDown(); SecurityContextHolder.clearContext(); }
            });
            assertThat(verified.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> approval = executor.submit(() -> {
                authenticate();
                approving.countDown();
                try {
                    service.approve(pending.getId(), staff.getId(), false);
                    return true;
                } catch (BusinessRuleException exception) {
                    assertThat(exception.getMessage()).contains("divalidasi");
                    return false;
                } finally { SecurityContextHolder.clearContext(); }
            });
            assertThat(approving.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> approval.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            verification.get(10, TimeUnit.SECONDS);
            assertThat(approval.get(10, TimeUnit.SECONDS)).isEqualTo(!rollback);
            if (rollback) {
                assertPriorState();
                assertThat(files()).isEqualTo(before);
            } else {
                var saved = reservations.findById(pending.getId()).orElseThrow();
                assertThat(saved.getStatus()).isEqualTo(ReservationStatus.APPROVED);
                assertThat(saved.isProposalValidated()).isTrue();
                assertThat(saved.getProposalPath()).isEqualTo(pending.getProposalPath());
                assertThat(directory.resolve(saved.getValidatedProposalPath())).exists();
            }
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    private void assertPriorState() {
        var saved = reservations.findById(pending.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(saved.getProposalValidatedAt()).isNull();
        assertThat(saved.getProposalValidatedBy()).isNull();
        assertThat(saved.getValidatedProposalPath()).isNull();
        assertThat(saved.getProposalPath()).isEqualTo(pending.getProposalPath());
    }

    private Set<String> files() throws Exception {
        try (var files = Files.list(directory)) {
            return files.map(path -> path.getFileName().toString()).collect(Collectors.toSet());
        }
    }

    private void authenticate() {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(staff.getEmail(), "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_PETUGAS"))));
        SecurityContextHolder.setContext(context);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for test gate");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
