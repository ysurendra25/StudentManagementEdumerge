import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { get } from '../api.js'

export default function StudentDetail() {
  const { id } = useParams()
  const [st, setSt] = useState(null)
  const [error, setError] = useState(null)
  useEffect(() => { get(`/api/students/${id}`).then(setSt).catch((e) => setError(e.message)) }, [id])
  if (error) return <div className="flash error">{error}</div>
  if (!st) return <p className="muted">Loading…</p>

  const eligible = st.pct >= st.threshold

  return (
    <>
      <div className="stu-head">
        <div>
          <h1 className="h">{st.name}</h1>
          <p className="muted">{st.dept} · Section {st.section} · Roll {st.rollNo} · Semester {st.semester}</p>
        </div>
        <div className={`ring ${eligible ? 'ok' : 'bad'}`} style={{ '--p': st.pct }}>
          <div><b>{st.pct}%</b><span>overall</span></div>
        </div>
      </div>
      <p className="muted">{st.present} of {st.held} sessions attended.
        {eligible ? <span className="badge ok">Eligible ({st.threshold}% rule)</span>
          : <span className="badge crit">Below {st.threshold}% — not eligible for end-sem exams</span>}</p>

      <h2 className="h2">Subject-wise</h2>
      <table className="tbl">
        <thead><tr><th>Subject</th><th>Attended</th><th>Attendance</th><th>Status</th></tr></thead>
        <tbody>
          {st.subjects.map((s) => (
            <tr key={s.code}>
              <td>{s.code} — {s.name}</td><td>{s.present}/{s.held}</td>
              <td><div className="bar"><div className={s.pct >= st.threshold ? 'fb ok' : 'fb low'}
                style={{ width: s.pct + '%' }} /></div> {s.pct}%</td>
              <td>{s.pct >= st.threshold ? <span className="badge ok">OK</span> : <span className="badge crit">LOW</span>}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <h2 className="h2">Recent sessions</h2>
      <table className="tbl">
        <thead><tr><th>Date</th><th>Period</th><th>Subject</th><th>Marked</th></tr></thead>
        <tbody>
          {st.history.map((h, i) => (
            <tr key={i}>
              <td>{h.date}</td><td>{h.period}</td><td>{h.subCode}</td>
              <td><span className={`badge ${h.status}`}>{h.status}</span></td>
            </tr>
          ))}
        </tbody>
      </table>

      {st.notifications.length > 0 && <>
        <h2 className="h2">Notifications sent</h2>
        <table className="tbl">
          <thead><tr><th>When</th><th>Channel</th><th>Message</th></tr></thead>
          <tbody>
            {st.notifications.map((n, i) => (
              <tr key={i}><td>{n.createdAt}</td><td>{n.channel}</td><td className="small">{n.message}</td></tr>
            ))}
          </tbody>
        </table>
      </>}
    </>
  )
}
