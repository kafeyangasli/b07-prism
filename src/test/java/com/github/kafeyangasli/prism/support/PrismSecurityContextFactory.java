package com.github.kafeyangasli.prism.support;

import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithSecurityContextFactory;

public class PrismSecurityContextFactory implements WithSecurityContextFactory<WithPrismUser> {
    @org.springframework.beans.factory.annotation.Autowired
    private com.github.kafeyangasli.prism.feature.user.repository.UserRepository users;
    public SecurityContext createSecurityContext(WithPrismUser annotation) {
        var principal = PrismTestUsers.principal(annotation.username(), users, annotation.roles());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, "hash", principal.getAuthorities()));
        return context;
    }
}
