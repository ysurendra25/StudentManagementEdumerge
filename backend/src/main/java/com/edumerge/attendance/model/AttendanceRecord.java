package com.edumerge.attendance.model;

import jakarta.persistence.*;

/**
 * Current attendance state per (session, student): P / A / L.
 * The original value of any correction is preserved in the corrections row
 * and in the append-only audit log — this row holds only current state.
 */
@Entity
@Table(name = "attendance")
@IdClass(AttendanceRecordId.class)
public class AttendanceRecord {
    @Id
    @Column(name = "session_id", nullable = false)
    public Long sessionId;

    @Id
    @Column(name = "student_id", nullable = false)
    public Long studentId;

    /** 'P' present, 'A' absent, 'L' late/OD */
    @Column(nullable = false)
    public String status;
}
