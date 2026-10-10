package com.github.kafeyangasli.prism.feature.report.service;

import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.report.dto.CreateReportRequest;
import com.github.kafeyangasli.prism.feature.report.model.Report;
import com.github.kafeyangasli.prism.feature.report.model.ReportStatus;
import com.github.kafeyangasli.prism.feature.report.repository.ReportRepository;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private FacilityRepository facilityRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StaffActorResolver staffActorResolver;

    @Mock
    private ReportPhotoStorage photoStorage;

    @Mock
    private ReportReservationService reportReservationService;

    @InjectMocks
    private ReportService reportService;

    private User user1;
    private User user2;
    private User staff;
    private Facility facility;

    @BeforeEach
    void setUp() {
        authenticate("user1@example.com", "PENGGUNA");
        user1 = new User("User One", "user1@example.com", "hash", Role.PENGGUNA, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(user1, "id", 1L);

        user2 = new User("User Two", "user2@example.com", "hash", Role.PENGGUNA, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(user2, "id", 2L);

        staff = new User("Staff Member", "staff@example.com", "hash", Role.PETUGAS, AccountStatus.ACTIVE);
        ReflectionTestUtils.setField(staff, "id", 3L);

        facility = new Facility("FAC01", "Gedung A", "Ruang Rapat", "Lantai 2", 50, "Desc", AdministrativeStatus.ACTIVE);
        ReflectionTestUtils.setField(facility, "id", 10L);
    }

    @Test
    void createReport_Success() {
        CreateReportRequest request = new CreateReportRequest();
        request.setFacilityId(10L);
        request.setCategory("AC Broken");
        request.setDescription("AC is blowing warm air");

        when(userRepository.findByEmailIgnoreCase("user1@example.com")).thenReturn(Optional.of(user1));
        when(facilityRepository.findById(10L)).thenReturn(Optional.of(facility));
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile photo = new MockMultipartFile("photo", "test.jpg", "image/jpeg", "dummy image content".getBytes());

        when(photoStorage.store(photo)).thenReturn("generated.jpg");
        Report result = reportService.createReport(request, photo);

        assertNotNull(result);
        assertEquals(user1, result.getUser());
        assertEquals(facility, result.getFacility());
        assertEquals("AC Broken", result.getCategory());
        assertEquals("AC is blowing warm air", result.getDescription());
        assertEquals(ReportStatus.NEW, result.getStatus());
        assertNotNull(result.getPhotoPath());
        assertEquals("generated.jpg", result.getPhotoPath());
        assertFalse(result.getPhotoPath().contains(".."));
    }

    @Test
    void createReport_UserNotFound_ThrowsException() {
        CreateReportRequest request = new CreateReportRequest();
        request.setFacilityId(10L);
        request.setCategory("AC");
        request.setDescription("Broken AC");

        when(userRepository.findByEmailIgnoreCase("user1@example.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reportService.createReport(request, null));
    }

    @Test
    void createReport_InvalidPhoto_ThrowsException() {
        CreateReportRequest request = new CreateReportRequest();
        request.setFacilityId(10L);
        request.setCategory("AC");
        request.setDescription("Broken AC");

        when(userRepository.findByEmailIgnoreCase("user1@example.com")).thenReturn(Optional.of(user1));
        when(facilityRepository.findById(10L)).thenReturn(Optional.of(facility));

        MockMultipartFile photo = new MockMultipartFile("photo", "../secret.txt", "text/plain", "data".getBytes());

        when(photoStorage.store(photo)).thenThrow(new BusinessRuleException("Invalid photo"));
        assertThrows(BusinessRuleException.class, () -> reportService.createReport(request, photo));
    }

    @Test
    void getReportsByUser_Success() {
        authenticate("user1@example.com", "PENGGUNA");
        when(userRepository.findByEmailIgnoreCase("user1@example.com")).thenReturn(Optional.of(user1));
        Report report = new Report(user1, facility, "Category", "Desc", null, ReportStatus.NEW);
        when(reportRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(report));

        List<Report> reports = reportService.getMyReports();

        assertEquals(1, reports.size());
        assertEquals(user1, reports.get(0).getUser());
    }

    @Test
    void getReportDetail_Owner_Success() {
        authenticate("user1@example.com", "PENGGUNA");
        when(userRepository.findByEmailIgnoreCase("user1@example.com")).thenReturn(Optional.of(user1));
        Report report = new Report(user1, facility, "Category", "Desc", null, ReportStatus.NEW);
        ReflectionTestUtils.setField(report, "id", 100L);

        when(reportRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(report));

        Report result = reportService.getReportDetail(100L);

        assertNotNull(result);
        assertEquals(100L, result.getId());
    }

    @Test
    void getReportDetail_NonOwner_ThrowsException() {
        authenticate("user2@example.com", "PENGGUNA");
        when(userRepository.findByEmailIgnoreCase("user2@example.com")).thenReturn(Optional.of(user2));
        when(reportRepository.findByIdAndUserId(100L, 2L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reportService.getReportDetail(100L));
    }

    @Test
    void updateStatus_NewToInProgress_Success() {
        Report report = new Report(user1, facility, "Category", "Desc", null, ReportStatus.NEW);
        ReflectionTestUtils.setField(report, "id", 100L);

        when(reportRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(report));
        authenticate("staff@example.com", "PETUGAS");
        when(staffActorResolver.resolveId("staff@example.com")).thenReturn(3L);
        when(userRepository.findById(3L)).thenReturn(Optional.of(staff));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        Report result = reportService.updateStatus(100L, ReportStatus.IN_PROGRESS, null);

        assertEquals(ReportStatus.IN_PROGRESS, result.getStatus());
        assertEquals(staff, result.getHandledBy());
        assertNotNull(result.getHandledAt());
    }

    @Test
    void updateStatus_NewToRejected_Success() {
        Report report = new Report(user1, facility, "Category", "Desc", null, ReportStatus.NEW);
        ReflectionTestUtils.setField(report, "id", 100L);

        when(reportRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(report));
        authenticate("staff@example.com", "PETUGAS");
        when(staffActorResolver.resolveId("staff@example.com")).thenReturn(3L);
        when(userRepository.findById(3L)).thenReturn(Optional.of(staff));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        Report result = reportService.updateStatus(100L, ReportStatus.REJECTED, "Not an issue");

        assertEquals(ReportStatus.REJECTED, result.getStatus());
        assertEquals("Not an issue", result.getResolutionNote());
        assertEquals(staff, result.getHandledBy());
    }

    @Test
    void updateStatus_InProgressToResolved_Success() {
        Report report = new Report(user1, facility, "Category", "Desc", null, ReportStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(report, "id", 100L);
        report.setHandledBy(staff);

        when(reportRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(report));
        authenticate("staff@example.com", "PETUGAS");
        when(staffActorResolver.resolveId("staff@example.com")).thenReturn(3L);
        when(userRepository.findById(3L)).thenReturn(Optional.of(staff));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        Report result = reportService.updateStatus(100L, ReportStatus.RESOLVED, "Fixed AC filter");

        assertEquals(ReportStatus.RESOLVED, result.getStatus());
        assertEquals("Fixed AC filter", result.getResolutionNote());
        assertNotNull(result.getResolvedAt());
    }

    @Test
    void updateStatus_InProgressToResolved_WithoutResolutionNote_ThrowsException() {
        Report report = new Report(user1, facility, "Category", "Desc", null, ReportStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(report, "id", 100L);

        when(reportRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(report));

        assertThrows(BusinessRuleException.class, () -> reportService.updateStatus(100L, ReportStatus.RESOLVED, "   "));
    }

    @Test
    void updateStatus_InvalidTransition_NewToResolved_ThrowsException() {
        Report report = new Report(user1, facility, "Category", "Desc", null, ReportStatus.NEW);
        ReflectionTestUtils.setField(report, "id", 100L);

        when(reportRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(report));

        assertThrows(BusinessRuleException.class, () -> reportService.updateStatus(100L, ReportStatus.RESOLVED, "Done"));
    }

    @Test
    void updateStatus_FromTerminalState_ThrowsException() {
        Report report = new Report(user1, facility, "Category", "Desc", null, ReportStatus.RESOLVED);
        ReflectionTestUtils.setField(report, "id", 100L);

        when(reportRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(report));

        assertThrows(BusinessRuleException.class, () -> reportService.updateStatus(100L, ReportStatus.IN_PROGRESS, null));
    }

    @Test
    void allStatusPairsEnforceExactlyTheFourAllowedTransitions() {
        for (ReportStatus current : ReportStatus.values()) {
            for (ReportStatus next : ReportStatus.values()) {
                boolean allowed = current == ReportStatus.NEW && (next == ReportStatus.IN_PROGRESS || next == ReportStatus.REJECTED)
                        || current == ReportStatus.IN_PROGRESS && (next == ReportStatus.RESOLVED || next == ReportStatus.REJECTED);
                if (allowed) assertDoesNotThrow(() -> reportService.validateTransition(current, next));
                else assertThrows(BusinessRuleException.class, () -> reportService.validateTransition(current, next));
            }
        }
        assertThrows(BusinessRuleException.class, () -> reportService.validateTransition(ReportStatus.NEW, null));
    }

    @Test
    void finalActionAuditsCurrentActorEvenWhenAnotherStaffStartedHandling() {
        Report report = new Report(user1, facility, "AC", "Broken", null, ReportStatus.IN_PROGRESS);
        report.setHandledBy(user2);
        var oldTime = java.time.LocalDateTime.now().minusDays(1);
        report.setHandledAt(oldTime);
        when(reportRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(report));
        authenticate("staff@example.com", "PETUGAS");
        when(staffActorResolver.resolveId("staff@example.com")).thenReturn(3L);
        when(userRepository.findById(3L)).thenReturn(Optional.of(staff));
        when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var result = reportService.updateStatus(100L, ReportStatus.REJECTED, "Duplicate");
        assertEquals(staff, result.getHandledBy());
        assertTrue(result.getHandledAt().isAfter(oldTime));
        assertEquals(result.getHandledAt(), result.getUpdatedAt());
        assertNull(result.getResolvedAt());
    }

    @Test
    void saveFailureRemovesUploadedPhoto() {
        var request = new CreateReportRequest();
        request.setFacilityId(10L);
        request.setCategory("AC");
        request.setDescription("Broken");
        when(userRepository.findByEmailIgnoreCase("user1@example.com")).thenReturn(Optional.of(user1));
        when(facilityRepository.findById(10L)).thenReturn(Optional.of(facility));
        when(photoStorage.store(any())).thenReturn("generated.jpg");
        when(reportRepository.save(any())).thenThrow(new IllegalStateException("DB unavailable"));
        assertThrows(IllegalStateException.class, () -> reportService.createReport(request, new MockMultipartFile("photo", new byte[]{1})));
        verify(photoStorage).delete("generated.jpg");
    }

    @Test
    void reservationReportUsesAuthenticatedOwnerAndReservationFacility() {
        var request = new CreateReportRequest();
        request.setReservationId(25L);
        request.setFacilityId(999L);
        request.setCategory("AC");
        request.setDescription("Broken");
        var end = java.time.LocalDateTime.now().minusHours(1);
        var reservation = new com.github.kafeyangasli.prism.feature.reservation.model.Reservation(user1, facility,
                end.minusHours(1), end, "Meeting", null,
                com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus.COMPLETED, null);
        when(userRepository.findByEmailIgnoreCase("user1@example.com")).thenReturn(Optional.of(user1));
        when(reportReservationService.eligibleReservation(25L, 1L)).thenReturn(reservation);
        when(photoStorage.store(any())).thenReturn("generated.jpg");
        when(reportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var result = reportService.createReport(request, new MockMultipartFile("photo", new byte[]{1}));
        assertEquals(user1, result.getUser());
        assertEquals(facility, result.getFacility());
        verifyNoInteractions(facilityRepository);
    }

    @Test
    void expiredReservationIsRejectedBeforePhotoStorage() {
        var request = new CreateReportRequest();
        request.setReservationId(25L);
        request.setCategory("AC");
        request.setDescription("Broken");
        when(userRepository.findByEmailIgnoreCase("user1@example.com")).thenReturn(Optional.of(user1));
        when(reportReservationService.eligibleReservation(25L, 1L)).thenThrow(new BusinessRuleException("Expired"));
        assertThrows(BusinessRuleException.class, () -> reportService.createReport(request, new MockMultipartFile("photo", new byte[]{1})));
        verifyNoInteractions(photoStorage, reportRepository);
    }
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String email, String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(email, "unused",
                        AuthorityUtils.createAuthorityList("ROLE_" + role)));
    }
}
