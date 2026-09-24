package com.edumerge.attendance.web;

import com.edumerge.attendance.config.SessionUser;
import com.edumerge.attendance.model.AttendanceRecord;
import com.edumerge.attendance.model.AttendanceSession;
import com.edumerge.attendance.model.Department;
import com.edumerge.attendance.model.Student;
import com.edumerge.attendance.model.Subject;
import com.edumerge.attendance.model.TimetableSlot;
import com.edumerge.attendance.repo.AttendanceRecordRepository;
import com.edumerge.attendance.repo.AttendanceSessionRepository;
import com.edumerge.attendance.repo.DepartmentRepository;
import com.edumerge.attendance.repo.StudentRepository;
import com.edumerge.attendance.repo.SubjectRepository;
import com.edumerge.attendance.repo.TimetableSlotRepository;
import com.edumerge.attendance.service.AttendanceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

/**
 * Faculty endpoints: today's sessions, unmarked past sessions (escalation),
 * the roster for marking, and submitting a session's attendance.
 */
@RestController
@RequestMapping("/api/faculty")
public class FacultyController {

    private final TimetableSlotRepository slots;
    private final AttendanceSessionRepository sessions;
    private final AttendanceRecordRepository records;
    private final StudentRepository students;
    private final SubjectRepository subjects;
    private final DepartmentRepository departments;
    private final AttendanceService service;

    public FacultyController(TimetableSlotRepository slots, AttendanceSessionRepository sessions,
                             AttendanceRecordRepository records, StudentRepository students,
                             SubjectRepository subjects, DepartmentRepository departments,
                             AttendanceService service) {
        this.slots = slots; this.sessions = sessions; this.records = records;
        this.students = students; this.subjects = subjects; this.departments = departments;
        this.service = service;
    }

    /** Today's slots + past unmarked slots (14 teaching days) + recently marked sessions. */
    @GetMapping("/sessions")
    public Map<String, Object> facultySessions(HttpServletRequest request) {
        SessionUser u = SessionUser.from(request);
        Long fid = u.refId();
        LocalDate today = LocalDate.now();

        Map<Long, String> codes = new HashMap<>();
        Map<Long, String> names = new HashMap<>();
        for (Subject s : subjects.findAll()) { codes.put(s.id, s.code); names.put(s.id, s.name); }
        Map<Long, String> deptCodes = new HashMap<>();
        for (Department d : departments.findAll()) deptCodes.put(d.id, d.code);

        List<Map<String, Object>> todayList = new ArrayList<>();
        for (TimetableSlot t : slots.findByFacultyIdAndDayOfWeekOrderByPeriod(
                fid, today.getDayOfWeek().getValue() - 1)) {
            Map<String, Object> m = slotView(t, codes, names, deptCodes);
            m.put("marked", sessions.findByTimetableIdAndDate(t.id, today) != null);
            todayList.add(m);
        }

        // past unmarked slots -> escalation candidates (only this faculty's)
        List<Map<String, Object>> unmarked = new ArrayList<>();
        for (LocalDate d : service.expectedDates(14)) {
            for (Object[] r : sessions.findUnmarkedSlots(d.getDayOfWeek().getValue() - 1, d)) {
                long slotFacultyId = ((Number) r[6]).longValue();
                if (slotFacultyId != fid) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", ((Number) r[0]).longValue());
                m.put("period", ((Number) r[1]).intValue());
                m.put("room", r[2]);
                m.put("section", r[3]);
                m.put("subCode", r[8]);
                m.put("date", d.toString());
                unmarked.add(m);
            }
        }

        List<Map<String, Object>> recent = new ArrayList<>();
        for (AttendanceSession s : sessions.findByMarkedByOrderByDateDesc(fid)) {
            TimetableSlot t = slots.findById(s.timetableId).orElse(null);
            if (t == null) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.id);
            m.put("date", s.date.toString());
            m.put("period", t.period);
            m.put("subCode", codes.get(t.subjectId));
            m.put("section", t.section);
            List<AttendanceRecord> rows = records.findBySessionIdOrderByStudentId(s.id);
            m.put("p", rows.stream().filter(r -> r.status.equals("P")).count());
            m.put("a", rows.stream().filter(r -> r.status.equals("A")).count());
            m.put("l", rows.stream().filter(r -> r.status.equals("L")).count());
            recent.add(m);
            if (recent.size() >= 10) break;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("today", todayList);
        out.put("unmarked", unmarked);
        out.put("recent", recent);
        return out;
    }

    /** Roster for marking a slot on a date (defaults to Present). */
    @GetMapping("/roster/{slotId}/{date}")
    public Map<String, Object> roster(@PathVariable Long slotId,
                                      @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                      HttpServletRequest request) {
        SessionUser u = SessionUser.from(request);
        TimetableSlot slot = slots.findById(slotId)
                .orElseThrow(() -> new IllegalArgumentException("Slot not found"));
        if (!slot.facultyId.equals(u.refId())) {
            throw new IllegalStateException("Only the faculty allotted to this slot can mark it.");
        }
        if (date.isAfter(LocalDate.now())) throw new IllegalArgumentException("Cannot mark a future date.");
        if (service.isHoliday(date)) throw new IllegalArgumentException("That date is a holiday.");
        if (sessions.findByTimetableIdAndDate(slotId, date) != null) {
            throw new IllegalStateException("Attendance already submitted for this session — use corrections.");
        }
        Subject subject = subjects.findById(slot.subjectId).orElseThrow();
        Department dept = departments.findById(slot.deptId).orElseThrow();

        List<Map<String, Object>> roster = new ArrayList<>();
        for (Student s : students.findByDeptIdAndSectionAndSemesterAndActiveTrueOrderByRollNo(
                slot.deptId, slot.section, subject.semester)) {
            roster.add(Map.of("id", s.id, "rollNo", s.rollNo, "name", s.name));
        }
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("slotId", slot.id);
        view.put("date", date.toString());
        view.put("subCode", subject.code);
        view.put("subName", subject.name);
        view.put("dept", dept.code);
        view.put("section", slot.section);
        view.put("period", slot.period);
        view.put("room", slot.room);
        view.put("roster", roster);
        return view;
    }

    /** Submit attendance for a slot/date. Statuses default to Present. */
    @PostMapping("/mark/{slotId}/{date}")
    public Map<String, Object> mark(@PathVariable Long slotId,
                                    @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                    @RequestBody Dtos.MarkRequest body,
                                    HttpServletRequest request) {
        SessionUser u = SessionUser.from(request);
        return service.mark(slotId, date,
                body.statuses() == null ? Map.of() : body.statuses(),
                u.id(), u.refId(), u.username());
    }

    private Map<String, Object> slotView(TimetableSlot t, Map<Long, String> codes,
                                         Map<Long, String> names, Map<Long, String> deptCodes) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.id);
        m.put("period", t.period);
        m.put("subCode", codes.get(t.subjectId));
        m.put("subName", names.get(t.subjectId));
        m.put("dept", deptCodes.get(t.deptId));
        m.put("section", t.section);
        m.put("room", t.room);
        return m;
    }
}
