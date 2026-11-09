package com.teamforge.platform.health;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import com.teamforge.platform.security.SecurityConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("health-test")
@SpringBootTest(classes = HealthIntegrationTest.HealthTestConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration,"
        + "org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration",
    "management.endpoint.health.group.readiness.include=readinessState"
})
class HealthIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    @Profile("health-test")
    @EnableAutoConfiguration
    @Import({HealthController.class, SecurityConfiguration.class})
    static class HealthTestConfiguration {
        @Bean UserDetailsService noAccounts() {
            return username -> { throw new UsernameNotFoundException("No accounts in health-only test."); };
        }
    }
    @LocalServerPort
    int port;

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void publicHealthContainsOnlyStatus() throws Exception {
        var response = get("/api/v1/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("content-type").orElse("")).contains("application/json");
        assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
        assertThat(response.headers().allValues("set-cookie")).isEmpty();
    }

    @Test
    void actuatorDoesNotExposeDetails() throws Exception {
        var response = get("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);
        var body = new ObjectMapper().readValue(response.body(),
            new TypeReference<Map<String, Object>>() {});
        // Actuator publishes probe group names, but no component or connection details.
        assertThat(body).containsOnlyKeys("status", "groups");
        assertThat(body.get("status")).isEqualTo("UP");
        assertThat(body.get("groups")).isEqualTo(List.of("liveness", "readiness"));
    }

    @Test
    void otherEndpointsAreNotPublic() throws Exception {
        assertThat(get("/api/v1/teams").statusCode()).isEqualTo(401);
        assertThat(get("/actuator/env").statusCode()).isEqualTo(401);
    }
}
