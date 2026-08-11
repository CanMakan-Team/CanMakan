package com.canmakan.backend.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence access for user accounts and the minimal role lookup required by
 * public registration.
 * 
 * @author Amelia
 * @author YangMaowei
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    // Check if a user with the given email exists
    boolean existsByEmail(String email);

    // Find a user by email
    Optional<UserAccount> findByEmail(String email);

    // Find the role ID by name
    @Query(value = "select id from roles where name = :roleName", nativeQuery = true)
    Optional<Long> findRoleIdByName(@Param("roleName") String roleName);

    @Query(value = """
        select u.id as userId,
               u.email as email,
               u.password_hash as passwordHash,
               u.is_active as active,
               r.name as roleName
        from users u
        join roles r on r.id = u.role_id
        where u.email = :email
        """, nativeQuery = true)
    Optional<AuthenticationAccountView> findAuthenticationAccountByEmail(
        @Param("email") String email
    );

    @Query(value = """
        select u.id as userId,
               u.email as email,
               u.password_hash as passwordHash,
               u.is_active as active,
               r.name as roleName
        from users u
        join roles r on r.id = u.role_id
        where u.id = :userId
        """, nativeQuery = true)
    Optional<AuthenticationAccountView> findAuthenticationAccountById(
        @Param("userId") Long userId
    );

    @Query(value = """
        select u.id as userId,
               u.email as email,
               r.name as role,
               u.is_active as active,
               u.updated_at as updatedAt
        from users u
        join roles r on r.id = u.role_id
        where (:query is null or lower(u.email) like concat('%', lower(:query), '%'))
          and (:role is null or r.name = :role)
          and (:active is null or u.is_active = :active)
        order by u.id asc
        """, nativeQuery = true)
    List<AdminUserSummaryView> findAdminUserSummaries(
        @Param("query") String query,
        @Param("role") String role,
        @Param("active") Boolean active
    );

    @Query(value = "select u.* from users u where u.id = :userId for update", nativeQuery = true)
    Optional<UserAccount> findByIdForUpdate(@Param("userId") Long userId);

    @Query(value = """
        select u.*
        from users u
        join roles r on r.id = u.role_id
        where r.name = 'ADMIN'
        order by u.id asc
        for update
        """, nativeQuery = true)
    List<UserAccount> findAllAdminsForUpdate();

    // Find the role name by ID
    @Query(value = "select name from roles where id = :roleId", nativeQuery = true)
    Optional<String> findRoleNameById(@Param("roleId") Long roleId);
}
