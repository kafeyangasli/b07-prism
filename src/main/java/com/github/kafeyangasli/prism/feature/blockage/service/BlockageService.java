package com.github.kafeyangasli.prism.feature.blockage.service;

import com.github.kafeyangasli.prism.feature.administration.service.StaffActorResolver;
import com.github.kafeyangasli.prism.feature.blockage.dto.BlockageImpactPreviewRequest;
import com.github.kafeyangasli.prism.feature.blockage.dto.BlockageImpactPreviewResponse;
import com.github.kafeyangasli.prism.feature.blockage.dto.CreateBlockageRequest;
import com.github.kafeyangasli.prism.feature.blockage.dto.EarlyCompletionRequest;
import com.github.kafeyangasli.prism.feature.blockage.dto.UpdateBlockageRequest;
import com.github.kafeyangasli.prism.feature.blockage.model.BlockageStatus;
import com.github.kafeyangasli.prism.feature.blockage.model.BlockageType;
import com.github.kafeyangasli.prism.feature.blockage.model.FacilityBlockage;
import com.github.kafeyangasli.prism.feature.blockage.repository.FacilityBlockageRepository;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.report.model.Report;
import com.github.kafeyangasli.prism.feature.report.repository.ReportRepository;
import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BlockageService {

    private final FacilityRepository facilityRepository;
    private final ReservationRepository reservationRepository;
    private final FacilityBlockageRepository facilityBlockageRepository;
    private final BlockageTypeService blockageTypeService;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final StaffActorResolver staffActorResolver;

    private final jakarta.persistence.EntityManager entityManager;
    private final java.util.Map<String, Confirmation> confirmations = new java.util.concurrent.ConcurrentHashMap<>();
    private static final List<ReservationStatus> IMPACT_STATUSES = List.of(ReservationStatus.APPROVED, ReservationStatus.PENDING);
    private record Confirmation(Long actorId, String operation, String impact, java.time.Instant expiresAt) {}
    private record UpdatePlan(LocalDateTime endAt, LocalDateTime impactStart, String publicReason) {}

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('PETUGAS', 'ADMIN')")
    public BlockageImpactPreviewResponse previewBlockageImpact(BlockageImpactPreviewRequest request) {
        if (request == null || request.getFacilityId() == null || request.getStartAt() == null) {
            throw new BusinessRuleException("Fasilitas dan waktu mulai wajib diisi untuk melihat dampak blokir.");
        }
        validateInterval(request.getStartAt(), request.getPlannedEndAt());
        Facility facility = facilityRepository.findById(request.getFacilityId())
                .orElseThrow(() -> new ResourceNotFoundException("Fasilitas tidak ditemukan."));
        // Keep count-only previews available. Only a complete, validated request can authorize a mutation.
        BlockageType type = null;
        User actor = null;
        if (request.getBlockageTypeId() != null || request.getPublicReason() != null) {
            validateCreate(request);
            type = blockageTypeService.validateAndGetActiveBlockageType(request.getBlockageTypeId());
            validateReport(request, facility, type);
            actor = currentStaff();
        }
        List<Reservation> impact = reservationRepository.findOverlapping(facility.getId(), request.getStartAt(), request.getPlannedEndAt(), IMPACT_STATUSES);
        var preview = preview(facility, request.getStartAt(), request.getPlannedEndAt(), impact);
        if (actor != null) issueConfirmation(preview, actor, createFingerprint(request, type), impact);
        return preview;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('PETUGAS', 'ADMIN')")
    public BlockageImpactPreviewResponse previewUpdate(Long blockageId, UpdateBlockageRequest request) {
        FacilityBlockage blockage = getBlockageById(blockageId);
        UpdatePlan plan = updatePlan(blockage, request);
        User actor = currentStaff();
        List<Reservation> impact = plan.impactStart() == null ? List.of()
                : reservationRepository.findOverlapping(blockage.getFacility().getId(), plan.impactStart(), plan.endAt(), IMPACT_STATUSES);
        var preview = preview(blockage.getFacility(), plan.impactStart(), plan.endAt(), impact);
        issueConfirmation(preview, actor, updateFingerprint(blockage, request), impact);
        return preview;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @PreAuthorize("hasAnyRole('PETUGAS', 'ADMIN')")
    public FacilityBlockage createBlockage(CreateBlockageRequest request) {
        validateCreate(request);
        requireConfirmation(request.isConfirmed(), request.getConfirmationToken());
        Facility facility = lockFacility(request.getFacilityId());
        BlockageType type = blockageTypeService.validateAndGetActiveBlockageType(request.getBlockageTypeId());
        Report report = validateReport(request, facility, type);
        User actor = currentStaff();
        List<Reservation> impact = lockedImpact(facility.getId(), request.getStartAt(), request.getPlannedEndAt());
        consumeConfirmation(request.getConfirmationToken(), actor, createFingerprint(request, type), impact);
        LocalDateTime now = LocalDateTime.now();
        var blockage = new FacilityBlockage(facility, type, report, request.getStartAt(), request.getPlannedEndAt(),
                request.getStartAt().isAfter(now) ? BlockageStatus.SCHEDULED : BlockageStatus.ACTIVE,
                request.getPublicReason().trim(), request.getInternalNote(), actor);
        FacilityBlockage saved = facilityBlockageRepository.save(blockage);
        processReservationImpact(impact, type.getCode(), blockage.getPublicReason(), actor, now);
        return saved;
    }

    private void validateCreate(CreateBlockageRequest request) {
        if (request == null || request.getFacilityId() == null || request.getFacilityId() <= 0
                || request.getBlockageTypeId() == null || request.getBlockageTypeId() <= 0 || request.getStartAt() == null) {
            throw new BusinessRuleException("Fasilitas, jenis blokir, dan waktu mulai wajib diisi.");
        }
        validateReason(request.getPublicReason());
        validateNote(request.getInternalNote());
        validateInterval(request.getStartAt(), request.getPlannedEndAt());
    }

    private void validateInterval(LocalDateTime start, LocalDateTime end) {
        if (end != null && !start.isBefore(end)) throw new BusinessRuleException("Waktu mulai blokir harus lebih awal dari rencana waktu selesai.");
    }

    private void validateReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 1000) {
            throw new BusinessRuleException("Alasan publik wajib diisi (maksimal 1000 karakter).");
        }
    }

    private void validateNote(String note) {
        if (note != null && note.length() > 2000) throw new BusinessRuleException("Catatan internal maksimal 2000 karakter.");
    }

    private Report validateReport(CreateBlockageRequest request, Facility facility, BlockageType type) {
        if (!"REPAIR".equalsIgnoreCase(type.getCode())) return null;
        if (request.getReportId() == null) throw new BusinessRuleException("Blokir perbaikan harus terhubung dengan laporan fasilitas.");
        Report report = reportRepository.findById(request.getReportId())
                .orElseThrow(() -> new ResourceNotFoundException("Laporan tidak ditemukan."));
        if (!report.getFacility().getId().equals(facility.getId())) {
            throw new BusinessRuleException("Fasilitas pada laporan tidak sesuai dengan fasilitas yang diblokir.");
        }
        return report;
    }

    private Facility lockFacility(Long id) {
        return facilityRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fasilitas tidak ditemukan."));
    }

    private List<Reservation> lockedImpact(Long facilityId, LocalDateTime start, LocalDateTime end) {
        List<Reservation> candidates = reservationRepository.findOverlapping(facilityId, start, end, IMPACT_STATUSES);
        // Refresh under row locks as cancellation/lifecycle operations can change reservation status independently.
        candidates.stream().sorted(java.util.Comparator.comparing(Reservation::getId)).forEach(r ->
                entityManager.refresh(r, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE));
        return candidates.stream().filter(r -> IMPACT_STATUSES.contains(r.getStatus()))
                .filter(r -> r.getEndAt().isAfter(start) && (end == null || r.getStartAt().isBefore(end))).toList();
    }

    private void processReservationImpact(List<Reservation> impact, String typeCode, String reason, User actor, LocalDateTime now) {
        String reasonCode = mapReasonCode(typeCode);
        for (Reservation reservation : impact) {
            if (reservation.getStatus() == ReservationStatus.APPROVED) {
                reservation.setStatus(ReservationStatus.CANCELLED);
                reservation.setCancelledBy(actor);
                reservation.setCancelledAt(now);
            } else if (reservation.getStatus() == ReservationStatus.PENDING) {
                reservation.setStatus(ReservationStatus.REJECTED);
                reservation.setProcessedBy(actor);
                reservation.setProcessedAt(now);
            }
            reservation.setReasonCode(reasonCode);
            reservation.setReasonDetail(reason);
            reservationRepository.save(reservation);
        }
    }

    private BlockageImpactPreviewResponse preview(Facility facility, LocalDateTime start, LocalDateTime end, List<Reservation> impact) {
        long approved = impact.stream().filter(r -> r.getStatus() == ReservationStatus.APPROVED).count();
        long pending = impact.stream().filter(r -> r.getStatus() == ReservationStatus.PENDING).count();
        return new BlockageImpactPreviewResponse(facility.getId(), facility.getName(), start, end, approved, pending, approved + pending);
    }

    private synchronized void issueConfirmation(BlockageImpactPreviewResponse preview, User actor, String operation, List<Reservation> impact) {
        java.time.Instant now = java.time.Instant.now();
        confirmations.entrySet().removeIf(e -> !e.getValue().expiresAt().isAfter(now));
        if (confirmations.size() >= 1000) throw new BusinessRuleException("Terlalu banyak pratinjau aktif. Coba lagi nanti.");
        String token = java.util.UUID.randomUUID().toString();
        java.time.Instant expires = now.plusSeconds(15 * 60);
        confirmations.put(token, new Confirmation(actor.getId(), operation, impactFingerprint(impact), expires));
        preview.setConfirmationToken(token);
        preview.setExpiresAt(expires);
    }

    private void requireConfirmation(boolean confirmed, String token) {
        if (!confirmed || token == null || token.isBlank()) {
            throw new BusinessRuleException("CONFIRMATION_REQUIRED", "Lihat pratinjau dampak lalu konfirmasikan secara eksplisit sebelum menyimpan.");
        }
    }

    private void consumeConfirmation(String token, User actor, String operation, List<Reservation> impact) {
        Confirmation confirmation = confirmations.get(token);
        if (confirmation == null || !confirmation.expiresAt().isAfter(java.time.Instant.now())) {
            throw new BusinessRuleException("PREVIEW_EXPIRED", "Pratinjau kedaluwarsa atau sudah digunakan. Lihat pratinjau dan konfirmasikan kembali.");
        }
        if (!confirmation.actorId().equals(actor.getId()) || !confirmation.operation().equals(operation)) {
            throw new BusinessRuleException("PREVIEW_CHANGED", "Data atau pengguna berbeda dari pratinjau. Lihat pratinjau dan konfirmasikan kembali.");
        }
        if (!confirmation.impact().equals(impactFingerprint(impact))) {
            confirmations.remove(token, confirmation);
            throw new BusinessRuleException("IMPACT_CHANGED", "Dampak reservasi berubah. Tidak ada data yang disimpan. Lihat pratinjau dan konfirmasikan kembali.");
        }
        if (!confirmations.remove(token, confirmation)) {
            throw new BusinessRuleException("PREVIEW_EXPIRED", "Pratinjau sudah digunakan. Konfirmasikan kembali.");
        }
    }

    private String createFingerprint(CreateBlockageRequest request, BlockageType type) {
        return fingerprint("CREATE", request.getFacilityId(), request.getBlockageTypeId(), type.getCode(), request.getReportId(),
                request.getStartAt(), request.getPlannedEndAt(), request.getPublicReason().trim(), request.getInternalNote());
    }

    private String updateFingerprint(FacilityBlockage blockage, UpdateBlockageRequest request) {
        return fingerprint("UPDATE", blockage.getId(), blockage.getFacility().getId(), blockage.getBlockageType().getId(),
                blockage.getBlockageType().getCode(), blockage.getStartAt(), blockage.getPlannedEndAt(), blockage.getStatus(),
                blockage.getPublicReason(), blockage.getInternalNote(), request.getPlannedEndAt(),
                request.getOpenEnded(), request.getPublicReason(), request.getInternalNote());
    }

    private String impactFingerprint(List<Reservation> impact) {
        return fingerprint(impact.stream().map(r -> fingerprint(r.getId(), r.getStatus(), r.getStartAt(), r.getEndAt())).sorted().toList());
    }

    private String fingerprint(Object... values) {
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            try (var output = new java.io.DataOutputStream(bytes)) {
                for (Object value : values) {
                    if (value == null) { output.writeInt(-1); continue; }
                    byte[] encoded = value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    output.writeInt(encoded.length);
                    output.write(encoded);
                }
            }
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("Gagal menyiapkan konfirmasi.", e);
        }
    }
    private String mapReasonCode(String typeCode) {
        if (typeCode == null) return "BLOCKAGE";
        return switch (typeCode.toUpperCase(java.util.Locale.ROOT)) {
            case "REPAIR" -> "BLOCKAGE_REPAIR";
            case "PLANNED_MAINTENANCE" -> "BLOCKAGE_PLANNED_MAINTENANCE";
            case "FORCE_MAJEURE" -> "BLOCKAGE_FORCE_MAJEURE";
            default -> "BLOCKAGE_" + typeCode.toUpperCase(java.util.Locale.ROOT);
        };
    }

    // Step 16 & 17 — FR-25 Blockage Lifecycle Reconciliation
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public int reconcileBlockageLifecycle() {
        LocalDateTime now = LocalDateTime.now();
        int count = 0;

        // 1. SCHEDULED -> ACTIVE
        List<FacilityBlockage> scheduledToActivate = facilityBlockageRepository.findByStatusOrderByStartAtAsc(BlockageStatus.SCHEDULED);
        for (FacilityBlockage blockage : scheduledToActivate) {
            lockFacility(blockage.getFacility().getId());
            entityManager.refresh(blockage, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (blockage.getStatus() == BlockageStatus.SCHEDULED && !blockage.getStartAt().isAfter(now)) {
                blockage.setStatus(BlockageStatus.ACTIVE);
                facilityBlockageRepository.save(blockage);
                count++;
            }
        }

        // 2. ACTIVE -> COMPLETED (for fixed-end blockages)
        List<FacilityBlockage> activeToComplete = facilityBlockageRepository.findByStatusOrderByStartAtAsc(BlockageStatus.ACTIVE);
        for (FacilityBlockage blockage : activeToComplete) {
            lockFacility(blockage.getFacility().getId());
            entityManager.refresh(blockage, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
            if (blockage.getStatus() == BlockageStatus.ACTIVE && blockage.getPlannedEndAt() != null && !blockage.getPlannedEndAt().isAfter(now)) {
                blockage.setStatus(BlockageStatus.COMPLETED);
                facilityBlockageRepository.save(blockage);
                count++;
            }
        }

        return count;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @PreAuthorize("hasAnyRole('PETUGAS', 'ADMIN')")
    public FacilityBlockage updateOrExtendBlockage(Long blockageId, UpdateBlockageRequest request) {
        if (request == null) throw new BusinessRuleException("Data perubahan wajib diisi.");
        requireConfirmation(request.isConfirmed(), request.getConfirmationToken());
        FacilityBlockage blockage = getBlockageById(blockageId);
        lockFacility(blockage.getFacility().getId());
        entityManager.refresh(blockage, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        UpdatePlan plan = updatePlan(blockage, request);
        User actor = currentStaff();
        List<Reservation> impact = plan.impactStart() == null ? List.of()
                : lockedImpact(blockage.getFacility().getId(), plan.impactStart(), plan.endAt());
        consumeConfirmation(request.getConfirmationToken(), actor, updateFingerprint(blockage, request), impact);
        blockage.setPlannedEndAt(plan.endAt());
        blockage.setPublicReason(plan.publicReason());
        if (request.getInternalNote() != null) blockage.setInternalNote(request.getInternalNote());
        FacilityBlockage saved = facilityBlockageRepository.save(blockage);
        processReservationImpact(impact, blockage.getBlockageType().getCode(), blockage.getPublicReason(), actor, LocalDateTime.now());
        return saved;
    }

    private UpdatePlan updatePlan(FacilityBlockage blockage, UpdateBlockageRequest request) {
        if (request == null) throw new BusinessRuleException("Data perubahan wajib diisi.");
        if (blockage.getStatus() == BlockageStatus.COMPLETED || blockage.getStatus() == BlockageStatus.CANCELLED) {
            throw new BusinessRuleException("Blokir yang telah selesai atau dibatalkan tidak dapat diperbarui.");
        }
        if (Boolean.TRUE.equals(request.getOpenEnded()) && request.getPlannedEndAt() != null) {
            throw new BusinessRuleException("Pilih rencana selesai atau tanpa batas waktu, bukan keduanya.");
        }
        LocalDateTime oldEnd = blockage.getPlannedEndAt();
        LocalDateTime end = Boolean.TRUE.equals(request.getOpenEnded()) ? null
                : request.getPlannedEndAt() != null ? request.getPlannedEndAt() : oldEnd;
        validateInterval(blockage.getStartAt(), end);
        String reason = request.getPublicReason() == null ? blockage.getPublicReason() : request.getPublicReason().trim();
        validateReason(reason);
        validateNote(request.getInternalNote());
        // An already open-ended interval covers everything; shortening never restores reservations.
        LocalDateTime impactStart = oldEnd != null && (end == null || end.isAfter(oldEnd)) ? oldEnd : null;
        return new UpdatePlan(end, impactStart, reason);
    }
    // Step 20 — FR-26 Early Completion
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @PreAuthorize("hasAnyRole('PETUGAS', 'ADMIN')")
    public FacilityBlockage earlyCompleteBlockage(Long blockageId, EarlyCompletionRequest request) {
        FacilityBlockage blockage = facilityBlockageRepository.findById(blockageId)
                .orElseThrow(() -> new ResourceNotFoundException("Blokir fasilitas dengan ID " + blockageId + " tidak ditemukan."));

        if (blockage.getStatus() == BlockageStatus.COMPLETED || blockage.getStatus() == BlockageStatus.CANCELLED) {
            throw new BusinessRuleException("Blokir fasilitas sudah selesai atau dibatalkan.");
        }

        if (request.getEarlyCompletionReason() == null || request.getEarlyCompletionReason().trim().isEmpty()) {
            throw new BusinessRuleException("Alasan penyelesaian lebih awal wajib diisi.");
        }

        if (request.getEarlyCompletionReason().length() > 1000) throw new BusinessRuleException("Alasan penyelesaian maksimal 1000 karakter.");
        lockFacility(blockage.getFacility().getId());
        entityManager.refresh(blockage, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (blockage.getStatus() == BlockageStatus.COMPLETED || blockage.getStatus() == BlockageStatus.CANCELLED) {
            throw new BusinessRuleException("Blokir fasilitas sudah selesai atau dibatalkan.");
        }

        User endedBy = currentStaff();

        LocalDateTime now = LocalDateTime.now();

        blockage.setStatus(BlockageStatus.COMPLETED);
        blockage.setActualEndAt(now);
        blockage.setEndedBy(endedBy);
        blockage.setEarlyCompletionReason(request.getEarlyCompletionReason().trim());

        // Note: As specified by SRS FR-26, previously cancelled/rejected reservations are NOT restored automatically.

        return facilityBlockageRepository.save(blockage);
    }

    // Step 21 — FR-27 Availability across multiple blockages
    @Transactional(readOnly = true)
    public boolean isFacilityBlocked(Long facilityId, LocalDateTime startAt, LocalDateTime endAt) {
        List<BlockageStatus> activeStatuses = List.of(BlockageStatus.SCHEDULED, BlockageStatus.ACTIVE);
        return facilityBlockageRepository.existsOverlapping(facilityId, startAt, endAt, activeStatuses, null);
    }

    @Transactional(readOnly = true)
    public List<FacilityBlockage> getEffectiveOverlappingBlockages(Long facilityId, LocalDateTime startAt, LocalDateTime endAt) {
        List<BlockageStatus> activeStatuses = List.of(BlockageStatus.SCHEDULED, BlockageStatus.ACTIVE);
        return facilityBlockageRepository.findOverlapping(facilityId, startAt, endAt, activeStatuses);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('PETUGAS', 'ADMIN')")
    public FacilityBlockage getBlockageById(Long id) {
        return facilityBlockageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Blokir fasilitas dengan ID " + id + " tidak ditemukan."));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('PETUGAS', 'ADMIN')")
    public List<FacilityBlockage> getAllBlockages() {
        return facilityBlockageRepository.findAll();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('PETUGAS', 'ADMIN')")
    public List<Facility> getStaffFacilities() {
        return facilityRepository.findAll(org.springframework.data.domain.Sort.by("name"));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('PETUGAS', 'ADMIN')")
    public List<BlockageType> getActiveTypes() {
        return blockageTypeService.getActiveBlockageTypes();
    }

    private User currentStaff() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Autentikasi diperlukan.");
        }
        long actorId = staffActorResolver.resolveId(authentication.getName());
        return userRepository.findById(actorId)
                .orElseThrow(() -> new ResourceNotFoundException("Akun staf tidak ditemukan."));
    }
}
