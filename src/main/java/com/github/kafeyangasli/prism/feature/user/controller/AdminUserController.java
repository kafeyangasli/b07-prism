package com.github.kafeyangasli.prism.feature.user.controller;

import com.github.kafeyangasli.prism.feature.user.dto.AdminCreateUserDto;
import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.service.UserService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String listUsers(Model model) {
        model.addAttribute("pendingUsers", userService.findUsersByStatus(AccountStatus.PENDING));
        model.addAttribute("allUsers", userService.findAllUsers());
        model.addAttribute("createUserDto", new AdminCreateUserDto());
        return "admin/users";
    }

    @PostMapping("/{id}/verify")
    public String verifyUser(@PathVariable("id") Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            User admin = userService.findByEmail(authentication.getName());
            userService.verifyUser(admin.getId(), id);
            redirectAttributes.addFlashAttribute("successMessage", "Akun berhasil diverifikasi.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/reject")
    public String rejectUser(@PathVariable("id") Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            User admin = userService.findByEmail(authentication.getName());
            userService.rejectUser(admin.getId(), id);
            redirectAttributes.addFlashAttribute("successMessage", "Akun berhasil ditolak.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivateUser(@PathVariable("id") Long id, Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            User admin = userService.findByEmail(authentication.getName());
            userService.deactivateUser(admin.getId(), id);
            redirectAttributes.addFlashAttribute("successMessage", "Akun berhasil dinonaktifkan.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping
    public String createUser(@ModelAttribute("createUserDto") AdminCreateUserDto dto,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            User admin = userService.findByEmail(authentication.getName());
            userService.createUserByAdmin(admin.getId(), dto);
            redirectAttributes.addFlashAttribute("successMessage", "Akun baru berhasil dibuat.");
        } catch (BusinessRuleException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/users";
    }
}
