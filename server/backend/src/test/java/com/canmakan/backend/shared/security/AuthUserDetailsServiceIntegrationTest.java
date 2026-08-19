package com.canmakan.backend.shared.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AuthUserDetailsServiceIntegrationTest {

    @Autowired
    private AuthUserDetailsService userDetailsService;

    @Test
    void loadsCanonicalRolesThroughJoinedAuthenticationProjection() {
        AuthUserDetails user = (AuthUserDetails) userDetailsService
            .loadUserByUsername("  Sarah@Example.COM  ");
        AuthUserDetails admin = (AuthUserDetails) userDetailsService
            .loadUserByUsername("admin1@canmakan.com");

        assertEquals(4L, user.getUserId());
        assertEquals("sarah@example.com", user.getUsername());
        assertEquals(SystemRole.USER, user.getSystemRole());
        assertEquals("ROLE_USER", user.getAuthorities().iterator().next().getAuthority());

        assertEquals(1L, admin.getUserId());
        assertEquals(SystemRole.ADMIN, admin.getSystemRole());
        assertEquals("ROLE_ADMIN", admin.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    void reloadsCurrentAccountThroughJoinedJwtSubjectProjection() {
        AuthUserDetails user = userDetailsService.loadUserById(4L);
        AuthUserDetails admin = userDetailsService.loadUserById(1L);

        assertEquals("sarah@example.com", user.getUsername());
        assertEquals(SystemRole.USER, user.getSystemRole());
        assertEquals("admin1@canmakan.com", admin.getUsername());
        assertEquals(SystemRole.ADMIN, admin.getSystemRole());
    }
}
