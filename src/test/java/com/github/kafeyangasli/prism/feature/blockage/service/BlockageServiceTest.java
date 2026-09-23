package com.github.kafeyangasli.prism.feature.blockage.service;

import com.github.kafeyangasli.prism.feature.blockage.dto.BlockageImpactPreviewRequest;
import com.github.kafeyangasli.prism.feature.blockage.dto.BlockageImpactPreviewResponse;
import com.github.kafeyangasli.prism.feature.blockage.dto.CreateBlockageRequest;
import com.github.kafeyangasli.prism.feature.blockage.dto.EarlyCompletionRequest;
import com.github.kafeyangasli.prism.feature.blockage.dto.UpdateBlockageRequest;
import com.github.kafeyangasli.prism.feature.blockage.model.BlockageStatus;
import com.github.kafeyangasli.prism.feature.blockage.model.BlockageType;
import com.github.kafeyangasli.prism.feature.blockage.model.FacilityBlockage;
import com.github.kafeyangasli.prism.feature.blockage.repository.FacilityBlockageRepository;
import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.report.model.Report;
import com.github.kafeyangasli.prism.feature.report.model.ReportStatus;
import com.github.kafeyangasli.prism.feature.report.repository.ReportRepository;
import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
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

    @Mock
    private FacilityBlockageRepository facilityBlockageRepository;

    @Mock
    private BlockageTypeService blockageTypeService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private BlockageService blockageService;

    private Facility facility;
    private User user;
    private User staff;
    private BlockageType repairType;
    private BlockageType maintenanceType;
    private LocalDateTime startAt;
    private LocalDateTime plannedEndAt;

    @BeforeEach
    void setUp() {
        facility = new Facility("FAC01", "Gedung A", "Ruang Rapat", "Lantai 2", 50, "Desc", AdministrativeStatus.ACTIVE);
        ReflectionTestUtils.setField(facility, "id", 10L);

        user = new User("User One", "user1@example.com", "hash", Role.PENGGUNA, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);

        staff = new User("Staff Member", "staff@example.com", "hash", Role.PETUGAS, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(staff, "id", 2L);

        repairType = new BlockageType("REPAIR", "Repair", "Repair blockage");
        ReflectionTestUtils.setField(repairType, "id", 100L);

        maintenanceType = new BlockageType("PLANNED_MAINTENANCE", "Planned Maintenance", "Maintenance blockage");
        ReflectionTestUtils.setField(maintenanceType, "id", 101L);

        startAt = LocalDateTime.now().plusHours(1);
        plannedEndAt = startAt.plusHours(5);
    }

    @Test
    void previewBlockageImpact_Success_CountsApprovedAndPending() {
        BlockageImpactPreviewRequest request = new BlockageImpactPreviewRequest(10L, startAt, plannedEndAt);

        Reservation resApproved = new Reservation(user, facility, startAt, startAt.plusHours(2), "Meeting", null, ReservationStatus.APPROVED, null);
        Reservation resPending = new Reservation(user, facility, startAt.plusHours(2), startAt.plusHours(4), "Discussion", null, ReservationStatus.PENDING, null);

        when(facilityRepository.findById(10L)).thenReturn(Optional.of(facility));
        when(reservationRepository.findOverlapping(eq(10L), eq(startAt), eq(plannedEndAt), anyCollection()))
                .thenReturn(List.of(resApproved, resPending));

        BlockageImpactPreviewResponse response = blockageService.previewBlockageImpact(request);

        assertNotNull(response);
        assertEquals(1, response.getApprovedCount());
        assertEquals(1, response.getPendingCount());
        assertEquals(2, response.getTotalAffectedCount());
    }

    @Test
    void createBlockage_Success_CancelsApprovedAndRejectsPendingReservations() {
        CreateBlockageRequest request = new CreateBlockageRequest(10L, 101L, null, startAt, plannedEndAt, "AC Maintenance", "Internal note");

        Reservation resApproved = new Reservation(user, facility, startAt, startAt.plusHours(2), "Meeting", null, ReservationStatus.APPROVED, null);
        Reservation resPending = new Reservation(user, facility, startAt.plusHours(2), startAt.plusHours(4), "Discussion", null, ReservationStatus.PENDING, null);

        when(facilityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(facility));
        when(blockageTypeService.validateAndGetActiveBlockageType(101L)).thenReturn(maintenanceType);
        when(userRepository.findById(2L)).thenReturn(Optional.of(staff));
        when(facilityBlockageRepository.save(any(FacilityBlockage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reservationRepository.findOverlapping(eq(10L), eq(startAt), eq(plannedEndAt), anyCollection()))
                .thenReturn(List.of(resApproved, resPending));

        FacilityBlockage result = blockageService.createBlockage(request, 2L);

        assertNotNull(result);
        assertEquals(BlockageStatus.SCHEDULED, result.getStatus());
        assertEquals("AC Maintenance", result.getPublicReason());

        // Verify reservation impact handling
        assertEquals(ReservationStatus.CANCELLED, resApproved.getStatus());
        assertEquals("BLOCKAGE_PLANNED_MAINTENANCE", resApproved.getReasonCode());
        assertEquals(staff, resApproved.getCancelledBy());

        assertEquals(ReservationStatus.REJECTED, resPending.getStatus());
        assertEquals("BLOCKAGE_PLANNED_MAINTENANCE", resPending.getReasonCode());
        assertEquals(staff, resPending.getProcessedBy());
    }

    @Test
    void createBlockage_RepairWithoutReport_ThrowsException() {
        CreateBlockageRequest request = new CreateBlockageRequest(10L, 100L, null, startAt, plannedEndAt, "Repair AC", null);

        when(facilityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(facility));
        when(blockageTypeService.validateAndGetActiveBlockageType(100L)).thenReturn(repairType);

        assertThrows(BusinessRuleException.class, () -> blockageService.createBlockage(request, 2L));
    }

    @Test
    void createBlockage_RepairWithReport_Success() {
        Report report = new Report(user, facility, "AC", "Broken", null, ReportStatus.NEW);
        ReflectionTestUtils.setField(report, "id", 50L);

        CreateBlockageRequest request = new CreateBlockageRequest(10L, 100L, 50L, startAt, plannedEndAt, "Repair AC", null);

        when(facilityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(facility));
        when(blockageTypeService.validateAndGetActiveBlockageType(100L)).thenReturn(repairType);
        when(reportRepository.findById(50L)).thenReturn(Optional.of(report));
        when(userRepository.findById(2L)).thenReturn(Optional.of(staff));
        when(facilityBlockageRepository.save(any(FacilityBlockage.class))).thenAnswer(inv -> inv.getArgument(0));

        FacilityBlockage result = blockageService.createBlockage(request, 2L);

        assertNotNull(result);
        assertEquals(report, result.getReport());
    }

    @Test
    void reconcileBlockageLifecycle_ActivatesScheduledAndCompletesPlanned() {
        LocalDateTime pastStart = LocalDateTime.now().minusHours(2);
        LocalDateTime pastEnd = LocalDateTime.now().minusHours(1);

        FacilityBlockage scheduledBlockage = new FacilityBlockage(facility, maintenanceType, null, pastStart, plannedEndAt, BlockageStatus.SCHEDULED, "Reason", null, staff);
        FacilityBlockage activeBlockage = new FacilityBlockage(facility, maintenanceType, null, pastStart, pastEnd, BlockageStatus.ACTIVE, "Reason", null, staff);

        when(facilityBlockageRepository.findByStatusOrderByStartAtAsc(BlockageStatus.SCHEDULED)).thenReturn(List.of(scheduledBlockage));
        when(facilityBlockageRepository.findByStatusOrderByStartAtAsc(BlockageStatus.ACTIVE)).thenReturn(List.of(activeBlockage));

        int count = blockageService.reconcileBlockageLifecycle();

        assertEquals(2, count);
        assertEquals(BlockageStatus.ACTIVE, scheduledBlockage.getStatus());
        assertEquals(BlockageStatus.COMPLETED, activeBlockage.getStatus());
    }

    @Test
    void updateOrExtendBlockage_Extension_CancelsNewlyAffectedReservations() {
        FacilityBlockage blockage = new FacilityBlockage(facility, maintenanceType, null, startAt, plannedEndAt, BlockageStatus.ACTIVE, "Reason", null, staff);
        ReflectionTestUtils.setField(blockage, "id", 200L);

        LocalDateTime newEndAt = plannedEndAt.plusHours(3);
        UpdateBlockageRequest request = new UpdateBlockageRequest(newEndAt, "Extended maintenance", null);

        Reservation newApproved = new Reservation(user, facility, plannedEndAt, plannedEndAt.plusHours(2), "Late Meeting", null, ReservationStatus.APPROVED, null);

        when(facilityBlockageRepository.findById(200L)).thenReturn(Optional.of(blockage));
        when(userRepository.findById(2L)).thenReturn(Optional.of(staff));
        when(facilityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(facility));
        when(reservationRepository.findOverlapping(eq(10L), eq(plannedEndAt), eq(newEndAt), anyCollection()))
                .thenReturn(List.of(newApproved));
        when(facilityBlockageRepository.save(any(FacilityBlockage.class))).thenAnswer(inv -> inv.getArgument(0));

        FacilityBlockage result = blockageService.updateOrExtendBlockage(200L, request, 2L);

        assertEquals(newEndAt, result.getPlannedEndAt());
        assertEquals("Extended maintenance", result.getPublicReason());
        assertEquals(ReservationStatus.CANCELLED, newApproved.getStatus());
    }

    @Test
    void earlyCompleteBlockage_Success_RecordsActorAndActualEndAt() {
        FacilityBlockage blockage = new FacilityBlockage(facility, maintenanceType, null, startAt, plannedEndAt, BlockageStatus.ACTIVE, "Reason", null, staff);
        ReflectionTestUtils.setField(blockage, "id", 200L);

        EarlyCompletionRequest request = new EarlyCompletionRequest("Repairs finished early");

        when(facilityBlockageRepository.findById(200L)).thenReturn(Optional.of(blockage));
        when(userRepository.findById(2L)).thenReturn(Optional.of(staff));
        when(facilityBlockageRepository.save(any(FacilityBlockage.class))).thenAnswer(inv -> inv.getArgument(0));

        FacilityBlockage result = blockageService.earlyCompleteBlockage(200L, request, 2L);

        assertEquals(BlockageStatus.COMPLETED, result.getStatus());
        assertNotNull(result.getActualEndAt());
        assertEquals(staff, result.getEndedBy());
        assertEquals("Repairs finished early", result.getEarlyCompletionReason());
    }

    @Test
    void earlyCompleteBlockage_WithoutReason_ThrowsException() {
        FacilityBlockage blockage = new FacilityBlockage(facility, maintenanceType, null, startAt, plannedEndAt, BlockageStatus.ACTIVE, "Reason", null, staff);

        when(facilityBlockageRepository.findById(200L)).thenReturn(Optional.of(blockage));

        EarlyCompletionRequest request = new EarlyCompletionRequest("   ");

        assertThrows(BusinessRuleException.class, () -> blockageService.earlyCompleteBlockage(200L, request, 2L));
    }
}
