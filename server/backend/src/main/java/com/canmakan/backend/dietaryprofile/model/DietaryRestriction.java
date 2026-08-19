package com.canmakan.backend.dietaryprofile.model;

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
 * Reference data for dietary restrictions such as gluten, dairy, halal, or vegetarian rules.
 * 
 * @author Amelia Wong
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "profileRestrictions")
@Entity
@Table(name = "dietary_restrictions")
public class DietaryRestriction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "display_name", nullable = false, length = 155)
    private String displayName;

    @Column(name = "category", nullable = false, length = 45)
    private String category;

    @Column(name = "description")
    private String description;

    @OneToMany(mappedBy = "dietaryRestriction")
    private Set<ProfileRestriction> profileRestrictions = new HashSet<>();
}
