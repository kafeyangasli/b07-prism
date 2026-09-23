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
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/blockages")
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
        Long mockUserId = 1L; // temporary until auth principal bound
        return blockageService.createBlockage(request, mockUserId);
    }

    @PutMapping("/{id}")
    public FacilityBlockage updateOrExtendBlockage(@PathVariable Long id, @RequestBody UpdateBlockageRequest request) {
        Long mockUserId = 1L;
        return blockageService.updateOrExtendBlockage(id, request, mockUserId);
    }

    @PatchMapping("/{id}/complete")
    public FacilityBlockage earlyCompleteBlockage(@PathVariable Long id, @RequestBody EarlyCompletionRequest request) {
        Long mockUserId = 1L;
        return blockageService.earlyCompleteBlockage(id, request, mockUserId);
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