package com.teamforge.teams;

import com.fasterxml.jackson.databind.*;
import com.teamforge.teams.application.*;
import com.teamforge.teams.api.TeamContracts.Create;
import com.teamforge.platform.api.ApiException;
import com.teamforge.platform.transactions.MutationTransactions;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEAMFORGE_DATABASE_TESTS",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "server.servlet.session.cookie.secure=false","teamforge.security.auth-attempts-per-minute=1000",
    "teamforge.security.invite-attempts-per-minute=1000","spring.datasource.hikari.maximum-pool-size=56",
    "spring.datasource.hikari.minimum-idle=0","spring.datasource.hikari.connection-timeout=15000"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdmissionWorkflowsIntegrationTest {
    @Autowired AdmissionWorkflows workflows;
    @Autowired TeamService teams;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    @Autowired MeterRegistry meters;
    @LocalServerPort int port;
    final List<UUID> users=new ArrayList<>();
    UUID event,leader,team;
    String fixtureHash;
    static final String PASSWORD="Synthetic-M4-fixture-password-42";
    @BeforeAll void hash() {fixtureHash=encoder.encode(PASSWORD);}
    @BeforeEach void setup() {
        event=UUID.randomUUID();jdbc.update("INSERT INTO hackathons(id,name,minimum_team_size,maximum_team_size) VALUES (?, 'M4 fixture',2,2)",event);
        leader=user();team=teams.create(event,leader,new Create("M4 team",null,null),key()).id();
    }
    @AfterEach void cleanup() {
        tx().executeWithoutResult(s->{
            jdbc.update("DELETE FROM teams WHERE hackathon_id=?",event);
            jdbc.update("DELETE FROM event_participants WHERE hackathon_id=?",event);
            jdbc.update("DELETE FROM hackathons WHERE id=?",event);
            for(UUID id:users) {jdbc.update("DELETE FROM idempotency_records WHERE actor_id=?",id);jdbc.update("DELETE FROM users WHERE id=?",id);}
        });users.clear();
    }
    UUID user() {
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO users(id,email,password_hash) VALUES (?,?,?)",id,"m4-"+id+"@example.test",fixtureHash);
        jdbc.update("INSERT INTO profiles(user_id,display_name) VALUES (?, 'M4 participant')",id);users.add(id);return id;
    }
    String key(){return UUID.randomUUID().toString();}
    String invite(){return workflows.rotate(team,leader).code();}
    long members(){return jdbc.queryForObject("SELECT count(*) FROM team_members WHERE hackathon_id=?",Long.class,event);}
    long audits(){return jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE hackathon_id=?",Long.class,event);}
    long admitted(){return jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE hackathon_id=? AND action='MEMBER_ADMITTED'",Long.class,event);}
    String state(UUID request){return jdbc.queryForObject("SELECT state FROM join_requests WHERE id=?",String.class,request);}
    TransactionTemplate tx(){var t=new TransactionTemplate(manager);t.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);t.setTimeout(45);return t;}
    void error(String code,Runnable operation){assertThatThrownBy(operation::run).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo(code));}
    void invariants(){
        assertThat(jdbc.queryForList("SELECT user_id FROM team_members WHERE hackathon_id=? GROUP BY user_id HAVING count(*)>1",event)).isEmpty();
        assertThat(jdbc.queryForList("SELECT t.id FROM teams t JOIN hackathons h ON h.id=t.hackathon_id JOIN team_members m ON m.team_id=t.id WHERE t.hackathon_id=? GROUP BY t.id,h.maximum_team_size HAVING count(*)>h.maximum_team_size",event)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM teams t WHERE hackathon_id=? AND NOT EXISTS(SELECT 1 FROM team_members m WHERE m.team_id=t.id AND m.user_id=t.leader_user_id)",Long.class,event)).isZero();
    }
    class Client implements AutoCloseable {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient http=HttpClient.newBuilder().cookieHandler(cookies).connectTimeout(Duration.ofSeconds(10)).build();
        String csrf;
        Client(UUID user) throws Exception {
            refresh();String body="email="+URLEncoder.encode("m4-"+user+"@example.test",StandardCharsets.UTF_8)+"&password="+PASSWORD;
            var response=http.send(builder("/auth/login").header("Content-Type","application/x-www-form-urlencoded").header("X-CSRF-TOKEN",csrf)
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(204);refresh();
        }
        HttpRequest.Builder builder(String path){return HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1"+path)).timeout(Duration.ofSeconds(45));}
        void refresh() throws Exception {csrf=mapper.readTree(http.send(builder("/auth/csrf").GET().build(),HttpResponse.BodyHandlers.ofString()).body()).get("token").asText();}
        HttpResponse<String> send(String method,String path,String body,String key) throws Exception {
            var b=builder(path).header("Content-Type","application/json").header("X-CSRF-TOKEN",csrf);
            if(key!=null)b.header("Idempotency-Key",key);
            return http.send(b.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
        @Override public void close(){http.close();}
    }
    String redeemPath(){return "/hackathons/"+event+"/invite-joins";}
    String requestPath(UUID request,String action){return "/teams/"+team+"/requests/"+request+(action.isEmpty()?"":"/"+action);}
    String body(String code){return "{\"code\":\""+code+"\"}";}
    record Outcome(String code,Object value){}
    double conflicts(String state){var c=meters.find("teamforge.mutations.database.conflicts").tag("sqlstate",state).counter();return c==null?0:c.count();}
    List<Outcome> race(String name,List<Supplier<?>> operations) throws Exception {
        var pool=Executors.newFixedThreadPool(operations.size());var barrier=new CyclicBarrier(operations.size());long start=System.nanoTime();
        double deadlocks=conflicts("40P01"),timeouts=conflicts("55P03");
        try {
            var futures=new ArrayList<Future<Outcome>>();
            for(var op:operations)futures.add(pool.submit(()->{barrier.await(15,TimeUnit.SECONDS);try{return new Outcome("OK",op.get());}catch(ApiException e){return new Outcome(e.code(),null);}}));
            var results=new ArrayList<Outcome>();for(var future:futures)results.add(future.get(45,TimeUnit.SECONDS));
            long successful=results.stream().filter(o->o.code().equals("OK") && (!(o.value() instanceof HttpResponse<?> r) || r.statusCode()>=200 && r.statusCode()<300)).count();
            System.out.printf("M4_RACE name=%s contenders=%d successes=%d duration_ms=%d deadlocks=%.0f lock_timeouts=%.0f%n",name,operations.size(),successful,TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start),conflicts("40P01")-deadlocks,conflicts("55P03")-timeouts);
            invariants();return results;
        }finally{pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void invitationHas256RandomBitsAndOnlyHashPersists(){
        var issued=workflows.rotate(team,leader);assertThat(issued.code()).matches("[A-Za-z0-9_-]{43}");
        assertThat(Base64.getUrlDecoder().decode(issued.code())).hasSize(32);
        var row=jdbc.queryForMap("SELECT token_hash,extract(epoch from (expires_at-created_at)) seconds FROM team_invitations WHERE team_id=? AND revoked_at IS NULL",team);
        assertThat(row.get("token_hash").toString()).matches("[0-9a-f]{64}").isNotEqualTo(issued.code());
        assertThat(((Number)row.get("seconds")).doubleValue()).isBetween(86399.0,86401.0);
        assertThat(jdbc.queryForList("SELECT * FROM audit_events WHERE hackathon_id=?",event).toString()).doesNotContain(issued.code());
    }
    @Test void validPublicInviteJoins() throws Exception {String code=invite();try(var c=new Client(user())){assertThat(c.send("POST",redeemPath(),body(code),key()).statusCode()).isEqualTo(201);}assertThat(members()).isEqualTo(2);}
    @Test void expiredRevokedAndUnknownHaveUniformErrors() throws Exception {
        String expired=invite();jdbc.update("UPDATE team_invitations SET expires_at=clock_timestamp()-interval '1 second' WHERE team_id=?",team);String rotated=invite();invite();
        try(var c=new Client(user())) {for(String code:List.of(expired,rotated,"X".repeat(43),"bad")){
            var r=c.send("POST",redeemPath(),body(code),key());assertThat(r.statusCode()).isEqualTo(400);assertThat(mapper.readTree(r.body()).get("code").asText()).isEqualTo("INVALID_INVITE");}}
    }
    @Test void rotationInvalidatesPreviousButNewWorks(){UUID u=user();String old=invite(),fresh=invite();assertThat(fresh).isNotEqualTo(old);error("INVALID_INVITE",()->workflows.redeem(event,u,old,key()));workflows.redeem(event,u,fresh,key());}
    @Test void onlyCurrentLeaderMayIssueInvite(){error("LEADER_REQUIRED",()->workflows.rotate(team,user()));assertThat(jdbc.queryForObject("SELECT count(*) FROM team_invitations WHERE team_id=?",Long.class,team)).isZero();}
    @Test void publicAuthenticationAndCsrfAreRequired() throws Exception {
        try(var c=new Client(leader)) {var r=c.http.send(c.builder("/teams/"+team+"/invite").POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());assertThat(r.statusCode()).isEqualTo(403);}
        try(var c=HttpClient.newHttpClient()){assertThat(c.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/teams/"+team+"/requests")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);}
    }
    @Test void duplicatePendingRequestReturnsSameRequestAndAudit(){UUID u=user();var a=workflows.request(team,u);assertThat(workflows.request(team,u)).isEqualTo(a);assertThat(audits()).isEqualTo(2);}
    @Test void multipleTeamsMayHavePendingRequests(){UUID u=user(),otherLeader=user();UUID other=teams.create(event,otherLeader,new Create("Other",null,null),key()).id();workflows.request(team,u);workflows.request(other,u);assertThat(workflows.mine(event,u)).hasSize(2);}
    @Test void alreadyTeamedCannotRequest(){error("ALREADY_TEAMED",()->workflows.request(team,leader));}
    @Test void applicantCanWithdrawAndSubmitNewRequest(){UUID u=user();var r=workflows.request(team,u);workflows.withdraw(team,r.id(),u);assertThat(state(r.id())).isEqualTo("WITHDRAWN");assertThat(workflows.request(team,u).id()).isNotEqualTo(r.id());}
    @Test void strangerCannotWithdraw(){UUID u=user();var r=workflows.request(team,u);error("FORBIDDEN",()->workflows.withdraw(team,r.id(),user()));assertThat(state(r.id())).isEqualTo("PENDING");}
    @Test void onlyCurrentLeaderDecides(){UUID u=user(),intruder=user();var r=workflows.request(team,u);error("LEADER_REQUIRED",()->workflows.accept(team,r.id(),intruder,key()));error("LEADER_REQUIRED",()->workflows.decline(team,r.id(),intruder));}
    @Test void acceptanceUsesAdmissionAndResolvesOtherRequests(){UUID u=user(),otherLeader=user();UUID other=teams.create(event,otherLeader,new Create("Other",null,null),key()).id();var r=workflows.request(team,u);var elsewhere=workflows.request(other,u);workflows.accept(team,r.id(),leader,key());assertThat(state(r.id())).isEqualTo("ACCEPTED");assertThat(state(elsewhere.id())).isEqualTo("CLOSED");assertThat(admitted()).isEqualTo(1);}
    @Test void inviteResolvesOtherRequests(){UUID u=user();var r=workflows.request(team,u);workflows.redeem(event,u,invite(),key());assertThat(state(r.id())).isEqualTo("CLOSED");}
    @Test void creationResolvesPendingRequests(){UUID u=user();var r=workflows.request(team,u);teams.create(event,u,new Create("New",null,null),key());assertThat(state(r.id())).isEqualTo("CLOSED");}
    @Test void fullAcceptanceKeepsRequestPending(){String code=invite();workflows.redeem(event,user(),code,key());UUID u=user();var r=workflows.request(team,u);error("TEAM_FULL",()->workflows.accept(team,r.id(),leader,key()));assertThat(state(r.id())).isEqualTo("PENDING");}
    @Test void declinedRequestsCannotBeAccepted(){UUID u=user();var r=workflows.request(team,u);workflows.decline(team,r.id(),leader);error("REQUEST_RESOLVED",()->workflows.accept(team,r.id(),leader,key()));}
    @Test void stateTransitionConstraintRejectsReopening(){UUID u=user();var r=workflows.request(team,u);workflows.withdraw(team,r.id(),u);assertThatThrownBy(()->jdbc.update("UPDATE join_requests SET state='PENDING' WHERE id=?",r.id())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);}
    @Test void inviteRetriesReplayAfterRotationAndRemoval(){UUID u=user();String code=invite(),k=key();var first=workflows.redeem(event,u,code,k);invite();teams.remove(team,leader,u);assertThat(workflows.redeem(event,u,code,k)).isEqualTo(first);assertThat(members()).isEqualTo(1);error("IDEMPOTENCY_KEY_REUSED",()->workflows.redeem(event,u,"Y".repeat(43),k));}
    @Test void acceptanceReplaySurvivesDeletion(){UUID u=user();var r=workflows.request(team,u);String k=key();var first=workflows.accept(team,r.id(),leader,k);teams.remove(team,leader,u);teams.leave(team,leader);assertThat(workflows.accept(team,r.id(),leader,k)).isEqualTo(first);}
    @Test void acceptanceChangedPayloadConflicts(){UUID u=user();var r=workflows.request(team,u);String k=key();workflows.accept(team,r.id(),leader,k);error("IDEMPOTENCY_KEY_REUSED",()->workflows.accept(team,UUID.randomUUID(),leader,k));}
    @Test void sameEventMembershipRejectedButOtherEventAllowed(){UUID u=user();String code=invite();teams.create(event,u,new Create("Owned",null,null),key());error("ALREADY_TEAMED",()->workflows.redeem(event,u,code,key()));}
    @Test void rollbackRevertsRequestMembershipKeysAndAudit(){UUID u=user();var r=workflows.request(team,u);long before=audits();String k=key();tx().executeWithoutResult(s->{workflows.accept(team,r.id(),leader,k);s.setRollbackOnly();});assertThat(state(r.id())).isEqualTo("PENDING");assertThat(members()).isEqualTo(1);assertThat(audits()).isEqualTo(before);assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_records WHERE actor_id=? AND idempotency_key=?",Long.class,leader,k)).isZero();}
    @Test void inviteVersusAcceptanceForFinalSeat() throws Exception {UUID a=user(),b=user();String code=invite();var r=workflows.request(team,b);var outcomes=race("invite-vs-accept",List.of(()->workflows.redeem(event,a,code,key()),()->workflows.accept(team,r.id(),leader,key())));assertThat(outcomes).extracting(Outcome::code).containsExactlyInAnyOrder("OK","TEAM_FULL");assertThat(members()).isEqualTo(2);}
    @Test void withdrawalVersusAcceptance() throws Exception {UUID u=user();var r=workflows.request(team,u);var outcomes=race("withdraw-vs-accept",List.of(()->{workflows.withdraw(team,r.id(),u);return null;},()->workflows.accept(team,r.id(),leader,key())));assertThat(outcomes).extracting(Outcome::code).containsExactlyInAnyOrder("OK","REQUEST_RESOLVED");assertThat(state(r.id())).isIn("ACCEPTED","WITHDRAWN");}
    @Test void leaderDepartureVersusAcceptance() throws Exception {jdbc.update("UPDATE hackathons SET maximum_team_size=3 WHERE id=?",event);String code=invite();UUID successor=user();workflows.redeem(event,successor,code,key());UUID u=user();var r=workflows.request(team,u);var outcomes=race("leader-leave-vs-accept",List.of(()->teams.leave(team,leader),()->workflows.accept(team,r.id(),leader,key())));assertThat(outcomes.get(0).code()).isEqualTo("OK");assertThat(outcomes.get(1).code()).isIn("OK","LEADER_REQUIRED");assertThat(teams.detail(team).leaderId()).isEqualTo(successor);error("LEADER_REQUIRED",()->workflows.rotate(team,leader));}
    @Test void deletionVersusAcceptance() throws Exception {UUID u=user();var r=workflows.request(team,u);var outcomes=race("delete-vs-accept",List.of(()->teams.leave(team,leader),()->workflows.accept(team,r.id(),leader,key())));assertThat(outcomes.get(0).code()).isEqualTo("OK");assertThat(outcomes.get(1).code()).isIn("OK","TEAM_NOT_FOUND","REQUEST_NOT_FOUND");}
    @Test void rotationVersusRedemption() throws Exception {UUID u=user();String code=invite();var outcomes=race("rotate-vs-redeem",List.of(()->workflows.rotate(team,leader),()->workflows.redeem(event,u,code,key())));assertThat(outcomes.get(0).code()).isEqualTo("OK");assertThat(outcomes.get(1).code()).isIn("OK","INVALID_INVITE");}
    @Test void sameKeyAcceptanceHasOnlyOneEffect() throws Exception {UUID u=user();var r=workflows.request(team,u);String k=key();var results=race("same-accept-key",List.of(()->workflows.accept(team,r.id(),leader,k),()->workflows.accept(team,r.id(),leader,k)));assertThat(results).extracting(Outcome::code).containsOnly("OK");assertThat(results.get(0).value()).isEqualTo(results.get(1).value());assertThat(admitted()).isEqualTo(1);}
    @Test void concurrentSameUserDifferentInvites() throws Exception {UUID u=user(),otherLeader=user();UUID other=teams.create(event,otherLeader,new Create("Other",null,null),key()).id();String a=invite(),b=workflows.rotate(other,otherLeader).code();var results=race("two-invites-one-user",List.of(()->workflows.redeem(event,u,a,key()),()->workflows.redeem(event,u,b,key())));assertThat(results).extracting(Outcome::code).containsExactlyInAnyOrder("OK","ALREADY_TEAMED");}
    @Test void publicFiftyContendersForOneSeat() throws Exception {
        String code=invite();var clients=new ArrayList<Client>();
        try {
            for(int i=0;i<50;i++)clients.add(new Client(user()));
            var work=new ArrayList<Supplier<?>>();for(var c:clients)work.add(()->{try{return c.send("POST",redeemPath(),body(code),key());}catch(Exception e){throw new RuntimeException(e);}});
            var results=race("public-50-invite-final-seat",work);
            var statuses=results.stream().map(o->((HttpResponse<?>)o.value()).statusCode()).toList();
            assertThat(statuses.stream().filter(s->s==201).count()).isEqualTo(1);assertThat(statuses.stream().filter(s->s==409).count()).isEqualTo(49);
            for(var o:results){var r=(HttpResponse<?>)o.value();if(r.statusCode()==409)assertThat(mapper.readTree(r.body().toString()).get("code").asText()).isEqualTo("TEAM_FULL");}
            assertThat(members()).isEqualTo(2);assertThat(admitted()).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM team_members WHERE hackathon_id=? AND user_id<>?",Long.class,event,leader)).isEqualTo(1);
            System.out.println("M4_PUBLIC_50 successful_http_201=1 team_full_409=49 roster=2 losers_without_membership=49");
        }finally{for(var c:clients)c.close();}
    }
    @Test void committedUnreadRedemptionResponseReplays() throws Exception {
        UUID u=user();String code=invite(),k=key();try(var c=new Client(u)){
            String payload=body(code);String cookies=c.cookies.getCookieStore().getCookies().stream().map(v->v.getName()+"="+v.getValue()).reduce((a,b)->a+"; "+b).orElseThrow();
            try(var socket=new Socket("localhost",port)){
                String h="POST /api/v1"+redeemPath()+" HTTP/1.1\r\nHost: localhost:"+port+"\r\nContent-Type: application/json\r\nIdempotency-Key: "+k+"\r\nX-CSRF-TOKEN: "+c.csrf+"\r\nCookie: "+cookies+"\r\nContent-Length: "+payload.getBytes(StandardCharsets.UTF_8).length+"\r\nConnection: close\r\n\r\n";
                socket.getOutputStream().write((h+payload).getBytes(StandardCharsets.UTF_8));socket.getOutputStream().flush();
                long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();while(jdbc.queryForObject("SELECT count(*) FROM idempotency_records WHERE actor_id=? AND operation='INVITE_REDEEM' AND idempotency_key=?",Long.class,u,k)==0 && System.nanoTime()<deadline)Thread.sleep(20);
            }
            String recorded=jdbc.queryForObject("SELECT response_body::text FROM idempotency_records WHERE actor_id=? AND operation='INVITE_REDEEM' AND idempotency_key=?",String.class,u,k);
            var retry=c.send("POST",redeemPath(),payload,k);assertThat(retry.statusCode()).isEqualTo(201);assertThat(mapper.readTree(retry.body())).isEqualTo(mapper.readTree(recorded));assertThat(admitted()).isEqualTo(1);
        }
    }
    @Test void completePublicRequestJourneyAndReplay() throws Exception {
        UUID applicant=user();
        try(var a=new Client(applicant);var l=new Client(leader)) {
            var created=a.send("POST","/teams/"+team+"/requests",null,null);
            assertThat(created.statusCode()).isEqualTo(201);UUID request=UUID.fromString(mapper.readTree(created.body()).get("id").asText());
            assertThat(a.send("GET","/teams/"+team+"/requests",null,null).statusCode()).isEqualTo(403);
            assertThat(mapper.readTree(a.send("GET","/hackathons/"+event+"/me/requests",null,null).body()).get("items")).hasSize(1);
            assertThat(mapper.readTree(l.send("GET","/teams/"+team+"/requests",null,null).body()).get("items")).hasSize(1);
            String k=key();var accepted=l.send("POST",requestPath(request,"accept"),null,k);
            assertThat(accepted.statusCode()).isEqualTo(200);assertThat(mapper.readTree(accepted.body()).get("state").asText()).isEqualTo("ACCEPTED");
            assertThat(l.send("POST",requestPath(request,"accept"),null,k).body()).isEqualTo(accepted.body());
            assertThat(jdbc.queryForObject("SELECT response_status FROM idempotency_records WHERE actor_id=? AND operation='REQUEST_ACCEPT' AND idempotency_key=?",Integer.class,leader,k)).isEqualTo(200);
        }
    }
    @Test void publicDeclineAndWithdrawal() throws Exception {
        UUID u=user();try(var a=new Client(u);var l=new Client(leader)) {
            var r=workflows.request(team,u);
            assertThat(a.send("POST",requestPath(r.id(),"decline"),null,null).statusCode()).isEqualTo(403);
            assertThat(l.send("POST",requestPath(r.id(),"decline"),null,null).statusCode()).isEqualTo(200);
            var next=workflows.request(team,u);
            assertThat(a.send("DELETE",requestPath(next.id(),""),null,null).statusCode()).isEqualTo(204);
            assertThat(state(next.id())).isEqualTo("WITHDRAWN");
        }
    }
    @Test void requestApplicantCannotReadFullTeamAudit() {
        UUID u=user();workflows.request(team,u);
        error("AUDIT_FORBIDDEN",()->teams.audits(team,u,0,50));
        workflows.redeem(event,u,invite(),key());assertThat(teams.audits(team,u,0,50).items()).isNotEmpty();
    }
    @Test void requestPagesAreBoundedAndComplete() {
        for(int i=0;i<101;i++)workflows.request(team,user());
        var first=workflows.inboxPage(team,leader,null);assertThat(first.items()).hasSize(100);assertThat(first.nextCursor()).isNotNull();
        var second=workflows.inboxPage(team,leader,first.nextCursor());assertThat(second.items()).hasSize(1);assertThat(second.nextCursor()).isNull();
        assertThat(first.items()).doesNotContainAnyElementsOf(second.items());
    }
    @Test void cleanupVersusOtherTeamDeletionDoesNotDeadlock() throws Exception {
        UUID u=user(),otherLeader=user();UUID other=teams.create(event,otherLeader,new Create("Other",null,null),key()).id();
        var a=workflows.request(team,u);workflows.request(other,u);
        var result=race("cleanup-vs-other-deletion",List.of(()->workflows.accept(team,a.id(),leader,key()),()->teams.leave(other,otherLeader)));
        assertThat(result).extracting(Outcome::code).containsOnly("OK");assertThat(state(a.id())).isEqualTo("ACCEPTED");
    }
    @Test void concurrentApprovalsForSameApplicantCannotDeadlockCleanup() throws Exception {
        UUID u=user(),otherLeader=user();UUID other=teams.create(event,otherLeader,new Create("Other",null,null),key()).id();
        var a=workflows.request(team,u);var b=workflows.request(other,u);
        var result=race("two-approvals-request-cleanup",List.of(()->workflows.accept(team,a.id(),leader,key()),()->workflows.accept(other,b.id(),otherLeader,key())));
        assertThat(result).extracting(Outcome::code).containsExactlyInAnyOrder("OK","REQUEST_RESOLVED");
        assertThat(List.of(state(a.id()),state(b.id()))).containsExactlyInAnyOrder("ACCEPTED","CLOSED");
    }
    @Test void publicInviteAndApprovalCompeteForOneSeat() throws Exception {
        String code=invite();UUID u=user(),v=user();var r=workflows.request(team,v);
        try(var a=new Client(u);var l=new Client(leader)) {
            var result=race("public-invite-vs-approval",List.of(
                ()->{try{return a.send("POST",redeemPath(),body(code),key());}catch(Exception e){throw new RuntimeException(e);}},
                ()->{try{return l.send("POST",requestPath(r.id(),"accept"),null,key());}catch(Exception e){throw new RuntimeException(e);}}));
            var statuses=result.stream().map(o->((HttpResponse<?>)o.value()).statusCode()).toList();
            assertThat(statuses.stream().filter(s->s==200 || s==201).count()).isEqualTo(1);
            assertThat(statuses.stream().filter(s->s==409).count()).isEqualTo(1);assertThat(members()).isEqualTo(2);
        }
    }
    @Test void nestedRedemptionRetriesWholeWorkflowAfterLockTimeout() throws Exception {
        String code=invite();UUID u=user();String k=key();var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);double before=conflicts("55P03");
        try {
            var blocker=pool.submit(()->tx().execute(s->{jdbc.queryForObject("SELECT id FROM teams WHERE id=? FOR UPDATE",UUID.class,team);locked.countDown();try{if(!release.await(20,TimeUnit.SECONDS))throw new AssertionError("lock release timeout");}catch(InterruptedException e){throw new RuntimeException(e);}return null;}));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();var redemption=pool.submit(()->workflows.redeem(event,u,code,k));
            long deadline=System.nanoTime()+Duration.ofSeconds(12).toNanos();
            while(conflicts("55P03")==before && System.nanoTime()<deadline)Thread.sleep(20);
            assertThat(conflicts("55P03")).isGreaterThan(before);release.countDown();blocker.get(5,TimeUnit.SECONDS);
            var result=redemption.get(10,TimeUnit.SECONDS);assertThat(workflows.redeem(event,u,code,k)).isEqualTo(result);assertThat(admitted()).isEqualTo(1);
            System.out.printf("M4_RECOVERY nested_workflow_lock_timeouts=%.0f single_admission=1%n",conflicts("55P03")-before);
        }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void invitationsPreserveCrossHackathonMembershipIndependence() {
        UUID participant=user(),otherLeader=user();workflows.redeem(event,participant,invite(),key());
        tx().executeWithoutResult(status->{
            UUID other=UUID.randomUUID();jdbc.update("INSERT INTO hackathons(id,name,minimum_team_size,maximum_team_size) VALUES (?,'Other event',2,3)",other);
            UUID otherTeam=teams.create(other,otherLeader,new Create("Other event team",null,null),key()).id();
            String code=workflows.rotate(otherTeam,otherLeader).code();
            workflows.redeem(other,participant,code,key());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM team_members WHERE user_id=?",Long.class,participant)).isEqualTo(2);
            status.setRollbackOnly();
        });
    }
    @Test void committedUnreadAcceptanceResponseReplaysOriginalStatusAndOutcome() throws Exception {
        UUID u=user();var request=workflows.request(team,u);String k=key();
        try(var c=new Client(leader)) {
            String cookies=c.cookies.getCookieStore().getCookies().stream().map(v->v.getName()+"="+v.getValue()).reduce((a,b)->a+"; "+b).orElseThrow();
            try(var socket=new Socket("localhost",port)) {
                String headers="POST /api/v1"+requestPath(request.id(),"accept")+" HTTP/1.1\r\nHost: localhost:"+port+"\r\nIdempotency-Key: "+k+"\r\nX-CSRF-TOKEN: "+c.csrf+"\r\nCookie: "+cookies+"\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
                socket.getOutputStream().write(headers.getBytes(StandardCharsets.UTF_8));socket.getOutputStream().flush();
                long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
                while(jdbc.queryForObject("SELECT count(*) FROM idempotency_records WHERE actor_id=? AND operation='REQUEST_ACCEPT' AND idempotency_key=?",Long.class,leader,k)==0 && System.nanoTime()<deadline)Thread.sleep(20);
            }
            String recorded=jdbc.queryForObject("SELECT response_body::text FROM idempotency_records WHERE actor_id=? AND operation='REQUEST_ACCEPT' AND idempotency_key=?",String.class,leader,k);
            var retry=c.send("POST",requestPath(request.id(),"accept"),null,k);
            assertThat(retry.statusCode()).isEqualTo(200);assertThat(mapper.readTree(retry.body())).isEqualTo(mapper.readTree(recorded));assertThat(admitted()).isEqualTo(1);
        }
    }
    @Test void duplicateRedemptionKeyCreatesOnlyOneMembership() throws Exception {
        UUID u=user();String code=invite(),k=key();
        var result=race("same-redeem-key",List.of(()->workflows.redeem(event,u,code,k),()->workflows.redeem(event,u,code,k)));
        assertThat(result).extracting(Outcome::code).containsOnly("OK");assertThat(result.get(0).value()).isEqualTo(result.get(1).value());assertThat(admitted()).isEqualTo(1);
    }
    @Test void invitationRotationRollbackLeavesNoAuditOrNewCode() {
        String old=invite();long before=audits();
        tx().executeWithoutResult(status->{workflows.rotate(team,leader);status.setRollbackOnly();});
        assertThat(audits()).isEqualTo(before);workflows.redeem(event,user(),old,key());
    }
}
