package com.github.kafeyangasli.prism.feature.facility.controller;

import com.github.kafeyangasli.prism.feature.facility.dto.FacilityDto;
import com.github.kafeyangasli.prism.feature.user.service.UserService;
import com.github.kafeyangasli.prism.feature.facility.service.FacilityService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
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
        populatePage(model);
        return "admin/facilities";
    }

    @PostMapping
    public String createFacility(@Valid @ModelAttribute("facilityDto") FacilityDto dto,
                                 BindingResult bindingResult,
                                 Authentication authentication,
                                 Model model,
                                 RedirectAttributes redirectAttributes,
                                 @RequestHeader(value = "HX-Request", required = false) String htmxRequest) {
        if (bindingResult.hasErrors()) {
            return renderCreateFailure(model, htmxRequest);
        }
        try {
            com.github.kafeyangasli.prism.feature.user.model.User admin = userService.findByEmail(authentication.getName());
            facilityService.createFacility(admin.getId(), dto);
            if (isHtmx(htmxRequest)) {
                model.addAttribute("facilityDto", new FacilityDto());
                model.addAttribute("facilities", facilityService.getAllFacilities());
                model.addAttribute("successMessage", "Fasilitas berhasil ditambahkan.");
                return "admin/facilities :: facility-creation-success";
            }
            redirectAttributes.addFlashAttribute("successMessage", "Fasilitas berhasil ditambahkan.");
        } catch (BusinessRuleException e) {
            if (isHtmx(htmxRequest)) {
                bindingResult.reject("facility.create", e.getMessage());
                return renderCreateFailure(model, htmxRequest);
            }
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

    private String renderCreateFailure(Model model, String htmxRequest) {
        model.addAttribute("openFacilityDialog", true);
        if (isHtmx(htmxRequest)) {
            return "admin/facilities :: facility-modal";
        }
        model.addAttribute("facilities", facilityService.getAllFacilities());
        return "admin/facilities";
    }

    private void populatePage(Model model) {
        model.addAttribute("facilities", facilityService.getAllFacilities());
        if (!model.containsAttribute("facilityDto")) {
            model.addAttribute("facilityDto", new FacilityDto());
        }
    }

    private boolean isHtmx(String htmxRequest) {
        return "true".equalsIgnoreCase(htmxRequest);
    }
}
