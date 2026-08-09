package com.canmakan.backend.family;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Public invite link base URL and default invitation lifetime.
 * Override via {@code canmakan.invites.*} or {@code CANMAKAN_INVITES_PUBLIC_BASE_URL}.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "canmakan.invites")
public class InviteProperties {

    /** Web origin used to build shareable {@code /invite/{token}} links. */
    private String publicBaseUrl = "http://localhost:5173";

    /** Days until a newly created invitation expires. */
    private int expiryDays = 7;
}
