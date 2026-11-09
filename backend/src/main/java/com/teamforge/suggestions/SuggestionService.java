package com.teamforge.suggestions;

import com.teamforge.platform.api.ApiException;
import com.teamforge.teams.persistence.TeamStore;
import com.teamforge.suggestions.SuggestionScoring.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class SuggestionService {
    public record Suggestion(UUID userId,String displayName,double score,double skillScore,double timeScore,List<String> reasons){}
    public record Response(List<Suggestion> items,LocalDate windowStart,int evaluatedCandidates,boolean truncated){}
    private final JdbcTemplate jdbc;private final TeamStore teams;private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    public SuggestionService(JdbcTemplate jdbc,TeamStore teams,com.fasterxml.jackson.databind.ObjectMapper mapper){this.jdbc=jdbc;this.teams=teams;this.mapper=mapper;}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Response suggest(UUID teamId,UUID actor,int limit){
        if(limit<1 || limit>50)throw new ApiException(400,"VALIDATION_ERROR","Suggestion limit must be 1–50.");
        var team=teams.find(teamId,false);
        if(!actor.equals(team.leaderId()))throw new ApiException(403,"LEADER_REQUIRED","Only the current leader can view suggestions.");
        var members=teams.members(team);var memberIds=new HashSet<UUID>();members.forEach(m->memberIds.add(m.userId()));
        LocalDate start=jdbc.queryForObject("SELECT (clock_timestamp() AT TIME ZONE 'UTC')::date",LocalDate.class);
        if(members.size()>=teams.event(team.eventId()).maxSize())return new Response(List.of(),start,0,false);
        // One bounded batch includes all team members even when the candidate cap is exceeded.
        var profiles=jdbc.query("""
            WITH candidates AS (
                SELECT u.id FROM users u WHERE NOT EXISTS(SELECT 1 FROM team_members m WHERE m.hackathon_id=? AND m.user_id=u.id)
                ORDER BY u.id LIMIT 10001
            ), selected AS (SELECT id FROM candidates UNION SELECT user_id FROM team_members WHERE team_id=?)
            SELECT u.id,coalesce(p.display_name,'Participant') display_name,p.time_zone,
                coalesce((SELECT array_agg(s.skill ORDER BY s.skill) FROM profile_skills s WHERE s.user_id=u.id),'{}') skills,
                coalesce((SELECT json_agg(json_build_object('day',a.day_of_week,'start',a.start_time,'end',a.end_time) ORDER BY a.slot_order)
                  FROM profile_availability a WHERE a.user_id=u.id),'[]'::json)::text windows
            FROM selected x JOIN users u ON u.id=x.id LEFT JOIN profiles p ON p.user_id=u.id ORDER BY u.id
            """,(r,i)->new Raw(r.getObject("id",UUID.class),r.getString("display_name"),Set.of((String[])r.getArray("skills").getArray()),r.getString("time_zone"),r.getString("windows")),team.eventId(),teamId);
        var parsed=new ArrayList<Profile>();for(var raw:profiles)parsed.add(profile(raw));
        var missing=new TreeSet<>(team.skills());BitSet common=new BitSet(10080);common.set(0,10080);
        for(var p:parsed)if(memberIds.contains(p.id())){missing.removeAll(p.skills());common.and(SuggestionScoring.availability(p,start));}
        var candidates=parsed.stream().filter(p->!memberIds.contains(p.id()) && !p.id().equals(actor)).toList();
        boolean truncated=candidates.size()>10000;int evaluated=Math.min(10000,candidates.size());
        Comparator<Suggestion> order=Comparator.comparingDouble(Suggestion::score).reversed().thenComparing(s->s.userId().toString());
        var best=new PriorityQueue<Suggestion>(order.reversed());
        for(var p:candidates.subList(0,evaluated)){
            var score=SuggestionScoring.score(missing,common,p,start);
            best.add(new Suggestion(p.id(),p.displayName(),score.score(),score.skillScore(),score.timeScore(),score.reasons()));
            if(best.size()>limit)best.poll();
        }
        var result=new ArrayList<>(best);result.sort(order);return new Response(List.copyOf(result),start,evaluated,truncated);
    }
    private record Raw(UUID id,String name,Set<String> skills,String zone,String windows){}
    private Profile profile(Raw row){
        try{
            var json=mapper.readTree(row.windows());var windows=new ArrayList<Window>();
            for(var w:json)windows.add(new Window(DayOfWeek.valueOf(w.get("day").asText()),LocalTime.parse(w.get("start").asText()),LocalTime.parse(w.get("end").asText())));
            return new Profile(row.id(),row.name(),row.skills(),row.zone(),List.copyOf(windows));
        }catch(com.fasterxml.jackson.core.JsonProcessingException invalid){throw new IllegalStateException("Invalid persisted availability.",invalid);}
    }
}
