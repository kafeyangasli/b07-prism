package com.github.kafeyangasli.prism.feature.facility;

import com.github.kafeyangasli.prism.feature.facility.dto.FacilityDto;
import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.service.FacilityService;
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
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void testFacilityManagementAndSearch() {
        User admin = new User("Admin Fac", "adminfac@campus.ac.id", passwordEncoder.encode("admin123"), Role.ADMIN, AccountStatus.ACTIVE);
        User savedAdmin = userRepository.save(admin);

        FacilityDto dto = new FacilityDto();
        dto.setCode("LAB-01");
        dto.setName("Laboratorium Komputer 1");
        dto.setType("LAB");
        dto.setLocation("Tembalang");
        dto.setCapacity(50);
        dto.setDescription("Lab Komputer");

        Facility created = facilityService.createFacility(savedAdmin.getId(), dto);
        assertNotNull(created.getId());
        assertEquals("LAB-01", created.getCode());
        assertEquals(AdministrativeStatus.ACTIVE, created.getAdministrativeStatus());

        // Negative capacity test
        FacilityDto invalidDto = new FacilityDto();
        invalidDto.setCode("LAB-02");
        invalidDto.setName("Lab 2");
        invalidDto.setType("LAB");
        invalidDto.setLocation("Tembalang");
        invalidDto.setCapacity(0);
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
}
