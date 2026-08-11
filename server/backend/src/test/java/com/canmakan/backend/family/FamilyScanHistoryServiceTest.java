package com.canmakan.backend.family;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
import com.canmakan.backend.dietaryprofile.model.DietaryRestriction;
import com.canmakan.backend.dietaryprofile.repository.DietaryProfileRepository;
import com.canmakan.backend.dietaryprofile.repository.DietaryRestrictionRepository;
import com.canmakan.backend.family.dto.FamilyScanRecordResponse;
import com.canmakan.backend.family.exception.FamilyNotFoundException;
import com.canmakan.backend.family.model.FamilyMember;
import com.canmakan.backend.family.repository.FamilyMemberRepository;
import com.canmakan.backend.product.model.ScanProduct;
import com.canmakan.backend.product.scan.Scan;
import com.canmakan.backend.product.scan.ScanRepository;
import com.canmakan.backend.user.UserAccount;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** FamilyScanHistoryService tests: GET /api/families/me/scans (family-wide scan history).
 *
 * @author Amelia
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FamilyScanHistoryService")
class FamilyScanHistoryServiceTest {

    @Mock
    private FamilyMemberRepository familyMemberRepository;
    @Mock
    private DietaryProfileRepository dietaryProfileRepository;
    @Mock
    private DietaryRestrictionRepository dietaryRestrictionRepository;
    @Mock
    private ScanRepository scanRepository;

    private FamilyScanHistoryService service;

    @BeforeEach
    void setUp() {
        service = new FamilyScanHistoryService(
            familyMemberRepository,
            dietaryProfileRepository,
            dietaryRestrictionRepository,
            scanRepository,
            new ObjectMapper());
    }

    @Test
    @DisplayName("caller with no family membership throws FamilyNotFoundException")
    void noMembershipThrows() {
        when(familyMemberRepository.findMembershipByUserId(10L)).thenReturn(Optional.empty());

        assertThrows(FamilyNotFoundException.class, () -> service.getFamilyScanHistory(10L));
        verify(scanRepository, never()).findByProfileIdInWithProductOrderByScannedAtDesc(anyList());
    }

    @Test
    @DisplayName("family with no profiles returns an empty list without querying scans")
    void noProfilesReturnsEmpty() {
        when(familyMemberRepository.findMembershipByUserId(10L))
            .thenReturn(Optional.of(membership(1L, 10L)));
        when(dietaryProfileRepository.findProfilesByFamilyId(1L)).thenReturn(List.of());

        List<FamilyScanRecordResponse> result = service.getFamilyScanHistory(10L);

        assertTrue(result.isEmpty());
        verify(scanRepository, never()).findByProfileIdInWithProductOrderByScannedAtDesc(anyList());
    }

    @Test
    @DisplayName("registered member's scan maps UNSAFE to AVOID, memberId to their user id")
    void registeredMemberScanMapsVerdictAndMemberId() {
        UserAccount user = new UserAccount();
        user.setId(20L);

        DietaryProfile profile = new DietaryProfile();
        profile.setId(77L);
        profile.setProfileName("Noah");
        profile.setLinkedUser(user);

        ScanProduct product = new ScanProduct();
        product.setProductName("Crunchy Peanut Bar");
        product.setBrand("Good Day");

        Scan scan = new Scan();
        scan.setId(501L);
        scan.setProfileId(77L);
        scan.setProduct(product);
        scan.setVerdict("UNSAFE");
        scan.setAiExplanation("Matched peanut to this profile's peanut allergy.");
        scan.setFindingsJson(
            "[{\"restrictionCode\":\"PEANUT_ALLERGY\",\"ingredientName\":\"Peanut pieces\",\"reason\":\"r\"}]");
        scan.setScannedAt(LocalDateTime.of(2026, 7, 28, 18, 42, 0));

        DietaryRestriction restriction = new DietaryRestriction();
        restriction.setCode("PEANUT_ALLERGY");
        restriction.setDisplayName("Peanut Allergy");

        when(familyMemberRepository.findMembershipByUserId(10L))
            .thenReturn(Optional.of(membership(1L, 10L)));
        when(dietaryProfileRepository.findProfilesByFamilyId(1L)).thenReturn(List.of(profile));
        when(scanRepository.findByProfileIdInWithProductOrderByScannedAtDesc(eq(List.of(77L))))
            .thenReturn(List.of(scan));
        when(dietaryRestrictionRepository.findByCodeIgnoreCase("PEANUT_ALLERGY"))
            .thenReturn(Optional.of(restriction));

        List<FamilyScanRecordResponse> result = service.getFamilyScanHistory(10L);

        assertEquals(1, result.size());
        FamilyScanRecordResponse row = result.get(0);
        assertEquals(501L, row.scanId());
        assertEquals("Crunchy Peanut Bar", row.product());
        assertEquals(20L, row.memberId());
        assertEquals("Noah", row.evaluatedProfile());
        assertEquals("AVOID", row.verdict());
        assertEquals("Peanut pieces", row.detectedIngredient());
        assertEquals("PEANUT_ALLERGY", row.resolvedIngredient());
        assertEquals("Peanut Allergy", row.matchedRestriction());
        assertEquals("COMPLETE", row.dataCompleteness());
    }

    @Test
    @DisplayName("dependant profile scan uses profile id as memberId and PRODUCT_NOT_FOUND when unmatched")
    void dependantScanWithNoProductIsProductNotFound() {
        DietaryProfile dependant = new DietaryProfile();
        dependant.setId(88L);
        dependant.setProfileName("Marcus");
        dependant.setLinkedUser(null);

        Scan scan = new Scan();
        scan.setId(504L);
        scan.setProfileId(88L);
        scan.setProduct(null);
        scan.setVerdict("SAFE");
        scan.setFindingsJson(null);
        scan.setScannedAt(LocalDateTime.of(2026, 7, 23, 20, 19, 0));

        when(familyMemberRepository.findMembershipByUserId(10L))
            .thenReturn(Optional.of(membership(1L, 10L)));
        when(dietaryProfileRepository.findProfilesByFamilyId(1L)).thenReturn(List.of(dependant));
        when(scanRepository.findByProfileIdInWithProductOrderByScannedAtDesc(eq(List.of(88L))))
            .thenReturn(List.of(scan));

        List<FamilyScanRecordResponse> result = service.getFamilyScanHistory(10L);

        assertEquals(1, result.size());
        FamilyScanRecordResponse row = result.get(0);
        assertEquals(88L, row.memberId());
        assertEquals("Unknown product", row.product());
        assertEquals("PRODUCT_NOT_FOUND", row.dataCompleteness());
        assertEquals("None flagged", row.detectedIngredient());
        assertEquals("No supplied match", row.matchedRestriction());
    }

    private static FamilyMember membership(long familyId, long userId) {
        FamilyMember member = new FamilyMember();
        member.setId(new FamilyMember.FamilyMemberId(familyId, userId));
        member.setMemberRole(FamilyMember.ROLE_MEMBER);
        return member;
    }
}
