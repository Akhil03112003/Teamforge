package com.teamforge.platform.security;

import java.net.URI;
import java.net.http.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEAMFORGE_DATABASE_TESTS",matches="true")
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={"teamforge.security.max-sessions=2"})
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
class SessionCapacityIntegrationTest {
    @LocalServerPort int port;
    @Test void anonymousSessionCapacityIsBoundedButHealthRemainsAvailable() throws Exception {
        try(var client=HttpClient.newHttpClient()){
            for(int i=0;i<3;i++){
                var response=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/auth/csrf")).GET().build(),HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).isEqualTo(i<2?200:503);
                if(i==0)assertThat(response.headers().firstValue("Set-Cookie").orElseThrow()).contains("Secure","HttpOnly","SameSite=Lax");
                if(i==2)assertThat(response.body()).contains("SESSION_CAPACITY").doesNotContain("Exception","stackTrace");
            }
            assertThat(client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/health")).GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
        }
    }
}
