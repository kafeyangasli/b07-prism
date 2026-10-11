package com.github.kafeyangasli.prism.feature.blockage.controller;

import com.github.kafeyangasli.prism.feature.blockage.dto.CreateBlockageTypeRequest;
import com.github.kafeyangasli.prism.feature.blockage.dto.UpdateBlockageTypeRequest;
import com.github.kafeyangasli.prism.feature.blockage.service.BlockageTypeService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** HTML adapter for the existing type-management service; the JSON API is unchanged. */
@Controller
@RequestMapping("/admin/blockage-types")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminBlockageTypePageController {
    private final BlockageTypeService types;

    @GetMapping
    public String list(Model model) {
        if (!model.containsAttribute("typeForm")) model.addAttribute("typeForm", new CreateBlockageTypeRequest());
        model.addAttribute("types", types.getAllBlockageTypes());
        return "admin/blockage-types";
    }

    @PostMapping
    public String create(@ModelAttribute("typeForm") CreateBlockageTypeRequest form,
                         Model model, RedirectAttributes redirect) {
        try {
            types.createBlockageType(form);
            redirect.addFlashAttribute("success", "Jenis blokir berhasil ditambahkan.");
            return "redirect:/admin/blockage-types";
        } catch (BusinessRuleException exception) {
            model.addAttribute("error", exception.getMessage());
            return list(model);
        }
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id, @RequestParam String name,
                         @RequestParam(defaultValue = "") String description, RedirectAttributes redirect) {
        var form = new UpdateBlockageTypeRequest();
        form.setName(name);
        form.setDescription(description);
        try {
            types.updateBlockageType(id, form);
            redirect.addFlashAttribute("success", "Jenis blokir berhasil diperbarui.");
        } catch (BusinessRuleException | ResourceNotFoundException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/blockage-types";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, RedirectAttributes redirect) {
        try {
            types.deactivateBlockageType(id);
            redirect.addFlashAttribute("success", "Jenis blokir dinonaktifkan. Blokir yang sudah tercatat tetap tersimpan.");
        } catch (ResourceNotFoundException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/admin/blockage-types";
    }
}
