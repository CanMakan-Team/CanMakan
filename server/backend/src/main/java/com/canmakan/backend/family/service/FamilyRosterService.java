package com.canmakan.backend.family.service;

import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
import com.canmakan.backend.dietaryprofile.repository.DietaryProfileRepository;
import com.canmakan.backend.family.service.FamilyDisplayUtil.RestrictionCodeSplit;
import com.canmakan.backend.family.dto.FamilyMeRestrictionDetail;
import com.canmakan.backend.family.dto.FamilyMeRestrictionSum;
import com.canmakan.backend.family.dto.FamilyMemberRosterDto;
import com.canmakan.backend.family.dto.FamilyRestrictionSumRes;
import com.canmakan.backend.family.model.FamilyMember;
import com.canmakan.backend.family.repository.FamilyMemberRepository;
import com.canmakan.backend.user.model.UserAccount;
import com.canmakan.backend.user.repository.UserAccountRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FamilyRosterService {

    private final UserAccountRepository userAccountRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final DietaryProfileRepository dietaryProfileRepository;
    private final FamilyAuthorizationService familyAuthorization;

    @Transactional(readOnly = true)
    public FamilyRestrictionSumRes getFamilyRestrictionSummary(Long currentUserId) {
        FamilyMember membership = familyAuthorization.requireMembership(currentUserId);
        Long familyId = membership.getFamilyId();
        List<FamilyMember> members = familyMemberRepository.findActiveMembersByFamilyId(familyId);
        Map<Long, DietaryProfile> profilesByUserId = linkedProfilesByUserId(memberUserIds(members));

        List<FamilyMeRestrictionSum> rows = new ArrayList<>();
        for (FamilyMember member : members) {
            DietaryProfile profile = profilesByUserId.get(member.getUserId());
            Optional<DietaryProfile> dietaryProfileOpt = Optional.ofNullable(profile);
            String name = dietaryProfileOpt
                .map(dietaryProfile -> dietaryProfile.getProfileName())
                .orElse("Unknown Member");
            Long profileId = dietaryProfileOpt
                .map(dietaryProfile -> dietaryProfile.getId())
                .orElse(null);
            List<FamilyMeRestrictionDetail> restrictionDetails =
                FamilyDisplayUtil.mapRestrictions(dietaryProfileOpt);
            rows.add(new FamilyMeRestrictionSum(
                member.getUserId(),
                profileId,
                name,
                member.getIsActive(),
                restrictionDetails
            ));
        }

        for (DietaryProfile dependant :
                dietaryProfileRepository.findActiveDependantsByFamilyIdWithRestrictions(familyId)) {
            rows.add(new FamilyMeRestrictionSum(
                0L,
                dependant.getId(),
                dependant.getProfileName(),
                true,
                FamilyDisplayUtil.mapRestrictions(Optional.of(dependant))
            ));
        }

        return new FamilyRestrictionSumRes(rows);
    }

    /**
     * Lists linked members and dependant profiles for the caller's family.
     * Registered rows use {@code memberId = userId}; dependants use {@code memberId = profileId}.
     * Prefer {@code profileId} for UC12 manage APIs.
     */
    @Transactional(readOnly = true)
    public List<FamilyMemberRosterDto> listFamilyMembers(long currentUserId) {
        FamilyMember membership = familyAuthorization.requireMembership(currentUserId);
        Long familyId = membership.getFamilyId();
        List<FamilyMember> members = familyMemberRepository.findActiveMembersByFamilyId(familyId);
        List<Long> userIds = memberUserIds(members);
        Map<Long, DietaryProfile> profilesByUserId = linkedProfilesByUserId(userIds);
        Map<Long, UserAccount> accountsById = userAccountsById(userIds);

        List<FamilyMemberRosterDto> rows = new ArrayList<>();
        for (FamilyMember member : members) {
            DietaryProfile dietaryProfile = profilesByUserId.get(member.getUserId());
            if (dietaryProfile == null) {
                continue;
            }
            String name = dietaryProfile.getProfileName() == null
                || dietaryProfile.getProfileName().isBlank()
                ? "Unknown Member"
                : dietaryProfile.getProfileName();
            String relationship = dietaryProfile.getRelationship() == null
                || dietaryProfile.getRelationship().isBlank()
                ? "OTHER"
                : dietaryProfile.getRelationship();
            RestrictionCodeSplit codes =
                FamilyDisplayUtil.splitRestrictionCodes(Optional.of(dietaryProfile));
            UserAccount account = accountsById.get(member.getUserId());
            String masked = account == null ? null : FamilyDisplayUtil.maskEmail(account.getEmail());
            rows.add(new FamilyMemberRosterDto(
                member.getUserId(),
                dietaryProfile.getId(),
                member.getUserId(),
                name,
                relationship,
                codes.commonRequirements(),
                codes.restrictions(),
                FamilyMemberRosterDto.SOURCE_REGISTERED,
                masked,
                member.getMemberRole(),
                dietaryProfile.isActive()
            ));
        }

        for (DietaryProfile dependant :
                dietaryProfileRepository.findAllDependantsByFamilyIdWithRestrictions(familyId)) {
            String relationship = dependant.getRelationship() == null
                || dependant.getRelationship().isBlank()
                ? "DEPENDANT"
                : dependant.getRelationship();
            RestrictionCodeSplit codes =
                FamilyDisplayUtil.splitRestrictionCodes(Optional.of(dependant));
            rows.add(new FamilyMemberRosterDto(
                dependant.getId(),
                dependant.getId(),
                null,
                dependant.getProfileName(),
                relationship,
                codes.commonRequirements(),
                codes.restrictions(),
                FamilyMemberRosterDto.SOURCE_DEPENDANT,
                null,
                null,
                dependant.isActive()
            ));
        }

        return rows;
    }

    private Map<Long, DietaryProfile> linkedProfilesByUserId(Collection<Long> userIds) {
        Map<Long, DietaryProfile> profilesByUserId = new HashMap<>();
        if (userIds.isEmpty()) {
            return profilesByUserId;
        }
        for (DietaryProfile profile :
                dietaryProfileRepository.findByLinkedUserIdInWithRestrictions(userIds)) {
            if (profile.getLinkedUser() != null && profile.getLinkedUser().getId() != null) {
                profilesByUserId.put(profile.getLinkedUser().getId(), profile);
            }
        }
        return profilesByUserId;
    }

    private Map<Long, UserAccount> userAccountsById(Collection<Long> userIds) {
        Map<Long, UserAccount> accountsById = new HashMap<>();
        if (userIds.isEmpty()) {
            return accountsById;
        }
        for (UserAccount account : userAccountRepository.findAllById(userIds)) {
            accountsById.put(account.getId(), account);
        }
        return accountsById;
    }

    private static List<Long> memberUserIds(List<FamilyMember> members) {
        return members.stream()
            .map(member -> member.getUserId())
            .toList();
    }
}
