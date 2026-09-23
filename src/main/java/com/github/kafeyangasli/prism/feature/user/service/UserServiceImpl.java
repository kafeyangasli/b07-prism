package com.github.kafeyangasli.prism.feature.user.service;

import com.github.kafeyangasli.prism.feature.user.dto.AdminCreateUserDto;
import com.github.kafeyangasli.prism.feature.user.dto.UserRegistrationDto;
import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public User registerUser(UserRegistrationDto dto) {
        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new BusinessRuleException("Email is required.");
        }
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new BusinessRuleException("Password is required.");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessRuleException("Name is required.");
        }

        String normalizedEmail = dto.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BusinessRuleException("Email already exists.");
        }

        String passwordHash = passwordEncoder.encode(dto.getPassword());
        User user = new User(dto.getName().trim(), normalizedEmail, passwordHash, Role.PENGGUNA, AccountStatus.PENDING);
        return userRepository.save(user);
    }

    @Override
    public User verifyUser(Long adminId, Long userId) {
        User admin = validateAdmin(adminId);
        User target = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (target.getAccountStatus() != AccountStatus.PENDING) {
            throw new BusinessRuleException("Only pending users can be verified.");
        }

        target.setAccountStatus(AccountStatus.ACTIVE);
        target.setVerifiedBy(admin);
        target.setVerifiedAt(LocalDateTime.now(clock));
        return userRepository.save(target);
    }

    @Override
    public User rejectUser(Long adminId, Long userId) {
        User admin = validateAdmin(adminId);
        User target = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (target.getAccountStatus() != AccountStatus.PENDING) {
            throw new BusinessRuleException("Only pending users can be rejected.");
        }

        target.setAccountStatus(AccountStatus.REJECTED);
        target.setVerifiedBy(admin);
        target.setVerifiedAt(LocalDateTime.now(clock));
        return userRepository.save(target);
    }

    @Override
    public User createUserByAdmin(Long adminId, AdminCreateUserDto dto) {
        User admin = validateAdmin(adminId);
        if (dto.getEmail() == null || dto.getEmail().isBlank()) {
            throw new BusinessRuleException("Email is required.");
        }
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new BusinessRuleException("Password is required.");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessRuleException("Name is required.");
        }
        if (dto.getRole() == null || (dto.getRole() != Role.PENGGUNA && dto.getRole() != Role.PETUGAS)) {
            throw new BusinessRuleException("Admin can only create PENGGUNA or PETUGAS accounts.");
        }

        String normalizedEmail = dto.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BusinessRuleException("Email already exists.");
        }

        String passwordHash = passwordEncoder.encode(dto.getPassword());
        User user = new User(dto.getName().trim(), normalizedEmail, passwordHash, dto.getRole(), AccountStatus.ACTIVE);
        user.setVerifiedBy(admin);
        user.setVerifiedAt(LocalDateTime.now(clock));
        return userRepository.save(user);
    }

    @Override
    public User deactivateUser(Long adminId, Long userId) {
        validateAdmin(adminId);
        User target = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        target.setAccountStatus(AccountStatus.INACTIVE);
        return userRepository.save(target);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findUsersByStatus(AccountStatus status) {
        return userRepository.findByAccountStatusOrderByCreatedAtAsc(status);
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));
    }

    private User validateAdmin(Long adminId) {
        if (adminId == null) {
            throw new BusinessRuleException("Admin ID is required.");
        }
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found with id: " + adminId));
        if (admin.getRole() != Role.ADMIN) {
            throw new BusinessRuleException("Only Admin can perform this action.");
        }
        return admin;
    }
}
