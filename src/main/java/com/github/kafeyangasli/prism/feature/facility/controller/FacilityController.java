package com.github.kafeyangasli.prism.feature.facility.controller;

import com.github.kafeyangasli.prism.feature.facility.service.FacilityService;
import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.reservation.service.EquipmentAvailabilityService;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationQueryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;
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
    public String facilityCatalogue(@RequestParam(required = false) String type,
                                    @RequestParam(required = false) String location,
                                    @RequestParam(required = false) Integer capacity,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
                                    Model model) {
        var results = facilityService.searchCatalogue(type, location, capacity);
        if ((startAt == null) != (endAt == null)) {
            model.addAttribute("filterError", "Isi waktu mulai dan selesai bersama untuk memeriksa ketersediaan.");
        } else if (startAt != null) {
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
        model.addAttribute("stockForSelectedInterval", startAt != null && endAt != null);
        model.addAttribute("type", type);
        model.addAttribute("location", location);
        model.addAttribute("capacity", capacity);
        model.addAttribute("startAt", startAt);
        model.addAttribute("endAt", endAt);
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
