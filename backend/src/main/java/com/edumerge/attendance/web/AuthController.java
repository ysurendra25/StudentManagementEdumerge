package com.edumerge.attendance.web;

import com.edumerge.attendance.config.SessionUser;
import com.edumerge.attendance.model.AppUser;
import com.edumerge.attendance.repo.AppUserRepository;
import com.edumerge.attendance.service.AttendanceService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AppUserRepository users;
    private final AttendanceService attendance;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Value("${app.demo-mode:true}")
    private boolean demoMode;

    public AuthController(AppUserRepository users, AttendanceService attendance) {
        this.users = users;
        this.attendance = attendance;
    }

    @PostMapping("/login")
    public ResponseEntity<Dtos.MeResponse> login(@RequestBody Dtos.LoginRequest req,
                                                HttpSession session) {
        AppUser u = users.findByUsername(req.username() == null ? "" : req.username().trim().toLowerCase());
        if (u == null || req.password() == null || !encoder.matches(req.password(), u.passwordHash)) {
            attendance.audit(req.username(), "LOGIN_FAILED", "invalid credentials");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        }
        SessionUser su = new SessionUser(u.id, u.username, u.role, u.refId);
        session.setAttribute(SessionUser.ATTR, su);
        attendance.audit(u.username, "LOGIN", "role=" + u.role);
        return ResponseEntity.ok(new Dtos.MeResponse(su.id(), su.username(), su.role(), su.refId()));
    }

    @PostMapping("/demo/{username}")
    public ResponseEntity<Dtos.MeResponse> demo(@PathVariable String username, HttpSession session) {
        // Quick login for reviewers — disabled when app.demo-mode=false.
        if (!demoMode) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        AppUser u = users.findByUsername(username);
        if (u == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        SessionUser su = new SessionUser(u.id, u.username, u.role, u.refId);
        session.setAttribute(SessionUser.ATTR, su);
        return ResponseEntity.ok(new Dtos.MeResponse(su.id(), su.username(), su.role(), su.refId()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Dtos.SimpleMessage> logout(HttpSession session) {
        SessionUser u = SessionUser.from(session);
        if (u != null) attendance.audit(u.username(), "LOGOUT", "user=" + u.username());
        session.invalidate();
        return ResponseEntity.ok(new Dtos.SimpleMessage("logged out"));
    }

    @GetMapping("/me")
    public ResponseEntity<Dtos.MeResponse> me(HttpServletRequest request) {
        SessionUser u = SessionUser.from(request);
        if (u == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(new Dtos.MeResponse(u.id(), u.username(), u.role(), u.refId()));
    }
}
