package com.teamforge.profiles.api;

import com.teamforge.identity.security.ParticipantPrincipal;
import com.teamforge.profiles.api.ProfileContracts.*;
import com.teamforge.profiles.application.ProfileService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me/profile")
public class ProfileController {
    private final ProfileService profiles;
    public ProfileController(ProfileService profiles) { this.profiles = profiles; }
    @GetMapping
    public Profile get(@AuthenticationPrincipal ParticipantPrincipal principal) { return profiles.get(principal.id()); }
    @PatchMapping
    public Profile update(@AuthenticationPrincipal ParticipantPrincipal principal, @Valid @RequestBody Update request) {
        return profiles.update(principal.id(), request);
    }
}
