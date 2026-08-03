package com.canmakan.backend.ai.log;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Persists and queries {@code ai_execution_logs}.
 *
 * @author XieHuayuan
 */
public interface AiExecutionLogRepository extends JpaRepository<AiExecutionLog, Long> {

    /** All execution logs for a given scan. */
    List<AiExecutionLog> findByScanId(Long scanId);
}
