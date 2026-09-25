package com.github.kafeyangasli.prism.feature.reservation.controller;

import com.github.kafeyangasli.prism.feature.administration.service.StaffActorResolver;
import com.github.kafeyangasli.prism.feature.reservation.dto.CancellationForm;
import com.github.kafeyangasli.prism.feature.reservation.dto.RejectionForm;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationProcessingService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@PreAuthorize("hasAnyRole('PETUGAS','ADMIN')")
public class ReservationProcessingController {

    private final ReservationProcessingService processingService;
    private final StaffActorResolver actorResolver;

    public ReservationProcessingController(ReservationProcessingService processingService,
                                           StaffActorResolver actorResolver) {
        this.processingService = processingService;
        this.actorResolver = actorResolver;
    }

    @PostMapping("/staff/reservations/{id}/approve")
    public String approve(@PathVariable long id,
                          @RequestParam(defaultValue = "false") boolean confirmCascade,
                          Authentication authentication,
                          RedirectAttributes redirect) {
        return execute(redirect, () -> processingService.approve(id,
                actorResolver.resolveId(authentication.getName()), confirmCascade),
                "Reservasi berhasil disetujui");
    }

    @PostMapping("/staff/reservations/{id}/reject")
    public String reject(@PathVariable long id,
                         @Valid @ModelAttribute RejectionForm form,
                         BindingResult bindingResult,
                         Authentication authentication,
                         RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            redirect.addFlashAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/staff/reservations";
        }
        return execute(redirect, () -> processingService.reject(id,
                actorResolver.resolveId(authentication.getName()), form.reasonDetail()),
                "Reservasi berhasil ditolak");
    }

    @PostMapping("/staff/reservations/{id}/cancel")
    public String cancel(@PathVariable long id,
                         @Valid @ModelAttribute CancellationForm form,
                         BindingResult bindingResult,
                         Authentication authentication,
                         RedirectAttributes redirect) {
        if (bindingResult.hasErrors()) {
            redirect.addFlashAttribute("error", bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/staff/reservations";
        }
        return execute(redirect, () -> processingService.cancelApproved(id,
                actorResolver.resolveId(authentication.getName()), form.reason()),
                "Reservasi berhasil dibatalkan");
    }

    private String execute(RedirectAttributes redirect, Runnable action, String successMessage) {
        try {
            action.run();
            redirect.addFlashAttribute("success", successMessage);
        } catch (BusinessRuleException | ResourceNotFoundException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/staff/reservations";
    }
}
