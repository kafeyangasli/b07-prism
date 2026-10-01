package com.github.kafeyangasli.prism.feature.facility;

import com.github.kafeyangasli.prism.feature.facility.dto.FacilityDto;
import com.github.kafeyangasli.prism.feature.facility.dto.FacilityTypeDto;
import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.model.FacilityType;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.facility.service.FacilityService;
import com.github.kafeyangasli.prism.feature.facility.service.FacilityTypeService;
import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:prismtest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class FacilityServiceTest {

    @Autowired
    private FacilityService facilityService;

    @Autowired
    private FacilityTypeService facilityTypeService;

    @Autowired
    private FacilityRepository facilityRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void testFacilityManagementAndSearch() {
        User admin = new User("Admin Fac", "adminfac@campus.ac.id", passwordEncoder.encode("admin123"), Role.ADMIN, AccountStatus.ACTIVE);
        User savedAdmin = userRepository.save(admin);

        FacilityType lab = createType("LAB", "Laboratorium");
        FacilityDto dto = facilityDto("LAB-01", "Laboratorium Komputer 1", lab, "Tembalang", 50);

        Facility created = facilityService.createFacility(savedAdmin.getId(), dto);
        assertNotNull(created.getId());
        assertEquals("LAB-01", created.getCode());
        assertEquals(AdministrativeStatus.ACTIVE, created.getAdministrativeStatus());

        // Negative capacity test
        FacilityDto invalidDto = facilityDto("LAB-02", "Lab 2", lab, "Tembalang", 0);
        assertThrows(BusinessRuleException.class, () -> facilityService.createFacility(savedAdmin.getId(), invalidDto));

        // Search & filter test
        List<Facility> searchResults = facilityService.searchCatalogue("LAB", "Tembalang", 40);
        assertEquals(1, searchResults.size());
        assertEquals("LAB-01", searchResults.get(0).getCode());

        // Deactivate facility
        Facility deactivated = facilityService.deactivateFacility(savedAdmin.getId(), created.getId());
        assertEquals(AdministrativeStatus.INACTIVE, deactivated.getAdministrativeStatus());
        assertEquals(0, facilityService.getPublicCatalogue().size());
    }

    @Test
    void facilityTypeLifecycleAndCaseInsensitiveUniqueness() {
        FacilityType created = createType(" room ", "Ruang Kelas");
        assertEquals("ROOM", created.getCode());
        assertTrue(created.isActive());

        assertThrows(BusinessRuleException.class, () -> createType("RoOm", "Nama lain"));
        assertThrows(BusinessRuleException.class, () -> createType("OTHER", "ruang kelas"));

        assertFalse(facilityTypeService.deactivate(created.getId()).isActive());
        assertTrue(facilityTypeService.activate(created.getId()).isActive());
    }

    @Test
    void inactiveTypeAssignmentRulesPreserveExistingRelation() {
        User admin = saveAdmin("admin-types@campus.ac.id");
        FacilityType original = createType("CLASS", "Kelas");
        FacilityType other = createType("HALL", "Aula");
        Facility facility = facilityService.createFacility(admin.getId(),
                facilityDto("R-101", "Ruang 101", original, "Gedung A", 30));

        facilityTypeService.deactivate(original.getId());
        FacilityDto retaining = facilityDto("R-101", "Ruang 101", original, "Gedung A", 30);
        Facility retained = facilityService.updateFacility(admin.getId(), facility.getId(), retaining);
        assertEquals(original.getId(), retained.getFacilityType().getId());

        facilityTypeService.deactivate(other.getId());
        assertThrows(BusinessRuleException.class, () -> facilityService.createFacility(admin.getId(),
                facilityDto("R-102", "Ruang 102", other, "Gedung A", 30)));

        FacilityDto switching = facilityDto("R-101", "Ruang 101", other, "Gedung A", 30);
        assertThrows(BusinessRuleException.class,
                () -> facilityService.updateFacility(admin.getId(), facility.getId(), switching));

        FacilityDto noType = facilityDto("R-103", "Ruang 103", original, "Gedung A", 30);
        noType.setFacilityTypeId(null);
        assertThrows(BusinessRuleException.class, () -> facilityService.createFacility(admin.getId(), noType));
    }

    @Test
    void relationSearchUsesIntersectionAndRepositoryContractsRemainAvailable() {
        User admin = saveAdmin("admin-search@campus.ac.id");
        FacilityType lab = createType("LAB", "Laboratorium");
        FacilityType hall = createType("HALL", "Aula");
        Facility matching = facilityService.createFacility(admin.getId(),
                facilityDto("LAB-A", "Lab A", lab, "Tembalang", 50));
        facilityService.createFacility(admin.getId(),
                facilityDto("LAB-B", "Lab B", lab, "Pleburan", 80));
        facilityService.createFacility(admin.getId(),
                facilityDto("HALL-A", "Aula A", hall, "Tembalang", 100));

        List<Facility> results = facilityService.searchCatalogue("lab", "tembalang", 40);
        assertEquals(List.of(matching.getId()), results.stream().map(Facility::getId).toList());
        assertEquals(matching.getId(), facilityRepository.findByIdForUpdate(matching.getId()).orElseThrow().getId());
        assertEquals(1, facilityRepository.findForRecap(null, "Laboratorium", "Tembalang").size());
    }

    private FacilityType createType(String code, String name) {
        FacilityTypeDto dto = new FacilityTypeDto();
        dto.setCode(code);
        dto.setName(name);
        dto.setDescription("Deskripsi " + name);
        return facilityTypeService.create(dto);
    }

    private User saveAdmin(String email) {
        return userRepository.save(new User("Admin Facility", email,
                passwordEncoder.encode("admin123"), Role.ADMIN, AccountStatus.ACTIVE));
    }

    private FacilityDto facilityDto(String code, String name, FacilityType type,
                                    String location, int capacity) {
        FacilityDto dto = new FacilityDto();
        dto.setCode(code);
        dto.setName(name);
        dto.setFacilityTypeId(type.getId());
        dto.setLocation(location);
        dto.setCapacity(capacity);
        dto.setDescription("Deskripsi " + name);
        return dto;
    }
}
