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
import org.springframework.data.domain.Sort;
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
            throw new BusinessRuleException("Email wajib diisi.");
        }
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new BusinessRuleException("Kata sandi wajib diisi.");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessRuleException("Nama lengkap wajib diisi.");
        }

        String normalizedEmail = dto.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BusinessRuleException("Email sudah terdaftar.");
        }

        String passwordHash = passwordEncoder.encode(dto.getPassword());
        User user = new User(dto.getName().trim(), normalizedEmail, passwordHash, Role.PENGGUNA, AccountStatus.PENDING);
        return userRepository.save(user);
    }

    @Override
    public User verifyUser(Long adminId, Long userId) {
        User admin = validateAdmin(adminId);
        User target = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pengguna dengan ID " + userId + " tidak ditemukan."));

        if (target.getAccountStatus() != AccountStatus.PENDING) {
            throw new BusinessRuleException("Hanya akun berstatus menunggu yang dapat diverifikasi.");
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
                .orElseThrow(() -> new ResourceNotFoundException("Pengguna dengan ID " + userId + " tidak ditemukan."));

        if (target.getAccountStatus() != AccountStatus.PENDING) {
            throw new BusinessRuleException("Hanya akun berstatus menunggu yang dapat ditolak.");
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
            throw new BusinessRuleException("Email wajib diisi.");
        }
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new BusinessRuleException("Kata sandi wajib diisi.");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessRuleException("Nama lengkap wajib diisi.");
        }
        if (dto.getRole() == null || (dto.getRole() != Role.PENGGUNA && dto.getRole() != Role.PETUGAS)) {
            throw new BusinessRuleException("Admin hanya dapat membuat akun Pengguna atau Petugas.");
        }

        String normalizedEmail = dto.getEmail().trim().toLowerCase(java.util.Locale.ROOT);
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new BusinessRuleException("Email sudah terdaftar.");
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
                .orElseThrow(() -> new ResourceNotFoundException("Pengguna dengan ID " + userId + " tidak ditemukan."));

        target.setAccountStatus(AccountStatus.INACTIVE);
        return userRepository.save(target);
    }

    @Override
    public User activateUser(Long adminId, Long userId) {
        User admin = validateAdmin(adminId);
        User target = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Pengguna dengan ID " + userId + " tidak ditemukan."));

        if (target.getAccountStatus() != AccountStatus.INACTIVE) {
            throw new BusinessRuleException("Hanya akun tidak aktif yang dapat diaktifkan.");
        }

        target.setAccountStatus(AccountStatus.ACTIVE);
        if (target.getVerifiedBy() == null) {
            target.setVerifiedBy(admin);
            target.setVerifiedAt(LocalDateTime.now(clock));
        }
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
    public List<User> findUsers(String search, Role role, AccountStatus status, String sort, String direction) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        String sortProperty = switch (sort == null ? "createdAt" : sort) {
            case "name" -> "name";
            case "email" -> "email";
            case "role" -> "role";
            case "status" -> "accountStatus";
            default -> "createdAt";
        };
        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        return userRepository.findForManagement(
                normalizedSearch, role, status, Sort.by(sortDirection, sortProperty));
    }

    @Override
    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pengguna dengan ID " + id + " tidak ditemukan."));
    }

    @Override
    @Transactional(readOnly = true)
    public User findByEmail(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Pengguna dengan email " + email + " tidak ditemukan."));
    }

    private User validateAdmin(Long adminId) {
        if (adminId == null) {
            throw new BusinessRuleException("ID admin wajib tersedia.");
        }
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new ResourceNotFoundException("Admin dengan ID " + adminId + " tidak ditemukan."));
        if (admin.getRole() != Role.ADMIN) {
            throw new BusinessRuleException("Tindakan ini hanya dapat dilakukan oleh Admin.");
        }
        return admin;
    }
}
