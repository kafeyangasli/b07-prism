package com.github.kafeyangasli.prism.feature.blockage.service;

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

    @Transactional(readOnly = true)
    public BlockageImpactPreviewResponse previewBlockageImpact(BlockageImpactPreviewRequest request) {
        if (request.getFacilityId() == null) {
            throw new BusinessRuleException("Facility ID is required for impact preview");
        }
        if (request.getStartAt() == null) {
            throw new BusinessRuleException("Start time is required for impact preview");
        }
        if (request.getPlannedEndAt() != null && !request.getStartAt().isBefore(request.getPlannedEndAt())) {
            throw new BusinessRuleException("Facility blockage startAt must be before plannedEndAt");
        }

        Facility facility = facilityRepository.findById(request.getFacilityId())
                .orElseThrow(() -> new ResourceNotFoundException("Facility not found with id: " + request.getFacilityId()));

        List<ReservationStatus> targetStatuses = List.of(ReservationStatus.APPROVED, ReservationStatus.PENDING);

        List<Reservation> overlapping = reservationRepository.findOverlapping(
                facility.getId(),
                request.getStartAt(),
                request.getPlannedEndAt(),
                targetStatuses
        );

        long approvedCount = overlapping.stream()
                .filter(r -> r.getStatus() == ReservationStatus.APPROVED)
                .count();

        long pendingCount = overlapping.stream()
                .filter(r -> r.getStatus() == ReservationStatus.PENDING)
                .count();

        return new BlockageImpactPreviewResponse(
                facility.getId(),
                facility.getName(),
                request.getStartAt(),
                request.getPlannedEndAt(),
                approvedCount,
                pendingCount,
                approvedCount + pendingCount
        );
    }

    @Transactional
    public FacilityBlockage createBlockage(CreateBlockageRequest request, Long creatorUserId) {
        if (request.getFacilityId() == null) {
            throw new BusinessRuleException("Facility ID is required");
        }
        if (request.getBlockageTypeId() == null) {
            throw new BusinessRuleException("Blockage type ID is required");
        }
        if (request.getStartAt() == null) {
            throw new BusinessRuleException("Start time is required");
        }
        if (request.getPublicReason() == null || request.getPublicReason().trim().isEmpty()) {
            throw new BusinessRuleException("Public reason is required");
        }
        if (request.getPlannedEndAt() != null && !request.getStartAt().isBefore(request.getPlannedEndAt())) {
            throw new BusinessRuleException("Facility blockage startAt must be before plannedEndAt");
        }

        // 1. Acquire pessimistic write lock on the Facility row (FR-24 & Locking Strategy)
        Facility facility = facilityRepository.findByIdForUpdate(request.getFacilityId())
                .orElseThrow(() -> new ResourceNotFoundException("Facility not found with id: " + request.getFacilityId()));

        // 2. Validate blockage type (must exist and be active)
        BlockageType blockageType = blockageTypeService.validateAndGetActiveBlockageType(request.getBlockageTypeId());

        // 3. Handle REPAIR linkage rule
        Report report = null;
        if ("REPAIR".equalsIgnoreCase(blockageType.getCode())) {
            if (request.getReportId() == null) {
                throw new BusinessRuleException("A repair blockage must reference a facility report");
            }
            report = reportRepository.findById(request.getReportId())
                    .orElseThrow(() -> new ResourceNotFoundException("Report not found with id: " + request.getReportId()));
            if (!report.getFacility().getId().equals(facility.getId())) {
                throw new BusinessRuleException("Report facility does not match the blockage facility");
            }
        }

        User creator = userRepository.findById(creatorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Creator user not found with id: " + creatorUserId));

        LocalDateTime now = LocalDateTime.now();
        BlockageStatus initialStatus = request.getStartAt().isAfter(now) ? BlockageStatus.SCHEDULED : BlockageStatus.ACTIVE;

        FacilityBlockage blockage = new FacilityBlockage(
                facility,
                blockageType,
                report,
                request.getStartAt(),
                request.getPlannedEndAt(),
                initialStatus,
                request.getPublicReason().trim(),
                request.getInternalNote(),
                creator
        );

        FacilityBlockage savedBlockage = facilityBlockageRepository.save(blockage);

        // 4. Process overlapping reservations (Step 15 - FR-24 Impact)
        processReservationImpact(facility.getId(), request.getStartAt(), request.getPlannedEndAt(), blockageType.getCode(), request.getPublicReason().trim(), creator, now);

        return savedBlockage;
    }

    private void processReservationImpact(Long facilityId, LocalDateTime startAt, LocalDateTime endAt,
                                           String typeCode, String publicReason, User actor, LocalDateTime now) {
        List<ReservationStatus> targetStatuses = List.of(ReservationStatus.APPROVED, ReservationStatus.PENDING);
        List<Reservation> overlappingReservations = reservationRepository.findOverlapping(facilityId, startAt, endAt, targetStatuses);

        String reasonCode = mapReasonCode(typeCode);

        for (Reservation reservation : overlappingReservations) {
            if (reservation.getStatus() == ReservationStatus.APPROVED) {
                reservation.setStatus(ReservationStatus.CANCELLED);
                reservation.setCancelledBy(actor);
                reservation.setCancelledAt(now);
                reservation.setReasonCode(reasonCode);
                reservation.setReasonDetail(publicReason);
            } else if (reservation.getStatus() == ReservationStatus.PENDING) {
                reservation.setStatus(ReservationStatus.REJECTED);
                reservation.setProcessedBy(actor);
                reservation.setProcessedAt(now);
                reservation.setReasonCode(reasonCode);
                reservation.setReasonDetail(publicReason);
            }
            reservationRepository.save(reservation);
        }
    }

    private String mapReasonCode(String typeCode) {
        if (typeCode == null) return "BLOCKAGE";
        switch (typeCode.toUpperCase(java.util.Locale.ROOT)) {
            case "REPAIR":
                return "BLOCKAGE_REPAIR";
            case "PLANNED_MAINTENANCE":
                return "BLOCKAGE_PLANNED_MAINTENANCE";
            case "FORCE_MAJEURE":
                return "BLOCKAGE_FORCE_MAJEURE";
            default:
                return "BLOCKAGE_" + typeCode.toUpperCase(java.util.Locale.ROOT);
        }
    }

    // Step 16 & 17 — FR-25 Blockage Lifecycle Reconciliation
    @Transactional
    public int reconcileBlockageLifecycle() {
        LocalDateTime now = LocalDateTime.now();
        int count = 0;

        // 1. SCHEDULED -> ACTIVE
        List<FacilityBlockage> scheduledToActivate = facilityBlockageRepository.findByStatusOrderByStartAtAsc(BlockageStatus.SCHEDULED);
        for (FacilityBlockage blockage : scheduledToActivate) {
            if (!blockage.getStartAt().isAfter(now)) {
                blockage.setStatus(BlockageStatus.ACTIVE);
                facilityBlockageRepository.save(blockage);
                count++;
            }
        }

        // 2. ACTIVE -> COMPLETED (for fixed-end blockages)
        List<FacilityBlockage> activeToComplete = facilityBlockageRepository.findByStatusOrderByStartAtAsc(BlockageStatus.ACTIVE);
        for (FacilityBlockage blockage : activeToComplete) {
            if (blockage.getPlannedEndAt() != null && !blockage.getPlannedEndAt().isAfter(now)) {
                blockage.setStatus(BlockageStatus.COMPLETED);
                facilityBlockageRepository.save(blockage);
                count++;
            }
        }

        return count;
    }

    // Step 18 & 19 — FR-26 Blockage Update / Extension
    @Transactional
    public FacilityBlockage updateOrExtendBlockage(Long blockageId, UpdateBlockageRequest request, Long actingUserId) {
        FacilityBlockage blockage = facilityBlockageRepository.findById(blockageId)
                .orElseThrow(() -> new ResourceNotFoundException("Blockage not found with id: " + blockageId));

        if (blockage.getStatus() == BlockageStatus.COMPLETED || blockage.getStatus() == BlockageStatus.CANCELLED) {
            throw new BusinessRuleException("Cannot update blockage in terminal state: " + blockage.getStatus());
        }

        User actor = userRepository.findById(actingUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + actingUserId));

        LocalDateTime now = LocalDateTime.now();

        // Handle extension if plannedEndAt is extended
        if (request.getPlannedEndAt() != null) {
            if (blockage.getStartAt().isAfter(request.getPlannedEndAt())) {
                throw new BusinessRuleException("Facility blockage startAt must be before plannedEndAt");
            }

            LocalDateTime extensionStart = blockage.getPlannedEndAt() != null ? blockage.getPlannedEndAt() : blockage.getStartAt();

            if (request.getPlannedEndAt().isAfter(extensionStart)) {
                // Lock facility before extending (FR-26 Extension)
                facilityRepository.findByIdForUpdate(blockage.getFacility().getId());

                // Process newly affected reservations in extension window
                processReservationImpact(
                        blockage.getFacility().getId(),
                        extensionStart,
                        request.getPlannedEndAt(),
                        blockage.getBlockageType().getCode(),
                        request.getPublicReason() != null ? request.getPublicReason().trim() : blockage.getPublicReason(),
                        actor,
                        now
                );
            }
            blockage.setPlannedEndAt(request.getPlannedEndAt());
        }

        if (request.getPublicReason() != null && !request.getPublicReason().trim().isEmpty()) {
            blockage.setPublicReason(request.getPublicReason().trim());
        }
        if (request.getInternalNote() != null) {
            blockage.setInternalNote(request.getInternalNote());
        }

        return facilityBlockageRepository.save(blockage);
    }

    // Step 20 — FR-26 Early Completion
    @Transactional
    public FacilityBlockage earlyCompleteBlockage(Long blockageId, EarlyCompletionRequest request, Long actingUserId) {
        FacilityBlockage blockage = facilityBlockageRepository.findById(blockageId)
                .orElseThrow(() -> new ResourceNotFoundException("Blockage not found with id: " + blockageId));

        if (blockage.getStatus() == BlockageStatus.COMPLETED || blockage.getStatus() == BlockageStatus.CANCELLED) {
            throw new BusinessRuleException("Blockage is already in terminal state: " + blockage.getStatus());
        }

        if (request.getEarlyCompletionReason() == null || request.getEarlyCompletionReason().trim().isEmpty()) {
            throw new BusinessRuleException("Early completion reason is required");
        }

        User endedBy = userRepository.findById(actingUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + actingUserId));

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
    public FacilityBlockage getBlockageById(Long id) {
        return facilityBlockageRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Blockage not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<FacilityBlockage> getAllBlockages() {
        return facilityBlockageRepository.findAll();
    }
}