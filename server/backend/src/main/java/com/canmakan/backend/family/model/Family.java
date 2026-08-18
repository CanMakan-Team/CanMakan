package com.canmakan.backend.family.model;

import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
import com.canmakan.backend.shared.AuditableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a family account that can own multiple dietary profiles.
 * Request validation for create lives on {@code CreateFamilyRequest}.
 *
 * @author Amelia Wong
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true, exclude = "dietaryProfiles")
@Entity
@Table(name = "families")
public class Family extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_name", nullable = false, length = 100)
    private String familyName;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    // Persist/merge children with the family aggregate; do not cascade REMOVE.
    @OneToMany(
            mappedBy = "family",
            cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private Set<DietaryProfile> dietaryProfiles = new HashSet<>();
}
