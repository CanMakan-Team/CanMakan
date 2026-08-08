package com.canmakan.backend.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence access for user accounts and the minimal role lookup required by
 * public registration.
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    boolean existsByEmail(String email);

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
}
