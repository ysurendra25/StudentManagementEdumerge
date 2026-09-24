package com.edumerge.attendance.repo;

import com.edumerge.attendance.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Notification findFirstByStudentIdAndKindOrderByCreatedAtDesc(Long studentId, String kind);
    List<Notification> findTop5ByStudentIdOrderByCreatedAtDesc(Long studentId);
}
