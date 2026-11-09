package com.teamforge.teams.api;

import com.teamforge.identity.security.ParticipantPrincipal;
import com.teamforge.teams.application.AdmissionWorkflows;
import com.teamforge.teams.application.AdmissionWorkflows.*;
import com.teamforge.teams.application.MembershipAdmissionService.Result;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class AdmissionController {
    public record Redeem(String code) {}
    private final AdmissionWorkflows workflows;
    public AdmissionController(AdmissionWorkflows workflows) {this.workflows=workflows;}
    @PostMapping("/teams/{team}/invite") @ResponseStatus(HttpStatus.CREATED)
    public Invite rotate(@PathVariable UUID team,@AuthenticationPrincipal ParticipantPrincipal actor) {
        return workflows.rotate(team,actor.id());
    }
    @PostMapping("/hackathons/{event}/invite-joins") @ResponseStatus(HttpStatus.CREATED)
    public Result redeem(@PathVariable UUID event,@AuthenticationPrincipal ParticipantPrincipal actor,
        @RequestBody Redeem input,@RequestHeader(value="Idempotency-Key",required=false) String key) {
        return workflows.redeem(event,actor.id(),input.code(),key);
    }
    @PostMapping("/teams/{team}/requests") @ResponseStatus(HttpStatus.CREATED)
    public JoinRequest request(@PathVariable UUID team,@AuthenticationPrincipal ParticipantPrincipal actor) {
        return workflows.request(team,actor.id());
    }
    @GetMapping("/teams/{team}/requests")
    public RequestPage inbox(@PathVariable UUID team,@AuthenticationPrincipal ParticipantPrincipal actor,
        @RequestParam(required=false) UUID cursor) {
        return workflows.inboxPage(team,actor.id(),cursor);
    }
    @GetMapping("/hackathons/{event}/me/requests")
    public RequestPage mine(@PathVariable UUID event,@AuthenticationPrincipal ParticipantPrincipal actor,
        @RequestParam(required=false) UUID cursor) {
        return workflows.minePage(event,actor.id(),cursor);
    }
    @DeleteMapping("/teams/{team}/requests/{request}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw(@PathVariable UUID team,@PathVariable UUID request,@AuthenticationPrincipal ParticipantPrincipal actor) {
        workflows.withdraw(team,request,actor.id());
    }
    @PostMapping("/teams/{team}/requests/{request}/accept")
    public Acceptance accept(@PathVariable UUID team,@PathVariable UUID request,@AuthenticationPrincipal ParticipantPrincipal actor,
        @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return workflows.accept(team,request,actor.id(),key);
    }
    @PostMapping("/teams/{team}/requests/{request}/decline")
    public JoinRequest decline(@PathVariable UUID team,@PathVariable UUID request,@AuthenticationPrincipal ParticipantPrincipal actor) {
        return workflows.decline(team,request,actor.id());
    }
}
