package com.edumerge.attendance.repo;

import com.edumerge.attendance.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    List<AuditLog> findTop150ByOrderByIdDesc();
    List<AuditLog> findTop8ByOrderByIdDesc();
    List<AuditLog> findTop12ByActionStartingWithOrderByIdDesc(String actionPrefix);
}
