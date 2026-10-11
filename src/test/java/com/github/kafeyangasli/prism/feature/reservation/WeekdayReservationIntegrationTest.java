package com.github.kafeyangasli.prism.feature.reservation;

import com.github.kafeyangasli.prism.feature.facility.model.*;
import com.github.kafeyangasli.prism.feature.facility.repository.*;
import com.github.kafeyangasli.prism.feature.facility.service.FacilityService;
import com.github.kafeyangasli.prism.feature.reservation.dto.*;
import com.github.kafeyangasli.prism.feature.reservation.model.*;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.reservation.service.*;
import com.github.kafeyangasli.prism.feature.user.model.*;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.support.ProposalPdf;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Path;
import java.time.*;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.github.kafeyangasli.prism.support.PrismTestUsers.user;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:weekday-rules;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
@Import(WeekdayReservationIntegrationTest.TimeConfiguration.class)
@Transactional
class WeekdayReservationIntegrationTest {
    @TempDir static Path proposals;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("prism.storage.proposals", () -> proposals.toString());
    }
    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-12T01:00:00Z"), ZoneId.of("Asia/Jakarta"));
        }
    }
    @Autowired ReservationSubmissionService submissions;
    @Autowired ReservationAvailabilityService availability;
    @Autowired EquipmentAvailabilityService equipmentAvailability;
    @Autowired ReservationRepository reservations;
    @Autowired FacilityRepository facilities;
    @Autowired FacilityTypeRepository types;
    @Autowired FacilityService facilityService;
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;
    @Autowired com.github.kafeyangasli.prism.feature.administration.service.AdministrationReportService recap;
    User owner;
    List<Facility> all;

    @BeforeEach void seed() {
        owner = users.save(new User("Owner", "weekday@test", "hash", Role.PENGGUNA, AccountStatus.ACTIVE));
        all = List.of(facility("ROOM", "Kelas"), facility("ALAT", "Alat"), facility("HALL", "Aula"));
    }
    private Facility facility(String code, String name) {
        var type = types.save(new FacilityType(code, name, null));
        var facility = new Facility(code, name, type, "Floor", 10, null, AdministrativeStatus.ACTIVE);
        if (facility.isEquipment()) facility.setTotalStock(10);
        facility.recordCreationStatus(LocalDateTime.of(2026, 10, 12, 8, 0));
        return facilities.save(facility);
    }

    @ParameterizedTest
    @CsvSource({"2026-10-16,true", "2026-10-17,false", "2026-10-18,false", "2026-10-19,true", "2026-12-25,true"})
    void fridayWeekendMondayAndWeekdayHolidayApplyToAllFacilityTypes(LocalDate date, boolean allowed) {
        for (var facility : all) {
            var form = new ReservationForm();
            form.setFacilityId(facility.getId());
            form.setStartAt(date.atTime(7, 0).toString());
            form.setEndAt(date.atTime(facility.getType().equals("Aula") ? 20 : 8, 0).toString());
            form.setPurpose("Event");
            if (facility.isEquipment()) form.setQuantity(1);
            if (allowed) assertThat(submissions.submit(owner.getEmail(), form, ProposalPdf.upload()).getStatus()).isEqualTo(ReservationStatus.PENDING);
            else assertThatThrownBy(() -> submissions.submit(owner.getEmail(), form, ProposalPdf.upload()))
                    .isInstanceOf(BusinessRuleException.class).hasMessageContaining("Senin sampai Jumat");
        }
        assertThat(reservations.count()).isEqualTo(allowed ? 3 : 0);
    }

    @ParameterizedTest @CsvSource({"2026-10-17", "2026-10-18"})
    void weekendsDisablePublicAndAuthenticatedAvailabilityWithoutChangingLegacyReservations(LocalDate date) throws Exception {
        for (var facility : all) {
            var legacy = new Reservation(owner, facility, date.atTime(9, 0), date.atTime(10, 0),
                    "Legacy weekend", null, ReservationStatus.APPROVED, null);
            if (facility.isEquipment()) legacy.setQuantity(2);
            reservations.saveAndFlush(legacy);
            var view = availability.availability(facility.getId(), date, date.atTime(7, 0), date.atTime(8, 0));
            assertThat(view.slots()).hasSize(26).allSatisfy(slot -> {
                assertThat(slot.state()).isEqualTo(ReservationSlotState.NON_OPERATING);
                assertThat(slot.isSelectable()).isFalse();
                if (facility.isEquipment()) assertThat(slot.availableQuantity()).isZero();
            });
            assertThat(view.selectedStart()).isNull();
            assertThat(view.selectedEnd()).isNull();
            assertThat(view.endTimes()).isEmpty();
            assertThat(view.aulaAvailable()).isFalse();
            mvc.perform(get("/facilities/" + facility.getId() + "/availability").param("date", date.toString()))
                    .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Akhir pekan - tidak tersedia")));
            mvc.perform(get("/reservations/availability").with(user(owner.getEmail())).param("facilityId", facility.getId().toString()).param("date", date.toString()))
                    .andExpect(status().isOk());
            assertThat(reservations.findById(legacy.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.APPROVED);
            if (facility.isEquipment()) assertThat(equipmentAvailability.available(facility, date.atTime(7, 0), date.atTime(8, 0))).isZero();
        }
        assertThat(facilityService.getAvailableFacilities(date.atTime(7, 0), date.atTime(8, 0))).isEmpty();
        assertThat(reservations.count()).isEqualTo(3);
    }

    @Test @com.github.kafeyangasli.prism.support.WithPrismUser(roles = "ADMIN")
    void recapExcludesWeekendCapacityForRoomsAndEquipmentAndRetainsLegacyHistory() {
        LocalDate friday = LocalDate.of(2026, 10, 16), monday = friday.plusDays(3);
        for (var facility : all) {
            var legacy = new Reservation(owner, facility, friday.plusDays(1).atTime(9, 0), friday.plusDays(1).atTime(10, 0),
                    "Legacy weekend", null, ReservationStatus.APPROVED, null);
            if (facility.isEquipment()) legacy.setQuantity(2);
            reservations.saveAndFlush(legacy);
            var weekdays = recap.generate(new com.github.kafeyangasli.prism.feature.administration.dto.RecapFilter(
                    friday, monday, facility.getId(), null, null)).rows().getFirst();
            assertThat(weekdays.bookableSlots()).isEqualTo(52); // Friday + Monday, 26 each.
            var weekend = recap.generate(new com.github.kafeyangasli.prism.feature.administration.dto.RecapFilter(
                    friday.plusDays(1), friday.plusDays(2), facility.getId(), null, null)).rows().getFirst();
            assertThat(weekend.bookableSlots()).isZero();
            assertThat(weekend.approvedSlots()).isEqualTo(2);
            assertThat(reservations.findById(legacy.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.APPROVED);
        }
    }
}
