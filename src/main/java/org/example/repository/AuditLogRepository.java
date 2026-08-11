package org.example.repository;

import org.example.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByUserId(Long userId);

    List<AuditLog> findByDbConnectionId(Long dbConnectionId);

    /** Audit trails are read newest-first. */
    List<AuditLog> findByUserIdOrderByPerformedAtDesc(Long userId);

    List<AuditLog> findByDbConnectionIdOrderByPerformedAtDesc(Long dbConnectionId);

    List<AuditLog> findByUserIdAndDbConnectionIdOrderByPerformedAtDesc(Long userId, Long dbConnectionId);
}
