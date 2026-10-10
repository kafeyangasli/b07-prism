package com.github.kafeyangasli.prism.feature.user.controller;

import com.github.kafeyangasli.prism.feature.user.dto.*;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.service.AccountSettingsService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/account")
public class AccountSettingsController {
    private final AccountSettingsService service;
    private final int avatarMaxBytes;

    public AccountSettingsController(AccountSettingsService service,
            @Value("${prism.avatars.max-bytes:5242880}") int avatarMaxBytes) {
        this.service = service;
        this.avatarMaxBytes = avatarMaxBytes;
    }

    @InitBinder("profileForm")
    void profileFields(WebDataBinder binder) { binder.setAllowedFields("name"); }

    @GetMapping
    public String settings(Model model) {
        var account = service.currentAccount();
        model.addAttribute("account", account);
        model.addAttribute("avatarMaxBytes", avatarMaxBytes);
        if (!model.containsAttribute("profileForm")) {
            var profile = new AccountProfileForm();
            profile.setName(account.name());
            model.addAttribute("profileForm", profile);
        }
        return "account/settings";
    }

    @PostMapping("/profile")
    public String profile(@Valid @ModelAttribute("profileForm") AccountProfileForm form,
                          BindingResult errors, Model model, RedirectAttributes redirect) {
        if (!errors.hasErrors()) {
            try {
                service.updateProfile(form);
                redirect.addFlashAttribute("successMessage", "Nama tampilan berhasil diperbarui.");
                return "redirect:/account";
            } catch (BusinessRuleException exception) {
                errors.reject("account.profile", exception.getMessage());
            } catch (DataAccessException | TransactionException exception) {
                errors.reject("account.persistence", persistenceMessage());
            }
        }
        return settings(model);
    }

    @GetMapping("/password")
    public String passwordPage(Model model) {
        model.addAttribute("account", service.currentAccount());
        return "account/password";
    }

    @PostMapping("/password")
    public String password(@RequestParam(defaultValue = "") String currentPassword,
                           @RequestParam(defaultValue = "") String newPassword,
                           @RequestParam(defaultValue = "") String confirmPassword,
                           Model model, HttpServletRequest request, HttpServletResponse response) {
        // Keep secret DTOs local: no password or rejected value enters the Model,
        // BindingResult, flash attributes, or redirects.
        var form = new AccountPasswordForm();
        form.setCurrentPassword(currentPassword);
        form.setNewPassword(newPassword);
        form.setConfirmPassword(confirmPassword);
        try {
            service.changePassword(form);
            logout(request, response);
            return "redirect:/login?passwordChanged";
        } catch (BusinessRuleException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
        } catch (DataAccessException | TransactionException exception) {
            model.addAttribute("errorMessage", persistenceMessage());
        }
        return passwordPage(model);
    }

    @GetMapping("/delete")
    public String deletionPage(Model model) {
        var account = service.currentAccount();
        if (account.role() != Role.PENGGUNA) {
            throw new AccessDeniedException("Hanya Pengguna yang dapat menonaktifkan akun sendiri.");
        }
        model.addAttribute("account", account);
        return "account/delete";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam(defaultValue = "false") boolean confirmed,
                         @RequestParam(defaultValue = "") String currentPassword,
                         Model model, HttpServletRequest request, HttpServletResponse response) {
        var form = new AccountDeletionForm();
        form.setConfirmed(confirmed);
        form.setCurrentPassword(currentPassword);
        try {
            service.deactivateAccount(form);
            logout(request, response);
            return "redirect:/login?accountDeactivated";
        } catch (BusinessRuleException exception) {
            model.addAttribute("errorMessage", exception.getMessage());
        } catch (DataAccessException | TransactionException exception) {
            model.addAttribute("errorMessage", persistenceMessage());
        }
        model.addAttribute("confirmed", confirmed);
        return deletionPage(model);
    }

    private void logout(HttpServletRequest request, HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response,
                SecurityContextHolder.getContext().getAuthentication());
    }

    private String persistenceMessage() {
        return "Perubahan belum dapat disimpan. Muat ulang halaman dan coba lagi.";
    }
}
