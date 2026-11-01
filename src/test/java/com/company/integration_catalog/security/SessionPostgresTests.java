package com.company.integration_catalog.security;

import com.company.integration_catalog.IntegrationCatalogApplication;
import com.company.integration_catalog.model.entity.UserAccount;
import com.company.integration_catalog.model.enums.UserRole;
import com.company.integration_catalog.repository.UserAccountRepository;
import com.company.integration_catalog.service.AccountLifecycleService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.session.Session;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "catalog.payload.postgres-test", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SessionPostgresTests {
    private static final String PASSWORD = "Session-test-password-123";
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<HttpClient> clients = new ArrayList<>();
    private Connection connection;
    private String schema;
    private String databaseUrl;
    private ServletWebServerApplicationContext first;
    private ServletWebServerApplicationContext second;
    private UserAccount user;

    @BeforeAll
    void startTwoBackendsOnTheSameIsolatedDatabaseSchema() throws Exception {
        databaseUrl = System.getProperty("catalog.payload.test-db-url",
                "jdbc:postgresql://localhost:5432/integration_payload_test_20261025");
        connection = DriverManager.getConnection(databaseUrl, "catalog_payload_test", "payload-test-only-password");
        schema = "session_test_" + UUID.randomUUID().toString().replace("-", "");
        try (var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
        }
        first = startBackend();
        second = startBackend();
    }

    private ServletWebServerApplicationContext startBackend() {
        return (ServletWebServerApplicationContext) new SpringApplicationBuilder(IntegrationCatalogApplication.class)
                .run("--server.port=0", "--spring.main.banner-mode=off",
                        "--spring.datasource.url=" + databaseUrl,
                        "--spring.datasource.username=catalog_payload_test",
                        "--spring.datasource.password=payload-test-only-password",
                        "--spring.datasource.hikari.schema=" + schema,
                        "--spring.datasource.hikari.maximum-pool-size=3",
                        "--spring.flyway.default-schema=" + schema,
                        "--catalog.credentials.key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                        "--catalog.bootstrap.username=session-test-admin",
                        "--catalog.bootstrap.password=" + PASSWORD,
                        "--catalog.ai.enabled=false", "--spring.jpa.show-sql=false",
                        "--server.servlet.session.cookie.secure=false",
                        "--logging.level.root=WARN");
    }

    @BeforeEach
    void createUser() {
        user = new UserAccount();
        user.setUsername("session-" + UUID.randomUUID());
        user.setPasswordHash(first.getBean(PasswordEncoder.class).encode(PASSWORD));
        user.setRole(UserRole.EDITOR);
        user = first.getBean(UserAccountRepository.class).save(user);
    }

    @AfterAll
    void stopBackendsAndRemoveTestSchema() throws Exception {
        try {
            if (second != null) second.close();
        } finally {
            try {
                if (first != null) first.close();
            } finally {
                if (connection != null) {
                    try (var testConnection = connection; var statement = testConnection.createStatement()) {
                        statement.execute("DROP SCHEMA " + schema + " CASCADE");
                    }
                }
            }
        }
    }

    @AfterEach
    void closeHttpClients() {
        clients.forEach(HttpClient::close);
        clients.clear();
    }

    @Test
    void sharesLoginCsrfAndLogoutAcrossBackendsUsingTheExistingCookieContract() throws Exception {
        var browser = new Browser();
        var csrfResponse = browser.get(first, "/api/v1/auth/csrf");
        assertThat(csrfResponse.statusCode()).isEqualTo(200);
        assertThat(csrfResponse.headers().allValues("Set-Cookie")).anySatisfy(cookie ->
                assertThat(cookie).startsWith("JSESSIONID=").contains("HttpOnly", "SameSite=Lax", "Path=/")
                        .doesNotContain("Secure"));
        String anonymousCookie = browser.sessionCookie();
        assertThat(browser.post(second, "/api/v1/auth/login", mapper.readTree(csrfResponse.body()),
                loginForm()).statusCode()).isEqualTo(200);
        assertThat(browser.sessionCookie()).isNotEqualTo(anonymousCookie);
        assertSignedIn(browser, first);
        assertSignedIn(browser, second);

        var stored = sessions(first).findByPrincipalName(user.getUsername());
        assertThat(stored).hasSize(1);
        Session session = stored.values().iterator().next();
        String primaryId = primaryId(session.getId());
        assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(30));
        SecurityContext security = session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        var principal = (AccountPrincipal) security.getAuthentication().getPrincipal();
        assertThat(principal.getAccountId()).isEqualTo(user.getId());
        assertThat(principal.getPassword()).isNull();

        try (var anonymousClient = HttpClient.newHttpClient()) {
            var oldSession = anonymousClient.send(HttpRequest.newBuilder(endpoint(first, "/api/v1/auth/me"))
                    .header("Cookie", anonymousCookie).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(oldSession.statusCode()).isEqualTo(401);
        }
        assertThat(browser.post(second, "/api/v1/auth/logout", null, "").statusCode()).isEqualTo(403);
        var refreshedCsrf = browser.csrf(first);
        String authenticatedCookie = browser.sessionCookie();
        var logout = browser.post(second, "/api/v1/auth/logout", refreshedCsrf, "");
        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(logout.headers().allValues("Set-Cookie")).anySatisfy(cookie ->
                assertThat(cookie).startsWith("JSESSIONID=").contains("Max-Age=0"));
        assertThat(sessions(first).findById(session.getId())).isNull();
        try (var replayClient = HttpClient.newHttpClient()) {
            var replay = replayClient.send(HttpRequest.newBuilder(endpoint(first, "/api/v1/auth/me"))
                    .header("Cookie", authenticatedCookie).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(replay.statusCode()).isEqualTo(401);
        }
        assertThat(attributeCount(primaryId)).isZero();
    }

    @Test
    void retainsAuthenticationAndCsrfAfterTheBackendRestarts() throws Exception {
        var browser = login(first);
        var csrf = browser.csrf(first);
        first.close();
        first = startBackend();
        assertSignedIn(browser, first);
        assertThat(browser.post(first, "/api/v1/auth/logout", csrf, "").statusCode()).isEqualTo(204);
        assertThat(browser.get(second, "/api/v1/auth/me").statusCode()).isEqualTo(401);
    }

    @Test
    void rejectsExpiredSessionsAndCleansUpTheirAttributes() throws Exception {
        var browser = login(first);
        String id = sessions(first).findByPrincipalName(user.getUsername()).keySet().iterator().next();
        String primaryId = primaryId(id);
        assertThat(attributeCount(primaryId)).isPositive();
        long expired = System.currentTimeMillis() - Duration.ofMinutes(31).toMillis();
        first.getBean(JdbcTemplate.class).update(
                "UPDATE SPRING_SESSION SET LAST_ACCESS_TIME = ?, EXPIRY_TIME = ? WHERE SESSION_ID = ?",
                expired, expired, id);
        assertThat(browser.get(second, "/api/v1/auth/me").statusCode()).isEqualTo(401);
        sessions(first).cleanUpExpiredSessions();
        assertThat(sessions(second).findById(id)).isNull();
        assertThat(attributeCount(primaryId)).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"revoked", "disabled", "role"})
    void accountAccessChangesInvalidatePersistedSessionsOnTheOtherBackend(String change) throws Exception {
        var browser = login(first);
        var lifecycle = first.getBean(AccountLifecycleService.class);
        switch (change) {
            case "revoked" -> lifecycle.revokeSessions(user.getId());
            case "disabled" -> lifecycle.changeAccess(user.getId(), false, null);
            case "role" -> lifecycle.changeAccess(user.getId(), null, UserRole.VIEWER);
            default -> throw new IllegalArgumentException(change);
        }
        var response = browser.get(second, "/api/v1/auth/me");
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(mapper.readTree(response.body()).get("code").asText()).isEqualTo("SESSION_INVALID");
        assertThat(sessions(first).findByPrincipalName(user.getUsername())).isEmpty();
        assertThat(browser.get(first, "/api/v1/auth/me").statusCode()).isEqualTo(401);
    }

    private Browser login(ServletWebServerApplicationContext backend) throws Exception {
        var browser = new Browser();
        assertThat(browser.post(backend, "/api/v1/auth/login", browser.csrf(backend), loginForm()).statusCode())
                .isEqualTo(200);
        return browser;
    }

    private String loginForm() {
        return "username=" + URLEncoder.encode(user.getUsername(), StandardCharsets.UTF_8) + "&password=" + PASSWORD;
    }

    private void assertSignedIn(Browser browser, ServletWebServerApplicationContext backend) throws Exception {
        var response = browser.get(backend, "/api/v1/auth/me");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(response.body()).get("username").asText()).isEqualTo(user.getUsername());
    }

    private String primaryId(String sessionId) {
        return first.getBean(JdbcTemplate.class).queryForObject(
                "SELECT PRIMARY_ID FROM SPRING_SESSION WHERE SESSION_ID = ?", String.class, sessionId);
    }

    private int attributeCount(String primaryId) {
        return first.getBean(JdbcTemplate.class).queryForObject(
                "SELECT count(*) FROM SPRING_SESSION_ATTRIBUTES WHERE SESSION_PRIMARY_ID = ?", Integer.class, primaryId);
    }

    private JdbcIndexedSessionRepository sessions(ServletWebServerApplicationContext backend) {
        return backend.getBean(JdbcIndexedSessionRepository.class);
    }

    private URI endpoint(ServletWebServerApplicationContext backend, String path) {
        return URI.create("http://localhost:" + backend.getWebServer().getPort() + path);
    }

    private class Browser {
        private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        private final HttpClient client = HttpClient.newBuilder().cookieHandler(cookies)
                .connectTimeout(Duration.ofSeconds(5)).build();

        Browser() {
            clients.add(client);
        }

        String sessionCookie() {
            return cookies.getCookieStore().getCookies().stream().filter(cookie -> cookie.getName().equals("JSESSIONID"))
                    .map(cookie -> cookie.getName() + "=" + cookie.getValue()).findFirst().orElseThrow();
        }

        HttpResponse<String> get(ServletWebServerApplicationContext backend, String path) throws Exception {
            return client.send(HttpRequest.newBuilder(endpoint(backend, path)).timeout(Duration.ofSeconds(10))
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
        }

        JsonNode csrf(ServletWebServerApplicationContext backend) throws Exception {
            var response = get(backend, "/api/v1/auth/csrf");
            assertThat(response.statusCode()).isEqualTo(200);
            return mapper.readTree(response.body());
        }

        HttpResponse<String> post(ServletWebServerApplicationContext backend, String path, JsonNode csrf, String body)
                throws Exception {
            var request = HttpRequest.newBuilder(endpoint(backend, path)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded");
            if (csrf != null) request.header(csrf.get("headerName").asText(), csrf.get("token").asText());
            return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
