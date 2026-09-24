import { useEffect, useState } from 'react'
import { get } from '../api.js'

export default function Audit() {
  const [rows, setRows] = useState(null)
  const [error, setError] = useState(null)
  useEffect(() => { get('/api/audit').then(setRows).catch((e) => setError(e.message)) }, [])
  if (error) return <div className="flash error">{error}</div>
  if (!rows) return <p className="muted">Loading…</p>

  return (
    <>
      <h1 className="h">Audit Trail</h1>
      <p className="muted">Append-only log of every marking, correction and notification action (latest 150).</p>
      <table className="tbl">
        <thead><tr><th>When</th><th>Action</th><th>Actor</th><th>Details</th></tr></thead>
        <tbody>
          {rows.map((a, i) => (
            <tr key={i}>
              <td className="mono small">{a.ts}</td>
              <td><span className="badge">{a.action}</span></td>
              <td className="mono small">{a.actor}</td>
              <td className="small muted">{a.details}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}
