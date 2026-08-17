package com.canmakan.backend.family.service;

import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
import com.canmakan.backend.dietaryprofile.model.DietaryRestriction;
import com.canmakan.backend.dietaryprofile.model.ProfileRestriction;
import com.canmakan.backend.family.dto.FamilyMeRestrictionDetail;
import com.canmakan.backend.user.model.UserAccount;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

final class FamilyDisplayUtil {

    private FamilyDisplayUtil() {
    }

    static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return local.charAt(0) + "***" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }

    static boolean isMembershipUniqueViolation(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException
                && sqlException.getErrorCode() == 1062) {
                return true;
            }
            String detail = current.getMessage() == null ? "" : current.getMessage().toLowerCase();
            if (detail.contains("uq_family_members_user_id")
                || detail.contains("family_members.user_id")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    static String profileNameFromUser(UserAccount user) {
        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            return "My Profile";
        }
        int at = email.indexOf('@');
        String local = at > 0 ? email.substring(0, at) : email;
        return local.isBlank() ? "My Profile" : local;
    }

    static List<FamilyMeRestrictionDetail> mapRestrictions(
            Optional<DietaryProfile> dietaryProfileOpt) {
        return dietaryProfileOpt.map(profile ->
            profile.getProfileRestrictions().stream()
                .map(restriction -> new FamilyMeRestrictionDetail(
                    restriction.getDietaryRestriction().getCode(),
                    restriction.getDietaryRestriction().getDisplayName(),
                    restriction.getSeverityLevel()
                )).toList()
        ).orElse(List.of());
    }

    /**
     * Splits profile restriction codes for the roster DTO.
     * RELIGIOUS category -> commonRequirements; all other categories -> restrictions.
     */
    static RestrictionCodeSplit splitRestrictionCodes(
            Optional<DietaryProfile> dietaryProfileOpt) {
        List<String> common = new ArrayList<>();
        List<String> individual = new ArrayList<>();
        if (dietaryProfileOpt.isEmpty()) {
            return new RestrictionCodeSplit(common, individual);
        }
        for (ProfileRestriction profileRestriction :
                dietaryProfileOpt.get().getProfileRestrictions()) {
            DietaryRestriction restriction = profileRestriction.getDietaryRestriction();
            if (restriction == null || restriction.getCode() == null) {
                continue;
            }
            String code = restriction.getCode();
            String category = restriction.getCategory() == null
                ? ""
                : restriction.getCategory().trim().toUpperCase(Locale.ROOT);
            if ("RELIGIOUS".equals(category)) {
                common.add(code);
            } else {
                individual.add(code);
            }
        }
        return new RestrictionCodeSplit(List.copyOf(common), List.copyOf(individual));
    }

    record RestrictionCodeSplit(
        List<String> commonRequirements,
        List<String> restrictions
    ) {
    }
}
