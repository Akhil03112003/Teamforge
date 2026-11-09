package com.teamforge.teams;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamforge.platform.api.ApiException;
import com.teamforge.platform.transactions.MutationTransactions;
import com.teamforge.teams.api.TeamContracts.Create;
import com.teamforge.teams.application.*;
import com.teamforge.teams.application.AdmissionAuthorization.LeaderApproval;
import com.teamforge.teams.application.MembershipAdmissionService.Command;
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
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
    "server.servlet.session.cookie.secure=false", "teamforge.security.auth-attempts-per-minute=1000",
    "spring.datasource.hikari.maximum-pool-size=56", "spring.datasource.hikari.minimum-idle=0",
    "spring.datasource.hikari.connection-timeout=15000"
})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MembershipConcurrencyIntegrationTest {
    @Autowired TeamService teams;
    @Autowired MembershipAdmissionService admission;
    @Autowired MutationTransactions mutations;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired MeterRegistry meters;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    @LocalServerPort int port;
    private final List<UUID> users=new ArrayList<>();
    private UUID event,leader,team;
    private String creationKey,fixtureHash;
    private static final String PASSWORD="Synthetic-concurrency-fixture-only-42";
    private static final Create CREATE=new Create("Concurrency fixture",null,null);

    @BeforeAll void fixtureHash() { fixtureHash=encoder.encode(PASSWORD); }
    @BeforeEach void setup() {
        event=UUID.randomUUID();
        jdbc.update("INSERT INTO hackathons(id,name,minimum_team_size,maximum_team_size,active) VALUES (?, ?,2,2,true)",event,"M3 fixture");
        leader=user();creationKey=key();team=teams.create(event,leader,CREATE,creationKey).id();
    }
    @AfterEach void cleanup() {
        tx().executeWithoutResult(status -> {
            jdbc.update("DELETE FROM teams WHERE hackathon_id=?",event);
            jdbc.update("DELETE FROM event_participants WHERE hackathon_id=?",event);
            jdbc.update("DELETE FROM hackathons WHERE id=?",event);
            for (UUID id:users) {
                jdbc.update("DELETE FROM idempotency_records WHERE actor_id=?",id);
                jdbc.update("DELETE FROM users WHERE id=?",id);
            }
        });
        users.clear();
    }
    private UUID user() {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO users(id,email,password_hash) VALUES (?,?,?)",id,"m3-"+id+"@example.test",fixtureHash);
        jdbc.update("INSERT INTO profiles(user_id,display_name) VALUES (?,?)",id,"M3 synthetic participant");
        users.add(id);return id;
    }
    private String key() { return UUID.randomUUID().toString(); }
    private TransactionTemplate tx() {
        var transaction=new TransactionTemplate(manager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setTimeout(45);return transaction;
    }
    private Command command(UUID target,UUID participant,UUID actor,String key) {
        return new Command(target,participant,actor,new LeaderApproval(actor),key);
    }
    private Command command(UUID participant) { return command(team,participant,leader,key()); }
    private long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM "+table+" WHERE hackathon_id=?",Long.class,event); }
    private long membership(UUID user) { return jdbc.queryForObject("SELECT count(*) FROM team_members WHERE hackathon_id=? AND user_id=?",Long.class,event,user); }
    private long admittedAudits() { return jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE hackathon_id=? AND action='MEMBER_ADMITTED'",Long.class,event); }
    private long keyRows(UUID actor,String key) { return jdbc.queryForObject("SELECT count(*) FROM idempotency_records WHERE actor_id=? AND idempotency_key=?",Long.class,actor,key); }
    private double metric(String name,String state) {
        var counter=meters.find(name).tag("sqlstate",state).counter();return counter==null ? 0 : counter.count();
    }
    private void invariants() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM team_members m LEFT JOIN teams t ON t.id=m.team_id WHERE m.hackathon_id=? AND t.id IS NULL",Long.class,event)).isZero();
        assertThat(jdbc.queryForList("SELECT user_id FROM team_members WHERE hackathon_id=? GROUP BY user_id HAVING count(*)>1",event)).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM teams t WHERE t.hackathon_id=? AND NOT EXISTS(SELECT 1 FROM team_members m WHERE m.team_id=t.id AND m.user_id=t.leader_user_id)",Long.class,event)).isZero();
        assertThat(jdbc.queryForList("SELECT t.id FROM teams t JOIN hackathons h ON h.id=t.hackathon_id JOIN team_members m ON m.team_id=t.id WHERE t.hackathon_id=? GROUP BY t.id,h.maximum_team_size HAVING count(*)>h.maximum_team_size",event)).isEmpty();
    }
    record Outcome(String code,Object result,int connection) {}
    private List<Outcome> race(String name,List<Supplier<?>> work) throws Exception {
        var barrier=new CyclicBarrier(work.size());long start=System.nanoTime();
        var connections=ConcurrentHashMap.<Integer>newKeySet();var pool=Executors.newFixedThreadPool(work.size());
        double deadlocks=metric("teamforge.mutations.database.conflicts","40P01");
        double timeouts=metric("teamforge.mutations.database.conflicts","55P03");
        try {
            var futures=new ArrayList<Future<Outcome>>();
            for (var operation:work) futures.add(pool.submit(() -> {
                var pid=new AtomicInteger();
                try {
                    Object result=tx().execute(status -> {
                        pid.set(jdbc.queryForObject("SELECT pg_backend_pid()",Integer.class));connections.add(pid.get());
                        await(barrier);return operation.get();
                    });
                    return new Outcome("OK",result,pid.get());
                } catch (ApiException error) { return new Outcome(error.code(),null,pid.get()); }
            }));
            long deadline=System.nanoTime()+Duration.ofSeconds(45).toNanos();
            var results=new ArrayList<Outcome>();
            for (var future:futures) results.add(future.get(Math.max(1,deadline-System.nanoTime()),TimeUnit.NANOSECONDS));
            assertThat(connections.size()).isEqualTo(work.size());
            System.out.printf("M3_RACE name=%s contenders=%d connections=%d successes=%d duration_ms=%d deadlocks=%.0f lock_timeouts=%.0f%n",
                name,work.size(),connections.size(),results.stream().filter(value -> value.code().equals("OK")).count(),
                Duration.ofNanos(System.nanoTime()-start).toMillis(),metric("teamforge.mutations.database.conflicts","40P01")-deadlocks,
                metric("teamforge.mutations.database.conflicts","55P03")-timeouts);
            return results;
        } finally { pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue(); }
    }
    private static void await(CyclicBarrier barrier) {
        try { barrier.await(15,TimeUnit.SECONDS); }
        catch (Exception error) { throw new IllegalStateException("Synchronized start failed",error); }
    }
    private void error(String code,org.assertj.core.api.ThrowableAssert.ThrowingCallable work) {
        assertThatThrownBy(work).isInstanceOfSatisfying(ApiException.class,exception -> assertThat(exception.code()).isEqualTo(code));
    }

    @RepeatedTest(3) void fiftyContendersProduceExactlyOneFinalSeatWinner() throws Exception {
        var contenders=new ArrayList<UUID>();var work=new ArrayList<Supplier<?>>();
        for (int i=0;i<50;i++) { UUID participant=user();contenders.add(participant);Command command=command(participant);work.add(() -> admission.admit(command)); }
        var results=race("last-seat",work);
        assertThat(results.stream().filter(result -> result.code().equals("OK")).count()).isEqualTo(1);
        assertThat(results.stream().filter(result -> result.code().equals("TEAM_FULL")).count()).isEqualTo(49);
        UUID winner=((MembershipAdmissionService.Result)results.stream().filter(result -> result.code().equals("OK")).findFirst().orElseThrow().result()).participantId();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM team_members WHERE team_id=?",Integer.class,team)).isEqualTo(2);
        for (UUID participant:contenders) assertThat(membership(participant)).isEqualTo(participant.equals(winner) ? 1 : 0);
        assertThat(admittedAudits()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_records WHERE actor_id=? AND operation='MEMBERSHIP_ADMIT' AND state='COMPLETED'",Integer.class,leader)).isEqualTo(1);
        invariants();
    }
    @Test void oneParticipantJoiningDifferentTeamsGetsOnlyOneMembership() throws Exception {
        UUID otherLeader=user(), participant=user();UUID other=teams.create(event,otherLeader,CREATE,key()).id();
        Command first=command(team,participant,leader,key()),second=command(other,participant,otherLeader,key());
        var result=race("two-teams-one-user",List.of(() -> admission.admit(first),() -> admission.admit(second)));
        assertThat(result).extracting(Outcome::code).containsExactlyInAnyOrder("OK","ALREADY_TEAMED");
        assertThat(membership(participant)).isEqualTo(1);assertThat(admittedAudits()).isEqualTo(1);invariants();
    }
    @Test void concurrentDuplicateCreateReplaysOneTeam() throws Exception {
        UUID actor=user();String key=key();
        var result=race("same-create-key",List.of(() -> teams.create(event,actor,CREATE,key),() -> teams.create(event,actor,CREATE,key)));
        assertThat(result).extracting(Outcome::code).containsOnly("OK");
        assertThat(result.get(0).result()).isEqualTo(result.get(1).result());
        assertThat(membership(actor)).isEqualTo(1);assertThat(keyRows(actor,key)).isEqualTo(1);assertThat(count("teams")).isEqualTo(2);invariants();
    }
    @Test void concurrentDuplicateAdmissionReplaysOneMembership() throws Exception {
        UUID participant=user();Command command=command(participant);
        var result=race("same-admission-key",List.of(() -> admission.admit(command),() -> admission.admit(command)));
        assertThat(result).extracting(Outcome::code).containsOnly("OK");assertThat(result.get(0).result()).isEqualTo(result.get(1).result());
        assertThat(membership(participant)).isEqualTo(1);assertThat(admittedAudits()).isEqualTo(1);assertThat(keyRows(leader,command.idempotencyKey())).isEqualTo(1);
    }
    @Test void replayReturnsPersistedCreateAfterActiveTeamWasDeleted() {
        var original=teams.create(event,leader,CREATE,creationKey);teams.leave(team,leader);
        assertThat(teams.create(event,leader,CREATE,creationKey)).isEqualTo(original);
        assertThat(count("teams")).isZero();assertThat(count("team_members")).isZero();assertThat(count("audit_events")).isEqualTo(3);
    }
    @Test void replayAdmissionAfterRemovalDoesNotReadmit() {
        UUID participant=user();Command command=command(participant);var original=admission.admit(command);
        teams.remove(team,leader,participant);assertThat(admission.admit(command)).isEqualTo(original);
        assertThat(membership(participant)).isZero();assertThat(admittedAudits()).isEqualTo(1);
    }
    @Test void differentCreatePayloadWithSameKeyIsRejected() {
        error("IDEMPOTENCY_KEY_REUSED",() -> teams.create(event,leader,new Create("Different",null,null),creationKey));
        assertThat(count("teams")).isEqualTo(1);assertThat(count("audit_events")).isEqualTo(1);
    }
    @Test void differentAdmissionPayloadWithSameKeyIsRejected() {
        UUID first=user(),second=user();String key=key();admission.admit(command(team,first,leader,key));
        error("IDEMPOTENCY_KEY_REUSED",() -> admission.admit(command(team,second,leader,key)));
        assertThat(membership(second)).isZero();assertThat(admittedAudits()).isEqualTo(1);
    }
    @Test void sameKeyIsScopedToCaller() {
        UUID actor=user();var other=teams.create(event,actor,CREATE,creationKey);
        assertThat(other.id()).isNotEqualTo(team);assertThat(keyRows(actor,creationKey)).isEqualTo(1);invariants();
    }
    @Test void canonicalCreateFingerprintIgnoresEquivalentTagOrdering() {
        UUID actor=user();String key=key();var first=teams.create(event,actor,new Create(" Team ",null,List.of("Java","design")),key);
        assertThat(teams.create(event,actor,new Create("Team","",List.of("design","java","java")),key)).isEqualTo(first);
    }
    @Test void admissionVersusLastLeaveCannotCreateOrphans() throws Exception {
        UUID participant=user();Command command=command(participant);
        var result=race("admit-vs-delete",List.of(() -> admission.admit(command),() -> teams.leave(team,leader)));
        assertThat(result.get(1).code()).isEqualTo("OK");assertThat(result.get(0).code()).isIn("OK","TEAM_NOT_FOUND");
        assertThat(admittedAudits()).isEqualTo(result.get(0).code().equals("OK") ? 1 : 0);invariants();
    }
    @Test void admissionVersusLeaderDepartureRechecksCurrentAuthority() throws Exception {
        jdbc.update("UPDATE hackathons SET maximum_team_size=3 WHERE id=?",event);
        UUID successor=user();admission.admit(command(successor));UUID participant=user();Command command=command(participant);
        var result=race("admit-vs-leader-leave",List.of(() -> admission.admit(command),() -> teams.leave(team,leader)));
        assertThat(result.get(1).code()).isEqualTo("OK");assertThat(result.get(0).code()).isIn("OK","LEADER_REQUIRED");
        assertThat(teams.detail(team).leaderId()).isEqualTo(successor);invariants();
    }
    @Test void admissionVersusRemovalPreservesCapacity() throws Exception {
        UUID removed=user();admission.admit(command(removed));UUID participant=user();Command command=command(participant);
        var result=race("admit-vs-remove",List.of(() -> admission.admit(command),() -> { teams.remove(team,leader,removed);return "removed"; }));
        assertThat(result.get(1).code()).isEqualTo("OK");assertThat(result.get(0).code()).isIn("OK","TEAM_FULL");
        assertThat(membership(removed)).isZero();assertThat(teams.detail(team).leaderId()).isEqualTo(leader);invariants();
    }
    @Test void createVersusAdmissionSerializesSameParticipant() throws Exception {
        UUID participant=user();Command command=command(participant);String createKey=key();
        var result=race("create-vs-admit",List.of(() -> admission.admit(command),() -> teams.create(event,participant,CREATE,createKey)));
        assertThat(result).extracting(Outcome::code).containsExactlyInAnyOrder("OK","ALREADY_TEAMED");assertThat(membership(participant)).isEqualTo(1);invariants();
    }
    @Test void leaderLeaveVersusRemovingSuccessorDoesNotBreakLeaderForeignKey() throws Exception {
        jdbc.update("UPDATE hackathons SET maximum_team_size=3 WHERE id=?",event);
        UUID first=user(),second=user();admission.admit(command(first));admission.admit(command(second));
        var result=race("leave-vs-successor-removal",List.of(() -> teams.leave(team,leader),() -> { teams.remove(team,leader,first);return "removed"; }));
        assertThat(result.get(0).code()).isEqualTo("OK");assertThat(result.get(1).code()).isIn("OK","LEADER_REQUIRED");invariants();
    }
    @Test void unauthorizedAdmissionLeavesNoOutcomeMembershipOrSuccessAudit() {
        UUID participant=user(),intruder=user();Command command=command(team,participant,intruder,key());
        error("LEADER_REQUIRED",() -> admission.admit(command));assertThat(membership(participant)).isZero();assertThat(admittedAudits()).isZero();assertThat(keyRows(intruder,command.idempotencyKey())).isZero();
    }
    @Test void rolledBackAdmissionAndOutcomeCanBeRetriedSafely() {
        UUID participant=user();Command command=command(participant);
        assertThatThrownBy(() -> tx().executeWithoutResult(status -> { admission.admit(command);throw new IllegalStateException("Synthetic rollback"); })).isInstanceOf(IllegalStateException.class);
        assertThat(membership(participant)).isZero();assertThat(admittedAudits()).isZero();assertThat(keyRows(leader,command.idempotencyKey())).isZero();
        admission.admit(command);assertThat(membership(participant)).isEqualTo(1);assertThat(admittedAudits()).isEqualTo(1);
    }
    @Test void failedFullRequestDoesNotReserveKeyOrSuccessAudit() {
        UUID first=user();admission.admit(command(first));UUID participant=user();Command command=command(participant);
        error("TEAM_FULL",() -> admission.admit(command));assertThat(keyRows(leader,command.idempotencyKey())).isZero();assertThat(admittedAudits()).isEqualTo(1);
        teams.remove(team,leader,first);admission.admit(command);assertThat(membership(participant)).isEqualTo(1);assertThat(admittedAudits()).isEqualTo(2);
    }
    @Test void committedInProgressRecordIsNotStolenOrExpired() {
        String pending=key();jdbc.update("INSERT INTO idempotency_records(actor_id,operation,idempotency_key,request_hash,state,created_at) SELECT actor_id,operation,?,request_hash,'IN_PROGRESS','2000-01-01' FROM idempotency_records WHERE actor_id=? AND operation='TEAM_CREATE' AND idempotency_key=?",pending,leader,creationKey);
        error("IDEMPOTENCY_IN_PROGRESS",() -> teams.create(event,leader,CREATE,pending));assertThat(count("teams")).isEqualTo(1);
    }
    @Test void missingAndUnsafeKeysAreRejected() {
        error("INVALID_IDEMPOTENCY_KEY",() -> teams.create(event,leader,CREATE,null));
        error("INVALID_IDEMPOTENCY_KEY",() -> admission.admit(command(team,user(),leader,"bad key")));
    }
    @Test void productionTransientDeadlockRecoveryRetriesWholeTransaction() throws Exception {
        UUID otherActor=user(),other=teams.create(event,otherActor,CREATE,key()).id();
        var barrier=new CyclicBarrier(2);var first=new AtomicInteger();var second=new AtomicInteger();double before=metric("teamforge.mutations.database.conflicts","40P01");
        var pool=Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(() -> mutations.run(() -> { lock(team);if(first.incrementAndGet()==1) await(barrier);lock(other);return true; }));
            var b=pool.submit(() -> mutations.run(() -> { lock(other);if(second.incrementAndGet()==1) await(barrier);lock(team);return true; }));
            assertThat(a.get(15,TimeUnit.SECONDS)).isTrue();assertThat(b.get(15,TimeUnit.SECONDS)).isTrue();
            double observed=metric("teamforge.mutations.database.conflicts","40P01")-before;assertThat(observed).isGreaterThanOrEqualTo(1);
            System.out.printf("M3_RECOVERY injected_deadlocks=%.0f attempts=%d%n",observed,first.get()+second.get());
        } finally { pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue(); }
        invariants();
    }
    private void lock(UUID id) { jdbc.queryForList("SELECT id FROM teams WHERE id=? FOR UPDATE",id); }
    private void waitFor(CountDownLatch latch) {
        try { if(!latch.await(25,TimeUnit.SECONDS)) throw new IllegalStateException("Bounded lock fixture timed out"); }
        catch(InterruptedException error) { Thread.currentThread().interrupt();throw new IllegalStateException(error); }
    }
    @Test void lockTimeoutRollsBackClaimAndRetriesAfterRelease() throws Exception {
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        UUID participant=user();Command command=command(participant);double before=metric("teamforge.mutations.retries","55P03");long start=System.nanoTime();
        try {
            var blocker=pool.submit(() -> tx().execute(status -> { lock(team);held.countDown();waitFor(release);return true; }));
            assertThat(held.await(5,TimeUnit.SECONDS)).isTrue();var operation=pool.submit(() -> admission.admit(command));
            long deadline=System.nanoTime()+Duration.ofSeconds(12).toNanos();
            while(metric("teamforge.mutations.retries","55P03")==before && System.nanoTime()<deadline) Thread.sleep(20);
            assertThat(metric("teamforge.mutations.retries","55P03")).isGreaterThan(before);release.countDown();
            assertThat(operation.get(10,TimeUnit.SECONDS).participantId()).isEqualTo(participant);assertThat(blocker.get(5,TimeUnit.SECONDS)).isTrue();
            assertThat(keyRows(leader,command.idempotencyKey())).isEqualTo(1);assertThat(admittedAudits()).isEqualTo(1);
            System.out.printf("M3_RECOVERY released_lock_timeout_retries=%.0f duration_ms=%d%n",metric("teamforge.mutations.retries","55P03")-before,Duration.ofNanos(System.nanoTime()-start).toMillis());
        } finally { release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue(); }
    }
    @Test void persistentLockTimeoutIsBoundedAndLeavesNoClaim() throws Exception {
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();UUID participant=user();Command command=command(participant);
        double before=metric("teamforge.mutations.database.conflicts","55P03");long start=System.nanoTime();
        try {
            var blocker=pool.submit(() -> tx().execute(status -> { lock(team);held.countDown();waitFor(release);return true; }));
            assertThat(held.await(5,TimeUnit.SECONDS)).isTrue();error("REQUEST_BUSY",() -> admission.admit(command));release.countDown();assertThat(blocker.get(5,TimeUnit.SECONDS)).isTrue();
            assertThat(keyRows(leader,command.idempotencyKey())).isZero();assertThat(admittedAudits()).isZero();assertThat(membership(participant)).isZero();
            System.out.printf("M3_RECOVERY bounded_lock_timeouts=%.0f duration_ms=%d%n",metric("teamforge.mutations.database.conflicts","55P03")-before,Duration.ofNanos(System.nanoTime()-start).toMillis());
        } finally { release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(5,TimeUnit.SECONDS)).isTrue(); }
    }
    class Client {
        final CookieManager cookies=new CookieManager(null,CookiePolicy.ACCEPT_ALL);
        final HttpClient http=HttpClient.newBuilder().cookieHandler(cookies).build();
        String csrf() throws Exception {
            return mapper.readTree(http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString()).body()).get("token").asText();
        }
        void login() throws Exception {
            String body="email="+URLEncoder.encode("m3-"+leader+"@example.test",StandardCharsets.UTF_8)+"&password="+URLEncoder.encode(PASSWORD,StandardCharsets.UTF_8);
            var response=http.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/auth/login"))
                .header("Content-Type","application/x-www-form-urlencoded").header("X-CSRF-TOKEN",csrf()).POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(204);
        }
        HttpResponse<String> create(UUID hackathon,String key,String body) throws Exception {
            var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/hackathons/"+hackathon+"/teams"))
                .header("Content-Type","application/json").header("X-CSRF-TOKEN",csrf());
            if(key!=null)builder.header("Idempotency-Key",key);
            return http.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
        }
    }
    @Test void committedCreateWithUnreadHttpResponseReplaysOriginalOutcome() throws Exception {
        teams.leave(team,leader);var client=new Client();client.login();String key=key();String body="{\"name\":\"Lost response fixture\"}";String csrf=client.csrf();
        try(var socket=new Socket("localhost",port)) {
            String cookies=client.cookies.getCookieStore().getCookies().stream().map(cookie -> cookie.getName()+"="+cookie.getValue()).reduce((a,b)->a+"; "+b).orElseThrow();
            String header="POST /api/v1/hackathons/"+event+"/teams HTTP/1.1\r\nHost: localhost:"+port+"\r\nContent-Type: application/json\r\nIdempotency-Key: "+key+"\r\nX-CSRF-TOKEN: "+csrf+"\r\nCookie: "+cookies+"\r\nContent-Length: "+body.getBytes(StandardCharsets.UTF_8).length+"\r\nConnection: close\r\n\r\n";
            socket.getOutputStream().write((header+body).getBytes(StandardCharsets.UTF_8));socket.getOutputStream().flush();
            long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
            while(keyRows(leader,key)==0 && System.nanoTime()<deadline) Thread.sleep(20);
            assertThat(keyRows(leader,key)).isEqualTo(1);
            // No HTTP response bytes are consumed: close the socket after observing DB commit.
        }
        String recorded=jdbc.queryForObject("SELECT response_body::text FROM idempotency_records WHERE actor_id=? AND operation='TEAM_CREATE' AND idempotency_key=?",String.class,leader,key);
        var replay=client.create(event,key,body);assertThat(replay.statusCode()).isEqualTo(201);assertThat(mapper.readTree(replay.body())).isEqualTo(mapper.readTree(recorded));
        assertThat(count("teams")).isEqualTo(1);assertThat(membership(leader)).isEqualTo(1);invariants();
    }
    @Test void httpCreateRequiresKeyAndRejectsPayloadMismatch() throws Exception {
        var client=new Client();client.login();assertThat(client.create(event,null,"{\"name\":\"No key\"}").statusCode()).isEqualTo(400);
        var mismatch=client.create(event,creationKey,"{\"name\":\"Different\"}");assertThat(mismatch.statusCode()).isEqualTo(409);
        assertThat(mapper.readTree(mismatch.body()).get("code").asText()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }
}
