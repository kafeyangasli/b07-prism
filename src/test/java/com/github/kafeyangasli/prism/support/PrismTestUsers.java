package com.github.kafeyangasli.prism.support;

import com.github.kafeyangasli.prism.security.PrismUserDetails;
import com.github.kafeyangasli.prism.feature.user.model.*;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;

public final class PrismTestUsers {
    private PrismTestUsers() {}
    public static PrismUserDetails principal(String username, com.github.kafeyangasli.prism.feature.user.repository.UserRepository users, String... roles) {
        if (roles.length != 1) throw new IllegalArgumentException("PRISM accounts have exactly one role");
        User account = users.findByEmailIgnoreCase(username).orElse(null);
        if (account == null) {
            account = new User(username, username, "hash", Role.valueOf(roles[0]), AccountStatus.ACTIVE);
            User saved = users.saveAndFlush(account);
            if (saved != null) account = saved;
        }
        User principalAccount = new User(account.getName(), account.getEmail(), "hash", Role.valueOf(roles[0]), AccountStatus.ACTIVE);
        org.springframework.test.util.ReflectionTestUtils.setField(principalAccount, "id", account.getId());
        return new PrismUserDetails(principalAccount);
    }
    public static Builder user(String username) { return new Builder(username); }
    public static final class Builder implements RequestPostProcessor {
        private final String username;
        private String[] roles = {"PENGGUNA"};
        private Builder(String username) { this.username = username; }
        public Builder roles(String... roles) { this.roles = roles; return this; }
        public MockHttpServletRequest postProcessRequest(MockHttpServletRequest request) {
            var context = org.springframework.web.context.support.WebApplicationContextUtils.getRequiredWebApplicationContext(request.getServletContext());
            var users = context.getBean(com.github.kafeyangasli.prism.feature.user.repository.UserRepository.class);
            return SecurityMockMvcRequestPostProcessors.user(principal(username, users, roles)).postProcessRequest(request);
        }
    }
}
