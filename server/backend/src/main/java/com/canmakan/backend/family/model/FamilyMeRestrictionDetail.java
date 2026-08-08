package com.canmakan.backend.family.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UC6: Restriction etail DTO for Family Member Dietary Restrictions.
 * Represents a specific dietary restriction detail associated with a family member.
 * Provides the required display and severity details to render for the data points
 * within the family allergy summary grid.
 */

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FamilyMeRestrictionDetail {
    private String code;
    private String displayName;
    private String severity;
}
