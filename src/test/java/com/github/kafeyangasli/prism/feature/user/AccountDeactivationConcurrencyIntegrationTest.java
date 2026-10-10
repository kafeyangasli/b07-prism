package com.github.kafeyangasli.prism.feature.user;

import com.github.kafeyangasli.prism.feature.user.dto.AccountDeletionForm;
import com.github.kafeyangasli.prism.feature.user.model.*;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.feature.user.service.AccountSettingsService;
import com.github.kafeyangasli.prism.feature.facility.model.*;
import com.github.kafeyangasli.prism.feature.facility.repository.*;
import com.github.kafeyangasli.prism.feature.reservation.model.*;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationProcessingService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:account-concurrency;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(AccountSettingsIntegrationTest.FixedClockConfiguration.class)
class AccountDeactivationConcurrencyIntegrationTest {
    @Autowired UserRepository users;
    @Autowired FacilityRepository facilities;
    @Autowired FacilityTypeRepository types;
    @Autowired ReservationRepository reservations;
    @Autowired AccountSettingsService accounts;
    @Autowired ReservationProcessingService processing;
    @Autowired PasswordEncoder encoder;
    User requester;
    User staff;
    Reservation pending;

    @BeforeEach void seed() {
        reservations.deleteAll(); facilities.deleteAll(); types.deleteAll(); users.deleteAll();
        requester = users.saveAndFlush(new User("Requester", "race@example.test", encoder.encode("old-password"), Role.PENGGUNA, AccountStatus.ACTIVE));
        staff = users.saveAndFlush(new User("Staff", "race-staff@example.test", "hash", Role.PETUGAS, AccountStatus.ACTIVE));
        FacilityType type = types.saveAndFlush(new FacilityType("RACE-ROOM", "Kelas", null));
        Facility room = facilities.saveAndFlush(new Facility("RACE-1", "Room", type, "Gedung C", 20, null, AdministrativeStatus.ACTIVE));
        LocalDateTime now = AccountSettingsIntegrationTest.NOW;
        pending = reservations.saveAndFlush(new Reservation(requester, room, now.plusDays(1), now.plusDays(1).plusHours(1),
                "Race", null, ReservationStatus.PENDING, now.plusHours(12)));
    }

    @RepeatedTest(5)
    void simultaneousApprovalAndDeactivationCannotBothSucceed() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> approval = executor.submit(() -> race(ready, start, this::approve));
            Future<Boolean> deletion = executor.submit(() -> race(ready, start, this::deactivate));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(List.of(approval.get(20, TimeUnit.SECONDS), deletion.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
        assertInvariant();
    }

    @Test void deactivationWinningBeforeApprovalPreventsApproval() {
        assertThat(deactivate()).isTrue();
        assertThat(approve()).isFalse();
        assertThat(reservations.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertInvariant();
    }
    @Test void approvalWinningBeforeDeactivationPreventsDeactivation() {
        assertThat(approve()).isTrue();
        assertThat(deactivate()).isFalse();
        assertThat(users.findById(requester.getId()).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertInvariant();
    }
    private Boolean race(CountDownLatch ready, CountDownLatch start, Supplier<Boolean> action) throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) throw new TimeoutException("Race start timed out");
        return action.get();
    }
    private boolean approve() {
        return as(staff, () -> processing.approve(pending.getId(), staff.getId(), true));
    }
    private boolean deactivate() {
        return as(requester, () -> {
            var form = new AccountDeletionForm();
            form.setConfirmed(true); form.setCurrentPassword("old-password");
            accounts.deactivateAccount(form);
        });
    }
    private boolean as(User account, Runnable action) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(account.getEmail(), "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + account.getRole().name()))));
        SecurityContextHolder.setContext(context);
        try {
            action.run(); return true;
        } catch (BusinessRuleException exception) {
            // Unexpected database/locking failures fail the test, not masquerade as business rejection.
            return false;
        } finally { SecurityContextHolder.clearContext(); }
    }
    private void assertInvariant() {
        AccountStatus status = users.findById(requester.getId()).orElseThrow().getAccountStatus();
        long approved = reservations.countActiveApproved(requester.getId(), ReservationStatus.APPROVED, AccountSettingsIntegrationTest.NOW);
        assertThat(status == AccountStatus.INACTIVE && approved > 0).isFalse();
    }
}
