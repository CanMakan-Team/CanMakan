package com.canmakan.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.canmakan.backend.shared.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "MYSQL_DB=canmakan_uc13_test"
)
@DisplayName("UC13: real System Admin account-management security runtime")
class AdminUsersRuntimeIntegrationTest {

    private static final String TEST_DATABASE = "canmakan_uc13_test";
    private static final String ENDPOINT = "/api/admin/users";
    private static final String PASSWORD_HASH =
            "$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Y1b7q/Q9I95zW";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final List<Long> insertedUserIds = new ArrayList<>();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void verifyIsolatedDatabase() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .isEqualTo(TEST_DATABASE);
    }

    @AfterEach
    void removeFixtures() {
        for (Long userId : insertedUserIds) {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        }
        insertedUserIds.clear();
    }

    @Test
    @DisplayName("missing access token returns 401")
    void missingAccessTokenReturnsUnauthorized() throws Exception {
        assertSecurityResponse(get(null), 401, "Authentication required.");
    }

    @Test
    @DisplayName("inactive ADMIN returns 401 despite a valid token")
    void inactiveAdminReturnsUnauthorized() throws Exception {
        Long adminId = insertAccount("ADMIN", false);

        assertSecurityResponse(
                get(jwtService.issueAccessToken(adminId)),
                401,
                "Authentication required."
        );
    }

    @Test
    @DisplayName("active USER returns 403")
    void activeUserReturnsForbidden() throws Exception {
        Long userId = insertAccount("USER", true);

        assertSecurityResponse(
                get(jwtService.issueAccessToken(userId)),
                403,
                "Access denied."
        );
    }

    @Test
    @DisplayName("active ADMIN reaches the real account-list endpoint")
    void activeAdminCanListAccounts() throws Exception {
        Long adminId = insertAccount("ADMIN", true);

        HttpResponse<String> response = get(jwtService.issueAccessToken(adminId));

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.isArray()).isTrue();
        assertThat(body.findValuesAsText("userId")).contains(adminId.toString());
    }

    private HttpResponse<String> get(String accessToken) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + ENDPOINT))
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .GET();
        if (accessToken != null) {
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertSecurityResponse(
            HttpResponse<String> response,
            int expectedStatus,
            String expectedMessage
    ) throws Exception {
        assertThat(response.statusCode()).isEqualTo(expectedStatus);
        assertThat(objectMapper.readTree(response.body()).path("message").asText())
                .isEqualTo(expectedMessage);
    }

    private Long insertAccount(String roleName, boolean active) {
        Long roleId = jdbcTemplate.queryForObject(
                "SELECT id FROM roles WHERE name = ?",
                Long.class,
                roleName
        );
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int inserted = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO users (role_id, email, password_hash, is_active)
                    VALUES (?, ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setLong(1, roleId);
            statement.setString(
                    2,
                    "uc13-security-" + UUID.randomUUID() + "@example.test"
            );
            statement.setString(3, PASSWORD_HASH);
            statement.setBoolean(4, active);
            return statement;
        }, keyHolder);

        assertThat(inserted).isOne();
        Long userId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        insertedUserIds.add(userId);
        return userId;
    }
}
