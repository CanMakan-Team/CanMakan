package com.canmakan.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.canmakan.backend.admin.dto.AdminUserSummaryResponse;
import com.canmakan.backend.admin.dto.UpdateAccountStatusRequest;
import com.canmakan.backend.admin.dto.UpdateAccountStatusResponse;
import com.canmakan.backend.admin.exception.AdminUserNotFoundException;
import com.canmakan.backend.admin.exception.InvalidAccountStatusRequestException;
import com.canmakan.backend.admin.exception.ProtectedAccountOperationException;
import com.canmakan.backend.admin.model.AdminAuditLog;
import com.canmakan.backend.admin.repository.AdminAuditLogRepository;
import com.canmakan.backend.admin.service.UserAccountManagementService;
import com.canmakan.backend.auth.service.RefreshTokenService;
import com.canmakan.backend.shared.security.SystemRole;
import com.canmakan.backend.user.repository.AdminUserSummaryView;
import com.canmakan.backend.user.model.UserAccount;
import com.canmakan.backend.user.repository.UserAccountRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@ExtendWith(MockitoExtension.class)
class UserAccountManagementServiceTest {

    private static final Long ACTOR_ID = 99L;
    private static final Long TARGET_ID = 30L;
    private static final LocalDateTime UPDATED_AT =
            LocalDateTime.of(2026, 8, 10, 20, 30);

    @Mock
    private UserAccountRepository userAccountRepository;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AdminAuditLogRepository adminAuditLogRepository;

    @Mock
    private EntityManager entityManager;

    private ObjectMapper objectMapper;
    private UserAccountManagementService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new UserAccountManagementService(
                userAccountRepository,
                refreshTokenService,
                adminAuditLogRepository,
                objectMapper,
                entityManager
        );
    }

    @Test
    void listWithoutFiltersUsesNullCriteriaAndMapsTheResponse() {
        AdminUserSummaryView view = summaryView(
                12L,
                "admin@example.com",
                "ADMIN",
                true,
                UPDATED_AT
        );
        when(userAccountRepository.findAdminUserSummaries(null, null, null))
                .thenReturn(List.of(view));

        List<AdminUserSummaryResponse> result = service.listAccounts(null, null, null);

        assertThat(result).containsExactly(new AdminUserSummaryResponse(
                12L,
                "admin@example.com",
                SystemRole.ADMIN,
                true,
                UPDATED_AT
        ));
    }

    @Test
    void listTrimsTheEmailQuery() {
        when(userAccountRepository.findAdminUserSummaries("user@example", null, null))
                .thenReturn(List.of());

        service.listAccounts("  user@example  ", null, null);

        verify(userAccountRepository).findAdminUserSummaries("user@example", null, null);
    }

    @Test
    void listConvertsBlankQueryToNull() {
        when(userAccountRepository.findAdminUserSummaries(null, null, null))
                .thenReturn(List.of());

        service.listAccounts("   ", "  ", null);

        verify(userAccountRepository).findAdminUserSummaries(null, null, null);
    }

    @Test
    void listAcceptsUserRoleCaseInsensitively() {
        when(userAccountRepository.findAdminUserSummaries(null, "USER", null))
                .thenReturn(List.of());

        service.listAccounts(null, " user ", null);

        verify(userAccountRepository).findAdminUserSummaries(null, "USER", null);
    }

    @Test
    void listAcceptsAdminRoleCaseInsensitively() {
        when(userAccountRepository.findAdminUserSummaries(null, "ADMIN", null))
                .thenReturn(List.of());

        service.listAccounts(null, "aDmIn", null);

        verify(userAccountRepository).findAdminUserSummaries(null, "ADMIN", null);
    }

    @Test
    void listRejectsAnUnsupportedRole() {
        assertThatThrownBy(() -> service.listAccounts(null, "PRIMARY_ADMIN", null))
                .isInstanceOf(InvalidAccountStatusRequestException.class)
                .hasMessage("Role must be USER or ADMIN.");

        verifyNoInteractions(userAccountRepository);
    }

    @Test
    void listPassesTheActiveFilterUnchanged() {
        when(userAccountRepository.findAdminUserSummaries(null, null, false))
                .thenReturn(List.of());

        service.listAccounts(null, null, false);

        verify(userAccountRepository).findAdminUserSummaries(null, null, false);
    }

    @Test
    void selfStatusManagementIsRejectedBeforeLocksOrWrites() {
        assertThatThrownBy(() -> service.updateAccountStatus(
                TARGET_ID,
                TARGET_ID,
                new UpdateAccountStatusRequest(true, null)
        )).isInstanceOf(ProtectedAccountOperationException.class);

        verifyNoInteractions(
                userAccountRepository,
                refreshTokenService,
                adminAuditLogRepository,
                entityManager
        );
    }

    @Test
    void missingActiveStateIsRejectedBeforeLocks() {
        assertThatThrownBy(() -> service.updateAccountStatus(
                ACTOR_ID,
                TARGET_ID,
                new UpdateAccountStatusRequest(null, "reason")
        )).isInstanceOf(InvalidAccountStatusRequestException.class)
                .hasMessage("Active status is required.");

        verifyNoInteractions(userAccountRepository);
    }

    @Test
    void unknownTargetIsReportedAfterTheRequiredLocks() {
        List<UserAccount> admins = List.of(account(1L, "a1@example.com", true));
        when(userAccountRepository.findAllAdminsForUpdate()).thenReturn(admins);
        when(userAccountRepository.findByIdForUpdate(TARGET_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateAccountStatus(
                ACTOR_ID,
                TARGET_ID,
                request(false, "reason")
        )).isInstanceOf(AdminUserNotFoundException.class)
                .hasMessage("User account not found: 30");

        InOrder order = inOrder(userAccountRepository);
        order.verify(userAccountRepository).findAllAdminsForUpdate();
        order.verify(userAccountRepository).findByIdForUpdate(TARGET_ID);
        verifyNoInteractions(refreshTokenService, adminAuditLogRepository, entityManager);
    }

    @Test
    void activeUserCanBeSuspendedWithRevocationAuditAndMappedResponse() throws Exception {
        UserAccount target = account(TARGET_ID, "user@example.com", true);
        stubUserTarget(target);
        when(entityManager.merge(target)).thenReturn(target);

        UpdateAccountStatusResponse response = service.updateAccountStatus(
                ACTOR_ID,
                TARGET_ID,
                request(false, "  Repeated misuse of the service  ")
        );

        assertThat(response).isEqualTo(new UpdateAccountStatusResponse(
                TARGET_ID,
                "user@example.com",
                SystemRole.USER,
                false,
                UPDATED_AT,
                true
        ));
        verify(refreshTokenService, times(1)).revokeAllForUser(TARGET_ID);
        verify(entityManager).merge(target);
        verify(userAccountRepository).flush();
        verify(entityManager).refresh(target);

        AdminAuditLog audit = captureSingleAudit();
        assertThat(audit.getAdminUserId()).isEqualTo(ACTOR_ID);
        assertThat(audit.getActionPerformed()).isEqualTo("ACCOUNT_STATUS_CHANGED");
        assertThat(audit.getTargetEntity()).isEqualTo("USER_ACCOUNT");
        assertThat(audit.getIpAddress()).isNull();
        JsonNode details = objectMapper.readTree(audit.getDetails());
        assertThat(details.path("targetUserId").longValue()).isEqualTo(TARGET_ID);
        assertThat(details.path("previousActive").booleanValue()).isTrue();
        assertThat(details.path("newActive").booleanValue()).isFalse();
        assertThat(details.path("reason").textValue())
                .isEqualTo("Repeated misuse of the service");
    }

    @Test
    void suspendedUserCanBeReactivatedWithoutRevokingSessions() {
        UserAccount target = account(TARGET_ID, "user@example.com", false);
        stubUserTarget(target);

        UpdateAccountStatusResponse response = service.updateAccountStatus(
                ACTOR_ID,
                TARGET_ID,
                request(true, "Appeal approved")
        );

        assertThat(response.role()).isEqualTo(SystemRole.USER);
        assertThat(response.active()).isTrue();
        assertThat(response.changed()).isTrue();
        verifyNoInteractions(refreshTokenService);
        verify(adminAuditLogRepository, times(1)).save(any(AdminAuditLog.class));
    }

    @Test
    void activeAdminCanBeSuspendedWhenAnotherActiveAdminRemains() {
        UserAccount target = account(TARGET_ID, "admin@example.com", true);
        UserAccount otherAdmin = account(31L, "other.admin@example.com", true);
        when(userAccountRepository.findAllAdminsForUpdate())
                .thenReturn(List.of(target, otherAdmin));
        when(entityManager.merge(target)).thenReturn(target);

        UpdateAccountStatusResponse response = service.updateAccountStatus(
                ACTOR_ID,
                TARGET_ID,
                request(false, "Administrative decision")
        );

        assertThat(response.role()).isEqualTo(SystemRole.ADMIN);
        assertThat(response.active()).isFalse();
        assertThat(response.changed()).isTrue();
        verify(userAccountRepository, never()).findByIdForUpdate(anyLong());
        verify(refreshTokenService).revokeAllForUser(TARGET_ID);
    }

    @Test
    void lastActiveAdminCannotBeSuspended() {
        UserAccount target = account(TARGET_ID, "admin@example.com", true);
        UserAccount inactiveAdmin = account(31L, "inactive.admin@example.com", false);
        when(userAccountRepository.findAllAdminsForUpdate())
                .thenReturn(List.of(target, inactiveAdmin));

        assertThatThrownBy(() -> service.updateAccountStatus(
                ACTOR_ID,
                TARGET_ID,
                request(false, "Administrative decision")
        )).isInstanceOf(ProtectedAccountOperationException.class)
                .hasMessage("The last active administrator cannot be suspended.");

        assertThat(target.isActive()).isTrue();
        verify(userAccountRepository, never()).findByIdForUpdate(anyLong());
        verify(userAccountRepository, never()).flush();
        verifyNoInteractions(refreshTokenService, adminAuditLogRepository, entityManager);
    }

    @Test
    void inactiveAdminCanBeReactivated() {
        UserAccount target = account(TARGET_ID, "admin@example.com", false);
        UserAccount otherAdmin = account(31L, "other.admin@example.com", true);
        when(userAccountRepository.findAllAdminsForUpdate())
                .thenReturn(List.of(target, otherAdmin));

        UpdateAccountStatusResponse response = service.updateAccountStatus(
                ACTOR_ID,
                TARGET_ID,
                request(true, "Access restored")
        );

        assertThat(response.role()).isEqualTo(SystemRole.ADMIN);
        assertThat(response.active()).isTrue();
        assertThat(response.changed()).isTrue();
        verifyNoInteractions(refreshTokenService);
        verify(adminAuditLogRepository).save(any(AdminAuditLog.class));
    }

    @Test
    void sameStateActiveRequestIsANoOpAndDoesNotRequireReason() {
        UserAccount target = account(TARGET_ID, "user@example.com", true);
        stubUserTarget(target);

        UpdateAccountStatusResponse response = service.updateAccountStatus(
                ACTOR_ID,
                TARGET_ID,
                request(true, null)
        );

        assertThat(response.active()).isTrue();
        assertThat(response.changed()).isFalse();
        assertThat(target.isActive()).isTrue();
        verifyNoTransitionSideEffects();
    }

    @Test
    void sameStateSuspendedRequestIsANoOpAndAllowsBlankReason() {
        UserAccount target = account(TARGET_ID, "user@example.com", false);
        stubUserTarget(target);

        UpdateAccountStatusResponse response = service.updateAccountStatus(
                ACTOR_ID,
                TARGET_ID,
                request(false, "   ")
        );

        assertThat(response.active()).isFalse();
        assertThat(response.changed()).isFalse();
        assertThat(target.isActive()).isFalse();
        verifyNoTransitionSideEffects();
    }

    @Test
    void realTransitionRejectsNullReason() {
        UserAccount target = account(TARGET_ID, "user@example.com", true);
        stubUserTarget(target);

        assertThatThrownBy(() -> service.updateAccountStatus(
                ACTOR_ID,
                TARGET_ID,
                request(false, null)
        )).isInstanceOf(InvalidAccountStatusRequestException.class);

        assertThat(target.isActive()).isTrue();
        verifyNoTransitionSideEffects();
    }

    @Test
    void realTransitionRejectsBlankReason() {
        UserAccount target = account(TARGET_ID, "user@example.com", true);
        stubUserTarget(target);

        assertThatThrownBy(() -> service.updateAccountStatus(
                ACTOR_ID,
                TARGET_ID,
                request(false, " \t\n ")
        )).isInstanceOf(InvalidAccountStatusRequestException.class)
                .hasMessage("Reason is required for an account status change.");

        verifyNoTransitionSideEffects();
    }

    @Test
    void reasonLongerThanFiveHundredTrimmedCharactersIsRejected() {
        UserAccount target = account(TARGET_ID, "user@example.com", true);
        stubUserTarget(target);

        assertThatThrownBy(() -> service.updateAccountStatus(
                ACTOR_ID,
                TARGET_ID,
                request(false, " " + "x".repeat(501) + " ")
        )).isInstanceOf(InvalidAccountStatusRequestException.class)
                .hasMessage("Reason must not exceed 500 characters.");

        verifyNoTransitionSideEffects();
    }

    @Test
    void fiveHundredCharacterReasonIsAccepted() throws Exception {
        UserAccount target = account(TARGET_ID, "user@example.com", true);
        stubUserTarget(target);
        when(entityManager.merge(target)).thenReturn(target);
        String reason = "x".repeat(500);

        service.updateAccountStatus(ACTOR_ID, TARGET_ID, request(false, reason));

        JsonNode details = objectMapper.readTree(captureSingleAudit().getDetails());
        assertThat(details.path("reason").textValue()).hasSize(500);
    }

    @Test
    void refreshRevocationFailurePropagatesWithoutAnAuditWrite() {
        UserAccount target = account(TARGET_ID, "user@example.com", true);
        stubUserTarget(target);
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("refresh deletion failed");
        when(refreshTokenService.revokeAllForUser(TARGET_ID)).thenThrow(failure);

        assertThatThrownBy(() -> service.updateAccountStatus(
                ACTOR_ID,
                TARGET_ID,
                request(false, "reason")
        )).isSameAs(failure);

        verifyNoInteractions(adminAuditLogRepository, entityManager);
        verify(userAccountRepository, never()).flush();
    }

    @Test
    void auditPersistenceFailureIsNotSwallowed() {
        UserAccount target = account(TARGET_ID, "user@example.com", false);
        stubUserTarget(target);
        DataAccessResourceFailureException failure =
                new DataAccessResourceFailureException("audit insert failed");
        when(adminAuditLogRepository.save(any(AdminAuditLog.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.updateAccountStatus(
                ACTOR_ID,
                TARGET_ID,
                request(true, "reason")
        )).isSameAs(failure);

        verifyNoInteractions(refreshTokenService, entityManager);
        verify(userAccountRepository, never()).flush();
    }

    @Test
    void auditSerializationFailureIsConvertedToAnUncheckedTransactionFailure()
            throws Exception {
        UserAccount target = account(TARGET_ID, "user@example.com", true);
        when(userAccountRepository.findAllAdminsForUpdate())
                .thenReturn(activeAdminSet());
        when(userAccountRepository.findByIdForUpdate(TARGET_ID))
                .thenReturn(Optional.of(target));
        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any())).thenThrow(
                new JsonProcessingException("serialization failed") {
                }
        );
        UserAccountManagementService failingService = new UserAccountManagementService(
                userAccountRepository,
                refreshTokenService,
                adminAuditLogRepository,
                failingMapper,
                entityManager
        );

        assertThatThrownBy(() -> failingService.updateAccountStatus(
                ACTOR_ID,
                TARGET_ID,
                request(false, "reason")
        )).isInstanceOf(IllegalStateException.class)
                .hasMessage("Failed to serialize account status audit details.")
                .hasCauseInstanceOf(JsonProcessingException.class);

        assertThat(target.isActive()).isTrue();
        verifyNoInteractions(refreshTokenService, adminAuditLogRepository, entityManager);
        verify(userAccountRepository, never()).flush();
    }

    @Test
    void userTargetLockOccursAfterTheAdminSetLock() {
        UserAccount target = account(TARGET_ID, "user@example.com", false);
        stubUserTarget(target);

        service.updateAccountStatus(ACTOR_ID, TARGET_ID, request(true, "reason"));

        InOrder order = inOrder(userAccountRepository);
        order.verify(userAccountRepository).findAllAdminsForUpdate();
        order.verify(userAccountRepository).findByIdForUpdate(TARGET_ID);
    }

    @Test
    void adminTargetUsesTheAlreadyLockedEntityWithoutASecondTargetLock() {
        UserAccount target = account(TARGET_ID, "admin@example.com", false);
        when(userAccountRepository.findAllAdminsForUpdate())
                .thenReturn(List.of(target, account(31L, "other@example.com", true)));

        service.updateAccountStatus(ACTOR_ID, TARGET_ID, request(true, "reason"));

        verify(userAccountRepository, never()).findByIdForUpdate(anyLong());
        verify(userAccountRepository).findAllAdminsForUpdate();
    }

    @Test
    void statusMutationUsesTheNormalRequiredTransaction() throws Exception {
        Method method = UserAccountManagementService.class.getMethod(
                "updateAccountStatus",
                Long.class,
                Long.class,
                UpdateAccountStatusRequest.class
        );

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
        assertThat(transactional.readOnly()).isFalse();
    }

    private void stubUserTarget(UserAccount target) {
        when(userAccountRepository.findAllAdminsForUpdate()).thenReturn(activeAdminSet());
        when(userAccountRepository.findByIdForUpdate(target.getId()))
                .thenReturn(Optional.of(target));
    }

    private static List<UserAccount> activeAdminSet() {
        return List.of(
                account(1L, "admin1@example.com", true),
                account(2L, "admin2@example.com", true)
        );
    }

    private static UserAccount account(Long id, String email, boolean active) {
        UserAccount account = new UserAccount();
        account.setId(id);
        account.setEmail(email);
        account.setRoleId(2L);
        account.setActive(active);
        account.setUpdatedAt(UPDATED_AT);
        return account;
    }

    private static UpdateAccountStatusRequest request(boolean active, String reason) {
        return new UpdateAccountStatusRequest(active, reason);
    }

    private AdminAuditLog captureSingleAudit() {
        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(adminAuditLogRepository, times(1)).save(captor.capture());
        return captor.getValue();
    }

    private void verifyNoTransitionSideEffects() {
        verifyNoInteractions(refreshTokenService, adminAuditLogRepository, entityManager);
        verify(userAccountRepository, never()).flush();
    }

    private static AdminUserSummaryView summaryView(
            Long userId,
            String email,
            String role,
            boolean active,
            LocalDateTime updatedAt
    ) {
        AdminUserSummaryView view = mock(AdminUserSummaryView.class);
        when(view.getUserId()).thenReturn(userId);
        when(view.getEmail()).thenReturn(email);
        when(view.getRole()).thenReturn(role);
        when(view.getActive()).thenReturn(active);
        when(view.getUpdatedAt()).thenReturn(updatedAt);
        return view;
    }
}
