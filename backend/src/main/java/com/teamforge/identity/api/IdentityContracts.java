package com.teamforge.identity.api;

import com.teamforge.profiles.api.ProfileContracts.Profile;
import jakarta.validation.constraints.*;
import java.util.UUID;

public final class IdentityContracts {
    private IdentityContracts() {}
    public record Registration(
        @NotBlank @Email @Size(max = 254) @Pattern(regexp = "^[\\x21-\\x7E]+$") String email,
        @NotBlank @Size(min = 12, max = 72) String password,
        @NotBlank @Size(max = 80) @Pattern(regexp = "^[^\\p{Cntrl}]+$") String displayName) {
        @Override public String toString() { return "Registration[REDACTED]"; }
    }
    public record User(UUID id, String email, Profile profile) {}
    public record Csrf(String headerName, String token) {}
}
