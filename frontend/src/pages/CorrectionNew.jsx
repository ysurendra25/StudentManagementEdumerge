import { useEffect, useState } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { get, post } from '../api.js'

export default function CorrectionNew() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const [sessions, setSessions] = useState([])
  const [sessionId, setSessionId] = useState(params.get('session') || '')
  const [roster, setRoster] = useState(null)
  const [studentId, setStudentId] = useState('')
  const [newStatus, setNewStatus] = useState('P')
  const [category, setCategory] = useState('medical')
  const [reason, setReason] = useState('')
  const [error, setError] = useState(null)
  const [msg, setMsg] = useState(null)

  useEffect(() => { get('/api/corrections/raise-options').then(setSessions).catch((e) => setError(e.message)) }, [])

  useEffect(() => {
    if (!sessionId) { setRoster(null); return }
    get(`/api/corrections/raise-roster/${sessionId}`)
      .then((d) => { setRoster(d); if (d.roster.length) setStudentId(String(d.roster[0].id)) })
      .catch((e) => setError(e.message))
  }, [sessionId])

  const submit = async (e) => {
    e.preventDefault()
    try {
      await post('/api/corrections', { sessionId: Number(sessionId), studentId: Number(studentId),
        newStatus, category, reason })
      setMsg('Correction request submitted to the HOD for approval.')
      setTimeout(() => navigate('/faculty'), 1600)
    } catch (err) { setError(err.message) }
  }

  const current = sessionId && roster?.roster?.find((s) => String(s.id) === studentId)

  return (
    <>
      <h1 className="h">Raise a Correction</h1>
      <p className="muted">You can raise corrections only for sessions you marked. Requests go to the HOD for approval.</p>
      {error && <div className="flash error">{error}</div>}
      {msg && <div className="flash success">{msg}</div>}

      <div className="card">
        <h3 className="h3">1. Choose a session you marked recently</h3>
        <table className="tbl">
          <thead><tr><th>Date</th><th>Period</th><th>Subject</th><th>Section</th><th></th></tr></thead>
          <tbody>
            {sessions.map((s) => (
              <tr key={s.id} style={String(s.id) === String(sessionId) ? { background: '#eef2ff' } : {}}>
                <td>{s.date}</td><td>{s.period}</td><td>{s.subCode}</td><td>{s.section}</td>
                <td><button className="btn ghost sm" onClick={() => setSessionId(String(s.id))}>Select</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {roster && (
        <div className="card">
          <h3 className="h3">2. Student and change</h3>
          <form onSubmit={submit}>
            <label>Student</label>
            <select value={studentId} onChange={(e) => setStudentId(e.target.value)}>
              {roster.roster.map((s) => (
                <option key={s.id} value={s.id}>
                  {s.rollNo} — {s.name} (currently {s.status})</option>
              ))}
            </select>
            {current && <p className="muted small" style={{ marginTop: 6 }}>
              Current status: <span className={`badge ${current.status}`}>{current.status}</span></p>}
            <label>New status</label>
            <select value={newStatus} onChange={(e) => setNewStatus(e.target.value)}>
              <option value="P">Present</option>
              <option value="A">Absent</option>
              <option value="L">Late / OD</option>
            </select>
            <label>Reason category</label>
            <select value={category} onChange={(e) => setCategory(e.target.value)}>
              <option value="medical">Medical</option>
              <option value="sports_od">Sports / OD</option>
              <option value="marked_in_error">Marked in error</option>
              <option value="other">Other</option>
            </select>
            <label>Reason (min 10 characters)</label>
            <textarea rows="3" value={reason} onChange={(e) => setReason(e.target.value)} required minLength={10}
              placeholder="e.g. Student was in the health centre during this period; verified with staff." />
            <button className="btn primary" style={{ marginTop: 12 }}>Submit for approval</button>
          </form>
        </div>
      )}
    </>
  )
}
