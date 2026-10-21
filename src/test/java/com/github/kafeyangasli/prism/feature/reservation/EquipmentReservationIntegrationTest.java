package com.github.kafeyangasli.prism.feature.reservation;

import com.github.kafeyangasli.prism.feature.facility.dto.FacilityDto;
import com.github.kafeyangasli.prism.feature.facility.model.*;
import com.github.kafeyangasli.prism.feature.facility.repository.*;
import com.github.kafeyangasli.prism.feature.facility.service.*;
import com.github.kafeyangasli.prism.feature.reservation.dto.ReservationForm;
import com.github.kafeyangasli.prism.feature.reservation.model.*;
import com.github.kafeyangasli.prism.feature.reservation.repository.ReservationRepository;
import com.github.kafeyangasli.prism.feature.reservation.service.*;
import com.github.kafeyangasli.prism.feature.blockage.dto.*;
import com.github.kafeyangasli.prism.feature.blockage.model.*;
import com.github.kafeyangasli.prism.feature.blockage.repository.*;
import com.github.kafeyangasli.prism.feature.blockage.service.BlockageService;
import com.github.kafeyangasli.prism.feature.user.model.*;
import com.github.kafeyangasli.prism.feature.user.model.Role;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.security.PrismUserDetails;
import com.github.kafeyangasli.prism.shared.exception.BusinessRuleException;
import com.github.kafeyangasli.prism.support.ProposalPdf;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:equipment;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop", "prism.scheduler.reservation-lifecycle-ms=3600000"})
@AutoConfigureMockMvc
@Import(EquipmentReservationIntegrationTest.TimeConfiguration.class)
class EquipmentReservationIntegrationTest {
    static final LocalDateTime NOW = LocalDateTime.of(2030, 1, 7, 10, 0);
    static final ThreadLocal<CyclicBarrier> PRE_LOCK_READS = new ThreadLocal<>();
    static final LocalDateTime START = NOW.plusDays(2).withHour(7);
    @TempDir static Path storage;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) { registry.add("prism.storage.proposals", () -> storage.toString()); }
    @Autowired EquipmentAvailabilityService inventory;
    @Autowired ReservationSubmissionService submissions;
    @Autowired ReservationProcessingService processing;
    @Autowired ReservationLifecycleService lifecycle;
    @Autowired ReservationAvailabilityService availability;
    @Autowired FacilityService facilityService;
    @Autowired FacilityTypeService typeService;
    @Autowired ReservationRepository reservations;
    @Autowired FacilityRepository facilities;
    @Autowired FacilityTypeRepository types;
    @Autowired UserRepository users;
    @Autowired FacilityBlockageRepository blockages;
    @Autowired BlockageTypeRepository blockageTypes;
    @Autowired BlockageService blockageService;
    @Autowired MockMvc mvc;
    Facility equipment, room;
    User requester, other, staff, admin;

    @BeforeEach void seed() {
        blockages.deleteAll(); reservations.deleteAll(); facilities.deleteAll(); types.deleteAll(); users.deleteAll();
        requester = createUser("Requester", Role.PENGGUNA); other = createUser("Other", Role.PENGGUNA);
        staff = createUser("Staff", Role.PETUGAS); admin = createUser("Admin", Role.ADMIN);
        var alat = types.saveAndFlush(new FacilityType("ALAT", "Alat", null));
        var kelas = types.saveAndFlush(new FacilityType("KELAS", "Kelas", null));
        equipment = new Facility("MIC", "Mikrofon Wireless", alat, "Gedung A", 20, null, AdministrativeStatus.ACTIVE);
        equipment.setTotalStock(20); equipment = facilities.saveAndFlush(equipment);
        room = facilities.saveAndFlush(new Facility("ROOM", "Ruang kelas", kelas, "Gedung A", 20, null, AdministrativeStatus.ACTIVE));
    }
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void pendingAndApprovedConsumeStockAndExactAvailableQuantitySucceeds() {
        reserve(requester, equipment, START, START.plusHours(2), 5, ReservationStatus.PENDING, NOW.plusHours(12));
        reserve(other, equipment, START, START.plusHours(2), 5, ReservationStatus.APPROVED, NOW.plusHours(12));
        assertThat(available(START, START.plusHours(2))).isEqualTo(10);
        assertThat(submit(requester, 10, 120, true).getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(available(START, START.plusHours(2))).isZero();
        assertThatThrownBy(() -> submit(other, 1, 120, false)).isInstanceOf(BusinessRuleException.class).hasMessageContaining("hanya 0 unit");
        assertThat(facilities.findById(equipment.getId()).orElseThrow().getTotalStock()).isEqualTo(20);
    }
    @Test void exceedingAvailableByOneFailsWithClearError() {
        reserve(requester,equipment,START,START.plusHours(2),15,ReservationStatus.APPROVED,NOW.plusHours(12));
        assertThatThrownBy(() -> submit(other,6,120,true)).hasMessageContaining("hanya 5 unit").hasMessageContaining("6 unit");
    }
    @Test void expiredPendingAndTerminalStatusesDoNotAllocate() {
        for (var status : List.of(ReservationStatus.CANCELLED,ReservationStatus.REJECTED,ReservationStatus.EXPIRED,ReservationStatus.COMPLETED))
            reserve(requester,equipment,START,START.plusHours(2),20,status,NOW.plusHours(12));
        reserve(requester,equipment,START,START.plusHours(2),20,ReservationStatus.PENDING,NOW);
        assertThat(available(START,START.plusHours(2))).isEqualTo(20);
        assertThat(submit(other,5,120,false).getStatus()).isEqualTo(ReservationStatus.PENDING);
    }
    @Test void adjacentAndDisjointReservationsAreNotSummed() {
        reserve(requester,equipment,START,START.plusHours(1),10,ReservationStatus.APPROVED,NOW.plusHours(12));
        reserve(other,equipment,START.plusHours(1),START.plusHours(2),10,ReservationStatus.PENDING,NOW.plusHours(12));
        assertThat(available(START,START.plusHours(2))).isEqualTo(10);
        assertThat(submit(requester,10,120,true).getStatus()).isEqualTo(ReservationStatus.PENDING);
    }
    @ParameterizedTest @NullSource @ValueSource(ints={0,-1,21})
    void invalidQuantitiesAreRejected(Integer quantity) {
        assertThatThrownBy(() -> submit(requester,quantity,60,true)).isInstanceOf(BusinessRuleException.class);
    }
    @ParameterizedTest @CsvSource({"5,60,false", "6,60,true", "5,360,true", "6,360,true"})
    void quantityAndDurationProposalThresholds(int quantity,int minutes,boolean required) {
        if (required) assertThatThrownBy(() -> submit(requester,quantity,minutes,false)).hasMessageContaining("Proposal");
        Reservation result = submit(requester,quantity,minutes,required);
        assertThat(result.isProposalRequired()).isEqualTo(required);
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.PENDING);
    }
    @Test void approvalRequiresValidatedMandatoryAndOptionalProposals() {
        Reservation pending=submit(requester,6,60,true); authenticate(staff);
        assertThatThrownBy(() -> processing.approve(pending.getId(),staff.getId(),false)).hasMessageContaining("divalidasi");
        assertThat(reservations.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.PENDING);
    }
    @Test void manualApprovalDoesNotDoubleAllocateOrCascade() {
        Reservation first=submit(requester,5,60,false), second=submit(other,5,60,false); authenticate(staff);
        processing.approve(first.getId(),staff.getId(),false);
        assertThat(reservations.findById(second.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.PENDING);
        assertThat(available(START,START.plusHours(1))).isEqualTo(10);
        processing.approve(second.getId(),staff.getId(),false);
        assertThat(available(START,START.plusHours(1))).isEqualTo(10);
    }
    @Test void validatedProposalAllowsApproval() throws Exception {
        Reservation pending=submit(requester,6,60,true); authenticate(staff);
        processing.validateProposal(pending.getId(),staff.getId(),new MockMultipartFile("validatedProposal","validated.pdf","application/pdf",ProposalPdf.bytes()));
        assertThat(processing.approve(pending.getId(),staff.getId(),false).getStatus()).isEqualTo(ReservationStatus.APPROVED);
        assertThat(available(START,START.plusHours(1))).isEqualTo(14);
    }
    @Test void sevenActiveApprovedLimitStillApplies() {
        for(int day=1;day<=7;day++) reserve(requester,room,START.plusDays(day),START.plusDays(day).plusHours(1),null,ReservationStatus.APPROVED,NOW.plusHours(12));
        Reservation pending=submit(requester,1,60,false); authenticate(admin);
        assertThatThrownBy(() -> processing.approve(pending.getId(),admin.getId(),false)).hasMessageContaining("tujuh");
        assertThat(reservations.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.PENDING);
    }
    @Test void rejectedCancelledAndExpiredPendingReleaseImmediately() {
        Reservation rejected=submit(requester,5,60,false); authenticate(staff);
        processing.reject(rejected.getId(),staff.getId(),"Tidak digunakan");
        assertThat(available(START,START.plusHours(1))).isEqualTo(20);
        Reservation cancelled=submit(requester,5,60,false);
        lifecycle.cancelOwnReservation(requester.getEmail(),cancelled.getId());
        assertThat(available(START,START.plusHours(1))).isEqualTo(20);
        Reservation expired=reserve(requester,equipment,START,START.plusHours(1),5,ReservationStatus.PENDING,NOW.minusSeconds(1));
        assertThat(lifecycle.expirePendingReservations()).isEqualTo(1);
        assertThat(reservations.findById(expired.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.EXPIRED);
    }
    @Test void expiredPendingCannotBeApproved() {
        Reservation expired=reserve(requester,equipment,START,START.plusHours(1),5,ReservationStatus.PENDING,NOW); authenticate(staff);
        assertThatThrownBy(() -> processing.approve(expired.getId(),staff.getId(),false)).hasMessageContaining("lewat");
    }
    @Test void cancellationOwnershipAndTwentyFourHourBoundaryRemainEnforced() {
        Reservation pending=submit(requester,1,60,false);
        assertThatThrownBy(() -> lifecycle.cancelOwnReservation(other.getEmail(),pending.getId())).hasMessageContaining("tidak ditemukan");
        Reservation approved=reserve(requester,equipment,NOW.plusHours(24),NOW.plusHours(25),1,ReservationStatus.APPROVED,NOW.plusHours(12));
        assertThatThrownBy(() -> lifecycle.cancelOwnReservation(requester.getEmail(),approved.getId())).hasMessageContaining("24 jam");
        authenticate(staff); processing.cancelApproved(approved.getId(),staff.getId(),"Tidak digunakan");
        assertThat(available(NOW.plusHours(24),NOW.plusHours(25))).isEqualTo(20);
    }
    @Test void ordinaryPendingDoesNotBlockAndApprovalRemainsExclusiveWithCascade() {
        ReservationForm form=form(room,START,START.plusHours(1),null);
        Reservation first=submissions.submit(requester.getEmail(),form,null), second=submissions.submit(other.getEmail(),form,null);
        assertThat(availability.availability(room.getId(),START.toLocalDate(),START,START.plusHours(1)).slots().stream().filter(s->s.startAt().equals(START)).findFirst().orElseThrow().isSelectable()).isTrue();
        authenticate(staff); processing.approve(first.getId(),staff.getId(),true);
        assertThat(reservations.findById(second.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.REJECTED);
        assertThatThrownBy(() -> submissions.submit(other.getEmail(),form,null)).hasMessageContaining("tidak lagi tersedia");
        assertThatThrownBy(() -> submissions.submit(other.getEmail(),form(room,START.plusHours(2),START.plusHours(3),1),null)).hasMessageContaining("Alat");
    }
    @Test void stockReductionUsesPeakAndIncreasesPreserveAllocations() {
        reserve(requester,equipment,START,START.plusHours(1),12,ReservationStatus.PENDING,NOW.plusHours(12));
        reserve(other,equipment,START.plusHours(1),START.plusHours(2),10,ReservationStatus.APPROVED,NOW.plusHours(12));
        assertThatThrownBy(() -> facilityService.updateFacility(admin.getId(),equipment.getId(),stock(11))).hasMessageContaining("alokasi");
        assertThat(facilityService.updateFacility(admin.getId(),equipment.getId(),stock(12)).getTotalStock()).isEqualTo(12);
        assertThat(facilityService.updateFacility(admin.getId(),equipment.getId(),stock(30)).getTotalStock()).isEqualTo(30);
        assertThat(reservations.count()).isEqualTo(2);
    }
    @Test void stockMutationsAreAdminOnlyAndValidated() {
        assertThatThrownBy(() -> facilityService.updateFacility(staff.getId(),equipment.getId(),stock(30))).hasMessageContaining("Admin");
        assertThatThrownBy(() -> facilityService.updateFacility(requester.getId(),equipment.getId(),stock(30))).hasMessageContaining("Admin");
        assertThatThrownBy(() -> facilityService.updateFacility(admin.getId(),equipment.getId(),stock(0))).hasMessageContaining("Stok");
        var dto=stock(3);dto.setCode("NEW-EQUIPMENT");
        assertThat(facilityService.createFacility(admin.getId(),dto).getTotalStock()).isEqualTo(3);
    }
    @Test void emptyFacilityCanChangeSemanticsWithExplicitStock() {
        Long classroomType = room.getFacilityType().getId();
        var dto = stock(3); dto.setCode(room.getCode()); dto.setName(room.getName());
        Facility converted = facilityService.updateFacility(admin.getId(), room.getId(), dto);
        assertThat(converted.isEquipment()).isTrue();
        assertThat(converted.getTotalStock()).isEqualTo(3);
        dto.setFacilityTypeId(classroomType); dto.setTotalStock(null);
        Facility restored = facilityService.updateFacility(admin.getId(), room.getId(), dto);
        assertThat(restored.isEquipment()).isFalse();
        assertThat(restored.getTotalStock()).isNull();
    }
    @Test void displayNameEditsPreserveQuantitySemantics() {
        var dto = new com.github.kafeyangasli.prism.feature.facility.dto.FacilityTypeDto();
        dto.setName("Perlengkapan");
        typeService.update(equipment.getFacilityType().getId(), dto);
        assertThat(facilities.findById(equipment.getId()).orElseThrow().isEquipment()).isTrue();
        assertThatThrownBy(() -> submit(requester, 6, 60, false)).hasMessageContaining("Proposal");
        dto.setName("Aula");
        assertThatThrownBy(() -> typeService.update(equipment.getFacilityType().getId(), dto)).hasMessageContaining("Aula");
        dto.setCode("ALAT");
        assertThatThrownBy(() -> typeService.create(dto)).hasMessageContaining("Aula");
    }
    @Test void historicalReservationsPreventSemanticTypeChanges() {
        Reservation request=submit(requester,1,60,false); var dto=stock(null);dto.setFacilityTypeId(room.getFacilityType().getId());
        assertThatThrownBy(() -> facilityService.updateFacility(admin.getId(),equipment.getId(),dto)).hasMessageContaining("riwayat");
        assertThat(reservations.findById(request.getId()).orElseThrow().getQuantity()).isEqualTo(1);
    }
    @Test void fullBlockageCancelsAllAllocationsAndExtensionBlocksNewIntervals() {
        Reservation first=submit(requester,5,60,false), second=submit(other,5,60,false); authenticate(staff);
        processing.approve(first.getId(),staff.getId(),false);
        var type=blockageTypes.findByCodeIgnoreCase("PLANNED_MAINTENANCE").orElseGet(() -> blockageTypes.saveAndFlush(new BlockageType("PLANNED_MAINTENANCE","Pemeliharaan",null)));
        var preview=new BlockageImpactPreviewRequest(equipment.getId(),START,START.plusHours(1));
        preview.setBlockageTypeId(type.getId());preview.setPublicReason("Pemeliharaan mikrofon");
        var create=new CreateBlockageRequest(equipment.getId(),type.getId(),null,START,START.plusHours(1),"Pemeliharaan mikrofon",null);
        create.setConfirmed(true);create.setConfirmationToken(blockageService.previewBlockageImpact(preview).getConfirmationToken());
        var blockage=blockageService.createBlockage(create);
        assertThat(reservations.findById(first.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(reservations.findById(second.getId()).orElseThrow().getStatus()).isEqualTo(ReservationStatus.REJECTED);
        assertThatThrownBy(() -> submit(requester,1,60,false)).hasMessageContaining("diblokir");
        var update=new UpdateBlockageRequest();update.setPlannedEndAt(START.plusHours(2));
        update.setConfirmationToken(blockageService.previewUpdate(blockage.getId(),update).getConfirmationToken());update.setConfirmed(true);
        blockageService.updateOrExtendBlockage(blockage.getId(),update);
        assertThat(available(START.plusHours(1),START.plusHours(2))).isZero();
    }
    @Test void simultaneousRequestsCannotOverbook() throws Exception {
        reserve(requester,equipment,START,START.plusHours(1),10,ReservationStatus.APPROVED,NOW.plusHours(12));
        var bothRead = new CyclicBarrier(2);
        assertThat(race(coordinatedSubmission(requester, 8, bothRead), coordinatedSubmission(other, 8, bothRead)))
                .containsExactlyInAnyOrder(true,false);
        assertThat(available(START,START.plusHours(1))).isEqualTo(2);
    }
    @Test void simultaneousSubmissionAndFullBlockageCannotLeaveLiveAllocation() throws Exception {
        var type = blockageTypes.findByCodeIgnoreCase("PLANNED_MAINTENANCE").orElseGet(() ->
                blockageTypes.saveAndFlush(new BlockageType("PLANNED_MAINTENANCE", "Pemeliharaan", null)));
        var preview = new BlockageImpactPreviewRequest(equipment.getId(), START, START.plusHours(1));
        preview.setBlockageTypeId(type.getId()); preview.setPublicReason("Pemeliharaan alat");
        authenticate(staff);
        var create = new CreateBlockageRequest(equipment.getId(), type.getId(), null, START, START.plusHours(1), "Pemeliharaan alat", null);
        create.setConfirmed(true); create.setConfirmationToken(blockageService.previewBlockageImpact(preview).getConfirmationToken());
        race(() -> trySubmit(other, 5), () -> {
            authenticate(staff);
            try {
                try { blockageService.createBlockage(create); }
                catch (BusinessRuleException changed) {
                    assertThat(changed.getCode()).isEqualTo("IMPACT_CHANGED");
                    create.setConfirmationToken(blockageService.previewBlockageImpact(preview).getConfirmationToken());
                    blockageService.createBlockage(create);
                }
                return true;
            } finally { SecurityContextHolder.clearContext(); }
        });
        assertThat(blockages.count()).isEqualTo(1);
        assertThat(reservations.findAll()).allMatch(r -> r.getStatus() == ReservationStatus.REJECTED);
        assertThat(inventory.maximumFutureAllocated(equipment)).isZero();
        assertThat(available(START, START.plusHours(1))).isZero();
    }
    @Test void concurrentStockReductionAndSubmissionRemainSafe() throws Exception {
        var results=race(()->trySubmit(requester,8),()->{
            try {facilityService.updateFacility(admin.getId(),equipment.getId(),stock(5));return true;}
            catch(BusinessRuleException expected){return false;}
        });
        assertThat(results).containsExactlyInAnyOrder(true,false);
        Facility current=facilities.findById(equipment.getId()).orElseThrow();
        assertThat(inventory.maximumFutureAllocated(current)).isLessThanOrEqualTo(current.getTotalStock().longValue());
    }
    @Test void quantityUiAndPublicPrivacy() throws Exception {
        reserve(requester,equipment,START,START.plusHours(1),5,ReservationStatus.PENDING,NOW.plusHours(12));
        String html=mvc.perform(get("/facilities/"+equipment.getId()+"/availability").param("date",START.toLocalDate().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(html).contains("Tersedia 15/20 unit", "Dialokasikan 5 unit").doesNotContain(requester.getEmail(),"Private purpose");
        snapshot("equipment-public", html);
        String form=mvc.perform(get("/reservations/availability").with(user(new PrismUserDetails(requester))).param("facilityId",equipment.getId().toString())
                .param("date",START.toLocalDate().toString()).param("startAt",START.toString()).param("endAt",START.plusHours(1).toString()).param("quantity","6"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(form).contains("name=\"quantity\"", "max=\"15\"", "data-proposal-required=\"true\"");
        snapshot("equipment-fragment", form);
        String fullForm = mvc.perform(get("/reservations/new").with(user(new PrismUserDetails(requester)))
                .param("facilityId", equipment.getId().toString()).param("date", START.toLocalDate().toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(fullForm).contains("name=\"quantity\"", "Lebih dari 5 unit");
        snapshot("equipment-form", fullForm);
    }

    @Test void facilityCardsAndDetailsShowAvailableEquipmentStockInsteadOfCapacity() throws Exception {
        reserve(requester, equipment, NOW.minusHours(1), NOW.plusHours(1), 6,
                ReservationStatus.APPROVED, NOW.plusHours(12));
        reserve(other, equipment, NOW.minusHours(1), NOW.plusHours(1), 20,
                ReservationStatus.CANCELLED, NOW.plusHours(12));
        var renamed = types.findById(equipment.getFacilityType().getId()).orElseThrow();
        renamed.updateDetails("Peralatan audio", null);
        types.saveAndFlush(renamed);

        String catalogue = mvc.perform(get("/facilities").param("type", "ALAT"))
                .andExpect(status().isOk()).andExpect(model().attribute("availableStock", Map.of(equipment.getId(), 14)))
                .andReturn().getResponse().getContentAsString();
        String card = catalogue.substring(catalogue.indexOf("<article"), catalogue.indexOf("</article>") + 10);
        assertThat(card).contains("Stok tersedia", "14 unit", "Saat ini")
                .doesNotContain("Kapasitas", "orang", "20 unit");
        String detail = mvc.perform(get("/facilities/" + equipment.getId()))
                .andExpect(status().isOk()).andExpect(model().attribute("availableStock", 14))
                .andReturn().getResponse().getContentAsString();
        assertThat(detail).contains("Stok tersedia", "14 unit").doesNotContain("Kapasitas", "Total stok alat");

        String roomCatalogue = mvc.perform(get("/facilities").param("type", "Kelas"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(roomCatalogue).contains("Kapasitas", "20 orang").doesNotContain("Stok tersedia");
        String roomDetail = mvc.perform(get("/facilities/" + room.getId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(roomDetail).contains("Kapasitas").doesNotContain("Stok tersedia");
    }

    @Test void facilityStockUsesSelectedDateAndCountsPendingAllocationsInHtmxResults() throws Exception {
        reserve(requester, equipment, START, START.plusHours(1), 5,
                ReservationStatus.PENDING, NOW.plusHours(12));
        reserve(other, equipment, START.plusMinutes(30), START.plusHours(2), 3,
                ReservationStatus.APPROVED, NOW.plusHours(12));
        reserve(requester, equipment, START, START.plusHours(2), 20,
                ReservationStatus.PENDING, NOW);

        String html = mvc.perform(get("/facilities").param("type", "ALAT")
                        .param("date", START.toLocalDate().toString())
                        .header("HX-Request", "true").header("HX-Target", "facility-results"))
                .andExpect(status().isOk()).andExpect(model().attribute("availableStock", Map.of(equipment.getId(), 12)))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Stok tersedia", "12 unit", "Pada tanggal yang dipilih");
        mvc.perform(get("/facilities/" + equipment.getId())).andExpect(status().isOk())
                .andExpect(model().attribute("availableStock", 20));
    }

    @Test void unavailableEquipmentDisplaysZeroStock() throws Exception {
        var maintenance = blockageTypes.saveAndFlush(new BlockageType("DISPLAY-MAINTENANCE", "Pemeliharaan", null));
        var blockage = blockages.saveAndFlush(new FacilityBlockage(equipment, maintenance, null,
                NOW.minusHours(1), NOW.plusHours(1), BlockageStatus.ACTIVE, "Pemeliharaan", null, admin));
        mvc.perform(get("/facilities").param("type", "ALAT")).andExpect(status().isOk())
                .andExpect(model().attribute("availableStock", Map.of(equipment.getId(), 0)));
        String blocked = mvc.perform(get("/facilities/" + equipment.getId())).andExpect(status().isOk())
                .andExpect(model().attribute("availableStock", 0)).andReturn().getResponse().getContentAsString();
        assertThat(blocked).contains("Stok tersedia", "0 unit").doesNotContain("Kapasitas", "Total stok alat");
        blockages.delete(blockage);
        equipment.setAdministrativeStatus(AdministrativeStatus.INACTIVE);
        facilities.saveAndFlush(equipment);
        mvc.perform(get("/facilities/" + equipment.getId())).andExpect(status().isOk())
                .andExpect(model().attribute("availableStock", 0));
    }
    @Test void adminAndStaffPagesShowStockAndQuantityWithoutCascade() throws Exception {
        submit(requester, 5, 60, false);
        submit(other, 5, 60, false);
        String adminHtml = mvc.perform(get("/admin/facilities").with(user(new PrismUserDetails(admin))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(adminHtml).contains("name=\"totalStock\"", "Alokasi per waktu", "20 unit");
        snapshot("equipment-admin", adminHtml);
        String staffHtml = mvc.perform(get("/staff/reservations").with(user(new PrismUserDetails(staff))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(staffHtml).contains("5 unit").doesNotContain("Setujui dan tolak konflik");
        snapshot("equipment-staff", staffHtml);
    }
    void snapshot(String name, String html) throws Exception {
        if (!Boolean.getBoolean("prism.ui.snapshots")) return;
        var directory = java.nio.file.Path.of("target/ui-preview");
        java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.writeString(directory.resolve(name + ".html"), html);
    }
    @Test void stockEndpointsDenyNonAdminsAndCsrfStaysEnabled() throws Exception {
        for(User actor:List.of(requester,staff)) mvc.perform(post("/admin/facilities/"+equipment.getId()+"/update").with(user(new PrismUserDetails(actor))).with(csrf()).param("totalStock","30")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/facilities/"+equipment.getId()+"/update").with(user(new PrismUserDetails(admin))).param("totalStock","30")).andExpect(status().isForbidden());
        Reservation pending=submit(requester,1,60,false);
        mvc.perform(post("/staff/reservations/"+pending.getId()+"/approve").with(user(new PrismUserDetails(requester))).with(csrf())).andExpect(status().isForbidden());
    }
    @Test void fractionalAndOverflowQuantitiesReturnFormErrors() throws Exception {
        for(String invalid:List.of("1.5","2147483648","abc")) mvc.perform(multipart("/reservations").with(user(new PrismUserDetails(requester))).with(csrf())
            .param("facilityId",equipment.getId().toString()).param("quantity",invalid).param("purpose","Private purpose")
            .param("date",START.toLocalDate().toString()).param("startAt",START.toString()).param("endAt",START.plusHours(1).toString()))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("reservationForm","quantity"));
        assertThat(reservations.count()).isZero();
    }
    int available(LocalDateTime start,LocalDateTime end) { return inventory.available(facilities.findById(equipment.getId()).orElseThrow(),start,end); }
    User createUser(String name,Role role) { return users.saveAndFlush(new User(name,name.toLowerCase()+"@equipment.test","hash",role,AccountStatus.ACTIVE)); }
    void authenticate(User actor) { var p=new PrismUserDetails(actor);SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(p,"hash",p.getAuthorities())); }
    Reservation submit(User user,Integer quantity,int minutes,boolean proposal) {
        var upload=proposal?new MockMultipartFile("proposal","signed.pdf","application/pdf","%PDF-1.4 fixture".getBytes()):null;
        return submissions.submit(user.getEmail(),form(equipment,START,START.plusMinutes(minutes),quantity),upload);
    }
    Callable<Boolean> coordinatedSubmission(User user, int quantity, CyclicBarrier reads) {
        return () -> { PRE_LOCK_READS.set(reads); try { return trySubmit(user, quantity); } finally { PRE_LOCK_READS.remove(); } };
    }
    boolean trySubmit(User user,int quantity) { try {submit(user,quantity,60,true);return true;}catch(BusinessRuleException expected){return false;} }
    ReservationForm form(Facility facility,LocalDateTime start,LocalDateTime end,Integer quantity) {
        var form=new ReservationForm();form.setFacilityId(facility.getId());form.setStartAt(start.toString());form.setEndAt(end.toString());form.setDate(start.toLocalDate().toString());form.setQuantity(quantity);form.setPurpose("Private purpose");return form;
    }
    Reservation reserve(User user,Facility facility,LocalDateTime start,LocalDateTime end,Integer quantity,ReservationStatus status,LocalDateTime expiry) {
        var r=new Reservation(user,facility,start,end,"Private purpose",null,status,expiry);r.setQuantity(quantity);return reservations.saveAndFlush(r);
    }
    FacilityDto stock(Integer amount) { var dto=new FacilityDto();dto.setCode(equipment.getCode());dto.setName(equipment.getName());dto.setFacilityTypeId(equipment.getFacilityType().getId());dto.setLocation("Gedung A");dto.setCapacity(20);dto.setTotalStock(amount);return dto; }
    List<Boolean> race(Callable<Boolean> first,Callable<Boolean> second) throws Exception {
        CyclicBarrier ready=new CyclicBarrier(2);var executor=Executors.newFixedThreadPool(2);
        try {var a=executor.submit(()->{ready.await(15,TimeUnit.SECONDS);return first.call();});var b=executor.submit(()->{ready.await(15,TimeUnit.SECONDS);return second.call();});return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));}
        finally{executor.shutdownNow();}
    }
    @TestConfiguration static class TimeConfiguration {
        @Bean @Primary Clock equipmentClock() {
            var zone=ZoneId.of("Asia/Jakarta");return Clock.fixed(NOW.atZone(zone).toInstant(),zone);
        }
        @Bean static org.springframework.beans.factory.config.BeanPostProcessor coordinateSubmissionReads() {
            return new org.springframework.beans.factory.config.BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof UserRepository)) return bean;
                    var proxy = new org.springframework.aop.framework.ProxyFactory(bean);
                    proxy.addAdvice((org.aopalliance.intercept.MethodInterceptor) invocation -> {
                        Object result = invocation.proceed();
                        var barrier = PRE_LOCK_READS.get();
                        if (invocation.getMethod().getName().equals("findIdByEmail") && barrier != null)
                            barrier.await(15, TimeUnit.SECONDS);
                        return result;
                    });
                    return proxy.getProxy();
                }
            };
        }
    }
}
