package com.edumerge.attendance.model;

import jakarta.persistence.*;

@Entity
public class Faculty {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false)
    public String name;

    @Column(name = "dept_id", nullable = false)
    public Long deptId;

    /** 'faculty' or 'hod' */
    @Column(nullable = false)
    public String role = "faculty";
}
