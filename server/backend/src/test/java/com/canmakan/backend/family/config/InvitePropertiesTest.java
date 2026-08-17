package com.canmakan.backend.family;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * InviteProperties mobile URL resolution for invite emails.
 */
@DisplayName("InviteProperties")
class InvitePropertiesTest {

    @Test
    void mobileInviteUrlReturnsHttpsAppDistributionUrlAsIs() {
        InviteProperties properties = new InviteProperties();
        properties.setMobileBaseUrl(
            "https://appdistribution.firebase.google.com/pub/testerapps/canmakan"
        );

        assertThat(properties.mobileInviteUrl("tok"))
            .isEqualTo("https://appdistribution.firebase.google.com/pub/testerapps/canmakan");
    }

    @Test
    void mobileInviteUrlAcceptsHttpSchemeWithoutAppendingToken() {
        InviteProperties properties = new InviteProperties();
        properties.setMobileBaseUrl("http://localhost:8080/dist");

        assertThat(properties.mobileInviteUrl("tok")).isEqualTo("http://localhost:8080/dist");
    }

    @Test
    void mobileInviteUrlAppendsTokenForCustomSchemeAndStripsTrailingSlash() {
        InviteProperties properties = new InviteProperties();
        properties.setMobileBaseUrl("canmakan://invite/");

        assertThat(properties.mobileInviteUrl(" tok ")).isEqualTo("canmakan://invite/tok");
    }

    @Test
    void mobileInviteUrlReturnsEmptyWhenBaseOrTokenMissing() {
        InviteProperties properties = new InviteProperties();

        properties.setMobileBaseUrl(null);
        assertThat(properties.mobileInviteUrl("tok")).isEmpty();

        properties.setMobileBaseUrl("   ");
        assertThat(properties.mobileInviteUrl("tok")).isEmpty();

        properties.setMobileBaseUrl("canmakan://invite");
        assertThat(properties.mobileInviteUrl(null)).isEmpty();
        assertThat(properties.mobileInviteUrl("  ")).isEmpty();
    }
}
