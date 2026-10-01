package com.github.kafeyangasli.prism.feature.facility.service;

import com.github.kafeyangasli.prism.feature.facility.dto.FacilityTypeDto;
import com.github.kafeyangasli.prism.feature.facility.model.FacilityType;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityTypeRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@Transactional
public class FacilityTypeService {

    private final FacilityTypeRepository facilityTypeRepository;

    public FacilityTypeService(FacilityTypeRepository facilityTypeRepository) {
        this.facilityTypeRepository = facilityTypeRepository;
    }

    @Transactional(readOnly = true)
    public List<FacilityType> getAll() {
        return facilityTypeRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public List<FacilityType> getActive() {
        return facilityTypeRepository.findByActiveTrueOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public FacilityType getById(Long id) {
        return facilityTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tipe fasilitas dengan ID " + id + " tidak ditemukan."));
    }

    public FacilityType create(FacilityTypeDto dto) {
        String code = requireCode(dto.getCode());
        String name = requireName(dto.getName());
        rejectDuplicateCode(code, null);
        rejectDuplicateName(name, null);
        return facilityTypeRepository.save(new FacilityType(code, name, dto.getDescription()));
    }

    public FacilityType update(Long id, FacilityTypeDto dto) {
        FacilityType type = getById(id);
        String name = requireName(dto.getName());
        rejectDuplicateName(name, id);
        type.updateDetails(name, dto.getDescription());
        return facilityTypeRepository.save(type);
    }

    public FacilityType deactivate(Long id) {
        FacilityType type = getById(id);
        type.deactivate();
        return facilityTypeRepository.save(type);
    }

    public FacilityType activate(Long id) {
        FacilityType type = getById(id);
        type.activate();
        return facilityTypeRepository.save(type);
    }

    private String requireCode(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException("Kode tipe fasilitas wajib diisi.");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String requireName(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException("Nama tipe fasilitas wajib diisi.");
        }
        return value.trim();
    }

    private void rejectDuplicateCode(String code, Long currentId) {
        facilityTypeRepository.findByCodeIgnoreCase(code)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new BusinessRuleException("Kode tipe fasilitas sudah digunakan.");
                });
    }

    private void rejectDuplicateName(String name, Long currentId) {
        facilityTypeRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(currentId))
                .ifPresent(existing -> {
                    throw new BusinessRuleException("Nama tipe fasilitas sudah digunakan.");
                });
    }
}
