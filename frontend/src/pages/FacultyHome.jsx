import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { get } from '../api.js'

export default function FacultyHome() {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => { get('/api/faculty/sessions').then(setData).catch((e) => setError(e.message)) }, [])

  if (error) return <div className="flash error">{error}</div>
  if (!data) return <p className="muted">Loading…</p>
  const today = new Date().toISOString().slice(0, 10)

  return (
    <>
      <h1 className="h">Today's Sessions <span className="muted small">{today}</span></h1>
      {data.today.length === 0 && <p className="empty">No sessions scheduled for you today.</p>}
      {data.today.length > 0 &&
        <table className="tbl">
          <thead><tr><th>Period</th><th>Subject</th><th>Dept / Section</th><th>Room</th><th>Status</th><th></th></tr></thead>
          <tbody>
            {data.today.map((s) => (
              <tr key={s.id}>
                <td><b>{s.period}</b></td>
                <td>{s.subCode} — {s.subName}</td>
                <td>{s.dept} / {s.section}</td>
                <td>{s.room}</td>
                <td>{s.marked ? <span className="badge ok">Marked</span> : <span className="badge warn">Pending</span>}</td>
                <td>{!s.marked &&
                  <Link className="btn primary sm" to={`/faculty/mark/${s.id}/${today}`}>Mark attendance</Link>}</td>
              </tr>
            ))}
          </tbody>
        </table>}

      {data.unmarked.length > 0 &&
        <>
          <div className="alert warn">
            <b>{data.unmarked.length} session(s) from the past 14 days are still unmarked.</b>
            {' '}Sessions unmarked for more than 48 hours are escalated to the HOD.
          </div>
          <table className="tbl">
            <thead><tr><th>Date</th><th>Period</th><th>Subject</th><th>Section</th><th>Room</th><th></th></tr></thead>
            <tbody>
              {data.unmarked.map((s, i) => (
                <tr key={i}>
                  <td>{s.date}</td><td><b>{s.period}</b></td><td>{s.subCode}</td>
                  <td>{s.section}</td><td>{s.room}</td>
                  <td><Link className="btn primary sm" to={`/faculty/mark/${s.id}/${s.date}`}>Mark now</Link></td>
                </tr>
              ))}
            </tbody>
          </table>
        </>}

      <h2 className="h2">Recently marked</h2>
      <table className="tbl">
        <thead><tr><th>Date</th><th>Period</th><th>Subject</th><th>Section</th><th>Present</th><th>Absent</th><th>Late/OD</th><th></th></tr></thead>
        <tbody>
          {data.recent.map((r) => (
            <tr key={r.id}>
              <td>{r.date}</td><td>{r.period}</td><td>{r.subCode}</td><td>{r.section}</td>
              <td className="ok"><b>{r.p}</b></td><td className="bad">{r.a}</td><td className="late">{r.l}</td>
              <td><Link className="btn ghost sm" to={`/corrections/new?session=${r.id}`}>Correct</Link></td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}
