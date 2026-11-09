package com.teamforge.identity.application;

import com.teamforge.identity.api.IdentityContracts.*;
import com.teamforge.identity.persistence.*;
import com.teamforge.identity.security.ParticipantPrincipal;
import com.teamforge.platform.api.ApiException;
import com.teamforge.profiles.application.ProfileService;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParticipantService implements UserDetailsService {
    private final AccountRepository accounts;
    private final ProfileService profiles;
    private final PasswordEncoder encoder;
    public ParticipantService(AccountRepository accounts, ProfileService profiles, PasswordEncoder encoder) {
        this.accounts = accounts; this.profiles = profiles; this.encoder = encoder;
    }
    public static String normalizeEmail(String email) { return email.strip().toLowerCase(Locale.ROOT); }
    @Transactional
    public User register(Registration request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ApiException(400, "VALIDATION_ERROR", "Password must be at most 72 UTF-8 bytes.");
        String email = normalizeEmail(request.email());
        if (accounts.existsByEmail(email)) throw new ApiException(409, "IDENTITY_CONFLICT", "An account with this email already exists.");
        var account = accounts.saveAndFlush(new AccountEntity(email, encoder.encode(request.password())));
        return new User(account.id(), account.email(), profiles.create(account.id(), request.displayName().strip()));
    }
    @Transactional(readOnly = true)
    public User current(UUID id) {
        var account = accounts.findById(id).orElseThrow(() ->
            new ApiException(401, "UNAUTHENTICATED", "Sign in to continue."));
        return new User(account.id(), account.email(), profiles.get(id));
    }
    @Override @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        var account = accounts.findByEmail(normalizeEmail(email))
            .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials."));
        return new ParticipantPrincipal(account.id(), account.email(), account.passwordHash());
    }
}
