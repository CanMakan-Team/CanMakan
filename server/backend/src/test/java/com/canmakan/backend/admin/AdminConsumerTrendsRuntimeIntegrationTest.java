package com.canmakan.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.canmakan.backend.product.verdict.Finding;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "MYSQL_DB=canmakan_uc7_test"
)
@DisplayName("UC7: real consumer-trends HTTP runtime")
class AdminConsumerTrendsRuntimeIntegrationTest {

    private static final String TEST_DATABASE = "canmakan_uc7_test";
    private static final String ENDPOINT = "/api/admin/consumer-trends";
    private static final LocalDate FROM = LocalDate.of(2000, 1, 1);
    private static final LocalDate TO = LocalDate.of(2000, 1, 3);
    private static final Instant START = Instant.parse("1999-12-31T16:00:00Z");
    private static final Instant END = Instant.parse("2000-01-03T16:00:00Z");

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final List<Long> insertedScanIds = new ArrayList<>();

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void verifyIsolatedDatabase() {
        assertThat(jdbcTemplate.queryForObject("SELECT DATABASE()", String.class))
                .isEqualTo(TEST_DATABASE);
    }

    @AfterEach
    void removeFixtures() {
        for (Long scanId : insertedScanIds) {
            jdbcTemplate.update("DELETE FROM scans WHERE id = ?", scanId);
        }
        insertedScanIds.clear();
    }

    @Test
    @DisplayName("real HTTP request returns database-derived aggregates")
    void returnsDatabaseDerivedConsumerTrendsOverRealHttp() throws Exception {
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
                ENDPOINT + "?from=2000-01-01&to=2000-01-03&limit=10"
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

        assertThat(json.at("/dataQuality/partial").asBoolean()).isTrue();
        assertThat(json.at("/dataQuality/skippedMalformedFindings").asLong()).isEqualTo(1);
        assertThat(OffsetDateTime.parse(json.path("generatedAt").asText()).getOffset())
                .isEqualTo(ZoneOffset.ofHours(8));
    }

    @Test
    @DisplayName("real semantic validation returns the scoped 400 response")
    void oneSidedDateReturnsBadRequestOverRealHttp() throws Exception {
        HttpResponse<String> response = get(ENDPOINT + "?from=2000-01-01");

        assertThat(response.statusCode()).isEqualTo(400);
        JsonNode json = objectMapper.readTree(response.body());
        assertThat(json.path("message").asText()).isNotBlank();
    }

    @Test
    @DisplayName("real malformed parameter binding returns 400")
    void malformedLimitReturnsBadRequestOverRealHttp() throws Exception {
        HttpResponse<String> response = get(ENDPOINT + "?limit=abc");

        assertThat(response.statusCode()).isEqualTo(400);
    }

    private HttpResponse<String> get(String pathAndQuery) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + pathAndQuery))
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .GET()
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
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
