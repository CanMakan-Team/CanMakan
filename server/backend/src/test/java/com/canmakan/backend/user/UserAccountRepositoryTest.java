package com.canmakan.backend.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.canmakan.backend.user.model.UserAccount;
import com.canmakan.backend.user.repository.UserAccountRepository;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@Transactional
@DisplayName("UC13: UserAccountRepository account listing")
class UserAccountRepositoryTest {

    private static final String PASSWORD_HASH =
            "$2a$10$8.UnVuG9HHgffUDAlk8qfOUVGkqRzgVymGe07xD0Y1b7q/Q9I95zW";

    @Autowired
    private UserAccountRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String marker;
    private String adminEmail;
    private String inactiveAdminEmail;
    private String activeUserEmail;
    private String inactiveUserEmail;
    private Long adminId;
    private Long inactiveAdminId;
    private Long activeUserId;
    private Long inactiveUserId;

    @BeforeEach
    void setUp() {
        marker = UUID.randomUUID().toString().replace("-", "");
        adminEmail = "Phase2.Admin." + marker + "@Example.COM";
        inactiveAdminEmail = "phase3.inactive.admin." + marker + "@example.com";
        activeUserEmail = "phase2.user." + marker + "@example.com";
        inactiveUserEmail = "phase2.inactive." + marker + "@example.com";

        adminId = insertAccount("ADMIN", adminEmail, true);
        inactiveAdminId = insertAccount("ADMIN", inactiveAdminEmail, false);
        activeUserId = insertAccount("USER", activeUserEmail, true);
        inactiveUserId = insertAccount("USER", inactiveUserEmail, false);
    }

    @Test
    @DisplayName("no filters return USER and ADMIN accounts ordered by user ID")
    void noFiltersReturnAllAccountsInDeterministicOrder() {
        List<AdminUserSummaryView> accounts = repository.findAdminUserSummaries(
                null,
                null,
                null
        );

        assertThat(accounts).extracting(account -> account.getUserId()).isSorted();
        assertThat(accounts).extracting(account -> account.getUserId())
                .contains(adminId, inactiveAdminId, activeUserId, inactiveUserId);
        assertThat(accounts).filteredOn(account -> account.getUserId().equals(adminId))
                .singleElement()
                .satisfies(account -> assertThat(account.getRole()).isEqualTo("ADMIN"));
        assertThat(accounts).filteredOn(account -> account.getUserId().equals(activeUserId))
                .singleElement()
                .satisfies(account -> assertThat(account.getRole()).isEqualTo("USER"));
    }

    @Test
    @DisplayName("email query uses case-insensitive contains matching")
    void emailQueryUsesCaseInsensitiveContainsMatching() {
        List<AdminUserSummaryView> accounts = repository.findAdminUserSummaries(
                ("PHASE2.ADMIN." + marker).toUpperCase(Locale.ROOT),
                null,
                null
        );

        assertThat(accounts).extracting(account -> account.getUserId())
                .containsExactly(adminId);
    }

    @Test
    @DisplayName("role filter returns only USER accounts")
    void roleFilterReturnsOnlyUsers() {
        List<AdminUserSummaryView> accounts = repository.findAdminUserSummaries(
                null,
                "USER",
                null
        );

        assertThat(accounts).isNotEmpty().allMatch(account -> "USER".equals(account.getRole()));
        assertThat(accounts).extracting(account -> account.getUserId())
                .contains(activeUserId, inactiveUserId)
                .doesNotContain(adminId, inactiveAdminId);
    }

    @Test
    @DisplayName("role filter returns only ADMIN accounts")
    void roleFilterReturnsOnlyAdmins() {
        List<AdminUserSummaryView> accounts = repository.findAdminUserSummaries(
                null,
                "ADMIN",
                null
        );

        assertThat(accounts).isNotEmpty().allMatch(account -> "ADMIN".equals(account.getRole()));
        assertThat(accounts).extracting(account -> account.getUserId())
                .contains(adminId, inactiveAdminId)
                .doesNotContain(activeUserId, inactiveUserId);
    }

    @Test
    @DisplayName("active filter distinguishes active and suspended accounts")
    void activeFilterDistinguishesAccountStatus() {
        List<AdminUserSummaryView> activeAccounts = repository.findAdminUserSummaries(
                null,
                null,
                true
        );
        List<AdminUserSummaryView> suspendedAccounts = repository.findAdminUserSummaries(
                null,
                null,
                false
        );

        assertThat(activeAccounts).isNotEmpty().allMatch(account -> account.getActive());
        assertThat(activeAccounts).extracting(account -> account.getUserId())
                .contains(adminId, activeUserId)
                .doesNotContain(inactiveAdminId, inactiveUserId);
        assertThat(suspendedAccounts).isNotEmpty()
                .allMatch(account -> !account.getActive());
        assertThat(suspendedAccounts).extracting(account -> account.getUserId())
                .contains(inactiveAdminId, inactiveUserId)
                .doesNotContain(adminId, activeUserId);
    }

    @Test
    @DisplayName("query, role, and active filters combine with AND semantics")
    void combinedFiltersUseAndSemantics() {
        List<AdminUserSummaryView> accounts = repository.findAdminUserSummaries(
                "INACTIVE." + marker,
                "USER",
                false
        );

        assertThat(accounts).extracting(account -> account.getUserId())
                .containsExactly(inactiveUserId);
    }

    @Test
    @DisplayName("filters with no match return an empty list")
    void noMatchReturnsEmptyList() {
        List<AdminUserSummaryView> accounts = repository.findAdminUserSummaries(
                "missing-" + marker,
                "ADMIN",
                true
        );

        assertThat(accounts).isEmpty();
    }

    @Test
    @DisplayName("projection exposes only the required account-list fields")
    void projectionContainsRequiredAccountFields() {
        AdminUserSummaryView account = repository.findAdminUserSummaries(
                adminEmail.toLowerCase(Locale.ROOT),
                null,
                null
        ).getFirst();

        assertThat(account.getUserId()).isEqualTo(adminId);
        assertThat(account.getEmail()).isEqualTo(adminEmail);
        assertThat(account.getRole()).isEqualTo("ADMIN");
        assertThat(account.getActive()).isTrue();
        assertThat(account.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("target row lock returns an existing account and no missing account")
    void targetRowLockReturnsExistingAccountAndEmptyForMissingId() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();

        assertThat(repository.findByIdForUpdate(activeUserId))
                .get()
                .extracting(account -> account.getId())
                .isEqualTo(activeUserId);
        assertThat(repository.findByIdForUpdate(Long.MAX_VALUE)).isEmpty();
    }

    @Test
    @DisplayName("ADMIN row lock includes active and inactive admins in user ID order")
    void adminRowLockReturnsAllAdminsInDeterministicOrder() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();

        Long adminRoleId = jdbcTemplate.queryForObject(
                "SELECT id FROM roles WHERE name = 'ADMIN'",
                Long.class
        );
        List<UserAccount> admins = repository.findAllAdminsForUpdate();

        assertThat(admins).allMatch(account -> account.getRoleId().equals(adminRoleId));
        assertThat(admins).extracting(account -> account.getId()).isSorted();
        assertThat(admins).extracting(account -> account.getId())
                .contains(adminId, inactiveAdminId)
                .doesNotContain(activeUserId, inactiveUserId);
        assertThat(admins).filteredOn(account -> account.getId().equals(adminId))
                .singleElement()
                .extracting(account -> account.isActive())
                .isEqualTo(true);
        assertThat(admins).filteredOn(account -> account.getId().equals(inactiveAdminId))
                .singleElement()
                .extracting(account -> account.isActive())
                .isEqualTo(false);
    }

    private Long insertAccount(String role, String email, boolean active) {
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
            statement.setString(2, email);
            statement.setString(3, PASSWORD_HASH);
            statement.setBoolean(4, active);
            return statement;
        }, keyHolder);

        assertThat(inserted).isOne();
        return Objects.requireNonNull(keyHolder.getKey()).longValue();
    }
}
