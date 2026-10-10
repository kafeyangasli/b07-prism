package com.github.kafeyangasli.prism.security;

import com.github.kafeyangasli.prism.feature.user.model.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;

public class PrismUserDetails extends org.springframework.security.core.userdetails.User {
    private final Long accountId;

    public PrismUserDetails(User user) {
        super(user.getEmail(), user.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        this.accountId = user.getId();
    }

    public Long getAccountId() { return accountId; }
}
