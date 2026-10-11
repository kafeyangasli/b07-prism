package com.github.kafeyangasli.prism.feature.user;

import com.github.kafeyangasli.prism.feature.user.dto.AccountPasswordForm;
import com.github.kafeyangasli.prism.feature.user.model.*;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import com.github.kafeyangasli.prism.feature.user.service.AccountSettingsService;
import com.github.kafeyangasli.prism.security.PrismUserDetails;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.io.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:password-sessions;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureMockMvc
class PasswordSessionRevocationIntegrationTest {
    static final String EMAIL = "sessions@test", OLD = "old-password", NEW = "new-password";
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired MockMvc mvc;
    @Autowired AccountSettingsService service;
    @Autowired PlatformTransactionManager transactions;
    User owner;
    @BeforeEach void seed() {
        users.deleteAll();
        owner = users.saveAndFlush(new User("Owner", EMAIL, encoder.encode(OLD), Role.PENGGUNA, AccountStatus.ACTIVE));
    }
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }

    @Test void successfulChangeRevokesOtherDevicesAndSerializedCachedSessionsButNotAnotherAccount() throws Exception {
        var current = login(EMAIL, OLD);
        var otherDevice = login(EMAIL, OLD);
        var cached = cachedSession(otherDevice);
        users.saveAndFlush(new User("Other", "unaffected@test", encoder.encode(OLD), Role.PENGGUNA, AccountStatus.ACTIVE));
        var unaffected = login("unaffected@test", OLD);
        mvc.perform(post("/account/password").session(current).with(csrf()).param("currentPassword", OLD)
                        .param("newPassword", NEW).param("confirmPassword", NEW))
                .andExpect(redirectedUrl("/login?passwordChanged")).andExpect(unauthenticated());
        assertThat(current.isInvalid()).isTrue();
        revoked(otherDevice);
        revoked(cached);
        mvc.perform(get("/account").session(unaffected)).andExpect(status().isOk());
        mvc.perform(formLogin().user(EMAIL).password(OLD)).andExpect(unauthenticated());
        mvc.perform(get("/account").session(login(EMAIL, NEW))).andExpect(status().isOk());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void persistedPasswordResetRevokesAllOldSessionsIncludingResetToSamePassword(boolean samePassword) throws Exception {
        var first = login(EMAIL, OLD);
        var second = cachedSession(login(EMAIL, OLD));
        String resetPassword = samePassword ? OLD : NEW;
        // No administrative reset endpoint exists. Exercise its persistence primitive directly:
        // any committed replacement hash must invalidate sessions, regardless of the writer.
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            var account = users.findByIdForUpdate(owner.getId()).orElseThrow();
            account.setPasswordHash(encoder.encode(resetPassword));
        });
        revoked(first);
        revoked(second);
        mvc.perform(get("/account").session(login(EMAIL, resetPassword))).andExpect(status().isOk());
    }

    @Test void failedChangeKeepsExistingSessionsValid() throws Exception {
        var current = login(EMAIL, OLD);
        var other = cachedSession(login(EMAIL, OLD));
        String prior = users.findById(owner.getId()).orElseThrow().getPasswordHash();
        mvc.perform(post("/account/password").session(current).with(csrf()).param("currentPassword", "incorrect")
                        .param("newPassword", NEW).param("confirmPassword", NEW)).andExpect(status().isOk());
        assertThat(users.findById(owner.getId()).orElseThrow().getPasswordHash()).isEqualTo(prior);
        mvc.perform(get("/account").session(current)).andExpect(status().isOk());
        mvc.perform(get("/account").session(other)).andExpect(status().isOk());
    }

    @Test void rolledBackChangeDoesNotRevokeCommittedCredentials() throws Exception {
        var first = login(EMAIL, OLD);
        var second = cachedSession(login(EMAIL, OLD));
        var form = new AccountPasswordForm();
        form.setCurrentPassword(OLD); form.setNewPassword(NEW); form.setConfirmPassword(NEW);
        SecurityContextHolder.setContext((SecurityContext) first.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY));
        try {
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                service.changePassword(form);
                tx.setRollbackOnly();
            });
        } finally { SecurityContextHolder.clearContext(); }
        mvc.perform(get("/account").session(first)).andExpect(status().isOk());
        mvc.perform(get("/account").session(second)).andExpect(status().isOk());
        assertThat(encoder.matches(OLD, users.findById(owner.getId()).orElseThrow().getPasswordHash())).isTrue();
    }

    private MockHttpSession login(String email, String password) throws Exception {
        var result = mvc.perform(formLogin().user(email).password(password)).andExpect(authenticated()).andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
    private MockHttpSession cachedSession(MockHttpSession source) throws Exception {
        var context = (SecurityContext) source.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        assertThat(((PrismUserDetails) context.getAuthentication().getPrincipal()).getPassword()).isNull();
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) { output.writeObject(context); }
        SecurityContext cached;
        try (var input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) { cached = (SecurityContext) input.readObject(); }
        var session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, cached);
        mvc.perform(get("/account").session(session)).andExpect(status().isOk());
        return session;
    }
    private void revoked(MockHttpSession session) throws Exception {
        mvc.perform(get("/account").session(session)).andExpect(redirectedUrl("/login?passwordChanged")).andExpect(unauthenticated());
        assertThat(session.isInvalid()).isTrue();
    }
}
