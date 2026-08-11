package com.canmakan.backend.admin;

import com.canmakan.backend.admin.dto.AdminUserSummaryResponse;
import com.canmakan.backend.admin.dto.UpdateAccountStatusRequest;
import com.canmakan.backend.admin.dto.UpdateAccountStatusResponse;
import com.canmakan.backend.admin.exception.AdminUserNotFoundException;
import com.canmakan.backend.admin.exception.InvalidAccountStatusRequestException;
import com.canmakan.backend.admin.exception.ProtectedAccountOperationException;
import com.canmakan.backend.auth.RefreshTokenService;
import com.canmakan.backend.shared.security.SystemRole;
import com.canmakan.backend.user.AdminUserSummaryView;
import com.canmakan.backend.user.UserAccount;
import com.canmakan.backend.user.UserAccountRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transactional application service for UC13 account listing and status management. */
@Service
public class UserAccountManagementService {

    private static final String AUDIT_ACTION = "ACCOUNT_STATUS_CHANGED";
    private static final String AUDIT_TARGET = "USER_ACCOUNT";
    private static final int MAX_REASON_LENGTH = 500;

    private final UserAccountRepository userAccountRepository;
    private final RefreshTokenService refreshTokenService;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    public UserAccountManagementService(
            UserAccountRepository userAccountRepository,
            RefreshTokenService refreshTokenService,
            AdminAuditLogRepository adminAuditLogRepository,
            ObjectMapper objectMapper,
            EntityManager entityManager
    ) {
        this.userAccountRepository = userAccountRepository;
        this.refreshTokenService = refreshTokenService;
        this.adminAuditLogRepository = adminAuditLogRepository;
        this.objectMapper = objectMapper;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<AdminUserSummaryResponse> listAccounts(
            String query,
            String role,
            Boolean active
    ) {
        String normalizedQuery = blankToNull(query);
        SystemRole normalizedRole = parseRole(role);

        return userAccountRepository.findAdminUserSummaries(
                normalizedQuery,
                normalizedRole == null ? null : normalizedRole.name(),
                active
        ).stream().map(UserAccountManagementService::toSummaryResponse).toList();
    }

    @Transactional
    public UpdateAccountStatusResponse updateAccountStatus(
            Long actorUserId,
            Long targetUserId,
        UpdateAccountStatusRequest request
    ) {
        validateIdentifiers(actorUserId, targetUserId);
        if (request == null || request.active() == null) {
            throw new InvalidAccountStatusRequestException("Active status is required.");
        }
        if (Objects.equals(actorUserId, targetUserId)) {
            throw new ProtectedAccountOperationException(
                    "Administrators cannot change their own account status."
            );
        }
        List<UserAccount> lockedAdmins = userAccountRepository.findAllAdminsForUpdate();
        UserAccount targetAdmin = lockedAdmins.stream()
                .filter(account -> Objects.equals(account.getId(), targetUserId))
                .findFirst()
                .orElse(null);
        SystemRole targetRole = targetAdmin == null ? SystemRole.USER : SystemRole.ADMIN;
        UserAccount target = targetAdmin == null
                ? userAccountRepository.findByIdForUpdate(targetUserId)
                        .orElseThrow(() -> new AdminUserNotFoundException(targetUserId))
                : targetAdmin;

        boolean requestedActive = request.active();
        boolean previousActive = target.isActive();
        if (requestedActive == previousActive) {
            return toStatusResponse(target, targetRole, false);
        }

        String reason = validateReason(request.reason());
        protectLastActiveAdmin(targetRole, previousActive, requestedActive, lockedAdmins);
        String auditDetails = serializeAuditDetails(
                targetUserId,
                previousActive,
                requestedActive,
                reason
        );

        target.changeActiveStatus(requestedActive);
        UserAccount responseTarget = target;
        if (!requestedActive) {
            refreshTokenService.revokeAllForUser(targetUserId);
            responseTarget = entityManager.merge(target);
        }
        adminAuditLogRepository.save(new AdminAuditLog(
                actorUserId,
                AUDIT_ACTION,
                AUDIT_TARGET,
                auditDetails,
                null
        ));

        userAccountRepository.flush();
        entityManager.refresh(responseTarget);
        return toStatusResponse(responseTarget, targetRole, true);
    }

    private static AdminUserSummaryResponse toSummaryResponse(AdminUserSummaryView account) {
        return new AdminUserSummaryResponse(
                account.getUserId(),
                account.getEmail(),
                SystemRole.fromDatabaseName(account.getRole()),
                account.getActive(),
                account.getUpdatedAt()
        );
    }

    private static UpdateAccountStatusResponse toStatusResponse(
            UserAccount account,
            SystemRole role,
            boolean changed
    ) {
        return new UpdateAccountStatusResponse(
                account.getId(),
                account.getEmail(),
                role,
                account.isActive(),
                account.getUpdatedAt(),
                changed
        );
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static SystemRole parseRole(String role) {
        String normalizedRole = blankToNull(role);
        if (normalizedRole == null) {
            return null;
        }
        try {
            return SystemRole.valueOf(normalizedRole.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidAccountStatusRequestException("Role must be USER or ADMIN.");
        }
    }

    private static void validateIdentifiers(Long actorUserId, Long targetUserId) {
        if (actorUserId == null || targetUserId == null) {
            throw new InvalidAccountStatusRequestException(
                    "Actor and target user IDs are required."
            );
        }
    }

    private static String validateReason(String reason) {
        if (reason == null || reason.strip().isBlank()) {
            throw new InvalidAccountStatusRequestException(
                    "Reason is required for an account status change."
            );
        }
        String normalizedReason = reason.strip();
        if (normalizedReason.length() > MAX_REASON_LENGTH) {
            throw new InvalidAccountStatusRequestException(
                    "Reason must not exceed 500 characters."
            );
        }
        return normalizedReason;
    }

    private static void protectLastActiveAdmin(
            SystemRole targetRole,
            boolean previousActive,
            boolean requestedActive,
            List<UserAccount> lockedAdmins
    ) {
        if (targetRole != SystemRole.ADMIN || !previousActive || requestedActive) {
            return;
        }
        long activeAdminCount = lockedAdmins.stream().filter(UserAccount::isActive).count();
        if (activeAdminCount <= 1) {
            throw new ProtectedAccountOperationException(
                    "The last active administrator cannot be suspended."
            );
        }
    }

    private String serializeAuditDetails(
            Long targetUserId,
            boolean previousActive,
            boolean newActive,
            String reason
    ) {
        try {
            return objectMapper.writeValueAsString(new AccountStatusAuditDetails(
                    targetUserId,
                    previousActive,
                    newActive,
                    reason
            ));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to serialize account status audit details.",
                    exception
            );
        }
    }

    private record AccountStatusAuditDetails(
            Long targetUserId,
            boolean previousActive,
            boolean newActive,
            String reason
    ) {
    }
}
