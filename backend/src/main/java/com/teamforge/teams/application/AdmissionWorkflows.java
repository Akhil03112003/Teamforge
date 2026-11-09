package com.teamforge.teams.application;

import com.teamforge.platform.api.ApiException;
import com.teamforge.platform.idempotency.IdempotentMutations;
import com.teamforge.platform.transactions.MutationTransactions;
import com.teamforge.teams.persistence.TeamStore;
import com.teamforge.teams.persistence.TeamStore.Row;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdmissionWorkflows {
    public record Invite(String code, Instant expiresAt) {}
    public record JoinRequest(UUID id, UUID teamId, UUID applicantId, String displayName, String state, Instant createdAt) {}
    public record RequestPage(List<JoinRequest> items, UUID nextCursor) {}
    public record Acceptance(UUID id, String state, MembershipAdmissionService.Result membership) {}
    private final JdbcTemplate jdbc;
    private final TeamStore teams;
    private final MutationTransactions transactions;
    private final IdempotentMutations keys;
    private final MembershipAdmissionService admission;
    private final SecureRandom random=new SecureRandom();
    public AdmissionWorkflows(JdbcTemplate jdbc,TeamStore teams,MutationTransactions transactions,
        IdempotentMutations keys,MembershipAdmissionService admission) {
        this.jdbc=jdbc;this.teams=teams;this.transactions=transactions;this.keys=keys;this.admission=admission;
    }
    public Invite rotate(UUID teamId,UUID actor) {
        return transactions.run(() -> {
            var team=teams.find(teamId,true);leader(team,actor);
            jdbc.update("UPDATE team_invitations SET revoked_at=clock_timestamp() WHERE team_id=? AND revoked_at IS NULL",teamId);
            byte[] bytes=new byte[32];random.nextBytes(bytes);
            String code=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            UUID id=UUID.randomUUID();
            jdbc.update("INSERT INTO team_invitations(id,hackathon_id,team_id,token_hash) VALUES (?,?,?,?)",id,team.eventId(),teamId,hash(code));
            Instant expiry=jdbc.queryForObject("SELECT expires_at FROM team_invitations WHERE id=?",(r,i)->r.getTimestamp(1).toInstant(),id);
            teams.audit(team,actor,"INVITE_ROTATED","TEAM",teamId);
            return new Invite(code,expiry);
        });
    }
    public MembershipAdmissionService.Result redeem(UUID event,UUID actor,String code,String key) {
        if (code==null || !code.matches("[A-Za-z0-9_-]{43}")) throw invalidInvite();
        String digest=hash(code);
        return keys.execute(actor,"INVITE_REDEEM",key,Map.of("version",1,"event",event,"tokenHash",digest),
            MembershipAdmissionService.Result.class,() -> {
                // Hash lookup is a locator only; admission revalidates under team then invitation lock.
                var located=jdbc.query("SELECT team_id FROM team_invitations WHERE hackathon_id=? AND token_hash=?",
                    (r,i)->r.getObject(1,UUID.class),event,digest);
                if (located.isEmpty()) throw invalidInvite();
                try {
                    return admission.admit(new MembershipAdmissionService.Command(located.getFirst(),actor,actor,
                        new AdmissionAuthorization.Invitation(digest),key));
                } catch (ApiException failure) {
                    if (failure.code().equals("TEAM_NOT_FOUND")) throw invalidInvite();
                    throw failure;
                }
            });
    }
    public JoinRequest request(UUID teamId,UUID actor) {
        return transactions.run(() -> {
            var team=lock(teamId,actor);
            if (teams.membership(team.eventId(),actor).isPresent())
                throw new ApiException(409,"ALREADY_TEAMED","You already belong to a team in this hackathon.");
            var pending=requests("r.team_id=? AND r.applicant_id=? AND r.state='PENDING'",teamId,actor);
            if (!pending.isEmpty()) return pending.getFirst();
            UUID id=UUID.randomUUID();
            jdbc.update("INSERT INTO join_requests(id,hackathon_id,team_id,applicant_id) VALUES (?,?,?,?)",id,team.eventId(),teamId,actor);
            teams.audit(team,actor,"REQUEST_CREATED","USER",actor);
            return get(teamId,id);
        });
    }
    @Transactional(readOnly=true)
    public List<JoinRequest> mine(UUID event,UUID actor) {
        teams.event(event);
        return requests("r.hackathon_id=? AND r.applicant_id=?",event,actor);
    }
    @Transactional(readOnly=true)
    public RequestPage minePage(UUID event,UUID actor,UUID cursor) {
        teams.event(event);return page("r.hackathon_id=? AND r.applicant_id=?",cursor,event,actor);
    }
    public RequestPage inboxPage(UUID teamId,UUID actor,UUID cursor) {
        return transactions.run(() -> {leader(teams.find(teamId,true),actor);return page("r.team_id=? AND r.state='PENDING'",cursor,teamId);});
    }
    public List<JoinRequest> inbox(UUID teamId,UUID actor) {
        return transactions.run(() -> {leader(teams.find(teamId,true),actor);return requests("r.team_id=?",teamId);});
    }
    public void withdraw(UUID teamId,UUID requestId,UUID actor) {
        transactions.run(() -> {
            var team=lock(teamId,actor);var request=get(teamId,requestId);
            if (!request.applicantId().equals(actor)) throw new ApiException(403,"FORBIDDEN","Only the applicant may withdraw.");
            pending(request);transition(requestId,"WITHDRAWN");teams.audit(team,actor,"REQUEST_WITHDRAWN","USER",actor);return null;
        });
    }
    public JoinRequest decline(UUID teamId,UUID requestId,UUID actor) {
        var located=get(teamId,requestId);
        return transactions.run(() -> {
            var team=lock(teamId,located.applicantId());leader(team,actor);
            var request=get(teamId,requestId);pending(request);transition(requestId,"DECLINED");
            teams.audit(team,actor,"REQUEST_DECLINED","USER",request.applicantId());return get(teamId,requestId);
        });
    }
    public Acceptance accept(UUID teamId,UUID requestId,UUID actor,String key) {
        return keys.execute(actor,"REQUEST_ACCEPT",key,Map.of("version",1,"team",teamId,"request",requestId),
            Acceptance.class,200,() -> {
                var located=get(teamId,requestId);
                var result=admission.admit(new MembershipAdmissionService.Command(teamId,located.applicantId(),actor,
                    new AdmissionAuthorization.RequestApproval(actor,requestId),key));
                return new Acceptance(requestId,"ACCEPTED",result);
            });
    }
    private Row lock(UUID teamId,UUID participant) {
        var located=teams.find(teamId,false);teams.ensureParticipant(located.eventId(),participant);
        teams.lockParticipant(located.eventId(),participant);return teams.find(teamId,true);
    }
    private void leader(Row team,UUID actor) {new AdmissionAuthorization.LeaderApproval(actor).verify(team,actor);}
    private void pending(JoinRequest request) {
        if (!request.state().equals("PENDING")) throw new ApiException(409,"REQUEST_RESOLVED","Request is no longer pending.");
    }
    private void transition(UUID request,String state) {
        if (jdbc.update("UPDATE join_requests SET state=?,updated_at=clock_timestamp() WHERE id=? AND state='PENDING'",state,request)!=1)
            throw new ApiException(409,"REQUEST_RESOLVED","Request is no longer pending.");
    }
    private JoinRequest get(UUID team,UUID request) {
        return requests("r.team_id=? AND r.id=?",team,request).stream().findFirst()
            .orElseThrow(()->new ApiException(404,"REQUEST_NOT_FOUND","Request not found."));
    }
    private List<JoinRequest> requests(String predicate,Object... arguments) {
        return jdbc.query("SELECT r.*,p.display_name FROM join_requests r JOIN profiles p ON p.user_id=r.applicant_id WHERE "+predicate+
            " ORDER BY r.created_at,r.id LIMIT 2000",(r,i)->new JoinRequest(r.getObject("id",UUID.class),r.getObject("team_id",UUID.class),
            r.getObject("applicant_id",UUID.class),r.getString("display_name"),r.getString("state"),r.getTimestamp("created_at").toInstant()),arguments);
    }
    private RequestPage page(String predicate,UUID cursor,Object... arguments) {
        var params=new ArrayList<Object>(Arrays.asList(arguments));params.add(cursor==null?new UUID(0,0):cursor);
        var rows=jdbc.query("SELECT r.*,p.display_name FROM join_requests r JOIN profiles p ON p.user_id=r.applicant_id WHERE "+predicate+
            " AND r.id>? ORDER BY r.id LIMIT 101",(r,i)->new JoinRequest(r.getObject("id",UUID.class),r.getObject("team_id",UUID.class),
                r.getObject("applicant_id",UUID.class),r.getString("display_name"),r.getString("state"),r.getTimestamp("created_at").toInstant()),params.toArray());
        boolean more=rows.size()>100;var items=List.copyOf(rows.subList(0,Math.min(100,rows.size())));
        return new RequestPage(items,more?items.getLast().id():null);
    }
    private static ApiException invalidInvite() {return new ApiException(400,"INVALID_INVITE","Invitation is invalid or unavailable.");}
    private static String hash(String code) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.US_ASCII)));}
        catch(NoSuchAlgorithmException impossible) {throw new IllegalStateException(impossible);}
    }
}
