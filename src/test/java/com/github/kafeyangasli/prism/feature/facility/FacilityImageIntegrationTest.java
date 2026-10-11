package com.github.kafeyangasli.prism.feature.facility;

import com.github.kafeyangasli.prism.feature.facility.dto.FacilityDto;
import com.github.kafeyangasli.prism.feature.facility.model.*;
import com.github.kafeyangasli.prism.feature.facility.repository.*;
import com.github.kafeyangasli.prism.feature.facility.service.*;
import com.github.kafeyangasli.prism.feature.user.model.*;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.shared.exception.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static com.github.kafeyangasli.prism.support.PrismTestUsers.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:facility-images;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "prism.facility-images.max-count=4", "prism.facility-images.max-bytes=1024"})
@AutoConfigureMockMvc
class FacilityImageIntegrationTest {
    @TempDir static Path directory;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("prism.storage.facilities", () -> directory.toString());
    }
    @Autowired FacilityImageService service;
    @Autowired FacilityService facilities;
    @Autowired FacilityRepository facilityRepository;
    @Autowired FacilityImageRepository images;
    @Autowired FacilityTypeRepository types;
    @Autowired UserRepository users;
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    User admin;
    FacilityType type;

    @BeforeEach void seed() {
        String key = UUID.randomUUID().toString();
        admin = users.save(new User("Admin", key + "@example.test", "hash", Role.ADMIN, AccountStatus.ACTIVE));
        type = types.save(new FacilityType(key, "Ruang " + key, null));
        // Hibernate's portable schema omits the MySQL generated key; exercise its equivalent in H2.
        jdbc.execute("ALTER TABLE facility_images ADD COLUMN IF NOT EXISTS thumbnail_facility_id BIGINT GENERATED ALWAYS AS (CASE WHEN is_thumbnail THEN facility_id ELSE NULL END)");
        jdbc.execute("CREATE UNIQUE INDEX IF NOT EXISTS uk_facility_images_thumbnail ON facility_images(thumbnail_facility_id)");
    }
    FacilityDto dto() {
        FacilityDto dto = new FacilityDto();
        dto.setCode(UUID.randomUUID().toString()); dto.setName("Ruang galeri");
        dto.setFacilityTypeId(type.getId()); dto.setLocation("Gedung A"); dto.setCapacity(20);
        dto.setDescription("Deskripsi awal"); return dto;
    }
    MockMultipartFile png() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("images", "../../unsafe.html", "text/html", output.toByteArray());
    }
    Facility create(Integer selection) throws Exception {
        return service.create(admin.getId(), dto(), List.of(png(), png()), selection);
    }
    List<FacilityImage> gallery(Facility f) { return service.ordered(f.getId()); }
    void oneThumbnail(Facility f, Long id) {
        assertThat(gallery(f).stream().filter(FacilityImage::isThumbnail).map(FacilityImage::getId).toList()).containsExactly(id);
    }

    @Test void createsMultipleImagesAndDefaultsToFirst() throws Exception {
        Facility f = create(null);
        assertThat(gallery(f)).hasSize(2);
        assertThat(gallery(f)).extracting(FacilityImage::getDisplayOrder).containsExactly(0, 1);
        oneThumbnail(f, gallery(f).getFirst().getId());
        for (FacilityImage image : gallery(f)) assertThat(directory.resolve(image.getStoragePath())).exists();
    }
    @Test void createsWithoutImagesAndAcceptsEmptyOptionalBrowserPart() {
        Facility f = service.create(admin.getId(), dto(), null, null);
        assertThat(gallery(f)).isEmpty();
        Facility empty = service.create(admin.getId(), dto(), List.of(new MockMultipartFile("images", "", "application/octet-stream", new byte[0])), null);
        assertThat(gallery(empty)).isEmpty();
    }
    @Test void selectsThumbnailDuringCreation() throws Exception {
        Facility f = create(1); oneThumbnail(f, gallery(f).get(1).getId());
    }
    @Test void additionsPreserveThumbnailAndCanExplicitlyChangeIt() throws Exception {
        Facility f = create(1); Long current = gallery(f).get(1).getId();
        service.add(admin.getId(), f.getId(), List.of(png()), null);
        oneThumbnail(f, current);
        service.add(admin.getId(), f.getId(), List.of(png()), 0);
        assertThat(gallery(f)).hasSize(4); oneThumbnail(f, gallery(f).getLast().getId());
    }
    @Test void thumbnailCanChangeWithoutUploading() throws Exception {
        Facility f = create(null); Long next = gallery(f).get(1).getId();
        service.selectThumbnail(admin.getId(), f.getId(), next); oneThumbnail(f, next);
    }
    @Test void replacementPreservesIdentityOrderAndThumbnailAndDeletesOldFile() throws Exception {
        Facility f = create(null); FacilityImage before = gallery(f).getFirst();
        service.replace(admin.getId(), f.getId(), before.getId(), png());
        FacilityImage after = gallery(f).getFirst();
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getDisplayOrder()).isEqualTo(before.getDisplayOrder());
        assertThat(after.getStoragePath()).isNotEqualTo(before.getStoragePath());
        assertThat(directory.resolve(before.getStoragePath())).doesNotExist();
        oneThumbnail(f, before.getId());
    }
    @Test void deletionPreservesOrReassignsThumbnailAndAllowsEmptyGallery() throws Exception {
        Facility f = create(null); List<FacilityImage> before = gallery(f);
        service.delete(admin.getId(), f.getId(), before.get(1).getId());
        oneThumbnail(f, before.getFirst().getId());
        service.add(admin.getId(), f.getId(), List.of(png()), null);
        Long next = gallery(f).get(1).getId();
        service.delete(admin.getId(), f.getId(), before.getFirst().getId()); oneThumbnail(f, next);
        service.delete(admin.getId(), f.getId(), next); assertThat(gallery(f)).isEmpty();
        for (FacilityImage image : before) assertThat(directory.resolve(image.getStoragePath())).doesNotExist();
    }
    @Test void invalidBatchRollsBackFacilityAndPreviouslyWrittenFiles() throws Exception {
        FacilityDto dto = dto(); long count = fileCount();
        assertThatThrownBy(() -> service.create(admin.getId(), dto, List.of(png(), new MockMultipartFile("images", "fake.png", "image/png", "invalid".getBytes())), null))
                .isInstanceOf(BusinessRuleException.class);
        assertThat(facilityRepository.findByCodeIgnoreCase(dto.getCode())).isEmpty();
        assertThat(fileCount()).isEqualTo(count);
    }
    @Test void rejectsOversizeEmptyCountAndInvalidSelections() throws Exception {
        Facility f = create(null);
        assertThatThrownBy(() -> service.add(admin.getId(), f.getId(), List.of(new MockMultipartFile("images", new byte[1025])), null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.replace(admin.getId(), f.getId(), gallery(f).getFirst().getId(), new MockMultipartFile("image", "empty.png", "image/png", new byte[0]))).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.add(admin.getId(), f.getId(), List.of(png(), png(), png()), null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.add(admin.getId(), f.getId(), List.of(png()), -1)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.add(admin.getId(), f.getId(), List.of(png()), 1)).isInstanceOf(BusinessRuleException.class);
        assertThat(gallery(f)).hasSize(2);
    }
    @Test void crossFacilityChangesAndNonAdminServiceCallsAreRejected() throws Exception {
        Facility f = create(null); Facility other = create(null); Long foreign = gallery(other).getFirst().getId();
        assertThatThrownBy(() -> service.selectThumbnail(admin.getId(), f.getId(), foreign)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.delete(admin.getId(), f.getId(), foreign)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.replace(admin.getId(), f.getId(), foreign, png())).isInstanceOf(ResourceNotFoundException.class);
        User user = users.save(new User("User", UUID.randomUUID() + "@example.test", "hash", Role.PENGGUNA, AccountStatus.ACTIVE));
        assertThatThrownBy(() -> service.add(user.getId(), f.getId(), List.of(png()), null)).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> service.selectThumbnail(null, f.getId(), gallery(f).getFirst().getId())).isInstanceOf(BusinessRuleException.class);
    }
    @Test void rollbackKeepsOriginalFileAndCleansReplacement() throws Exception {
        Facility f = create(null); FacilityImage original = gallery(f).getFirst(); long count = fileCount();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            try { service.replace(admin.getId(), f.getId(), original.getId(), png()); }
            catch (Exception e) { throw new RuntimeException(e); }
            tx.setRollbackOnly();
        });
        assertThat(gallery(f).getFirst().getStoragePath()).isEqualTo(original.getStoragePath());
        assertThat(directory.resolve(original.getStoragePath())).exists();
        assertThat(fileCount()).isEqualTo(count);
    }
    @Test void concurrentSelectionsAreSerializedAndDatabaseAlsoRejectsDuplicates() throws Exception {
        Facility f = create(null); List<FacilityImage> gallery = gallery(f);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> futures = new ArrayList<>();
            for (FacilityImage image : gallery) futures.add(executor.submit(() -> {
                try { start.await(); service.selectThumbnail(admin.getId(), f.getId(), image.getId()); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            }));
            start.countDown(); for (Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
        }
        assertThat(gallery(f).stream().filter(FacilityImage::isThumbnail).toList()).hasSize(1);
        Long notSelected = gallery(f).stream().filter(i -> !i.isThumbnail()).findFirst().orElseThrow().getId();
        assertThatThrownBy(() -> jdbc.update("UPDATE facility_images SET is_thumbnail = TRUE WHERE id = ?", notSelected))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void publicGalleryCardsAndImageDeliveryRenderWithoutStoragePaths() throws Exception {
        Facility f = create(1); FacilityImage thumb = gallery(f).get(1);
        String detail = mvc.perform(get("/facilities/" + f.getId())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(detail).contains("data-gallery-main", "data-gallery-choice", thumb.getUrl()).doesNotContain(thumb.getStoragePath(), directory.toString());
        String listing = mvc.perform(get("/facilities")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(listing).contains(thumb.getUrl(), "facility-image-card");
        mvc.perform(get(thumb.getUrl())).andExpect(status().isOk()).andExpect(content().contentType("image/png")).andExpect(header().string("X-Content-Type-Options", "nosniff"));
        mvc.perform(get("/facilities/999999/images/" + thumb.getId())).andExpect(status().isNotFound());
        Files.delete(directory.resolve(thumb.getStoragePath()));
        mvc.perform(get(thumb.getUrl())).andExpect(status().isOk()).andExpect(content().contentType("image/svg+xml"));
        Facility empty = service.create(admin.getId(), dto(), null, null);
        mvc.perform(get("/facilities/" + empty.getId())).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("facility-placeholder.svg")));
    }
    @Test void adminMultipartEndpointsCsrfAndImageOnlyEditsWork() throws Exception {
        Facility f = create(null); FacilityImage image = gallery(f).getFirst();
        String uploadUrl = "/admin/facilities/" + f.getId() + "/images";
        mvc.perform(multipart(uploadUrl).file(png()).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(multipart(uploadUrl).file(png()).with(user("member").roles("PENGGUNA")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(multipart(uploadUrl).file(png()).with(user(admin.getEmail()).roles("ADMIN"))).andExpect(status().isForbidden());
        for (String operation : List.of("delete", "thumbnail", "replace")) {
            String url = "/admin/facilities/" + f.getId() + "/images/" + image.getId() + "/" + operation;
            mvc.perform(post(url).with(csrf())).andExpect(status().is3xxRedirection());
            mvc.perform(post(url).with(user("member").roles("PENGGUNA")).with(csrf())).andExpect(status().isForbidden());
            mvc.perform(post(url).with(user(admin.getEmail()).roles("ADMIN"))).andExpect(status().isForbidden());
        }
        mvc.perform(multipart("/admin/facilities/" + f.getId() + "/images").file(png())
                .with(user(admin.getEmail()).roles("ADMIN")).with(csrf()).header("HX-Request", "true"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Galeri berhasil diperbarui")));
        assertThat(gallery(f)).hasSize(3);
        Facility current = facilities.getFacilityById(f.getId());
        assertThat(current.getName()).isEqualTo(f.getName()); assertThat(current.getDescription()).isEqualTo("Deskripsi awal");
        mvc.perform(get("/admin/facilities").with(user(admin.getEmail()).roles("ADMIN"))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("multipart/form-data")));
    }
    @Test void creationControllerAcceptsMultipleImagesAndCrudStillWorks() throws Exception {
        FacilityDto dto = dto();
        mvc.perform(multipart("/admin/facilities").file(png()).file(png()).param("thumbnailIndex", "1")
                .param("code", dto.getCode()).param("name", dto.getName()).param("facilityTypeId", type.getId().toString())
                .param("location", dto.getLocation()).param("capacity", "20")
                .with(user(admin.getEmail()).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        Facility f = facilityRepository.findByCodeIgnoreCase(dto.getCode()).orElseThrow();
        oneThumbnail(f, gallery(f).get(1).getId());
        dto.setName("Nama baru"); facilities.updateFacility(admin.getId(), f.getId(), dto);
        facilities.deactivateFacility(admin.getId(), f.getId()); facilities.activateFacility(admin.getId(), f.getId());
        assertThat(facilities.getFacilityById(f.getId()).getName()).isEqualTo("Nama baru");
        assertThat(gallery(f)).hasSize(2);
    }
    @Test void managementEndpointsReplaceSelectDeleteAndRejectForeignIds() throws Exception {
        Facility f = create(null); Facility other = create(null);
        Long imageId = gallery(f).get(1).getId();
        String base = "/admin/facilities/" + f.getId() + "/images/";
        mvc.perform(post(base + imageId + "/thumbnail").with(user(admin.getEmail()).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        oneThumbnail(f, imageId);
        MockMultipartFile replacement = new MockMultipartFile("image", "new.png", "image/png", png().getBytes());
        mvc.perform(multipart(base + imageId + "/replace").file(replacement).with(user(admin.getEmail()).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        oneThumbnail(f, imageId);
        mvc.perform(post(base + gallery(other).getFirst().getId() + "/delete").with(user(admin.getEmail()).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("errorMessage"));
        assertThat(gallery(other)).hasSize(2);
        mvc.perform(post(base + imageId + "/delete").with(user(admin.getEmail()).roles("ADMIN")).with(csrf()).header("HX-Request", "true"))
                .andExpect(status().isOk());
        assertThat(gallery(f)).hasSize(1); oneThumbnail(f, gallery(f).getFirst().getId());
    }
    @Test void htmxCreationRefreshesListingAndResetsMultipartForm() throws Exception {
        FacilityDto dto = dto();
        String html = mvc.perform(multipart("/admin/facilities").file(png()).param("code", dto.getCode())
                .param("name", dto.getName()).param("facilityTypeId", type.getId().toString())
                .param("location", dto.getLocation()).param("capacity", "20")
                .with(user(admin.getEmail()).roles("ADMIN")).with(csrf()).header("HX-Request", "true"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("id=\"facility-list\"", "hx-swap-oob=\"outerHTML\"", "id=\"facility-modal-region\"", "multipart/form-data");
        Facility f = facilityRepository.findByCodeIgnoreCase(dto.getCode()).orElseThrow();
        assertThat(html).contains(gallery(f).getFirst().getUrl());
    }
    private long fileCount() throws Exception { try (var files = Files.list(directory)) { return files.count(); } }
}
