package com.github.kafeyangasli.prism.feature.blockage.service;

import com.github.kafeyangasli.prism.feature.blockage.dto.BlockageImpactPreviewRequest;
import com.github.kafeyangasli.prism.feature.blockage.dto.BlockageImpactPreviewResponse;
import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlockageServiceTest {

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @InjectMocks
    private BlockageService blockageService;

    private Facility facility;
    private User user;
    private LocalDateTime startAt;
    private LocalDateTime plannedEndAt;

    @BeforeEach
    void setUp() {
        facility = new Facility("FAC01", "Gedung A", "Ruang Rapat", "Lantai 2", 50, "Desc", AdministrativeStatus.ACTIVE);
        ReflectionTestUtils.setField(facility, "id", 10L);

        user = new User("User One", "user1@example.com", "hash", Role.PENGGUNA, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);

        startAt = LocalDateTime.of(2026, 10, 1, 9, 0);
        plannedEndAt = LocalDateTime.of(2026, 10, 1, 17, 0);
    }

    @Test
    void previewBlockageImpact_Success_CountsApprovedAndPending() {
        BlockageImpactPreviewRequest request = new BlockageImpactPreviewRequest(10L, startAt, plannedEndAt);

        Reservation resApproved1 = new Reservation(user, facility, startAt, startAt.plusHours(2), "Meeting", null, ReservationStatus.APPROVED, null);
        Reservation resApproved2 = new Reservation(user, facility, startAt.plusHours(2), startAt.plusHours(4), "Workshop", null, ReservationStatus.APPROVED, null);
        Reservation resPending = new Reservation(user, facility, startAt.plusHours(4), startAt.plusHours(6), "Discussion", null, ReservationStatus.PENDING, null);

        when(facilityRepository.findById(10L)).thenReturn(Optional.of(facility));
        when(reservationRepository.findOverlapping(eq(10L), eq(startAt), eq(plannedEndAt), anyCollection()))
                .thenReturn(List.of(resApproved1, resApproved2, resPending));

        BlockageImpactPreviewResponse response = blockageService.previewBlockageImpact(request);

        assertNotNull(response);
        assertEquals(10L, response.getFacilityId());
        assertEquals("Gedung A", response.getFacilityName());
        assertEquals(2, response.getApprovedCount());
        assertEquals(1, response.getPendingCount());
        assertEquals(3, response.getTotalAffectedCount());
    }

    @Test
    void previewBlockageImpact_OpenEnded_Success() {
        BlockageImpactPreviewRequest request = new BlockageImpactPreviewRequest(10L, startAt, null);

        Reservation resApproved = new Reservation(user, facility, startAt, startAt.plusHours(2), "Meeting", null, ReservationStatus.APPROVED, null);

        when(facilityRepository.findById(10L)).thenReturn(Optional.of(facility));
        when(reservationRepository.findOverlapping(eq(10L), eq(startAt), isNull(), anyCollection()))
                .thenReturn(List.of(resApproved));

        BlockageImpactPreviewResponse response = blockageService.previewBlockageImpact(request);

        assertNotNull(response);
        assertEquals(1, response.getApprovedCount());
        assertEquals(0, response.getPendingCount());
        assertNull(response.getPlannedEndAt());
    }

    @Test
    void previewBlockageImpact_FacilityNotFound_ThrowsException() {
        BlockageImpactPreviewRequest request = new BlockageImpactPreviewRequest(99L, startAt, plannedEndAt);

        when(facilityRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> blockageService.previewBlockageImpact(request));
    }

    @Test
    void previewBlockageImpact_InvalidTimeRange_ThrowsException() {
        BlockageImpactPreviewRequest request = new BlockageImpactPreviewRequest(10L, plannedEndAt, startAt);

        assertThrows(BusinessRuleException.class, () -> blockageService.previewBlockageImpact(request));
    }

    @Test
    void previewBlockageImpact_MissingFacilityId_ThrowsException() {
        BlockageImpactPreviewRequest request = new BlockageImpactPreviewRequest(null, startAt, plannedEndAt);

        assertThrows(BusinessRuleException.class, () -> blockageService.previewBlockageImpact(request));
    }
}
