package com.edumerge.attendance.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "corrections")
public class Correction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "session_id", nullable = false)
    public Long sessionId;

    @Column(name = "student_id", nullable = false)
    public Long studentId;

    @Column(name = "old_status", nullable = false)
    public String oldStatus;

    @Column(name = "new_status", nullable = false)
    public String newStatus;

    /** medical / sports_od / marked_in_error / other */
    @Column(nullable = false)
    public String category;

    @Column(nullable = false)
    public String reason;

    @Column(name = "proof_file")
    public String proofFile;

    @Column(name = "requested_by", nullable = false)
    public Long requestedBy;

    /** pending / approved / rejected */
    @Column(nullable = false)
    public String status = "pending";

    @Column(name = "decided_by")
    public Long decidedBy;

    @Column(name = "decided_at")
    public LocalDateTime decidedAt;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;
}
