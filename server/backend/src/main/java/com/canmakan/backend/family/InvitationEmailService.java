package com.canmakan.backend.family;

import com.canmakan.backend.family.dto.InvitationResponse;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Sends invitation emails via Resend when configured; otherwise no-ops.
 * Failures are logged and never fail invitation creation.
 *
 * @author Amelia
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvitationEmailService {

    private static final ZoneId SINGAPORE = ZoneId.of("Asia/Singapore");
    private static final DateTimeFormatter EXPIRY_FORMAT = DateTimeFormatter
        .ofPattern("d MMM yyyy, h:mm a 'SGT'", Locale.ENGLISH)
        .withZone(SINGAPORE);
    private static final String MASCOT_RESOURCE = "/email/canmakan-mascot-wave.png";
    private static final String PRIMARY_GREEN = "#1E7A5C";
    private static final String ON_PRIMARY = "#FFFFFF";
    private static final String PRIMARY_CONTAINER = "#DCF0E6";
    private static final String TEXT_PRIMARY = "#1C1C1C";
    private static final String TEXT_SECONDARY = "#6E6E6E";

    private final ResendProperties resendProperties;

    /**
     * Attempt to email the invitee. Safe to call when Resend is disabled.
     *
     * @return true when Resend accepted the send; false when skipped or failed
     */
    public boolean sendInvitationEmail(String familyName, InvitationResponse invitation) {
        if (!isConfigured()) {
            log.warn("Skipping invitation email to {}; Resend is not configured "
                + "(enabled={}, apiKey set={}, from set={}).",
                invitation.invitedEmail(),
                resendProperties.isEnabled(),
                hasText(resendProperties.getApiKey()),
                hasText(resendProperties.getFrom()));
            return false;
        }

        String subject = "You're invited to join " + familyName + " on CanMakan";
        String mascotBase64 = loadMascotPngBase64();
        String html = buildInvitationHtml(familyName, invitation, mascotBase64 != null);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", resendProperties.getFrom());
        payload.put("to", List.of(invitation.invitedEmail()));
        payload.put("subject", subject);
        payload.put("html", html);
        if (mascotBase64 != null) {
            Map<String, String> mascot = new LinkedHashMap<>();
            mascot.put("filename", "canmakan-mascot-wave.png");
            mascot.put("content", mascotBase64);
            mascot.put("content_type", "image/png");
            mascot.put("content_id", "mascot-wave");
            payload.put("attachments", List.of(mascot));
        }

        try {
            RestClient.create()
                .post()
                .uri(resendProperties.getApiUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + resendProperties.getApiKey().strip())
                .body(payload)
                .retrieve()
                .toBodilessEntity();
            log.info("Invitation email sent to {}", invitation.invitedEmail());
            return true;
        } catch (Exception ex) {
            String detail = ex.getMessage();
            if (ex instanceof RestClientResponseException restEx) {
                detail = restEx.getStatusCode() + " " + restEx.getResponseBodyAsString();
            }
            log.warn("Failed to send invitation email to {}: {}",
                invitation.invitedEmail(), detail);
            return false;
        }
    }

    String buildInvitationHtml(
        String familyName,
        InvitationResponse invitation,
        boolean inlineMascot
    ) {
        String expiry = invitation.expiresAt() == null
            ? ""
            : EXPIRY_FORMAT.format(invitation.expiresAt());
        String inviteUrl = escape(invitation.inviteUrl());
        String appInviteUrl = escape(appInviteDeepLink(invitation));
        String inviteCode = escape(invitation.inviteCode());
        String mascotSrc = inlineMascot
            ? "cid:mascot-wave"
            : escape(hostedMascotUrl(invitation));
        String expiryLine = expiry.isEmpty()
            ? "This invitation will not wait forever — join when you can."
            : "This invitation stays open until <strong>" + escape(expiry) + "</strong>.";

        return """
            <div style="font-family:Arial,Helvetica,sans-serif;color:%s;font-size:16px;line-height:1.5;max-width:560px;">
            <p style="margin:0 0 8px 0;">Hello!</p>
            <table role="presentation" cellpadding="0" cellspacing="0" border="0" width="100%">
            <tr>
            <td align="center" style="text-align:center;">
            <img src="%s" width="96" height="96" alt="CanMakan mascot waving" style="display:block;margin:0 auto;border:0;"/>
            </td>
            </tr>
            </table>
            <br>
            <br>
            <p>Good news — you have a seat waiting in the <strong>%s</strong> family circle on CanMakan.</p>
            <p>Scan groceries together, catch allergens and dietary no-gos before they hit the table, and keep everyone in the loop. Food shopping just got a whole lot kinder.</p>
            <br>
            <table role="presentation" cellpadding="0" cellspacing="0" border="0">
            <tr>
            <td bgcolor="%s" style="border-radius:8px;">
            <a href="%s" style="display:inline-block;padding:12px 28px;font-family:Arial,Helvetica,sans-serif;font-size:16px;font-weight:bold;color:%s;text-decoration:none;">Accept the invitation</a>
            </td>
            </tr>
            </table>
            <br>
            <p style="color:%s;font-size:14px;margin:0 0 8px 0;">That button opens the CanMakan app. On a computer, <a href="%s" style="color:%s;">continue in the browser</a>.</p>
            <p style="color:%s;font-size:14px;margin:0 0 8px 0;">If the app does not open, long-press or select the code to copy it, then paste it when you sign up or log in.</p>
            <p style="margin:8px 0 16px 0;">
            <span style="font-family:Consolas,Monaco,monospace;font-size:20px;font-weight:bold;letter-spacing:0.12em;color:%s;background-color:%s;padding:8px 16px;border-radius:8px;display:inline-block;-webkit-user-select:all;user-select:all;">%s</span>
            </p>
            <p style="color:%s;font-size:14px;">%s</p>
            <br>
            <p style="color:%s;font-size:13px;">If this was not meant for you, you can ignore this email — no action needed.</p>
            </div>
            """.formatted(
                TEXT_PRIMARY,
                mascotSrc,
                escape(familyName),
                PRIMARY_GREEN,
                appInviteUrl,
                ON_PRIMARY,
                TEXT_SECONDARY,
                inviteUrl,
                PRIMARY_GREEN,
                TEXT_SECONDARY,
                PRIMARY_GREEN,
                PRIMARY_CONTAINER,
                inviteCode,
                TEXT_SECONDARY,
                expiryLine,
                TEXT_SECONDARY
            );
    }

    static String appInviteDeepLink(InvitationResponse invitation) {
        String token = invitation.invitationToken() == null
            ? ""
            : invitation.invitationToken().strip();
        if (token.isEmpty()) {
            return invitation.inviteUrl() == null ? "#" : invitation.inviteUrl();
        }
        return "canmakan://invite/"
            + URLEncoder.encode(token, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String hostedMascotUrl(InvitationResponse invitation) {
        return webOrigin(invitation.inviteUrl()) + "/email/canmakan-mascot-wave.png";
    }

    private static String webOrigin(String inviteUrl) {
        if (!hasText(inviteUrl)) {
            return "";
        }
        try {
            URI uri = URI.create(inviteUrl);
            if (uri.getScheme() == null || uri.getAuthority() == null) {
                return "";
            }
            return uri.getScheme() + "://" + uri.getAuthority();
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private static String loadMascotPngBase64() {
        try (InputStream stream = InvitationEmailService.class.getResourceAsStream(MASCOT_RESOURCE)) {
            if (stream == null) {
                log.warn("Invitation mascot PNG missing at classpath {}", MASCOT_RESOURCE);
                return null;
            }
            return Base64.getEncoder().encodeToString(stream.readAllBytes());
        } catch (Exception ex) {
            log.warn("Could not load invitation mascot PNG: {}", ex.getMessage());
            return null;
        }
    }

    private boolean isConfigured() {
        return resendProperties.isEnabled()
            && hasText(resendProperties.getApiKey())
            && hasText(resendProperties.getFrom());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }
}
