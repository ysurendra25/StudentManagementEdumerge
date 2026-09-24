package com.edumerge.attendance.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/** The logged-in user, kept in the HTTP session. */
public record SessionUser(Long id, String username, String role, Long refId) {

    public static final String ATTR = "sessionUser";

    public static SessionUser from(HttpSession s) {
        if (s == null) return null;
        Object o = s.getAttribute(ATTR);
        return o instanceof SessionUser su ? su : null;
    }

    public static SessionUser from(HttpServletRequest req) {
        return from(req.getSession(false));
    }

    public boolean isAny(String... roles) {
        for (String r : roles) if (r.equals(role)) return true;
        return false;
    }
}
