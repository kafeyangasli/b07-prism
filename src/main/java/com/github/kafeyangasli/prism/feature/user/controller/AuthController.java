package com.github.kafeyangasli.prism.feature.user.controller;

import com.github.kafeyangasli.prism.feature.user.dto.UserRegistrationDto;
import com.github.kafeyangasli.prism.feature.user.service.UserService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "auth/login";
    }

    @GetMapping("/auth/register")
    public String registerPage(Model model) {
        model.addAttribute("registrationDto", new UserRegistrationDto());
        return "auth/register";
    }

    @PostMapping("/auth/register")
    public String processRegistration(@Valid @ModelAttribute("registrationDto") UserRegistrationDto dto,
                                      BindingResult bindingResult,
                                      RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        try {
            userService.registerUser(dto);
            redirectAttributes.addFlashAttribute("successMessage", "Registrasi berhasil! Akun Anda menunggu verifikasi admin.");
            return "redirect:/login";
        } catch (BusinessRuleException e) {
            bindingResult.reject("registration", e.getMessage());
            return "auth/register";
        }
    }
}
