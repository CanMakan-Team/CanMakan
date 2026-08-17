package com.canmakan.backend.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Per-user preferences including UC11 active scan profile.
 *
 * @author Amelia
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "user_preferences")
public class UserPreference {

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "theme", length = 20)
    private String theme = "DEFAULT";

    // Off by default: a new user must explicitly opt in via the Settings toggle, which
    // is what triggers the POST_NOTIFICATIONS permission prompt on the client.
    @Column(name = "notifications_enabled")
    private Boolean notificationsEnabled = false;

    @Column(name = "language", length = 10)
    private String language = "ENGLISH";

    @Column(name = "active_profile_id")
    private Long activeProfileId;
}
