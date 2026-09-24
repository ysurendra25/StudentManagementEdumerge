package com.edumerge.attendance.repo;

import com.edumerge.attendance.model.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubjectRepository extends JpaRepository<Subject, Long> {
    List<Subject> findByDeptIdAndSemester(Long deptId, int semester);
}
