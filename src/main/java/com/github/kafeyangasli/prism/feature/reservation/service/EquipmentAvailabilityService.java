package com.github.kafeyangasli.prism.feature.reservation.service;

import com.github.kafeyangasli.prism.feature.blockage.model.BlockageStatus;
import com.github.kafeyangasli.prism.feature.blockage.repository.FacilityBlockageRepository;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Set;
import java.util.TreeMap;

/** Authoritative half-open inventory calculation. Mutations must hold the Facility lock. */
@Service
@Transactional(readOnly = true)
public class EquipmentAvailabilityService {
    private final ReservationRepository reservations;
    private final FacilityBlockageRepository blockages;
    private final Clock clock;

    public EquipmentAvailabilityService(ReservationRepository reservations,
            FacilityBlockageRepository blockages, Clock clock) {
        this.reservations = reservations;
        this.blockages = blockages;
        this.clock = clock;
    }

    public int available(Facility facility, LocalDateTime start, LocalDateTime end) {
        requireStock(facility);
        validateInterval(start, end);
        if (blockages.existsOverlapping(facility.getId(), start, end,
                Set.of(BlockageStatus.SCHEDULED, BlockageStatus.ACTIVE), null)) return 0;
        return (int) Math.max(0L, facility.getTotalStock() - maximumAllocated(facility, start, end, null));
    }

    public java.util.List<ReservationRepository.EquipmentAllocation> allocations(Facility facility,
            LocalDateTime start, LocalDateTime end) {
        return reservations.findEquipmentAllocations(facility.getId(), start, end, LocalDateTime.now(clock), null);
    }

    public int availableFromAllocations(Facility facility,
            Collection<? extends ReservationRepository.EquipmentAllocation> allocations,
            LocalDateTime start, LocalDateTime end, boolean blocked) {
        requireStock(facility);
        return blocked ? 0 : (int) Math.max(0L, facility.getTotalStock() - maximumConcurrent(allocations, start, end));
    }

    public long maximumAllocated(Facility facility, LocalDateTime start, LocalDateTime end, Long ignoredId) {
        validateInterval(start, end);
        return maximumConcurrent(reservations.findEquipmentAllocations(
                facility.getId(), start, end, LocalDateTime.now(clock), ignoredId), start, end);
    }

    public long maximumFutureAllocated(Facility facility) {
        return maximumAllocated(facility, LocalDateTime.now(clock),
                LocalDateTime.of(9999, 12, 31, 23, 59), null);
    }

    public void ensureFits(Facility facility, LocalDateTime start, LocalDateTime end,
            Integer quantity, Long ignoredId) {
        requireStock(facility);
        validateInterval(start, end);
        if (quantity == null || quantity <= 0) {
            throw new BusinessRuleException("INVALID_QUANTITY", "Jumlah alat wajib berupa bilangan bulat positif.");
        }
        if (blockages.existsOverlapping(facility.getId(), start, end,
                Set.of(BlockageStatus.SCHEDULED, BlockageStatus.ACTIVE), null)) {
            throw new BusinessRuleException("FACILITY_BLOCKED", "Fasilitas diblokir pada interval yang diminta.");
        }
        long available = Math.max(0L, facility.getTotalStock() - maximumAllocated(facility, start, end, ignoredId));
        if (quantity > available) {
            throw new BusinessRuleException("INSUFFICIENT_EQUIPMENT", "Jumlah alat yang tersedia pada waktu tersebut hanya "
                    + available + " unit. Anda meminta " + quantity + " unit.");
        }
    }

    public static long maximumConcurrent(Collection<? extends ReservationRepository.EquipmentAllocation> allocations,
            LocalDateTime start, LocalDateTime end) {
        validateInterval(start, end);
        TreeMap<LocalDateTime, Long> events = new TreeMap<>();
        for (var allocation : allocations) {
            if (!allocation.getStartAt().isBefore(end) || !allocation.getEndAt().isAfter(start)) continue;
            if (allocation.getQuantity() == null || allocation.getQuantity() <= 0) {
                throw new BusinessRuleException("INVALID_EQUIPMENT_DATA", "Jumlah reservasi alat belum valid. Hubungi Admin.");
            }
            LocalDateTime from = allocation.getStartAt().isBefore(start) ? start : allocation.getStartAt();
            LocalDateTime to = allocation.getEndAt().isAfter(end) ? end : allocation.getEndAt();
            events.merge(from, allocation.getQuantity().longValue(), Long::sum);
            events.merge(to, -allocation.getQuantity().longValue(), Long::sum);
        }
        long current = 0, maximum = 0;
        // Grouping equal timestamps applies ends and starts together: adjacent intervals never overlap.
        for (long delta : events.values()) {
            current += delta;
            maximum = Math.max(maximum, current);
        }
        return maximum;
    }

    private static void validateInterval(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || !start.isBefore(end)) {
            throw new BusinessRuleException("INVALID_INTERVAL", "Waktu mulai dan selesai tidak valid.");
        }
    }

    private void requireStock(Facility facility) {
        if (!facility.isEquipment() || facility.getTotalStock() == null || facility.getTotalStock() <= 0) {
            throw new BusinessRuleException("INVALID_EQUIPMENT_STOCK", "Stok alat belum valid. Hubungi Admin.");
        }
    }
}
