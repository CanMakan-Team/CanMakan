package com.canmakan.backend.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.canmakan.backend.admin.model.AdminAuditLog;
import com.canmakan.backend.admin.repository.AdminAuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class AdminAuditLogRepositoryTest {

    private static final String PASSWORD_HASH =
            "$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Y1b7q/Q9I95zW";

    private static final String DETAILS = """
            {
              "targetUserId": 123,
              "previousActive": true,
              "newActive": false,
              "reason": "Repeated misuse of the service"
            }
            """;

    @Autowired
    private AdminAuditLogRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void persistsAccountStatusAuditWithDatabaseTimestampAndNullableIp() throws Exception {
        Long adminUserId = insertAdminAccount();
        AdminAuditLog saved = repository.saveAndFlush(new AdminAuditLog(
                adminUserId,
                "ACCOUNT_STATUS_CHANGED",
                "USER_ACCOUNT",
                DETAILS,
                null
        ));

        Long auditId = saved.getId();
        entityManager.clear();

        AdminAuditLog persisted = repository.findById(auditId).orElseThrow();

        assertThat(persisted.getAdminUserId()).isEqualTo(adminUserId);
        assertThat(persisted.getActionPerformed()).isEqualTo("ACCOUNT_STATUS_CHANGED");
        assertThat(persisted.getTargetEntity()).isEqualTo("USER_ACCOUNT");
        assertThat(objectMapper.readTree(persisted.getDetails()))
                .isEqualTo(objectMapper.readTree(DETAILS));
        assertThat(persisted.getIpAddress()).isNull();
        assertThat(persisted.getCreatedAt()).isNotNull();
    }

    private Long insertAdminAccount() {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int inserted = jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    """
                    INSERT INTO users (role_id, email, password_hash, is_active)
                    VALUES ((SELECT id FROM roles WHERE name = 'ADMIN'), ?, ?, true)
                    """,
                    Statement.RETURN_GENERATED_KEYS
            );
            statement.setString(1, "phase3.audit." + UUID.randomUUID() + "@example.com");
            statement.setString(2, PASSWORD_HASH);
            return statement;
        }, keyHolder);

        assertThat(inserted).isOne();
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }
}
