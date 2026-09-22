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

    public StaffDashboardController(StaffDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/staff/dashboard")
    public String dashboard(@RequestParam(defaultValue = "created") String sort, Model model) {
        model.addAttribute("dashboard", dashboardService.load(sort));
        return "staff/dashboard";
    }
}
