package com.edumerge.attendance.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One concrete held instance of a TimetableSlot on a date. */
@Entity
@Table(name = "sessions", uniqueConstraints = {
        @UniqueConstraint(name = "uq_session", columnNames = {"timetable_id", "date"})})
public class AttendanceSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "timetable_id", nullable = false)
    public Long timetableId;

    @Column(name = "date", nullable = false)
    public LocalDate date;

    @Column(nullable = false)
    public String status = "submitted";

    @Column(name = "marked_by", nullable = false)
    public Long markedBy;

    @Column(name = "marked_at", nullable = false)
    public LocalDateTime markedAt;
}
