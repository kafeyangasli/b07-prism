package com.github.kafeyangasli.prism.feature.reservation;

import com.github.kafeyangasli.prism.feature.blockage.dto.*;
import com.github.kafeyangasli.prism.feature.blockage.model.*;
import com.github.kafeyangasli.prism.feature.blockage.repository.*;
import com.github.kafeyangasli.prism.feature.blockage.service.BlockageService;
import com.github.kafeyangasli.prism.feature.facility.model.*;
import com.github.kafeyangasli.prism.feature.facility.repository.*;
import com.github.kafeyangasli.prism.feature.reservation.model.*;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationProcessingService;
import com.github.kafeyangasli.prism.feature.user.model.*;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.security.PrismUserDetails;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;

/** Explicit opt-in: requires a disposable real MySQL database; never falls back to H2. */
@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=validate", "spring.flyway.enabled=true",
        "spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver", "prism.scheduler.reservation-lifecycle-ms=3600000"})
@Import(MySqlConcurrencyIT.TimeConfiguration.class)
class MySqlConcurrencyIT {
    static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 7, 10, 0);
    @DynamicPropertySource static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("PRISM_TEST_MYSQL_URL");
        if (url == null || !url.startsWith("jdbc:mysql:")) throw new IllegalStateException("PRISM_TEST_MYSQL_URL must name a disposable MySQL database");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("PRISM_TEST_MYSQL_USERNAME", "root"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("PRISM_TEST_MYSQL_PASSWORD", ""));
    }
    @Autowired ReservationProcessingService service;
    @Autowired BlockageService blockageService;
    @Autowired ReservationRepository reservations;
    @Autowired FacilityRepository facilities;
    @Autowired FacilityTypeRepository facilityTypes;
    @Autowired FacilityBlockageRepository blockages;
    @Autowired BlockageTypeRepository blockageTypes;
    @Autowired UserRepository users;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    User actor, requester;
    Facility firstRoom, secondRoom;
    static final AtomicBoolean synchronizePreviews = new AtomicBoolean();
    static CyclicBarrier previews;

    @BeforeEach void seed() {
        synchronizePreviews.set(false);
        blockages.deleteAll();
        reservations.deleteAll();
        facilities.deleteAll();
        facilityTypes.deleteAll();
        users.deleteAll();
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).doesNotContainIgnoringCase("MariaDB");
        assertThat(jdbc.queryForObject("SELECT @@transaction_isolation", String.class)).isEqualTo("REPEATABLE-READ");
        assertThat(jdbc.queryForObject("SELECT ENGINE FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='reservations'", String.class)).isEqualTo("InnoDB");
        actor = users.saveAndFlush(new User("Staff", "mysql-staff@example.test", "hash", Role.PETUGAS, AccountStatus.ACTIVE));
        requester = users.saveAndFlush(new User("Requester", "mysql-user@example.test", "hash", Role.PENGGUNA, AccountStatus.ACTIVE));
        var type = facilityTypes.saveAndFlush(new FacilityType("MYSQL", "MySQL Classroom", null));
        firstRoom = facilities.saveAndFlush(new Facility("MYSQL-1", "First", type, "Floor", 20, null, AdministrativeStatus.ACTIVE));
        secondRoom = facilities.saveAndFlush(new Facility("MYSQL-2", "Second", type, "Floor", 20, null, AdministrativeStatus.ACTIVE));
        previews = new CyclicBarrier(2);
    }

    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void simultaneousApprovals() throws Exception {
        Reservation first = pending(firstRoom, 1), second = pending(firstRoom, 1);
        synchronizePreviews.set(true);
        assertThat(race(() -> approve(first), () -> approve(second))).containsExactlyInAnyOrder(true, false);
        assertThat(statuses(first, second)).containsExactlyInAnyOrder(ReservationStatus.APPROVED, ReservationStatus.REJECTED);
    }

    @Test void sevenReservationLimitAfterBothPreLockReads() throws Exception {
        for (int i = 1; i <= 6; i++) approved(firstRoom, i);
        Reservation first = pending(firstRoom, 8), second = pending(secondRoom, 9);
        synchronizePreviews.set(true);
        assertThat(race(() -> approve(first), () -> approve(second))).containsExactlyInAnyOrder(true, false);
        assertThat(reservations.countActiveApproved(requester.getId(), ReservationStatus.APPROVED, NOW)).isEqualTo(7);
        assertThat(statuses(first, second)).containsExactlyInAnyOrder(ReservationStatus.APPROVED, ReservationStatus.PENDING);
    }

    @Test void approvalVersusBlockage() throws Exception {
        Reservation request = pending(firstRoom, 1);
        var type = blockageTypes.findByCodeIgnoreCase("PLANNED_MAINTENANCE").orElseThrow();
        var previewRequest = new BlockageImpactPreviewRequest(firstRoom.getId(), request.getStartAt(), request.getEndAt());
        previewRequest.setBlockageTypeId(type.getId());
        previewRequest.setPublicReason("MySQL maintenance");
        authenticate();
        String token = blockageService.previewBlockageImpact(previewRequest).getConfirmationToken();
        var create = new CreateBlockageRequest(firstRoom.getId(), type.getId(), null, request.getStartAt(), request.getEndAt(), "MySQL maintenance", null);
        create.setConfirmationToken(token);
        create.setConfirmed(true);
        // The preview token binds statuses. If approval wins first, a fresh preview is required.
        race(() -> approve(request), () -> {
            authenticate();
            try {
                try { blockageService.createBlockage(create); }
                catch (BusinessRuleException changedImpact) {
                    assertThat(changedImpact.getCode()).isEqualTo("IMPACT_CHANGED");
                    create.setConfirmationToken(blockageService.previewBlockageImpact(previewRequest).getConfirmationToken());
                    blockageService.createBlockage(create);
                }
                return true;
            } finally { SecurityContextHolder.clearContext(); }
        });
        assertThat(blockages.findAll()).hasSize(1);
        assertThat(reservations.findById(request.getId()).orElseThrow().getStatus())
                .isIn(ReservationStatus.REJECTED, ReservationStatus.CANCELLED);
    }

    @Test void earlierSnapshotSurvivesLockAtRepeatableRead() throws Exception {
        assertSnapshotVisibility(TransactionDefinition.ISOLATION_REPEATABLE_READ, 0);
    }

    @Test void readCommittedSeesCommitAfterLock() throws Exception {
        assertSnapshotVisibility(TransactionDefinition.ISOLATION_READ_COMMITTED, 1);
    }

    private void assertSnapshotVisibility(int isolation, long expected) throws Exception {
        Reservation first = pending(firstRoom, 1), second = pending(secondRoom, 2);
        CountDownLatch snapshotRead = new CountDownLatch(1), committed = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            Future<Long> reader = executor.submit(() -> {
                var tx = new TransactionTemplate(transactions);
                tx.setIsolationLevel(isolation);
                return tx.execute(status -> {
                    reservations.findLockContextById(second.getId()).orElseThrow();
                    snapshotRead.countDown();
                    await(committed);
                    facilities.findByIdForUpdate(secondRoom.getId()).orElseThrow();
                    users.findByIdForUpdate(requester.getId()).orElseThrow();
                    return reservations.countActiveApproved(requester.getId(), ReservationStatus.APPROVED, NOW);
                });
            });
            assertThat(snapshotRead.await(15, TimeUnit.SECONDS)).isTrue();
            assertThat(approve(first)).isTrue();
            committed.countDown();
            assertThat(reader.get(20, TimeUnit.SECONDS)).isEqualTo(expected);
        } finally { committed.countDown(); executor.shutdownNow(); }
    }

    private void authenticate() {
        var principal = new PrismUserDetails(actor);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, "hash", principal.getAuthorities()));
    }
    private boolean approve(Reservation reservation) {
        authenticate();
        try { service.approve(reservation.getId(), actor.getId(), true); return true; }
        catch (BusinessRuleException expected) { return false; }
        finally { SecurityContextHolder.clearContext(); }
    }
    private Reservation pending(Facility room, int day) {
        LocalDateTime start = NOW.plusDays(day).withHour(9);
        return reservations.saveAndFlush(new Reservation(requester, room, start, start.plusHours(1), "MySQL test", null, ReservationStatus.PENDING, NOW.plusHours(12)));
    }
    private void approved(Facility room, int day) {
        LocalDateTime start = NOW.plusDays(day).withHour(7);
        reservations.saveAndFlush(new Reservation(requester, room, start, start.plusHours(1), "Existing", null, ReservationStatus.APPROVED, NOW.plusHours(12)));
    }
    private List<ReservationStatus> statuses(Reservation a, Reservation b) {
        return List.of(reservations.findById(a.getId()).orElseThrow().getStatus(), reservations.findById(b.getId()).orElseThrow().getStatus());
    }
    private List<Boolean> race(Callable<Boolean> a, Callable<Boolean> b) throws Exception {
        CyclicBarrier start = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = executor.submit(() -> { start.await(15, TimeUnit.SECONDS); return a.call(); });
            Future<Boolean> second = executor.submit(() -> { start.await(15, TimeUnit.SECONDS); return b.call(); });
            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(15, TimeUnit.SECONDS)) throw new AssertionError("Coordination timed out"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }
    @TestConfiguration static class TimeConfiguration {
        @Bean static org.springframework.beans.factory.config.BeanPostProcessor coordinateRealRepository() {
            return new org.springframework.beans.factory.config.BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof ReservationRepository)) return bean;
                    var proxy = new org.springframework.aop.framework.ProxyFactory(bean);
                    proxy.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
                        Object result = invocation.proceed();
                        if (invocation.getMethod().getName().equals("findLockContextById") && synchronizePreviews.get())
                            previews.await(15, TimeUnit.SECONDS);
                        return result;
                    });
                    return proxy.getProxy();
                }
            };
        }
        @Bean @Primary Clock clock() {
            var zone = ZoneId.of("Asia/Jakarta");
            return Clock.fixed(NOW.atZone(zone).toInstant(), zone);
        }
    }
}
