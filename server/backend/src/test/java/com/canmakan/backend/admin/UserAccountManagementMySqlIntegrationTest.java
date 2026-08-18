package com.canmakan.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.canmakan.backend.admin.dto.UpdateAccountStatusRequest;
import com.canmakan.backend.admin.dto.UpdateAccountStatusResponse;
import com.canmakan.backend.admin.exception.ProtectedAccountOperationException;
import com.canmakan.backend.admin.service.UserAccountManagementService;
import com.canmakan.backend.auth.service.RefreshTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "MYSQL_DB=canmakan_uc13_tx_test")
class UserAccountManagementMySqlIntegrationTest {

    private static final String PASSWORD_HASH =
            "$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Y1b7q/Q9I95zW";

    private final List<Long> testUserIds = new ArrayList<>();
    private final List<AdminState> adminStatesToRestore = new ArrayList<>();

    @Autowired
    private UserAccountManagementService service;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void cleanUpFixturesAndRestoreAdminStates() {
        try {
            for (Long userId : testUserIds) {
                jdbcTemplate.update(
                        """
                        DELETE FROM admin_audit_logs
                        WHERE admin_user_id = ?
                           OR JSON_UNQUOTE(JSON_EXTRACT(details, '$.targetUserId')) = ?
                        """,
                        userId,
                        userId.toString()
                );
                jdbcTemplate.update("DELETE FROM refresh_tokens WHERE user_id = ?", userId);
            }
        } finally {
            try {
                for (AdminState state : adminStatesToRestore) {
                    jdbcTemplate.update(
                            "UPDATE users SET is_active = ? WHERE id = ?",
                            state.active(),
                            state.userId()
                    );
                }
            } finally {
                for (Long userId : testUserIds) {
                    jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
                }
            }
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email LIKE 'uc13.phase7.%@example.com'",
                Long.class
        )).isZero();
        for (AdminState state : adminStatesToRestore) {
            assertThat(readActive(state.userId())).isEqualTo(state.active());
        }
    }

    @Test
    void successfulSuspensionCommitsStatusRevocationAndExactAudit() throws Exception {
        Long actorUserId = insertAccount("ADMIN", true);
        Long targetUserId = insertAccount("USER", true);
        createRefreshSession(targetUserId);

        UpdateAccountStatusResponse response = service.updateAccountStatus(
                actorUserId,
                targetUserId,
                new UpdateAccountStatusRequest(false, "  Repeated misuse  ")
        );

        LocalDateTime persistedUpdatedAt = readUpdatedAt(targetUserId);
        assertThat(response.changed()).isTrue();
        assertThat(response.active()).isFalse();
        assertThat(response.updatedAt()).isNotNull();
        assertThat(persistedUpdatedAt).isNotNull();
        assertThat(readActive(targetUserId)).isFalse();
        assertThat(countRefreshTokens(targetUserId)).isZero();

        AuditRow audit = singleAuditForTarget(targetUserId);
        assertThat(audit.adminUserId()).isEqualTo(actorUserId);
        assertThat(audit.actionPerformed()).isEqualTo("ACCOUNT_STATUS_CHANGED");
        assertThat(audit.targetEntity()).isEqualTo("USER_ACCOUNT");
        assertThat(audit.ipAddress()).isNull();
        assertThat(audit.createdAt()).isNotNull();
        assertAuditDetails(audit.details(), targetUserId, true, false, "Repeated misuse");
    }

    @Test
    void successfulReactivationCommitsStatusAndAuditWithoutChangingRefreshTokens()
            throws Exception {
        Long actorUserId = insertAccount("ADMIN", true);
        Long targetUserId = insertAccount("USER", false);
        createRefreshSession(targetUserId);
        long tokenCountBefore = countRefreshTokens(targetUserId);

        UpdateAccountStatusResponse response = service.updateAccountStatus(
                actorUserId,
                targetUserId,
                new UpdateAccountStatusRequest(true, "  Appeal approved  ")
        );

        assertThat(response.changed()).isTrue();
        assertThat(response.active()).isTrue();
        assertThat(response.updatedAt()).isNotNull();
        assertThat(readUpdatedAt(targetUserId)).isNotNull();
        assertThat(readActive(targetUserId)).isTrue();
        assertThat(countRefreshTokens(targetUserId)).isEqualTo(tokenCountBefore);

        AuditRow audit = singleAuditForTarget(targetUserId);
        assertThat(audit.adminUserId()).isEqualTo(actorUserId);
        assertThat(audit.actionPerformed()).isEqualTo("ACCOUNT_STATUS_CHANGED");
        assertThat(audit.targetEntity()).isEqualTo("USER_ACCOUNT");
        assertAuditDetails(audit.details(), targetUserId, false, true, "Appeal approved");
    }

    @Test
    void suspensionRollsBackStatusAndRefreshRevocationWhenAuditForeignKeyFails() {
        Long targetUserId = insertAccount("USER", true);
        createRefreshSession(targetUserId);
        long tokenCountBefore = countRefreshTokens(targetUserId);

        Throwable failure = catchThrowable(() -> service.updateAccountStatus(
                missingActorUserId(),
                targetUserId,
                new UpdateAccountStatusRequest(false, "Rollback suspension")
        ));

        assertAuditForeignKeyFailure(failure);
        assertThat(readActive(targetUserId)).isTrue();
        assertThat(countRefreshTokens(targetUserId)).isEqualTo(tokenCountBefore);
        assertThat(countAuditsForTarget(targetUserId)).isZero();
    }

    @Test
    void reactivationRollsBackStatusWhenAuditForeignKeyFails() {
        Long targetUserId = insertAccount("USER", false);

        Throwable failure = catchThrowable(() -> service.updateAccountStatus(
                missingActorUserId(),
                targetUserId,
                new UpdateAccountStatusRequest(true, "Rollback reactivation")
        ));

        assertAuditForeignKeyFailure(failure);
        assertThat(readActive(targetUserId)).isFalse();
        assertThat(countRefreshTokens(targetUserId)).isZero();
        assertThat(countAuditsForTarget(targetUserId)).isZero();
    }

    @Test
    void sameStateRequestDoesNotWriteAuditMutateStatusOrRevokeRefreshTokens() {
        Long actorUserId = insertAccount("ADMIN", true);
        Long targetUserId = insertAccount("USER", true);
        createRefreshSession(targetUserId);
        long tokenCountBefore = countRefreshTokens(targetUserId);
        LocalDateTime updatedAtBefore = readUpdatedAt(targetUserId);

        UpdateAccountStatusResponse response = service.updateAccountStatus(
                actorUserId,
                targetUserId,
                new UpdateAccountStatusRequest(true, null)
        );

        assertThat(response.changed()).isFalse();
        assertThat(response.active()).isTrue();
        assertThat(readActive(targetUserId)).isTrue();
        assertThat(readUpdatedAt(targetUserId)).isEqualTo(updatedAtBefore);
        assertThat(countRefreshTokens(targetUserId)).isEqualTo(tokenCountBefore);
        assertThat(countAuditsForTarget(targetUserId)).isZero();
    }

    @RepeatedTest(value = 5, name = "concurrent cross-suspension repetition {currentRepetition}")
    void concurrentCrossSuspensionsLeaveExactlyOneActiveAdminWithoutDeadlock()
            throws Exception {
        isolateExistingAdmins();
        Long firstAdminId = insertAccount("ADMIN", true);
        Long secondAdminId = insertAccount("ADMIN", true);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothReady = new CountDownLatch(2);
        CountDownLatch startTogether = new CountDownLatch(1);

        try {
            Future<UpdateAccountStatusResponse> firstAttempt = executor.submit(() -> {
                awaitConcurrentStart(bothReady, startTogether);
                return service.updateAccountStatus(
                        firstAdminId,
                        secondAdminId,
                        new UpdateAccountStatusRequest(false, "Concurrent suspension one")
                );
            });
            Future<UpdateAccountStatusResponse> secondAttempt = executor.submit(() -> {
                awaitConcurrentStart(bothReady, startTogether);
                return service.updateAccountStatus(
                        secondAdminId,
                        firstAdminId,
                        new UpdateAccountStatusRequest(false, "Concurrent suspension two")
                );
            });

            assertThat(bothReady.await(10, TimeUnit.SECONDS)).isTrue();
            startTogether.countDown();

            List<Future<UpdateAccountStatusResponse>> attempts =
                    List.of(firstAttempt, secondAttempt);
            int successes = 0;
            int protectedRejections = 0;
            for (Future<UpdateAccountStatusResponse> attempt : attempts) {
                try {
                    UpdateAccountStatusResponse response = attempt.get(20, TimeUnit.SECONDS);
                    assertThat(response.changed()).isTrue();
                    assertThat(response.active()).isFalse();
                    successes++;
                } catch (ExecutionException exception) {
                    if (exception.getCause() instanceof ProtectedAccountOperationException) {
                        protectedRejections++;
                    } else {
                        throw exception;
                    }
                }
            }

            assertThat(successes).isOne();
            assertThat(protectedRejections).isOne();
            boolean firstAdminActive = readActive(firstAdminId);
            boolean secondAdminActive = readActive(secondAdminId);
            assertThat(List.of(firstAdminActive, secondAdminActive))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(countActiveAdmins()).isOne();
            assertThat(countAuditsForTargets(firstAdminId, secondAdminId)).isOne();

            Long successfulActorId = firstAdminActive ? firstAdminId : secondAdminId;
            Long suspendedAdminId = firstAdminActive ? secondAdminId : firstAdminId;
            String successfulReason = firstAdminActive
                    ? "Concurrent suspension one"
                    : "Concurrent suspension two";
            AuditRow audit = singleAuditForTarget(suspendedAdminId);
            assertThat(audit.adminUserId()).isEqualTo(successfulActorId);
            assertThat(audit.actionPerformed()).isEqualTo("ACCOUNT_STATUS_CHANGED");
            assertThat(audit.targetEntity()).isEqualTo("USER_ACCOUNT");
            assertAuditDetails(audit.details(), suspendedAdminId, true, false, successfulReason);
        } finally {
            startTogether.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private void assertAuditDetails(
            String details,
            Long targetUserId,
            boolean previousActive,
            boolean newActive,
            String reason
    ) throws Exception {
        JsonNode json = objectMapper.readTree(details);
        assertThat(json).hasSize(4);
        assertThat(json.get("targetUserId").longValue()).isEqualTo(targetUserId);
        assertThat(json.get("previousActive").booleanValue()).isEqualTo(previousActive);
        assertThat(json.get("newActive").booleanValue()).isEqualTo(newActive);
        assertThat(json.get("reason").textValue()).isEqualTo(reason);
    }

    private static void assertAuditForeignKeyFailure(Throwable failure) {
        assertThat(failure).isNotNull();
        Throwable rootCause = failure;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        assertThat(rootCause).isInstanceOf(SQLIntegrityConstraintViolationException.class);
        assertThat(rootCause.getMessage()).contains("fk_audit_admin");
    }

    private static void awaitConcurrentStart(
            CountDownLatch bothReady,
            CountDownLatch startTogether
    ) throws InterruptedException {
        bothReady.countDown();
        if (!startTogether.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent account-status start timed out");
        }
    }

    private Long insertAccount(String role, boolean active) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int inserted = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO users (role_id, email, password_hash, is_active)
                    VALUES ((SELECT id FROM roles WHERE name = ?), ?, ?, ?)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, role);
            statement.setString(
                    2,
                    "uc13.phase7." + UUID.randomUUID() + "@example.com"
            );
            statement.setString(3, PASSWORD_HASH);
            statement.setBoolean(4, active);
            return statement;
        }, keyHolder);

        assertThat(inserted).isOne();
        Long userId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        testUserIds.add(userId);
        return userId;
    }

    private void createRefreshSession(Long userId) {
        transactionTemplate.executeWithoutResult(
                status -> refreshTokenService.createSession(userId)
        );
    }

    private void isolateExistingAdmins() {
        adminStatesToRestore.addAll(jdbcTemplate.query(
                """
                SELECT u.id, u.is_active
                FROM users u
                JOIN roles r ON r.id = u.role_id
                WHERE r.name = 'ADMIN'
                ORDER BY u.id
                """,
                (resultSet, rowNumber) -> new AdminState(
                        resultSet.getLong("id"),
                        resultSet.getBoolean("is_active")
                )
        ));
        jdbcTemplate.update(
                """
                UPDATE users u
                JOIN roles r ON r.id = u.role_id
                SET u.is_active = false
                WHERE r.name = 'ADMIN'
                """
        );
    }

    private Long missingActorUserId() {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(id), 0) + 1000000 FROM users",
                Long.class
        );
    }

    private boolean readActive(Long userId) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT is_active FROM users WHERE id = ?",
                Boolean.class,
                userId
        ));
    }

    private LocalDateTime readUpdatedAt(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT updated_at FROM users WHERE id = ?",
                LocalDateTime.class,
                userId
        );
    }

    private long countRefreshTokens(Long userId) {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?",
                Long.class,
                userId
        ));
    }

    private long countAuditsForTarget(Long targetUserId) {
        return countAuditsForTargets(targetUserId);
    }

    private long countAuditsForTargets(Long... targetUserIds) {
        long count = 0;
        for (Long targetUserId : targetUserIds) {
            count += Objects.requireNonNull(jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM admin_audit_logs
                    WHERE JSON_UNQUOTE(JSON_EXTRACT(details, '$.targetUserId')) = ?
                    """,
                    Long.class,
                    targetUserId.toString()
            ));
        }
        return count;
    }

    private long countActiveAdmins() {
        return Objects.requireNonNull(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM users u
                JOIN roles r ON r.id = u.role_id
                WHERE r.name = 'ADMIN' AND u.is_active = true
                """,
                Long.class
        ));
    }

    private AuditRow singleAuditForTarget(Long targetUserId) {
        List<AuditRow> audits = jdbcTemplate.query(
                """
                SELECT id, admin_user_id, action_performed, target_entity,
                       details, ip_address, created_at
                FROM admin_audit_logs
                WHERE JSON_UNQUOTE(JSON_EXTRACT(details, '$.targetUserId')) = ?
                ORDER BY id
                """,
                (resultSet, rowNumber) -> new AuditRow(
                        resultSet.getLong("id"),
                        resultSet.getLong("admin_user_id"),
                        resultSet.getString("action_performed"),
                        resultSet.getString("target_entity"),
                        resultSet.getString("details"),
                        resultSet.getString("ip_address"),
                        resultSet.getTimestamp("created_at").toLocalDateTime()
                ),
                targetUserId.toString()
        );
        assertThat(audits).hasSize(1);
        return audits.getFirst();
    }

    private record AdminState(Long userId, boolean active) {
    }

    private record AuditRow(
            Long id,
            Long adminUserId,
            String actionPerformed,
            String targetEntity,
            String details,
            String ipAddress,
            LocalDateTime createdAt
    ) {
    }
}
