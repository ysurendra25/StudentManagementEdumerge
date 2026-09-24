package com.edumerge.attendance.model;

import java.io.Serializable;

/** Composite key for AttendanceRecord. */
public class AttendanceRecordId implements Serializable {
    public Long sessionId;
    public Long studentId;

    public AttendanceRecordId() {}

    public AttendanceRecordId(Long sessionId, Long studentId) {
        this.sessionId = sessionId;
        this.studentId = studentId;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AttendanceRecordId that)) return false;
        return sessionId.equals(that.sessionId) && studentId.equals(that.studentId);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(sessionId, studentId);
    }
}
