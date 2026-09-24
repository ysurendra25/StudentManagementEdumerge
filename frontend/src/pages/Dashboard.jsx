import { useEffect, useState } from 'react'
import { get } from '../api.js'

export default function Dashboard() {
  const [d, setD] = useState(null)
  const [error, setError] = useState(null)
  useEffect(() => { get('/api/dashboard').then(setD).catch((e) => setError(e.message)) }, [])
  if (error) return <div className="flash error">{error}</div>
  if (!d) return <p className="muted">Loading…</p>

  return (
    <>
      <h1 className="h">Attendance Dashboard <span className="muted small">all departments · updated live</span></h1>
      <div className="cards">
        <div className="card kpi">
          <div className="lab">Avg attendance (this month)</div>
          <div className="val">{d.mavg}%</div>
          <div className={d.mavg >= d.pavg ? 'delta up' : 'delta dn'}>
            {d.mavg >= d.pavg ? '▲' : '▼'} {Math.abs(Math.round((d.mavg - d.pavg) * 10) / 10)}% vs last month</div>
        </div>
        <div className="card kpi">
          <div className="lab">Sessions marked today</div>
          <div className="val">{d.todayMarked}<span className="sub-val">/{d.todayTotal}</span></div>
          <div className="delta am">{d.todayTotal - d.todayMarked} pending</div>
        </div>
        <div className="card kpi">
          <div className="lab">Defaulters ({'<'} 75%)</div>
          <div className="val">{d.defaulters}</div>
          <div className="delta dn">{d.critical} critical ({'<'} 65%)</div>
        </div>
      </div>

      {d.unmarked.length > 0 &&
        <div className="alert warn">
          <b>{d.unmarked.length} past session(s) unmarked for more than 24h.</b>
          {' '}Reminders are sent to the faculty; open items escalate to the HOD after 48h.
          <table className="tbl tight" style={{ marginTop: 8 }}>
            <thead><tr><th>Date</th><th>Faculty</th><th>Subject</th><th>Section</th><th>Period</th></tr></thead>
            <tbody>
              {d.unmarked.slice(0, 5).map((u, i) => (
                <tr key={i}><td>{u.date}</td><td>{u.facName}</td><td>{u.subCode}</td>
                  <td>{u.section}</td><td>{u.period}</td></tr>
              ))}
            </tbody>
          </table>
        </div>}

      <div className="two-col">
        <div className="card">
          <h3 className="h3">Institution attendance trend</h3>
          <div className="bars">
            {d.trend.map((t) => (
              <div className="bw" key={t.month}>
                <div className="bv">{Math.round(t.pct)}%</div>
                <div className="b" style={{ height: Math.round(t.pct * 1.4) }} />
                <div className="bl">{t.month.slice(5)}</div>
              </div>
            ))}
          </div>
        </div>
        <div className="card">
          <h3 className="h3">Dept-wise attendance</h3>
          {d.deptWise.map((x) => (
            <div className="deptrow" key={x.code}>
              <span className="dot" /> {x.code}
              <div className="bar"><div className={x.pct >= 75 ? 'fb ok' : 'fb low'}
                style={{ width: Math.round(x.pct) + '%' }} /></div>
              <b>{Math.round(x.pct)}%</b>
            </div>
          ))}
        </div>
      </div>

      <div className="card">
        <h3 className="h3">Recent activity (audit trail)</h3>
        <table className="tbl">
          <tbody>
            {d.activity.map((a, i) => (
              <tr key={i}>
                <td className="mono small">{a.ts}</td><td><b>{a.action}</b></td>
                <td className="mono small">{a.actor}</td><td className="small muted">{a.details}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  )
}
