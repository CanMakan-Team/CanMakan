package com.canmakan.backend.family.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.canmakan.backend.family.config.InviteProperties;
import com.canmakan.backend.family.config.ResendProperties;
import com.canmakan.backend.family.dto.InvitationResponse;
import com.canmakan.backend.family.model.InvitationStatus;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Invitation email HTML copy and branding.
 *
 * @author Amelia
 */
@DisplayName("InvitationEmailService HTML")
class InvitationEmailServiceTest {

    private HttpServer resendStub;

    @AfterEach
    void tearDown() {
        if (resendStub != null) {
            resendStub.stop(0);
        }
    }

    @Test
    void htmlLinksWebInviteAndFirebaseAppDistributionForMobile() {
        InviteProperties inviteProperties = new InviteProperties();
        inviteProperties.setMobileBaseUrl(
            "https://appdistribution.firebase.google.com/pub/testerapps/canmakan"
        );
        InvitationEmailService service =
            new InvitationEmailService(new ResendProperties(), inviteProperties);

        InvitationResponse invitation = invitation(
            "token",
            "https://canmakan-project.web.app/invite/token",
            Instant.parse("2026-08-16T00:00:00Z")
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

        String html = service.buildInvitationHtml(
            "Wong Family",
            invitation("token", "https://canmakan-project.web.app/invite/token", null),
            false
        );

        assertThat(html).contains("href=\"canmakan://invite/token\"");
        assertThat(html).contains("https://canmakan-project.web.app/email/canmakan-mascot-wave.png");
        assertThat(html).contains("will not wait forever");
    }

    @Test
    void htmlShowsWebOnlyAcceptLinkWhenMobileBaseIsBlank() {
        InviteProperties inviteProperties = new InviteProperties();
        inviteProperties.setMobileBaseUrl(" ");
        InvitationEmailService service =
            new InvitationEmailService(new ResendProperties(), inviteProperties);

        String html = service.buildInvitationHtml(
            "Wong Family",
            invitation("token", "https://canmakan-project.web.app/invite/token", null),
            true
        );

        assertThat(html).contains("Accept via <a href=\"https://canmakan-project.web.app/invite/token\">web</a>.</p>");
        assertThat(html).doesNotContain(">mobile</a>");
    }

    @Test
    void htmlShowsMobileOnlyAcceptLinkWhenWebInviteUrlMissing() {
        InviteProperties inviteProperties = new InviteProperties();
        inviteProperties.setMobileBaseUrl("canmakan://invite");
        InvitationEmailService service =
            new InvitationEmailService(new ResendProperties(), inviteProperties);

        String html = service.buildInvitationHtml(
            "Wong & Family",
            invitation("token", null, null),
            true
        );

        assertThat(html).contains("<strong>Wong &amp; Family</strong>");
        assertThat(html).contains("Accept via <a href=\"canmakan://invite/token\">mobile</a>.</p>");
        assertThat(html).doesNotContain(">web</a>");
    }

    @Test
    void htmlOmitsAcceptLinksWhenBothUrlsMissing() {
        InviteProperties inviteProperties = new InviteProperties();
        inviteProperties.setMobileBaseUrl("");
        InvitationEmailService service =
            new InvitationEmailService(new ResendProperties(), inviteProperties);

        String html = service.buildInvitationHtml(
            "Wong Family",
            invitation("token", "   ", null),
            true
        );

        assertThat(html).doesNotContain("Accept via");
    }

    @Test
    void sendInvitationEmailReturnsFalseWhenResendIsNotConfigured() {
        ResendProperties resend = new ResendProperties();
        resend.setEnabled(false);
        InvitationEmailService service =
            new InvitationEmailService(resend, new InviteProperties());

        boolean sent = service.sendInvitationEmail(
            "Wong Family",
            invitation("token", "https://canmakan-project.web.app/invite/token", null)
        );

        assertThat(sent).isFalse();
    }

    @Test
    void sendInvitationEmailReturnsFalseWhenApiKeyIsBlank() {
        ResendProperties resend = new ResendProperties();
        resend.setEnabled(true);
        resend.setApiKey("  ");
        resend.setFrom("CanMakan <onboarding@resend.dev>");
        InvitationEmailService service =
            new InvitationEmailService(resend, new InviteProperties());

        boolean sent = service.sendInvitationEmail(
            "Wong Family",
            invitation("token", "https://canmakan-project.web.app/invite/token", null)
        );

        assertThat(sent).isFalse();
    }

    @Test
    void sendInvitationEmailReturnsFalseWhenFromAddressIsBlank() {
        ResendProperties resend = new ResendProperties();
        resend.setEnabled(true);
        resend.setApiKey("test-key");
        resend.setFrom("  ");
        InvitationEmailService service =
            new InvitationEmailService(resend, new InviteProperties());

        boolean sent = service.sendInvitationEmail(
            "Wong Family",
            invitation("token", "https://canmakan-project.web.app/invite/token", null)
        );

        assertThat(sent).isFalse();
    }

    @Test
    void sendInvitationEmailReturnsTrueWhenResendAcceptsTheRequest() throws IOException {
        resendStub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        resendStub.createContext("/emails", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        resendStub.start();
        InvitationEmailService service = new InvitationEmailService(
            resendConfiguredFor(resendStub), new InviteProperties());

        boolean sent = service.sendInvitationEmail(
            "Wong Family",
            invitation("token", "https://canmakan-project.web.app/invite/token", null)
        );

        assertThat(sent).isTrue();
    }

    @Test
    void sendInvitationEmailReturnsFalseWhenResendRejectsTheRequest() throws IOException {
        resendStub = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        resendStub.createContext("/emails", exchange -> {
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"message\":\"invalid api key\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(401, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        resendStub.start();
        InvitationEmailService service = new InvitationEmailService(
            resendConfiguredFor(resendStub), new InviteProperties());

        boolean sent = service.sendInvitationEmail(
            "Wong Family",
            invitation("token", "https://canmakan-project.web.app/invite/token", null)
        );

        assertThat(sent).isFalse();
    }

    @Test
    void sendInvitationEmailReturnsFalseWhenResendHostIsUnreachable() {
        ResendProperties resend = new ResendProperties();
        resend.setEnabled(true);
        resend.setApiKey("test-key");
        resend.setFrom("CanMakan <onboarding@resend.dev>");
        resend.setApiUrl("http://127.0.0.1:1/emails");
        InvitationEmailService service = new InvitationEmailService(resend, new InviteProperties());

        boolean sent = service.sendInvitationEmail(
            "Wong Family",
            invitation("token", "https://canmakan-project.web.app/invite/token", null)
        );

        assertThat(sent).isFalse();
    }

    private static ResendProperties resendConfiguredFor(HttpServer server) {
        ResendProperties resend = new ResendProperties();
        resend.setEnabled(true);
        resend.setApiKey("test-key");
        resend.setFrom("CanMakan <onboarding@resend.dev>");
        resend.setApiUrl("http://localhost:" + server.getAddress().getPort() + "/emails");
        return resend;
    }

    @Test
    void htmlMascotFallsBackToHostedUrlWhenInviteUrlHasNoAuthority() {
        InvitationEmailService service =
            new InvitationEmailService(new ResendProperties(), new InviteProperties());

        String html = service.buildInvitationHtml(
            "Wong Family",
            invitation("token", "invite/token", null),
            false
        );

        assertThat(html).contains("src=\"/email/canmakan-mascot-wave.png\"");
    }

    @Test
    void htmlMascotFallsBackToHostedUrlWhenInviteUrlIsMissing() {
        InvitationEmailService service =
            new InvitationEmailService(new ResendProperties(), new InviteProperties());

        String html = service.buildInvitationHtml(
            "Wong Family",
            invitation("token", null, null),
            false
        );

        assertThat(html).contains("src=\"/email/canmakan-mascot-wave.png\"");
    }

    @Test
    void htmlMascotFallsBackToHostedUrlWhenInviteUrlIsMalformed() {
        InvitationEmailService service =
            new InvitationEmailService(new ResendProperties(), new InviteProperties());

        String html = service.buildInvitationHtml(
            "Wong Family",
            invitation("token", "http://exa mple.com/invite/token", null),
            false
        );

        assertThat(html).contains("src=\"/email/canmakan-mascot-wave.png\"");
    }

    private static InvitationResponse invitation(
        String token,
        String inviteUrl,
        Instant expiresAt
    ) {
        return new InvitationResponse(
            1L,
            "guest@example.com",
            "SPOUSE",
            token,
            "ABCD1234",
            inviteUrl,
            InvitationStatus.PENDING,
            expiresAt,
            false,
            true
        );
    }
}
