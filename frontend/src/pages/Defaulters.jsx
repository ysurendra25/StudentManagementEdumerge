import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { get, post } from '../api.js'

export default function Defaulters() {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [msg, setMsg] = useState(null)
  const [selected, setSelected] = useState(new Set())
  const [dept, setDept] = useState('')
  const [section, setSection] = useState('')
  const [threshold, setThreshold] = useState(75)

  const load = () => {
    const q = new URLSearchParams()
    if (dept) q.set('dept', dept)
    if (section) q.set('section', section)
    q.set('threshold', threshold)
    get(`/api/defaulters?${q}`).then((d) => { setData(d); setSelected(new Set()) }).catch((e) => setError(e.message))
  }
  useEffect(load, [dept, section, threshold])

  const toggle = (id) => setSelected((s) => {
    const n = new Set(s)
    n.has(id) ? n.delete(id) : n.add(id)
    return n
  })

  const notify = async () => {
    try {
      const res = await post('/api/defaulters/notify', { studentIds: [...selected] })
      setMsg(res.message)
    } catch (e) { setError(e.message) }
  }

  if (error && !data) return <div className="flash error">{error}</div>
  if (!data) return <p className="muted">Loading…</p>

  return (
    <>
      <h1 className="h">Low-Attendance Defaulters</h1>
      <p className="muted">Rule: below {data.threshold}% aggregate is ineligible for end-semester exams (configurable).
        {data.rows.length} student(s) matched.</p>
      {error && <div className="flash error">{error}</div>}
      {msg && <div className="flash success">{msg}</div>}

      <div className="card" style={{ display: 'flex', gap: 18, alignItems: 'end', flexWrap: 'wrap' }}>
        <div>
          <label>Dept</label>
          <select value={dept} onChange={(e) => setDept(e.target.value)}>
            <option value="">All</option>
            {data.departments.map((d) => <option key={d.id} value={d.id}>{d.code}</option>)}
          </select>
        </div>
        <div>
          <label>Section</label>
          <select value={section} onChange={(e) => setSection(e.target.value)}>
            <option value="">All</option>
            {['A', 'B', 'C', 'D'].map((s) => <option key={s}>{s}</option>)}
          </select>
        </div>
        <div>
          <label>Threshold %</label>
          <input type="number" value={threshold} min="1" max="100" step="0.5"
            onChange={(e) => setThreshold(e.target.value)} style={{ width: 90 }} />
        </div>
        <a className="btn ghost" style={{ marginBottom: 2 }}
          href={`/api/defaulters/export?threshold=${threshold}${dept ? `&dept=${dept}` : ''}${section ? `&section=${section}` : ''}`}>
          Export CSV</a>
      </div>

      <table className="tbl">
        <thead><tr><th></th><th>Student</th><th>Roll No</th><th>Dept/Sec</th><th>Aggregate</th>
          <th>Classes needed to reach {data.threshold}%</th><th>Recoverable?</th><th>Last notified</th></tr></thead>
        <tbody>
          {data.rows.map((r) => (
            <tr key={r.id} className={r.critical ? 'crit' : ''}>
              <td><input type="checkbox" style={{ width: 'auto' }} checked={selected.has(r.id)}
                onChange={() => toggle(r.id)} /></td>
              <td><Link to={`/students/${r.id}`}>{r.name}</Link></td>
              <td className="mono">{r.rollNo}</td>
              <td>{r.dept} / {r.section}</td>
              <td><b className={r.critical ? 'bad' : 'am'}>{r.pct}%</b></td>
              <td>+{r.need} consecutive</td>
              <td>{r.need === 0 ? '—' : r.recoverable
                ? <span className="badge ok">Yes ({r.remaining} sessions left)</span>
                : <span className="badge crit">No — condonation required</span>}</td>
              <td className="small muted">{r.lastNotified || '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <div className="row-end">
        <span className="muted small">Select students, then notify parents (simulated SMS + email).</span>
        <button className="btn primary" disabled={selected.size === 0} onClick={notify}>
          Notify selected parents ({selected.size})</button>
      </div>
    </>
  )
}
