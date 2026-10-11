package com.github.kafeyangasli.prism.feature.report.controller;

import com.github.kafeyangasli.prism.feature.report.dto.CreateReportRequest;
import com.github.kafeyangasli.prism.feature.report.dto.ReportDto;
import com.github.kafeyangasli.prism.feature.report.model.ReportStatus;
import com.github.kafeyangasli.prism.feature.report.service.ReportService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class ReportPageController {
    private final ReportService reportService;

    @InitBinder("reportForm")
    void bindForm(WebDataBinder binder) {
        binder.setAllowedFields("facilityId", "reservationId", "category", "description");
    }

    @GetMapping("/reports/new")
    public String newReport(@RequestParam(required = false) Long reservationId,
                            @RequestHeader(name = "HX-Request", required = false) String hxRequest, Model model) {
        var request = new CreateReportRequest();
        request.setReservationId(reservationId);
        if (reservationId != null) {
            request.setFacilityId(reportService.getReservationForReport(reservationId).getFacility().getId());
        }
        model.addAttribute("reportForm", request);
        return form(model, hxRequest);
    }

    private String form(Model model, String hxRequest) {
        var request = (CreateReportRequest) model.getAttribute("reportForm");
        model.addAttribute("reservationReport", request.getReservationId() != null);
        model.addAttribute("openReportDialog", true);
        if (request.getReservationId() == null) {
            model.addAttribute("facilities", reportService.getReportFacilities());
        } else {
            try {
                var reservation = reportService.getReservationForReport(request.getReservationId());
                request.setFacilityId(reservation.getFacility().getId());
                model.addAttribute("selectedFacility", reservation.getFacility());
                model.addAttribute("reportDeadline", reservation.getEndAt().plusDays(1));
            } catch (BusinessRuleException | ResourceNotFoundException e) {
                model.addAttribute("reservationError", e.getMessage());
            }
        }
        return isHtmx(hxRequest) ? "reports/form :: report-modal" : "reports/new";
    }

    @PostMapping("/reports")
    public Object create(@Valid @ModelAttribute("reportForm") CreateReportRequest request, BindingResult errors,
                         @RequestParam(value = "photo", required = false) MultipartFile photo,
                         @RequestHeader(name = "HX-Request", required = false) String hxRequest,
                         Model model, RedirectAttributes redirect,
                         jakarta.servlet.http.HttpServletRequest servletRequest,
                         jakarta.servlet.http.HttpServletResponse servletResponse) {
        if (photo == null || photo.isEmpty()) errors.reject("photo.required", "Foto laporan wajib diunggah.");
        if (errors.hasErrors()) return form(model, hxRequest);
        try {
            var report = reportService.createReport(request, photo);
            redirect.addFlashAttribute("success", "Laporan berhasil dikirim.");
            String detailUrl = "/reports/" + report.getId();
            if (isHtmx(hxRequest)) {
                org.springframework.web.servlet.support.RequestContextUtils.getOutputFlashMap(servletRequest)
                        .putAll(redirect.getFlashAttributes());
                org.springframework.web.servlet.support.RequestContextUtils.saveOutputFlashMap(detailUrl, servletRequest, servletResponse);
                return ResponseEntity.noContent().header("HX-Redirect", detailUrl).build();
            }
            return "redirect:" + detailUrl;
        } catch (BusinessRuleException | ResourceNotFoundException e) {
            errors.reject("report.invalid", e.getMessage());
            return form(model, hxRequest);
        }
    }

    private boolean isHtmx(String hxRequest) {
        return "true".equalsIgnoreCase(hxRequest);
    }

    @GetMapping("/reports")
    public String list(Model model) {
        model.addAttribute("reports", reportService.getMyReports().stream().map(ReportDto::from).toList());
        return "reports/list";
    }

    @GetMapping("/reports/{id}")
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("report", ReportDto.from(reportService.getReportDetail(id)));
        model.addAttribute("staffView", false);
        return "reports/detail";
    }

    @GetMapping({"/reports/{id}/photo", "/api/reports/{id}/photo", "/staff/reports/{id}/photo"})
    @ResponseBody
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        byte[] bytes = reportService.getPhoto(id);
        String type = bytes.length > 3 && bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8 ? "image/jpeg"
                : bytes.length > 8 && bytes[0] == (byte) 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G'
                ? "image/png" : "application/octet-stream";
        return ResponseEntity.ok().header("Content-Type", type).header("X-Content-Type-Options", "nosniff")
                .header("Cache-Control", "no-store, private")
                .header("Content-Disposition", type.startsWith("image/") ? "inline" : "attachment")
                .body(bytes);
    }

    @GetMapping("/staff/reports/{id}")
    @PreAuthorize("hasAnyRole('PETUGAS', 'ADMIN')")
    public String staffDetail(@PathVariable Long id, Model model) {
        model.addAttribute("report", ReportDto.from(reportService.getStaffReportDetail(id)));
        model.addAttribute("staffView", true);
        return "reports/detail";
    }

    @PostMapping("/staff/reports/{id}/status")
    @PreAuthorize("hasAnyRole('PETUGAS', 'ADMIN')")
    public String update(@PathVariable Long id, @RequestParam ReportStatus status,
                         @RequestParam(required = false) String resolutionNote, RedirectAttributes redirect) {
        try {
            reportService.updateStatus(id, status, resolutionNote);
            redirect.addFlashAttribute("success", "Status laporan berhasil diperbarui.");
        } catch (BusinessRuleException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/staff/reports";
    }
}
