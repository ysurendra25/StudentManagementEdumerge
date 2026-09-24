package com.edumerge.attendance.model;

import jakarta.persistence.*;

@Entity
@Table(name = "students")
public class Student {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "roll_no", unique = true, nullable = false)
    public String rollNo;

    @Column(nullable = false)
    public String name;

    @Column(name = "dept_id", nullable = false)
    public Long deptId;

    @Column(nullable = false)
    public String section;

    @Column(nullable = false)
    public int semester;

    @Column(name = "parent_email")
    public String parentEmail;

    @Column(nullable = false)
    public boolean active = true;
}
