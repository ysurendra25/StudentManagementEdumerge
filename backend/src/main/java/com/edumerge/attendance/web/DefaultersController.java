package com.edumerge.attendance.web;

import com.edumerge.attendance.model.Department;
import com.edumerge.attendance.model.Notification;
import com.edumerge.attendance.model.Student;
import com.edumerge.attendance.repo.DepartmentRepository;
import com.edumerge.attendance.repo.NotificationRepository;
import com.edumerge.attendance.repo.StudentRepository;
import com.edumerge.attendance.service.AttendanceService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/** Low-attendance defaulters: report, CSV export, parent notifications. */
@RestController
@RequestMapping("/api/defaulters")
public class DefaultersController {

    private final AttendanceService service;
    private final DepartmentRepository departments;
    private final StudentRepository students;
    private final NotificationRepository notifications;

    public DefaultersController(AttendanceService service, DepartmentRepository departments,
                                StudentRepository students, NotificationRepository notifications) {
        this.service = service; this.departments = departments;
        this.students = students; this.notifications = notifications;
    }

    private List<Map<String, Object>> rows(Double threshold, Long dept, String section) {
        return service.defaulters(threshold, dept, (section == null || section.isBlank()) ? null : section);
    }

    @GetMapping
    public Map<String, Object> report(@RequestParam(required = false) Double threshold,
                                      @RequestParam(required = false) Long dept,
                                      @RequestParam(required = false) String section) {
        List<Map<String, Object>> depts = new ArrayList<>();
        for (Department d : departments.findAll()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.id); m.put("code", d.code); m.put("name", d.name);
            depts.add(m);
        }
        return Map.of("rows", rows(threshold, dept, section), "departments", depts,
                "threshold", threshold == null ? service.thresholdPct() : threshold);
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(@RequestParam(required = false) Double threshold,
                                         @RequestParam(required = false) Long dept,
                                         @RequestParam(required = false) String section) {
        StringBuilder csv = new StringBuilder(
                "roll_no,name,dept,section,aggregate_pct,classes_needed_to_recover,recoverable\n");
        for (Map<String, Object> r : rows(threshold, dept, section)) {
            csv.append("%s,%s,%s,%s,%.1f,%d,%s\n".formatted(
                    r.get("rollNo"), r.get("name"), r.get("dept"), r.get("section"),
                    (Double) r.get("pct"), r.get("need"), Boolean.TRUE.equals(r.get("recoverable")) ? "yes" : "no"));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=defaulters.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.toString());
    }

    /** Notify selected defaulters' parents (simulated SMS+email, logged). */
    @PostMapping("/notify")
    @Transactional
    public Map<String, Object> notify(@RequestBody Dtos.NotifyRequest body, HttpServletRequest request) {
        com.edumerge.attendance.config.SessionUser u =
                com.edumerge.attendance.config.SessionUser.from(request);
        int n = 0;
        for (Long sid : body.studentIds() == null ? List.<Long>of() : body.studentIds()) {
            Student st = students.findById(sid).orElse(null);
            if (st == null) continue;
            Notification notif = new Notification();
            notif.studentId = st.id;
            notif.channel = "sms+email";
            notif.message = "Dear parent, your ward's attendance is below the required threshold. "
                    + "Please contact the class advisor.";
            notif.kind = "low_attendance";
            notif.createdAt = LocalDateTime.now().withNano(0);
            notifications.save(notif);
            n++;
        }
        if (n > 0) {
            service.audit(u.username(), "LOW_ATTENDANCE_NOTIFIED", "students=%d".formatted(n));
        }
        return Map.of("message", "Notification (SMS + email) queued for %d student(s)/parents.".formatted(n));
    }
}
