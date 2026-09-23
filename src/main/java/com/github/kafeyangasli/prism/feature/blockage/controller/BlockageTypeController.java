package com.github.kafeyangasli.prism.feature.blockage.controller;

import com.github.kafeyangasli.prism.feature.blockage.dto.CreateBlockageTypeRequest;
import com.github.kafeyangasli.prism.feature.blockage.dto.UpdateBlockageTypeRequest;
import com.github.kafeyangasli.prism.feature.blockage.model.BlockageType;
import com.github.kafeyangasli.prism.feature.blockage.service.BlockageTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/blockage-types")
@RequiredArgsConstructor
public class BlockageTypeController {

    private final BlockageTypeService blockageTypeService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BlockageType createBlockageType(@RequestBody CreateBlockageTypeRequest request) {
        return blockageTypeService.createBlockageType(request);
    }

    @PutMapping("/{id}")
    public BlockageType updateBlockageType(@PathVariable Long id, @RequestBody UpdateBlockageTypeRequest request) {
        return blockageTypeService.updateBlockageType(id, request);
    }

    @PatchMapping("/{id}/deactivate")
    public BlockageType deactivateBlockageType(@PathVariable Long id) {
        return blockageTypeService.deactivateBlockageType(id);
    }

    @GetMapping
    public List<BlockageType> getAllBlockageTypes() {
        return blockageTypeService.getAllBlockageTypes();
    }

    @GetMapping("/{id}")
    public BlockageType getBlockageTypeById(@PathVariable Long id) {
        return blockageTypeService.getBlockageTypeById(id);
    }
}
