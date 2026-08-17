package com.canmakan.backend.admin.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.canmakan.backend.admin.model.AdminAuditLog;

/** Persistence access for administrative audit records. */
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
}
