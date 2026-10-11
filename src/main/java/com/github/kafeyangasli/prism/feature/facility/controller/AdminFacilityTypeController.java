package com.github.kafeyangasli.prism.feature.facility.controller;

import com.github.kafeyangasli.prism.feature.facility.dto.FacilityTypeDto;
import com.github.kafeyangasli.prism.feature.facility.service.FacilityTypeService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/facility-types")
public class AdminFacilityTypeController {

    private final FacilityTypeService facilityTypeService;

    public AdminFacilityTypeController(FacilityTypeService facilityTypeService) {
        this.facilityTypeService = facilityTypeService;
    }

    @GetMapping
    public String list(Model model) {
        populate(model);
        return "admin/facility-types";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("facilityTypeDto") FacilityTypeDto dto,
                         BindingResult bindingResult, Model model,
                         RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            populate(model);
            return "admin/facility-types";
        }
        try {
            facilityTypeService.create(dto);
            redirectAttributes.addFlashAttribute("successMessage", "Tipe fasilitas berhasil ditambahkan.");
            return "redirect:/admin/facility-types";
        } catch (BusinessRuleException ex) {
            bindingResult.reject("facilityType.create", ex.getMessage());
            populate(model);
            return "admin/facility-types";
        }
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id, @ModelAttribute FacilityTypeDto dto,
                         RedirectAttributes redirectAttributes) {
        return perform(redirectAttributes, "Tipe fasilitas berhasil diperbarui.",
                () -> facilityTypeService.update(id, dto));
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return perform(redirectAttributes, "Tipe fasilitas berhasil dinonaktifkan.",
                () -> facilityTypeService.deactivate(id));
    }

    @PostMapping("/{id}/activate")
    public String activate(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        return perform(redirectAttributes, "Tipe fasilitas berhasil diaktifkan.",
                () -> facilityTypeService.activate(id));
    }

    private String perform(RedirectAttributes redirectAttributes, String message, Runnable action) {
        try {
            action.run();
            redirectAttributes.addFlashAttribute("successMessage", message);
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/facility-types";
    }

    private void populate(Model model) {
        model.addAttribute("facilityTypes", facilityTypeService.getAll());
        if (!model.containsAttribute("facilityTypeDto")) {
            model.addAttribute("facilityTypeDto", new FacilityTypeDto());
        }
    }
}
