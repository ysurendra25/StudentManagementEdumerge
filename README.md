# Smart Attendance Management

**Edumerge Pre-Drive Product Engineering — Assignment 1**

A working prototype for a college (~5,000 students, ~200 faculty, multiple
departments / sections / subjects) covering the full attendance lifecycle:
**recording → review & history → corrections (audited) → low-attendance
identification → parent notification → reports/export.**

**Stack:** Spring Boot 3 (Java 21) + React 18 (Vite) + H2 database.
The React build is bundled into the Spring Boot jar, so the whole product
runs as **one service** on one port.

---

## 1. Approach in one page

The brief asks for attendance recording, corrections, review, history and
low-attendance identification. Attendance is modelled as a **two-level ledger**:

1. A **timetable slot** is the recurring unit (dept + section + subject +
   faculty + weekday + period). A **session** is one concrete instance of a
   slot on a date. Attendance rows hang off sessions, never off slots — so
   history is never ambiguous.
2. Attendance rows are *current state*; the **audit log** is append-only and
   the **corrections** table stores old→new + reason + approver. Nothing is
   ever silently overwritten.

Roles and what they can do:

| Role | Key powers |
|---|---|
| Faculty | Today's + unmarked past slots, mark attendance (P/A/L, default Present), raise corrections for own sessions |
| HOD / Admin | Dashboard, defaulters report & export, approve/reject corrections, notify parents, any student's detail, audit trail |
| Student | Own aggregate + subject-wise %, history, raise correction requests for own absences |

Hard rules enforced **server-side** (a `HandlerInterceptor`, not just in the UI):

- Only the **allotted faculty** of a slot can mark it (proxy marking blocked).
- A session can be marked **once**; any change afterwards goes through the
  correction workflow (HOD-approved, reason required).
- Corrections need a reason (≥ 10 chars) and category; every decision is
  audited with actor + timestamp.
- Students can only request corrections for **their own** records, and only
  where they are Absent/Late.
- Future dates and holidays cannot be marked.
- Role guards: students can't reach management endpoints, faculty can't
  reach the approval queue, anonymous users get 401.

### Low-attendance engine

- Threshold configurable per query (default **75%**, the common university
  norm); Late/OD counts as present.
- For each defaulter the report computes **classes needed to recover**:
  `(P + x) / (H + x) ≥ T  ⟹  x = ⌈(T·H − P)/(1 − T)⌉`, compared with the
  **remaining sessions** in the semester (weeks left × slots/week). Students
  who cannot mathematically recover are flagged for **condonation** instead
  of a meaningless "+N".
- Parent notifications (simulated SMS/email) are logged per student with
  timestamps, so "last notified" is auditable.

## 2. Architecture

```
React 18 (Vite SPA)  ──REST/JSON──►  Spring Boot 3 (port 8080)
   src/pages/…                        ├─ AuthInterceptor   (session + role checks)
   src/api.js  fetch wrapper          ├─ Controllers        (REST endpoints)
                                      ├─ AttendanceService  (marking, recovery math, audit)
                                      ├─ Spring Data JPA repositories
                                      └─ H2 file DB (auto-created, auto-seeded)
```

- **One runnable artifact:** the built SPA is served from
  `backend/src/main/resources/static`, and a small `SpaForwardFilter`
  forwards non-API GETs to `index.html` so React Router deep links work.
- **Auth:** `HttpSession` + BCrypt password hashes (spring-security-crypto,
  no full filter chain in the prototype). Demo quick-login endpoints exist
  only while `app.demo-mode: true`.
- **DB:** H2 file database — zero setup, created and seeded on first start.
  The schema is plain JPA entities; queries are standard SQL (aggregates use
  native queries), so porting to PostgreSQL is a config change.
- **Why JdbcTemplate for the seed:** 72k attendance rows are bulk-inserted
  with batched SQL; JPA `save()` would issue a SELECT per row for assigned
  composite keys. (A deliberate, explainable trade-off.)

### Data model (core entities)

```
Department ─┬─ Faculty (role: faculty | hod)
            ├─ Student (rollNo, section, semester, parentEmail)
            └─ Subject (dept, semester)
TimetableSlot (dept, section, subject, faculty, dayOfWeek, period)   UNIQUE(dept, section, dow, period)
AttendanceSession (timetableSlot, date, markedBy, markedAt)          UNIQUE(timetableSlot, date)
AttendanceRecord (sessionId, studentId, status ∈ {P, A, L})          PK(sessionId, studentId)
Correction (session, student, old→new, category, reason, requestedBy, status, decidedBy, decidedAt)
AuditLog  (append-only: ts, actor, action, details)
Notification (student, channel, message, kind, ts)
AppUser   (username, bcrypt hash, role, refId)
```

## 3. Running it

Prerequisites: **Java 21+**, **Maven 3.9+**, **Node 18+** (only to rebuild the UI).

```bash
./run.sh        # builds frontend + bundles it + runs the backend
```

or step by step:

```bash
cd frontend && npm install && npm run build          # build the SPA
cp -r dist/* ../backend/src/main/resources/static/   # bundle (run.sh does this)
cd ../backend && mvn spring-boot:run                  # http://localhost:8080
```

The database file (`backend/attendance-db.mv.db`) is created and seeded with
deterministic demo data on first start — 640 students, 44 faculty, 4
departments, 20 subjects, ~1,800 sessions of history over 8 weeks, 5 seeded
corrections, unmarked-session escalation candidates. Delete the file to
re-seed.

Demo logins (also one-click chips on the login page):

| Role | User | Password |
|---|---|---|
| Admin | `admin` | `admin123` |
| Faculty (CSE, teaches Data Structures) | `mehta` | `faculty123` |
| HOD (CSE) | `rao` | `hod123` |
| Student (Esha Gosh, 23CSEB05) | `23cseb05` | `student123` |

Scale note: the brief says ~5,000 students / 200 faculty. The demo ships
smaller to keep the repo light; bump `STUDENTS_PER_SECTION` in
`SeedRunner` (or port the generator) to seed at full scale — the schema and
queries are indexed for it.

## 4. Assumptions

1. Late/OD counts as **present** for percentage purposes.
2. Default threshold **75%** aggregate; subject-wise shortfall visible on
   student detail views.
3. A cohort = (department, section); sem-5 uses sections A/B, sem-3 C/D,
   so each cohort owns one timetable without collisions.
4. Semester ends 2026-12-11 — drives the "recoverable?" estimate.
5. Notifications (SMS/email) are **simulated** and logged; the integration
   point is isolated in one controller.
6. Holidays are a small configured set (production: admin-managed table).

## 5. Validation performed

`tests/smoke_test.py` (run against a freshly seeded DB — **23/23 passing**):

- Mark a session (40-student roster, mixed P/A/L) → correct summary.
- Re-marking the same session is **blocked** with a pointer to corrections.
- Marking a **future** date is blocked.
- Faculty **A** opening faculty **B**'s roster/mark URL is blocked.
- Raise correction as faculty → appears in HOD queue → approve → attendance
  row updated + both audit entries written.
- Student raises correction for own absence → lands in the HOD queue.
- Defaulters report: filters (dept/section/threshold), recovery math fields,
  CSV export, parent notification logging.
- Role guards (student/faculty/anonymous) on every protected endpoint.
- Frontend served with working SPA deep links.

Edge cases handled explicitly: division by zero for students with no
sessions, the exactly-at-threshold boundary, corrections that wouldn't change
the status are refused, double-deciding a correction is refused (409).

## 6. Trade-offs & what I'd build next

- **Session auth instead of JWT/Spring Security chain** — simplest thing that
  satisfies the role model for a prototype; swapping in Spring Security with
  the same `AuthInterceptor` rules is straightforward.
- **H2 instead of PostgreSQL** — zero-setup review; queries are standard SQL.
- **Simulated notifications** — the send is isolated in one method.
- Next iterations: real SMS/email gateway, timetable CSV import, per-subject
  thresholds, QR/biometric capture, offline draft sync for the faculty phone,
  nightly job for unmarked-session escalation emails, pagination at 5k scale.

## 7. Repository layout

```
backend/    Spring Boot app (Java 21, Maven)
frontend/   React SPA (Vite; built output bundled into backend/static)
mockups/    the 5 product-design mockups created before coding
tests/      end-to-end API smoke test
run.sh      build frontend + run everything
AI_USAGE_REPORT.md
```
