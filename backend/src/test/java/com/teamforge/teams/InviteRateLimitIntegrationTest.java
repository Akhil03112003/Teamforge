package com.teamforge.teams;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEAMFORGE_DATABASE_TESTS",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
    "server.servlet.session.cookie.secure=false","teamforge.security.invite-attempts-per-minute=2"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class InviteRateLimitIntegrationTest {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper mapper;
    @Test void authenticatedRedemptionIsRateLimitedWithoutLeakingCodes() throws Exception {
        UUID id=UUID.randomUUID();String email="m4-limit-"+id+"@example.test",password="Synthetic-rate-fixture-only-42";
        jdbc.update("INSERT INTO users(id,email,password_hash) VALUES (?,?,?)",id,email,encoder.encode(password));
        jdbc.update("INSERT INTO profiles(user_id,display_name) VALUES (?, 'Limiter fixture')",id);
        try(var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build()) {
            String base="http://localhost:"+port+"/api/v1";
            String csrf=mapper.readTree(client.send(HttpRequest.newBuilder(URI.create(base+"/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString()).body()).get("token").asText();
            assertThat(client.send(HttpRequest.newBuilder(URI.create(base+"/auth/login")).header("X-CSRF-TOKEN",csrf)
                .header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString("email="+email+"&password="+password)).build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(204);
            csrf=mapper.readTree(client.send(HttpRequest.newBuilder(URI.create(base+"/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString()).body()).get("token").asText();
            for(int attempt=0;attempt<3;attempt++) {
                var response=client.send(HttpRequest.newBuilder(URI.create(base+"/hackathons/"+UUID.randomUUID()+"/invite-joins"))
                    .header("X-CSRF-TOKEN",csrf).header("Idempotency-Key",UUID.randomUUID().toString()).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"code\":\"invalid\"}")).build(),HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(attempt<2?400:429);
                if(attempt==2){assertThat(response.headers().firstValue("Retry-After")).contains("60");assertThat(mapper.readTree(response.body()).get("code").asText()).isEqualTo("RATE_LIMITED");}
            }
            assertThat(client.send(HttpRequest.newBuilder(URI.create(base+"/health")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        } finally {jdbc.update("DELETE FROM users WHERE id=?",id);}
    }
}
