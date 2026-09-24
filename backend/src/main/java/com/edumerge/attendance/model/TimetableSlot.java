package com.edumerge.attendance.model;

import jakarta.persistence.*;

/** A recurring timetable slot: dept + section + subject + faculty + weekday + period. */
@Entity
@Table(name = "timetable", uniqueConstraints = {
        @UniqueConstraint(name = "uq_slot", columnNames = {"dept_id", "section", "day_of_week", "period"})})
public class TimetableSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "dept_id", nullable = false)
    public Long deptId;

    @Column(nullable = false)
    public String section;

    @Column(name = "subject_id", nullable = false)
    public Long subjectId;

    @Column(name = "faculty_id", nullable = false)
    public Long facultyId;

    /** 0 = Monday ... 4 = Friday */
    @Column(name = "day_of_week", nullable = false)
    public int dayOfWeek;

    @Column(nullable = false)
    public int period;

    @Column(nullable = false)
    public String room;
}
