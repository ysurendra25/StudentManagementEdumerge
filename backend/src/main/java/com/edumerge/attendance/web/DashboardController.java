package com.edumerge.attendance.web;

import com.edumerge.attendance.model.AuditLog;
import com.edumerge.attendance.repo.AuditLogRepository;
import com.edumerge.attendance.repo.AttendanceSessionRepository;
import com.edumerge.attendance.repo.DepartmentRepository;
import com.edumerge.attendance.service.AttendanceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.*;

/** Management dashboard: KPIs, trend, dept-wise, activity, unmarked escalation. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final AttendanceSessionRepository sessions;
    private final AuditLogRepository audit;
    private final DepartmentRepository departments;
    private final AttendanceService service;

    public DashboardController(AttendanceSessionRepository sessions, AuditLogRepository audit,
                               DepartmentRepository departments, AttendanceService service) {
        this.sessions = sessions; this.audit = audit; this.departments = departments;
        this.service = service;
    }

    @GetMapping
    public Map<String, Object> dashboard() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate lastMonthStart = monthStart.minusMonths(1);

        // marked sessions today + slots still unmarked today = total slots today
        long todayMarked = sessions.countByDate(today);
        int dow = today.getDayOfWeek().getValue() - 1;
        long todaySlots = todayMarked + sessions.findUnmarkedSlots(dow, today).size();

        Double mavg = sessions.avgAttendanceBetween(monthStart, monthStart.plusMonths(1));
        Double pavg = sessions.avgAttendanceBetween(lastMonthStart, monthStart);

        List<Map<String, Object>> defaulters = service.defaulters(null, null, null);
        long critical = defaulters.stream().filter(d -> (Boolean) d.get("critical")).count();

        List<Map<String, Object>> trend = new ArrayList<>();
        for (Object[] r : sessions.monthlyTrend()) {
            trend.add(Map.of("month", "%04d-%02d".formatted(((Number) r[0]).intValue(), ((Number) r[1]).intValue()),
                    "pct", Math.round(((Number) r[2]).doubleValue() * 10) / 10.0));
        }
        List<Map<String, Object>> deptWise = new ArrayList<>();
        for (Object[] r : sessions.deptWise()) {
            deptWise.add(Map.of("code", r[0], "name", r[1],
                    "pct", Math.round(((Number) r[2]).doubleValue() * 10) / 10.0));
        }

        List<Map<String, Object>> activity = new ArrayList<>();
        for (AuditLog a : audit.findTop8ByOrderByIdDesc()) {
            activity.add(Map.of("ts", a.ts.toString(), "action", a.action, "actor", a.actor, "details", a.details));
        }

        List<Map<String, Object>> unmarked = new ArrayList<>();
        for (LocalDate d : service.expectedDates(7)) {
            for (Object[] r : sessions.findUnmarkedSlots(d.getDayOfWeek().getValue() - 1, d)) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("date", d.toString());
                m.put("facName", r[7]);
                m.put("subCode", r[8]);
                m.put("section", r[3]);
                m.put("period", ((Number) r[1]).intValue());
                unmarked.add(m);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("todayMarked", todayMarked);
        out.put("todayTotal", todaySlots);
        out.put("mavg", mavg == null ? 0 : Math.round(mavg * 10) / 10.0);
        out.put("pavg", pavg == null ? 0 : Math.round(pavg * 10) / 10.0);
        out.put("defaulters", defaulters.size());
        out.put("critical", critical);
        out.put("trend", trend);
        out.put("deptWise", deptWise);
        out.put("activity", activity);
        out.put("unmarked", unmarked);
        return out;
    }
}
