package com.teamforge.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfEnvironmentVariable(named = "TEAMFORGE_DATABASE_TESTS", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "server.servlet.session.cookie.secure=false", "teamforge.security.auth-attempts-per-minute=1000"
})
class ParticipantIntegrationTest {
    // Synthetic fixture only. All accounts created here are removed by their returned IDs.
    private static final String PASSWORD = "Test-only-long-passphrase-42";
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    private final List<UUID> created = new CopyOnWriteArrayList<>();
    private String email;
    private Session session;

    @BeforeEach void prepare() { email = "tf-test-" + UUID.randomUUID() + "@example.test"; session = new Session(); }
    @AfterEach void cleanup() { created.forEach(id -> jdbc.update("DELETE FROM users WHERE id = ?", id)); }

    class Session {
        final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
        HttpResponse<String> call(String method, String path, String body, boolean csrf, String type) throws Exception {
            String token = csrf ? token() : null;
            var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1" + path))
                .header("Content-Type", type).method(method,
                    body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
            if (token != null) request.header("X-CSRF-TOKEN", token);
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
        String token() throws Exception {
            var response = call("GET", "/auth/csrf", null, false, "application/json");
            assertThat(response.statusCode()).isEqualTo(200);
            return json(response).get("token").asText();
        }
        HttpResponse<String> get(String path) throws Exception { return call("GET", path, null, false, "application/json"); }
        HttpResponse<String> register(String name, String password) throws Exception {
            var response = call("POST", "/auth/register", mapper.writeValueAsString(Map.of(
                "email", name, "password", password, "displayName", "Test Participant")), true, "application/json");
            if (response.statusCode() == 201) created.add(UUID.fromString(json(response).get("id").asText()));
            return response;
        }
        HttpResponse<String> login(String name, String password, boolean csrf) throws Exception {
            return call("POST", "/auth/login", "email=" + URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8), csrf, "application/x-www-form-urlencoded");
        }
        HttpResponse<String> patch(String body, boolean csrf) throws Exception {
            return call("PATCH", "/me/profile", body, csrf, "application/json");
        }
        void authenticated() throws Exception {
            assertThat(register(email, PASSWORD).statusCode()).isEqualTo(201);
            assertThat(login(email, PASSWORD, true).statusCode()).isEqualTo(204);
        }
        String sessionId() { return cookies.getCookieStore().getCookies().stream()
            .filter(cookie -> cookie.getName().equals("JSESSIONID")).findFirst().orElseThrow().getValue(); }
    }
    JsonNode json(HttpResponse<String> response) throws Exception { return mapper.readTree(response.body()); }

    @Test void registrationCreatesProfileWithoutExposingHash() throws Exception {
        var response = session.register(email, PASSWORD);
        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(json(response).path("profile").path("timeZone").asText()).isEqualTo("UTC");
        assertThat(response.body()).doesNotContain("password", "hash", PASSWORD);
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(encoder.matches(PASSWORD, hash)).isTrue();
        assertThat(session.get("/me").statusCode()).isEqualTo(401);
    }
    @Test void csrfEndpointCreatesHttpOnlySession() throws Exception {
        var response = session.get("/auth/csrf");
        assertThat(json(response).get("headerName").asText()).isEqualTo("X-CSRF-TOKEN");
        assertThat(json(response).get("token").asText().length()).isGreaterThan(20);
        assertThat(response.headers().allValues("set-cookie").stream().anyMatch(value -> value.contains("HttpOnly"))).isTrue();
    }
    @Test void duplicateEmailIsCaseInsensitive() throws Exception {
        assertThat(session.register(email, PASSWORD).statusCode()).isEqualTo(201);
        assertThat(session.register(email.toUpperCase(Locale.ROOT), PASSWORD).statusCode()).isEqualTo(409);
    }
    @Test void concurrentDuplicateRegistrationCreatesOneIdentity() throws Exception {
        var first = new Session(); var second = new Session();
        first.token(); second.token();
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Callable<Integer> one = () -> { barrier.await(5, TimeUnit.SECONDS); return first.register(email, PASSWORD).statusCode(); };
            Callable<Integer> two = () -> { barrier.await(5, TimeUnit.SECONDS); return second.register(email, PASSWORD).statusCode(); };
            var a = pool.submit(one); var b = pool.submit(two);
            assertThat(List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE email = ?", Integer.class, email)).isEqualTo(1);
    }
    @Test void shortPasswordRejected() throws Exception { assertThat(session.register(email, "short").statusCode()).isEqualTo(400); }
    @Test void oversizedUtf8PasswordRejected() throws Exception { assertThat(session.register(email, "界".repeat(25)).statusCode()).isEqualTo(400); }
    @Test void invalidEmailRejected() throws Exception { assertThat(session.register("invalid", PASSWORD).statusCode()).isEqualTo(400); }
    @Test void blankDisplayNameRejected() throws Exception {
        assertThat(session.call("POST", "/auth/register", mapper.writeValueAsString(Map.of(
            "email", email, "password", PASSWORD, "displayName", "  ")), true, "application/json").statusCode()).isEqualTo(400);
    }
    @Test void loginRotatesSessionAndRestoresCurrentUser() throws Exception {
        assertThat(session.register(email, PASSWORD).statusCode()).isEqualTo(201);
        String before = session.sessionId();
        assertThat(session.login(email.toUpperCase(Locale.ROOT), PASSWORD, true).statusCode()).isEqualTo(204);
        assertThat(before.equals(session.sessionId())).isFalse();
        assertThat(json(session.get("/me")).get("email").asText()).isEqualTo(email);
        session.client = HttpClient.newBuilder().cookieHandler(session.cookies).build();
        assertThat(session.get("/me").statusCode()).isEqualTo(200);
    }
    @Test void incorrectAndUnknownCredentialsHaveSameError() throws Exception {
        assertThat(session.register(email, PASSWORD).statusCode()).isEqualTo(201);
        var wrong = session.login(email, "wrong-password", true);
        var absent = session.login("absent-" + email, PASSWORD, true);
        assertThat(wrong.statusCode()).isEqualTo(401);
        assertThat(absent.statusCode()).isEqualTo(401);
        assertThat(json(wrong).get("message")).isEqualTo(json(absent).get("message"));
    }
    @Test void oversizedLoginPasswordIsRejectedWithoutServerError() throws Exception {
        assertThat(session.login(email, "界".repeat(100), true).statusCode()).isEqualTo(401);
    }
    @Test void anonymousCurrentUserAndProfileDenied() throws Exception {
        assertThat(session.get("/me").statusCode()).isEqualTo(401);
        assertThat(session.get("/me/profile").statusCode()).isEqualTo(401);
    }
    @Test void anonymousProfileUpdateWithValidCsrfDenied() throws Exception {
        assertThat(session.patch("{\"displayName\":\"Attack\"}",true).statusCode()).isEqualTo(401);
    }
    @Test void preLoginCsrfTokenCannotMutateAuthenticatedProfile() throws Exception {
        assertThat(session.register(email,PASSWORD).statusCode()).isEqualTo(201);
        String beforeLogin = session.token();
        assertThat(session.login(email,PASSWORD,true).statusCode()).isEqualTo(204);
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/me/profile"))
            .header("Content-Type","application/json").header("X-CSRF-TOKEN",beforeLogin)
            .method("PATCH",HttpRequest.BodyPublishers.ofString("{}")).build();
        assertThat(session.client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }
    @Test void logoutInvalidatesAuthenticatedSession() throws Exception {
        session.authenticated();
        assertThat(session.call("POST", "/auth/logout", null, true, "application/json").statusCode()).isEqualTo(204);
        assertThat(session.get("/me").statusCode()).isEqualTo(401);
    }
    @Test void profileAllFieldsRoundTrip() throws Exception {
        session.authenticated();
        var response = session.patch("{\"displayName\":\"Builder\",\"skills\":[\"Java\",\"design\",\"java\"],\"timeZone\":\"Asia/Kolkata\",\"collaborationWindows\":[{\"day\":\"MONDAY\",\"start\":\"09:00\",\"end\":\"17:00\"}]}", true);
        assertThat(response.statusCode()).isEqualTo(200);
        var profile = json(session.get("/me/profile"));
        assertThat(profile.get("displayName").asText()).isEqualTo("Builder");
        assertThat(profile.get("timeZone").asText()).isEqualTo("Asia/Kolkata");
        assertThat(profile.get("skills").size()).isEqualTo(2);
        assertThat(profile.get("collaborationWindows").get(0).get("start").asText()).isEqualTo("09:00");
    }
    @Test void partialProfileUpdatePreservesOtherFields() throws Exception {
        session.authenticated();
        assertThat(session.patch("{\"timeZone\":\"Europe/London\"}", true).statusCode()).isEqualTo(200);
        assertThat(session.patch("{\"displayName\":\"Updated\"}", true).statusCode()).isEqualTo(200);
        assertThat(json(session.get("/me/profile")).get("timeZone").asText()).isEqualTo("Europe/London");
    }
    @Test void invalidTimeZoneRejected() throws Exception {
        session.authenticated(); assertThat(session.patch("{\"timeZone\":\"+05:30\"}", true).statusCode()).isEqualTo(400);
    }
    @Test void tooManySkillsRejected() throws Exception {
        session.authenticated();
        assertThat(session.patch(mapper.writeValueAsString(Map.of("skills", Collections.nCopies(21,"java"))), true).statusCode()).isEqualTo(400);
    }
    @Test void reversedAvailabilityRejected() throws Exception {
        session.authenticated();
        assertThat(session.patch("{\"collaborationWindows\":[{\"day\":\"MONDAY\",\"start\":\"17:00\",\"end\":\"09:00\"}]}",true).statusCode()).isEqualTo(400);
    }
    @Test void overlappingAvailabilityRejected() throws Exception {
        session.authenticated();
        assertThat(session.patch("{\"collaborationWindows\":[{\"day\":\"MONDAY\",\"start\":\"09:00\",\"end\":\"17:00\"},{\"day\":\"MONDAY\",\"start\":\"10:00\",\"end\":\"11:00\"}]}",true).statusCode()).isEqualTo(400);
    }
    @Test void cannotSupplyAnotherParticipantsIdentity() throws Exception {
        session.authenticated();
        var other = new Session(); var otherEmail = "other-" + email;
        var account = other.register(otherEmail, PASSWORD);
        assertThat(session.patch(mapper.writeValueAsString(Map.of("userId",json(account).get("id").asText(),"displayName","Attack")),true).statusCode()).isEqualTo(400);
        assertThat(other.login(otherEmail,PASSWORD,true).statusCode()).isEqualTo(204);
        assertThat(json(other.get("/me/profile")).get("displayName").asText()).isEqualTo("Test Participant");
    }
    @Test void registrationRequiresCsrf() throws Exception {
        assertThat(session.call("POST","/auth/register","{}",false,"application/json").statusCode()).isEqualTo(403);
    }
    @Test void loginRequiresCsrf() throws Exception { assertThat(session.login(email,PASSWORD,false).statusCode()).isEqualTo(403); }
    @Test void profileUpdateRequiresCsrf() throws Exception {
        session.authenticated(); assertThat(session.patch("{\"displayName\":\"Attack\"}",false).statusCode()).isEqualTo(403);
    }
    @Test void logoutRequiresCsrf() throws Exception {
        session.authenticated(); assertThat(session.call("POST","/auth/logout",null,false,"application/json").statusCode()).isEqualTo(403);
        assertThat(session.get("/me").statusCode()).isEqualTo(200);
    }
    @Test void csrfFromOtherSessionRejected() throws Exception {
        session.authenticated(); var other = new Session(); String foreignToken = other.token();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/me/profile"))
            .header("Content-Type","application/json").header("X-CSRF-TOKEN",foreignToken)
            .method("PATCH",HttpRequest.BodyPublishers.ofString("{}")).build();
        assertThat(session.client.send(request,HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(403);
    }
    @Test void malformedBodyRejectedWithoutSecretEcho() throws Exception {
        session.authenticated(); var response=session.patch("{broken",true);
        assertThat(response.statusCode()).isEqualTo(400); assertThat(response.body()).doesNotContain("Exception", "{broken");
    }
}
