package com.edumerge.attendance.model;

import jakarta.persistence.*;

@Entity
@Table(name = "subjects")
public class Subject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(unique = true, nullable = false)
    public String code;

    @Column(nullable = false)
    public String name;

    @Column(name = "dept_id", nullable = false)
    public Long deptId;

    @Column(nullable = false)
    public int semester;
}
