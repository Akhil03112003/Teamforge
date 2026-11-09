package com.teamforge.teams.application;

import com.teamforge.platform.api.ApiException;
import com.teamforge.teams.persistence.TeamStore.Row;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Trusted server-domain authorization, never an HTTP DTO. M4 must add validated invite proof here. */
public sealed interface AdmissionAuthorization permits AdmissionAuthorization.LeaderApproval,
    AdmissionAuthorization.Invitation, AdmissionAuthorization.RequestApproval {
    void verify(Row lockedTeam, UUID actor);
    default void verify(Row lockedTeam, UUID actor, UUID participant, JdbcTemplate jdbc) { verify(lockedTeam,actor); }
    String kind();
    record LeaderApproval(UUID leaderId) implements AdmissionAuthorization {
        @Override public String kind() { return "LEADER_APPROVAL"; }
        @Override public void verify(Row lockedTeam,UUID actor) {
            if (!actor.equals(leaderId) || !lockedTeam.leaderId().equals(actor))
                throw new ApiException(403,"LEADER_REQUIRED","Only the current leader can authorize admission.");
        }
    }
    record Invitation(String tokenHash) implements AdmissionAuthorization {
        @Override public String kind() { return "INVITATION"; }
        @Override public void verify(Row team, UUID actor) { throw new IllegalStateException("Invitation requires database validation."); }
        @Override public void verify(Row team, UUID actor, UUID participant, JdbcTemplate jdbc) {
            if (!actor.equals(participant)) throw new ApiException(403,"FORBIDDEN","Access denied.");
            var valid=jdbc.query("""
                SELECT id FROM team_invitations WHERE team_id=? AND token_hash=?
                AND revoked_at IS NULL AND expires_at>clock_timestamp() FOR UPDATE
                """,(r,i)->r.getObject(1,UUID.class),team.id(),tokenHash);
            if (valid.isEmpty()) throw new ApiException(400,"INVALID_INVITE","Invitation is invalid or unavailable.");
        }
    }
    record RequestApproval(UUID leaderId, UUID requestId) implements AdmissionAuthorization {
        @Override public String kind() { return "REQUEST_APPROVAL"; }
        @Override public void verify(Row team, UUID actor) { new LeaderApproval(leaderId).verify(team,actor); }
        @Override public void verify(Row team, UUID actor, UUID participant, JdbcTemplate jdbc) {
            verify(team,actor);
            var states=jdbc.query("SELECT state FROM join_requests WHERE id=? AND team_id=? AND applicant_id=? FOR UPDATE",
                (r,i)->r.getString(1),requestId,team.id(),participant);
            if (states.isEmpty()) throw new ApiException(404,"REQUEST_NOT_FOUND","Request not found.");
            if (!states.getFirst().equals("PENDING")) throw new ApiException(409,"REQUEST_RESOLVED","Request is no longer pending.");
        }
    }
}
