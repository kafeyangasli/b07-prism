package com.github.kafeyangasli.prism.security;

import com.github.kafeyangasli.prism.feature.user.model.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.List;

public class PrismUserDetails extends org.springframework.security.core.userdetails.User {
    private final Long accountId;
    private final byte[] credentialFingerprint;
    private volatile String displayName;

    public PrismUserDetails(User user) {
        super(user.getEmail(), user.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
        this.accountId = user.getId();
        // Spring erases User.getPassword() after login; keep a one-way snapshot for revocation.
        this.credentialFingerprint = fingerprint(user.getPasswordHash());
        this.displayName = user.getName();
    }

    public Long getAccountId() { return accountId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public boolean matchesStoredCredentials(String passwordHash) {
        return credentialFingerprint != null && passwordHash != null
                && java.security.MessageDigest.isEqual(credentialFingerprint, fingerprint(passwordHash));
    }

    private static byte[] fingerprint(String passwordHash) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(passwordHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
