package com.teamforge.identity.api;

import com.teamforge.identity.api.IdentityContracts.*;
import com.teamforge.identity.application.ParticipantService;
import com.teamforge.identity.security.ParticipantPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class IdentityController {
    private final ParticipantService participants;
    public IdentityController(ParticipantService participants) { this.participants = participants; }
    @GetMapping("/auth/csrf")
    public Csrf csrf(CsrfToken token) { return new Csrf(token.getHeaderName(), token.getToken()); }
    @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED)
    public User register(@Valid @RequestBody Registration request) { return participants.register(request); }
    @GetMapping("/me")
    public User me(@AuthenticationPrincipal ParticipantPrincipal principal) { return participants.current(principal.id()); }
}
