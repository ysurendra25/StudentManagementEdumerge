import { useState } from 'react'
import { post } from '../api.js'

export default function Login({ onLogin }) {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)

  const submit = async (e) => {
    e.preventDefault()
    try { onLogin(await post('/api/auth/login', { username, password })) }
    catch (err) { setError(err.message) }
  }

  const demo = async (u) => {
    try { onLogin(await post(`/api/auth/demo/${u}`)) }
    catch (err) { setError(err.message) }
  }

  return (
    <div className="login-wrap">
      <div className="login-card">
        <h1>Smart<b>Attendance</b></h1>
        <p className="muted small" style={{ marginBottom: 14 }}>
          Attendance management · corrections · defaulters · reports</p>
        {error && <div className="flash error">{error}</div>}
        <form onSubmit={submit}>
          <label>Username</label>
          <input value={username} onChange={(e) => setUsername(e.target.value)}
                 placeholder="e.g. mehta / 23cseb05 / rao / admin" required />
          <label>Password</label>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          <button className="btn primary" style={{ width: '100%', marginTop: 14 }}>Sign in</button>
        </form>
        <div className="demo">
          <div className="demo-h">Reviewer quick login (demo mode)</div>
          <button className="chip" onClick={() => demo('mehta')}>Faculty — Dr. Mehta</button>
          <button className="chip" onClick={() => demo('rao')}>HOD — Dr. Rao</button>
          <button className="chip" onClick={() => demo('admin')}>Admin</button>
          <button className="chip" onClick={() => demo('23cseb05')}>Student — Esha</button>
        </div>
      </div>
    </div>
  )
}
