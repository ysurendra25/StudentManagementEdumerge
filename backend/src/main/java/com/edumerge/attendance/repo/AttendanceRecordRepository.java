package com.edumerge.attendance.repo;

import com.edumerge.attendance.model.AttendanceRecord;
import com.edumerge.attendance.model.AttendanceRecordId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, AttendanceRecordId> {
    List<AttendanceRecord> findBySessionIdOrderByStudentId(Long sessionId);
    AttendanceRecord findBySessionIdAndStudentId(Long sessionId, Long studentId);
    List<AttendanceRecord> findByStudentId(Long studentId);
}
