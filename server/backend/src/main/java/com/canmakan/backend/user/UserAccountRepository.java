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
}
