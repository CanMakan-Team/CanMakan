package com.canmakan.backend.product.history;

import static org.assertj.core.api.Assertions.assertThat;

import com.canmakan.backend.product.scan.Scan;
import com.canmakan.backend.product.scan.ScanRepository;
import com.canmakan.backend.product.scan.ScanService;
import com.canmakan.backend.product.verdict.Finding;
import com.canmakan.backend.product.verdict.SafetyVerdict;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository-layer test for the "View Scan History" feature (the History icon
 * in the bottom nav bar), which is served by ScanController /
 * ScanHistoryService on top of {@link ScanRepository}, specifically
 * {@link ScanRepository#findByProfileIdWithProductOrderByScannedAtDesc}.
 * Named for the feature it covers rather than {@code ScanRepositoryTest}, and
 * placed in its own {@code product.history} package (mirroring the mobile
 * app's {@code features/product/history} layout) to stay distinct from
 * {@code ScanControllerTest}/{@code ScanServiceTest} (UC2/UC3, in
 * {@code product.scan}), which test the barcode-scanner write side, not
 * history.
 *
 * <p>Fixtures are the seeded rows from {@code 06_scans_and_ai_logs.sql} (scans)
 * and {@code 01_products.sql} (products), rather than synthetic data, so these
 * tests exercise the repository against the same dataset the app actually
 * ships with. {@code spring.sql.init.mode} reloads those files on every
 * context start and each test below runs inside a transaction that is rolled
 * back afterwards. The trade-off: if the seed data in those files changes,
 * the fixture ids/values referenced here need to be updated to match.
 *
 * <p>Boots the full application context and runs against the app's real
 * configured MySQL datasource (same constraint as {@code BackendApplicationTests}
 * and {@code DietaryProfileRepositoryTest}): the project has no embedded-database
 * test dependency, and Spring Boot 4.1's spring-boot-test-autoconfigure no
 * longer ships the {@code @DataJpaTest} slice, so a full {@code @SpringBootTest}
 * is used instead.
 */
@SpringBootTest
@Transactional
@DisplayName("View Scan History: ScanRepository")
class ScanHistoryRepositoryTest {

    // Household profiles seeded by 05_household_dietary_data.sql.
    private static final long PROFILE_SARAH_TAN = 1L;
    private static final long PROFILE_MICHAEL_TAN = 2L;
    private static final long NONEXISTENT_PROFILE = 9_999L;

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private ScanRepository scanRepository;

    @Autowired
    private ScanService scanService;

    @Test
    @DisplayName("returns Sarah Tan's 5 seeded scans newest first, with the matching product joined")
    void returnsSarahTansScansNewestFirstWithProductJoined() {
        List<Scan> scans =
            scanRepository.findByProfileIdWithProductOrderByScannedAtDesc(PROFILE_SARAH_TAN);

        assertThat(scans).extracting(Scan::getId)
            .containsExactly(5L, 4L, 3L, 2L, 1L);
        assertThat(scans).extracting(Scan::getVerdict)
            .containsExactly("UNSAFE", "SAFE", "WARNING", "UNSAFE", "SAFE");

        Scan oldest = scans.get(scans.size() - 1);
        assertThat(oldest.getId()).isEqualTo(1L);
        assertThat(oldest.getBarcode()).isEqualTo("95500539");
        assertThat(oldest.getProduct()).isNotNull();
        assertThat(oldest.getProduct().getProductName()).isEqualTo("Sardines in tomato sauce");
        assertThat(oldest.getProduct().getBrand()).isEqualTo("Ayam Brand");

        Scan secondOldest = scans.get(scans.size() - 2);
        assertThat(secondOldest.getId()).isEqualTo(2L);
        assertThat(secondOldest.getProduct().getProductName()).isEqualTo("Oatmeal Squares Original");
        assertThat(secondOldest.getProduct().getBrand()).isEqualTo("Quaker");
    }

    @Test
    @DisplayName("returns Michael Tan's 5 seeded scans newest first, with the matching product joined")
    void returnsMichaelTansScansNewestFirstWithProductJoined() {
        List<Scan> scans =
            scanRepository.findByProfileIdWithProductOrderByScannedAtDesc(PROFILE_MICHAEL_TAN);

        assertThat(scans).extracting(Scan::getId)
            .containsExactly(10L, 9L, 8L, 7L, 6L);

        Scan newest = scans.get(0);
        assertThat(newest.getBarcode()).isEqualTo("8888077103549");
        assertThat(newest.getProduct()).isNotNull();
        assertThat(newest.getProduct().getProductName()).isEqualTo("Plain Crackers");
        assertThat(newest.getProduct().getBrand()).isEqualTo("Meiji");

        Scan oldest = scans.get(scans.size() - 1);
        assertThat(oldest.getBarcode()).isEqualTo("9557305001368");
        assertThat(oldest.getProduct().getProductName()).isEqualTo("Logan Red Dates drink");
        assertThat(oldest.getProduct().getBrand()).isEqualTo("Marigold");
    }

    @Test
    @DisplayName("returns an empty list for a profile id with no scans")
    void returnsEmptyListForProfileWithNoScans() {
        List<Scan> scans =
            scanRepository.findByProfileIdWithProductOrderByScannedAtDesc(NONEXISTENT_PROFILE);

        assertThat(scans).isEmpty();
    }

    @Test
    @DisplayName("an OCR-only scan with no barcode still returns, with product left null")
    void scanWithNullBarcodeReturnsWithNullProduct() {
        // None of the seeded scans exercise this path (every seeded barcode has a
        // matching product row), but Scan.product is documented as nullable for
        // OCR-only scans (no barcode at all) — the left join must not silently
        // drop the row in that case. Note: scans.barcode has a real FK to
        // products(barcode), so an arbitrary non-matching barcode string would
        // violate that constraint; NULL is the only way a scan can lack a product.
        Scan ocrOnlyScan = new Scan();
        ocrOnlyScan.setUserId(4L);
        ocrOnlyScan.setProfileId(PROFILE_SARAH_TAN);
        ocrOnlyScan.setBarcode(null);
        ocrOnlyScan.setVerdict("WARNING");
        ocrOnlyScan.setScannedAt(LocalDateTime.now());
        entityManager.persist(ocrOnlyScan);
        entityManager.flush();
        entityManager.clear();

        List<Scan> scans =
            scanRepository.findByProfileIdWithProductOrderByScannedAtDesc(PROFILE_SARAH_TAN);

        assertThat(scans).hasSize(6);
        Scan found = scans.stream()
            .filter(s -> s.getId().equals(ocrOnlyScan.getId()))
            .findFirst()
            .orElseThrow();
        assertThat(found.getBarcode()).isNull();
        assertThat(found.getProduct()).isNull();
    }

    @Test
    @DisplayName("UC4-AC1: a successful assess persists product, verdict, timestamp, and profile for the scan")
    void recordingAssessPersistsProductVerdictTimestampAndProfile() {
        // Exercises ScanService.record directly — the method AssessmentOrchestrator
        // calls after producing a verdict — rather than the raw repository, since
        // this criterion describes what a completed assess must persist. Nutella
        // (9300698500181) is a seeded products row, so ensureProductRow finds it
        // already present and skips inserting a stub.
        String barcode = "9300698500181";
        SafetyVerdict verdict = SafetyVerdict.warning(
            "Contains milk, which the profile prefers to avoid.",
            List.of(new Finding("DAIRY", "Skimmed milk powder", "Contains milk"))
        );

        Scan saved = scanService.record(3L, PROFILE_MICHAEL_TAN, barcode, verdict, "Nutella");
        entityManager.flush();
        entityManager.clear();

        List<Scan> scans =
            scanRepository.findByProfileIdWithProductOrderByScannedAtDesc(PROFILE_MICHAEL_TAN);
        Scan persisted = scans.stream()
            .filter(s -> s.getId().equals(saved.getId()))
            .findFirst()
            .orElseThrow();

        assertThat(persisted.getProfileId()).isEqualTo(PROFILE_MICHAEL_TAN);
        assertThat(persisted.getVerdict()).isEqualTo("WARNING");
        assertThat(persisted.getScannedAt()).isNotNull();
        assertThat(persisted.getProduct()).isNotNull();
        assertThat(persisted.getProduct().getProductName()).isEqualTo("Nutella");
    }
}
