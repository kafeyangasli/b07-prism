package com.github.kafeyangasli.prism.feature.blockage.service;

import com.github.kafeyangasli.prism.feature.blockage.dto.CreateBlockageTypeRequest;
import com.github.kafeyangasli.prism.feature.blockage.dto.UpdateBlockageTypeRequest;
import com.github.kafeyangasli.prism.feature.blockage.model.BlockageType;
import com.github.kafeyangasli.prism.feature.blockage.repository.BlockageTypeRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.util.UUID;
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

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void baselineMigrationSeedsMissingTypesAndPreservesExistingRows(int existingMask) throws Exception {
        List<String> codes = List.of("REPAIR", "PLANNED_MAINTENANCE", "FORCE_MAJEURE");
        Timestamp originalTimestamp = Timestamp.valueOf("2026-01-01 00:00:00");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:blockage_seed_" + UUID.randomUUID() + ";MODE=MySQL")) {
            // Execute the original table definition so the seed test follows the real schema.
            String schema = new ClassPathResource(
                    "db/migration/V20260917010346__create_prism_core_tables.sql")
                    .getContentAsString(StandardCharsets.UTF_8);
            int start = schema.indexOf("CREATE TABLE blockage_types (");
            int end = schema.indexOf(");", start) + 2;
            assertTrue(start >= 0 && end > start);
            ScriptUtils.executeSqlScript(connection,
                    new ByteArrayResource(schema.substring(start, end).getBytes(StandardCharsets.UTF_8)));

            try (var insert = connection.prepareStatement("""
                    INSERT INTO blockage_types (code, name, description, is_active, created_at, updated_at)
                    VALUES (?, 'Existing name', 'Existing description', FALSE, ?, ?)
                    """)) {
                for (int i = 0; i < codes.size(); i++) {
                    if ((existingMask & (1 << i)) == 0) continue;
                    insert.setString(1, codes.get(i));
                    insert.setTimestamp(2, originalTimestamp);
                    insert.setTimestamp(3, originalTimestamp);
                    insert.executeUpdate();
                }
                insert.setString(1, "CUSTOM_TYPE");
                insert.setTimestamp(2, originalTimestamp);
                insert.setTimestamp(3, originalTimestamp);
                insert.executeUpdate();
            }

            var migration = new ClassPathResource("db/migration/V20261010210000__seed_blockage_types.sql");
            ScriptUtils.executeSqlScript(connection, migration);
            ScriptUtils.executeSqlScript(connection, migration);

            try (var query = connection.prepareStatement("SELECT * FROM blockage_types WHERE code = ?")) {
                for (int i = 0; i < codes.size(); i++) {
                    query.setString(1, codes.get(i));
                    try (var rows = query.executeQuery()) {
                        assertTrue(rows.next(), "Missing baseline code: " + codes.get(i));
                        if ((existingMask & (1 << i)) != 0) {
                            assertEquals("Existing name", rows.getString("name"));
                            assertEquals("Existing description", rows.getString("description"));
                            assertFalse(rows.getBoolean("is_active"));
                            assertEquals(originalTimestamp, rows.getTimestamp("created_at"));
                            assertEquals(originalTimestamp, rows.getTimestamp("updated_at"));
                        } else {
                            assertTrue(rows.getBoolean("is_active"));
                            assertNotNull(rows.getString("name"));
                            assertNotNull(rows.getTimestamp("created_at"));
                            assertNotNull(rows.getTimestamp("updated_at"));
                        }
                        assertFalse(rows.next(), "Duplicate baseline code: " + codes.get(i));
                    }
                }
            }
            try (var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM blockage_types")) {
                assertTrue(rows.next());
                assertEquals(4, rows.getInt(1), "Three baseline types and the existing custom type must remain");
            }
        }
    }
}
