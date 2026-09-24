import { useEffect, useState } from 'react'
import { Routes, Route, Navigate, useNavigate, Link } from 'react-router-dom'
import { get, post } from './api.js'
import Login from './pages/Login.jsx'
import FacultyHome from './pages/FacultyHome.jsx'
import Mark from './pages/Mark.jsx'
import CorrectionNew from './pages/CorrectionNew.jsx'
import Dashboard from './pages/Dashboard.jsx'
import Defaulters from './pages/Defaulters.jsx'
import Corrections from './pages/Corrections.jsx'
import StudentHome from './pages/StudentHome.jsx'
import StudentDetail from './pages/StudentDetail.jsx'
import Audit from './pages/Audit.jsx'

export const me = { current: null }

function Nav({ user, onLogout }) {
  const links =
    user.role === 'hod' || user.role === 'admin'
      ? [['/dashboard', 'Dashboard'], ['/defaulters', 'Defaulters'], ['/corrections', 'Corrections'], ['/audit', 'Audit']]
      : user.role === 'faculty'
        ? [['/faculty', 'My Sessions'], ['/corrections/new', 'Raise Correction']]
        : [['/student', 'My Attendance']]
  return (
    <nav className="topnav">
      <div className="brand">Smart<b>Attendance</b></div>
      <div className="navlinks">
        {links.map(([to, label]) => (
          <Link key={to} to={to}>{label}</Link>
        ))}
      </div>
      <div className="userbox">
        <span className="u">{user.username}</span>
        <span className="r">{user.role}</span>
        <button className="lo" onClick={onLogout}>Logout</button>
      </div>
    </nav>
  )
}

export default function App() {
  const [user, setUser] = useState(null)
  const [ready, setReady] = useState(false)
  const navigate = useNavigate()

  useEffect(() => {
    get('/api/auth/me')
      .then((u) => { setUser(u); me.current = u })
      .catch(() => { setUser(null); me.current = null })
      .finally(() => setReady(true))
  }, [])

  if (!ready) return null

  if (!user) {
    return (
      <Routes>
        <Route path="*" element={<Login onLogin={(u) => { setUser(u); me.current = u; navigate(u.role === 'student' ? '/student' : u.role === 'faculty' ? '/faculty' : '/dashboard') }} />} />
      </Routes>
    )
  }

  const home = user.role === 'student' ? '/student' : user.role === 'faculty' ? '/faculty' : '/dashboard'
  const can = (...roles) => roles.includes(user.role)

  return (
    <>
      <Nav user={user} onLogout={async () => { await post('/api/auth/logout').catch(() => {}); setUser(null) }} />
      <div className="container">
        <Routes>
          <Route path="/" element={<Navigate to={home} replace />} />
          <Route path="/login" element={<Navigate to={home} replace />} />
          {can('faculty', 'admin') && <Route path="/faculty" element={<FacultyHome />} />}
          {can('faculty') && <Route path="/faculty/mark/:slotId/:date" element={<Mark />} />}
          {can('faculty', 'admin') && <Route path="/corrections/new" element={<CorrectionNew />} />}
          {can('hod', 'admin') && <Route path="/dashboard" element={<Dashboard />} />}
          {can('hod', 'admin') && <Route path="/defaulters" element={<Defaulters />} />}
          {can('hod', 'admin') && <Route path="/corrections" element={<Corrections />} />}
          {can('hod', 'admin') && <Route path="/students/:id" element={<StudentDetail />} />}
          {can('hod', 'admin') && <Route path="/audit" element={<Audit />} />}
          {can('student') && <Route path="/student" element={<StudentHome />} />}
          <Route path="*" element={<Navigate to={home} replace />} />
        </Routes>
      </div>
    </>
  )
}
