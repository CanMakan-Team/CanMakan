package com.canmakan.backend.family;

import static org.assertj.core.api.Assertions.assertThat;

import com.canmakan.backend.family.dto.InvitationResponse;
import com.canmakan.backend.family.model.InvitationStatus;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Invitation email HTML copy and branding.
 *
 * @author Amelia
 */
@DisplayName("InvitationEmailService HTML")
class InvitationEmailServiceTest {

    @Test
    void htmlLinksWebInviteAndFirebaseAppDistributionForMobile() {
        InviteProperties inviteProperties = new InviteProperties();
        inviteProperties.setMobileBaseUrl(
            "https://appdistribution.firebase.google.com/pub/testerapps/canmakan"
        );
        InvitationEmailService service =
            new InvitationEmailService(new ResendProperties(), inviteProperties);

        InvitationResponse invitation = new InvitationResponse(
            1L,
            "guest@example.com",
            "SPOUSE",
            "token",
            "ABCD1234",
            "https://canmakan-project.web.app/invite/token",
            InvitationStatus.PENDING,
            Instant.parse("2026-08-16T00:00:00Z"),
            false,
            true
        );

        String html = service.buildInvitationHtml("Wong Family", invitation, true);

        assertThat(html).contains("Hello!");
        assertThat(html).contains("cid:mascot-wave");
        assertThat(html).contains("Good news");
        assertThat(html).contains("<strong>Wong Family</strong>");
        assertThat(html).contains("register a CanMakan account");
        assertThat(html).contains("Notifications");
        assertThat(html).contains("Accept via ");
        assertThat(html).contains("href=\"https://canmakan-project.web.app/invite/token\"");
        assertThat(html).contains(">web</a>");
        assertThat(html).contains(
            "href=\"https://appdistribution.firebase.google.com/pub/testerapps/canmakan\""
        );
        assertThat(html).contains(">mobile</a>");
        assertThat(html).doesNotContain(
            "appdistribution.firebase.google.com/pub/testerapps/canmakan/token"
        );
        assertThat(html).doesNotContain("ABCD1234");
        assertThat(html).contains("16 Aug 2026, 8:00 AM SGT");
    }

    @Test
    void htmlAppendsTokenWhenMobileBaseIsCustomScheme() {
        InviteProperties inviteProperties = new InviteProperties();
        inviteProperties.setMobileBaseUrl("canmakan://invite");
        InvitationEmailService service =
            new InvitationEmailService(new ResendProperties(), inviteProperties);

        InvitationResponse invitation = new InvitationResponse(
            1L,
            "guest@example.com",
            "SPOUSE",
            "token",
            "ABCD1234",
            "https://canmakan-project.web.app/invite/token",
            InvitationStatus.PENDING,
            null,
            false,
            false
        );

        String html = service.buildInvitationHtml("Wong Family", invitation, false);

        assertThat(html).contains("href=\"canmakan://invite/token\"");
        assertThat(html).contains("https://canmakan-project.web.app/email/canmakan-mascot-wave.png");
    }
}
