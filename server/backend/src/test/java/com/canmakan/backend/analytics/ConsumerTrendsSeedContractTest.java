package com.canmakan.backend.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.canmakan.backend.product.verdict.Finding;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(properties = {
    Uc7IsolatedDatabase.DATASOURCE_URL_PROPERTY,
    Uc7IsolatedDatabase.DISABLE_AUTOMATIC_SQL_INIT_PROPERTY,
    Uc7IsolatedDatabase.DISABLE_HIBERNATE_DDL_PROPERTY
})
@ContextConfiguration(initializers = Uc7IsolatedDatabase.class)
@DisplayName("UC7: canonical scan seed contract")
class ConsumerTrendsSeedContractTest {

    private static final TypeReference<List<Finding>> FINDINGS_TYPE = new TypeReference<>() {
    };

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void verifyIsolatedDatabase() {
        Uc7IsolatedDatabase.assertConnectedToTestDatabase(dataSource);
    }

    @Test
    @DisplayName("all 50 scan seeds deserialize as Finding[] without changing verdicts or products")
    void seededFindingsAreCanonicalAndRelationshipsRemainIntact() throws Exception {
        List<SeededScan> scans = jdbcTemplate.query(
                """
                SELECT id, verdict, barcode, ai_explanation, findings_json
                FROM scans
                WHERE id BETWEEN 1 AND 50
                ORDER BY id
                """,
                (resultSet, rowNumber) -> new SeededScan(
                        resultSet.getLong("id"),
                        resultSet.getString("verdict"),
                        resultSet.getString("barcode"),
                        resultSet.getString("ai_explanation"),
                        resultSet.getString("findings_json")
                )
        );

        assertThat(scans).hasSize(50);
        for (SeededScan scan : scans) {
            JsonNode canonicalJson = objectMapper.readTree(scan.findingsJson());
            assertThat(canonicalJson.isArray()).isTrue();
            canonicalJson.forEach(findingJson -> {
                Set<String> fieldNames = new HashSet<>();
                findingJson.properties().forEach(entry -> fieldNames.add(entry.getKey()));
                assertThat(fieldNames).containsExactlyInAnyOrder(
                        "restrictionCode",
                        "ingredientName",
                        "reason"
                );
            });

            List<Finding> findings = objectMapper.readValue(scan.findingsJson(), FINDINGS_TYPE);
            assertThat(findings).doesNotContainNull();
            assertThat(findings).allSatisfy(finding -> {
                assertThat(finding.restrictionCode()).isNotBlank();
                assertThat(finding.ingredientName()).isNotBlank();
                assertThat(finding.reason()).isNotBlank();
                assertThat(finding.reason()).isEqualTo(scan.aiExplanation());
            });
            if ("SAFE".equals(scan.verdict())) {
                assertThat(findings).isEmpty();
            } else {
                assertThat(findings).isNotEmpty();
            }
        }

        assertThat(scans).filteredOn(scan -> "SAFE".equals(scan.verdict())).hasSize(19);
        assertThat(scans).filteredOn(scan -> "WARNING".equals(scan.verdict())).hasSize(14);
        assertThat(scans).filteredOn(scan -> "UNSAFE".equals(scan.verdict())).hasSize(17);
        assertThat(scans).allSatisfy(scan -> assertThat(scan.barcode()).isNotBlank());

        assertFinding(scans, 2, "GLUTEN_ALLERGY", "Wheat Flour");
        assertFinding(scans, 3, "HIGH_SUGAR_WARNING", "Sugar");
        assertFinding(scans, 16, "HALAL_UNCERTAIN", "Missing Halal Certification");
        assertFinding(scans, 27, "SHELLFISH_ALLERGY", "Crab Paste");
        assertFinding(scans, 27, "NON_HALAL_INGREDIENT", "Non-Halal flavoring agent");

        Long missingProducts = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM scans s
                LEFT JOIN products p ON p.barcode = s.barcode
                WHERE s.id BETWEEN 1 AND 50
                  AND p.barcode IS NULL
                """,
                Long.class
        );
        assertThat(missingProducts).isZero();
    }

    private void assertFinding(
            List<SeededScan> scans,
            long scanId,
            String restrictionCode,
            String ingredientName
    ) throws Exception {
        SeededScan scan = scans.stream()
                .filter(candidate -> candidate.id() == scanId)
                .findFirst()
                .orElseThrow();
        List<Finding> findings = objectMapper.readValue(scan.findingsJson(), FINDINGS_TYPE);
        assertThat(findings).anySatisfy(finding -> {
            assertThat(finding.restrictionCode()).isEqualTo(restrictionCode);
            assertThat(finding.ingredientName()).isEqualTo(ingredientName);
        });
    }

    private record SeededScan(
            long id,
            String verdict,
            String barcode,
            String aiExplanation,
            String findingsJson
    ) {
    }
}
