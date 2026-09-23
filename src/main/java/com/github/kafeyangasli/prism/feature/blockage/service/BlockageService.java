package com.github.kafeyangasli.prism.feature.blockage.service;

import com.github.kafeyangasli.prism.feature.blockage.dto.BlockageImpactPreviewRequest;
import com.github.kafeyangasli.prism.feature.blockage.dto.BlockageImpactPreviewResponse;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BlockageService {

    private final FacilityRepository facilityRepository;
    private final ReservationRepository reservationRepository;

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
}