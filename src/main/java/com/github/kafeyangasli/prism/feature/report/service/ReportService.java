package com.github.kafeyangasli.prism.feature.report.service;

import com.github.kafeyangasli.prism.feature.administration.service.StaffActorResolver;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.report.dto.CreateReportRequest;
import com.github.kafeyangasli.prism.feature.report.model.Report;
import com.github.kafeyangasli.prism.feature.report.model.ReportStatus;
import com.github.kafeyangasli.prism.feature.report.repository.ReportRepository;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.util.List;
import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final ReportRepository reportRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;
    private final StaffActorResolver staffActorResolver;
    private final ReportPhotoStorage photoStorage;
    private final ReportReservationService reportReservationService;

    @PreAuthorize("isAuthenticated()")
    public Report createReport(CreateReportRequest request, MultipartFile photo) {
        User user = currentUser();
        if (request == null
                || request.getCategory() == null || request.getCategory().isBlank() || request.getCategory().length() > 80
                || request.getDescription() == null || request.getDescription().isBlank() || request.getDescription().length() > 2000) {
            throw new BusinessRuleException("Fasilitas, kategori (maksimal 80 karakter), dan deskripsi (maksimal 2000 karakter) wajib diisi.");
        }
        Facility facility;
        if (request.getReservationId() != null) {
            facility = reportReservationService.eligibleReservation(request.getReservationId(), user.getId()).getFacility();
        } else {
            if (request.getFacilityId() == null || request.getFacilityId() <= 0) {
                throw new BusinessRuleException("Fasilitas wajib dipilih.");
            }
            facility = facilityRepository.findById(request.getFacilityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Fasilitas tidak ditemukan."));
        }
        String photoPath = photoStorage.store(photo);
        Report report = new Report(user, facility, request.getCategory().trim(), request.getDescription().trim(), photoPath, ReportStatus.NEW);
        try {
            return reportRepository.save(report);
        } catch (RuntimeException e) {
            try { photoStorage.delete(photoPath); } catch (RuntimeException cleanup) { e.addSuppressed(cleanup); }
            throw e;
        }
    }

    @PreAuthorize("isAuthenticated()")
    public List<Report> getMyReports() {
        return reportRepository.findByUserIdOrderByCreatedAtDesc(currentUser().getId());
    }

    @PreAuthorize("isAuthenticated()")
    public Report getReportDetail(Long reportId) {
        return reportRepository.findByIdAndUserId(reportId, currentUser().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Laporan tidak ditemukan atau Anda tidak memiliki akses."));
    }

    @PreAuthorize("hasAnyRole('PETUGAS', 'ADMIN')")
    public Report getStaffReportDetail(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Laporan tidak ditemukan."));
    }

    @PreAuthorize("isAuthenticated()")
    public byte[] getPhoto(Long reportId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        authenticatedEmail();
        boolean staff = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_PETUGAS") || a.getAuthority().equals("ROLE_ADMIN"));
        Report report = staff ? getStaffReportDetail(reportId) : getReportDetail(reportId);
        return photoStorage.load(report.getPhotoPath());
    }

    @PreAuthorize("isAuthenticated()")
    public List<Facility> getReportFacilities() {
        return facilityRepository.findAll(org.springframework.data.domain.Sort.by("name"));
    }

    @PreAuthorize("isAuthenticated()")
    public Reservation getReservationForReport(Long reservationId) {
        return reportReservationService.eligibleReservation(reservationId, currentUser().getId());
    }

    public void validateTransition(ReportStatus current, ReportStatus next) {
        if (current == null || next == null) throw new BusinessRuleException("Status laporan wajib diisi.");
        boolean allowed = switch (current) {
            case NEW -> next == ReportStatus.IN_PROGRESS || next == ReportStatus.REJECTED;
            case IN_PROGRESS -> next == ReportStatus.RESOLVED || next == ReportStatus.REJECTED;
            case RESOLVED, REJECTED -> false;
        };
        if (!allowed) throw new BusinessRuleException("Perubahan status laporan tidak diizinkan.");
    }

    @Transactional
    @PreAuthorize("hasAnyRole('PETUGAS', 'ADMIN')")
    public Report updateStatus(Long reportId, ReportStatus newStatus, String resolutionNote) {
        Report report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Laporan tidak ditemukan."));
        validateTransition(report.getStatus(), newStatus);
        if (newStatus == ReportStatus.RESOLVED && (resolutionNote == null || resolutionNote.isBlank())) {
            throw new BusinessRuleException("Catatan penyelesaian wajib diisi saat menyelesaikan laporan.");
        }
        if (resolutionNote != null && resolutionNote.length() > 2000) {
            throw new BusinessRuleException("Catatan maksimal 2000 karakter.");
        }
        long staffId = staffActorResolver.resolveId(authenticatedEmail());
        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Akun staf tidak ditemukan."));
        LocalDateTime now = LocalDateTime.now();
        report.setStatus(newStatus);
        // Existing schema: these fields describe the latest staff action, including rejection.
        report.setHandledBy(staff);
        report.setHandledAt(now);
        report.setUpdatedAt(now);
        if (resolutionNote != null && !resolutionNote.isBlank()) report.setResolutionNote(resolutionNote.trim());
        if (newStatus == ReportStatus.RESOLVED) report.setResolvedAt(now);
        return reportRepository.save(report);
    }

    private User currentUser() {
        return userRepository.findByEmailIgnoreCase(authenticatedEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Akun pengguna tidak ditemukan."));
    }

    private String authenticatedEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Autentikasi diperlukan.");
        }
        return authentication.getName();
    }
}
