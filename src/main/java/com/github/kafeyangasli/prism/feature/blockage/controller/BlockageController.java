package com.github.kafeyangasli.prism.feature.blockage.controller;

import com.github.kafeyangasli.prism.feature.blockage.dto.*;
import com.github.kafeyangasli.prism.feature.blockage.service.BlockageService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.List;
import java.util.Map;

@Controller
@PreAuthorize("hasAnyRole('PETUGAS', 'ADMIN')")
@RequiredArgsConstructor
public class BlockageController {
    private final BlockageService blockageService;

    @PostMapping("/api/staff/blockages/preview") @ResponseBody
    public BlockageImpactPreviewResponse previewBlockageImpact(@RequestBody BlockageImpactPreviewRequest request) {
        return blockageService.previewBlockageImpact(request);
    }

    @PostMapping("/api/staff/blockages/{id}/preview") @ResponseBody
    public BlockageImpactPreviewResponse previewUpdate(@PathVariable Long id, @RequestBody UpdateBlockageRequest request) {
        return blockageService.previewUpdate(id, request);
    }

    @PostMapping("/api/staff/blockages") @ResponseBody @ResponseStatus(HttpStatus.CREATED)
    public BlockageDto createBlockage(@RequestBody CreateBlockageRequest request) {
        return BlockageDto.from(blockageService.createBlockage(request));
    }

    @PutMapping("/api/staff/blockages/{id}") @ResponseBody
    public BlockageDto updateOrExtendBlockage(@PathVariable Long id, @RequestBody UpdateBlockageRequest request) {
        return BlockageDto.from(blockageService.updateOrExtendBlockage(id, request));
    }

    @PatchMapping("/api/staff/blockages/{id}/complete") @ResponseBody
    public BlockageDto earlyCompleteBlockage(@PathVariable Long id, @RequestBody EarlyCompletionRequest request) {
        return BlockageDto.from(blockageService.earlyCompleteBlockage(id, request));
    }

    @GetMapping("/api/staff/blockages") @ResponseBody
    public List<BlockageDto> getAllBlockages() {
        return blockageService.getAllBlockages().stream().map(BlockageDto::from).toList();
    }

    @GetMapping("/api/staff/blockages/{id}") @ResponseBody
    public BlockageDto getBlockageById(@PathVariable Long id) {
        return BlockageDto.from(blockageService.getBlockageById(id));
    }

    @InitBinder("blockageForm")
    void bind(WebDataBinder binder) {
        binder.setAllowedFields("facilityId", "blockageTypeId", "reportId", "startAt", "plannedEndAt", "publicReason",
                "internalNote", "openEnded", "confirmed", "confirmationToken");
    }

    private String page(Model model, Object request, Long id) {
        model.addAttribute("blockageForm", request);
        model.addAttribute("blockageId", id);
        model.addAttribute("blockages", blockageService.getAllBlockages().stream().map(BlockageDto::from).toList());
        model.addAttribute("facilities", blockageService.getStaffFacilities());
        model.addAttribute("types", blockageService.getActiveTypes());
        if (id != null) model.addAttribute("editing", BlockageDto.from(blockageService.getBlockageById(id)));
        return "staff/blockages";
    }

    @GetMapping("/staff/blockages")
    public String page(Model model) { return page(model, new CreateBlockageRequest(), null); }

    @GetMapping("/staff/blockages/{id}/edit")
    public String edit(@PathVariable Long id, Model model) {
        var b = blockageService.getBlockageById(id);
        var request = new UpdateBlockageRequest(b.getPlannedEndAt(), b.getPublicReason(), b.getInternalNote());
        request.setOpenEnded(b.getPlannedEndAt() == null);
        return page(model, request, id);
    }

    @PostMapping({"/staff/blockages/preview", "/staff/blockages/input"})
    public String previewCreate(@ModelAttribute("blockageForm") BlockageImpactPreviewRequest request, BindingResult errors,
                                jakarta.servlet.http.HttpServletRequest servletRequest, Model model) {
        if (!errors.hasErrors() && servletRequest.getRequestURI().endsWith("/preview")) {
            try { model.addAttribute("preview", blockageService.previewBlockageImpact(request)); }
            catch (BusinessRuleException | ResourceNotFoundException e) { model.addAttribute("error", e.getMessage()); }
        }
        return page(model, request, null);
    }

    @PostMapping({"/staff/blockages/{id}/preview", "/staff/blockages/{id}/input"})
    public String previewEdit(@PathVariable Long id, @ModelAttribute("blockageForm") UpdateBlockageRequest request, BindingResult errors,
                              jakarta.servlet.http.HttpServletRequest servletRequest, Model model) {
        if (!errors.hasErrors() && servletRequest.getRequestURI().endsWith("/preview")) {
            try { model.addAttribute("preview", blockageService.previewUpdate(id, request)); }
            catch (BusinessRuleException | ResourceNotFoundException e) { model.addAttribute("error", e.getMessage()); }
        }
        return page(model, request, id);
    }

    @PostMapping("/staff/blockages")
    public String confirmCreate(@ModelAttribute("blockageForm") CreateBlockageRequest request, BindingResult errors,
                                Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) return page(model, request, null);
        try {
            blockageService.createBlockage(request);
            redirect.addFlashAttribute("success", "Blokir dan dampak reservasi berhasil disimpan.");
            return "redirect:/staff/blockages";
        } catch (BusinessRuleException | ResourceNotFoundException e) {
            model.addAttribute("error", e.getMessage());
            return page(model, request, null);
        }
    }

    @PostMapping("/staff/blockages/{id}")
    public String confirmEdit(@PathVariable Long id, @ModelAttribute("blockageForm") UpdateBlockageRequest request, BindingResult errors,
                              Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) return page(model, request, id);
        try {
            blockageService.updateOrExtendBlockage(id, request);
            redirect.addFlashAttribute("success", "Perubahan blokir dan dampak reservasi berhasil disimpan.");
            return "redirect:/staff/blockages";
        } catch (BusinessRuleException | ResourceNotFoundException e) {
            model.addAttribute("error", e.getMessage());
            return page(model, request, id);
        }
    }

    @PostMapping("/staff/blockages/{id}/complete")
    public String complete(@PathVariable Long id, @RequestParam String earlyCompletionReason, RedirectAttributes redirect) {
        try {
            blockageService.earlyCompleteBlockage(id, new EarlyCompletionRequest(earlyCompletionReason));
            redirect.addFlashAttribute("success", "Blokir diselesaikan. Reservasi terdampak tetap dibatalkan atau ditolak.");
        } catch (BusinessRuleException | ResourceNotFoundException e) { redirect.addFlashAttribute("error", e.getMessage()); }
        return "redirect:/staff/blockages";
    }

    @ExceptionHandler(BusinessRuleException.class) @ResponseBody
    public ResponseEntity<Map<String, String>> invalid(BusinessRuleException e) {
        int status = List.of("CONFIRMATION_REQUIRED", "PREVIEW_EXPIRED", "PREVIEW_CHANGED", "IMPACT_CHANGED").contains(e.getCode()) ? 409 : 400;
        return ResponseEntity.status(status).body(Map.of("code", e.getCode(), "message", e.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class) @ResponseBody
    public ResponseEntity<Map<String, String>> missing(ResourceNotFoundException e) {
        return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
    }
}
