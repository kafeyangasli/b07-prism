package com.github.kafeyangasli.prism.feature.user;

import com.github.kafeyangasli.prism.feature.user.model.*;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.feature.user.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.access.AccessDeniedException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static com.github.kafeyangasli.prism.support.PrismTestUsers.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:avatars;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "prism.avatars.max-bytes=1024"
})
@AutoConfigureMockMvc
class AvatarIntegrationTest {
    @TempDir static Path directory;
    @DynamicPropertySource static void storage(DynamicPropertyRegistry registry) {
        registry.add("prism.storage.avatars", () -> directory.toString());
    }
    @Autowired UserRepository users;
    @Autowired AccountSettingsService accounts;
    @Autowired AvatarStorage storage;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager transactions;
    @Autowired MockMvc mvc;
    User owner;
    @BeforeEach void seed() {
        owner = users.saveAndFlush(new User("Avatar Owner", UUID.randomUUID() + "@example.test", encoder.encode("old-password"), Role.PENGGUNA, AccountStatus.ACTIVE));
    }
    @AfterEach void clearAuthentication() { SecurityContextHolder.clearContext(); }
    User current() { return users.findById(owner.getId()).orElseThrow(); }
    void asOwner() {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(owner.getEmail(), "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_" + owner.getRole().name()))));
        SecurityContextHolder.setContext(context);
    }
    void upload(String format) throws Exception {
        mvc.perform(multipart("/account/avatar").file(AvatarStorageTest.picture(format))
                .with(user(owner.getEmail()).roles(owner.getRole().name())).with(csrf()))
                .andExpect(redirectedUrl("/account")).andExpect(flash().attributeExists("successMessage"));
    }
    @ParameterizedTest @EnumSource(Role.class)
    void allRolesUploadReplaceAndRemoveOnlyTheirPicture(Role role) throws Exception {
        owner.setRole(role); users.saveAndFlush(owner);
        String hash = owner.getPasswordHash();
        upload("png"); String first = current().getProfilePicturePath();
        assertThat(directory.resolve(first)).exists();
        mvc.perform(get("/account/avatar").with(user(owner.getEmail()).roles(role.name())))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store"));
        upload("webp"); String next = current().getProfilePicturePath();
        assertThat(next).isNotEqualTo(first); assertThat(directory.resolve(first)).doesNotExist(); assertThat(directory.resolve(next)).exists();
        String html = mvc.perform(get("/account").with(user(owner.getEmail()).roles(role.name())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("multipart/form-data", "data-avatar-input", "data-avatar-preview", "Hapus Foto Profil", "/account/avatar")
                .doesNotContain(next, directory.toString(), "th:replace=");
        mvc.perform(post("/account/avatar/remove").with(user(owner.getEmail()).roles(role.name())).with(csrf()))
                .andExpect(redirectedUrl("/account"));
        assertThat(current().getProfilePicturePath()).isNull(); assertThat(directory.resolve(next)).doesNotExist();
        assertThat(current().getName()).isEqualTo(owner.getName()); assertThat(current().getEmail()).isEqualTo(owner.getEmail());
        assertThat(current().getRole()).isEqualTo(role); assertThat(current().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(current().getPasswordHash()).isEqualTo(hash);
    }
    @Test void fallbackIsSharedByAccountSidebarAndMobileNavigation() throws Exception {
        mvc.perform(get("/account/avatar").with(user(owner.getEmail()).roles("PENGGUNA")))
                .andExpect(status().isOk()).andExpect(content().contentType("image/svg+xml"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Foto profil bawaan")));
        String html = mvc.perform(get("/account").with(user(owner.getEmail()).roles("PENGGUNA")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("ui-avatar-large", "ui-avatar-small", "ui-avatar", "/js/avatar.js", "name=\"_csrf\"")
                .doesNotContain("Hapus Foto Profil");
        assertThat(html.split("src=\"/account/avatar\"", -1)).hasSize(4);
    }
    @Test void retrievalAlwaysUsesOwnAccountAndDoesNotExposeOtherPictures() throws Exception {
        upload("png"); String own = current().getProfilePicturePath();
        User other = users.saveAndFlush(new User("Other", UUID.randomUUID() + "@example.test", "hash", Role.ADMIN, AccountStatus.ACTIVE));
        byte[] ownBytes = storage.load(own);
        mvc.perform(get("/account/avatar").param("id", other.getId().toString()).with(user(owner.getEmail()).roles("PENGGUNA")))
                .andExpect(content().bytes(ownBytes));
        mvc.perform(multipart("/account/avatar").file(AvatarStorageTest.picture("jpeg"))
                .param("id", other.getId().toString()).param("userId", other.getId().toString())
                .param("email", other.getEmail()).param("role", "ADMIN").param("profilePicturePath", own)
                .with(user(owner.getEmail()).roles("PENGGUNA")).with(csrf())).andExpect(redirectedUrl("/account"));
        assertThat(users.findById(other.getId()).orElseThrow().getProfilePicturePath()).isNull();
        mvc.perform(post("/account/avatar/remove").param("userId", other.getId().toString())
                .with(user(other.getEmail()).roles("ADMIN")).with(csrf())).andExpect(redirectedUrl("/account"));
        assertThat(current().getProfilePicturePath()).isNotNull();
        mvc.perform(get("/account/avatar/" + owner.getId()).with(user(other.getEmail()).roles("ADMIN"))).andExpect(status().isNotFound());
    }
    @Test void failedValidationPreservesOldFileAndReference() throws Exception {
        upload("png"); String original = current().getProfilePicturePath(); long files = count();
        for (MockMultipartFile bad : List.of(new MockMultipartFile("image", new byte[0]),
                new MockMultipartFile("image", "fake.png", "image/png", "not an image".getBytes()),
                AvatarStorageTest.picture("gif"), new MockMultipartFile("image", new byte[1025]))) {
            mvc.perform(multipart("/account/avatar").file(bad).with(user(owner.getEmail()).roles("PENGGUNA")).with(csrf()))
                    .andExpect(redirectedUrl("/account")).andExpect(flash().attributeExists("errorMessage"));
            assertThat(current().getProfilePicturePath()).isEqualTo(original);
            assertThat(directory.resolve(original)).exists(); assertThat(count()).isEqualTo(files);
        }
        mvc.perform(post("/account/avatar").with(user(owner.getEmail()).roles("PENGGUNA")).with(csrf()))
                .andExpect(flash().attributeExists("errorMessage"));
    }
    @Test void rollbackPreservesOriginalReferenceAndCleansNewFile() throws Exception {
        upload("png"); String original = current().getProfilePicturePath(); long files = count(); asOwner();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            try { accounts.updateAvatar(AvatarStorageTest.picture("webp")); }
            catch (Exception e) { throw new RuntimeException(e); }
            tx.setRollbackOnly();
        });
        assertThat(current().getProfilePicturePath()).isEqualTo(original);
        assertThat(directory.resolve(original)).exists(); assertThat(count()).isEqualTo(files);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> { accounts.removeAvatar(); tx.setRollbackOnly(); });
        assertThat(current().getProfilePicturePath()).isEqualTo(original); assertThat(directory.resolve(original)).exists();
    }
    @Test void concurrentReplacementsSerializeAndLeaveExactlyOneNewReferencedFile() throws Exception {
        upload("png"); String first = current().getProfilePicturePath(); long files = count();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> futures = new ArrayList<>();
            for (String format : List.of("jpeg", "webp")) futures.add(executor.submit(() -> {
                asOwner();
                try { start.await(); accounts.updateAvatar(AvatarStorageTest.picture(format)); }
                catch (Exception e) { throw new RuntimeException(e); }
                finally { SecurityContextHolder.clearContext(); }
            }));
            start.countDown(); for (Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
        }
        assertThat(directory.resolve(first)).doesNotExist(); assertThat(directory.resolve(current().getProfilePicturePath())).exists();
        assertThat(count()).isEqualTo(files);
    }
    @Test void missingStoredFileReturnsTheSameFallback() throws Exception {
        upload("png"); Files.delete(directory.resolve(current().getProfilePicturePath()));
        mvc.perform(get("/account/avatar").with(user(owner.getEmail()).roles("PENGGUNA")))
                .andExpect(status().isOk()).andExpect(content().contentType("image/svg+xml"));
    }
    @Test void deactivationRetainsFileAndBlocksAuthenticatedAndAnonymousRetrieval() throws Exception {
        upload("png"); String name = current().getProfilePicturePath();
        MockHttpSession session = (MockHttpSession) mvc.perform(formLogin().user(owner.getEmail()).password("old-password"))
                .andExpect(authenticated()).andReturn().getRequest().getSession(false);
        mvc.perform(post("/account/delete").session(session).with(csrf()).param("confirmed", "true").param("currentPassword", "old-password"))
                .andExpect(redirectedUrl("/login?accountDeactivated"));
        assertThat(current().getAccountStatus()).isEqualTo(AccountStatus.INACTIVE);
        assertThat(current().getProfilePicturePath()).isEqualTo(name); assertThat(directory.resolve(name)).exists();
        mvc.perform(get("/account/avatar")).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/account/avatar").with(user(owner.getEmail()).roles("PENGGUNA"))).andExpect(redirectedUrl("/login?inactive"));
        asOwner();
        assertThatThrownBy(accounts::removeAvatar).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> accounts.updateAvatar(AvatarStorageTest.picture("png"))).isInstanceOf(AccessDeniedException.class);
    }
    @ParameterizedTest @ValueSource(strings = {"/account/avatar", "/account/avatar/remove"})
    void authenticationAndCsrfAreRequired(String path) throws Exception {
        mvc.perform(multipart(path).file(AvatarStorageTest.picture("png")).with(csrf())).andExpect(redirectedUrl("/login"));
        mvc.perform(multipart(path).file(AvatarStorageTest.picture("png")).with(user(owner.getEmail()).roles("PENGGUNA")))
                .andExpect(status().isForbidden());
        assertThat(current().getProfilePicturePath()).isNull();
    }
    @Test void profileRequestCannotInjectAStorageReference() throws Exception {
        mvc.perform(post("/account/profile").param("name", "Updated").param("profilePicturePath", "../../secret")
                .with(user(owner.getEmail()).roles("PENGGUNA")).with(csrf())).andExpect(redirectedUrl("/account"));
        assertThat(current().getName()).isEqualTo("Updated"); assertThat(current().getProfilePicturePath()).isNull();
    }
    private long count() throws Exception { try (var files = Files.list(directory)) { return files.count(); } }
}
