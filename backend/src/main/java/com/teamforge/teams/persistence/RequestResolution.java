package com.teamforge.teams.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Caller must hold the applicant's event anchor. Never locks other teams during cleanup. */
@Repository
public class RequestResolution {
    private final JdbcTemplate jdbc;
    public RequestResolution(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void joined(UUID event, UUID participant, UUID actor, UUID acceptedRequest) {
        var requests=jdbc.query("""
            SELECT id,team_id FROM join_requests WHERE hackathon_id=? AND applicant_id=? AND state='PENDING'
            ORDER BY id FOR UPDATE
            """,(r,i)->new UUID[]{r.getObject(1,UUID.class),r.getObject(2,UUID.class)},event,participant);
        for (var request:requests) {
            boolean accepted=request[0].equals(acceptedRequest);
            jdbc.update("UPDATE join_requests SET state=?,updated_at=clock_timestamp() WHERE id=?",
                accepted ? "ACCEPTED":"CLOSED",request[0]);
            jdbc.update("""
                INSERT INTO audit_events(id,hackathon_id,team_id,actor_id,action,target_type,target_id)
                VALUES (?,?,?,?,?,'USER',?)
                """,UUID.randomUUID(),event,request[1],actor,accepted ? "REQUEST_ACCEPTED":"REQUEST_CLOSED",participant);
        }
    }
}
