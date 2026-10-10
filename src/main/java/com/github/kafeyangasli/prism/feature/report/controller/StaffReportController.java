package com.github.kafeyangasli.prism.feature.report.controller;

import com.github.kafeyangasli.prism.feature.report.model.Report;
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

    @PatchMapping("/{id}/status")
    public Report updateStatusByStaff(@PathVariable Long id,
                                     @RequestParam ReportStatus status,
                                     @RequestParam(required = false) String resolutionNote) {
        return reportService.updateStatus(id, status, resolutionNote);
    }
}
