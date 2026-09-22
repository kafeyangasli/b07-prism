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

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final FacilityRepository facilityRepository;
    private final UserRepository userRepository;

    public Report createReport(CreateReportRequest request, Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Facility facility = facilityRepository.findById(request.getFacilityId())
                .orElseThrow(() -> new RuntimeException("Facility not found"));

        Report report = new Report(
                user,
                facility,
                request.getCategory(),
                request.getDescription(),
                request.getPhotoPath(),
                ReportStatus.NEW
        );

        return reportRepository.save(report);
    }
}