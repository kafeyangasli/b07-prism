package com.github.kafeyangasli.prism.feature.reservation;

import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationProcessingService;
import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:concurrency;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(ReservationApprovalConcurrencyIntegrationTest.FixedClockConfiguration.class)
class ReservationApprovalConcurrencyIntegrationTest {

    private static final ZoneId WIB = ZoneId.of("Asia/Jakarta");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 22, 10, 0);

    @Autowired ReservationProcessingService service;
    @Autowired ReservationRepository reservations;
    @Autowired FacilityRepository facilities;
    @Autowired UserRepository users;

    @BeforeEach
    void cleanDatabase() {
        reservations.deleteAll();
        facilities.deleteAll();
        users.deleteAll();
    }

    @Test
    void simultaneousOverlappingApprovalsCannotDoubleBookFacility() throws Exception {
        User actor = user("Staff", "concurrent-staff@example.test", Role.PETUGAS);
        User firstUser = user("First", "concurrent-first@example.test", Role.PENGGUNA);
        User secondUser = user("Second", "concurrent-second@example.test", Role.PENGGUNA);
        Facility room = facility("CONC-1");
        LocalDateTime start = NOW.plusDays(1).withHour(9);
        Reservation first = pending(firstUser, room, start, start.plusHours(1));
        Reservation second = pending(secondUser, room, start.plusMinutes(30), start.plusHours(2));

        List<Boolean> results = race(
                () -> tryApprove(first.getId(), actor.getId()),
                () -> tryApprove(second.getId(), actor.getId()));

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(List.of(reservations.findById(first.getId()).orElseThrow().getStatus(),
                           reservations.findById(second.getId()).orElseThrow().getStatus()))
                .containsExactlyInAnyOrder(ReservationStatus.APPROVED, ReservationStatus.REJECTED);
    }

    @Test
    void simultaneousApprovalsOnDifferentFacilitiesCannotExceedSevenForUser() throws Exception {
        User actor = user("Staff", "limit-staff@example.test", Role.PETUGAS);
        User requester = user("Requester", "limit-user@example.test", Role.PENGGUNA);
        Facility firstRoom = facility("LIMIT-1");
        Facility secondRoom = facility("LIMIT-2");
        for (int index = 0; index < 6; index++) {
            reservations.save(new Reservation(requester, firstRoom,
                    NOW.plusDays(index + 1).withHour(8), NOW.plusDays(index + 1).withHour(9),
                    "Existing", null, ReservationStatus.APPROVED, NOW.plusHours(1)));
        }
        Reservation first = pending(requester, firstRoom,
                NOW.plusDays(8).withHour(9), NOW.plusDays(8).withHour(10));
        Reservation second = pending(requester, secondRoom,
                NOW.plusDays(9).withHour(9), NOW.plusDays(9).withHour(10));

        List<Boolean> results = race(
                () -> tryApprove(first.getId(), actor.getId()),
                () -> tryApprove(second.getId(), actor.getId()));

        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(List.of(reservations.findById(first.getId()).orElseThrow().getStatus(),
                           reservations.findById(second.getId()).orElseThrow().getStatus()))
                .containsExactlyInAnyOrder(ReservationStatus.APPROVED, ReservationStatus.PENDING);
        assertThat(reservations.countActiveApproved(requester.getId(), ReservationStatus.APPROVED, NOW))
                .isEqualTo(7);
    }

    private List<Boolean> race(java.util.concurrent.Callable<Boolean> first,
                               java.util.concurrent.Callable<Boolean> second) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> firstResult = executor.submit(() -> awaitAndRun(ready, start, first));
            Future<Boolean> secondResult = executor.submit(() -> awaitAndRun(ready, start, second));
            ready.await();
            start.countDown();
            return List.of(firstResult.get(), secondResult.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private Boolean awaitAndRun(CountDownLatch ready, CountDownLatch start,
                                java.util.concurrent.Callable<Boolean> action) throws Exception {
        ready.countDown();
        start.await();
        return action.call();
    }

    private boolean tryApprove(long reservationId, long actorId) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken("staff", "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_PETUGAS"))));
        SecurityContextHolder.setContext(context);
        try {
            service.approve(reservationId, actorId, true);
            return true;
        } catch (RuntimeException exception) {
            return false;
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private User user(String name, String email, Role role) {
        return users.save(new User(name, email, "hash", role, AccountStatus.ACTIVE));
    }

    private Facility facility(String code) {
        return facilities.save(new Facility(code, code, "Kelas", "Gedung C", 20,
                null, AdministrativeStatus.ACTIVE));
    }

    private Reservation pending(User requester, Facility facility,
                                LocalDateTime start, LocalDateTime end) {
        return reservations.save(new Reservation(requester, facility, start, end,
                "Concurrent", null, ReservationStatus.PENDING, NOW.plusHours(12)));
    }

    @TestConfiguration
    static class FixedClockConfiguration {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW.atZone(WIB).toInstant(), WIB);
        }
    }
}
