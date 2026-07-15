import { Link, Route, Routes } from 'react-router'
import { AdminMembersPage } from './features/admin-members/admin-members-page'
import { AdminCouponRegistrationPage } from './features/admin-coupons/admin-coupon-registration-page'
import { AdminTimeSlotsPage } from './features/admin-timeslots/admin-timeslots-page'
import { AdminReservationsPage } from './features/admin-reservations/admin-reservations-page'
import { AdminAttendancePage } from './features/admin-attendance/admin-attendance-page'
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
        <Link to="/admin/coupons/new">10회권 쿠폰 등록</Link>
        <Link to="/admin/timeslots">시간대 및 정원</Link>
        <Link to="/admin/reservations">예약 승인 및 입금 확인</Link>
        <Link to="/admin/attendance">수업 완료 및 노쇼</Link>
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
      <Route path="/admin/coupons/new" element={<AdminCouponRegistrationPage />} />
      <Route path="/admin/timeslots" element={<AdminTimeSlotsPage />} />
      <Route path="/admin/reservations" element={<AdminReservationsPage />} />
      <Route path="/admin/attendance" element={<AdminAttendancePage />} />
    </Routes>
  )
}
