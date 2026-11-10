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
    "teamforge.sse.heartbeat-ms=100","teamforge.sse.timeout-ms=10000","spring.datasource.hikari.minimum-idle=0","spring.datasource.hikari.connection-timeout=15000"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LiveDiscoveryIntegrationTest {
    @Autowired com.teamforge.live.EventStreamHub hub;
    @Autowired BoardQuery board;
    @Autowired com.teamforge.suggestions.SuggestionService suggestions;
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

    class Stream implements AutoCloseable {
        final java.io.InputStream input;
        final BlockingQueue<String> events=new LinkedBlockingQueue<>();
        final BlockingQueue<String> data=new LinkedBlockingQueue<>();
        final AtomicInteger heartbeats=new AtomicInteger();
        final Thread reader;
        Stream(Client client,UUID scope) throws Exception {
            var response=client.http.send(client.builder("/hackathons/"+scope+"/events").header("Last-Event-ID","stale-id")
                .GET().build(),HttpResponse.BodyHandlers.ofInputStream());
            assertThat(response.statusCode()).isEqualTo(200);assertThat(response.headers().firstValue("X-Accel-Buffering")).contains("no");
            input=response.body();reader=Thread.ofVirtual().start(()->{
                try(var lines=new java.io.BufferedReader(new java.io.InputStreamReader(input,StandardCharsets.UTF_8))){
                    String line;while((line=lines.readLine())!=null){if(line.startsWith("event:"))events.offer(line.substring(6).trim());
                        if(line.startsWith("data:"))data.offer(line.substring(5).trim());if(line.startsWith(":"))heartbeats.incrementAndGet();}
                }catch(java.io.IOException disconnected){}
            });
            assertThat(events.poll(5,TimeUnit.SECONDS)).isEqualTo("ready");assertThat(data.poll(5,TimeUnit.SECONDS)).isEqualTo("{\"type\":\"REFRESH\"}");
        }
        public void close() throws Exception {input.close();reader.join(1000);}
    }
    BoardQuery.Page page(String cursor,int limit,String skill,boolean open,String status,String sort){return board.board(event,cursor,limit,skill,open,status,sort);}
    UUID another(String name,List<String> skills){return teams.create(event,user(),new Create(name,null,skills),key()).id();}
    @Test void boardHasCapacityAndPublicLeaderOnly() throws Exception {
        try(var client=new Client(leader)){
            var response=client.send("GET","/hackathons/"+event+"/teams",null,null);assertThat(response.statusCode()).isEqualTo(200);
            var item=mapper.readTree(response.body()).get("items").get(0);assertThat(item.get("openSeats").asInt()).isEqualTo(1);
            assertThat(item.get("leaderDisplayName").asText()).isEqualTo("M4 participant");assertThat(response.body()).doesNotContain("@example.test","password","timeZone","collaborationWindows");
        }
    }
    @Test void boardIdPagesHaveNoDuplicates(){another("Second",List.of());another("Third",List.of());var a=page(null,1,null,false,null,"id");var b=page(a.nextCursor(),1,null,false,null,"id");var c=page(b.nextCursor(),1,null,false,null,"id");assertThat(Set.of(a.items().getFirst().id(),b.items().getFirst().id(),c.items().getFirst().id())).hasSize(3);assertThat(c.nextCursor()).isNull();}
    @Test void boardNameSortAndTiesHaveStableCursor(){another("alpha",List.of());another("Alpha",List.of());var first=page(null,1,null,false,null,"name");var second=page(first.nextCursor(),1,null,false,null,"name");assertThat(first.items().getFirst().name().toLowerCase()).isEqualTo("alpha");assertThat(second.items().getFirst().id()).isNotEqualTo(first.items().getFirst().id());}
    @Test void boardSkillOpenAndReadinessFilters(){UUID target=another("Skilled",List.of("java"));assertThat(page(null,50,"JAVA",true,"INCOMPLETE","id").items()).extracting(BoardQuery.Summary::id).containsExactly(target);workflows.redeem(event,user(),workflows.rotate(target,teams.detail(target).leaderId()).code(),key());assertThat(page(null,50,"java",true,null,"id").items()).isEmpty();assertThat(page(null,50,"java",false,"READY","id").items()).hasSize(1);}
    @Test void boardCapacityReflectsAdmissionAndDeparture(){UUID u=user();String code=invite();workflows.redeem(event,u,code,key());assertThat(page(null,50,null,false,null,"id").items().getFirst().openSeats()).isZero();teams.leave(team,u);assertThat(page(null,50,null,false,null,"id").items().getFirst().openSeats()).isEqualTo(1);}
    @Test void boardValidation(){error("VALIDATION_ERROR",()->page("bad",1,null,false,null,"id"));error("VALIDATION_ERROR",()->page(null,51,null,false,null,"id"));error("VALIDATION_ERROR",()->page(null,1,null,false,"bad","id"));error("VALIDATION_ERROR",()->page(null,1,null,false,null,"bad"));error("VALIDATION_ERROR",()->page("bad",1,null,false,null,"name"));}
    @Test void boardDoesNotExposeOtherEvents(){tx().executeWithoutResult(status->{UUID other=UUID.randomUUID();jdbc.update("INSERT INTO hackathons(id,name) VALUES (?,'Other')",other);teams.create(other,user(),new Create("Hidden team",null,null),key());assertThat(page(null,50,null,false,null,"id").items()).hasSize(1);status.setRollbackOnly();});}
    @Test void uncommittedBoardChangesAreInvisibleToIndependentConnection() throws Exception {var pool=Executors.newSingleThreadExecutor();try{tx().executeWithoutResult(status->{another("Uncommitted",List.of());try{assertThat(pool.submit(()->page(null,50,null,false,null,"id").items().size()).get(5,TimeUnit.SECONDS)).isEqualTo(1);}catch(Exception e){throw new RuntimeException(e);}status.setRollbackOnly();});}finally{pool.shutdownNow();}}
    @Test void ssePublishesOnlyAfterCommitAndCoalescesTransaction() throws Exception {
        try(var client=new Client(leader);var stream=new Stream(client,event)){
            tx().executeWithoutResult(status->{teams.edit(team,leader,new com.teamforge.teams.api.TeamContracts.Update("Committed",null,null));teams.edit(team,leader,new com.teamforge.teams.api.TeamContracts.Update("Again",null,null));try{assertThat(stream.events.poll(200,TimeUnit.MILLISECONDS)).isNull();}catch(Exception e){throw new RuntimeException(e);}});
            assertThat(stream.events.poll(5,TimeUnit.SECONDS)).isEqualTo("invalidate");assertThat(stream.data.poll(5,TimeUnit.SECONDS)).isEqualTo("{\"type\":\"REFRESH\"}");assertThat(stream.events.poll(200,TimeUnit.MILLISECONDS)).isNull();
        }
    }
    @Test void sseRollbackPublishesNothing() throws Exception {
        try(var client=new Client(leader);var stream=new Stream(client,event)){
            tx().executeWithoutResult(status->{teams.edit(team,leader,new com.teamforge.teams.api.TeamContracts.Update("Rollback",null,null));status.setRollbackOnly();});
            assertThat(stream.events.poll(300,TimeUnit.MILLISECONDS)).isNull();assertThat(teams.detail(team).name()).isEqualTo("M4 team");
        }
    }
    @Test void sseDoesNotPublishAnotherEvent() throws Exception {try(var client=new Client(leader);var stream=new Stream(client,event)){tx().executeWithoutResult(status->hub.changed(UUID.randomUUID()));assertThat(stream.events.poll(250,TimeUnit.MILLISECONDS)).isNull();}}
    @Test void workflowNotificationsContainNoTokensOrPrivateFields() throws Exception {
        try(var client=new Client(leader);var stream=new Stream(client,event)){
            var issued=workflows.rotate(team,leader);assertThat(stream.events.poll(5,TimeUnit.SECONDS)).isEqualTo("invalidate");
            String data=stream.data.poll(5,TimeUnit.SECONDS);assertThat(data).isEqualTo("{\"type\":\"REFRESH\"}").doesNotContain(issued.code(),"email","timeZone","request");
        }
    }
    @Test void reconnectSendsReadyAndRestReconcilesChanges() throws Exception {
        try(var client=new Client(leader)){
            try(var stream=new Stream(client,event)){}teams.edit(team,leader,new com.teamforge.teams.api.TeamContracts.Update("While disconnected",null,null));
            // Old emitter may linger until its next failed heartbeat write; bounded per-user allows a reconnect.
            try(var stream=new Stream(client,event)){assertThat(client.send("GET","/teams/"+team,null,null).body()).contains("While disconnected");}
        }
    }
    @Test void heartbeatsDetectAndCleanUpDisconnectedSubscribers() throws Exception {
        int before=hub.subscriberCount();try(var client=new Client(leader)){try(var stream=new Stream(client,event)){long deadline=System.nanoTime()+Duration.ofSeconds(3).toNanos();while(stream.heartbeats.get()==0 && System.nanoTime()<deadline)Thread.sleep(20);assertThat(stream.heartbeats.get()).isPositive();}
            long deadline=System.nanoTime()+Duration.ofSeconds(5).toNanos();while(hub.subscriberCount()>before && System.nanoTime()<deadline)Thread.sleep(20);assertThat(hub.subscriberCount()).isLessThanOrEqualTo(before);
        }
    }
    @Test void logoutTerminatesExistingStream() throws Exception {try(var client=new Client(leader);var stream=new Stream(client,event)){assertThat(client.send("POST","/auth/logout",null,null).statusCode()).isEqualTo(204);assertThat(stream.events.poll(5,TimeUnit.SECONDS)).isEqualTo("session-expired");}}
    @Test void streamIsAuthenticatedAndChecksEvent() throws Exception {try(var c=HttpClient.newHttpClient()){assertThat(c.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/hackathons/"+event+"/events")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(401);}try(var c=new Client(leader)){assertThat(c.send("GET","/hackathons/"+UUID.randomUUID()+"/events",null,null).statusCode()).isEqualTo(404);}}
    @Test void streamLimitRejectsThirdConnection() throws Exception {try(var c=new Client(leader);var a=new Stream(c,event);var b=new Stream(c,event)){assertThat(c.send("GET","/hackathons/"+event+"/events",null,null).statusCode()).isEqualTo(429);}}
    @Test void storedReplayDoesNotPublishAgain() throws Exception {UUID u=user();String code=invite(),k=key();workflows.redeem(event,u,code,k);try(var c=new Client(leader);var stream=new Stream(c,event)){workflows.redeem(event,u,code,k);assertThat(stream.events.poll(250,TimeUnit.MILLISECONDS)).isNull();}}
    @Test void suggestionsArePrivateLeaderOnly(){error("LEADER_REQUIRED",()->suggestions.suggest(team,user(),20));}
    @Test void suggestionsExcludeEventMembersAndSelf(){UUID u=user();UUID other=teams.create(event,u,new Create("Other",null,null),key()).id();var result=suggestions.suggest(team,leader,50);assertThat(result.items()).extracting(com.teamforge.suggestions.SuggestionService.Suggestion::userId).doesNotContain(u,leader);}
    @Test void suggestionsRankComplementarySkillsAndExplain() throws Exception {UUID candidate=user();teams.edit(team,leader,new com.teamforge.teams.api.TeamContracts.Update(null,null,List.of("rare-m5-skill")));jdbc.update("INSERT INTO profile_skills(user_id,skill) VALUES (?,'rare-m5-skill')",candidate);var result=suggestions.suggest(team,leader,50);assertThat(result.items().getFirst().userId()).isEqualTo(candidate);assertThat(result.items().getFirst().skillScore()).isEqualTo(100);try(var c=new Client(leader)){String response=c.send("GET","/teams/"+team+"/suggestions",null,null).body();assertThat(response).doesNotContain("@example.test","time_zone","timeZone","collaborationWindows","password_hash");}}
    @Test void suggestionsAreStableAndBounded(){var a=suggestions.suggest(team,leader,5);assertThat(suggestions.suggest(team,leader,5)).isEqualTo(a);assertThat(a.items()).hasSizeLessThanOrEqualTo(5);for(var row:a.items()){assertThat(row.score()).isBetween(0d,100d);assertThat(row.skillScore()).isBetween(0d,100d);assertThat(row.timeScore()).isBetween(0d,100d);}error("VALIDATION_ERROR",()->suggestions.suggest(team,leader,51));}
    @Test void fullTeamHasNoSuggestions(){workflows.redeem(event,user(),invite(),key());assertThat(suggestions.suggest(team,leader,20).items()).isEmpty();}
    @Test void missingProfileIsSafeAndScoresZero(){UUID u=user();jdbc.update("DELETE FROM profiles WHERE user_id=?",u);var row=suggestions.suggest(team,leader,50).items().stream().filter(s->s.userId().equals(u)).findFirst().orElseThrow();assertThat(row.displayName()).isEqualTo("Participant");assertThat(row.skillScore()).isZero();assertThat(row.timeScore()).isZero();}
    @Test void equalScoresUseAscendingUuidTieBreak(){
        user();user();user();var rows=suggestions.suggest(team,leader,50).items();
        var groups=rows.stream().collect(java.util.stream.Collectors.groupingBy(com.teamforge.suggestions.SuggestionService.Suggestion::score));
        for(var tied:groups.values())assertThat(tied.stream().map(row->row.userId().toString()).toList()).isSorted();
    }
    @Test void boardSearchUsesLiteralCaseInsensitiveNameAndDescriptionBeforePagination(){
        UUID target=another("Searchable robotics",List.of());
        teams.edit(target,teams.detail(target).leaderId(),new com.teamforge.teams.api.TeamContracts.Update(null,"Open-source collaborators",null));
        assertThat(board.board(event,null,1,null,false,null,"id","ROBOTICS").items()).extracting(BoardQuery.Summary::id).containsExactly(target);
        assertThat(board.board(event,null,1,null,false,null,"id","source collaborators").items()).extracting(BoardQuery.Summary::id).containsExactly(target);
        assertThat(board.board(event,null,50,null,false,null,"id","%' OR true").items()).isEmpty();
        error("VALIDATION_ERROR",()->board.board(event,null,1,null,false,null,"id","x".repeat(121)));
    }
}
