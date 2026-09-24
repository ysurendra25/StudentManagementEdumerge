package com.edumerge.attendance.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Simulated outbound notification (SMS/email) to a student's parents. */
@Entity
public class Notification {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "student_id", nullable = false)
    public Long studentId;

    @Column(nullable = false)
    public String channel;

    @Column(nullable = false)
    public String message;

    @Column(nullable = false)
    public String kind;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;
}
