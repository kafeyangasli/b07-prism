package com.github.kafeyangasli.prism.feature.facility.controller;

import com.github.kafeyangasli.prism.feature.facility.service.FacilityService;
import com.github.kafeyangasli.prism.feature.facility.service.FacilityFilters;
import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.reservation.service.EquipmentAvailabilityService;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;

@Controller
public class FacilityController {

    private final FacilityService facilityService;
    private final ReservationQueryService reservationQueryService;
    private final EquipmentAvailabilityService equipmentAvailability;
    private final Clock clock;

    public FacilityController(
            FacilityService facilityService,
            ReservationQueryService reservationQueryService,
            EquipmentAvailabilityService equipmentAvailability,
            Clock clock
    ) {
        this.facilityService = facilityService;
        this.reservationQueryService = reservationQueryService;
        this.equipmentAvailability = equipmentAvailability;
        this.clock = clock;
    }

    @GetMapping("/")
    public String landingPage(Authentication authentication, Model model) {
        if (authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_PETUGAS")
                        || authority.getAuthority().equals("ROLE_ADMIN"))) {
            return "redirect:/staff/dashboard";
        }
        if (authentication != null) {
            model.addAttribute(
                    "userDashboard",
                    reservationQueryService.dashboard(authentication.getName())
            );
        }
        return "home";
    }

    @GetMapping("/facilities")
    public String facilityCatalogue(@RequestParam MultiValueMap<String, String> parameters,
                                    @RequestParam(required = false) Integer capacity,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                    Model model) {
        // Read raw repeated values: Spring's scalar-to-List conversion would split locations containing commas.
        var types = FacilityFilters.normalize(parameters.get("type"));
        var locations = FacilityFilters.normalize(parameters.get("location"));
        var results = facilityService.searchCatalogueSelections(types, locations, capacity);
        var catalogue = facilityService.getPublicCatalogue();
        var catalogueTypes = catalogue.stream().map(Facility::getFacilityType)
                .collect(Collectors.toMap(t -> t.getId(), t -> t, (a, b) -> a)).values();
        model.addAttribute("typeOptions", FacilityFilters.types(catalogueTypes, types));
        model.addAttribute("locationOptions", FacilityFilters.locations(catalogue, locations));
        LocalDate minimumDate = LocalDate.now(clock);
        LocalDateTime startAt = date == null ? null : date.atTime(7, 0);
        LocalDateTime endAt = date == null ? null : date.atTime(20, 0);
        if (date != null && date.isBefore(minimumDate)) {
            model.addAttribute("filterError", "Tanggal penggunaan harus hari ini atau setelahnya.");
        } else if (date != null) {
            try {
                var availableIds = facilityService.getAvailableFacilities(startAt, endAt).stream()
                        .map(facility -> facility.getId()).collect(Collectors.toSet());
                results = results.stream().filter(facility -> availableIds.contains(facility.getId())).toList();
            } catch (BusinessRuleException exception) {
                model.addAttribute("filterError", exception.getMessage());
            }
        }
        model.addAttribute("facilities", model.containsAttribute("filterError") ? List.of() : results);
        var availableStock = new LinkedHashMap<Long, Integer>();
        if (!model.containsAttribute("filterError")) {
            LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
            LocalDateTime stockStart = startAt == null ? now : startAt;
            LocalDateTime stockEnd = endAt == null ? now.plusNanos(1_000) : endAt;
            for (Facility facility : results) {
                if (facility.isEquipment()) availableStock.put(facility.getId(), availableStock(facility, stockStart, stockEnd));
            }
        }
        model.addAttribute("availableStock", availableStock);
        model.addAttribute("stockForSelectedDate", date != null);
        model.addAttribute("type", types);
        model.addAttribute("location", locations);
        model.addAttribute("capacity", capacity);
        model.addAttribute("date", date);
        model.addAttribute("minimumDate", minimumDate);
        return "facilities/list";
    }

    @GetMapping("/facilities/{id}")
    public String facilityDetail(@PathVariable("id") Long id, Model model) {
        Facility facility = facilityService.getFacilityById(id);
        model.addAttribute("facility", facility);
        if (facility.isEquipment()) {
            LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
            model.addAttribute("availableStock", availableStock(facility, now, now.plusNanos(1_000)));
        }
        model.addAttribute("approvedReservations", facilityService.getApprovedReservationsForFacility(id));
        model.addAttribute("blockages", facilityService.getBlockagesForFacility(id));
        return "facilities/detail";
    }

    private int availableStock(Facility facility, LocalDateTime start, LocalDateTime end) {
        return facility.getAdministrativeStatus() == AdministrativeStatus.ACTIVE
                ? equipmentAvailability.available(facility, start, end) : 0;
    }
}
