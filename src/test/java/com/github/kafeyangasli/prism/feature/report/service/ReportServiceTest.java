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

    @InjectMocks
    private ReportService reportService;

    private User user1;
    private User user2;
    private User staff;
    private Facility facility;

    @BeforeEach
    void setUp() {
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

        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(facilityRepository.findById(10L)).thenReturn(Optional.of(facility));
        when(reportRepository.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MockMultipartFile photo = new MockMultipartFile("photo", "test.jpg", "image/jpeg", "dummy image content".getBytes());

        Report result = reportService.createReport(request, photo, 1L);

        assertNotNull(result);
        assertEquals(user1, result.getUser());
        assertEquals(facility, result.getFacility());
        assertEquals("AC Broken", result.getCategory());
        assertEquals("AC is blowing warm air", result.getDescription());
        assertEquals(ReportStatus.NEW, result.getStatus());
        assertNotNull(result.getPhotoPath());
        assertTrue(result.getPhotoPath().endsWith("test.jpg"));
        assertFalse(result.getPhotoPath().contains(".."));
    }

    @Test
    void createReport_UserNotFound_ThrowsException() {
        CreateReportRequest request = new CreateReportRequest();
        request.setFacilityId(10L);

        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reportService.createReport(request, null, 99L));
    }

    @Test
    void createReport_PathTraversalPhoto_ThrowsException() {
        CreateReportRequest request = new CreateReportRequest();
        request.setFacilityId(10L);

        when(userRepository.findById(1L)).thenReturn(Optional.of(user1));
        when(facilityRepository.findById(10L)).thenReturn(Optional.of(facility));

        MockMultipartFile photo = new MockMultipartFile("photo", "../secret.txt", "text/plain", "data".getBytes());

        assertThrows(BusinessRuleException.class, () -> reportService.createReport(request, photo, 1L));
    }

    @Test
    void getReportsByUser_Success() {
        Report report = new Report(user1, facility, "Category", "Desc", null, ReportStatus.NEW);
        when(reportRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(report));

        List<Report> reports = reportService.getReportsByUser(1L);

        assertEquals(1, reports.size());
        assertEquals(user1, reports.get(0).getUser());
    }

    @Test
    void getReportDetail_Owner_Success() {
        Report report = new Report(user1, facility, "Category", "Desc", null, ReportStatus.NEW);
        ReflectionTestUtils.setField(report, "id", 100L);

        when(reportRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(report));

        Report result = reportService.getReportDetail(100L, 1L);

        assertNotNull(result);
        assertEquals(100L, result.getId());
    }

    @Test
    void getReportDetail_NonOwner_ThrowsException() {
        when(reportRepository.findByIdAndUserId(100L, 2L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reportService.getReportDetail(100L, 2L));
    }

    @Test
    void updateStatus_NewToInProgress_Success() {
        Report report = new Report(user1, facility, "Category", "Desc", null, ReportStatus.NEW);
        ReflectionTestUtils.setField(report, "id", 100L);

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(userRepository.findById(3L)).thenReturn(Optional.of(staff));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        Report result = reportService.updateStatus(100L, 3L, ReportStatus.IN_PROGRESS, null);

        assertEquals(ReportStatus.IN_PROGRESS, result.getStatus());
        assertEquals(staff, result.getHandledBy());
        assertNotNull(result.getHandledAt());
    }

    @Test
    void updateStatus_NewToRejected_Success() {
        Report report = new Report(user1, facility, "Category", "Desc", null, ReportStatus.NEW);
        ReflectionTestUtils.setField(report, "id", 100L);

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(userRepository.findById(3L)).thenReturn(Optional.of(staff));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        Report result = reportService.updateStatus(100L, 3L, ReportStatus.REJECTED, "Not an issue");

        assertEquals(ReportStatus.REJECTED, result.getStatus());
        assertEquals("Not an issue", result.getResolutionNote());
        assertEquals(staff, result.getHandledBy());
    }

    @Test
    void updateStatus_InProgressToResolved_Success() {
        Report report = new Report(user1, facility, "Category", "Desc", null, ReportStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(report, "id", 100L);
        report.setHandledBy(staff);

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));
        when(userRepository.findById(3L)).thenReturn(Optional.of(staff));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        Report result = reportService.updateStatus(100L, 3L, ReportStatus.RESOLVED, "Fixed AC filter");

        assertEquals(ReportStatus.RESOLVED, result.getStatus());
        assertEquals("Fixed AC filter", result.getResolutionNote());
        assertNotNull(result.getResolvedAt());
    }

    @Test
    void updateStatus_InProgressToResolved_WithoutResolutionNote_ThrowsException() {
        Report report = new Report(user1, facility, "Category", "Desc", null, ReportStatus.IN_PROGRESS);
        ReflectionTestUtils.setField(report, "id", 100L);

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));

        assertThrows(BusinessRuleException.class, () -> reportService.updateStatus(100L, 3L, ReportStatus.RESOLVED, "   "));
    }

    @Test
    void updateStatus_InvalidTransition_NewToResolved_ThrowsException() {
        Report report = new Report(user1, facility, "Category", "Desc", null, ReportStatus.NEW);
        ReflectionTestUtils.setField(report, "id", 100L);

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));

        assertThrows(BusinessRuleException.class, () -> reportService.updateStatus(100L, 3L, ReportStatus.RESOLVED, "Done"));
    }

    @Test
    void updateStatus_FromTerminalState_ThrowsException() {
        Report report = new Report(user1, facility, "Category", "Desc", null, ReportStatus.RESOLVED);
        ReflectionTestUtils.setField(report, "id", 100L);

        when(reportRepository.findById(100L)).thenReturn(Optional.of(report));

        assertThrows(BusinessRuleException.class, () -> reportService.updateStatus(100L, 3L, ReportStatus.IN_PROGRESS, null));
    }
}
