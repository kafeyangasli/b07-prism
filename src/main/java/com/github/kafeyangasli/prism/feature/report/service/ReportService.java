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
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Facility facility = facilityRepository.findById(request.getFacilityId())
                .orElseThrow(() -> new ResourceNotFoundException("Facility not found with id: " + request.getFacilityId()));

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
                throw new BusinessRuleException("Invalid filename containing path traversal characters");
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
            throw new RuntimeException("Failed to store file", e);
        }
    }

    public List<Report> getReportsByUser(Long userId) {
        return reportRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public Report getReportDetail(Long reportId, Long userId) {
        return reportRepository.findByIdAndUserId(reportId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found or access denied"));
    }

    public void validateTransition(ReportStatus current, ReportStatus next) {
        switch (current) {
            case NEW:
                if (next != ReportStatus.IN_PROGRESS && next != ReportStatus.REJECTED) {
                    throw new BusinessRuleException("Invalid status transition from NEW to " + next);
                }
                break;

            case IN_PROGRESS:
                if (next != ReportStatus.RESOLVED && next != ReportStatus.REJECTED) {
                    throw new BusinessRuleException("Invalid status transition from IN_PROGRESS to " + next);
                }
                break;

            case RESOLVED:
            case REJECTED:
                throw new BusinessRuleException("Cannot change status from terminal state: " + current);

            default:
                throw new BusinessRuleException("Unknown report status: " + current);
        }
    }

    public Report updateStatus(Long reportId, Long staffId, ReportStatus newStatus, String resolutionNote) {

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found with id: " + reportId));

        validateTransition(report.getStatus(), newStatus);

        if (newStatus == ReportStatus.RESOLVED && (resolutionNote == null || resolutionNote.trim().isEmpty())) {
            throw new BusinessRuleException("Resolution note is required when resolving a report");
        }

        User staff = userRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff user not found with id: " + staffId));

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