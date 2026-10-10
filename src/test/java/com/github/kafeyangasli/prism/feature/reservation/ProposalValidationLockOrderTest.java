package com.github.kafeyangasli.prism.feature.reservation;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.github.kafeyangasli.prism.feature.blockage.repository.FacilityBlockageRepository;
import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationProcessingService;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.shared.exception.storage.ProposalStorageService;

class ProposalValidationLockOrderTest {
    @Test
    void validatedApprovalKeepsFacilityThenUserThenReservationLocksBeforeConflictChecks() {
        ReservationRepository reservations = mock(ReservationRepository.class);
        FacilityRepository facilities = mock(FacilityRepository.class);
        UserRepository users = mock(UserRepository.class);
        FacilityBlockageRepository blockages = mock(FacilityBlockageRepository.class);
        LocalDateTime now = LocalDateTime.of(2026, 9, 22, 10, 0);
        ReservationProcessingService service = new ReservationProcessingService(reservations, facilities, users,
                blockages, Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC), mock(ProposalStorageService.class));
        ReservationRepository.ReservationLockContext context = mock(ReservationRepository.ReservationLockContext.class);
        Facility facility = mock(Facility.class);
        User requester = mock(User.class);
        User staff = mock(User.class);
        Reservation pending = mock(Reservation.class);
        when(context.getFacilityId()).thenReturn(10L);
        when(context.getUserId()).thenReturn(20L);
        when(reservations.findLockContextById(1L)).thenReturn(Optional.of(context));
        when(facilities.findByIdForUpdate(10L)).thenReturn(Optional.of(facility));
        when(users.findByIdForUpdate(20L)).thenReturn(Optional.of(requester));
        when(reservations.findByIdForUpdate(1L)).thenReturn(Optional.of(pending));
        when(users.findById(30L)).thenReturn(Optional.of(staff));
        when(staff.getRole()).thenReturn(Role.PETUGAS);
        when(facility.getId()).thenReturn(10L);
        when(facility.getAdministrativeStatus()).thenReturn(AdministrativeStatus.ACTIVE);
        when(requester.getId()).thenReturn(20L);
        when(requester.getAccountStatus()).thenReturn(AccountStatus.ACTIVE);
        when(pending.getId()).thenReturn(1L);
        when(pending.getFacility()).thenReturn(facility);
        when(pending.getUser()).thenReturn(requester);
        when(pending.getStatus()).thenReturn(ReservationStatus.PENDING);
        when(pending.getStartAt()).thenReturn(now.plusDays(1));
        when(pending.getEndAt()).thenReturn(now.plusDays(1).plusHours(6));
        when(pending.getExpiresAt()).thenReturn(now.plusHours(12));
        when(pending.getProposalValidatedAt()).thenReturn(now);
        when(pending.getProposalValidatedBy()).thenReturn(staff);

        service.approve(1L, 30L, false);

        InOrder order = inOrder(reservations, facilities, users, blockages);
        order.verify(reservations).findLockContextById(1L);
        order.verify(facilities).findByIdForUpdate(10L);
        order.verify(users).findByIdForUpdate(20L);
        order.verify(reservations).findByIdForUpdate(1L);
        order.verify(reservations).existsOverlapping(eq(10L), any(), any(), any(), eq(1L));
        order.verify(blockages).existsOverlapping(eq(10L), any(), any(), any(), isNull());
        order.verify(reservations).countActiveApproved(eq(20L), eq(ReservationStatus.APPROVED), eq(now));
        verify(pending).setStatus(ReservationStatus.APPROVED);
    }
}
