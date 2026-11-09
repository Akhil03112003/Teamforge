package com.teamforge.teams.api;

import com.teamforge.identity.security.ParticipantPrincipal;
import com.teamforge.teams.api.TeamContracts.*;
import com.teamforge.teams.application.TeamService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Validated
public class TeamController {
    private final TeamService teams;
    public TeamController(TeamService teams) { this.teams=teams; }
    @GetMapping("/hackathons")
    public Items<Hackathon> events() { return teams.events(); }
    @GetMapping("/hackathons/{event}/teams")
    public Board board(@PathVariable UUID event, @RequestParam(required=false) UUID cursor,
                       @RequestParam(defaultValue="50") @Min(1) @Max(50) int limit) {
        return teams.board(event,cursor,limit);
    }
    @GetMapping("/hackathons/{event}/me/team")
    public ResponseEntity<Team> mine(@PathVariable UUID event, @AuthenticationPrincipal ParticipantPrincipal actor) {
        return teams.mine(event,actor.id()).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }
    @PostMapping("/hackathons/{event}/teams") @ResponseStatus(HttpStatus.CREATED)
    public Team create(@PathVariable UUID event, @AuthenticationPrincipal ParticipantPrincipal actor,
                       @Valid @RequestBody Create request,
                       @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return teams.create(event,actor.id(),request,key);
    }
    @GetMapping("/teams/{id}")
    public Team detail(@PathVariable UUID id) { return teams.detail(id); }
    @GetMapping("/teams/{id}/members")
    public Items<Member> members(@PathVariable UUID id) { return new Items<>(teams.members(id)); }
    @PatchMapping("/teams/{id}")
    public Team edit(@PathVariable UUID id, @AuthenticationPrincipal ParticipantPrincipal actor,
                     @Valid @RequestBody Update request) { return teams.edit(id,actor.id(),request); }
    @DeleteMapping("/teams/{id}/members/me")
    public Leave leave(@PathVariable UUID id, @AuthenticationPrincipal ParticipantPrincipal actor) { return teams.leave(id,actor.id()); }
    @DeleteMapping("/teams/{id}/members/{target}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID id, @PathVariable UUID target,
                       @AuthenticationPrincipal ParticipantPrincipal actor) { teams.remove(id,actor.id(),target); }
    @GetMapping("/teams/{id}/audit-events")
    public Items<Audit> audits(@PathVariable UUID id, @AuthenticationPrincipal ParticipantPrincipal actor,
        @RequestParam(defaultValue="0") @Min(0) @Max(1000000) int offset,
        @RequestParam(defaultValue="50") @Min(1) @Max(100) int limit) {
        return teams.audits(id,actor.id(),offset,limit);
    }
}
