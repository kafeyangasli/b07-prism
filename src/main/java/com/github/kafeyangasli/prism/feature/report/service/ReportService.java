package com.github.kafeyangasli.prism.feature.report.service;

import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.report.dto.CreateReportRequest;
import com.github.kafeyangasli.prism.feature.report.model.Report;
import com.github.kafeyangasli.prism.feature.report.model.ReportStatus;
import com.github.kafeyangasli.prism.feature.report.repository.ReportRepository;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;

    public Report createReport(CreateReportRequest request, MultipartFile photo, Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Facility facility = facilityRepository.findById(request.getFacilityId())
                .orElseThrow(() -> new RuntimeException("Facility not found"));

        // 🔹 HANDLE FILE
        String photoPath = storeFile(photo);

        Report report = new Report(
                user,
                facility,
                request.getCategory(),
                request.getDescription(),
                photoPath, // ✔ dari file, bukan request
                ReportStatus.NEW
        );

        return reportRepository.save(report);
    }

    // 🔒 File storage (core Step 5)
    private String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) return null;

        try {
            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();

            Path uploadDir = Paths.get("uploads");

            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }

            Path filePath = uploadDir.resolve(filename);

            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            return filename; // ⚠️ hanya nama file (AMAN)

        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }
}