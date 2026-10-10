package com.github.kafeyangasli.prism.feature.reservation.controller;

import java.time.LocalDate;
import java.time.Clock;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import com.github.kafeyangasli.prism.feature.reservation.dto.ReservationAvailabilityView;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationAvailabilityService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;

@Controller
public class PublicFacilityAvailabilityController {
    private final ReservationAvailabilityService availabilityService;
    private final Clock clock;

    public PublicFacilityAvailabilityController(ReservationAvailabilityService availabilityService, Clock clock) {
        this.availabilityService = availabilityService;
        this.clock = clock;
    }

    @GetMapping("/facilities/{facilityId}/availability")
    public String availability(
            @PathVariable("facilityId") Long facilityId,
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Model model
    ) {
        ReservationAvailabilityView availability;
        try {
            availability = availabilityService.availability(facilityId, date != null ? date : LocalDate.now(clock), null, null);
        } catch (ResourceNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        } catch (BusinessRuleException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }

        model.addAttribute("facilityId", facilityId);
        model.addAttribute("facilityName", availability.facility().getName());
        model.addAttribute("date", availability.date());
        model.addAttribute("slots", availability.slots());

        return "reservations/public-availability";
    }
}
