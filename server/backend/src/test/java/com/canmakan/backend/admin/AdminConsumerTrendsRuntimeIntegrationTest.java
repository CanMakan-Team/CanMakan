package com.canmakan.backend.admin;

import static com.canmakan.backend.shared.security.JwtTestTokenFactory.issueExpiredAccessToken;
import static org.assertj.core.api.Assertions.assertThat;

import com.canmakan.backend.analytics.Uc7IsolatedDatabase;
import com.canmakan.backend.product.verdict.Finding;
import com.canmakan.backend.shared.security.JwtProperties;
import com.canmakan.backend.shared.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
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
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            Uc7IsolatedDatabase.DATASOURCE_URL_PROPERTY,
            Uc7IsolatedDatabase.DISABLE_AUTOMATIC_SQL_INIT_PROPERTY,
            Uc7IsolatedDatabase.DISABLE_HIBERNATE_DDL_PROPERTY
        }
)
@ContextConfiguration(initializers = Uc7IsolatedDatabase.class)
@DisplayName("UC7: real consumer-trends HTTP runtime")
class AdminConsumerTrendsRuntimeIntegrationTest {

    private static final String ENDPOINT = "/api/admin/consumer-trends";
    private static final LocalDate FROM = LocalDate.of(2000, 1, 1);
    private static final LocalDate TO = LocalDate.of(2000, 1, 3);
    private static final Instant START = Instant.parse("1999-12-31T16:00:00Z");
    private static final Instant END = Instant.parse("2000-01-03T16:00:00Z");
    private static final String PASSWORD_HASH =
            "$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Y1b7q/Q9I95zW";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final List<Long> insertedScanIds = new ArrayList<>();
    private final List<Long> insertedUserIds = new ArrayList<>();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    @BeforeEach
    void verifyIsolatedDatabase() {
        Uc7IsolatedDatabase.assertConnectedToTestDatabase(dataSource);
    }

    @AfterEach
    void removeFixtures() {
        Uc7IsolatedDatabase.assertConnectedToTestDatabase(dataSource);
        for (Long scanId : insertedScanIds) {
            jdbcTemplate.update("DELETE FROM scans WHERE id = ?", scanId);
        }
        insertedScanIds.clear();
        for (Long userId : insertedUserIds) {
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
        }
        insertedUserIds.clear();
    }

    @Test
    @DisplayName("missing access token is rejected before the admin controller")
    void missingAccessTokenReturnsUnauthorized() throws Exception {
        assertSecurityResponse(get(ENDPOINT), 401, "Authentication required.");
    }

    @Test
    @DisplayName("malformed access token is rejected before the admin controller")
    void malformedAccessTokenReturnsUnauthorized() throws Exception {
        assertSecurityResponse(
                getWithAuthorization(ENDPOINT, "Bearer not-a-jwt"),
                401,
                "Authentication required."
        );
    }

    @Test
    @DisplayName("genuinely expired shared access token is rejected")
    void expiredAccessTokenReturnsUnauthorized() throws Exception {
        Long adminId = insertAccount("ADMIN", true);
        String expiredToken = issueExpiredAccessToken(jwtProperties, adminId);

        assertSecurityResponse(
                get(ENDPOINT, expiredToken),
                401,
                "Authentication required."
        );
    }

    @Test
    @DisplayName("active USER is forbidden from the admin endpoint")
    void activeUserReturnsForbidden() throws Exception {
        Long userId = insertAccount("USER", true);

        assertSecurityResponse(
                get(ENDPOINT, jwtService.issueAccessToken(userId)),
                403,
                "Access denied."
        );
    }

    @Test
    @DisplayName("inactive account is rejected despite a valid access token")
    void inactiveAccountReturnsUnauthorized() throws Exception {
        Long adminId = insertAccount("ADMIN", false);

        assertSecurityResponse(
                get(ENDPOINT, jwtService.issueAccessToken(adminId)),
                401,
                "Authentication required."
        );
    }

    @Test
    @DisplayName("current database role controls access after token issuance")
    void roleChangeToUserReturnsForbidden() throws Exception {
        Long accountId = insertAccount("ADMIN", true);
        String accessToken = jwtService.issueAccessToken(accountId);
        String validQuery = ENDPOINT + "?from=2000-01-01&to=2000-01-03&limit=10";

        assertThat(get(validQuery, accessToken).statusCode()).isEqualTo(200);

        changeAccountRole(accountId, "USER");

        assertSecurityResponse(
                get(validQuery, accessToken),
                403,
                "Access denied."
        );
    }

    @Test
    @DisplayName("active ADMIN receives database-derived aggregates over real HTTP")
    void activeAdminReturnsDatabaseDerivedConsumerTrendsOverRealHttp() throws Exception {
        Long adminId = insertAccount("ADMIN", true);
        assertReportingRangeStartsEmpty();
        insertScan(
                "WARNING",
                canonicalFindings("Peanut", "Peanut", "Milk"),
                START,
                "UC7_HTTP_DAY1_WARNING"
        );
        insertScan(
                "UNSAFE",
                canonicalFindings("Peanut"),
                START.plusSeconds(43_200),
                "UC7_HTTP_DAY1_UNSAFE"
        );
        insertScan(
                "SAFE",
                "{\"matched_rules\":[\"PEANUT\"]}",
                END.minusSeconds(1),
                "UC7_HTTP_DAY3_SAFE_LEGACY"
        );

        HttpResponse<String> response = get(
                ENDPOINT + "?from=2000-01-01&to=2000-01-03&limit=10",
                jwtService.issueAccessToken(adminId)
        );

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .startsWith(MediaType.APPLICATION_JSON_VALUE);

        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.at("/period/from").asText()).isEqualTo("2000-01-01");
        assertThat(json.at("/period/to").asText()).isEqualTo("2000-01-03");
        assertThat(json.at("/period/timezone").asText()).isEqualTo("Asia/Singapore");

        assertThat(json.at("/summary/totalScans").asLong()).isEqualTo(3);
        assertThat(json.at("/summary/safeCount").asLong()).isEqualTo(1);
        assertThat(json.at("/summary/warningCount").asLong()).isEqualTo(1);
        assertThat(json.at("/summary/unsafeCount").asLong()).isEqualTo(1);
        assertThat(json.at("/summary/uniqueProducts").asLong()).isZero();
        assertThat(json.path("mostScannedProducts")).isEmpty();
        assertThat(json.path("categoryOverview")).singleElement().satisfies(category -> {
            assertThat(category.path("category").asText()).isEqualTo("Uncategorised");
            assertThat(category.path("scanCount").asLong()).isEqualTo(3);
            assertThat(category.path("percentage").decimalValue()).isEqualByComparingTo("100.00");
        });

        JsonNode dailyTrend = json.path("dailyTrend");
        assertThat(dailyTrend.size()).isEqualTo(3);
        assertDailyPoint(dailyTrend.get(0), "2000-01-01", 2, 0, 1, 1);
        assertDailyPoint(dailyTrend.get(1), "2000-01-02", 0, 0, 0, 0);
        assertDailyPoint(dailyTrend.get(2), "2000-01-03", 1, 1, 0, 0);

        JsonNode ingredients = json.path("topFlaggedIngredients");
        assertThat(ingredients.size()).isEqualTo(2);
        assertThat(ingredients.get(0).path("ingredientName").asText()).isEqualTo("Peanut");
        assertThat(ingredients.get(0).path("flaggedCount").asLong()).isEqualTo(2);
        assertThat(ingredients.get(1).path("ingredientName").asText()).isEqualTo("Milk");
        assertThat(ingredients.get(1).path("flaggedCount").asLong()).isEqualTo(1);
        assertThat(json.path("topRestrictions")).singleElement().satisfies(restriction -> {
            assertThat(restriction.path("restrictionCode").asText()).isEqualTo("TEST");
            assertThat(restriction.path("flaggedCount").asLong()).isEqualTo(2);
        });

        assertThat(json.at("/dataQuality/partial").asBoolean()).isTrue();
        assertThat(json.at("/dataQuality/skippedMalformedFindings").asLong()).isEqualTo(1);
        assertThat(OffsetDateTime.parse(json.path("generatedAt").asText()).getOffset())
                .isEqualTo(ZoneOffset.ofHours(8));
        assertAggregateResponseContainsNoPrivateData(json);
    }

    @Test
    @DisplayName("default and category requests retain one period-wide category overview")
    void defaultAndCategoryRequestsReturnCompatibleAggregateContracts() throws Exception {
        Long adminId = insertAccount("ADMIN", true);
        String accessToken = jwtService.issueAccessToken(adminId);

        HttpResponse<String> defaultResponse = get(ENDPOINT, accessToken);
        assertThat(defaultResponse.statusCode()).isEqualTo(200);
        JsonNode unfiltered = objectMapper.readTree(defaultResponse.body());
        LocalDate defaultFrom = LocalDate.parse(unfiltered.at("/period/from").asText());
        LocalDate defaultTo = LocalDate.parse(unfiltered.at("/period/to").asText());
        assertThat(ChronoUnit.DAYS.between(defaultFrom, defaultTo)).isEqualTo(29);
        assertThat(unfiltered.at("/period/timezone").asText()).isEqualTo("Asia/Singapore");
        assertThat(unfiltered.at("/appliedFilters/category").isNull()).isTrue();
        assertThat(unfiltered.path("categoryOverview")).isNotEmpty();
        assertAggregateResponseContainsNoPrivateData(unfiltered);

        String category = unfiltered.path("categoryOverview").get(0).path("category").asText();
        String encodedCategory = URLEncoder.encode(category, StandardCharsets.UTF_8);
        HttpResponse<String> categoryResponse = get(
                ENDPOINT + "?category=" + encodedCategory,
                accessToken
        );

        assertThat(categoryResponse.statusCode()).isEqualTo(200);
        JsonNode filtered = objectMapper.readTree(categoryResponse.body());
        assertThat(filtered.at("/appliedFilters/category").asText()).isEqualTo(category);
        assertThat(filtered.path("period")).isEqualTo(unfiltered.path("period"));
        assertThat(filtered.path("categoryOverview"))
                .isEqualTo(unfiltered.path("categoryOverview"));
        assertThat(filtered.at("/summary/totalScans").asLong())
                .isPositive()
                .isLessThanOrEqualTo(unfiltered.at("/summary/totalScans").asLong());
        assertThat(filtered.path("dailyTrend").findValuesAsText("date"))
                .containsExactlyElementsOf(unfiltered.path("dailyTrend").findValuesAsText("date"));
        assertAggregateResponseContainsNoPrivateData(filtered);
    }

    @Test
    @DisplayName("active ADMIN reaches the scoped semantic validation handler")
    void activeAdminWithOneSidedDateReturnsBadRequestOverRealHttp() throws Exception {
        Long adminId = insertAccount("ADMIN", true);
        HttpResponse<String> response = get(
                ENDPOINT + "?from=2000-01-01",
                jwtService.issueAccessToken(adminId)
        );

        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.path("message").asText()).isNotBlank();
    }

    @Test
    @DisplayName("active ADMIN reaches malformed parameter binding")
    void activeAdminWithMalformedLimitReturnsBadRequestOverRealHttp() throws Exception {
        Long adminId = insertAccount("ADMIN", true);
        HttpResponse<String> response = get(
                ENDPOINT + "?limit=abc",
                jwtService.issueAccessToken(adminId)
        );

        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("active ADMIN receives 400 for reversed ranges and out-of-range limits")
    void activeAdminWithInvalidSemanticCriteriaReturnsBadRequestOverRealHttp() throws Exception {
        Long adminId = insertAccount("ADMIN", true);
        String accessToken = jwtService.issueAccessToken(adminId);

        assertThat(get(
                ENDPOINT + "?from=2000-01-03&to=2000-01-01",
                accessToken
        ).statusCode()).isEqualTo(400);
        assertThat(get(ENDPOINT + "?limit=21", accessToken).statusCode()).isEqualTo(400);
    }

    private HttpResponse<String> get(String pathAndQuery) throws Exception {
        return getWithAuthorization(pathAndQuery, null);
    }

    private HttpResponse<String> get(String pathAndQuery, String accessToken) throws Exception {
        return getWithAuthorization(pathAndQuery, "Bearer " + accessToken);
    }

    private HttpResponse<String> getWithAuthorization(
            String pathAndQuery,
            String authorization
    ) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + pathAndQuery))
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .GET();
        if (authorization != null) {
            request.header(HttpHeaders.AUTHORIZATION, authorization);
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

    private static void assertAggregateResponseContainsNoPrivateData(JsonNode response) {
        assertThat(response.properties()).extracting(java.util.Map.Entry::getKey)
                .containsExactlyInAnyOrder(
                        "period",
                        "appliedFilters",
                        "summary",
                        "dailyTrend",
                        "mostScannedProducts",
                        "categoryOverview",
                        "topRestrictions",
                        "topFlaggedIngredients",
                        "dataQuality",
                        "generatedAt"
                );
        assertThat(response.toString()).doesNotContain(
                "userId",
                "profileId",
                "familyId",
                "email",
                "barcode",
                "scanId",
                "findingsJson",
                "findings",
                "aiExplanation"
        );
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
                    "uc7-security-" + UUID.randomUUID() + "@example.test"
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

    private void changeAccountRole(Long userId, String roleName) {
        int updated = jdbcTemplate.update(
                """
                UPDATE users
                SET role_id = (SELECT id FROM roles WHERE name = ?)
                WHERE id = ?
                """,
                roleName,
                userId
        );
        assertThat(updated).isOne();
    }

    private void assertReportingRangeStartsEmpty() {
        Long existingScans = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM scans
                WHERE scanned_at >= FROM_UNIXTIME(?)
                  AND scanned_at < FROM_UNIXTIME(?)
                """,
                Long.class,
                START.getEpochSecond(),
                END.getEpochSecond()
        );
        assertThat(existingScans).isZero();
    }

    private String canonicalFindings(String... ingredientNames) throws Exception {
        List<Finding> findings = java.util.Arrays.stream(ingredientNames)
                .map(name -> new Finding("TEST", name, "Runtime fixture"))
                .toList();
        return objectMapper.writeValueAsString(findings);
    }

    private void insertScan(
            String verdict,
            String findingsJson,
            Instant scannedAt,
            String marker
    ) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int inserted = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO scans (
                        user_id,
                        profile_id,
                        verdict,
                        ai_explanation,
                        findings_json,
                        scanned_at
                    ) VALUES (4, 1, ?, ?, ?, FROM_UNIXTIME(?))
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, verdict);
            statement.setString(2, marker);
            statement.setString(3, findingsJson);
            statement.setLong(4, scannedAt.getEpochSecond());
            return statement;
        }, keyHolder);

        assertThat(inserted).isOne();
        Long scanId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        insertedScanIds.add(scanId);
        Long storedEpoch = jdbcTemplate.queryForObject(
                "SELECT UNIX_TIMESTAMP(scanned_at) FROM scans WHERE id = ?",
                Long.class,
                scanId
        );
        assertThat(storedEpoch).isEqualTo(scannedAt.getEpochSecond());
    }

    private static void assertDailyPoint(
            JsonNode point,
            String date,
            long total,
            long safe,
            long warning,
            long unsafe
    ) {
        assertThat(point.path("date").asText()).isEqualTo(date);
        assertThat(point.path("totalCount").asLong()).isEqualTo(total);
        assertThat(point.path("safeCount").asLong()).isEqualTo(safe);
        assertThat(point.path("warningCount").asLong()).isEqualTo(warning);
        assertThat(point.path("unsafeCount").asLong()).isEqualTo(unsafe);
    }
}
