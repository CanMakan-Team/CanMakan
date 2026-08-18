package com.canmakan.backend.user;

import com.canmakan.backend.shared.security.AuthUserChecker;
import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.user.dto.NotificationPreferenceResponse;
import com.canmakan.backend.user.dto.SetNotificationPreferenceRequest;
import com.canmakan.backend.user.service.UserPreferenceService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account-level preference APIs. Unlike {@code FamilyController}, these do not require
 * (or check) family membership -- an individual account with no family circle uses them
 * the same way a family member does. Caller identity comes from the JWT principal.
 *
 * @author Amelia
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/users")
public class UserPreferenceController {

    private final UserPreferenceService userPreferenceService;

    // GET /api/users/me/preferences/notifications -> current notification preference
    @GetMapping("/me/preferences/notifications")
    public NotificationPreferenceResponse getNotificationPreference(
            @AuthenticationPrincipal AuthUserDetails userDetails) {
        long userId = AuthUserChecker.requireUserId(userDetails);
        NotificationPreferenceResponse preference =
            userPreferenceService.getNotificationPreference(userId);
        log.info("GET /users/me/preferences/notifications → 200 enabled={}",
            preference.notificationsEnabled());
        return preference;
    }

    // PUT /api/users/me/preferences/notifications -> set notification preference
    @PutMapping("/me/preferences/notifications")
    public NotificationPreferenceResponse setNotificationPreference(
            @AuthenticationPrincipal AuthUserDetails userDetails,
            @Valid @RequestBody SetNotificationPreferenceRequest request) {
        long userId = AuthUserChecker.requireUserId(userDetails);
        NotificationPreferenceResponse preference =
            userPreferenceService.setNotificationPreference(userId, request.notificationsEnabled());
        log.info("PUT /users/me/preferences/notifications → 200 enabled={}",
            preference.notificationsEnabled());
        return preference;
    }
}
