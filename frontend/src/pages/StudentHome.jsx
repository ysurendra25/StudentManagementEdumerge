import { useEffect, useState } from 'react'
import { get, post } from '../api.js'

export default function StudentHome() {
  const [me, setMe] = useState(null)
  const [error, setError] = useState(null)
  const [msg, setMsg] = useState(null)
  const [sessionId, setSessionId] = useState('')
  const [category, setCategory] = useState('medical')
  const [reason, setReason] = useState('')

  useEffect(() => { get('/api/student/me').then(setMe).catch((e) => setError(e.message)) }, [])
  if (error && !me) return <div className="flash error">{error}</div>
  if (!me) return <p className="muted">Loading…</p>

  const pct = me.pct
  const eligible = pct >= me.threshold
  const absents = me.history.filter((h) => h.status !== 'P')

  const submit = async (e) => {
    e.preventDefault()
    try {
      const res = await post('/api/student/correction', { sessionId: Number(sessionId), category, reason })
      setMsg(res.message)
      setReason('')
      get('/api/student/me').then(setMe)
    } catch (err) { setError(err.message) }
  }

  return (
    <>
      <div className="stu-head">
        <div>
          <h1 className="h">{me.name}</h1>
          <p className="muted">{me.dept} · Section {me.section} · Roll {me.rollNo} · Semester {me.semester}</p>
        </div>
        <div className={`ring ${eligible ? 'ok' : 'bad'}`} style={{ '--p': pct }}>
          <div><b>{pct}%</b><span>overall</span></div>
        </div>
      </div>
      <p className="muted">{me.present} of {me.held} sessions attended.
        {eligible
          ? <span className="badge ok">Eligible — above {me.threshold}%</span>
          : <span className="badge crit">Below {me.threshold}% — not eligible for end-sem exams</span>}</p>

      {error && <div className="flash error">{error}</div>}
      {msg && <div className="flash success">{msg}</div>}

      <h2 className="h2">Subject-wise attendance</h2>
      <table className="tbl">
        <thead><tr><th>Subject</th><th>Attended</th><th>Attendance</th><th>Status</th></tr></thead>
        <tbody>
          {me.subjects.map((s) => (
            <tr key={s.code}>
              <td>{s.code} — {s.name}</td><td>{s.present}/{s.held}</td>
              <td><div className="bar"><div className={s.pct >= me.threshold ? 'fb ok' : 'fb low'}
                style={{ width: s.pct + '%' }} /></div> {s.pct}%</td>
              <td>{s.pct >= me.threshold ? <span className="badge ok">OK</span> : <span className="badge crit">LOW</span>}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <h2 className="h2">Request a correction</h2>
      <div className="card">
        <p className="muted small">Pick a session where you were marked Absent or Late, explain why.
          Your faculty and HOD will review it.</p>
        {absents.length === 0
          ? <p className="empty">No absences to correct.</p>
          : <form onSubmit={submit}>
            <label>Session</label>
            <select value={sessionId} onChange={(e) => setSessionId(e.target.value)} required>
              <option value="">— choose —</option>
              {absents.map((h) => (
                <option key={h.sessionId} value={h.sessionId}>
                  {h.date} · Period {h.period} · {h.subCode} (marked {h.status})</option>
              ))}
            </select>
            <label>Reason category</label>
            <select value={category} onChange={(e) => setCategory(e.target.value)}>
              <option value="medical">Medical</option>
              <option value="sports_od">Sports / OD</option>
              <option value="other">Other</option>
            </select>
            <label>Reason (min 10 characters)</label>
            <textarea rows="3" value={reason} onChange={(e) => setReason(e.target.value)} required minLength={10}
              placeholder="e.g. I was representing the college at the inter-university meet on this date." />
            <button className="btn primary" style={{ marginTop: 12 }}>Submit request</button>
          </form>}
      </div>

      {me.corrections.length > 0 && <>
        <h2 className="h2">My correction requests</h2>
        <table className="tbl">
          <thead><tr><th>Change</th><th>Reason</th><th>Status</th><th>When</th></tr></thead>
          <tbody>
            {me.corrections.map((c) => (
              <tr key={c.id}>
                <td>{c.oldStatus} → {c.newStatus} <span className="badge cat">{c.category}</span></td>
                <td className="small">{c.reason}</td>
                <td><span className={`badge ${c.status}`}>{c.status.toUpperCase()}</span></td>
                <td className="small muted">{c.createdAt}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </>}

      <h2 className="h2">Recent sessions</h2>
      <table className="tbl">
        <thead><tr><th>Date</th><th>Period</th><th>Subject</th><th>Marked</th></tr></thead>
        <tbody>
          {me.history.map((h, i) => (
            <tr key={i}>
              <td>{h.date}</td><td>{h.period}</td><td>{h.subCode}</td>
              <td><span className={`badge ${h.status}`}>{h.status}</span></td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}
