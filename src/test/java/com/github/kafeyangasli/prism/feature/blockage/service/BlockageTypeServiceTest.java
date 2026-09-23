package com.github.kafeyangasli.prism.feature.blockage.service;

import com.github.kafeyangasli.prism.feature.blockage.dto.CreateBlockageTypeRequest;
import com.github.kafeyangasli.prism.feature.blockage.dto.UpdateBlockageTypeRequest;
import com.github.kafeyangasli.prism.feature.blockage.model.BlockageType;
import com.github.kafeyangasli.prism.feature.blockage.repository.BlockageTypeRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BlockageTypeServiceTest {

    @Mock
    private BlockageTypeRepository blockageTypeRepository;

    @InjectMocks
    private BlockageTypeService blockageTypeService;

    private BlockageType blockageType;

    @BeforeEach
    void setUp() {
        blockageType = new BlockageType("REPAIR", "Repair & Maintenance", "Facility repair");
        ReflectionTestUtils.setField(blockageType, "id", 1L);
    }

    @Test
    void createBlockageType_Success() {
        CreateBlockageTypeRequest request = new CreateBlockageTypeRequest();
        request.setCode("REPAIR");
        request.setName("Repair & Maintenance");
        request.setDescription("Facility repair");

        when(blockageTypeRepository.existsByCodeIgnoreCase("REPAIR")).thenReturn(false);
        when(blockageTypeRepository.save(any(BlockageType.class))).thenAnswer(inv -> inv.getArgument(0));

        BlockageType result = blockageTypeService.createBlockageType(request);

        assertNotNull(result);
        assertEquals("REPAIR", result.getCode());
        assertEquals("Repair & Maintenance", result.getName());
        assertTrue(result.isActive());
    }

    @Test
    void createBlockageType_DuplicateCode_ThrowsException() {
        CreateBlockageTypeRequest request = new CreateBlockageTypeRequest();
        request.setCode("REPAIR");
        request.setName("Repair & Maintenance");

        when(blockageTypeRepository.existsByCodeIgnoreCase("REPAIR")).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> blockageTypeService.createBlockageType(request));
    }

    @Test
    void createBlockageType_MissingCodeOrName_ThrowsException() {
        CreateBlockageTypeRequest request = new CreateBlockageTypeRequest();
        request.setCode("   ");
        request.setName("Repair");

        assertThrows(BusinessRuleException.class, () -> blockageTypeService.createBlockageType(request));
    }

    @Test
    void updateBlockageType_Success() {
        UpdateBlockageTypeRequest request = new UpdateBlockageTypeRequest();
        request.setName("Updated Repair Name");
        request.setDescription("Updated description");

        when(blockageTypeRepository.findById(1L)).thenReturn(Optional.of(blockageType));
        when(blockageTypeRepository.save(any(BlockageType.class))).thenAnswer(inv -> inv.getArgument(0));

        BlockageType result = blockageTypeService.updateBlockageType(1L, request);

        assertEquals("Updated Repair Name", result.getName());
        assertEquals("Updated description", result.getDescription());
    }

    @Test
    void updateBlockageType_NotFound_ThrowsException() {
        UpdateBlockageTypeRequest request = new UpdateBlockageTypeRequest();
        request.setName("Updated");

        when(blockageTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> blockageTypeService.updateBlockageType(99L, request));
    }

    @Test
    void deactivateBlockageType_Success() {
        when(blockageTypeRepository.findById(1L)).thenReturn(Optional.of(blockageType));
        when(blockageTypeRepository.save(any(BlockageType.class))).thenAnswer(inv -> inv.getArgument(0));

        BlockageType result = blockageTypeService.deactivateBlockageType(1L);

        assertFalse(result.isActive());
        assertEquals("REPAIR", result.getCode()); // code remains intact, not hard deleted
    }

    @Test
    void deactivateBlockageType_NotFound_ThrowsException() {
        when(blockageTypeRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> blockageTypeService.deactivateBlockageType(99L));
    }

    @Test
    void getAllBlockageTypes_Success() {
        when(blockageTypeRepository.findAll()).thenReturn(List.of(blockageType));

        List<BlockageType> types = blockageTypeService.getAllBlockageTypes();

        assertEquals(1, types.size());
        assertEquals("REPAIR", types.get(0).getCode());
    }

    @Test
    void getBlockageTypeByCode_Success() {
        when(blockageTypeRepository.findByCodeIgnoreCase("REPAIR")).thenReturn(Optional.of(blockageType));

        BlockageType result = blockageTypeService.getBlockageTypeByCode("repair");

        assertNotNull(result);
        assertEquals("REPAIR", result.getCode());
    }

    @Test
    void getBlockageTypeByCode_NotFound_ThrowsException() {
        when(blockageTypeRepository.findByCodeIgnoreCase("UNKNOWN")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> blockageTypeService.getBlockageTypeByCode("UNKNOWN"));
    }

    @Test
    void validateAndGetActiveBlockageType_Active_Success() {
        when(blockageTypeRepository.findById(1L)).thenReturn(Optional.of(blockageType));

        BlockageType result = blockageTypeService.validateAndGetActiveBlockageType(1L);

        assertNotNull(result);
        assertTrue(result.isActive());
    }

    @Test
    void validateAndGetActiveBlockageType_Inactive_ThrowsException() {
        blockageType.setActive(false);
        when(blockageTypeRepository.findById(1L)).thenReturn(Optional.of(blockageType));

        assertThrows(BusinessRuleException.class, () -> blockageTypeService.validateAndGetActiveBlockageType(1L));
    }

    @Test
    void deleteBlockageType_ThrowsBusinessRuleException() {
        when(blockageTypeRepository.findById(1L)).thenReturn(Optional.of(blockageType));

        assertThrows(BusinessRuleException.class, () -> blockageTypeService.deleteBlockageType(1L));
    }
}
