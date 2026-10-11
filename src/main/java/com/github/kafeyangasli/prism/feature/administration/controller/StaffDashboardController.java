package com.github.kafeyangasli.prism.feature.administration.controller;

import com.github.kafeyangasli.prism.feature.administration.service.StaffDashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ModelAttribute;
import com.github.kafeyangasli.prism.shared.exception.storage.ProposalStorageService;

@Controller
@PreAuthorize("hasAnyRole('PETUGAS','ADMIN')")
public class StaffDashboardController {

    private final StaffDashboardService dashboardService;
    private final java.time.Clock clock;
    private final ProposalStorageService proposals;

    public StaffDashboardController(StaffDashboardService dashboardService, java.time.Clock clock,
                                    ProposalStorageService proposals) {
        this.dashboardService = dashboardService;
        this.clock = clock;
        this.proposals = proposals;
    }

    @ModelAttribute("validatedProposalMaxBytes")
    public long validatedProposalMaxBytes() { return proposals.getValidatedMaxBytes(); }

    @GetMapping("/staff/dashboard")
    public String dashboard(@RequestParam(defaultValue = "created") String sort, Model model) {
        model.addAttribute("dashboard", dashboardService.load(sort));
        model.addAttribute("urgentBefore", java.time.LocalDateTime.now(clock).plusHours(24));
        return "staff/dashboard";
    }

    @GetMapping("/staff/reservations")
    public String reservations(@RequestParam(defaultValue = "created") String sort, Model model) {
        model.addAttribute("dashboard", dashboardService.load(sort));
        model.addAttribute("urgentBefore", java.time.LocalDateTime.now(clock).plusHours(24));
        return "staff/reservations";
    }

    @GetMapping("/staff/reports")
    public String reports(Model model) {
        model.addAttribute("dashboard", dashboardService.load("created"));
        model.addAttribute("reportHistory", dashboardService.reportHistory());
        return "staff/reports";
    }
}
