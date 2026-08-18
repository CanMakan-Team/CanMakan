package com.canmakan.backend.family.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
import com.canmakan.backend.dietaryprofile.repository.DietaryProfileRepository;
import com.canmakan.backend.family.dto.FamilyScanHistoryDto;
import com.canmakan.backend.family.exception.FamilyForbiddenException;
import com.canmakan.backend.family.model.FamilyMember;
import com.canmakan.backend.product.model.ScanProduct;
import com.canmakan.backend.product.scan.Scan;
import com.canmakan.backend.product.scan.ScanRepository;
import com.canmakan.backend.user.model.UserAccount;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("FamilyScanHistoryService")
class FamilyScanHistoryServiceTest {

    private static final long USER_ID = 1L;
    private static final long FAMILY_ID = 100L;

    @Mock
    private DietaryProfileRepository dietaryProfileRepository;
    @Mock
    private ScanRepository scanRepository;
    @Mock
    private FamilyAuthorizationService familyAuthorization;

    private FamilyScanHistoryService service;

    @BeforeEach
    void setUp() {
        service = new FamilyScanHistoryService(
                dietaryProfileRepository, scanRepository, familyAuthorization);
    }

    @Test
    @DisplayName("listFamilyScans throws when the caller is not the family's primary admin")
    void listFamilyScansThrowsWhenNotPrimaryAdmin() {
        when(familyAuthorization.requirePrimaryAdmin(USER_ID))
                .thenThrow(new FamilyForbiddenException("Only the primary admin can do this."));

        assertThrows(FamilyForbiddenException.class, () -> service.listFamilyScans(USER_ID));
    }

    @Test
    @DisplayName("listFamilyScans returns an empty list when the family has no profiles")
    void listFamilyScansReturnsEmptyWhenNoProfiles() {
        when(familyAuthorization.requirePrimaryAdmin(USER_ID)).thenReturn(membership());
        when(dietaryProfileRepository.findAllProfilesByFamilyId(FAMILY_ID)).thenReturn(List.of());

        List<FamilyScanHistoryDto> rows = service.listFamilyScans(USER_ID);

        assertTrue(rows.isEmpty());
        verify(scanRepository, never()).findByProfileIdInWithProductOrderByScannedAtDesc(anyCollection());
    }

    @Test
    @DisplayName("listFamilyScans maps a scan whose product was found in the catalog")
    void listFamilyScansMapsScanWithProduct() {
        DietaryProfile profile = profile(10L, "Dad", null);
        ScanProduct product = product("012345", "Peanut Bar", "Acme");
        Scan scan = scan(1L, 10L, "012345", product, "safe", LocalDateTime.of(2026, 1, 2, 3, 4, 5));
        when(familyAuthorization.requirePrimaryAdmin(USER_ID)).thenReturn(membership());
        when(dietaryProfileRepository.findAllProfilesByFamilyId(FAMILY_ID)).thenReturn(List.of(profile));
        when(scanRepository.findByProfileIdInWithProductOrderByScannedAtDesc(anyCollection()))
                .thenReturn(List.of(scan));

        List<FamilyScanHistoryDto> rows = service.listFamilyScans(USER_ID);

        assertEquals(1, rows.size());
        FamilyScanHistoryDto row = rows.get(0);
        assertEquals(1L, row.scanId());
        assertEquals("Peanut Bar", row.product());
        assertEquals("Acme", row.brand());
        assertEquals(10L, row.memberId());
        assertEquals("Dad", row.evaluatedProfile());
        assertEquals("SAFE", row.verdict());
        assertEquals("COMPLETE", row.dataCompleteness());
        assertEquals("2026-01-02T03:04:05", row.scannedAt());
    }

    @Test
    @DisplayName("listFamilyScans falls back to the barcode and PRODUCT_NOT_FOUND when the catalog has no match")
    void listFamilyScansMapsScanWithoutProduct() {
        DietaryProfile profile = profile(10L, "Dad", null);
        Scan scan = scan(2L, 10L, "099999", null, null, null);
        when(familyAuthorization.requirePrimaryAdmin(USER_ID)).thenReturn(membership());
        when(dietaryProfileRepository.findAllProfilesByFamilyId(FAMILY_ID)).thenReturn(List.of(profile));
        when(scanRepository.findByProfileIdInWithProductOrderByScannedAtDesc(anyCollection()))
                .thenReturn(List.of(scan));

        List<FamilyScanHistoryDto> rows = service.listFamilyScans(USER_ID);

        FamilyScanHistoryDto row = rows.get(0);
        assertEquals("099999", row.product());
        assertEquals("", row.brand());
        assertEquals("PRODUCT_NOT_FOUND", row.dataCompleteness());
        assertEquals("WARNING", row.verdict());
    }

    @Test
    @DisplayName("listFamilyScans reports an unknown product when neither the product nor the barcode is available")
    void listFamilyScansMapsScanWithoutProductOrBarcode() {
        DietaryProfile profile = profile(10L, "Dad", null);
        Scan scan = scan(3L, 10L, null, null, "WARNING", null);
        when(familyAuthorization.requirePrimaryAdmin(USER_ID)).thenReturn(membership());
        when(dietaryProfileRepository.findAllProfilesByFamilyId(FAMILY_ID)).thenReturn(List.of(profile));
        when(scanRepository.findByProfileIdInWithProductOrderByScannedAtDesc(anyCollection()))
                .thenReturn(List.of(scan));

        List<FamilyScanHistoryDto> rows = service.listFamilyScans(USER_ID);

        assertEquals("Unknown product", rows.get(0).product());
    }

    @Test
    @DisplayName("listFamilyScans labels a profile as unknown and a member as 0 when the scan's profile no longer exists")
    void listFamilyScansHandlesMissingProfile() {
        // The family has one known profile (10L), but the returned scan references a
        // profile id (999L) that is not among them, e.g. deleted after the scan was recorded.
        DietaryProfile profile = profile(10L, "Dad", null);
        Scan scan = scan(4L, 999L, "012345", null, "unsafe", null);
        when(familyAuthorization.requirePrimaryAdmin(USER_ID)).thenReturn(membership());
        when(dietaryProfileRepository.findAllProfilesByFamilyId(FAMILY_ID)).thenReturn(List.of(profile));
        when(scanRepository.findByProfileIdInWithProductOrderByScannedAtDesc(anyCollection()))
                .thenReturn(List.of(scan));

        List<FamilyScanHistoryDto> rows = service.listFamilyScans(USER_ID);

        FamilyScanHistoryDto row = rows.get(0);
        assertEquals("Unknown profile", row.evaluatedProfile());
        assertEquals(0L, row.memberId());
        assertEquals("UNSAFE", row.verdict());
    }

    @Test
    @DisplayName("listFamilyScans resolves the member id from the profile's linked user when present")
    void listFamilyScansUsesLinkedUserIdAsMemberId() {
        UserAccount linkedUser = new UserAccount();
        linkedUser.setId(55L);
        DietaryProfile profile = profile(10L, "Dad", linkedUser);
        Scan scan = scan(5L, 10L, "012345", null, "AVOID", null);
        when(familyAuthorization.requirePrimaryAdmin(USER_ID)).thenReturn(membership());
        when(dietaryProfileRepository.findAllProfilesByFamilyId(FAMILY_ID)).thenReturn(List.of(profile));
        when(scanRepository.findByProfileIdInWithProductOrderByScannedAtDesc(anyCollection()))
                .thenReturn(List.of(scan));

        List<FamilyScanHistoryDto> rows = service.listFamilyScans(USER_ID);

        FamilyScanHistoryDto row = rows.get(0);
        assertEquals(55L, row.memberId());
        // Legacy "AVOID" verdict from older mocks/clients normalizes to UNSAFE.
        assertEquals("UNSAFE", row.verdict());
    }

    @Test
    @DisplayName("listFamilyScans normalizes an unrecognized verdict to WARNING")
    void listFamilyScansNormalizesUnknownVerdictToWarning() {
        DietaryProfile profile = profile(10L, "Dad", null);
        Scan scan = scan(6L, 10L, "012345", null, "garbage", null);
        when(familyAuthorization.requirePrimaryAdmin(USER_ID)).thenReturn(membership());
        when(dietaryProfileRepository.findAllProfilesByFamilyId(FAMILY_ID)).thenReturn(List.of(profile));
        when(scanRepository.findByProfileIdInWithProductOrderByScannedAtDesc(anyCollection()))
                .thenReturn(List.of(scan));

        List<FamilyScanHistoryDto> rows = service.listFamilyScans(USER_ID);

        assertEquals("WARNING", rows.get(0).verdict());
    }

    @Test
    @DisplayName("listFamilyScans treats a blank (non-null) verdict the same as a missing one")
    void listFamilyScansTreatsBlankVerdictAsWarning() {
        DietaryProfile profile = profile(10L, "Dad", null);
        Scan scan = scan(8L, 10L, "012345", null, "   ", null);
        when(familyAuthorization.requirePrimaryAdmin(USER_ID)).thenReturn(membership());
        when(dietaryProfileRepository.findAllProfilesByFamilyId(FAMILY_ID)).thenReturn(List.of(profile));
        when(scanRepository.findByProfileIdInWithProductOrderByScannedAtDesc(anyCollection()))
                .thenReturn(List.of(scan));

        List<FamilyScanHistoryDto> rows = service.listFamilyScans(USER_ID);

        assertEquals("WARNING", rows.get(0).verdict());
    }

    @Test
    @DisplayName("listFamilyScans defaults the brand to empty when the matched product has none")
    void listFamilyScansDefaultsMissingBrandToEmptyString() {
        DietaryProfile profile = profile(10L, "Dad", null);
        ScanProduct product = product("012345", "Peanut Bar", null);
        Scan scan = scan(9L, 10L, "012345", product, "SAFE", null);
        when(familyAuthorization.requirePrimaryAdmin(USER_ID)).thenReturn(membership());
        when(dietaryProfileRepository.findAllProfilesByFamilyId(FAMILY_ID)).thenReturn(List.of(profile));
        when(scanRepository.findByProfileIdInWithProductOrderByScannedAtDesc(anyCollection()))
                .thenReturn(List.of(scan));

        List<FamilyScanHistoryDto> rows = service.listFamilyScans(USER_ID);

        assertEquals("", rows.get(0).brand());
    }

    @Test
    @DisplayName("listFamilyScans falls back to the barcode as the product name when the catalog entry has no name")
    void listFamilyScansFallsBackToBarcodeWhenProductNameMissing() {
        DietaryProfile profile = profile(10L, "Dad", null);
        ScanProduct product = product("012345", null, "Acme");
        Scan scan = scan(10L, 10L, "012345", product, "SAFE", null);
        when(familyAuthorization.requirePrimaryAdmin(USER_ID)).thenReturn(membership());
        when(dietaryProfileRepository.findAllProfilesByFamilyId(FAMILY_ID)).thenReturn(List.of(profile));
        when(scanRepository.findByProfileIdInWithProductOrderByScannedAtDesc(anyCollection()))
                .thenReturn(List.of(scan));

        List<FamilyScanHistoryDto> rows = service.listFamilyScans(USER_ID);

        assertEquals("012345", rows.get(0).product());
    }

    @Test
    @DisplayName("listFamilyScans labels the profile as unknown when the matched profile has no name")
    void listFamilyScansDefaultsMissingProfileNameToUnknown() {
        DietaryProfile profile = profile(10L, null, null);
        Scan scan = scan(11L, 10L, "012345", null, "SAFE", null);
        when(familyAuthorization.requirePrimaryAdmin(USER_ID)).thenReturn(membership());
        when(dietaryProfileRepository.findAllProfilesByFamilyId(FAMILY_ID)).thenReturn(List.of(profile));
        when(scanRepository.findByProfileIdInWithProductOrderByScannedAtDesc(anyCollection()))
                .thenReturn(List.of(scan));

        List<FamilyScanHistoryDto> rows = service.listFamilyScans(USER_ID);

        assertEquals("Unknown profile", rows.get(0).evaluatedProfile());
    }

    @Test
    @DisplayName("listFamilyScans falls back to the profile id as the member id when the linked user has no id")
    void listFamilyScansFallsBackToProfileIdWhenLinkedUserIdMissing() {
        UserAccount linkedUser = new UserAccount();
        DietaryProfile profile = profile(10L, "Dad", linkedUser);
        Scan scan = scan(12L, 10L, "012345", null, "SAFE", null);
        when(familyAuthorization.requirePrimaryAdmin(USER_ID)).thenReturn(membership());
        when(dietaryProfileRepository.findAllProfilesByFamilyId(FAMILY_ID)).thenReturn(List.of(profile));
        when(scanRepository.findByProfileIdInWithProductOrderByScannedAtDesc(anyCollection()))
                .thenReturn(List.of(scan));

        List<FamilyScanHistoryDto> rows = service.listFamilyScans(USER_ID);

        assertEquals(10L, rows.get(0).memberId());
    }

    @Test
    @DisplayName("listFamilyScans defaults the member id to 0 when the matched profile has no id")
    void listFamilyScansDefaultsMemberIdToZeroWhenProfileIdMissing() {
        // The profile's own id is null, so the scan referencing it (via the same null
        // profileId) still resolves to this profile through the profilesById lookup.
        DietaryProfile profile = profile(null, "Dad", null);
        Scan scan = scan(13L, null, "012345", null, "SAFE", null);
        when(familyAuthorization.requirePrimaryAdmin(USER_ID)).thenReturn(membership());
        when(dietaryProfileRepository.findAllProfilesByFamilyId(FAMILY_ID)).thenReturn(List.of(profile));
        when(scanRepository.findByProfileIdInWithProductOrderByScannedAtDesc(anyCollection()))
                .thenReturn(List.of(scan));

        List<FamilyScanHistoryDto> rows = service.listFamilyScans(USER_ID);

        assertEquals(0L, rows.get(0).memberId());
    }

    @Test
    @DisplayName("listFamilyScans falls back to an empty explanation when the scan has none")
    void listFamilyScansDefaultsMissingExplanationToEmptyString() {
        DietaryProfile profile = profile(10L, "Dad", null);
        Scan scan = scan(7L, 10L, "012345", null, "SAFE", null);
        scan.setAiExplanation(null);
        when(familyAuthorization.requirePrimaryAdmin(USER_ID)).thenReturn(membership());
        when(dietaryProfileRepository.findAllProfilesByFamilyId(FAMILY_ID)).thenReturn(List.of(profile));
        when(scanRepository.findByProfileIdInWithProductOrderByScannedAtDesc(anyCollection()))
                .thenReturn(List.of(scan));

        List<FamilyScanHistoryDto> rows = service.listFamilyScans(USER_ID);

        assertEquals("", rows.get(0).explanation());
    }

    @Test
    @DisplayName("listFamilyScans defaults the scan id to 0 when it is null")
    void listFamilyScansDefaultsMissingScanIdToZero() {
        DietaryProfile profile = profile(10L, "Dad", null);
        Scan scan = scan(null, 10L, "012345", null, "SAFE", null);
        when(familyAuthorization.requirePrimaryAdmin(USER_ID)).thenReturn(membership());
        when(dietaryProfileRepository.findAllProfilesByFamilyId(FAMILY_ID)).thenReturn(List.of(profile));
        when(scanRepository.findByProfileIdInWithProductOrderByScannedAtDesc(anyCollection()))
                .thenReturn(List.of(scan));

        List<FamilyScanHistoryDto> rows = service.listFamilyScans(USER_ID);

        assertEquals(0L, rows.get(0).scanId());
    }

    private static FamilyMember membership() {
        FamilyMember member = new FamilyMember();
        member.setId(new FamilyMember.FamilyMemberId(FAMILY_ID, USER_ID));
        member.setMemberRole(FamilyMember.ROLE_PRIMARY_ADMIN);
        member.setIsActive(true);
        return member;
    }

    private static DietaryProfile profile(Long id, String name, UserAccount linkedUser) {
        DietaryProfile profile = new DietaryProfile();
        profile.setId(id);
        profile.setProfileName(name);
        profile.setLinkedUser(linkedUser);
        profile.setActive(true);
        return profile;
    }

    private static ScanProduct product(String barcode, String name, String brand) {
        ScanProduct product = new ScanProduct();
        product.setBarcode(barcode);
        product.setProductName(name);
        product.setBrand(brand);
        return product;
    }

    private static Scan scan(
            Long id, Long profileId, String barcode, ScanProduct product, String verdict, LocalDateTime scannedAt) {
        Scan scan = new Scan();
        scan.setId(id);
        scan.setProfileId(profileId);
        scan.setBarcode(barcode);
        scan.setProduct(product);
        scan.setVerdict(verdict);
        scan.setAiExplanation("Looks fine.");
        scan.setScannedAt(scannedAt);
        return scan;
    }
}
