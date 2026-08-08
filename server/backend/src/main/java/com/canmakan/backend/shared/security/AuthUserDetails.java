package com.canmakan.backend.shared.security;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Spring Security user representation backed by an authenticated CanMakan account. */
public final class AuthUserDetails implements UserDetails, CredentialsContainer {

    private final AuthenticatedPrincipal principal;
    private final List<GrantedAuthority> authorities;
    private String password;

    public AuthUserDetails(AuthenticatedPrincipal principal, String passwordHash) {
        this.principal = Objects.requireNonNull(principal, "principal is required");
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("passwordHash is required");
        }
        this.password = passwordHash;
        this.authorities = List.of(principal.systemRole().authority());
    }

    public AuthenticatedPrincipal getAuthenticatedPrincipal() {
        return principal;
    }

    public Long getUserId() {
        return principal.userId();
    }

    public SystemRole getSystemRole() {
        return principal.systemRole();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return principal.email();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return principal.active();
    }

    @Override
    public void eraseCredentials() {
        password = null;
    }

    @Override
    public String toString() {
        return "AuthUserDetails[userId=" + getUserId()
            + ", email=" + getUsername()
            + ", active=" + isEnabled()
            + ", systemRole=" + getSystemRole() + "]";
    }
}
