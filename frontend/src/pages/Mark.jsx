import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { get, post } from '../api.js'

export default function Mark() {
  const { slotId, date } = useParams()
  const navigate = useNavigate()
  const [roster, setRoster] = useState(null)
  const [statuses, setStatuses] = useState({})
  const [error, setError] = useState(null)
  const [msg, setMsg] = useState(null)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    get(`/api/faculty/roster/${slotId}/${date}`)
      .then((d) => {
        setRoster(d)
        const s = {}
        d.roster.forEach((st) => { s[st.id] = 'P' })
        setStatuses(s)
      })
      .catch((e) => setError(e.message))
  }, [slotId, date])

  if (error) return <><div className="flash error">{error}</div>
    <button className="btn ghost" onClick={() => navigate('/faculty')}>Back to sessions</button></>
  if (!roster) return <p className="muted">Loading…</p>

  const set = (id, st) => setStatuses((s) => ({ ...s, [id]: st }))
  const counts = Object.values(statuses).reduce((a, v) => ({ ...a, [v]: (a[v] || 0) + 1 }), {})

  const submit = async () => {
    setBusy(true)
    try {
      const res = await post(`/api/faculty/mark/${slotId}/${date}`, { statuses })
      setMsg(`Attendance submitted: ${res.present} present, ${res.absent} absent, ${res.late} late/OD.`)
      setTimeout(() => navigate('/faculty'), 1600)
    } catch (e) { setError(e.message); setBusy(false) }
  }

  return (
    <>
      {msg && <div className="flash success">{msg}</div>}
      <div className="card mark-head">
        <div>
          <h1 className="h">{roster.subCode} — {roster.subName}</h1>
          <p className="muted small">{roster.dept} / Section {roster.section} · Period {roster.period}
            {' '}· Room {roster.room} · {roster.date}</p>
        </div>
        <div className="mark-note">Default is <b>Present</b> — tap <b>A</b> for absent, <b>L</b> for late/OD.
          Submitting locks the register; changes go through the correction workflow.</div>
      </div>
      <div className="card">
        <table className="tbl">
          <thead><tr><th>Roll No</th><th>Student</th><th style={{ textAlign: 'center' }}>P / A / L</th></tr></thead>
          <tbody>
            {roster.roster.map((s) => (
              <tr key={s.id}>
                <td className="mono">{s.rollNo}</td>
                <td>{s.name}</td>
                <td style={{ textAlign: 'center' }}>
                  <div className="seg">
                    {['P', 'A', 'L'].map((st) => (
                      <button key={st} type="button" className={statuses[s.id] === st ? `on-${st}` : ''}
                        onClick={() => set(s.id, st)}>{st}</button>
                    ))}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="mark-foot">
          <span className="muted small">
            {roster.roster.length} students on roll · P: {counts.P || 0} · A: {counts.A || 0} · L: {counts.L || 0}</span>
          <button className="btn primary" disabled={busy} onClick={submit}>
            {busy ? 'Submitting…' : 'Submit attendance'}</button>
        </div>
      </div>
    </>
  )
}
