package com.github.kafeyangasli.prism.feature.reservation.controller;

import com.github.kafeyangasli.prism.feature.administration.service.StaffActorResolver;
import com.github.kafeyangasli.prism.feature.reservation.dto.CancellationForm;
import com.github.kafeyangasli.prism.feature.reservation.dto.RejectionForm;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationProcessingService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.ui.Model;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionException;

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

    @GetMapping("/staff/reservations/{id}")
    public String detail(@PathVariable long id, Authentication authentication, Model model) {
        try {
            model.addAttribute("reservation", processingService.managementDetail(id,
                    actorResolver.resolveId(authentication.getName())));
            return "staff/reservation-detail";
        } catch (ResourceNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    @PostMapping("/staff/reservations/{id}/proposal/validate")
    public String validateProposal(@PathVariable long id,
                                   @RequestParam(required = false) MultipartFile validatedProposal,
                                   Authentication authentication,
                                   RedirectAttributes redirect) {
        try {
            return execute(redirect, () -> processingService.validateProposal(id,
                    actorResolver.resolveId(authentication.getName()), validatedProposal),
                    "Proposal dan PDF tervalidasi berhasil disimpan. Reservasi tetap menunggu persetujuan");
        } catch (DataAccessException | TransactionException exception) {
            redirect.addFlashAttribute("error", "Validasi proposal gagal disimpan. Silakan coba lagi.");
            return "redirect:/staff/reservations";
        }
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<String> oversizedProposal() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body("Ukuran PDF tervalidasi melebihi batas unggahan. Pilih berkas yang lebih kecil.");
    }

    @GetMapping("/staff/reservations/{id}/proposal")
    public ResponseEntity<Resource> proposal(@PathVariable long id, Authentication authentication) {
        try {
            Resource resource = processingService.proposalForReview(id,
                    actorResolver.resolveId(authentication.getName()));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename(resource.getFilename() == null ? "proposal-" + id : resource.getFilename())
                            .build().toString())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
        } catch (ResourceNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (BusinessRuleException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
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
            redirect.addFlashAttribute("cancellationReason", form.reason());
            return "redirect:/staff/reservations/" + id;
        }
        try {
            processingService.cancelApproved(id, actorResolver.resolveId(authentication.getName()), form.reason());
            redirect.addFlashAttribute("success", "Reservasi berhasil dibatalkan");
        } catch (BusinessRuleException | ResourceNotFoundException exception) {
            redirect.addFlashAttribute("error", exception.getMessage());
            redirect.addFlashAttribute("cancellationReason", form.reason());
        }
        return "redirect:/staff/reservations/" + id;
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
