package com.edumerge.attendance.repo;

import com.edumerge.attendance.model.TimetableSlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TimetableSlotRepository extends JpaRepository<TimetableSlot, Long> {
    List<TimetableSlot> findByFacultyIdAndDayOfWeekOrderByPeriod(Long facultyId, int dayOfWeek);
    List<TimetableSlot> findByFacultyId(Long facultyId);
    List<TimetableSlot> findByDayOfWeek(int dayOfWeek);
    long countByDeptIdAndSection(Long deptId, String section);
    long countByDeptIdAndSectionAndSubjectId(Long deptId, String section, Long subjectId);
}
