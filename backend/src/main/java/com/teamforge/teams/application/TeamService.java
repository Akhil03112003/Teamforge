package com.teamforge.teams.application;

import com.teamforge.platform.api.ApiException;
import com.teamforge.teams.api.TeamContracts.*;
import com.teamforge.teams.persistence.TeamStore;
import com.teamforge.teams.persistence.TeamStore.Row;
import java.util.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class TeamService {
    private final TeamStore store;
    public TeamService(TeamStore store) { this.store=store; }
    @Transactional(readOnly=true)
    public Items<Hackathon> events() { return new Items<>(store.events()); }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Team detail(UUID id) { return store.detail(store.find(id,false)); }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public List<Member> members(UUID id) { return store.members(store.find(id,false)); }
    @Transactional(readOnly=true)
    public Board board(UUID event, UUID cursor, int limit) { return store.board(event,cursor,limit); }
    @Transactional(readOnly=true, isolation=Isolation.REPEATABLE_READ)
    public Optional<Team> mine(UUID event, UUID user) {
        store.event(event);
        return store.membership(event,user).map(id -> store.detail(store.find(id,false)));
    }
    public Team create(UUID event, UUID actor, Create request) {
        var bounds=store.event(event);
        if (bounds.maxSize()<1) throw new ApiException(409,"TEAM_FULL","No team capacity is available.");
        store.ensureParticipant(event,actor);
        store.lockParticipant(event,actor);
        if (store.membership(event,actor).isPresent())
            throw new ApiException(409,"ALREADY_TEAMED","You already belong to a team in this hackathon.");
        var skills=skills(request.desiredSkills());
        UUID id=UUID.randomUUID();
        try {
            store.create(id,event,actor,name(request.name()),request.description()==null ? "" : request.description(),skills);
        } catch (DuplicateKeyException error) {
            throw new ApiException(409,"TEAM_CONFLICT","Team membership changed. Reload and try again.");
        }
        var row=store.find(id,false);
        store.audit(row,actor,"TEAM_CREATED","TEAM",id);
        return store.detail(row);
    }
    public Team edit(UUID id, UUID actor, Update request) {
        var team=store.find(id,true);
        requireLeader(team,actor);
        String name=request.name()==null ? team.name() : name(request.name());
        String description=request.description()==null ? team.description() : request.description();
        var skills=request.desiredSkills()==null ? team.skills() : skills(request.desiredSkills());
        if (!name.equals(team.name()) || !description.equals(team.description()) || !skills.equals(team.skills())) {
            store.edit(team,name,description,skills);
            store.audit(team,actor,"DETAILS_UPDATED","TEAM",id);
        }
        return store.detail(store.find(id,false));
    }
    public Leave leave(UUID id, UUID actor) {
        var locator=store.find(id,false);
        store.lockParticipant(locator.eventId(),actor);
        var team=store.find(id,true);
        if (store.removeMember(id,actor)!=1)
            throw new ApiException(403,"NOT_A_MEMBER","You are not a member of this team.");
        store.audit(team,actor,"MEMBER_LEFT","USER",actor);
        var remaining=store.members(team);
        if (remaining.isEmpty()) {
            store.audit(team,actor,"TEAM_DELETED","TEAM",id);
            store.delete(id);
            return new Leave(id,true,null);
        }
        UUID leader=team.leaderId();
        if (leader.equals(actor)) {
            leader=remaining.getFirst().userId();
            store.transfer(id,leader);
            store.audit(team,actor,"LEADERSHIP_TRANSFERRED","USER",leader);
        }
        return new Leave(id,false,leader);
    }
    public void remove(UUID id, UUID actor, UUID target) {
        if (actor.equals(target)) throw new ApiException(409,"USE_LEAVE","Use Leave team to remove yourself.");
        var locator=store.find(id,false);
        store.lockParticipant(locator.eventId(),target);
        var team=store.find(id,true);
        requireLeader(team,actor);
        if (store.removeMember(id,target)!=1)
            throw new ApiException(404,"MEMBER_NOT_FOUND","This participant is not on this team.");
        store.audit(team,actor,"MEMBER_REMOVED","USER",target);
    }
    @Transactional(readOnly=true)
    public Items<Audit> audits(UUID id, UUID actor, int offset, int limit) {
        if (!store.hasAudit(id)) throw new ApiException(404,"TEAM_NOT_FOUND","Team history not found.");
        if (!store.mayReadAudit(id,actor)) throw new ApiException(403,"AUDIT_FORBIDDEN","Team history is private to its participants.");
        return new Items<>(store.audits(id,offset,limit));
    }
    private static void requireLeader(Row team, UUID actor) {
        if (!team.leaderId().equals(actor)) throw new ApiException(403,"LEADER_REQUIRED","Only the current leader can do that.");
    }
    private static String name(String value) {
        if (value.isBlank()) throw new ApiException(400,"VALIDATION_ERROR","Team name must not be blank.");
        return value.strip();
    }
    private static List<String> skills(List<String> values) {
        if (values==null) return List.of();
        var result=new TreeSet<String>();
        for (var raw:values) {
            var skill=raw.strip().toLowerCase(Locale.ROOT);
            if (!skill.matches("[a-z0-9][a-z0-9 +#./-]{0,39}"))
                throw new ApiException(400,"VALIDATION_ERROR","Desired skills must be short text tags.");
            result.add(skill);
        }
        return List.copyOf(result);
    }
}
