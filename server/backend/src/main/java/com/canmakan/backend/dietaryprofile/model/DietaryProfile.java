package com.canmakan.backend.dietaryprofile.model;

import com.canmakan.backend.family.model.Family;
import com.canmakan.backend.shared.AuditableEntity;
import com.canmakan.backend.user.UserAccount;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a dietary profile belonging to a family member.
 * Stores profile metadata and linked restrictions.
 * 
 * @author Amelia Wong
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true, exclude = "profileRestrictions")
@Entity
@Table(name = "dietary_profiles")
public class DietaryProfile extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "family_id", nullable = true)
    private Family family;

    @OneToOne
    @JoinColumn(name = "linked_user_id")
    private UserAccount linkedUser;

    @Column(name = "profile_name", nullable = false, length = 100)
    private String profileName;

    @Column(name = "relationship", length = 30)
    private String relationship;

    @Column(name = "is_primary")
    private boolean primary;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    @OneToMany(mappedBy = "dietaryProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProfileRestriction> profileRestrictions = new HashSet<>();
}
