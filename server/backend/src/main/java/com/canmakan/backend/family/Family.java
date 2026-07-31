package com.canmakan.backend.family;

import com.canmakan.backend.dietaryprofile.DietaryProfile;
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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a family account that can own multiple dietary profiles.
 * 
 * @author Amelia Wong
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = false)
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

    // Use hash set to ensure unique dietary profiles
    @OneToMany(mappedBy = "family", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<DietaryProfile> dietaryProfiles = new HashSet<>();
}
