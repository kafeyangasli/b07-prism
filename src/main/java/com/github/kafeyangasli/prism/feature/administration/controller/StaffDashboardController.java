package com.github.kafeyangasli.prism.feature.administration.controller;

import com.github.kafeyangasli.prism.feature.administration.service.StaffDashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@PreAuthorize("hasAnyRole('PETUGAS','ADMIN')")
public class StaffDashboardController {

    private final StaffDashboardService dashboardService;
    private final java.time.Clock clock;

    public StaffDashboardController(StaffDashboardService dashboardService, java.time.Clock clock) {
        this.dashboardService = dashboardService;
        this.clock = clock;
    }

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
