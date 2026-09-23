package com.github.kafeyangasli.prism.feature.user;

import com.github.kafeyangasli.prism.feature.user.dto.UserRegistrationDto;
import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.feature.user.service.UserService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:prismtest;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void testSelfRegistrationAndVerification() {
        UserRegistrationDto regDto = new UserRegistrationDto();
        regDto.setName("Mahasiswa Test");
        regDto.setEmail("STUDENT@campus.ac.id");
        regDto.setPassword("secret123");

        User registered = userService.registerUser(regDto);
        assertNotNull(registered.getId());
        assertEquals("student@campus.ac.id", registered.getEmail());
        assertEquals(Role.PENGGUNA, registered.getRole());
        assertEquals(AccountStatus.PENDING, registered.getAccountStatus());
        assertTrue(passwordEncoder.matches("secret123", registered.getPasswordHash()));

        // Duplicate registration check (case insensitive)
        UserRegistrationDto dupDto = new UserRegistrationDto();
        dupDto.setName("Other");
        dupDto.setEmail("student@CAMPUS.ac.id");
        dupDto.setPassword("other123");
        assertThrows(BusinessRuleException.class, () -> userService.registerUser(dupDto));

        // Create Admin directly for verification
        User admin = new User("Admin Test", "admin@campus.ac.id", passwordEncoder.encode("admin123"), Role.ADMIN, AccountStatus.ACTIVE);
        admin = userRepository.save(admin);

        // Verify user
        User verified = userService.verifyUser(admin.getId(), registered.getId());
        assertEquals(AccountStatus.ACTIVE, verified.getAccountStatus());
        assertEquals(admin.getId(), verified.getVerifiedBy().getId());
        assertNotNull(verified.getVerifiedAt());

        // Deactivate user
        User deactivated = userService.deactivateUser(admin.getId(), verified.getId());
        assertEquals(AccountStatus.INACTIVE, deactivated.getAccountStatus());
    }
}
