package com.github.kafeyangasli.prism.feature.administration.service;

import com.github.kafeyangasli.prism.feature.administration.dto.FacilityRecapRow;
import com.github.kafeyangasli.prism.feature.administration.dto.RecapFilter;
import com.github.kafeyangasli.prism.feature.administration.dto.RecapResult;
import com.github.kafeyangasli.prism.feature.blockage.model.BlockageStatus;
import com.github.kafeyangasli.prism.feature.blockage.model.FacilityBlockage;
import com.github.kafeyangasli.prism.feature.blockage.repository.FacilityBlockageRepository;
import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.report.repository.ReportRepository;
import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AdministrationReportService {

    private static final LocalTime OPEN_TIME = LocalTime.of(7, 0);
    private static final LocalTime CLOSE_TIME = LocalTime.of(20, 0);
    private static final int SLOT_MINUTES = 30;

    private final FacilityRepository facilityRepository;
    private final ReservationRepository reservationRepository;
    private final FacilityBlockageRepository blockageRepository;
    private final ReportRepository reportRepository;
    private final Clock clock;

    public AdministrationReportService(FacilityRepository facilityRepository,
                                       ReservationRepository reservationRepository,
                                       FacilityBlockageRepository blockageRepository,
                                       ReportRepository reportRepository,
                                       Clock clock) {
        this.facilityRepository = facilityRepository;
        this.reservationRepository = reservationRepository;
        this.blockageRepository = blockageRepository;
        this.reportRepository = reportRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public RecapResult generate(RecapFilter rawFilter) {
        RecapFilter filter = validate(rawFilter == null ? null : rawFilter.normalized());
        LocalDateTime periodStart = filter.startDate().atStartOfDay();
        LocalDateTime periodEnd = filter.endDate().plusDays(1).atStartOfDay();
        List<Facility> facilities = facilityRepository.findForRecap(
                filter.facilityId(), filter.facilityType(), filter.location());

        if (facilities.isEmpty()) {
            return new RecapResult(filter, List.of(), 0, 0, 0, LocalDateTime.now(clock));
        }

        List<Long> facilityIds = facilities.stream().map(Facility::getId).toList();
        List<Reservation> reservations = reservationRepository.findForOccupancy(
                EnumSet.of(ReservationStatus.APPROVED, ReservationStatus.COMPLETED),
                periodStart, periodEnd, facilityIds);
        List<FacilityBlockage> blockages = blockageRepository.findForCapacity(
                facilityIds, periodStart, periodEnd, BlockageStatus.CANCELLED);

        Map<Long, List<Reservation>> reservationsByFacility = groupReservations(reservations);
        Map<Long, List<FacilityBlockage>> blockagesByFacility = groupBlockages(blockages);
        Map<Long, Long> issuesByFacility = new HashMap<>();
        reportRepository.countIssuesByFacility(facilityIds, periodStart, periodEnd)
                .forEach(row -> issuesByFacility.put(row.getFacilityId(), row.getIssueCount()));

        List<FacilityRecapRow> rows = new ArrayList<>();
        long totalApproved = 0;
        long totalBookable = 0;
        long totalIssues = 0;
        for (Facility facility : facilities) {
            SlotCounts counts = countSlots(facility, filter.startDate(), filter.endDate(),
                    reservationsByFacility.getOrDefault(facility.getId(), List.of()),
                    blockagesByFacility.getOrDefault(facility.getId(), List.of()));
            long issueCount = issuesByFacility.getOrDefault(facility.getId(), 0L);
            BigDecimal percent = counts.bookable == 0
                    ? BigDecimal.ZERO
                    : BigDecimal.valueOf(counts.approved * 100.0 / counts.bookable)
                            .setScale(2, RoundingMode.HALF_UP);
            rows.add(new FacilityRecapRow(facility.getId(), facility.getCode(), facility.getName(),
                    facility.getType(), facility.getLocation(), counts.approved,
                    counts.bookable, percent, issueCount));
            totalApproved += counts.approved;
            totalBookable += counts.bookable;
            totalIssues += issueCount;
        }
        return new RecapResult(filter, List.copyOf(rows), totalApproved, totalBookable,
                totalIssues, LocalDateTime.now(clock));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<Facility> facilitiesForFilter() {
        return facilityRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(Facility::getName))
                .toList();
    }

    private RecapFilter validate(RecapFilter filter) {
        if (filter == null || filter.startDate() == null || filter.endDate() == null) {
            throw new BusinessRuleException("INVALID_REPORT_PERIOD", "Periode rekap wajib diisi");
        }
        if (filter.endDate().isBefore(filter.startDate())) {
            throw new BusinessRuleException("INVALID_REPORT_PERIOD",
                    "Tanggal akhir tidak boleh sebelum tanggal awal");
        }
        if (filter.startDate().plusYears(5).isBefore(filter.endDate())) {
            throw new BusinessRuleException("INVALID_REPORT_PERIOD",
                    "Rentang rekap maksimal lima tahun");
        }
        return filter;
    }

    private SlotCounts countSlots(Facility facility, LocalDate startDate, LocalDate endDate,
                                  List<Reservation> reservations,
                                  List<FacilityBlockage> blockages) {
        Set<LocalDateTime> approvedSlots = new HashSet<>();
        long bookableSlots = 0;
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY
                    || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            for (LocalDateTime slotStart = date.atTime(OPEN_TIME);
                 slotStart.isBefore(date.atTime(CLOSE_TIME));
                 slotStart = slotStart.plusMinutes(SLOT_MINUTES)) {
                LocalDateTime slotEnd = slotStart.plusMinutes(SLOT_MINUTES);
                boolean blocked = overlapsAnyBlockage(slotStart, slotEnd, blockages);
                if (facility.getAdministrativeStatus() == AdministrativeStatus.ACTIVE && !blocked) {
                    bookableSlots++;
                }
                if (overlapsAnyReservation(slotStart, slotEnd, reservations)) {
                    approvedSlots.add(slotStart);
                }
            }
        }
        return new SlotCounts(approvedSlots.size(), bookableSlots);
    }

    private boolean overlapsAnyReservation(LocalDateTime slotStart, LocalDateTime slotEnd,
                                           List<Reservation> reservations) {
        return reservations.stream().anyMatch(r ->
                r.getStartAt().isBefore(slotEnd) && r.getEndAt().isAfter(slotStart));
    }

    private boolean overlapsAnyBlockage(LocalDateTime slotStart, LocalDateTime slotEnd,
                                       List<FacilityBlockage> blockages) {
        return blockages.stream().anyMatch(b -> {
            LocalDateTime effectiveEnd = b.getActualEndAt() != null
                    ? b.getActualEndAt() : b.getPlannedEndAt();
            return b.getStartAt().isBefore(slotEnd)
                    && (effectiveEnd == null || effectiveEnd.isAfter(slotStart));
        });
    }

    private Map<Long, List<Reservation>> groupReservations(List<Reservation> reservations) {
        Map<Long, List<Reservation>> grouped = new HashMap<>();
        reservations.forEach(r -> grouped.computeIfAbsent(r.getFacility().getId(), ignored -> new ArrayList<>())
                .add(r));
        return grouped;
    }

    private Map<Long, List<FacilityBlockage>> groupBlockages(List<FacilityBlockage> blockages) {
        Map<Long, List<FacilityBlockage>> grouped = new HashMap<>();
        blockages.forEach(b -> grouped.computeIfAbsent(b.getFacility().getId(), ignored -> new ArrayList<>())
                .add(b));
        return grouped;
    }

    private record SlotCounts(long approved, long bookable) {
    }
}
