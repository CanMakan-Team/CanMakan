package com.canmakan.backend.shared.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** System-level business roles supported by CanMakan authentication. */
public enum SystemRole {
    USER,
    ADMIN;

    public static SystemRole fromDatabaseName(String roleName) {
        return switch (roleName) {
            case "USER" -> USER;
            case "ADMIN" -> ADMIN;
            case null, default -> throw new IllegalArgumentException("Unsupported system role");
        };
    }

    public String authorityName() {
        return "ROLE_" + name();
    }

    public SimpleGrantedAuthority authority() {
        return new SimpleGrantedAuthority(authorityName());
    }
}
