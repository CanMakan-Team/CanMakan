package com.canmakan.backend.analytics.repository;

/**
 * One application-user row for UC15 usage statistics: the account id, when it was created (epoch
 * milliseconds, to avoid timestamp-mapping ambiguity), and whether it has a dietary profile.
 */
public interface AppUserProjection {

    Long getUserId();

    Long getCreatedAtEpochMs();

    /** Number of dietary profiles linked to this user; greater than zero means the profile is set up. */
    Long getProfileCount();
}
