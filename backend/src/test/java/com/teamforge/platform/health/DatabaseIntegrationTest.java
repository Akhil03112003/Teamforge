package com.teamforge.platform.health;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "TEAMFORGE_DATABASE_TESTS", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DatabaseIntegrationTest {
    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;
    @LocalServerPort int port;

    @Test
    void migrationAndDatabaseReadinessAreHealthy() throws Exception {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("3");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
            URI.create("http://localhost:" + port + "/actuator/health/readiness")).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"status\":\"UP\"}");
    }

    @Test
    void databaseRejectsInvalidTeamBounds() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> jdbc.update(
            "INSERT INTO hackathons(id,name,minimum_team_size,maximum_team_size) VALUES (?,?,?,?)",
            java.util.UUID.randomUUID(), "Invalid limits", 5, 2)))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
