package com.teamforge.live;

import com.teamforge.identity.security.ParticipantPrincipal;
import com.teamforge.teams.application.TeamService;
import com.teamforge.teams.persistence.TeamStore;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1")
public class EventStreamController {
    private final EventStreamHub hub;
    private final TeamStore teams;
    private final TeamService service;
    public EventStreamController(EventStreamHub hub,TeamStore teams,TeamService service){this.hub=hub;this.teams=teams;this.service=service;}
    @GetMapping(value="/hackathons/{event}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> event(@PathVariable UUID event,@AuthenticationPrincipal ParticipantPrincipal actor,HttpServletRequest request){
        teams.event(event);return stream(event,actor,request);
    }
    @GetMapping(value="/teams/{team}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> team(@PathVariable UUID team,@AuthenticationPrincipal ParticipantPrincipal actor,HttpServletRequest request){
        return stream(service.detail(team).hackathonId(),actor,request);
    }
    private ResponseEntity<SseEmitter> stream(UUID event,ParticipantPrincipal actor,HttpServletRequest request){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Accel-Buffering","no")
            .body(hub.subscribe(event,actor.id(),request.getSession(false)).emitter());
    }
}
