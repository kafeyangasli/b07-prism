package com.github.kafeyangasli.prism.feature.blockage.controller;

import com.github.kafeyangasli.prism.feature.blockage.dto.BlockageImpactPreviewRequest;
import com.github.kafeyangasli.prism.feature.blockage.dto.BlockageImpactPreviewResponse;
import com.github.kafeyangasli.prism.feature.blockage.service.BlockageService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/blockages")
@RequiredArgsConstructor
public class BlockageController {

    private final BlockageService blockageService;

    @PostMapping("/preview")
    public BlockageImpactPreviewResponse previewBlockageImpact(@RequestBody BlockageImpactPreviewRequest request) {
        return blockageService.previewBlockageImpact(request);
    }
}