package com.teamforge.suggestions;

import com.teamforge.identity.security.ParticipantPrincipal;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/teams/{team}/suggestions")
public class SuggestionController {
    private final SuggestionService service;
    public SuggestionController(SuggestionService service){this.service=service;}
    @GetMapping public SuggestionService.Response get(@PathVariable UUID team,@AuthenticationPrincipal ParticipantPrincipal actor,
        @RequestParam(defaultValue="20") int limit){return service.suggest(team,actor.id(),limit);}
}
