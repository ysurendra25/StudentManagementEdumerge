package com.edumerge.attendance.web;

import com.edumerge.attendance.model.AuditLog;
import com.edumerge.attendance.repo.AuditLogRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/** Append-only audit trail viewer (latest 150). */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditLogRepository audit;

    public AuditController(AuditLogRepository audit) {
        this.audit = audit;
    }

    @GetMapping
    public List<Map<String, Object>> audit() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (AuditLog a : audit.findTop150ByOrderByIdDesc()) {
            out.add(Map.of("ts", a.ts.toString(), "actor", a.actor,
                    "action", a.action, "details", a.details));
        }
        return out;
    }
}
