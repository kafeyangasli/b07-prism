package com.github.kafeyangasli.prism.feature.reservation.controller;

import java.net.MalformedURLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.reservation.dto.ReservationForm;
import com.github.kafeyangasli.prism.feature.reservation.dto.ReservationAvailabilityView;
import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationLifecycleService;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationAvailabilityService;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationQueryService;
import com.github.kafeyangasli.prism.feature.reservation.service.ReservationSubmissionService;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.shared.exception.ResourceNotFoundException;
import com.github.kafeyangasli.prism.shared.exception.storage.ProposalStorageService;

@Controller
@RequestMapping("/reservations")
public class ReservationController {
    private final ReservationSubmissionService submissionService;
    private final ReservationLifecycleService lifecycleService;
    private final ReservationQueryService queryService;
    private final ReservationAvailabilityService availabilityService;
    private final FacilityRepository facilityRepository;
    private final ProposalStorageService proposalStorageService;

    public ReservationController(
            ReservationSubmissionService submissionService,
            ReservationLifecycleService lifecycleService,
            ReservationQueryService queryService,
            ReservationAvailabilityService availabilityService,
            FacilityRepository facilityRepository,
            ProposalStorageService proposalStorageService
    ) {

        this.submissionService = submissionService;
        this.lifecycleService = lifecycleService;
        this.queryService = queryService;
        this.availabilityService = availabilityService;
        this.facilityRepository = facilityRepository;
        this.proposalStorageService = proposalStorageService;
    }

    /*
     * Form pengajuan.
     */
    @GetMapping("/new")
    public String newReservation(
            @RequestParam(name = "facilityId", required = false) Long facilityId,
            @RequestHeader(name = "HX-Request", required = false) String hxRequest,
            Model model
    ) {
        ReservationForm form = new ReservationForm();
        form.setFacilityId(facilityId);
        form.setFacilityFixed(facilityId != null);
        populateFormModel(model, form, null);
        model.addAttribute("openReservationDialog", true);
        return isHtmx(hxRequest)
                ? "reservations/form :: reservation-modal"
                : "reservations/form";
    }

    @GetMapping("/availability")
    public String availability(
            @RequestParam(name = "facilityId", required = false) Long facilityId,
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(name = "startAt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startAt,
            @RequestParam(name = "endAt", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endAt,
            @RequestParam(name = "reset", defaultValue = "false") boolean reset,
            Model model
    ) {
        if (reset) {
            startAt = null;
            endAt = null;
        }
        if (facilityId != null && date != null) {
            try {
                model.addAttribute(
                        "availability",
                        availabilityService.availability(facilityId, date, startAt, endAt)
                );
            } catch (BusinessRuleException | ResourceNotFoundException exception) {
                model.addAttribute("availabilityError", exception.getMessage());
            }
        }
        return "reservations/availability :: availability";
    }

    /*
     * Submit reservation.
     */
    @PostMapping(
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Object submit(
            @ModelAttribute ReservationForm form,
            @RequestParam(
                    name = "proposal",
                    required = false
            )
            MultipartFile proposal,
            @RequestHeader(name = "HX-Request", required = false) String hxRequest,
            Authentication authentication,
            Model model,
            RedirectAttributes redirect
    ) {

        try {

            Reservation reservation =
                    submissionService.submit(
                            authentication.getName(),
                            form,
                            proposal
                    );

            redirect.addFlashAttribute(
                    "success",
                    "Reservasi berhasil diajukan dan berstatus Menunggu."
            );

            String detailUrl = "/reservations/" + reservation.getId();
            if (isHtmx(hxRequest)) {
                return ResponseEntity.noContent()
                        .header("HX-Redirect", detailUrl)
                        .build();
            }
            return "redirect:" + detailUrl;

        } catch (
                BusinessRuleException
                        | ResourceNotFoundException exception
        ) {

            model.addAttribute(
                    "error",
                    exception.getMessage()
            );

            populateFormModel(model, form, availabilityFor(form));
            model.addAttribute("openReservationDialog", true);
            return isHtmx(hxRequest)
                    ? "reservations/form :: reservation-modal"
                    : "reservations/form";
        }
    }

    private void populateFormModel(
            Model model,
            ReservationForm form,
            ReservationAvailabilityView availability
    ) {
        List<Facility> facilities = facilityRepository
                .findByAdministrativeStatusOrderByNameAsc(AdministrativeStatus.ACTIVE);
        model.addAttribute("facilities", facilities);
        model.addAttribute("reservationForm", form);
        model.addAttribute("minimumDate", availabilityService.minimumDate());
        model.addAttribute("maximumDate", availabilityService.maximumDate());
        model.addAttribute("availability", availability);
        if (form.getFacilityId() != null) {
            facilities.stream()
                    .filter(facility -> facility.getId().equals(form.getFacilityId()))
                    .findFirst()
                    .ifPresent(facility -> model.addAttribute("selectedFacility", facility));
        }
    }

    private ReservationAvailabilityView availabilityFor(ReservationForm form) {
        if (form == null || form.getFacilityId() == null || form.getDate() == null
                || form.getDate().isBlank()) {
            return null;
        }
        try {
            LocalDate date = LocalDate.parse(form.getDate());
            LocalDateTime start = parseOptionalDateTime(form.getStartAt());
            LocalDateTime end = parseOptionalDateTime(form.getEndAt());
            return availabilityService.availability(form.getFacilityId(), date, start, end);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private LocalDateTime parseOptionalDateTime(String value) {
        return value == null || value.isBlank() ? null : LocalDateTime.parse(value);
    }

    private boolean isHtmx(String hxRequest) {
        return "true".equalsIgnoreCase(hxRequest);
    }

    /*
     * History.
     */
    @GetMapping
    public String history(
            Authentication authentication,
            Model model
    ) {

        model.addAttribute(
                "reservations",
                queryService.findOwnReservations(
                        authentication.getName()
                )
        );

        return "reservations/history";
    }

    /*
     * Detail.
     */
    @GetMapping("/{id}")
    public String detail(
            @PathVariable long id,
            Authentication authentication,
            Model model
    ) {

        model.addAttribute(
                "reservation",
                queryService.findOwnReservation(
                        authentication.getName(),
                        id
                )
        );

        return "reservations/detail";
    }

    /*
     * Cancellation.
     */
    @PostMapping("/{id}/cancel")
    public String cancel(
            @PathVariable long id,
            Authentication authentication,
            RedirectAttributes redirect
    ) {

        try {

            lifecycleService.cancelOwnReservation(
                    authentication.getName(),
                    id
            );

            redirect.addFlashAttribute(
                    "success",
                    "Reservasi berhasil dibatalkan."
            );

        } catch (
                BusinessRuleException
                        | ResourceNotFoundException exception
        ) {

            redirect.addFlashAttribute(
                    "error",
                    exception.getMessage()
            );
        }

        return "redirect:/reservations/" + id;
    }

    /*
     * Download proposal milik sendiri.
     */
    @GetMapping("/{id}/proposal")
    public ResponseEntity<Resource> proposal(
            @PathVariable long id,
            Authentication authentication
    ) {

        Reservation reservation =
                queryService.findOwnReservation(
                        authentication.getName(),
                        id
                );

        if (reservation.getProposalPath() == null
                || reservation.getProposalPath().isBlank()) {

            throw new ResourceNotFoundException(
                    "Reservasi ini tidak memiliki proposal"
            );
        }

        try {

            Resource resource =
                    proposalStorageService.load(
                            reservation.getProposalPath()
                    );

            return ResponseEntity.ok()
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition
                                    .attachment()
                                    .filename(
                                            "proposal-" + id
                                    )
                                    .build()
                                    .toString()
                    )
                    .contentType(
                            MediaType.APPLICATION_OCTET_STREAM
                    )
                    .body(resource);

        } catch (
                MalformedURLException
                        | IllegalArgumentException exception
        ) {

            throw new ResourceNotFoundException(
                    "Proposal tidak ditemukan",
                    exception
            );
        }
    }

    /*
     * Download template proposal.
     */
    @GetMapping("/proposal-template")
    public ResponseEntity<Resource> proposalTemplate()
            throws MalformedURLException {

        Resource resource =
                new ClassPathResource(
                        "static/templates/proposal-template.txt"
                );

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition
                                .attachment()
                                .filename(
                                        "proposal-template.txt"
                                )
                                .build()
                                .toString()
                )
                .contentType(
                        MediaType.TEXT_PLAIN
                )
                .body(resource);
    }
}
