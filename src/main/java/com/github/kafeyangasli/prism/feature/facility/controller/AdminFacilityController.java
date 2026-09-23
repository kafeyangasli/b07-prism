package com.github.kafeyangasli.prism.feature.facility.controller;

import com.github.kafeyangasli.prism.feature.facility.dto.FacilityDto;
import com.github.kafeyangasli.prism.feature.user.service.UserService;
import com.github.kafeyangasli.prism.feature.facility.service.FacilityService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/facilities")
public class AdminFacilityController {

    private final FacilityService facilityService;
    private final UserService userService;

    public AdminFacilityController(FacilityService facilityService, UserService userService) {
        this.facilityService = facilityService;
        this.userService = userService;
    }

    @GetMapping
    public String listFacilities(Model model) {
        model.addAttribute("facilities", facilityService.getAllFacilities());
        model.addAttribute("facilityDto", new FacilityDto());
        return "admin/facilities";
    }

    @PostMapping
    public String createFacility(@ModelAttribute("facilityDto") FacilityDto dto,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        try {
            com.github.kafeyangasli.prism.feature.user.model.User admin = userService.findByEmail(authentication.getName());
            facilityService.createFacility(admin.getId(), dto);
            redirectAttributes.addFlashAttribute("successMessage", "Fasilitas berhasil ditambahkan.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/facilities";
    }

    @PostMapping("/{id}/update")
    public String updateFacility(@PathVariable("id") Long id,
                                 @ModelAttribute("facilityDto") FacilityDto dto,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes) {
        try {
            com.github.kafeyangasli.prism.feature.user.model.User admin = userService.findByEmail(authentication.getName());
            facilityService.updateFacility(admin.getId(), id, dto);
            redirectAttributes.addFlashAttribute("successMessage", "Fasilitas berhasil diperbarui.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/facilities";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivateFacility(@PathVariable("id") Long id,
                                     Authentication authentication,
                                     RedirectAttributes redirectAttributes) {
        try {
            com.github.kafeyangasli.prism.feature.user.model.User admin = userService.findByEmail(authentication.getName());
            facilityService.deactivateFacility(admin.getId(), id);
            redirectAttributes.addFlashAttribute("successMessage", "Fasilitas berhasil dinonaktifkan.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/facilities";
    }
}
