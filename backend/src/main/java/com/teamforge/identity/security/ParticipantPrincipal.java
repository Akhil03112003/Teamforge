package com.teamforge.identity.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public final class ParticipantPrincipal implements UserDetails, CredentialsContainer {
    private static final long serialVersionUID = 1L;
    private final UUID id;
    private final String email;
    private String passwordHash;

    public ParticipantPrincipal(UUID id, String email, String passwordHash) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
    }
    public UUID id() { return id; }
    @Override public String getUsername() { return email; }
    @Override public String getPassword() { return passwordHash; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_PARTICIPANT"));
    }
    @Override public void eraseCredentials() { passwordHash = null; }
}
