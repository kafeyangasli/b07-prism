package com.github.kafeyangasli.prism.feature.report.controller;

import com.github.kafeyangasli.prism.feature.report.dto.CreateReportRequest;
import com.github.kafeyangasli.prism.feature.report.dto.ReportDto;
import com.github.kafeyangasli.prism.feature.report.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService reportService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReportDto createReport(@Valid @RequestPart("data") CreateReportRequest request,
                                 @RequestPart("photo") MultipartFile photo) {
        return ReportDto.from(reportService.createReport(request, photo));
    }

    @GetMapping
    public List<ReportDto> getMyReports() {
        return reportService.getMyReports().stream().map(ReportDto::from).toList();
    }

    @GetMapping("/{id}")
    public ReportDto getReportDetail(@PathVariable Long id) {
        return ReportDto.from(reportService.getReportDetail(id));
    }
}
