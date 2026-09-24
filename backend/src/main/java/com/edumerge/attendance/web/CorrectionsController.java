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

/**
 * Correction workflow: faculty raises, HOD decides, everyone sees the audit.
 * The attendance row is only ever changed through an approved correction.
 */
@RestController
@RequestMapping("/api/corrections")
public class CorrectionsController {

    private final CorrectionRepository corrections;
    private final AttendanceSessionRepository sessions;
    private final AttendanceRecordRepository records;
    private final TimetableSlotRepository slots;
    private final StudentRepository students;
    private final SubjectRepository subjects;
    private final AttendanceService service;

    public CorrectionsController(CorrectionRepository corrections, AttendanceSessionRepository sessions,
                                 AttendanceRecordRepository records, TimetableSlotRepository slots,
                                 StudentRepository students, SubjectRepository subjects,
                                 AttendanceService service) {
        this.corrections = corrections; this.sessions = sessions; this.records = records;
        this.slots = slots; this.students = students; this.subjects = subjects; this.service = service;
    }

    /** Approval queue (hod/admin). */
    @GetMapping
    public Map<String, Object> list(@RequestParam(defaultValue = "pending") String status) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] r : corrections.findWithDetails(status)) {
            // c.id, session_id, student_id, old, new, category, reason, requested_by,
            // status, decided_by, decided_at, created_at, st_name, roll_no, sub_code, date, period, requester
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r[0]);
            m.put("sessionId", r[1]);
            m.put("studentId", r[2]);
            m.put("oldStatus", r[3]);
            m.put("newStatus", r[4]);
            m.put("category", r[5]);
            m.put("reason", r[6]);
            m.put("status", r[8]);
            m.put("decidedAt", r[10] == null ? null : r[10].toString());
            m.put("createdAt", r[11] == null ? null : r[11].toString());
            m.put("studentName", r[12]);
            m.put("rollNo", r[13]);
            m.put("subCode", r[14]);
            m.put("date", r[15] == null ? null : r[15].toString());
            m.put("period", r[16]);
            m.put("requester", r[17]);
            rows.add(m);
        }
        long pending = rows.stream().filter(m -> m.get("status").equals("pending")).count();
        return Map.of("rows", rows, "pendingCount",
                status.equals("all") ? corrections.findWithDetails("pending").size() : (int) pending);
    }

    /** Options for the faculty raise-form: their recently marked sessions. */
    @GetMapping("/raise-options")
    public List<Map<String, Object>> raiseOptions(HttpServletRequest request) {
        SessionUser u = SessionUser.from(request);
        Map<Long, String> codes = new HashMap<>();
        subjects.findAll().forEach(s -> codes.put(s.id, s.code));
        List<Map<String, Object>> out = new ArrayList<>();
        for (AttendanceSession s : sessions.findByMarkedByOrderByDateDesc(u.refId())) {
            TimetableSlot t = slots.findById(s.timetableId).orElse(null);
            if (t == null) continue;
            out.add(Map.of("id", s.id, "date", s.date.toString(),
                    "subCode", codes.get(t.subjectId), "section", t.section, "period", t.period));
            if (out.size() >= 30) break;
        }
        return out;
    }

    /** Roster (with current statuses) of one of my sessions, for the raise-form. */
    @GetMapping("/raise-roster/{sessionId}")
    public Map<String, Object> raiseRoster(@PathVariable Long sessionId, HttpServletRequest request) {
        SessionUser u = SessionUser.from(request);
        AttendanceSession s = sessions.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        if (!s.markedBy.equals(u.refId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your session.");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AttendanceRecord r : records.findBySessionIdOrderByStudentId(s.id)) {
            Student st = students.findById(r.studentId).orElse(null);
            if (st == null) continue;
            rows.add(Map.of("id", st.id, "rollNo", st.rollNo, "name", st.name, "status", r.status));
        }
        return Map.of("sessionId", s.id, "date", s.date.toString(), "roster", rows);
    }

    /** Faculty (or student) raises a correction. */
    @PostMapping
    @Transactional
    public Map<String, Object> raise(@RequestBody Dtos.CorrectionRequest body, HttpServletRequest request) {
        SessionUser u = SessionUser.from(request);
        if (body.reason() == null || body.reason().trim().length() < 10) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A reason of at least 10 characters is required.");
        }
        if (body.newStatus() == null || !List.of("P", "A", "L").contains(body.newStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status.");
        }
        AttendanceSession s = sessions.findById(body.sessionId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        if (!s.markedBy.equals(u.refId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only correct your own sessions.");
        }
        AttendanceRecord rec = records.findBySessionIdAndStudentId(s.id, body.studentId());
        if (rec == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No attendance row for that student.");
        }
        if (rec.status.equals(body.newStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New status is the same as current.");
        }
        Correction c = new Correction();
        c.sessionId = s.id;
        c.studentId = body.studentId();
        c.oldStatus = rec.status;
        c.newStatus = body.newStatus();
        c.category = body.category() == null ? "other" : body.category();
        c.reason = body.reason().trim();
        c.requestedBy = u.id();
        c.createdAt = LocalDateTime.now().withNano(0);
        corrections.save(c);
        service.audit(u.username(), "CORRECTION_REQUESTED",
                "session=%d student=%d %s->%s (%s)".formatted(s.id, body.studentId(), rec.status,
                        body.newStatus(), c.category));
        return Map.of("message", "Correction request submitted to the HOD for approval.");
    }

    /** HOD/admin approves or rejects. */
    @PostMapping("/{id}/decision")
    @Transactional
    public Map<String, Object> decide(@PathVariable Long id, @RequestBody Dtos.DecisionRequest body,
                                      HttpServletRequest request) {
        SessionUser u = SessionUser.from(request);
        if (!"approve".equals(body.decision()) && !"reject".equals(body.decision())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "decision must be approve|reject");
        }
        Correction c = corrections.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Correction not found"));
        if (!"pending".equals(c.status)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "That correction was already decided.");
        }
        boolean approved = "approve".equals(body.decision());
        c.status = approved ? "approved" : "rejected";
        c.decidedBy = u.id();
        c.decidedAt = LocalDateTime.now().withNano(0);
        if (approved) {
            AttendanceRecord rec = new AttendanceRecord();
            rec.sessionId = c.sessionId;
            rec.studentId = c.studentId;
            rec.status = c.newStatus;
            records.save(rec); // upsert via IdClass
            service.audit(u.username(), "CORRECTION_APPROVED",
                    "correction=%d student=%d session=%d %s->%s".formatted(id, c.studentId, c.sessionId,
                            c.oldStatus, c.newStatus));
        } else {
            service.audit(u.username(), "CORRECTION_REJECTED",
                    "correction=%d student=%d".formatted(id, c.studentId));
        }
        corrections.save(c);
        return Map.of("message", approved
                ? "Correction approved and applied. Low-attendance status for this student is recomputed automatically."
                : "Correction rejected.");
    }
}
