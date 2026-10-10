package com.github.kafeyangasli.prism.feature.blockage.controller;

import com.github.kafeyangasli.prism.feature.blockage.dto.BlockageImpactPreviewRequest;
import com.github.kafeyangasli.prism.feature.blockage.dto.BlockageImpactPreviewResponse;
import com.github.kafeyangasli.prism.feature.blockage.dto.CreateBlockageRequest;
import com.github.kafeyangasli.prism.feature.blockage.dto.EarlyCompletionRequest;
import com.github.kafeyangasli.prism.feature.blockage.dto.UpdateBlockageRequest;
import com.github.kafeyangasli.prism.feature.blockage.model.FacilityBlockage;
import com.github.kafeyangasli.prism.feature.blockage.service.BlockageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/staff/blockages")
@PreAuthorize("hasAnyRole('PETUGAS', 'ADMIN')")
@RequiredArgsConstructor
public class BlockageController {

    private final BlockageService blockageService;

    @PostMapping("/preview")
    public BlockageImpactPreviewResponse previewBlockageImpact(@RequestBody BlockageImpactPreviewRequest request) {
        return blockageService.previewBlockageImpact(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FacilityBlockage createBlockage(@RequestBody CreateBlockageRequest request) {
        return blockageService.createBlockage(request);
    }

    @PutMapping("/{id}")
    public FacilityBlockage updateOrExtendBlockage(@PathVariable Long id, @RequestBody UpdateBlockageRequest request) {
        return blockageService.updateOrExtendBlockage(id, request);
    }

    @PatchMapping("/{id}/complete")
    public FacilityBlockage earlyCompleteBlockage(@PathVariable Long id, @RequestBody EarlyCompletionRequest request) {
        return blockageService.earlyCompleteBlockage(id, request);
    }

    @GetMapping
    public List<FacilityBlockage> getAllBlockages() {
        return blockageService.getAllBlockages();
    }

    @GetMapping("/{id}")
    public FacilityBlockage getBlockageById(@PathVariable Long id) {
        return blockageService.getBlockageById(id);
    }
}