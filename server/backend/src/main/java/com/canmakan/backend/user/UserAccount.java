package com.canmakan.backend.user;

import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
import com.canmakan.backend.shared.AuditableEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Lightweight mapping for application users.
 * This allows other entities to reference users via JPA relationships
 * 
 * @author Amelia
 * @author YangMaowei
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true, exclude = {"dietaryProfile", "passwordHash"})
@Entity
@Table(name = "users")
@AttributeOverrides({
    @AttributeOverride(
        name = "createdAt",
        column = @Column(name = "created_at", insertable = false, updatable = false)
    ),
    @AttributeOverride(
        name = "updatedAt",
        column = @Column(name = "updated_at", insertable = false, updatable = false)
    )
})
public class UserAccount extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @OneToOne(mappedBy = "linkedUser")
    private DietaryProfile dietaryProfile;
}
