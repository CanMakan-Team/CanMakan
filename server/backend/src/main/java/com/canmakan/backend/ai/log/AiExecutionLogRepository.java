package com.canmakan.backend.ai.log;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persists and queries {@code ai_execution_logs}.
 *
 * @author XieHuayuan
 */
public interface AiExecutionLogRepository extends JpaRepository<AiExecutionLog, Long> {
}
