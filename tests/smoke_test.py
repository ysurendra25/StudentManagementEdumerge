"""End-to-end smoke test for the Smart Attendance prototype (REST API).

Start the backend first (see README), then:  python tests/smoke_test.py

Covers: auth (password + bad password), dashboard KPIs, defaulters report +
CSV export, faculty marking (roster, P/A/L, re-mark block, future-date block,
proxy-mark block), the correction cycle (raise -> approve, state applied),
student self-service correction, parent notifications, the audit trail, and
role guards. Prints PASS/FAIL per check; exits non-zero on any failure.

NOTE: repeatable — it always picks students/sessions that are still
absent, and marks only sessions still unmarked.
"""
import datetime
import sys

import requests

BASE = "http://localhost:8081"


def demo(user):
    s = requests.Session()
    r = s.post(f"{BASE}/api/auth/demo/{user}")
    assert r.status_code == 200, f"demo login failed for {user}: {r.status_code} {r.text}"
    return s


def main():
    checks = []
    rao, mehta, esha, nair = demo("rao"), demo("mehta"), demo("23cseb05"), demo("nair")
    today = datetime.date.today().isoformat()

    # --- auth ---------------------------------------------------------------
    r = requests.Session().post(f"{BASE}/api/auth/login",
                                json={"username": "mehta", "password": "faculty123"})
    checks.append(("password login", r.status_code == 200))
    r = requests.Session().post(f"{BASE}/api/auth/login",
                                json={"username": "mehta", "password": "wrong"})
    checks.append(("bad password rejected", r.status_code == 401))

    # --- dashboard ----------------------------------------------------------
    d = rao.get(f"{BASE}/api/dashboard").json()
    checks.append(("dashboard KPIs", d["defaulters"] > 0 and len(d["deptWise"]) == 4
                   and len(d["trend"]) >= 1))

    # --- defaulters -----------------------------------------------------------
    dd = rao.get(f"{BASE}/api/defaulters").json()
    checks.append(("defaulters report", dd["threshold"] == 75.0 and len(dd["rows"]) > 0))
    checks.append(("recovery math fields", all(
        k in dd["rows"][0] for k in ("need", "recoverable", "lastNotified"))))
    csv = rao.get(f"{BASE}/api/defaulters/export").text
    checks.append(("CSV export", csv.startswith("roll_no") and len(csv.splitlines()) > 3))

    # --- marking -------------------------------------------------------------
    fs = mehta.get(f"{BASE}/api/faculty/sessions").json()
    slot = next((t for t in fs["today"] if not t["marked"]), None)
    if slot is None:
        slot = fs["unmarked"][0] if fs["unmarked"] else None
    if slot:
        slot_date = today if "date" not in slot else slot["date"]
        roster = mehta.get(f"{BASE}/api/faculty/roster/{slot['id']}/{slot_date}").json()
        checks.append(("roster loaded", len(roster["roster"]) == 40))
        payload = {str(s["id"]): "P" for s in roster["roster"]}
        payload[str(roster["roster"][0]["id"])] = "A"
        payload[str(roster["roster"][1]["id"])] = "L"
        r = mehta.post(f"{BASE}/api/faculty/mark/{slot['id']}/{slot_date}",
                       json={"statuses": payload})
        ok = r.status_code == 200 and r.json()["absent"] == 1 and r.json()["late"] == 1
        checks.append(("mark session", ok))
        r2 = mehta.get(f"{BASE}/api/faculty/roster/{slot['id']}/{slot_date}")
        checks.append(("re-mark blocked", r2.status_code == 400))
    other = fs["today"][0] if fs["today"] else None
    if other:
        r = nair.get(f"{BASE}/api/faculty/roster/{other['id']}/{today}")
        checks.append(("proxy marking blocked", r.status_code in (400, 403)))
    r = mehta.get(f"{BASE}/api/faculty/roster/{fs['today'][0]['id']}/2027-01-01"
                  if fs["today"] else f"{BASE}/api/faculty/roster/1/2027-01-01")
    checks.append(("future date blocked", r.status_code == 400))

    # --- correction cycle -------------------------------------------------------
    opts = mehta.get(f"{BASE}/api/corrections/raise-options").json()
    sid = opts[0]["id"]
    rr = mehta.get(f"{BASE}/api/corrections/raise-roster/{sid}").json()
    absent = [s for s in rr["roster"] if s["status"] != "P"]
    target = absent[0] if absent else rr["roster"][0]
    r = mehta.post(f"{BASE}/api/corrections", json={
        "sessionId": sid, "studentId": target["id"], "newStatus": "P",
        "category": "medical", "reason": "Was at the health centre; verified with the nurse."})
    checks.append(("faculty raises correction", r.status_code == 200))
    q = rao.get(f"{BASE}/api/corrections?status=pending").json()
    cid = q["rows"][0]["id"]
    r = rao.post(f"{BASE}/api/corrections/{cid}/decision", json={"decision": "approve"})
    checks.append(("HOD approves correction", r.status_code == 200
                   and "applied" in r.json()["message"]))
    rr2 = mehta.get(f"{BASE}/api/corrections/raise-roster/{sid}").json()
    after = [s for s in rr2["roster"] if s["id"] == target["id"]][0]
    checks.append(("attendance row updated after approval", after["status"] != target["status"]))

    # --- student self-service ------------------------------------------------------
    me = esha.get(f"{BASE}/api/student/me").json()
    checks.append(("student self view", me["rollNo"] == "23CSEB05" and len(me["subjects"]) > 0))
    absents = [h for h in me["history"] if h["status"] != "P"]
    if absents:
        r = esha.post(f"{BASE}/api/student/correction", json={
            "sessionId": absents[0]["sessionId"], "category": "medical",
            "reason": "Documented health centre visit on that date."})
        checks.append(("student raises correction", r.status_code == 200))

    # --- notifications + audit -------------------------------------------------------
    r = rao.post(f"{BASE}/api/defaulters/notify",
                 json={"studentIds": [dd["rows"][0]["id"], dd["rows"][1]["id"]]})
    checks.append(("parent notification", r.status_code == 200 and "queued" in r.json()["message"]))
    au = rao.get(f"{BASE}/api/audit").json()
    checks.append(("audit trail", any(a["action"] == "CORRECTION_APPROVED" for a in au)))

    # --- role guards ----------------------------------------------------------------------
    checks.append(("student blocked from dashboard", esha.get(f"{BASE}/api/dashboard").status_code == 403))
    checks.append(("faculty blocked from corrections queue",
                   mehta.get(f"{BASE}/api/corrections").status_code == 403))
    checks.append(("anonymous blocked", requests.get(f"{BASE}/api/dashboard").status_code == 401))
    checks.append(("student blocked from other student's detail",
                   esha.get(f"{BASE}/api/students/1").status_code == 403))
    checks.append(("frontend served", requests.get(f"{BASE}/").status_code == 200))

    fails = [n for n, v in checks if not v]
    for n, v in checks:
        print(("PASS" if v else "FAIL"), "-", n)
    print("\nRESULT:", "ALL PASS" if not fails else f"{len(fails)} FAILURE(S)")
    sys.exit(1 if fails else 0)


if __name__ == "__main__":
    main()
