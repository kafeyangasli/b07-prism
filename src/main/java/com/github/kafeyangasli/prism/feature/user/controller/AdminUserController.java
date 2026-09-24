package com.github.kafeyangasli.prism.feature.user.controller;

import com.github.kafeyangasli.prism.feature.user.dto.AdminCreateUserDto;
import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.service.UserService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String listUsers(@RequestParam(required = false) String search,
                            @RequestParam(name = "filterRole", required = false) Role role,
                            @RequestParam(name = "filterStatus", required = false) AccountStatus status,
                            @RequestParam(defaultValue = "createdAt") String sort,
                            @RequestParam(defaultValue = "desc") String direction,
                            @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                            Model model) {
        populatePage(model, search, role, status, sort, direction);
        return isHtmx(htmxRequest) ? "admin/users :: user-results" : "admin/users";
    }

    @PostMapping("/{id}/verify")
    public String verifyUser(@PathVariable("id") Long id,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes,
                             @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                             @RequestParam(required = false) String search,
                             @RequestParam(name = "filterRole", required = false) Role role,
                             @RequestParam(name = "filterStatus", required = false) AccountStatus status,
                             @RequestParam(defaultValue = "createdAt") String sort,
                             @RequestParam(defaultValue = "desc") String direction,
                             Model model) {
        return mutateUser(() -> userService.verifyUser(currentAdmin(authentication).getId(), id),
                "Akun berhasil diverifikasi.", redirectAttributes, htmxRequest,
                search, role, status, sort, direction, model);
    }

    @PostMapping("/{id}/reject")
    public String rejectUser(@PathVariable("id") Long id,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes,
                             @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                             @RequestParam(required = false) String search,
                             @RequestParam(name = "filterRole", required = false) Role role,
                             @RequestParam(name = "filterStatus", required = false) AccountStatus status,
                             @RequestParam(defaultValue = "createdAt") String sort,
                             @RequestParam(defaultValue = "desc") String direction,
                             Model model) {
        return mutateUser(() -> userService.rejectUser(currentAdmin(authentication).getId(), id),
                "Akun berhasil ditolak.", redirectAttributes, htmxRequest,
                search, role, status, sort, direction, model);
    }

    @PostMapping("/{id}/deactivate")
    public String deactivateUser(@PathVariable("id") Long id,
                                 Authentication authentication,
                                 RedirectAttributes redirectAttributes,
                                 @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                                 @RequestParam(required = false) String search,
                                 @RequestParam(name = "filterRole", required = false) Role role,
                                 @RequestParam(name = "filterStatus", required = false) AccountStatus status,
                                 @RequestParam(defaultValue = "createdAt") String sort,
                                 @RequestParam(defaultValue = "desc") String direction,
                                 Model model) {
        return mutateUser(() -> userService.deactivateUser(currentAdmin(authentication).getId(), id),
                "Akun berhasil dinonaktifkan.", redirectAttributes, htmxRequest,
                search, role, status, sort, direction, model);
    }

    @PostMapping("/{id}/activate")
    public String activateUser(@PathVariable("id") Long id,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes,
                               @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                               @RequestParam(required = false) String search,
                               @RequestParam(name = "filterRole", required = false) Role role,
                               @RequestParam(name = "filterStatus", required = false) AccountStatus status,
                               @RequestParam(defaultValue = "createdAt") String sort,
                               @RequestParam(defaultValue = "desc") String direction,
                               Model model) {
        return mutateUser(() -> userService.activateUser(currentAdmin(authentication).getId(), id),
                "Akun berhasil diaktifkan.", redirectAttributes, htmxRequest,
                search, role, status, sort, direction, model);
    }

    @PostMapping
    public String createUser(@Valid @ModelAttribute("createUserDto") AdminCreateUserDto dto,
                             BindingResult bindingResult,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes,
                             @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                             @RequestParam(required = false) String search,
                             @RequestParam(name = "filterRole", required = false) Role role,
                             @RequestParam(name = "filterStatus", required = false) AccountStatus status,
                             @RequestParam(defaultValue = "createdAt") String sort,
                             @RequestParam(defaultValue = "desc") String direction,
                             Model model) {
        if (bindingResult.hasErrors()) {
            return renderCreateFailure(model, htmxRequest, search, role, status, sort, direction);
        }
        try {
            userService.createUserByAdmin(currentAdmin(authentication).getId(), dto);
            if (isHtmx(htmxRequest)) {
                model.addAttribute("createUserDto", new AdminCreateUserDto());
                model.addAttribute("successMessage", "Akun baru berhasil dibuat.");
                populatePage(model, search, role, status, sort, direction);
                return "admin/users :: user-creation-success";
            }
            redirectAttributes.addFlashAttribute("successMessage", "Akun baru berhasil dibuat.");
        } catch (BusinessRuleException exception) {
            if (isHtmx(htmxRequest)) {
                bindingResult.reject("user.create", exception.getMessage());
                return renderCreateFailure(model, htmxRequest, search, role, status, sort, direction);
            }
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/admin/users";
    }

    private String mutateUser(
            UserMutation mutation,
            String successMessage,
            RedirectAttributes redirectAttributes,
            String htmxRequest,
            String search,
            Role role,
            AccountStatus status,
            String sort,
            String direction,
            Model model
    ) {
        try {
            mutation.execute();
            if (isHtmx(htmxRequest)) {
                model.addAttribute("actionMessage", successMessage);
                populatePage(model, search, role, status, sort, direction);
                return "admin/users :: user-results";
            }
            redirectAttributes.addFlashAttribute("successMessage", successMessage);
        } catch (Exception exception) {
            if (isHtmx(htmxRequest)) {
                model.addAttribute("actionError", exception.getMessage());
                populatePage(model, search, role, status, sort, direction);
                return "admin/users :: user-results";
            }
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/admin/users";
    }

    private String renderCreateFailure(
            Model model,
            String htmxRequest,
            String search,
            Role role,
            AccountStatus status,
            String sort,
            String direction
    ) {
        model.addAttribute("openUserDialog", true);
        if (isHtmx(htmxRequest)) {
            return "admin/users :: user-modal";
        }
        populatePage(model, search, role, status, sort, direction);
        return "admin/users";
    }

    private void populatePage(Model model,
                              String search,
                              Role role,
                              AccountStatus status,
                              String sort,
                              String direction) {
        model.addAttribute("pendingUsers", userService.findUsersByStatus(AccountStatus.PENDING));
        model.addAttribute("allUsers", userService.findUsers(search, role, status, sort, direction));
        model.addAttribute("search", search);
        model.addAttribute("selectedRole", role);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("sort", sort);
        model.addAttribute("direction", direction);
        if (!model.containsAttribute("createUserDto")) {
            model.addAttribute("createUserDto", new AdminCreateUserDto());
        }
    }

    private User currentAdmin(Authentication authentication) {
        return userService.findByEmail(authentication.getName());
    }

    private boolean isHtmx(String htmxRequest) {
        return "true".equalsIgnoreCase(htmxRequest);
    }

    @FunctionalInterface
    private interface UserMutation {
        User execute();
    }
}
