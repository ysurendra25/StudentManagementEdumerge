package com.edumerge.attendance.service;

import com.edumerge.attendance.model.*;
import com.edumerge.attendance.repo.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Seeds deterministic demo data on first startup (users table empty).
 * Same generator shape as the design phase: 4 departments, 48 faculty
 * (incl. 4 HODs), 20 subjects, 16 cohorts, 40 students each, ~8 weeks of
 * session history, a few corrections/notifications, holidays, and a slice of
 * deliberately unmarked recent sessions to demo escalation.
 *
 * Deterministic: new Random(42) so every fresh run yields the same data.
 */
@Component
public class SeedRunner implements org.springframework.boot.CommandLineRunner {

    private static final long SEED = 42L;
    private static final int STUDENTS_PER_SECTION = 40;
    private static final int WEEKS_OF_HISTORY = 8;
    private static final double LOW_ATTENDANCE_RATE = 0.04;
    private static final double UNMARKED_RECENT_RATE = 0.08;
    private static final Set<LocalDate> HOLIDAYS = Set.of(LocalDate.parse("2026-09-08"),
            LocalDate.parse("2026-09-21"));

    private static final String[][] DEPTS = {
            {"CSE", "Computer Science & Engineering"},
            {"ECE", "Electronics & Communication Engineering"},
            {"ME", "Mechanical Engineering"},
            {"CE", "Civil Engineering"}};
    private static final Map<String, String[][]> SUBJECTS = Map.of(
            "CSE", new String[][]{{"CS201", "Data Structures", "5"}, {"CS202", "DBMS", "5"},
                    {"CS203", "Computer Networks", "5"}, {"CS301", "Operating Systems", "3"},
                    {"CS302", "Software Engineering", "3"}},
            "ECE", new String[][]{{"EC201", "Signals & Systems", "5"}, {"EC202", "Digital Electronics", "5"},
                    {"EC203", "EM Theory", "5"}, {"EC301", "Microprocessors", "3"},
                    {"EC302", "Control Systems", "3"}},
            "ME", new String[][]{{"ME201", "Thermodynamics", "5"}, {"ME202", "Fluid Mechanics", "5"},
                    {"ME203", "Machine Design", "5"}, {"ME301", "Manufacturing Processes", "3"},
                    {"ME302", "Heat Transfer", "3"}},
            "CE", new String[][]{{"CE201", "Structural Analysis", "5"}, {"CE202", "Geotechnical Engineering", "5"},
                    {"CE203", "Transportation Engg", "5"}, {"CE301", "Environmental Engg", "3"},
                    {"CE302", "Concrete Technology", "3"}});

    private static final String[] FIRST = {"Aarav", "Bhavya", "Chirag", "Deepak", "Esha", "Farhan",
            "Gauri", "Harsh", "Ishita", "Jayesh", "Kavya", "Lokesh", "Meera", "Nikhil", "Oviya",
            "Pranav", "Qadir", "Ravi", "Sneha", "Tanvi", "Umesh", "Vidya", "Wasim", "Yash", "Zoya",
            "Ananya", "Rohit", "Divya", "Karthik", "Priya"};
    private static final String[] LAST = {"Sharma", "Nair", "Reddy", "Menon", "Gosh", "Ali", "Patil",
            "Verma", "Roy", "Kumar", "Iyer", "Babu", "Joshi", "Rao", "Sen", "Kulkarni", "Pillai",
            "Sinha", "Das", "Malhotra", "Krishnan", "Shetty", "Chauhan"};
    private static final String[] FACULTY_FIRST = {"Sunil", "Anita", "Vikram", "Priyanka", "Suresh",
            "Lakshmi", "Mahesh", "Deepa", "Ramesh", "Kiran", "Nandini", "Prakash", "Geetha"};
    private static final Map<String, String> HOD_NAMES = Map.of(
            "CSE", "Dr. Kavitha Rao", "ECE", "Dr. Suresh Nair",
            "ME", "Dr. Lakshmi Iyer", "CE", "Dr. Prakash Sharma");

    private final DepartmentRepository departments;
    private final FacultyRepository facultyRepo;
    private final SubjectRepository subjectRepo;
    private final StudentRepository studentRepo;
    private final TimetableSlotRepository slotRepo;
    private final AppUserRepository userRepo;
    private final CorrectionRepository correctionRepo;
    private final NotificationRepository notificationRepo;
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public SeedRunner(DepartmentRepository departments, FacultyRepository facultyRepo,
                      SubjectRepository subjectRepo, StudentRepository studentRepo,
                      TimetableSlotRepository slotRepo, AppUserRepository userRepo,
                      CorrectionRepository correctionRepo, NotificationRepository notificationRepo,
                      JdbcTemplate jdbc) {
        this.departments = departments; this.facultyRepo = facultyRepo;
        this.subjectRepo = subjectRepo; this.studentRepo = studentRepo;
        this.slotRepo = slotRepo; this.userRepo = userRepo;
        this.correctionRepo = correctionRepo; this.notificationRepo = notificationRepo;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepo.count() > 0) return; // already seeded

        Random rng = new Random(SEED);
        LocalDate today = LocalDate.now();

        // departments
        Map<String, Long> deptIds = new LinkedHashMap<>();
        for (String[] d : DEPTS) {
            Department dep = new Department();
            dep.code = d[0]; dep.name = d[1];
            departments.save(dep);
            deptIds.put(d[0], dep.id);
        }

        // faculty + users (4 distinct bcrypt hashes, reused — demo scale)
        String facHash = encoder.encode("faculty123");
        String hodHash = encoder.encode("hod123");
        String adminHash = encoder.encode("admin123");
        Map<String, Long> facultyIds = new HashMap<>();   // dept + "#" + idx
        Set<String> takenUsernames = new HashSet<>();
        List<AppUser> users = new ArrayList<>();
        AppUser admin = user("admin", adminHash, "admin", null);
        users.add(admin);
        int nameCounter = 0;
        for (String deptCode : deptIds.keySet()) {
            for (int i = 0; i <= 10; i++) {
                Faculty f = new Faculty();
                if (i == 10) {
                    f.name = HOD_NAMES.get(deptCode);
                    f.role = "hod";
                } else if (deptCode.equals("CSE") && i == 0) {
                    f.name = "Dr. Rajesh Mehta";   // demo faculty: teaches CS201
                } else {
                    f.name = "Dr. " + FACULTY_FIRST[nameCounter % FACULTY_FIRST.length]
                            + " " + LAST[nameCounter % LAST.length];
                    nameCounter++;
                }
                f.deptId = deptIds.get(deptCode);
                facultyRepo.save(f);
                facultyIds.put(deptCode + "#" + i, f.id);
                users.add(user(usernameFor(f.name, takenUsernames),
                        i == 10 ? hodHash : facHash, i == 10 ? "hod" : "faculty", f.id));
            }
        }

        // subjects
        Map<String, Long> subjectIds = new HashMap<>();
        for (String deptCode : deptIds.keySet()) {
            for (String[] s : SUBJECTS.get(deptCode)) {
                Subject sub = new Subject();
                sub.code = s[0]; sub.name = s[1];
                sub.deptId = deptIds.get(deptCode);
                sub.semester = Integer.parseInt(s[2]);
                subjectRepo.save(sub);
                subjectIds.put(s[0], sub.id);
            }
        }

        // cohorts + students: sem-5 -> sections A/B, sem-3 -> C/D
        Set<String> lowAttRolls = new HashSet<>();
        Map<String, Long> rollToId = new HashMap<>();
        String studentHash = encoder.encode("student123");
        for (String deptCode : deptIds.keySet()) {
            for (int sem = 5; sem >= 3; sem -= 2) {
                String[] secs = sem == 5 ? new String[]{"A", "B"} : new String[]{"C", "D"};

            }
        }
        for (String deptCode : deptIds.keySet()) {
            for (int sem : new int[]{5, 3}) {
                String[] secs = sem == 5 ? new String[]{"A", "B"} : new String[]{"C", "D"};
                for (String sec : secs) {
                    String yearPrefix = sem == 5 ? "23" : "25";
                    for (int n = 1; n <= STUDENTS_PER_SECTION; n++) {
                        Student st = new Student();
                        st.rollNo = "%s%s%s%02d".formatted(yearPrefix, deptCode, sec, n);
                        st.name = st.rollNo.equals("23CSEB05") ? "Esha Gosh"
                                : FIRST[rng.nextInt(FIRST.length)] + " " + LAST[rng.nextInt(LAST.length)];
                        st.deptId = deptIds.get(deptCode);
                        st.section = sec;
                        st.semester = sem;
                        st.parentEmail = "parent." + st.rollNo.toLowerCase() + "@example.com";
                        studentRepo.save(st);
                        rollToId.put(st.rollNo, st.id);
                        if (rng.nextDouble() < LOW_ATTENDANCE_RATE) lowAttRolls.add(st.rollNo);
                        users.add(user(st.rollNo.toLowerCase(), studentHash, "student", st.id));
                    }
                }
            }
        }
        userRepo.saveAll(users);

        // timetable: 3 slots/subject, unique (dept, section, day, period)
        List<Object[]> ttRows = new ArrayList<>();
        Set<String> usedKeys = new HashSet<>();
        Map<Long, List<Long>> sectionStudents = new HashMap<>();
        for (String deptCode : deptIds.keySet()) {
            long deptId = deptIds.get(deptCode);
            for (int sem : new int[]{5, 3}) {
                String[] secs = sem == 5 ? new String[]{"A", "B"} : new String[]{"C", "D"};
                for (String sec : secs) {
                    usedKeys.clear();
                    String[][] subs = SUBJECTS.get(deptCode);
                    for (int k = 0; k < subs.length; k++) {
                        int leadIdx = k * 2 % 10;
                        if (deptCode.equals("CSE") && sem == 5 && subs[k][0].equals("CS201")) leadIdx = 0;
                        for (int t = 0; t < 3; t++) {
                            while (true) {
                                int day = rng.nextInt(5), period = 1 + rng.nextInt(6);
                                String key = day + "-" + period;
                                if (usedKeys.add(key)) {
                                    ttRows.add(new Object[]{deptId, sec, subjectIds.get(subs[k][0]),
                                            facultyIds.get(deptCode + "#" + leadIdx), day, period,
                                            deptCode + "-" + sec + period + "ABC".charAt(rng.nextInt(3)) + "-L"});
                                    break;
                                }
                            }
                        }
                    }
                    List<Long> ids = new ArrayList<>();
                    for (Student s : studentRepo.findAll()) {
                        if (s.deptId == deptId && s.section.equals(sec) && s.active) ids.add(s.id);
                    }
                    sectionStudents.put((deptId << 8) + sec.charAt(0), ids);
                }
            }
        }
        List<Long> ttIds = new ArrayList<>();
        List<Long> ttFaculties = new ArrayList<>();
        for (Object[] r : ttRows) {
            TimetableSlot t = new TimetableSlot();
            t.deptId = (Long) r[0];
            t.section = (String) r[1];
            t.subjectId = (Long) r[2];
            t.facultyId = (Long) r[3];
            t.dayOfWeek = (Integer) r[4];
            t.period = (Integer) r[5];
            t.room = (String) r[6];
            slotRepo.save(t);
            ttIds.add(t.id);
            ttFaculties.add(t.facultyId);
        }

        // per-student attendance profile
        Map<Long, Double> profile = new HashMap<>();
        for (Map.Entry<String, Long> e : rollToId.entrySet()) {
            profile.put(e.getValue(), lowAttRolls.contains(e.getKey())
                    ? 0.55 + rng.nextDouble() * 0.15 : 0.80 + rng.nextDouble() * 0.15);
        }
        Long demoId = rollToId.get("23CSEB05");
        if (demoId != null) profile.put(demoId, 0.80);

        // history: bulk inserts via JdbcTemplate (36k rows; JPA merge would be too slow)
        List<Object[]> sessionBatch = new ArrayList<>();
        List<Object[]> attBatch = new ArrayList<>();
        List<Object[]> auditBatch = new ArrayList<>();
        LocalDate start = today.minusWeeks(WEEKS_OF_HISTORY);
        for (LocalDate d = start; d.isBefore(today); d = d.plusDays(1)) {
            if (HOLIDAYS.contains(d) || d.getDayOfWeek().getValue() > 5) continue;
            int dow = d.getDayOfWeek().getValue() - 1;
            for (int ti = 0; ti < ttIds.size(); ti++) {
                if (((int) (ttRows.get(ti)[4])) != dow) continue;
                long daysAgo = today.toEpochDay() - d.toEpochDay();
                if (daysAgo <= 3 && rng.nextDouble() < UNMARKED_RECENT_RATE) continue; // unmarked demo
                long sessionId = sessionBatch.size() + 1L;
                long facultyId = ttFaculties.get(ti);
                String markedAt = "%s %02d:%02d:00".formatted(d, 8 + (int) (facultyId % 8),
                        10 + rng.nextInt(49));
                sessionBatch.add(new Object[]{ttIds.get(ti), d.toString(), "submitted", facultyId, markedAt});
                long deptId = (Long) ttRows.get(ti)[0];
                char sec = ((String) ttRows.get(ti)[1]).charAt(0);
                for (Long sid : sectionStudents.get((deptId << 8) + sec)) {
                    double r = rng.nextDouble();
                    String st = r < profile.get(sid) ? "P" : (r > 0.96 ? "L" : "A");
                    attBatch.add(new Object[]{sessionId, sid, st});
                }
                auditBatch.add(new Object[]{markedAt, "faculty#" + facultyId, "SESSION_MARKED",
                        "session=" + sessionId + " timetable=" + ttIds.get(ti)});
            }
        }
        jdbc.batchUpdate("INSERT INTO sessions(timetable_id, date, status, marked_by, marked_at) VALUES (?,?,?,?,?)",
                sessionBatch);
        jdbc.batchUpdate("INSERT INTO attendance(session_id, student_id, status) VALUES (?,?,?)", attBatch);
        jdbc.batchUpdate("INSERT INTO audit_log(ts, actor, action, details) VALUES (?,?,?,?)", auditBatch);

        // corrections
        Long mehtaUserId = userRepo.findByUsername("mehta").id;
        Long raoUserId = userRepo.findByUsername("rao").id;
        String[][] corrSpecs = {
                {"23CSEB05", "A", "P", "medical", "Student was in the sick bay during the period; verified with the health centre.", "pending"},
                {"23CSEA07", "A", "P", "sports_od", "Inter-college badminton; approval from sports cell attached.", "pending"},
                {"23CSEA19", "P", "A", "marked_in_error", "Marked present in error; student had left early for a family emergency.", "approved"},
                {"23ECEA02", "L", "P", "other", "Late arrival due to bus breakdown; route proof submitted.", "approved"},
                {"23MEB11", "A", "P", "medical", "Medical certificate for the session date attached.", "rejected"}};
        int n = 0;
        for (String[] spec : corrSpecs) {
            Long sid = rollToId.get(spec[0]);
            if (sid == null) continue;
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT session_id FROM attendance WHERE student_id = ? AND status = ? LIMIT 1", sid, spec[1]);
            if (rows.isEmpty()) continue;
            n++;
            long sessId = ((Number) rows.get(0).get("session_id")).longValue();
            String created = "%s 09:%02d:00".formatted(today.minusDays(1), 15 + n);
            boolean decided = !spec[5].equals("pending");
            jdbc.update("INSERT INTO corrections(session_id, student_id, old_status, new_status, category, " +
                            "reason, requested_by, status, decided_by, decided_at, created_at) " +
                            "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    sessId, sid, spec[1], spec[2], spec[3], spec[4], mehtaUserId, spec[5],
                    decided ? raoUserId : null, decided ? created.replace("09:", "10:") : null, created);
            if (spec[5].equals("approved")) {
                jdbc.update("UPDATE attendance SET status = ? WHERE session_id = ? AND student_id = ?",
                        spec[2], sessId, sid);
            }
            jdbc.update("INSERT INTO audit_log(ts, actor, action, details) VALUES (?,?,?,?)",
                    created, "user#mehta", "CORRECTION_REQUESTED",
                    "session=" + sessId + " student=" + sid + " " + spec[1] + "->" + spec[2] + " (" + spec[3] + ")");
            if (decided) {
                jdbc.update("INSERT INTO audit_log(ts, actor, action, details) VALUES (?,?,?,?)",
                        created.replace("09:", "10:"), "user#rao",
                        "CORRECTION_" + spec[5].toUpperCase(), "session=" + sessId + " student=" + sid);
            }
        }

        // a few defaulter notifications
        for (String roll : List.of("23CSEA07", "23ECEA02", "23CEEA02")) {
            Long sid = rollToId.get(roll);
            if (sid == null) continue;
            Notification notif = new Notification();
            notif.studentId = sid;
            notif.channel = "sms+email";
            notif.message = "Dear parent, your ward's attendance is below the 75% requirement. "
                    + "Please contact the class advisor.";
            notif.kind = "low_attendance";
            notif.createdAt = LocalDateTime.now().minusDays(2).withNano(0);
            notificationRepo.save(notif);
        }

        System.out.println("Seed complete: students=" + studentRepo.count() + " faculty=" + facultyRepo.count()
                + " sessions=" + sessionBatch.size() + " attendanceRows=" + attBatch.size()
                + " corrections=" + n);
        System.out.println("Demo logins: admin/admin123 | mehta/faculty123 | rao/hod123 | 23cseb05/student123");
    }

    private AppUser user(String username, String hash, String role, Long refId) {
        AppUser u = new AppUser();
        u.username = username;
        u.passwordHash = hash;
        u.role = role;
        u.refId = refId;
        return u;
    }

    private String usernameFor(String name, Set<String> taken) {
        String base = name.replace("Dr. ", "").split(" ")[name.replace("Dr. ", "").split(" ").length - 1]
                .toLowerCase();
        String u = base;
        int i = 1;
        while (taken.contains(u)) u = base + (++i);
        taken.add(u);
        return u;
    }
}
