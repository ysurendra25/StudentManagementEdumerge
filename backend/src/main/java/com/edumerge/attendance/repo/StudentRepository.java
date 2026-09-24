package com.edumerge.attendance.repo;

import com.edumerge.attendance.model.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentRepository extends JpaRepository<Student, Long> {
    List<Student> findByDeptIdAndSectionAndSemesterAndActiveTrueOrderByRollNo(
            Long deptId, String section, int semester);
}
