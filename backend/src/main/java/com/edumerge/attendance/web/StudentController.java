package com.edumerge.attendance.web;

import com.edumerge.attendance.config.SessionUser;
import com.edumerge.attendance.model.*;
import com.edumerge.attendance.repo.*;
import com.edumerge.attendance.service.AttendanceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

/** Student self-service + admin drill-down into any student. */
@RestController
public class StudentController {

    private final StudentRepository students;
    private final DepartmentRepository departments;
    private final AttendanceSessionRepository sessions;
    private final AttendanceRecordRepository records;
    private final TimetableSlotRepository slots;
    private final SubjectRepository subjects;
    private final CorrectionRepository corrections;
    private final NotificationRepository notifications;
    private final AttendanceService service;

    public StudentController(StudentRepository students, DepartmentRepository departments,
                             AttendanceSessionRepository sessions, AttendanceRecordRepository records,
                             TimetableSlotRepository slots, SubjectRepository subjects,
                             CorrectionRepository corrections, NotificationRepository notifications,
                             AttendanceService service) {
        this.students = students; this.departments = departments; this.sessions = sessions;
        this.records = records; this.slots = slots; this.subjects = subjects;
        this.corrections = corrections; this.notifications = notifications; this.service = service;
    }

    @GetMapping("/api/student/me")
    public Map<String, Object> me(HttpServletRequest request) {
        SessionUser u = SessionUser.from(request);
        return detail(u.refId());
    }

    @PostMapping("/api/student/correction")
    @Transactional
    public Map<String, Object> raise(@RequestBody Dtos.StudentCorrectionRequest body,
                                     HttpServletRequest request) {
        SessionUser u = SessionUser.from(request);
        if (body.reason() == null || body.reason().trim().length() < 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Describe the reason (min 10 characters).");
        }
        AttendanceRecord rec = records.findBySessionIdAndStudentId(body.sessionId(), u.refId());
        if (rec == null || rec.status.equals("P")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Pick a session you were marked absent or late for.");
        }
        Correction c = new Correction();
        c.sessionId = body.sessionId();
        c.studentId = u.refId();
        c.oldStatus = rec.status;
        c.newStatus = "P";
        c.category = body.category() == null ? "other" : body.category();
        c.reason = body.reason().trim();
        c.requestedBy = u.id();
        c.createdAt = LocalDateTime.now().withNano(0);
        corrections.save(c);
        service.audit(u.username(), "CORRECTION_REQUESTED",
                "session=%d student=%d %s->P (student)".formatted(body.sessionId(), u.refId(), rec.status));
        return Map.of("message",
                "Correction request submitted. Your class faculty and HOD will review it.");
    }

    /** Full detail for one student (admin/HOD). */
    @GetMapping("/api/students/{id}")
    public Map<String, Object> student(@PathVariable Long id) {
        return detail(id);
    }

    private Map<String, Object> detail(Long studentId) {
        Student st = students.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found"));
        Department dept = departments.findById(st.deptId).orElse(null);

        long held = 0, present = 0;
        for (Object[] r : sessions.subjectWise(studentId)) {
            held += ((Number) r[2]).longValue();
            present += ((Number) r[3]).longValue();
        }

        List<Map<String, Object>> subs = new ArrayList<>();
        for (Object[] r : sessions.subjectWise(studentId)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", r[0]); m.put("name", r[1]);
            m.put("held", ((Number) r[2]).longValue());
            m.put("present", ((Number) r[3]).longValue());
            m.put("pct", ((Number) r[2]).longValue() == 0 ? 0 :
                    Math.round(1000.0 * ((Number) r[3]).longValue() / ((Number) r[2]).longValue()) / 10.0);
            subs.add(m);
        }

        // recent history
        List<AttendanceRecord> recs = records.findByStudentId(studentId);
        Map<Long, AttendanceSession> sessMap = new HashMap<>();
        for (AttendanceRecord r : recs) sessions.findById(r.sessionId).ifPresent(s -> sessMap.put(s.id, s));
        List<Map<String, Object>> history = new ArrayList<>();
        recs.stream()
                .sorted((x, y) -> {
                    AttendanceSession sx = sessMap.get(x.sessionId), sy = sessMap.get(y.sessionId);
                    int c = sy.date.compareTo(sx.date);
                    return c != 0 ? c : Long.compare(
                            slots.findById(sy.timetableId).map(t -> t.period).orElse(0),
                            slots.findById(sx.timetableId).map(t -> t.period).orElse(0));
                })
                .limit(15)
                .forEach(r -> {
                    AttendanceSession s = sessMap.get(r.sessionId);
                    TimetableSlot t = slots.findById(s.timetableId).orElse(null);
                    if (t == null) return;
                    Subject sub = subjects.findById(t.subjectId).orElse(null);
                    history.add(Map.of("sessionId", r.sessionId, "date", s.date.toString(),
                            "period", t.period, "subCode", sub == null ? "?" : sub.code, "status", r.status));
                });

        List<Map<String, Object>> corr = new ArrayList<>();
        for (Correction c : corrections.findByStudentIdOrderByCreatedAtDesc(studentId)) {
            corr.add(Map.of("id", c.id, "oldStatus", c.oldStatus, "newStatus", c.newStatus,
                    "category", c.category, "reason", c.reason, "status", c.status,
                    "createdAt", c.createdAt.toString()));
            if (corr.size() >= 5) break;
        }

        List<Map<String, Object>> notifs = new ArrayList<>();
        for (Notification n : notifications.findTop5ByStudentIdOrderByCreatedAtDesc(studentId)) {
            notifs.add(Map.of("createdAt", n.createdAt.toString(), "channel", n.channel, "message", n.message));
        }

        double pct = held == 0 ? 0 : 100.0 * present / held;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", st.id);
        out.put("name", st.name);
        out.put("rollNo", st.rollNo);
        out.put("section", st.section);
        out.put("semester", st.semester);
        out.put("dept", dept == null ? "?" : dept.code);
        out.put("held", held);
        out.put("present", present);
        out.put("pct", Math.round(pct * 10) / 10.0);
        out.put("threshold", service.thresholdPct());
        out.put("subjects", subs);
        out.put("history", history);
        out.put("corrections", corr);
        out.put("notifications", notifs);
        return out;
    }
}
