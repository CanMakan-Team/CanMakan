package com.canmakan.backend.family.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Public invite link bases and default invitation lifetime.
 * Override via {@code canmakan.invites.*}, {@code CANMAKAN_INVITES_PUBLIC_BASE_URL},
 * or {@code FIREBASE_APP_DISTRIBUTION_URL} (shared with the web portal download banner).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "canmakan.invites")
public class InviteProperties {

    /** Web origin used to build shareable {@code /invite/{token}} links. */
    private String publicBaseUrl = "http://localhost:5173";

    /**
     * Mobile link used in invite emails (typically Firebase App Distribution).
     * Value comes from {@code canmakan.invites.mobile-base-url} in
     * {@code application.properties}, which resolves
     * {@code FIREBASE_APP_DISTRIBUTION_URL} (same secret as the web
     * {@code VITE_FIREBASE_APP_DISTRIBUTION_URL} banner). This field initializer
     * is only the Java fallback if that property is missing.
     * HTTPS URLs are used as-is; custom schemes such as {@code canmakan://invite}
     * append {@code /{token}}.
     */
    private String mobileBaseUrl = "https://appdistribution.firebase.google.com/";

    /** Days until a newly created invitation expires. */
    private int expiryDays = 7;

    /**
     * Resolve the mobile href for invite email.
     * App Distribution / other HTTPS links are returned unchanged; custom-scheme
     * bases become {@code {base}/{token}}.
     */
    public String mobileInviteUrl(String invitationToken) {
        String base = mobileBaseUrl == null ? "" : mobileBaseUrl.strip();
        if (base.isEmpty()) {
            return "";
        }
        if (isHttpOrHttps(base)) {
            return base;
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String token = invitationToken == null ? "" : invitationToken.strip();
        if (token.isEmpty()) {
            return "";
        }
        return base + "/" + token;
    }

    private static boolean isHttpOrHttps(String value) {
        String lower = value.toLowerCase();
        return lower.startsWith("https://") || lower.startsWith("http://");
    }
}
