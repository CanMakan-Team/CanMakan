package com.canmakan.backend.family;

import com.canmakan.backend.family.dto.InvitationResponse;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

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

    private static final DateTimeFormatter EXPIRY_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private final ResendProperties resendProperties;

    /**
     * Attempt to email the invitee. Safe to call when Resend is disabled.
     *
     * @return true when Resend accepted the send; false when skipped or failed
     */
    public boolean sendInvitationEmail(String familyName, InvitationResponse invitation) {
        if (!isConfigured()) {
            log.debug("Skipping invitation email; Resend is not configured.");
            return false;
        }

        String subject = "You're invited to join " + familyName + " on CanMakan";
        String expiry = invitation.expiresAt() == null
            ? ""
            : EXPIRY_FORMAT.format(invitation.expiresAt());
        String html = """
            <p>Hello,</p>
            <p>You have been invited to join the family circle <strong>%s</strong> on CanMakan.</p>
            <p>CanMakan helps your household check food products against dietary restrictions.</p>
            <p><a href="%s">Accept the invitation</a></p>
            <p>Or enter this invite code in the app: <strong>%s</strong></p>
            <p>This invitation expires at %s.</p>
            <p>If you did not expect this email, you can ignore it.</p>
            """.formatted(
                escape(familyName),
                escape(invitation.inviteUrl()),
                escape(invitation.inviteCode()),
                escape(expiry)
            );

        try {
            RestClient.create()
                .post()
                .uri(resendProperties.getApiUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + resendProperties.getApiKey().strip())
                .body(Map.of(
                    "from", resendProperties.getFrom(),
                    "to", java.util.List.of(invitation.invitedEmail()),
                    "subject", subject,
                    "html", html
                ))
                .retrieve()
                .toBodilessEntity();
            log.info("Invitation email sent to {}", invitation.invitedEmail());
            return true;
        } catch (Exception ex) {
            log.warn("Failed to send invitation email to {}: {}",
                invitation.invitedEmail(), ex.getMessage());
            return false;
        }
    }

    private boolean isConfigured() {
        if (!resendProperties.isEnabled()) {
            return false;
        }
        String key = resendProperties.getApiKey();
        return key != null && !key.isBlank();
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
