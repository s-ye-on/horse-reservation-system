import { Link, Route, Routes } from 'react-router'
import { AdminMembersPage } from './features/admin-members/admin-members-page'
import { AdminCouponRegistrationPage } from './features/admin-coupons/admin-coupon-registration-page'
import { AdminTimeSlotsPage } from './features/admin-timeslots/admin-timeslots-page'
import { AdminReservationsPage } from './features/admin-reservations/admin-reservations-page'
import { AdminAttendancePage } from './features/admin-attendance/admin-attendance-page'
import { AdminDashboardPage } from './features/admin-dashboard/admin-dashboard-page'
import { AdminAuditPage } from './features/admin-audit/admin-audit-page'
import { AdminScheduleConfigurationPage } from './features/admin-schedule-configuration/admin-schedule-configuration-page'
import { ReservationCalendarPage } from './features/reservation-calendar/reservation-calendar-page'
import { ReservationApplicationPage } from './features/reservation-application/reservation-application-page'
import { MyReservationsPage } from './features/my-reservations/my-reservations-page'
import { MyCouponsPage } from './features/my-coupons/my-coupons-page'
import { ReservationChangePage } from './features/reservation-change/reservation-change-page'
import { ReservationCancelPage } from './features/reservation-cancel/reservation-cancel-page'
import './app.css'

function HomePage() {
  return (
    <main>
      <h1>마장 예약</h1>
      <nav aria-label="주요 메뉴">
        <Link to="/reservations">회원 예약</Link>
        <Link to="/my/reservations">내 예약</Link>
        <Link to="/my/coupons">내 쿠폰</Link>
        <Link to="/admin">관리자</Link>
      </nav>
    </main>
  )
}

function AdminHomePage() {
  return (
    <main>
      <Link to="/">홈</Link>
      <h1>관리자</h1>
      <nav aria-label="관리자 메뉴">
        <Link to="/admin/dashboard">운영 대시보드</Link>
        <Link to="/admin/members">회원 및 기승 승인</Link>
        <Link to="/admin/coupons/new">10회권 쿠폰 등록</Link>
        <Link to="/admin/timeslots">시간대 및 정원</Link>
        <Link to="/admin/schedule-configuration">정규 시간표 및 정기 휴일</Link>
        <Link to="/admin/reservations">예약 승인 및 입금 확인</Link>
        <Link to="/admin/attendance">수업 완료 및 노쇼</Link>
        <Link to="/admin/audit-logs">예약 감사 이력</Link>
      </nav>
    </main>
  )
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<HomePage />} />
      <Route path="/reservations" element={<ReservationCalendarPage />} />
      <Route path="/reservations/new" element={<ReservationApplicationPage />} />
      <Route path="/my/reservations" element={<MyReservationsPage />} />
      <Route path="/my/reservations/:reservationId/change" element={<ReservationChangePage />} />
      <Route path="/my/reservations/:reservationId/cancel" element={<ReservationCancelPage />} />
      <Route path="/my/coupons" element={<MyCouponsPage />} />
      <Route path="/admin" element={<AdminHomePage />} />
      <Route path="/admin/dashboard" element={<AdminDashboardPage />} />
      <Route path="/admin/members" element={<AdminMembersPage />} />
      <Route path="/admin/coupons/new" element={<AdminCouponRegistrationPage />} />
      <Route path="/admin/timeslots" element={<AdminTimeSlotsPage />} />
      <Route path="/admin/schedule-configuration" element={<AdminScheduleConfigurationPage />} />
      <Route path="/admin/reservations" element={<AdminReservationsPage />} />
      <Route path="/admin/attendance" element={<AdminAttendancePage />} />
      <Route path="/admin/audit-logs" element={<AdminAuditPage />} />
    </Routes>
  )
}
