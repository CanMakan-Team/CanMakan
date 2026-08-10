package com.canmakan.backend.admin;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence access for administrative audit records. */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
}
