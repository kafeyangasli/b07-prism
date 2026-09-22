package com.github.kafeyangasli.prism.feature.report.controller;

import com.github.kafeyangasli.prism.feature.report.dto.CreateReportRequest;
import com.github.kafeyangasli.prism.feature.report.model.Report;
import com.github.kafeyangasli.prism.feature.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public Report createReport(
        @RequestPart("data") CreateReportRequest request,
        @RequestPart(value = "photo", required = false) MultipartFile photo
) {

    Long mockUserId = 1L; // nanti diganti auth

    return reportService.createReport(request, photo, mockUserId);
}
}