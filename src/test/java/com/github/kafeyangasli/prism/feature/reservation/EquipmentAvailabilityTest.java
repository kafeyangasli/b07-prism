package com.github.kafeyangasli.prism.feature.reservation;

import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository.EquipmentAllocation;
import com.github.kafeyangasli.prism.feature.reservation.service.EquipmentAvailabilityService;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class EquipmentAvailabilityTest {
    static final LocalDateTime DAY = LocalDateTime.of(2030, 1, 8, 0, 0);
    record Allocation(LocalDateTime startAt, LocalDateTime endAt, Integer quantity) implements EquipmentAllocation {
        public LocalDateTime getStartAt() { return startAt; }
        public LocalDateTime getEndAt() { return endAt; }
        public Integer getQuantity() { return quantity; }
    }
    static Allocation allocation(int start, int end, int quantity) {
        return new Allocation(DAY.plusMinutes(start), DAY.plusMinutes(end), quantity);
    }
    long peak(List<Allocation> allocations, int start, int end) {
        return EquipmentAvailabilityService.maximumConcurrent(allocations, DAY.plusMinutes(start), DAY.plusMinutes(end));
    }
    @Test void emptyInventoryHasNoAllocation() { assertThat(peak(List.of(), 420, 660)).isZero(); }
    @Test void singlePartiallyOverlappingReservation() { assertThat(peak(List.of(allocation(420, 600, 10)), 540, 660)).isEqualTo(10); }
    @Test void fullyOverlappingReservationsAddQuantities() { assertThat(peak(List.of(allocation(420, 600, 10), allocation(420, 600, 5)), 420, 600)).isEqualTo(15); }
    @Test void adjacentIntervalsAreNotSummed() { assertThat(peak(List.of(allocation(420, 540, 10), allocation(540, 660, 8)), 420, 660)).isEqualTo(10); }
    @Test void nonOverlappingAllocationsUseMaximumInsteadOfSum() { assertThat(peak(List.of(allocation(420, 480, 10), allocation(540, 600, 8)), 420, 660)).isEqualTo(10); }
    @Test void promptExampleAtExactEndBoundaryLeavesTenUnits() { assertThat(peak(List.of(allocation(420, 600, 10), allocation(480, 540, 5)), 540, 660)).isEqualTo(10); }
    @Test void partialOverlapUsesBusiestSubinterval() { assertThat(peak(List.of(allocation(420, 600, 10), allocation(480, 540, 5)), 510, 600)).isEqualTo(15); }
    @Test void allocationsOutsideQueryAreIgnored() { assertThat(peak(List.of(allocation(420, 480, 20), allocation(600, 660, 20)), 480, 600)).isZero(); }
    @Test void multipleBoundaryEventsAndClipping() { assertThat(peak(List.of(allocation(420, 480, 12), allocation(480, 540, 10), allocation(480, 510, 5), allocation(510, 600, 8)), 450, 570)).isEqualTo(18); }
    @Test void accumulationCannotOverflowInteger() { assertThat(peak(List.of(allocation(420, 600, Integer.MAX_VALUE), allocation(420, 600, Integer.MAX_VALUE)), 420, 600)).isEqualTo(4294967294L); }
    @Test void invalidLegacyQuantityFailsClosed() { assertThatThrownBy(() -> peak(List.of(new Allocation(DAY.plusHours(7),DAY.plusHours(8),null)),420,480)).hasMessageContaining("belum valid"); }
}
