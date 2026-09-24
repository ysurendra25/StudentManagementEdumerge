package com.edumerge.attendance.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(unique = true, nullable = false)
    public String username;

    @Column(name = "password_hash", nullable = false)
    public String passwordHash;

    /** admin / hod / faculty / student */
    @Column(nullable = false)
    public String role;

    /** faculty.id or student.id */
    @Column(name = "ref_id")
    public Long refId;
}
