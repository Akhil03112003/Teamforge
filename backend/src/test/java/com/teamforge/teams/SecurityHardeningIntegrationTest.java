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
    "teamforge.security.invite-attempts-per-minute=1000","spring.datasource.hikari.maximum-pool-size=10",
    "spring.datasource.hikari.minimum-idle=0","spring.datasource.hikari.connection-timeout=15000"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityHardeningIntegrationTest {
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

    @Test void fixedLengthOversizedJsonIsRejectedBeforeValidation() throws Exception {
        try(var c=new Client(leader)){var r=c.send("POST","/teams/"+team,"{\"private-marker\":\""+"x".repeat(70000)+"\"}",key());assertThat(r.statusCode()).isEqualTo(413);assertThat(r.body()).contains("REQUEST_TOO_LARGE").doesNotContain("private-marker");}
    }
    @Test void chunkedJsonCannotBypassSizeLimit() throws Exception {
        try(var c=new Client(leader)){
            byte[] payload=("{\"private-marker\":\""+"x".repeat(70000)+"\"}").getBytes(StandardCharsets.UTF_8);
            var r=c.http.send(c.builder("/auth/register").header("Content-Type","application/json").header("X-CSRF-TOKEN",c.csrf)
                .POST(HttpRequest.BodyPublishers.ofInputStream(()->new java.io.ByteArrayInputStream(payload))).build(),HttpResponse.BodyHandlers.ofString());
            assertThat(r.statusCode()).isEqualTo(413);assertThat(r.body()).doesNotContain("private-marker");
        }
    }
    @Test void loginCredentialsInUrlAreRejected() throws Exception {
        try(var c=new Client(leader)){var r=c.send("POST","/auth/login?email=private-marker&password=private-marker",null,null);assertThat(r.statusCode()).isEqualTo(400);assertThat(r.body()).doesNotContain("private-marker");}
    }
    @Test void duplicateAndMalformedLoginFormsAreRejected() throws Exception {
        try(var c=new Client(leader)){
            for(String body:List.of("email=one&email=two&password=value","password=private-marker%ZZ")){
                var r=c.http.send(c.builder("/auth/login").header("Content-Type","application/x-www-form-urlencoded").header("X-CSRF-TOKEN",c.csrf)
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
                assertThat(r.statusCode()).isEqualTo(400);assertThat(r.body()).doesNotContain("private-marker");
            }
        }
    }
    @Test void everyCookieMutationRequiresCsrf() throws Exception {
        UUID applicant=user();var request=workflows.request(team,applicant);
        try(var c=new Client(leader)){
            var paths=List.of("/auth/register","/auth/login","/auth/logout","/hackathons/"+event+"/teams",
                "/teams/"+team+"/invite","/hackathons/"+event+"/invite-joins","/teams/"+team+"/requests",
                "/teams/"+team+"/requests/"+request.id()+"/accept","/teams/"+team+"/requests/"+request.id()+"/decline");
            for(String path:paths){var r=c.http.send(c.builder(path).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString());assertThat(r.statusCode()).as(path).isEqualTo(403);}
            for(String path:List.of("/teams/"+team,"/me/profile")){var r=c.http.send(c.builder(path).header("Content-Type","application/json").method("PATCH",HttpRequest.BodyPublishers.ofString("{}")).build(),HttpResponse.BodyHandlers.ofString());assertThat(r.statusCode()).isEqualTo(403);}
            for(String path:List.of("/teams/"+team+"/members/me","/teams/"+team+"/members/"+applicant,"/teams/"+team+"/requests/"+request.id())){
                assertThat(c.http.send(c.builder(path).DELETE().build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
            }
        }
    }
    @Test void nonLeaderCannotUseAnyLeaderOnlySurface() throws Exception {
        UUID applicant=user();var r=workflows.request(team,applicant);
        try(var c=new Client(applicant)){
            for(String path:List.of("/teams/"+team+"/requests","/teams/"+team+"/suggestions","/teams/"+team+"/audit-events"))assertThat(c.send("GET",path,null,null).statusCode()).isEqualTo(403);
            for(String path:List.of("/teams/"+team+"/invite",requestPath(r.id(),"accept"),requestPath(r.id(),"decline")))assertThat(c.send("POST",path,null,key()).statusCode()).isEqualTo(403);
            assertThat(c.send("PATCH","/teams/"+team,"{\"name\":\"forged\"}",null).statusCode()).isEqualTo(403);
            assertThat(c.send("DELETE","/teams/"+team+"/members/"+leader,null,null).statusCode()).isEqualTo(403);
        }
    }
    @Test void wrongEventInvitationReturnsUniformErrorAndNoMembership() throws Exception {
        String code=invite();UUID applicant=user();try(var c=new Client(applicant)){var r=c.send("POST","/hackathons/"+UUID.randomUUID()+"/invite-joins",body(code),key());assertThat(r.statusCode()).isEqualTo(400);assertThat(mapper.readTree(r.body()).get("code").asText()).isEqualTo("INVALID_INVITE");assertThat(members()).isEqualTo(1);}
    }
    @Test void crossCallerIdempotencyCannotReplayAnotherPersonsMutation() throws Exception {
        String k=key();UUID applicant=user();var created=teams.create(event,applicant,new Create("Private outcome",null,null),k);
        try(var c=new Client(leader)){var r=c.send("POST","/hackathons/"+event+"/teams","{\"name\":\"Private outcome\"}",k);assertThat(r.statusCode()).isEqualTo(409);assertThat(r.body()).doesNotContain(created.id().toString());}
    }
    @Test void securityHeadersAndPrivateCachingAreExplicit() throws Exception {
        try(var c=new Client(leader)){var r=c.send("GET","/me",null,null);assertThat(r.statusCode()).isEqualTo(200);
            assertThat(r.headers().firstValue("Content-Security-Policy").orElseThrow()).contains("default-src 'self'","object-src 'none'","frame-ancestors 'none'");
            assertThat(r.headers().firstValue("Referrer-Policy")).contains("no-referrer");assertThat(r.headers().firstValue("X-Content-Type-Options")).contains("nosniff");
            assertThat(r.headers().firstValue("Cache-Control").orElseThrow()).contains("no-store");assertThat(r.headers().firstValue("Permissions-Policy").orElseThrow()).contains("camera=()");
        }
    }
    @Test void unsafeContentTypesAndUnexpectedErrorsNeverExposeSubmittedSecrets() throws Exception {
        try(var c=new Client(leader)){var r=c.http.send(c.builder("/me/profile").header("X-CSRF-TOKEN",c.csrf).header("Content-Type","text/plain").method("PATCH",HttpRequest.BodyPublishers.ofString("private-marker")).build(),HttpResponse.BodyHandlers.ofString());assertThat(r.statusCode()).isEqualTo(415);assertThat(r.body()).doesNotContain("private-marker","Exception","stackTrace");}
    }
}
