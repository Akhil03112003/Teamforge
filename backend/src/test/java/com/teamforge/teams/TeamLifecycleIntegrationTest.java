package com.teamforge.teams;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamforge.teams.api.TeamContracts.Create;
import com.teamforge.teams.application.TeamService;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEAMFORGE_DATABASE_TESTS", matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "server.servlet.session.cookie.secure=false", "teamforge.security.auth-attempts-per-minute=1000"
})
class TeamLifecycleIntegrationTest {
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;
    @Autowired TeamService service;
    private final List<UUID> users=new ArrayList<>(), events=new ArrayList<>();
    private UUID event;
    private Client leader;
    private static final String PASSWORD="Synthetic-team-test-passphrase-42";

    @BeforeEach void prepare() throws Exception { event=event(2,5);leader=participant(); }
    @AfterEach void cleanup() {
        // Only fixture event/user IDs; immutable audit snapshots intentionally remain.
        transaction.executeWithoutResult(status -> {
            events.forEach(id -> jdbc.update("DELETE FROM teams WHERE hackathon_id=?",id));
            events.forEach(id -> jdbc.update("DELETE FROM event_participants WHERE hackathon_id=?",id));
            events.forEach(id -> jdbc.update("DELETE FROM hackathons WHERE id=?",id));
            users.forEach(id -> jdbc.update("DELETE FROM users WHERE id=?",id));
        });
    }
    private UUID event(int min,int max) {
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO hackathons(id,name,minimum_team_size,maximum_team_size,active) VALUES (?,?,?,?,true)",id,"Lifecycle fixture",min,max);events.add(id);return id;
    }
    class Client {
        UUID id;
        final HttpClient http=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();
        HttpResponse<String> request(String method,String path,Object body,boolean csrf) throws Exception {
            return request(method,path,body==null ? null : mapper.writeValueAsString(body),csrf,"application/json");
        }
        HttpResponse<String> request(String method,String path,String body,boolean csrf,String contentType) throws Exception {
            var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path)).header("Content-Type",contentType);
            if (csrf) builder.header("X-CSRF-TOKEN",json(get("/auth/csrf")).get("token").asText());
            return http.send(builder.method(method,body==null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
        HttpResponse<String> get(String path) throws Exception { return request("GET",path,null,false); }
        HttpResponse<String> create(UUID hackathon) throws Exception { return request("POST","/hackathons/"+hackathon+"/teams",Map.of("name","Lifecycle Team"),true); }
        HttpResponse<String> leave(UUID team) throws Exception { return request("DELETE","/teams/"+team+"/members/me",null,true); }
    }
    private Client participant() throws Exception {
        var client=new Client();String email="m2-"+UUID.randomUUID()+"@example.test";
        var registered=client.request("POST","/auth/register",Map.of("email",email,"password",PASSWORD,"displayName","Lifecycle Participant"),true);
        assertThat(registered.statusCode()).isEqualTo(201);client.id=UUID.fromString(json(registered).get("id").asText());users.add(client.id);
        var logged=client.request("POST","/auth/login","email="+URLEncoder.encode(email,StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(PASSWORD,StandardCharsets.UTF_8),true,"application/x-www-form-urlencoded");
        assertThat(logged.statusCode()).isEqualTo(204);return client;
    }
    private JsonNode json(HttpResponse<String> response) throws Exception { return mapper.readTree(response.body()); }
    private UUID team() throws Exception {
        var response=leader.create(event);assertThat(response.statusCode()).isEqualTo(201);return UUID.fromString(json(response).get("id").asText());
    }
    private long count(String table,UUID eventId) { return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE hackathon_id=?",Long.class,eventId); }
    private JsonNode detail(UUID id) throws Exception { var response=leader.get("/teams/"+id);assertThat(response.statusCode()).isEqualTo(200);return json(response); }
    private void fixtureMember(UUID team,Client user,Instant joined,UUID membership) {
        transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO event_participants(hackathon_id,user_id) VALUES (?,?) ON CONFLICT DO NOTHING",event,user.id);
            jdbc.queryForList("SELECT user_id FROM event_participants WHERE hackathon_id=? AND user_id=? FOR NO KEY UPDATE",event,user.id);
            jdbc.queryForList("SELECT id FROM teams WHERE id=? FOR UPDATE",team);
            jdbc.update("INSERT INTO team_members(id,hackathon_id,team_id,user_id,joined_at) VALUES (?,?,?,?,?)",membership,event,team,user.id,Timestamp.from(joined));
        });
    }
    private void fixtureMember(UUID team,Client user) { fixtureMember(team,user,Instant.now(),UUID.randomUUID()); }

    @Test void createsLeaderMembershipAndIncompleteTeamAtomically() throws Exception {
        UUID id=team();var result=detail(id);
        assertThat(result.get("leaderId").asText()).isEqualTo(leader.id.toString());
        assertThat(result.get("members").size()).isEqualTo(1);assertThat(result.get("status").asText()).isEqualTo("INCOMPLETE");
        assertThat(count("teams",event)).isEqualTo(1);assertThat(count("team_members",event)).isEqualTo(1);assertThat(count("audit_events",event)).isEqualTo(1);
    }
    @Test void duplicateCreateCannotLeaveOrphanTeam() throws Exception {
        team();assertThat(leader.create(event).statusCode()).isEqualTo(409);
        assertThat(count("teams",event)).isEqualTo(1);assertThat(count("team_members",event)).isEqualTo(1);assertThat(count("audit_events",event)).isEqualTo(1);
    }
    @Test void sameParticipantMayHaveOneTeamInEachHackathon() throws Exception {
        team();UUID other=event(1,3);assertThat(leader.create(other).statusCode()).isEqualTo(201);
        assertThat(count("team_members",event)).isEqualTo(1);assertThat(count("team_members",other)).isEqualTo(1);
    }
    @Test void minimumIsReadinessRatherThanAdmissionRequirement() throws Exception {
        UUID id=team();var member=participant();fixtureMember(id,member);
        assertThat(detail(id).get("status").asText()).isEqualTo("READY");
        assertThat(member.leave(id).statusCode()).isEqualTo(200);
        assertThat(detail(id).get("status").asText()).isEqualTo("INCOMPLETE");
    }
    @Test void usesEventConfiguredLimits() throws Exception {
        UUID configured=event(3,4);var response=leader.create(configured);
        assertThat(response.statusCode()).isEqualTo(201);assertThat(json(response).get("minSize").asInt()).isEqualTo(3);
        assertThat(json(response).get("maxSize").asInt()).isEqualTo(4);assertThat(json(response).get("status").asText()).isEqualTo("INCOMPLETE");
    }
    @Test void databaseCapacityGuardRejectsSecondSeatWhenMaximumIsOne() throws Exception {
        UUID solo=event(1,1);var response=leader.create(solo);UUID id=UUID.fromString(json(response).get("id").asText());var other=participant();
        assertThat(json(response).get("status").asText()).isEqualTo("READY");
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO event_participants(hackathon_id,user_id) VALUES (?,?)",solo,other.id);
            jdbc.update("INSERT INTO team_members(id,hackathon_id,team_id,user_id) VALUES (?,?,?,?)",UUID.randomUUID(),solo,id,other.id);
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("team_members",solo)).isEqualTo(1);
    }
    @Test void leaderLeaveTransfersToEarliestRemainingMember() throws Exception {
        UUID id=team();var first=participant();var second=participant();
        fixtureMember(id,second,Instant.parse("2026-01-02T00:00:00Z"),UUID.randomUUID());
        fixtureMember(id,first,Instant.parse("2026-01-01T00:00:00Z"),UUID.randomUUID());
        var response=leader.leave(id);assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("leaderId").asText()).isEqualTo(first.id.toString());
        assertThat(detail(id).get("members").size()).isEqualTo(2);
        assertThat(first.request("PATCH","/teams/"+id,Map.of("name","New leader"),true).statusCode()).isEqualTo(200);
        assertThat(leader.request("PATCH","/teams/"+id,Map.of("name","Old leader"),true).statusCode()).isEqualTo(403);
    }
    @Test void tiedJoinTimesUseMembershipIdAsStableTieBreaker() throws Exception {
        UUID id=team();var first=participant();var second=participant();Instant time=Instant.parse("2026-01-01T00:00:00Z");
        fixtureMember(id,second,time,UUID.fromString("00000000-0000-4000-8000-000000000002"));
        fixtureMember(id,first,time,UUID.fromString("00000000-0000-4000-8000-000000000001"));
        assertThat(json(leader.leave(id)).get("leaderId").asText()).isEqualTo(first.id.toString());
    }
    @Test void lastDepartureDeletesActiveTeamAndRetainsReadableHistory() throws Exception {
        UUID id=team();var response=leader.leave(id);
        assertThat(json(response).get("deleted").asBoolean()).isTrue();assertThat(json(response).get("leaderId").isNull()).isTrue();
        assertThat(leader.get("/teams/"+id).statusCode()).isEqualTo(404);assertThat(count("teams",event)).isZero();assertThat(count("team_members",event)).isZero();
        var history=leader.get("/teams/"+id+"/audit-events");assertThat(history.statusCode()).isEqualTo(200);
        assertThat(json(history).get("items").size()).isEqualTo(3);
    }
    @Test void nonleaderLeaveDoesNotChangeLeader() throws Exception {
        UUID id=team();var member=participant();fixtureMember(id,member);
        assertThat(member.leave(id).statusCode()).isEqualTo(200);assertThat(detail(id).get("leaderId").asText()).isEqualTo(leader.id.toString());
    }
    @Test void leaderCanRemoveAnotherMemberAndAuditTarget() throws Exception {
        UUID id=team();var member=participant();fixtureMember(id,member);
        assertThat(leader.request("DELETE","/teams/"+id+"/members/"+member.id,null,true).statusCode()).isEqualTo(204);
        assertThat(detail(id).get("members").size()).isEqualTo(1);
        var history=json(member.get("/teams/"+id+"/audit-events")).get("items");
        assertThat(history.get(0).get("action").asText()).isEqualTo("MEMBER_REMOVED");assertThat(history.get(0).get("targetId").asText()).isEqualTo(member.id.toString());
    }
    @Test void nonleaderCannotRemoveOrEdit() throws Exception {
        UUID id=team();var member=participant();fixtureMember(id,member);
        assertThat(member.request("DELETE","/teams/"+id+"/members/"+leader.id,null,true).statusCode()).isEqualTo(403);
        assertThat(member.request("PATCH","/teams/"+id,Map.of("name","Unauthorized"),true).statusCode()).isEqualTo(403);
        assertThat(count("audit_events",event)).isEqualTo(1);
    }
    @Test void leaderMustUseLeaveInsteadOfSelfRemoval() throws Exception {
        UUID id=team();var response=leader.request("DELETE","/teams/"+id+"/members/"+leader.id,null,true);
        assertThat(response.statusCode()).isEqualTo(409);assertThat(json(response).get("code").asText()).isEqualTo("USE_LEAVE");assertThat(count("team_members",event)).isEqualTo(1);
    }
    @Test void outsiderCannotLeaveOrReadAudit() throws Exception {
        UUID id=team();var outsider=participant();assertThat(outsider.leave(id).statusCode()).isEqualTo(403);
        assertThat(outsider.get("/teams/"+id+"/audit-events").statusCode()).isEqualTo(403);
        leader.leave(id);assertThat(outsider.get("/teams/"+id+"/audit-events").statusCode()).isEqualTo(403);
    }
    @Test void leaderCannotRemoveAnUnrelatedParticipant() throws Exception {
        UUID id=team();var outsider=participant();assertThat(leader.request("DELETE","/teams/"+id+"/members/"+outsider.id,null,true).statusCode()).isEqualTo(404);
        assertThat(count("audit_events",event)).isEqualTo(1);
    }
    @Test void leaderUpdatesDetailsAndNormalizedSkillsWithAudit() throws Exception {
        UUID id=team();var response=leader.request("PATCH","/teams/"+id,Map.of("name","Updated team","description","Build something useful","desiredSkills",List.of("Java","design","java")),true);
        assertThat(response.statusCode()).isEqualTo(200);assertThat(json(response).get("desiredSkills").size()).isEqualTo(2);
        assertThat(detail(id).get("description").asText()).isEqualTo("Build something useful");assertThat(count("audit_events",event)).isEqualTo(2);
    }
    @Test void invalidDetailsDoNotChangeTeamOrAudit() throws Exception {
        UUID id=team();assertThat(leader.request("PATCH","/teams/"+id,Map.of("name","  "),true).statusCode()).isEqualTo(400);
        assertThat(detail(id).get("name").asText()).isEqualTo("Lifecycle Team");assertThat(count("audit_events",event)).isEqualTo(1);
    }
    @Test void rosterIsAuthenticatedAndDoesNotExposeEmails() throws Exception {
        UUID id=team();var response=leader.get("/teams/"+id+"/members");assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("items").size()).isEqualTo(1);assertThat(response.body()).doesNotContain("email","password");
        assertThat(new Client().get("/teams/"+id+"/members").statusCode()).isEqualTo(401);
    }
    @Test void mutationsRequireCsrfAndServerDerivedIdentity() throws Exception {
        assertThat(leader.request("POST","/hackathons/"+event+"/teams",Map.of("name","Missing CSRF"),false).statusCode()).isEqualTo(403);
        assertThat(leader.request("POST","/hackathons/"+event+"/teams",Map.of("name","Forged","leaderId",UUID.randomUUID()),true).statusCode()).isEqualTo(400);
        assertThat(new Client().create(event).statusCode()).isEqualTo(401);assertThat(count("teams",event)).isZero();
    }
    @Test void databaseRejectsSecondMembershipEvenWithoutApplicationPrecheck() throws Exception {
        team();UUID otherTeam=UUID.randomUUID();
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO teams(id,hackathon_id,name,leader_user_id) VALUES (?,?,?,?)",otherTeam,event,"Constraint fixture",leader.id);
            jdbc.update("INSERT INTO team_members(id,hackathon_id,team_id,user_id) VALUES (?,?,?,?)",UUID.randomUUID(),event,otherTeam,leader.id);
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("teams",event)).isEqualTo(1);assertThat(count("team_members",event)).isEqualTo(1);
    }
    @Test void databaseRejectsCrossHackathonTeamReference() throws Exception {
        UUID id=team();UUID other=event(2,5);var member=participant();
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO event_participants(hackathon_id,user_id) VALUES (?,?)",other,member.id);
            jdbc.update("INSERT INTO team_members(id,hackathon_id,team_id,user_id) VALUES (?,?,?,?)",UUID.randomUUID(),other,id,member.id);
        })).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void deferredConstraintRequiresLeaderToBeATeamMember() throws Exception {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO event_participants(hackathon_id,user_id) VALUES (?,?)",event,leader.id);
            jdbc.update("INSERT INTO teams(id,hackathon_id,name,leader_user_id) VALUES (?,?,?,?)",UUID.randomUUID(),event,"Missing member",leader.id);
            jdbc.execute("SET CONSTRAINTS teams_leader_is_member IMMEDIATE");
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(count("teams",event)).isZero();
    }
    @Test void outerRollbackRevertsTeamMembershipAndAuditTogether() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.create(event,leader.id,new Create("Rolled back",null,null));throw new IllegalStateException("Synthetic rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(count("teams",event)).isZero();assertThat(count("team_members",event)).isZero();assertThat(count("audit_events",event)).isZero();assertThat(count("event_participants",event)).isZero();
    }
    @Test void auditContainsActorTargetTimestampAndIsImmutable() throws Exception {
        UUID id=team();var item=json(leader.get("/teams/"+id+"/audit-events")).get("items").get(0);
        assertThat(item.get("actorId").asText()).isEqualTo(leader.id.toString());assertThat(item.get("targetId").asText()).isEqualTo(id.toString());assertThat(Instant.parse(item.get("timestamp").asText())).isNotNull();
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_events SET action='TEAM_DELETED' WHERE team_id=?",id)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_events WHERE team_id=?",id)).isInstanceOf(DataAccessException.class);
        assertThat(count("audit_events",event)).isEqualTo(1);
    }
    @Test void departedParticipantMayCreateAnotherTeamWithoutDeletingHistory() throws Exception {
        UUID old=team();leader.leave(old);assertThat(leader.create(event).statusCode()).isEqualTo(201);
        assertThat(leader.get("/teams/"+old+"/audit-events").statusCode()).isEqualTo(200);assertThat(count("teams",event)).isEqualTo(1);
    }
    @Test void boardUsesBoundedCursorAndMineIsEventScoped() throws Exception {
        UUID own=team();var other=participant();assertThat(other.create(event).statusCode()).isEqualTo(201);
        var first=json(leader.get("/hackathons/"+event+"/teams?limit=1"));assertThat(first.get("items").size()).isEqualTo(1);
        var second=json(leader.get("/hackathons/"+event+"/teams?limit=1&cursor="+first.get("nextCursor").asText()));
        assertThat(second.get("items").get(0).get("id")).isNotEqualTo(first.get("items").get(0).get("id"));
        assertThat(json(leader.get("/hackathons/"+event+"/me/team")).get("id").asText()).isEqualTo(own.toString());
        assertThat(leader.get("/hackathons/"+event(2,5)+"/me/team").statusCode()).isEqualTo(204);
        assertThat(leader.get("/hackathons/"+event+"/teams?limit=0").statusCode()).isEqualTo(400);
    }
    @Test void unknownTeamAndHackathonReturnNotFound() throws Exception {
        assertThat(leader.get("/teams/"+UUID.randomUUID()).statusCode()).isEqualTo(404);
        assertThat(leader.create(UUID.randomUUID()).statusCode()).isEqualTo(404);
    }
}
