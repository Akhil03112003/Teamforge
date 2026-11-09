package com.teamforge.teams.persistence;

import com.teamforge.platform.api.ApiException;
import com.teamforge.teams.api.TeamContracts.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class TeamStore {
    private final JdbcTemplate jdbc;
    public TeamStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Row(UUID id, UUID eventId, String name, String description,
                      List<String> skills, UUID leaderId) {}
    private Row row(ResultSet result, int index) throws SQLException {
        return new Row(result.getObject("id", UUID.class), result.getObject("hackathon_id", UUID.class),
            result.getString("name"), result.getString("description"),
            List.of((String[]) result.getArray("desired_skills").getArray()),
            result.getObject("leader_user_id", UUID.class));
    }
    public List<Hackathon> events() {
        return jdbc.query("SELECT * FROM hackathons ORDER BY active DESC, created_at, id LIMIT 100",
            (r, i) -> new Hackathon(r.getObject("id", UUID.class),r.getString("name"),
                r.getInt("minimum_team_size"),r.getInt("maximum_team_size"),r.getBoolean("active")));
    }
    public Hackathon event(UUID id) {
        return jdbc.query("SELECT * FROM hackathons WHERE id = ?", (r,i) ->
            new Hackathon(id,r.getString("name"),r.getInt("minimum_team_size"),
                r.getInt("maximum_team_size"),r.getBoolean("active")),id).stream().findFirst()
            .orElseThrow(() -> new ApiException(404,"HACKATHON_NOT_FOUND","Hackathon not found."));
    }
    public Row find(UUID id, boolean lock) {
        return jdbc.query("SELECT * FROM teams WHERE id = ?" + (lock ? " FOR UPDATE" : ""), this::row,id)
            .stream().findFirst().orElseThrow(() -> new ApiException(404,"TEAM_NOT_FOUND","Team not found."));
    }
    public void ensureParticipant(UUID event, UUID user) {
        jdbc.update("INSERT INTO event_participants(hackathon_id,user_id) VALUES (?,?) ON CONFLICT DO NOTHING",event,user);
    }
    public boolean participantExists(UUID user) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM users WHERE id=?)",Boolean.class,user));
    }
    public void lockParticipant(UUID event, UUID user) {
        // Serializes this event/person without blocking FK key-share checks on successors.
        jdbc.query("SELECT user_id FROM event_participants WHERE hackathon_id = ? AND user_id = ? FOR NO KEY UPDATE",
            (r,i) -> r.getObject(1,UUID.class),event,user);
    }
    public Optional<UUID> membership(UUID event, UUID user) {
        return jdbc.query("SELECT team_id FROM team_members WHERE hackathon_id = ? AND user_id = ?",
            (r,i) -> r.getObject(1,UUID.class),event,user).stream().findFirst();
    }
    public void create(UUID id, UUID event, UUID user, String name, String description, List<String> skills) {
        jdbc.update(connection -> {
            var statement=connection.prepareStatement("INSERT INTO teams(id,hackathon_id,leader_user_id,name,description,desired_skills) VALUES (?,?,?,?,?,?)");
            statement.setObject(1,id);statement.setObject(2,event);statement.setObject(3,user);
            statement.setString(4,name);statement.setString(5,description);
            statement.setArray(6,connection.createArrayOf("text",skills.toArray(String[]::new)));
            return statement;
        });
    }
    public int memberCount(UUID team) {
        return jdbc.queryForObject("SELECT count(*) FROM team_members WHERE team_id=?",Integer.class,team);
    }
    public UUID addMember(Row team,UUID user) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO team_members(id,hackathon_id,team_id,user_id) VALUES (?,?,?,?)",id,team.eventId(),team.id(),user);
        return id;
    }
    public void edit(Row team, String name, String description, List<String> skills) {
        jdbc.update(connection -> {
            var statement=connection.prepareStatement("UPDATE teams SET name=?,description=?,desired_skills=? WHERE id=?");
            statement.setString(1,name);statement.setString(2,description);
            statement.setArray(3,connection.createArrayOf("text",skills.toArray(String[]::new)));
            statement.setObject(4,team.id());return statement;
        });
    }
    public List<Member> members(Row team) {
        return jdbc.query("""
            SELECT m.user_id, coalesce(p.display_name,'Participant'), m.joined_at FROM team_members m
            LEFT JOIN profiles p ON p.user_id=m.user_id WHERE m.team_id=? ORDER BY m.joined_at,m.id
            """,(r,i) -> new Member(r.getObject(1,UUID.class),r.getString(2),
                r.getTimestamp(3).toInstant(),team.leaderId().equals(r.getObject(1,UUID.class))),team.id());
    }
    public Team detail(Row row) {
        var event=event(row.eventId());var members=members(row);
        return new Team(row.id(),row.eventId(),row.name(),row.description(),row.skills(),row.leaderId(),
            members.size()<event.minSize() ? "INCOMPLETE" : "READY",event.minSize(),event.maxSize(),members);
    }
    public Board board(UUID event, UUID cursor, int limit) {
        var bounds=event(event);
        var rows=jdbc.query("""
            SELECT t.*, count(m.id) AS member_count FROM teams t
            LEFT JOIN team_members m ON m.team_id=t.id
            WHERE t.hackathon_id=? AND t.id > ? GROUP BY t.id ORDER BY t.id LIMIT ?
            """,(r,i) -> {
                int count=r.getInt("member_count");
                return new Summary(r.getObject("id",UUID.class),r.getString("name"),r.getString("description"),
                    r.getObject("leader_user_id",UUID.class),count<bounds.minSize() ? "INCOMPLETE" : "READY",
                    bounds.minSize(),bounds.maxSize(),count);
            },event,cursor==null ? new UUID(0,0) : cursor,limit+1);
        boolean more=rows.size()>limit;var page=List.copyOf(rows.subList(0,Math.min(limit,rows.size())));
        return new Board(page,more ? page.getLast().id() : null);
    }
    public int removeMember(UUID team, UUID user) {
        return jdbc.update("DELETE FROM team_members WHERE team_id=? AND user_id=?",team,user);
    }
    public void transfer(UUID team, UUID leader) {
        jdbc.update("UPDATE teams SET leader_user_id=? WHERE id=?",leader,team);
    }
    public void delete(UUID team) { jdbc.update("DELETE FROM teams WHERE id=?",team); }
    public void audit(Row team, UUID actor, String action, String targetType, UUID target) {
        jdbc.update("INSERT INTO audit_events(id,hackathon_id,team_id,actor_id,action,target_type,target_id) VALUES (?,?,?,?,?,?,?)",
            UUID.randomUUID(),team.eventId(),team.id(),actor,action,targetType,target);
    }
    public boolean hasAudit(UUID team) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM audit_events WHERE team_id=?)",Boolean.class,team));
    }
    public boolean mayReadAudit(UUID team, UUID actor) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM team_members WHERE team_id=? AND user_id=?)
                OR EXISTS(SELECT 1 FROM audit_events WHERE team_id=? AND action IN
                    ('TEAM_CREATED','MEMBER_ADMITTED','MEMBER_LEFT','MEMBER_REMOVED','LEADERSHIP_TRANSFERRED') AND
                    (actor_id=? OR (target_type='USER' AND target_id=?)))
            """,Boolean.class,team,actor,team,actor,actor));
    }
    public List<Audit> audits(UUID team, int offset, int limit) {
        return jdbc.query("""
            SELECT * FROM audit_events WHERE team_id=? ORDER BY occurred_at DESC,id DESC LIMIT ? OFFSET ?
            """,(r,i) -> new Audit(r.getObject("id",UUID.class),r.getObject("actor_id",UUID.class),
                r.getString("action"),r.getString("target_type"),r.getObject("target_id",UUID.class),
                r.getTimestamp("occurred_at").toInstant()),team,limit,offset);
    }
}
