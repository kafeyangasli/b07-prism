package com.github.kafeyangasli.prism.feature.reservation.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.kafeyangasli.prism.feature.blockage.model.BlockageStatus;
import com.github.kafeyangasli.prism.feature.blockage.model.FacilityBlockage;
import com.github.kafeyangasli.prism.feature.blockage.repository.FacilityBlockageRepository;
import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.reservation.dto.ReservationAvailabilityView;
import com.github.kafeyangasli.prism.feature.reservation.dto.ReservationEndTimeView;
import com.github.kafeyangasli.prism.feature.reservation.dto.ReservationSlotState;
import com.github.kafeyangasli.prism.feature.reservation.dto.ReservationSlotView;
import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;

@Service
public class ReservationAvailabilityService {
    public static final LocalTime OPENING_TIME = LocalTime.of(7, 0);
    public static final LocalTime CLOSING_TIME = LocalTime.of(20, 0);
    public static final int SLOT_MINUTES = 30;

    private static final Set<ReservationStatus> BLOCKING_RESERVATION_STATUSES =
            Set.of(ReservationStatus.APPROVED);
    private static final Set<BlockageStatus> BLOCKING_BLOCKAGE_STATUSES =
            Set.of(BlockageStatus.SCHEDULED, BlockageStatus.ACTIVE);

    private final FacilityRepository facilityRepository;
    private final ReservationRepository reservationRepository;
    private final FacilityBlockageRepository blockageRepository;
    private final Clock clock;

    public ReservationAvailabilityService(
            FacilityRepository facilityRepository,
            ReservationRepository reservationRepository,
            FacilityBlockageRepository blockageRepository,
            Clock clock
    ) {
        this.facilityRepository = facilityRepository;
        this.reservationRepository = reservationRepository;
        this.blockageRepository = blockageRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReservationAvailabilityView availability(
            Long facilityId,
            LocalDate date,
            LocalDateTime selectedStart,
            LocalDateTime selectedEnd
    ) {
        Facility facility = activeFacility(facilityId);
        validateDate(date);

        LocalDateTime dayStart = date.atTime(OPENING_TIME);
        LocalDateTime dayEnd = date.atTime(CLOSING_TIME);
        List<Reservation> reservations = reservationRepository.findOverlapping(
                facilityId, dayStart, dayEnd, BLOCKING_RESERVATION_STATUSES);
        List<FacilityBlockage> blockages = blockageRepository.findOverlapping(
                facilityId, dayStart, dayEnd, BLOCKING_BLOCKAGE_STATUSES);

        LocalDateTime now = LocalDateTime.now(clock);
        List<ReservationSlotView> slots = new ArrayList<>();
        for (LocalDateTime start = dayStart; start.isBefore(dayEnd); start = start.plusMinutes(SLOT_MINUTES)) {
            LocalDateTime end = start.plusMinutes(SLOT_MINUTES);
            ReservationSlotState state;
            String reason = null;

            FacilityBlockage blockage = firstOverlappingBlockage(blockages, start, end);
            if (blockage != null) {
                state = ReservationSlotState.BLOCKED;
                reason = blockage.getPublicReason();
            } else if (overlapsReservation(reservations, start, end)) {
                state = ReservationSlotState.RESERVED;
            } else if (start.isBefore(now)) {
                state = ReservationSlotState.PAST;
            } else if (start.isBefore(now.plusMinutes(30))) {
                state = ReservationSlotState.TOO_SOON;
            } else if (selectedStart != null && start.equals(selectedStart)) {
                state = ReservationSlotState.SELECTED;
            } else {
                state = ReservationSlotState.AVAILABLE;
            }
            slots.add(new ReservationSlotView(start, end, state, reason));
        }

        boolean aula = isAula(facility);
        boolean aulaAvailable = slots.stream().allMatch(ReservationSlotView::isSelectable);
        LocalDateTime effectiveStart = selectedStart;
        LocalDateTime effectiveEnd = selectedEnd;
        List<ReservationEndTimeView> endTimes = List.of();

        if (aula && aulaAvailable) {
            effectiveStart = dayStart;
            effectiveEnd = dayEnd;
        } else if (!aula && selectedStart != null) {
            endTimes = contiguousEndTimes(slots, selectedStart, selectedEnd);
            if (endTimes.isEmpty()) {
                effectiveStart = null;
                effectiveEnd = null;
            } else if (endTimes.stream().noneMatch(ReservationEndTimeView::selected)) {
                effectiveEnd = endTimes.getFirst().endAt();
                endTimes = contiguousEndTimes(slots, selectedStart, effectiveEnd);
            }
        }

        return new ReservationAvailabilityView(
                facility, date, aula, aulaAvailable, slots, endTimes, effectiveStart, effectiveEnd);
    }

    @Transactional(readOnly = true)
    public void ensureIntervalAvailable(Long facilityId, LocalDateTime startAt, LocalDateTime endAt) {
        boolean reserved = reservationRepository.existsOverlapping(
                facilityId, startAt, endAt, BLOCKING_RESERVATION_STATUSES, null);
        boolean blocked = blockageRepository.existsOverlapping(
                facilityId, startAt, endAt, BLOCKING_BLOCKAGE_STATUSES, null);
        if (reserved || blocked) {
            throw new BusinessRuleException(
                    "Satu atau lebih slot yang dipilih tidak lagi tersedia. Silakan pilih waktu lain.");
        }
    }

    public LocalDate minimumDate() {
        return LocalDate.now(clock);
    }

    public LocalDate maximumDate() {
        return YearMonth.from(LocalDate.now(clock)).plusMonths(6).atEndOfMonth();
    }

    private Facility activeFacility(Long facilityId) {
        if (facilityId == null) {
            throw new BusinessRuleException("Fasilitas wajib dipilih");
        }
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Fasilitas tidak ditemukan"));
        if (facility.getAdministrativeStatus() != AdministrativeStatus.ACTIVE) {
            throw new BusinessRuleException("Fasilitas tidak aktif dan tidak dapat dipesan");
        }
        return facility;
    }

    private void validateDate(LocalDate date) {
        if (date == null) {
            throw new BusinessRuleException("Tanggal reservasi wajib dipilih");
        }
        if (date.isBefore(minimumDate()) || date.isAfter(maximumDate())) {
            throw new BusinessRuleException("Tanggal reservasi berada di luar rentang yang diizinkan");
        }
    }

    private List<ReservationEndTimeView> contiguousEndTimes(
            List<ReservationSlotView> slots,
            LocalDateTime selectedStart,
            LocalDateTime selectedEnd
    ) {
        List<ReservationEndTimeView> result = new ArrayList<>();
        boolean collecting = false;
        for (ReservationSlotView slot : slots) {
            if (slot.startAt().equals(selectedStart)) {
                collecting = true;
            }
            if (!collecting) {
                continue;
            }
            if (!slot.isSelectable()) {
                break;
            }
            long minutes = java.time.Duration.between(selectedStart, slot.endAt()).toMinutes();
            result.add(new ReservationEndTimeView(
                    slot.endAt(), slot.endAt().equals(selectedEnd), minutes));
        }
        return result;
    }

    private boolean overlapsReservation(
            List<Reservation> reservations,
            LocalDateTime start,
            LocalDateTime end
    ) {
        return reservations.stream().anyMatch(reservation ->
                reservation.getStartAt().isBefore(end) && reservation.getEndAt().isAfter(start));
    }

    private FacilityBlockage firstOverlappingBlockage(
            List<FacilityBlockage> blockages,
            LocalDateTime start,
            LocalDateTime end
    ) {
        return blockages.stream().filter(blockage -> {
            LocalDateTime blockageEnd = blockage.getActualEndAt() != null
                    ? blockage.getActualEndAt()
                    : blockage.getPlannedEndAt();
            return blockage.getStartAt().isBefore(end)
                    && (blockageEnd == null || blockageEnd.isAfter(start));
        }).findFirst().orElse(null);
    }

    private boolean isAula(Facility facility) {
        return facility.getType() != null && facility.getType().equalsIgnoreCase("Aula");
    }
}
