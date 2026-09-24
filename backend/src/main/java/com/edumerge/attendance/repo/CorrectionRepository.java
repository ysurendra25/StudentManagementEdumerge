package com.edumerge.attendance.repo;

import com.edumerge.attendance.model.Correction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CorrectionRepository extends JpaRepository<Correction, Long> {

    @Query(value = "SELECT c.id, c.session_id, c.student_id, c.old_status, c.new_status, c.category, " +
            "c.reason, c.requested_by, c.status, c.decided_by, c.decided_at, c.created_at, " +
            "st.name AS st_name, st.roll_no, sub.code AS sub_code, s.date, t.period, " +
            "ur.username AS requester " +
            "FROM corrections c " +
            "JOIN students st ON c.student_id = st.id " +
            "JOIN sessions s ON c.session_id = s.id " +
            "JOIN timetable t ON s.timetable_id = t.id " +
            "JOIN subjects sub ON t.subject_id = sub.id " +
            "JOIN users ur ON c.requested_by = ur.id " +
            "WHERE (:status = 'all' OR c.status = :status) " +
            "ORDER BY c.created_at DESC",
            nativeQuery = true)
    List<Object[]> findWithDetails(@Param("status") String status);

    List<Correction> findByStudentIdOrderByCreatedAtDesc(Long studentId);
}
