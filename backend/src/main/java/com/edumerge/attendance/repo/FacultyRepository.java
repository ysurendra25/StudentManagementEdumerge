package com.edumerge.attendance.repo;

import com.edumerge.attendance.model.Faculty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FacultyRepository extends JpaRepository<Faculty, Long> {
    List<Faculty> findByDeptId(Long deptId);
    Faculty findFirstByDeptIdAndRole(Long deptId, String role);
}
