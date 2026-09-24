package com.edumerge.attendance.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Append-only audit trail. Nothing in the app ever updates or deletes here. */
@Entity
@Table(name = "audit_log")
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public LocalDateTime ts;

    @Column(nullable = false)
    public String actor;

    @Column(nullable = false)
    public String action;

    @Column(nullable = false)
    public String details;
}
