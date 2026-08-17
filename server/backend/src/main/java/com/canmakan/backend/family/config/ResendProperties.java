package com.canmakan.backend.family.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Optional Resend delivery for family invitation emails (UC10).
 * Empty API key keeps local/CI runs as a no-op.
 *
 * @author Amelia
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "canmakan.email.resend")
public class ResendProperties {

    /** When false, invitation emails are never sent. */
    private boolean enabled = false;

    /** Resend API key; blank disables sending even when enabled. */
    private String apiKey = "";

    /** Verified sender address, e.g. {@code CanMakan <invites@example.com>}. */
    private String from = "CanMakan <onboarding@resend.dev>";

    /** Resend emails API URL. */
    private String apiUrl = "https://api.resend.com/emails";
}
