package com.github.kafeyangasli.prism.feature.facility.controller;

import com.github.kafeyangasli.prism.feature.facility.service.FacilityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;

@Controller
public class FacilityController {

    private final FacilityService facilityService;

    public FacilityController(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    @GetMapping({"/", "/facilities"})
    public String facilityCatalogue(@RequestParam(required = false) String type,
                                    @RequestParam(required = false) String location,
                                    @RequestParam(required = false) Integer capacity,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
                                    @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
                                    Model model) {
        if (startAt != null && endAt != null) {
            model.addAttribute("facilities", facilityService.getAvailableFacilities(startAt, endAt));
        } else if (type != null || location != null || capacity != null) {
            model.addAttribute("facilities", facilityService.searchCatalogue(type, location, capacity));
        } else {
            model.addAttribute("facilities", facilityService.getPublicCatalogue());
        }
        model.addAttribute("type", type);
        model.addAttribute("location", location);
        model.addAttribute("capacity", capacity);
        model.addAttribute("startAt", startAt);
        model.addAttribute("endAt", endAt);
        return "facilities/list";
    }

    @GetMapping("/facilities/{id}")
    public String facilityDetail(@PathVariable("id") Long id, Model model) {
        model.addAttribute("facility", facilityService.getFacilityById(id));
        return "facilities/detail";
    }
}
