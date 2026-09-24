package com.github.kafeyangasli.prism.feature.report.service;

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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;

    public Report createReport(CreateReportRequest request, MultipartFile photo, Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pengguna dengan ID " + userId + " tidak ditemukan."));

        Facility facility = facilityRepository.findById(request.getFacilityId())
                .orElseThrow(() -> new ResourceNotFoundException("Fasilitas dengan ID " + request.getFacilityId() + " tidak ditemukan."));

        String photoPath = storeFile(photo);

        Report report = new Report(
                user,
                facility,
                request.getCategory(),
                request.getDescription(),
                photoPath,
                ReportStatus.NEW
        );

        return reportRepository.save(report);
    }

    private String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;

        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename != null && (originalFilename.contains("..") || originalFilename.contains("/") || originalFilename.contains("\\"))) {
                throw new BusinessRuleException("Nama berkas tidak valid.");
            }
            String filename = UUID.randomUUID() + "_" + (originalFilename != null ? Paths.get(originalFilename).getFileName().toString() : "file");

            Path uploadDir = Paths.get("uploads");

            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            Path filePath = uploadDir.resolve(filename);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            return filename;

        } catch (IOException e) {
            throw new RuntimeException("Berkas gagal disimpan.", e);
        }
    }

    public List<Report> getReportsByUser(Long userId) {
        return reportRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Report getReportDetail(Long reportId, Long userId) {
        return reportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Laporan tidak ditemukan atau Anda tidak memiliki akses."));
    }

    public void validateTransition(ReportStatus current, ReportStatus next) {
        switch (current) {
            case NEW:
                if (next != ReportStatus.IN_PROGRESS && next != ReportStatus.REJECTED) {
                    throw new BusinessRuleException("Status laporan Baru hanya dapat diubah menjadi Sedang Ditangani atau Ditolak.");
                }
                break;

            case IN_PROGRESS:
                if (next != ReportStatus.RESOLVED && next != ReportStatus.REJECTED) {
                    throw new BusinessRuleException("Status laporan Sedang Ditangani hanya dapat diubah menjadi Terselesaikan atau Ditolak.");
                }
                break;

            case RESOLVED:
            case REJECTED:
                throw new BusinessRuleException("Status laporan yang telah selesai tidak dapat diubah.");

            default:
                throw new BusinessRuleException("Status laporan tidak dikenali.");
        }
    }

    public Report updateStatus(Long reportId, Long staffId, ReportStatus newStatus, String resolutionNote) {

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Laporan dengan ID " + reportId + " tidak ditemukan."));

        validateTransition(report.getStatus(), newStatus);

        if (newStatus == ReportStatus.RESOLVED && (resolutionNote == null || resolutionNote.trim().isEmpty())) {
            throw new BusinessRuleException("Catatan penyelesaian wajib diisi saat menyelesaikan laporan.");
        }

        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Petugas dengan ID " + staffId + " tidak ditemukan."));

        report.setStatus(newStatus);

        if (newStatus == ReportStatus.IN_PROGRESS) {
            report.setHandledAt(LocalDateTime.now());
            report.setHandledBy(staff);
        } else if (newStatus == ReportStatus.RESOLVED) {
            report.setResolutionNote(resolutionNote);
            report.setResolvedAt(LocalDateTime.now());
            if (report.getHandledBy() == null) {
                report.setHandledBy(staff);
                report.setHandledAt(LocalDateTime.now());
            }
        } else if (newStatus == ReportStatus.REJECTED) {
            if (resolutionNote != null && !resolutionNote.trim().isEmpty()) {
                report.setResolutionNote(resolutionNote);
            }
            if (report.getHandledBy() == null) {
                report.setHandledBy(staff);
                report.setHandledAt(LocalDateTime.now());
            }
        }

        return reportRepository.save(report);
    }
}
