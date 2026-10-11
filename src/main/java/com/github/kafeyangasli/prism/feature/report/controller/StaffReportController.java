package com.github.kafeyangasli.prism.feature.report.controller;

import com.github.kafeyangasli.prism.feature.report.dto.ReportDto;
import com.github.kafeyangasli.prism.feature.report.model.ReportStatus;
import com.github.kafeyangasli.prism.feature.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/staff/reports")
@PreAuthorize("hasAnyRole('PETUGAS', 'ADMIN')")
@RequiredArgsConstructor
public class StaffReportController {
    private final ReportService reportService;

    @GetMapping("/{id}")
    public ReportDto detail(@PathVariable Long id) {
        return ReportDto.from(reportService.getStaffReportDetail(id));
    }

    @PatchMapping("/{id}/status")
    public ReportDto updateStatusByStaff(@PathVariable Long id, @RequestParam ReportStatus status,
                                        @RequestParam(required = false) String resolutionNote) {
        return ReportDto.from(reportService.updateStatus(id, status, resolutionNote));
    }
}
