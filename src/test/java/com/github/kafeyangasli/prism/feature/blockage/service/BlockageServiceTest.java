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
import com.github.kafeyangasli.prism.feature.administration.service.StaffActorResolver;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @Mock
    private StaffActorResolver staffActorResolver;

    @Mock private jakarta.persistence.EntityManager entityManager;

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
        authenticate("staff@example.com", "PETUGAS");
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
        ReflectionTestUtils.setField(resApproved, "id", 30L);
        ReflectionTestUtils.setField(resPending, "id", 31L);
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
        when(staffActorResolver.resolveId("staff@example.com")).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(staff));
        when(facilityBlockageRepository.save(any(FacilityBlockage.class))).thenAnswer(inv -> inv.getArgument(0));
        ReflectionTestUtils.setField(resApproved, "id", 30L);
        ReflectionTestUtils.setField(resPending, "id", 31L);
        when(reservationRepository.findOverlapping(eq(10L), eq(startAt), eq(plannedEndAt), anyCollection()))
                .thenReturn(List.of(resApproved, resPending));

        confirmCreate(request);
        FacilityBlockage result = blockageService.createBlockage(request);

        assertNotNull(result);
        assertEquals(BlockageStatus.SCHEDULED, result.getStatus());
        assertEquals("AC Maintenance", result.getPublicReason());
        assertEquals(staff, result.getCreatedBy());

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

        when(facilityRepository.findById(10L)).thenReturn(Optional.of(facility));
        when(blockageTypeService.validateAndGetActiveBlockageType(100L)).thenReturn(repairType);
        assertThrows(BusinessRuleException.class, () -> blockageService.previewBlockageImpact(previewRequest(request)));
    }

    @Test
    void createBlockage_RepairWithReport_Success() {
        Report report = new Report(user, facility, "AC", "Broken", null, ReportStatus.NEW);
        ReflectionTestUtils.setField(report, "id", 50L);

        CreateBlockageRequest request = new CreateBlockageRequest(10L, 100L, 50L, startAt, plannedEndAt, "Repair AC", null);

        when(facilityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(facility));
        when(blockageTypeService.validateAndGetActiveBlockageType(100L)).thenReturn(repairType);
        when(reportRepository.findById(50L)).thenReturn(Optional.of(report));
        when(staffActorResolver.resolveId("staff@example.com")).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(staff));
        when(facilityBlockageRepository.save(any(FacilityBlockage.class))).thenAnswer(inv -> inv.getArgument(0));

        confirmCreate(request);
        FacilityBlockage result = blockageService.createBlockage(request);

        assertNotNull(result);
        assertEquals(report, result.getReport());
    }

    @Test
    void reconcileBlockageLifecycle_ActivatesScheduledAndCompletesPlanned() {
        when(facilityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(facility));
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
        when(staffActorResolver.resolveId("staff@example.com")).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(staff));
        when(facilityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(facility));
        ReflectionTestUtils.setField(newApproved, "id", 32L);
        when(reservationRepository.findOverlapping(eq(10L), eq(plannedEndAt), eq(newEndAt), anyCollection()))
                .thenReturn(List.of(newApproved));
        when(facilityBlockageRepository.save(any(FacilityBlockage.class))).thenAnswer(inv -> inv.getArgument(0));

        confirmUpdate(200L, request);
        FacilityBlockage result = blockageService.updateOrExtendBlockage(200L, request);

        assertEquals(newEndAt, result.getPlannedEndAt());
        assertEquals("Extended maintenance", result.getPublicReason());
        assertEquals(ReservationStatus.CANCELLED, newApproved.getStatus());
        assertEquals(staff, newApproved.getCancelledBy());
    }

    @Test
    void earlyCompleteBlockage_Success_RecordsActorAndActualEndAt() {
        FacilityBlockage blockage = new FacilityBlockage(facility, maintenanceType, null, startAt, plannedEndAt, BlockageStatus.ACTIVE, "Reason", null, staff);
        ReflectionTestUtils.setField(blockage, "id", 200L);

        EarlyCompletionRequest request = new EarlyCompletionRequest("Repairs finished early");

        when(facilityBlockageRepository.findById(200L)).thenReturn(Optional.of(blockage));
        when(staffActorResolver.resolveId("staff@example.com")).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(staff));
        when(facilityBlockageRepository.save(any(FacilityBlockage.class))).thenAnswer(inv -> inv.getArgument(0));

        when(facilityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(facility));
        FacilityBlockage result = blockageService.earlyCompleteBlockage(200L, request);

        assertEquals(BlockageStatus.COMPLETED, result.getStatus());
        assertNotNull(result.getActualEndAt());
        assertEquals(staff, result.getEndedBy());
        assertEquals("Repairs finished early", result.getEarlyCompletionReason());
        verifyNoInteractions(reservationRepository);
    }

    @Test
    void earlyCompleteBlockage_WithoutReason_ThrowsException() {
        FacilityBlockage blockage = new FacilityBlockage(facility, maintenanceType, null, startAt, plannedEndAt, BlockageStatus.ACTIVE, "Reason", null, staff);

        when(facilityBlockageRepository.findById(200L)).thenReturn(Optional.of(blockage));

        EarlyCompletionRequest request = new EarlyCompletionRequest("   ");

        assertThrows(BusinessRuleException.class, () -> blockageService.earlyCompleteBlockage(200L, request));
    }

    @Test
    void confirmationIsMandatoryEvenForZeroImpact() {
        var request = new CreateBlockageRequest(10L, 101L, null, startAt, plannedEndAt, "Reason", null);
        assertEquals("CONFIRMATION_REQUIRED", assertThrows(BusinessRuleException.class,
                () -> blockageService.createBlockage(request)).getCode());
        request.setConfirmed(true);
        assertThrows(BusinessRuleException.class, () -> blockageService.createBlockage(request));
        verifyNoInteractions(facilityBlockageRepository, reservationRepository);
    }

    private CreateBlockageRequest prepareCreation() {
        when(facilityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(facility));
        when(blockageTypeService.validateAndGetActiveBlockageType(101L)).thenReturn(maintenanceType);
        when(staffActorResolver.resolveId("staff@example.com")).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(staff));
        var request = new CreateBlockageRequest(10L, 101L, null, startAt, plannedEndAt, "Reason", null);
        confirmCreate(request);
        clearInvocations(facilityRepository, reservationRepository, entityManager);
        return request;
    }

    @Test
    void changedImpactFailsAfterFacilityLockAndDoesNotMutate() {
        var request = prepareCreation();
        var pending = new Reservation(user, facility, startAt, plannedEndAt, "Meeting", null, ReservationStatus.PENDING, null);
        ReflectionTestUtils.setField(pending, "id", 40L);
        when(reservationRepository.findOverlapping(eq(10L), eq(startAt), eq(plannedEndAt), anyCollection())).thenReturn(List.of(pending));
        assertEquals("IMPACT_CHANGED", assertThrows(BusinessRuleException.class,
                () -> blockageService.createBlockage(request)).getCode());
        var order = inOrder(facilityRepository, reservationRepository, entityManager);
        order.verify(facilityRepository).findByIdForUpdate(10L);
        order.verify(reservationRepository).findOverlapping(eq(10L), eq(startAt), eq(plannedEndAt), anyCollection());
        order.verify(entityManager).refresh(pending, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        verify(facilityBlockageRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
        assertEquals(ReservationStatus.PENDING, pending.getStatus());
    }

    @Test
    void identicalCountsWithDifferentReservationsStillRequireConfirmation() {
        var first = new Reservation(user, facility, startAt, plannedEndAt, "Meeting", null, ReservationStatus.APPROVED, null);
        var second = new Reservation(user, facility, startAt, plannedEndAt, "Meeting", null, ReservationStatus.APPROVED, null);
        ReflectionTestUtils.setField(first, "id", 40L);
        ReflectionTestUtils.setField(second, "id", 41L);
        when(reservationRepository.findOverlapping(eq(10L), eq(startAt), eq(plannedEndAt), anyCollection())).thenReturn(List.of(first));
        var request = prepareCreation();
        when(reservationRepository.findOverlapping(eq(10L), eq(startAt), eq(plannedEndAt), anyCollection())).thenReturn(List.of(second));
        assertEquals("IMPACT_CHANGED", assertThrows(BusinessRuleException.class,
                () -> blockageService.createBlockage(request)).getCode());
        verify(facilityBlockageRepository, never()).save(any());
    }

    @Test
    void changedInputsAndDifferentActorCannotReusePreview() {
        var request = prepareCreation();
        request.setPublicReason("Changed");
        assertEquals("PREVIEW_CHANGED", assertThrows(BusinessRuleException.class,
                () -> blockageService.createBlockage(request)).getCode());
        request.setPublicReason("Reason");
        authenticate("other@example.com", "ADMIN");
        when(staffActorResolver.resolveId("other@example.com")).thenReturn(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        assertEquals("PREVIEW_CHANGED", assertThrows(BusinessRuleException.class,
                () -> blockageService.createBlockage(request)).getCode());
        verify(facilityBlockageRepository, never()).save(any());
    }

    @Test
    void successfulPreviewCannotBeSubmittedTwice() {
        var request = prepareCreation();
        when(facilityBlockageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        blockageService.createBlockage(request);
        assertEquals("PREVIEW_EXPIRED", assertThrows(BusinessRuleException.class,
                () -> blockageService.createBlockage(request)).getCode());
        verify(facilityBlockageRepository, times(1)).save(any());
    }

    @Test
    void closingAnOpenEndedBlockageHasNoNewImpactOrRestoration() {
        var blockage = new FacilityBlockage(facility, maintenanceType, null, startAt, null, BlockageStatus.ACTIVE, "Reason", null, staff);
        ReflectionTestUtils.setField(blockage, "id", 200L);
        when(facilityBlockageRepository.findById(200L)).thenReturn(Optional.of(blockage));
        when(facilityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(facility));
        when(staffActorResolver.resolveId("staff@example.com")).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(staff));
        when(facilityBlockageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var request = new UpdateBlockageRequest(plannedEndAt, "Shortened", null);
        var preview = blockageService.previewUpdate(200L, request);
        assertEquals(0, preview.getApprovedCount());
        assertEquals(0, preview.getPendingCount());
        request.setConfirmed(true);
        request.setConfirmationToken(preview.getConfirmationToken());
        blockageService.updateOrExtendBlockage(200L, request);
        assertEquals(plannedEndAt, blockage.getPlannedEndAt());
        assertEquals(facility, blockage.getFacility());
        assertEquals(maintenanceType, blockage.getBlockageType());
        assertEquals(startAt, blockage.getStartAt());
        verifyNoInteractions(reservationRepository);
    }

    @Test
    void extendingToOpenEndedUsesOnlyOldEndOnward() {
        var blockage = new FacilityBlockage(facility, maintenanceType, null, startAt, plannedEndAt, BlockageStatus.ACTIVE, "Reason", null, staff);
        ReflectionTestUtils.setField(blockage, "id", 200L);
        when(facilityBlockageRepository.findById(200L)).thenReturn(Optional.of(blockage));
        when(facilityRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(facility));
        when(staffActorResolver.resolveId("staff@example.com")).thenReturn(2L);
        when(userRepository.findById(2L)).thenReturn(Optional.of(staff));
        when(facilityBlockageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var request = new UpdateBlockageRequest();
        request.setOpenEnded(true);
        confirmUpdate(200L, request);
        blockageService.updateOrExtendBlockage(200L, request);
        assertNull(blockage.getPlannedEndAt());
        verify(reservationRepository, times(2)).findOverlapping(eq(10L), eq(plannedEndAt), isNull(), anyCollection());
        verify(reservationRepository, never()).findOverlapping(eq(10L), eq(startAt), any(), anyCollection());
    }

    @Test
    void repairReportMustBelongToSameFacility() {
        var otherFacility = new Facility("OTHER", "Other", "Room", "Other", 5, null, AdministrativeStatus.ACTIVE);
        ReflectionTestUtils.setField(otherFacility, "id", 99L);
        var report = new Report(user, otherFacility, "AC", "Broken", null, ReportStatus.NEW);
        when(facilityRepository.findById(10L)).thenReturn(Optional.of(facility));
        when(blockageTypeService.validateAndGetActiveBlockageType(100L)).thenReturn(repairType);
        when(reportRepository.findById(50L)).thenReturn(Optional.of(report));
        var request = new CreateBlockageRequest(10L, 100L, 50L, startAt, plannedEndAt, "Repair", null);
        assertThrows(BusinessRuleException.class, () -> blockageService.previewBlockageImpact(previewRequest(request)));
        verify(facilityBlockageRepository, never()).save(any());
    }
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private BlockageImpactPreviewRequest previewRequest(CreateBlockageRequest request) {
        var preview = new BlockageImpactPreviewRequest(request.getFacilityId(), request.getStartAt(), request.getPlannedEndAt());
        preview.setBlockageTypeId(request.getBlockageTypeId());
        preview.setReportId(request.getReportId());
        preview.setPublicReason(request.getPublicReason());
        preview.setInternalNote(request.getInternalNote());
        return preview;
    }

    private void confirmCreate(CreateBlockageRequest request) {
        when(facilityRepository.findById(10L)).thenReturn(Optional.of(facility));
        request.setConfirmationToken(blockageService.previewBlockageImpact(previewRequest(request)).getConfirmationToken());
        request.setConfirmed(true);
    }

    private void confirmUpdate(Long id, UpdateBlockageRequest request) {
        request.setConfirmationToken(blockageService.previewUpdate(id, request).getConfirmationToken());
        request.setConfirmed(true);
    }
    private void authenticate(String email, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "unused",
                        AuthorityUtils.createAuthorityList("ROLE_" + role)));
    }
}
