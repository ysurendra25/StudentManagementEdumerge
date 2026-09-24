package com.edumerge.attendance.service;

import com.edumerge.attendance.model.*;
import com.edumerge.attendance.repo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Core attendance business rules: session marking, correction decisions and
 * the low-attendance (defaulters) engine.
 */
@Service
public class AttendanceService {

    private final TimetableSlotRepository slots;
    private final AttendanceSessionRepository sessions;
    private final AttendanceRecordRepository records;
    private final StudentRepository students;
    private final SubjectRepository subjects;
    private final DepartmentRepository departments;
    private final CorrectionRepository corrections;
    private final NotificationRepository notifications;
    private final AuditLogRepository auditLog;

    @Value("${app.threshold:75.0}")
    private double thresholdPct;

    @Value("${app.critical-threshold:65.0}")
    private double criticalPct;

    @Value("${app.semester-end:2026-12-11}")
    private String semesterEnd;

    @Value("${app.holidays:}")
    private String holidaysCsv;

    public AttendanceService(TimetableSlotRepository slots, AttendanceSessionRepository sessions,
                            AttendanceRecordRepository records, StudentRepository students,
                            SubjectRepository subjects, DepartmentRepository departments,
                            CorrectionRepository corrections, NotificationRepository notifications,
                            AuditLogRepository auditLog) {
        this.slots = slots; this.sessions = sessions; this.records = records;
        this.students = students; this.subjects = subjects; this.departments = departments;
        this.corrections = corrections; this.notifications = notifications; this.auditLog = auditLog;
    }

    // --- configuration helpers --------------------------------------------------

    public double thresholdPct() { return thresholdPct; }

    public Set<LocalDate> holidays() {
        Set<LocalDate> out = new HashSet<>();
        for (String s : holidaysCsv.split(",")) if (!s.isBlank()) out.add(LocalDate.parse(s.trim()));
        return out;
    }

    public boolean isHoliday(LocalDate d) { return holidays().contains(d); }

    /** Teaching dates in the last N days (weekday, not a holiday). */
    public List<LocalDate> expectedDates(int daysBack) {
        List<LocalDate> out = new ArrayList<>();
        LocalDate d = LocalDate.now().minusDays(daysBack);
        while (d.isBefore(LocalDate.now())) {
            if (d.getDayOfWeek().getValue() <= 5 && !isHoliday(d)) out.add(d);
            d = d.plusDays(1);
        }
        return out;
    }

    // --- marking ---------------------------------------------------------------

    @Transactional
    public Map<String, Object> mark(Long slotId, LocalDate date, Map<String, String> statuses,
                                     Long facultyUserId, Long facultyId, String actor) {
        TimetableSlot slot = slots.findById(slotId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found"));
        if (!slot.facultyId.equals(facultyId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Only the faculty allotted to this slot can mark it.");
        }
        if (date.isAfter(LocalDate.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot mark attendance for a future date.");
        }
        if (isHoliday(date)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That date is a holiday — no session expected.");
        }
        if (sessions.findByTimetableIdAndDate(slotId, date) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Attendance for this session was already submitted. Use the correction workflow to change any entry.");
        }
        // roster must match the cohort exactly
        Subject subject = subjects.findById(slot.subjectId).orElseThrow();
        List<Student> roster = students.findByDeptIdAndSectionAndSemesterAndActiveTrueOrderByRollNo(
                slot.deptId, slot.section, subject.semester);
        Set<Long> rosterIds = new HashSet<>();
        roster.forEach(s -> rosterIds.add(s.id));

        int p = 0, a = 0, l = 0;
        AttendanceSession session = new AttendanceSession();
        session.timetableId = slotId;
        session.date = date;
        session.status = "submitted";
        session.markedBy = facultyId;
        session.markedAt = LocalDateTime.now().withNano(0);
        sessions.save(session);

        List<AttendanceRecord> rows = new ArrayList<>();
        for (Student s : roster) {
            String v = statuses.get(String.valueOf(s.id));
            String st = (v != null && (v.equals("P") || v.equals("A") || v.equals("L"))) ? v : "P";
            switch (st) { case "A" -> a++; case "L" -> l++; default -> p++; }
            AttendanceRecord r = new AttendanceRecord();
            r.sessionId = session.id;
            r.studentId = s.id;
            r.status = st;
            rows.add(r);
        }
        records.saveAll(rows);

        audit(actor, "SESSION_MARKED", "session=%d timetable=%d date=%s P=%d A=%d L=%d"
                .formatted(session.id, slotId, date, p, a, l));
        return Map.of("sessionId", session.id, "present", p, "absent", a, "late", l);
    }

    // --- defaulters engine --------------------------------------------------------

    /**
     * Sessions to attend consecutively to reach the threshold:
     * (P + x) / (H + x) >= T  =>  x = ceil((T*H - P) / (1 - T))
     */
    public static int classesNeeded(long present, long held, double thresholdFraction) {
        if (held == 0) return 0;
        if (present >= thresholdFraction * held) return 0;
        return (int) Math.ceil((thresholdFraction * held - present) / (1 - thresholdFraction));
    }

    public int remainingEstimate(Long deptId, String section) {
        long perWeek = slots.countByDeptIdAndSection(deptId, section);
        long weeks = Math.max(0, LocalDate.parse(semesterEnd).toEpochDay() - LocalDate.now().toEpochDay()) / 7;
        return (int) (weeks * perWeek);
    }

    public List<Map<String, Object>> defaulters(Double thresholdOverride, Long deptId, String section) {
        double t = (thresholdOverride != null ? thresholdOverride : thresholdPct);
        double fraction = t / 100.0;
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object[] row : sessions.studentAggregates(deptId, section)) {
            long held = ((Number) row[7]).longValue();
            long present = ((Number) row[8]).longValue();
            double pct = held == 0 ? 0 : 100.0 * present / held;
            if (held > 0 && pct < t) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", row[0]);
                m.put("rollNo", row[1]);
                m.put("name", row[2]);
                m.put("section", row[3]);
                m.put("deptId", row[5]);
                m.put("dept", row[6]);
                m.put("held", held);
                m.put("present", present);
                m.put("pct", Math.round(pct * 10) / 10.0);
                m.put("critical", pct < criticalPct);
                int need = classesNeeded(present, held, fraction);
                int remaining = remainingEstimate(((Number) row[5]).longValue(), (String) row[3]);
                m.put("need", need);
                m.put("remaining", remaining);
                m.put("recoverable", need <= remaining);
                Notification n = notifications.findFirstByStudentIdAndKindOrderByCreatedAtDesc(
                        ((Number) row[0]).longValue(), "low_attendance");
                m.put("lastNotified", n == null ? null : n.createdAt.toString());
                out.add(m);
            }
        }
        return out;
    }

    // --- shared -----------------------------------------------------------------

    @Transactional
    public void audit(String actor, String action, String details) {
        AuditLog log = new AuditLog();
        log.ts = LocalDateTime.now().withNano(0);
        log.actor = actor == null ? "system" : actor;
        log.action = action;
        log.details = details;
        auditLog.save(log);
    }
}
