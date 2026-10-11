package com.github.kafeyangasli.prism.security;

import com.github.kafeyangasli.prism.feature.user.model.AccountStatus;
import com.github.kafeyangasli.prism.feature.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

// Created only inside the security chain, avoiding servlet-container double registration.
public class AccountStatusFilter extends OncePerRequestFilter {
    private final UserRepository users;

    public AccountStatusFilter(UserRepository users) { this.users = users; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        // Read DB state on every request even when the SecurityContext came from a cached session.
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof PrismUserDetails principal) {
            var state = users.findAuthenticationStateById(principal.getAccountId()).orElse(null);
            boolean active = state != null && state.getAccountStatus() == AccountStatus.ACTIVE;
            if (!active || !principal.matchesStoredCredentials(state.getPasswordHash())) {
                new SecurityContextLogoutHandler().logout(request, response, authentication);
                response.sendRedirect(request.getContextPath() + (active ? "/login?passwordChanged" : "/login?inactive"));
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
