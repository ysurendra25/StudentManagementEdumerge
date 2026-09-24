package com.edumerge.attendance.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Minimal role-based access control for the REST API.
 *
 * Rules (mirror the product's permission model):
 *  - /api/auth/**                      : open (login/logout/me/demo)
 *  - /api/faculty/**                   : faculty (+ admin)
 *  - /api/student/**                   : student
 *  - /api/dashboard|defaulters|audit   : hod + admin (management visibility)
 *  - /api/students/{id}                : hod + admin
 *  - GET  /api/corrections             : hod + admin (approval queue)
 *  - POST /api/corrections             : faculty (raise) + hod/admin
 *  - POST /api/corrections/{id}/decision  : hod + admin
 *  - GET  /api/corrections/raise-options  : faculty (own sessions / rosters)
 * Everything else under /api requires at least a logged-in user.
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request,
                             @NonNull HttpServletResponse response,
                             @NonNull Object handler) throws Exception {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || path.equals("/api")) return true; // static frontend
        if (path.startsWith("/api/auth/")) return true;                    // login/logout/me/demo are open

        String method = request.getMethod();

        // --- faculty endpoints -------------------------------------------------
        if (path.startsWith("/api/faculty/")) {
            SessionUser u = SessionUser.from(request);
            if (u == null) return deny(response, HttpStatus.UNAUTHORIZED);
            // marking a session is faculty-only; viewing the list also allows admin
            if (method.equals("POST")) return u.isAny("faculty") || deny(response, HttpStatus.FORBIDDEN);
            return u.isAny("faculty", "admin") || deny(response, HttpStatus.FORBIDDEN);
        }

        // --- student endpoints ---------------------------------------------------
        if (path.startsWith("/api/student/")) {
            SessionUser u = SessionUser.from(request);
            if (u == null) return deny(response, HttpStatus.UNAUTHORIZED);
            return u.isAny("student") || deny(response, HttpStatus.FORBIDDEN);
        }

        // --- management endpoints --------------------------------------------------
        if (path.startsWith("/api/dashboard") || path.startsWith("/api/defaulters")
                || path.startsWith("/api/audit") || path.startsWith("/api/students/")) {
            SessionUser u = SessionUser.from(request);
            if (u == null) return deny(response, HttpStatus.UNAUTHORIZED);
            return u.isAny("hod", "admin") || deny(response, HttpStatus.FORBIDDEN);
        }

        // --- corrections ------------------------------------------------------------
        if (path.startsWith("/api/corrections")) {
            SessionUser u = SessionUser.from(request);
            if (u == null) return deny(response, HttpStatus.UNAUTHORIZED);
            boolean decision = path.matches("/api/corrections/\\d+/decision");
            boolean raiseOptions = path.startsWith("/api/corrections/raise");
            if (decision) return u.isAny("hod", "admin") || deny(response, HttpStatus.FORBIDDEN);
            if (raiseOptions) return u.isAny("faculty", "admin") || deny(response, HttpStatus.FORBIDDEN);
            if (method.equals("GET")) return u.isAny("hod", "admin") || deny(response, HttpStatus.FORBIDDEN);
            return u.isAny("faculty", "hod", "admin") || deny(response, HttpStatus.FORBIDDEN);
        }

        // --- anything else under /api: must be logged in --------------------------------
        return SessionUser.from(request) != null || deny(response, HttpStatus.UNAUTHORIZED);
    }

    private boolean deny(HttpServletResponse response, HttpStatus status) throws Exception {
        response.sendError(status.value());
        return false;
    }
}
