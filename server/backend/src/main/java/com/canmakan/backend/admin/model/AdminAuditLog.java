package com.canmakan.backend.admin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Administrative action persisted to the existing admin audit table. */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "admin_audit_logs")
public class AdminAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_user_id", nullable = false)
    private Long adminUserId;

    @Column(name = "action_performed", nullable = false, length = 255)
    private String actionPerformed;

    @Column(name = "target_entity", length = 50)
    private String targetEntity;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details")
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public AdminAuditLog(
            Long adminUserId,
            String actionPerformed,
            String targetEntity,
            String details,
            String ipAddress
    ) {
        this.adminUserId = adminUserId;
        this.actionPerformed = actionPerformed;
        this.targetEntity = targetEntity;
        this.details = details;
        this.ipAddress = ipAddress;
    }
}
