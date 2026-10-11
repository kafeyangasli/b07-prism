package com.github.kafeyangasli.prism.feature.administration.controller;

import com.github.kafeyangasli.prism.feature.administration.dto.RecapFilter;
import com.github.kafeyangasli.prism.feature.administration.dto.RecapResult;
import com.github.kafeyangasli.prism.feature.administration.service.AdministrationReportService;
import com.github.kafeyangasli.prism.feature.administration.service.ReportExportService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.util.MultiValueMap;
import com.github.kafeyangasli.prism.feature.facility.service.FacilityFilters;

@Controller
@RequestMapping("/admin/recap")
@PreAuthorize("hasRole('ADMIN')")
public class AdministrationReportController {

    private final AdministrationReportService reportService;
    private final ReportExportService exportService;
    private final Clock clock;

    public AdministrationReportController(
            AdministrationReportService reportService,
            ReportExportService exportService,
            Clock clock
    ) {
        this.reportService = reportService;
        this.exportService = exportService;
        this.clock = clock;
    }

    @GetMapping
    public String recap(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                        @RequestParam(required = false) Long facilityId,
                        @RequestParam MultiValueMap<String, String> parameters, Model model) {
        RecapFilter filter = selections(startDate, endDate, facilityId, parameters);
        model.addAttribute("filter", filter);
        var facilities = reportService.facilitiesForFilter();
        model.addAttribute("facilities", facilities);
        model.addAttribute("typeOptions", FacilityFilters.types(reportService.typesForFilter(), filter.facilityTypes()));
        model.addAttribute("locationOptions", FacilityFilters.locations(facilities, filter.locations()));
        try {
            model.addAttribute("recap", reportService.generate(filter));
        } catch (BusinessRuleException exception) {
            model.addAttribute("error", exception.getMessage());
        }
        return "admin/recap";
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                         @RequestParam(required = false) Long facilityId,
                                         @RequestParam MultiValueMap<String, String> parameters,
                                         @RequestParam String format) {
        RecapResult result = reportService.generate(selections(startDate, endDate, facilityId, parameters));
        return switch (format.toLowerCase(java.util.Locale.ROOT)) {
            case "csv" -> download(exportService.toCsv(result), "prism-recap.csv", "text/csv");
            case "xlsx" -> download(exportService.toXlsx(result), "prism-recap.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "pdf" -> download(exportService.toPdf(result), "prism-recap.pdf", "application/pdf");
            default -> throw new BusinessRuleException("INVALID_EXPORT_FORMAT", "Format ekspor tidak didukung");
        };
    }

    private RecapFilter selections(LocalDate start, LocalDate end, Long facilityId, MultiValueMap<String, String> parameters) {
        var filter = new RecapFilter(start, end, facilityId, parameters.getFirst("facilityType"), parameters.getFirst("location"),
                FacilityFilters.combine(parameters.get("facilityTypes"), parameters.getFirst("facilityType")),
                FacilityFilters.combine(parameters.get("locations"), parameters.getFirst("location")));
        return withDefaultPeriod(filter).normalized();
    }

    private RecapFilter withDefaultPeriod(RecapFilter filter) {
        LocalDate today = LocalDate.now(clock);
        if (filter == null || (filter.startDate() == null && filter.endDate() == null)) {
            return new RecapFilter(today.withDayOfMonth(1), today,
                    filter == null ? null : filter.facilityId(),
                    filter == null ? null : filter.facilityType(),
                    filter == null ? null : filter.location(),
                    filter == null ? null : filter.facilityTypes(), filter == null ? null : filter.locations());
        }
        return filter;
    }

    private ResponseEntity<byte[]> download(byte[] content, String filename, String mediaType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mediaType));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(content);
    }

    @ExceptionHandler(BusinessRuleException.class)
    @ResponseBody
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String invalidExportRequest(BusinessRuleException exception) {
        return exception.getMessage();
    }
}
