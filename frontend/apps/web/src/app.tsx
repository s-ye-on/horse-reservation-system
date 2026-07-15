import { Link, Route, Routes } from 'react-router'
import { AdminMembersPage } from './features/admin-members/admin-members-page'
import './app.css'

function HomePage() {
  return (
    <main>
      <h1>마장 예약</h1>
      <nav aria-label="주요 메뉴">
        <Link to="/reservations">회원 예약</Link>
        <Link to="/admin">관리자</Link>
      </nav>
    </main>
  )
}

function PlaceholderPage({ title }: { title: string }) {
  return (
    <main>
      <Link to="/">홈</Link>
      <h1>{title}</h1>
    </main>
  )
}

function AdminHomePage() {
  return (
    <main>
      <Link to="/">홈</Link>
      <h1>관리자</h1>
      <nav aria-label="관리자 메뉴">
        <Link to="/admin/members">회원 및 기승 승인</Link>
      </nav>
    </main>
  )
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/reservations" element={<PlaceholderPage title="회원 예약" />} />
      <Route path="/admin" element={<AdminHomePage />} />
      <Route path="/admin/members" element={<AdminMembersPage />} />
    </Routes>
  )
}
