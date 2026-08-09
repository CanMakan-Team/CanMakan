package com.canmakan.backend.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SystemRoleTest {

    @Test
    void mapsUserToRoleUser() {
        SystemRole role = SystemRole.fromDatabaseName("USER");

        assertEquals(SystemRole.USER, role);
        assertEquals("ROLE_USER", role.authorityName());
        assertEquals("ROLE_USER", role.authority().getAuthority());
    }

    @Test
    void mapsAdminToRoleAdmin() {
        SystemRole role = SystemRole.fromDatabaseName("ADMIN");

        assertEquals(SystemRole.ADMIN, role);
        assertEquals("ROLE_ADMIN", role.authorityName());
        assertEquals("ROLE_ADMIN", role.authority().getAuthority());
    }

    @Test
    void rejectsUnknownAndMissingRoles() {
        assertThrows(
            IllegalArgumentException.class,
            () -> SystemRole.fromDatabaseName("ROLE_FAMILY_ADMIN")
        );
        assertThrows(IllegalArgumentException.class, () -> SystemRole.fromDatabaseName("user"));
        assertThrows(IllegalArgumentException.class, () -> SystemRole.fromDatabaseName(null));
    }
}
