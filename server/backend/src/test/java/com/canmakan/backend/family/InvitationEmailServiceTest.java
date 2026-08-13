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

    private final InvitationEmailService service = new InvitationEmailService(new ResendProperties());

    @Test
    void htmlAsksInviteeToRegisterOrSignInAndCheckNotifications() {
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
        assertThat(html).contains("align=\"center\"");
        assertThat(html).contains("width=\"100%\"");
        assertThat(html).contains("margin:0 auto");
        assertThat(html).contains("<br>");
        assertThat(html).contains("Good news");
        assertThat(html).contains("<strong>Wong Family</strong>");
        assertThat(html).contains("register a CanMakan account");
        assertThat(html).contains("sign in if you already have one");
        assertThat(html).contains("Notifications");
        assertThat(html).doesNotContain("Accept the invitation");
        assertThat(html).doesNotContain("href=\"https://canmakan-project.web.app/invite/token\"");
        assertThat(html).doesNotContain("/invite/token");
        assertThat(html).doesNotContain("canmakan://");
        assertThat(html).doesNotContain("ABCD1234");
        assertThat(html).doesNotContain("user-select:all");
        assertThat(html).contains("16 Aug 2026, 8:00 AM SGT");
        assertThat(html).doesNotContain("UTC");
    }

    @Test
    void htmlFallsBackToHostedMascotWhenInlineImageIsUnavailable() {
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

        assertThat(html).contains("https://canmakan-project.web.app/email/canmakan-mascot-wave.png");
        assertThat(html).doesNotContain("cid:mascot-wave");
        assertThat(html).contains("will not wait forever");
    }
}
