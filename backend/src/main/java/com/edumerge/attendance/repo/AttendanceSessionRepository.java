package com.edumerge.attendance.repo;

import com.edumerge.attendance.model.AttendanceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, Long> {

    AttendanceSession findByTimetableIdAndDate(Long timetableId, LocalDate date);

    long countByDate(LocalDate date);

    List<AttendanceSession> findByMarkedByOrderByDateDesc(Long markedBy);

    /** Slots on a given weekday with no session recorded for the given date (unmarked). */
    @Query(value = "SELECT t.id, t.period, t.room, t.section, t.dept_id, t.subject_id, t.faculty_id, " +
            "f.name AS fac_name, sub.code AS sub_code " +
            "FROM timetable t " +
            "JOIN faculty f ON t.faculty_id = f.id " +
            "JOIN subjects sub ON t.subject_id = sub.id " +
            "WHERE t.day_of_week = :dow " +
            "AND NOT EXISTS (SELECT 1 FROM sessions x WHERE x.timetable_id = t.id AND x.date = :d)",
            nativeQuery = true)
    List<Object[]> findUnmarkedSlots(@Param("dow") int dow, @Param("d") LocalDate d);

    /** Aggregate attendance per student, optionally filtered by dept/section. */
    @Query(value = "SELECT st.id, st.roll_no, st.name, st.section, st.semester, st.dept_id, d.code AS dept, " +
            "COUNT(*) AS held, SUM(CASE WHEN a.status IN ('P','L') THEN 1 ELSE 0 END) AS present " +
            "FROM attendance a JOIN sessions s ON a.session_id = s.id " +
            "JOIN timetable t ON s.timetable_id = t.id " +
            "JOIN subjects sub ON t.subject_id = sub.id " +
            "JOIN students st ON a.student_id = st.id " +
            "JOIN departments d ON st.dept_id = d.id " +
            "WHERE st.active = TRUE " +
            "AND (:deptId IS NULL OR st.dept_id = :deptId) " +
            "AND (:section IS NULL OR st.section = :section) " +
            "GROUP BY st.id, st.roll_no, st.name, st.section, st.semester, st.dept_id, d.code " +
            "ORDER BY st.roll_no",
            nativeQuery = true)
    List<Object[]> studentAggregates(@Param("deptId") Long deptId, @Param("section") String section);

    /** Subject-wise attendance for one student. */
    @Query(value = "SELECT sub.code, sub.name, COUNT(*) AS held, " +
            "SUM(CASE WHEN a.status IN ('P','L') THEN 1 ELSE 0 END) AS present " +
            "FROM attendance a JOIN sessions s ON a.session_id = s.id " +
            "JOIN timetable t ON s.timetable_id = t.id " +
            "JOIN subjects sub ON t.subject_id = sub.id " +
            "WHERE a.student_id = :studentId " +
            "GROUP BY sub.id, sub.code, sub.name ORDER BY sub.code",
            nativeQuery = true)
    List<Object[]> subjectWise(@Param("studentId") Long studentId);

    /** Average attendance % within a date range [from, to). */
    @Query(value = "SELECT CAST(SUM(CASE WHEN a.status IN ('P','L') THEN 1 ELSE 0 END) AS DOUBLE) / COUNT(*) * 100 " +
            "FROM attendance a JOIN sessions s ON a.session_id = s.id " +
            "WHERE s.date >= :from AND s.date < :to",
            nativeQuery = true)
    Double avgAttendanceBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    /** Monthly attendance %: rows of (year, month, pct). */
    @Query(value = "SELECT EXTRACT(YEAR FROM s.date) AS y, EXTRACT(MONTH FROM s.date) AS m, " +
            "CAST(SUM(CASE WHEN a.status IN ('P','L') THEN 1 ELSE 0 END) AS DOUBLE) / COUNT(*) * 100 " +
            "FROM attendance a JOIN sessions s ON a.session_id = s.id " +
            "GROUP BY EXTRACT(YEAR FROM s.date), EXTRACT(MONTH FROM s.date) " +
            "ORDER BY y, m",
            nativeQuery = true)
    List<Object[]> monthlyTrend();

    /** Department-wise attendance %. */
    @Query(value = "SELECT d.code, d.name, " +
            "CAST(SUM(CASE WHEN a.status IN ('P','L') THEN 1 ELSE 0 END) AS DOUBLE) / COUNT(*) * 100 " +
            "FROM attendance a JOIN sessions s ON a.session_id = s.id " +
            "JOIN students st ON a.student_id = st.id " +
            "JOIN departments d ON st.dept_id = d.id " +
            "GROUP BY d.id, d.code, d.name ORDER BY d.code",
            nativeQuery = true)
    List<Object[]> deptWise();
}
