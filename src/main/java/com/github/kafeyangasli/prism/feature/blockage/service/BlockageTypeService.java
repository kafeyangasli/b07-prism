package com.github.kafeyangasli.prism.feature.blockage.service;

import com.github.kafeyangasli.prism.feature.blockage.dto.CreateBlockageTypeRequest;
import com.github.kafeyangasli.prism.feature.blockage.dto.UpdateBlockageTypeRequest;
import com.github.kafeyangasli.prism.feature.blockage.model.BlockageType;
import com.github.kafeyangasli.prism.feature.blockage.repository.BlockageTypeRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BlockageTypeService {

    private final BlockageTypeRepository blockageTypeRepository;

    @Transactional
    public BlockageType createBlockageType(CreateBlockageTypeRequest request) {
        if (request.getCode() == null || request.getCode().trim().isEmpty()) {
            throw new BusinessRuleException("Kode jenis blokir wajib diisi.");
        }
        if (request.getName() == null || request.getName().trim().isEmpty()) {
            throw new BusinessRuleException("Nama jenis blokir wajib diisi.");
        }

        String normalizedCode = request.getCode().trim().toUpperCase(java.util.Locale.ROOT);
        if (blockageTypeRepository.existsByCodeIgnoreCase(normalizedCode)) {
            throw new BusinessRuleException("Kode jenis blokir sudah digunakan: " + normalizedCode);
        }

        BlockageType blockageType = new BlockageType(normalizedCode, request.getName().trim(), request.getDescription());
        return blockageTypeRepository.save(blockageType);
    }

    @Transactional
    public BlockageType updateBlockageType(Long id, UpdateBlockageTypeRequest request) {
        BlockageType blockageType = blockageTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jenis blokir dengan ID " + id + " tidak ditemukan."));

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            blockageType.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            blockageType.setDescription(request.getDescription());
        }
        if (request.getActive() != null) {
            blockageType.setActive(request.getActive());
        }

        return blockageTypeRepository.save(blockageType);
    }

    @Transactional
    public BlockageType deactivateBlockageType(Long id) {
        BlockageType blockageType = blockageTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jenis blokir dengan ID " + id + " tidak ditemukan."));

        blockageType.setActive(false);
        return blockageTypeRepository.save(blockageType);
    }

    @Transactional(readOnly = true)
    public List<BlockageType> getAllBlockageTypes() {
        return blockageTypeRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<BlockageType> getActiveBlockageTypes() {
        return blockageTypeRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public BlockageType getBlockageTypeById(Long id) {
        return blockageTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Jenis blokir dengan ID " + id + " tidak ditemukan."));
    }

    @Transactional(readOnly = true)
    public BlockageType getBlockageTypeByCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new BusinessRuleException("Kode jenis blokir wajib diisi.");
        }
        String normalizedCode = code.trim().toUpperCase(java.util.Locale.ROOT);
        return blockageTypeRepository.findByCodeIgnoreCase(normalizedCode)
                .orElseThrow(() -> new ResourceNotFoundException("Jenis blokir dengan kode " + normalizedCode + " tidak ditemukan."));
    }

    @Transactional(readOnly = true)
    public BlockageType validateAndGetActiveBlockageType(Long id) {
        BlockageType blockageType = getBlockageTypeById(id);
        if (!blockageType.isActive()) {
            throw new BusinessRuleException("Jenis blokir tidak aktif tidak dapat digunakan: " + blockageType.getCode());
        }
        return blockageType;
    }

    @Transactional
    public void deleteBlockageType(Long id) {
        BlockageType blockageType = getBlockageTypeById(id);
        throw new BusinessRuleException("Jenis blokir yang telah dibuat tidak dapat dihapus permanen. Nonaktifkan jenis tersebut: " + blockageType.getCode());
    }
}
