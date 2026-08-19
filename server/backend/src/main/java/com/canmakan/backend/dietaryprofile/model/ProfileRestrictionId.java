package com.canmakan.backend.dietaryprofile.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite primary key for the profile_restrictions join table.
 * 
 * @author Amelia Wong
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class ProfileRestrictionId implements Serializable {

    @Column(name = "dietary_profile_id")
    private Long dietaryProfileId;

    @Column(name = "dietary_restriction_id")
    private Long dietaryRestrictionId;
}
