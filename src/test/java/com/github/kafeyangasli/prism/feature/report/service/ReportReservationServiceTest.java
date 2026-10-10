package com.github.kafeyangasli.prism.feature.report.service;

import com.github.kafeyangasli.prism.feature.facility.model.*;
import com.github.kafeyangasli.prism.feature.reservation.model.*;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.user.model.*;
import com.github.kafeyangasli.prism.shared.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReportReservationServiceTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 10, 12, 0);
    private final Clock clock = Clock.fixed(now.atZone(ZoneId.of("Asia/Jakarta")).toInstant(), ZoneId.of("Asia/Jakarta"));
    private final ReservationRepository reservations = mock(ReservationRepository.class);
    private final ReportReservationService service = new ReportReservationService(reservations, clock);
    private User owner;

    @BeforeEach void setUp() {
        owner = new User("Owner", "owner@example.test", "hash", Role.PENGGUNA, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(owner, "id", 42L);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("owner@example.test", "unused", java.util.List.of()));
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void inclusiveWindowUsesEndTimeAndNotCalendarDay() {
        for (LocalDateTime end : new LocalDateTime[]{now, now.minusHours(1), now.minusDays(1)}) {
            var reservation = reservation(end, ReservationStatus.COMPLETED);
            when(reservations.findByIdAndUserId(10L, 42L)).thenReturn(Optional.of(reservation));
            assertSame(reservation, service.eligibleReservation(10L, 42L));
            assertTrue(service.canReport(reservation));
        }
        for (LocalDateTime end : new LocalDateTime[]{now.plusNanos(1), now.minusDays(1).minusNanos(1)}) {
            var reservation = reservation(end, ReservationStatus.COMPLETED);
            when(reservations.findByIdAndUserId(10L, 42L)).thenReturn(Optional.of(reservation));
            assertThrows(BusinessRuleException.class, () -> service.eligibleReservation(10L, 42L));
            assertFalse(service.canReport(reservation));
        }
    }

    @ParameterizedTest @EnumSource(ReservationStatus.class)
    void onlyCompletedOrEndedApprovedReservationsQualify(ReservationStatus status) {
        var reservation = reservation(now.minusHours(1), status);
        when(reservations.findByIdAndUserId(10L, 42L)).thenReturn(Optional.of(reservation));
        boolean eligible = status == ReservationStatus.COMPLETED || status == ReservationStatus.APPROVED;
        assertEquals(eligible, service.canReport(reservation));
        if (eligible) assertSame(reservation, service.eligibleReservation(10L, 42L));
        else assertThrows(BusinessRuleException.class, () -> service.eligibleReservation(10L, 42L));
    }

    @Test void anotherOwnerAndMissingReservationCannotBeUsed() {
        when(reservations.findByIdAndUserId(10L, 84L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.eligibleReservation(10L, 84L));
        verify(reservations).findByIdAndUserId(10L, 84L);
        verify(reservations, never()).findById(anyLong());
        var reservation = reservation(now.minusHours(1), ReservationStatus.COMPLETED);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("other@example.test", "unused", java.util.List.of()));
        assertFalse(service.canReport(reservation));
        SecurityContextHolder.clearContext();
        assertFalse(service.canReport(reservation));
    }

    private Reservation reservation(LocalDateTime end, ReservationStatus status) {
        var facility = new Facility("R01", "Room", new FacilityType("ROOM", "Room", null), "Floor", 10, null, AdministrativeStatus.ACTIVE);
        return new Reservation(owner, facility, end.minusHours(1), end, "Meeting", null, status, null);
    }
}
