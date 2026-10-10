package com.github.kafeyangasli.prism.feature.reservation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.github.kafeyangasli.prism.feature.blockage.model.BlockageStatus;
import com.github.kafeyangasli.prism.feature.blockage.model.BlockageType;
import com.github.kafeyangasli.prism.feature.blockage.model.FacilityBlockage;
import com.github.kafeyangasli.prism.feature.blockage.repository.BlockageTypeRepository;
import com.github.kafeyangasli.prism.feature.blockage.repository.FacilityBlockageRepository;
import com.github.kafeyangasli.prism.feature.facility.model.AdministrativeStatus;
import com.github.kafeyangasli.prism.feature.facility.model.Facility;
import com.github.kafeyangasli.prism.feature.facility.model.FacilityType;
import com.github.kafeyangasli.prism.feature.facility.repository.FacilityRepository;
import com.github.kafeyangasli.prism.feature.reservation.dto.ReservationSlotState;
import com.github.kafeyangasli.prism.feature.reservation.dto.ReservationSlotView;
import com.github.kafeyangasli.prism.feature.reservation.model.Reservation;
import com.github.kafeyangasli.prism.feature.reservation.model.ReservationStatus;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.model.User;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:public-availability;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureMockMvc
@Import(ReservationAvailabilityIntegrationTest.FixedClockConfiguration.class)
@Transactional
class PublicFacilityAvailabilityIntegrationTest {
    private static final LocalDate DATE = LocalDate.of(2026, 9, 25);

    @Autowired MockMvc mvc;
    @Autowired FacilityRepository facilities;
    @Autowired ReservationRepository reservations;
    @Autowired FacilityBlockageRepository blockages;
    @Autowired BlockageTypeRepository blockageTypes;
    @Autowired UserRepository users;
    @Autowired EntityManager entityManager;

    private Facility room;
    private User requester;
    private User staff;
    private BlockageType type;

    @BeforeEach
    void setUp() {
        requester = users.save(new User("PRIVATE-REQUESTER", "private-requester@example.test", "hash",
                Role.PENGGUNA, AccountStatus.ACTIVE));
        staff = users.save(new User("PRIVATE-STAFF", "private-staff@example.test", "hash",
                Role.PETUGAS, AccountStatus.ACTIVE));
        FacilityType facilityType = new FacilityType("PUBLIC-ROOM", "Kelas Publik", null);
        entityManager.persist(facilityType);
        room = facilities.save(new Facility("PUB-101", "Ruang Publik", facilityType, "Gedung A", 30,
                null, AdministrativeStatus.ACTIVE));
        type = blockageTypes.save(new BlockageType("PUBLIC-TEST", "Uji blokir", null));
    }

    @Test
    void anonymousPageShowsHalfHourStatesAndOnlyPublicReasons() throws Exception {
        reservation(9, 0, 10, 0, ReservationStatus.PENDING);
        reservation(10, 15, 11, 0, ReservationStatus.APPROVED);
        blockage(11, 30, 12, 30, BlockageStatus.SCHEDULED, "Pembersihan publik");

        MvcResult result = request();
        List<ReservationSlotView> slots = slots(result);
        assertThat(slots).hasSize(26).allSatisfy(slot ->
                assertThat(Duration.between(slot.startAt(), slot.endAt()).toMinutes()).isEqualTo(30));
        assertState(slots, "09:00", ReservationSlotState.AVAILABLE);
        assertState(slots, "09:30", ReservationSlotState.AVAILABLE);
        assertState(slots, "10:00", ReservationSlotState.RESERVED);
        assertState(slots, "10:30", ReservationSlotState.RESERVED);
        assertState(slots, "11:00", ReservationSlotState.AVAILABLE);
        assertState(slots, "11:30", ReservationSlotState.BLOCKED);
        assertState(slots, "12:00", ReservationSlotState.BLOCKED);
        assertState(slots, "12:30", ReservationSlotState.AVAILABLE);
        assertThat(slots).filteredOn(slot -> slot.state() != ReservationSlotState.BLOCKED)
                .allSatisfy(slot -> assertThat(slot.reason()).isNull());

        Map<String, Object> model = result.getModelAndView().getModel();
        assertThat(model).containsOnlyKeys("facilityId", "facilityName", "date", "slots");
        String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("Ruang Publik", "07:00", "20:00", "Pembersihan publik",
                "data-start=\"10:00\" data-state=\"RESERVED\"",
                "data-start=\"11:00\" data-state=\"AVAILABLE\"")
                .doesNotContain("PRIVATE-REQUESTER", "private-requester@example.test", "PRIVATE-STAFF",
                        "private-staff@example.test", "PRIVATE-PURPOSE", "PRIVATE-PROPOSAL.pdf",
                        "PRIVATE-INTERNAL-NOTE", "proposal", "Proposal", "th:text");
    }

    @Test
    void effectiveBlockageEndAndNonBlockingStatusesAreRespected() throws Exception {
        FacilityBlockage early = blockage(8, 0, 10, 0, BlockageStatus.ACTIVE, "Selesai lebih awal");
        early.setActualEndAt(DATE.atTime(8, 30));
        blockages.save(early);
        FacilityBlockage extended = blockage(10, 0, 10, 30, BlockageStatus.ACTIVE, "Diperpanjang");
        extended.setActualEndAt(DATE.atTime(11, 0));
        blockages.save(extended);
        blockage(12, 0, 13, 0, BlockageStatus.CANCELLED, "PRIVATE-CANCELLED-REASON");
        blockage(13, 0, 14, 0, BlockageStatus.COMPLETED, "PRIVATE-COMPLETED-REASON");
        blockages.save(new FacilityBlockage(room, type, null, DATE.atTime(19, 30), null,
                BlockageStatus.ACTIVE, "Tanpa batas akhir", "PRIVATE-INTERNAL-NOTE", staff));

        MvcResult result = request();
        List<ReservationSlotView> slots = slots(result);
        assertState(slots, "07:30", ReservationSlotState.AVAILABLE);
        assertState(slots, "08:00", ReservationSlotState.BLOCKED);
        assertState(slots, "08:30", ReservationSlotState.AVAILABLE);
        assertState(slots, "10:30", ReservationSlotState.BLOCKED);
        assertState(slots, "11:00", ReservationSlotState.AVAILABLE);
        assertState(slots, "12:00", ReservationSlotState.AVAILABLE);
        assertState(slots, "13:00", ReservationSlotState.AVAILABLE);
        assertState(slots, "19:00", ReservationSlotState.AVAILABLE);
        assertState(slots, "19:30", ReservationSlotState.BLOCKED);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .doesNotContain("PRIVATE-CANCELLED-REASON", "PRIVATE-COMPLETED-REASON", "PRIVATE-INTERNAL-NOTE");
    }

    @Test
    void aulaPageDoesNotExposeProposalInformation() throws Exception {
        FacilityType aula = new FacilityType("PUBLIC-AULA", "Aula", null);
        entityManager.persist(aula);
        room.setFacilityType(aula);
        facilities.save(room);
        String html = request().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).doesNotContain("proposal", "Proposal", "selectedStart", "selectedEnd");
    }

    @Test
    void invalidDatesAndMissingFacilityReturnClientErrors() throws Exception {
        mvc.perform(get(route())).andExpect(status().isBadRequest());
        mvc.perform(get(route()).param("date", "invalid")).andExpect(status().isBadRequest());
        mvc.perform(get(route()).param("date", "2026-09-23")).andExpect(status().isBadRequest());
        mvc.perform(get(route()).param("date", "2027-04-01")).andExpect(status().isBadRequest());
        mvc.perform(get("/facilities/9223372036854775807/availability").param("date", DATE.toString()))
                .andExpect(status().isNotFound());
    }

    private MvcResult request() throws Exception {
        return mvc.perform(get(route()).param("date", DATE.toString()))
                .andExpect(status().isOk()).andExpect(view().name("reservations/public-availability"))
                .andReturn();
    }

    private String route() {
        return "/facilities/" + room.getId() + "/availability";
    }

    @SuppressWarnings("unchecked")
    private List<ReservationSlotView> slots(MvcResult result) {
        return (List<ReservationSlotView>) result.getModelAndView().getModel().get("slots");
    }

    private void assertState(List<ReservationSlotView> slots, String time, ReservationSlotState state) {
        assertThat(slots).filteredOn(slot -> slot.getLabel().equals(time)).singleElement()
                .satisfies(slot -> assertThat(slot.state()).isEqualTo(state));
    }

    private void reservation(int startHour, int startMinute, int endHour, int endMinute, ReservationStatus status) {
        reservations.save(new Reservation(requester, room, DATE.atTime(startHour, startMinute),
                DATE.atTime(endHour, endMinute), "PRIVATE-PURPOSE", "PRIVATE-PROPOSAL.pdf", status,
                DATE.atStartOfDay()));
    }

    private FacilityBlockage blockage(int startHour, int startMinute, int endHour, int endMinute,
                                     BlockageStatus status, String publicReason) {
        return blockages.save(new FacilityBlockage(room, type, null, DATE.atTime(startHour, startMinute),
                DATE.atTime(endHour, endMinute), status, publicReason, "PRIVATE-INTERNAL-NOTE", staff));
    }
}
