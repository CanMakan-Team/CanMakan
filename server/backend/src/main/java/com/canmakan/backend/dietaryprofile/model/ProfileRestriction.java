package com.canmakan.backend.dietaryprofile.model;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Join entity linking a dietary profile to a selected dietary restriction and severity level.
 * 
 * @author Amelia Wong
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = {"dietaryProfile", "dietaryRestriction"})
@Entity
@Table(name = "profile_restrictions")
public class ProfileRestriction {

    @EmbeddedId
    private ProfileRestrictionId id;

    @ManyToOne
    @MapsId("dietaryProfileId")
    @JoinColumn(name = "dietary_profile_id")
    private DietaryProfile dietaryProfile;

    @ManyToOne
    @MapsId("dietaryRestrictionId")
    @JoinColumn(name = "dietary_restriction_id")
    private DietaryRestriction dietaryRestriction;

    @Column(name = "severity_level", length = 20)
    private String severityLevel;
}
