package com.github.kafeyangasli.prism.feature.administration.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.github.kafeyangasli.prism.feature.administration.service.ProposalTemplateSettingService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;

@Controller
@RequestMapping("/admin/settings")
public class ApplicationSettingsController {
    private final ProposalTemplateSettingService proposalTemplateSettings;

    public ApplicationSettingsController(ProposalTemplateSettingService proposalTemplateSettings) {
        this.proposalTemplateSettings = proposalTemplateSettings;
    }

    @GetMapping
    public String settings(Model model) {
        if (!model.containsAttribute("proposalTemplateUrl")) {
            model.addAttribute("proposalTemplateUrl", proposalTemplateSettings.getUrl().orElse(""));
        }
        return "admin/settings";
    }

    @PostMapping("/proposal-template")
    public String updateProposalTemplate(@RequestParam String proposalTemplateUrl,
                                         RedirectAttributes redirect) {
        try {
            proposalTemplateSettings.updateUrl(proposalTemplateUrl);
            redirect.addFlashAttribute("successMessage", "Tautan templat proposal berhasil diperbarui.");
        } catch (BusinessRuleException exception) {
            redirect.addFlashAttribute("errorMessage", exception.getMessage());
            redirect.addFlashAttribute("proposalTemplateUrl", proposalTemplateUrl);
        }
        return "redirect:/admin/settings";
    }
}
