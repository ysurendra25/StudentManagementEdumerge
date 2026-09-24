import { useEffect, useState } from 'react'
import { get, post } from '../api.js'

export default function Corrections() {
  const [status, setStatus] = useState('pending')
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [msg, setMsg] = useState(null)

  const load = (s = status) => get(`/api/corrections?status=${s}`)
    .then(setData).catch((e) => setError(e.message))
  useEffect(() => { load(status) }, [status])

  const decide = async (id, decision) => {
    try {
      const res = await post(`/api/corrections/${id}/decision`, { decision })
      setMsg(res.message)
      load()
    } catch (e) { setError(e.message) }
  }

  if (error && !data) return <div className="flash error">{error}</div>
  if (!data) return <p className="muted">Loading…</p>

  return (
    <>
      <h1 className="h">Attendance Corrections</h1>
      <p className="muted">Original entries are never overwritten — every change is versioned in the audit trail.</p>
      {error && <div className="flash error">{error}</div>}
      {msg && <div className="flash success">{msg}</div>}

      <div className="tabs">
        {['pending', 'approved', 'rejected', 'all'].map((s) => (
          <button key={s} className={status === s ? 'act' : ''} onClick={() => setStatus(s)}>
            {s[0].toUpperCase() + s.slice(1)}</button>
        ))}
      </div>

      {data.rows.length === 0 && <p className="empty">Nothing here.</p>}
      {data.rows.map((c) => (
        <div className="card" key={c.id}>
          <div className="corr-head">
            <div><b>{c.studentName}</b> <span className="mono small">({c.rollNo})</span>
              {' '}<span className={`badge ${c.status}`}>{c.status.toUpperCase()}</span></div>
            <div className="muted small">{c.subCode} · {c.date} · Period {c.period} · by {c.requester}</div>
          </div>
          <div className="corr-body">
            <span className={`badge ${c.oldStatus}`}>{c.oldStatus}</span> →
            <span className={`badge ${c.newStatus}`}>{c.newStatus}</span>
            <span className="badge cat">{c.category}</span>
            <p className="reason">{c.reason}</p>
            {c.status === 'pending'
              ? <div className="inline">
                  <button className="btn ok sm" onClick={() => decide(c.id, 'approve')}>Approve</button>
                  <button className="btn bad sm" onClick={() => decide(c.id, 'reject')}>Reject</button>
                </div>
              : <div className="muted small">decided {c.decidedAt}</div>}
          </div>
        </div>
      ))}
    </>
  )
}
