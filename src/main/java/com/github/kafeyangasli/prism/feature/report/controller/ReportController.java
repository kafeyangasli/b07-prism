package com.github.kafeyangasli.prism.feature.report.controller;

import com.github.kafeyangasli.prism.feature.report.dto.CreateReportRequest;
import com.github.kafeyangasli.prism.feature.report.model.Report;
import com.github.kafeyangasli.prism.feature.report.service.ReportService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // STEP 4 + 5
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Report createReport(
        @RequestPart("data") CreateReportRequest request,
        @RequestPart(value = "photo", required = false) MultipartFile photo
    ) {

        Long mockUserId = 1L; // nanti diganti auth

        return reportService.createReport(request, photo, mockUserId);
    }

    // STEP 6
    @GetMapping
    public List<Report> getMyReports() {

        Long mockUserId = 1L;

        return reportService.getReportsByUser(mockUserId);
    }

    @GetMapping("/{id}")
    public Report getReportDetail(@PathVariable Long id) {

        Long mockUserId = 1L; // nanti diganti auth

        return reportService.getReportDetail(id, mockUserId);
    }
}