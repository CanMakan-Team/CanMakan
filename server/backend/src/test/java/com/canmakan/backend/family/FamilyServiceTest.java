package com.canmakan.backend.family;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.canmakan.backend.dietaryprofile.DietaryProfile;
import com.canmakan.backend.dietaryprofile.DietaryProfileRepository;
import com.canmakan.backend.family.exception.AlreadyInFamilyException;
import com.canmakan.backend.family.exception.FamilyNotFoundException;
import com.canmakan.backend.family.model.CreateFamilyRequest;
import com.canmakan.backend.family.model.Family;
import com.canmakan.backend.family.model.FamilyMeResponse;
import com.canmakan.backend.family.model.FamilyMember;
import com.canmakan.backend.family.repository.FamilyMemberRepository;
import com.canmakan.backend.family.repository.FamilyRepository;
import com.canmakan.backend.user.UserAccount;
import com.canmakan.backend.user.UserAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/** UC8: FamilyService tests
 * 
 * @author Amelia
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UC8: FamilyService tests")
class FamilyServiceTest {

    @Mock
    private UserAccountRepository userAccountRepository;
    @Mock
    private FamilyRepository familyRepository;
    @Mock
    private FamilyMemberRepository familyMemberRepository;
    @Mock
    private DietaryProfileRepository dietaryProfileRepository;

    private FamilyService familyService;

    // UC8 setup family service
    @BeforeEach
    void setUp() {
        familyService = new FamilyService(
                userAccountRepository,
                familyRepository,
                familyMemberRepository,
                dietaryProfileRepository
        );
    }

    // UC8 creates family, PRIMARY_ADMIN membership, and SELF profile
    @Test
    @DisplayName("creates family, PRIMARY_ADMIN membership, and SELF profile")
    void createFamilyHappyPath() {
        when(familyMemberRepository.existsByIdUserId(14L)).thenReturn(false);

        UserAccount user = new UserAccount();
        user.setId(14L);
        user.setEmail("person@example.com");
        when(userAccountRepository.findById(14L)).thenReturn(Optional.of(user));

        when(familyRepository.saveAndFlush(any(Family.class))).thenAnswer(invocation -> {
            Family family = invocation.getArgument(0);
            family.setId(50L);
            return family;
        });
        when(familyMemberRepository.saveAndFlush(any(FamilyMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(dietaryProfileRepository.saveAndFlush(any(DietaryProfile.class))).thenAnswer(invocation -> {
            DietaryProfile profile = invocation.getArgument(0);
            profile.setId(77L);
            return profile;
        });

        FamilyMeResponse response = familyService.createFamily(14L, new CreateFamilyRequest("  Wong Family  "));

        assertEquals(50L, response.familyId());
        assertEquals("Wong Family", response.familyName());
        assertEquals(FamilyMember.ROLE_PRIMARY_ADMIN, response.memberRole());
        assertEquals(77L, response.selfProfileId());
        assertEquals(14L, response.createdByUserId());

        ArgumentCaptor<FamilyMember> memberCaptor = ArgumentCaptor.forClass(FamilyMember.class);
        verify(familyMemberRepository).saveAndFlush(memberCaptor.capture());
        assertEquals(FamilyMember.ROLE_PRIMARY_ADMIN, memberCaptor.getValue().getMemberRole());
        assertEquals(50L, memberCaptor.getValue().getFamilyId());
        assertEquals(14L, memberCaptor.getValue().getUserId());

        ArgumentCaptor<DietaryProfile> profileCaptor = ArgumentCaptor.forClass(DietaryProfile.class);
        verify(dietaryProfileRepository).saveAndFlush(profileCaptor.capture());
        assertEquals("person", profileCaptor.getValue().getProfileName());
        assertEquals("SELF", profileCaptor.getValue().getRelationship());
        assertEquals(true, profileCaptor.getValue().isPrimary());
    }

    // UC8 rejects second create when user already has membership
    @Test
    @DisplayName("rejects second create when user already has membership")
    void createFamilyConflictWhenAlreadyMember() {
        when(familyMemberRepository.existsByIdUserId(4L)).thenReturn(true);

        assertThrows(
                AlreadyInFamilyException.class,
                () -> familyService.createFamily(4L, new CreateFamilyRequest("Another"))
        );
        verify(familyRepository, never()).saveAndFlush(any());
    }

    // UC8 maps unique constraint violation to already-in-family
    @Test
    @DisplayName("maps unique constraint violation to already-in-family")
    void createFamilyMapsUniqueViolation() {
        when(familyMemberRepository.existsByIdUserId(14L)).thenReturn(false);
        UserAccount user = new UserAccount();
        user.setId(14L);
        user.setEmail("person@example.com");
        when(userAccountRepository.findById(14L)).thenReturn(Optional.of(user));
        when(familyRepository.saveAndFlush(any(Family.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(
                AlreadyInFamilyException.class,
                () -> familyService.createFamily(14L, new CreateFamilyRequest("Race"))
        );
    }

    // UC8 gets family by user id
    @Test
    @DisplayName("getMyFamily returns context for membership")
    void getMyFamilyHappyPath() {
        FamilyMember membership = new FamilyMember(
                new FamilyMember.FamilyMemberId(1L, 4L),
                FamilyMember.ROLE_PRIMARY_ADMIN,
                null
        );
        when(familyMemberRepository.findMembershipByUserId(4L)).thenReturn(Optional.of(membership));

        Family family = new Family();
        family.setId(1L);
        family.setFamilyName("Tan Family");
        family.setCreatedByUserId(4L);
        when(familyRepository.findById(1L)).thenReturn(Optional.of(family));

        DietaryProfile profile = new DietaryProfile();
        profile.setId(1L);
        when(dietaryProfileRepository.findByLinkedUser_Id(4L)).thenReturn(Optional.of(profile));

        FamilyMeResponse response = familyService.getMyFamily(4L);
        assertEquals(1L, response.familyId());
        assertEquals("Tan Family", response.familyName());
        assertEquals(1L, response.selfProfileId());
    }

    // UC8 gets family by user id not found
    @Test
    @DisplayName("getMyFamily 404 when no membership")
    void getMyFamilyNotFound() {
        when(familyMemberRepository.findMembershipByUserId(99L)).thenReturn(Optional.empty());

        assertThrows(FamilyNotFoundException.class, () -> familyService.getMyFamily(99L));
    }

    // UC8 uses email local-part for profile name
    @Test
    @DisplayName("profileNameFromUser uses email local-part")
    void profileNameFromEmail() {
        UserAccount user = new UserAccount();
        user.setEmail("sarah.tan@example.com");
        assertEquals("sarah.tan", FamilyService.profileNameFromUser(user));
    }
}
