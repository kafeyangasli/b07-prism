package com.github.kafeyangasli.prism.feature.user.service;

import com.github.kafeyangasli.prism.feature.user.dto.*;
import com.github.kafeyangasli.prism.feature.user.model.*;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import jakarta.validation.Validator;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@Transactional
public class AccountSettingsService {
    private final UserRepository users;
    private final ReservationRepository reservations;
    private final PasswordEncoder encoder;
    private final Clock clock;
    private final Validator validator;
    private final AvatarStorage avatars;

    public AccountSettingsService(UserRepository users, ReservationRepository reservations,
                                  PasswordEncoder encoder, Clock clock, Validator validator, AvatarStorage avatars) {
        this.users = users;
        this.reservations = reservations;
        this.encoder = encoder;
        this.clock = clock;
        this.validator = validator;
        this.avatars = avatars;
    }

    @Transactional(readOnly = true)
    public AccountSettingsView currentAccount() {
        User user = requireActive(users.findByEmailIgnoreCase(authenticatedEmail())
                .orElseThrow(() -> new AccessDeniedException("Akun tidak tersedia.")));
        return new AccountSettingsView(user.getName(), user.getEmail(), user.getRole(), user.getAccountStatus(),
                user.getProfilePicturePath() != null);
    }

    public void updateAvatar(MultipartFile upload) {
        User user = lockedAccount();
        String previous = user.getProfilePicturePath();
        String name = avatars.storeForTransaction(upload);
        user.setProfilePicturePath(name);
        users.save(user);
        avatars.deleteAfterCommit(previous);
    }

    public void removeAvatar() {
        User user = lockedAccount();
        String previous = user.getProfilePicturePath();
        user.setProfilePicturePath(null);
        users.save(user);
        avatars.deleteAfterCommit(previous);
    }

    @Transactional(readOnly = true)
    public byte[] currentAvatar() {
        User user = requireActive(users.findByEmailIgnoreCase(authenticatedEmail())
                .orElseThrow(() -> new AccessDeniedException("Akun tidak tersedia.")));
        return user.getProfilePicturePath() == null ? null : avatars.load(user.getProfilePicturePath());
    }

    public void updateProfile(AccountProfileForm form) {
        validate(form);
        User user = lockedAccount();
        user.setName(form.getName());
        users.save(user);
    }

    public void changePassword(AccountPasswordForm form) {
        validate(form);
        if (!form.getNewPassword().equals(form.getConfirmPassword())) {
            throw new BusinessRuleException("Konfirmasi kata sandi baru tidak cocok.");
        }
        if (form.getNewPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessRuleException("Kata sandi baru maksimal 72 byte UTF-8.");
        }
        User user = lockedAccount();
        verifyPassword(form.getCurrentPassword(), user);
        if (encoder.matches(form.getNewPassword(), user.getPasswordHash())) {
            throw new BusinessRuleException("Kata sandi baru harus berbeda dari kata sandi saat ini.");
        }
        user.setPasswordHash(encoder.encode(form.getNewPassword()));
        users.save(user);
    }

    public void deactivateAccount(AccountDeletionForm form) {
        User user = lockedAccount();
        if (user.getRole() != Role.PENGGUNA) {
            throw new AccessDeniedException("Hanya Pengguna yang dapat menonaktifkan akun sendiri.");
        }
        validate(form);
        verifyPassword(form.getCurrentPassword(), user);
        // Approval must acquire this same user row and recheck ACTIVE. This
        // transaction never takes facility/reservation locks after the user lock.
        if (reservations.countActiveApproved(user.getId(), ReservationStatus.APPROVED,
                LocalDateTime.now(clock)) > 0) {
            throw new BusinessRuleException("Selesaikan reservasi disetujui yang masih berlangsung atau akan datang sebelum menonaktifkan akun.");
        }
        user.setAccountStatus(AccountStatus.INACTIVE);
        users.save(user);
    }

    private User lockedAccount() {
        return requireActive(users.findByEmailForUpdate(authenticatedEmail())
                .orElseThrow(() -> new AccessDeniedException("Akun tidak tersedia.")));
    }

    private User requireActive(User user) {
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new AccessDeniedException("Akun sudah tidak aktif.");
        }
        return user;
    }

    private String authenticatedEmail() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Autentikasi diperlukan.");
        }
        return authentication.getName().trim().toLowerCase(Locale.ROOT);
    }

    private void verifyPassword(String password, User user) {
        if (password == null || password.getBytes(StandardCharsets.UTF_8).length > 72
                || !encoder.matches(password, user.getPasswordHash())) {
            throw new BusinessRuleException("Kata sandi saat ini tidak sesuai.");
        }
    }

    private void validate(Object form) {
        validator.validate(form).stream().map(violation -> violation.getMessage()).sorted().findFirst()
                .ifPresent(message -> { throw new BusinessRuleException(message); });
    }
}
