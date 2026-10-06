import { Link, Outlet, Route, Routes } from 'react-router'
import { AdminMembersPage } from './features/admin-members/admin-members-page'
import { AdminCouponRegistrationPage } from './features/admin-coupons/admin-coupon-registration-page'
import { AdminTimeSlotsPage } from './features/admin-timeslots/admin-timeslots-page'
import { AdminReservationsPage } from './features/admin-reservations/admin-reservations-page'
import { AdminManualReservationPage } from './features/admin-reservations/admin-manual-reservation-page'
import { AdminAttendancePage } from './features/admin-attendance/admin-attendance-page'
import { AdminDashboardPage } from './features/admin-dashboard/admin-dashboard-page'
import { AdminMonthlyRideStatisticsPage } from './features/admin-monthly-ride-statistics/admin-monthly-ride-statistics-page'
import { AdminWeeklyOperationsCalendarPage } from './features/admin-weekly-operations-calendar/admin-weekly-operations-calendar-page'
import { AdminAuditPage } from './features/admin-audit/admin-audit-page'
import { AdminScheduleConfigurationPage } from './features/admin-schedule-configuration/admin-schedule-configuration-page'
import { AdminScheduleClosuresPage } from './features/admin-schedule-closures/admin-schedule-closures-page'
import { AdminFamilyGroupsPage } from './features/admin-family-groups/admin-family-groups-page'
import { ReservationCalendarPage } from './features/reservation-calendar/reservation-calendar-page'
import { ReservationApplicationPage } from './features/reservation-application/reservation-application-page'
import { MyReservationsPage } from './features/my-reservations/my-reservations-page'
import { MyCouponsPage } from './features/my-coupons/my-coupons-page'
import { MemberHomePage } from './features/member-home/member-home-page'
import { ReservationChangePage } from './features/reservation-change/reservation-change-page'
import { ReservationCancelPage } from './features/reservation-cancel/reservation-cancel-page'
import { AuthAccessDeniedPage } from './features/auth/auth-access-denied-page'
import { AuthLoginPage } from './features/auth/auth-login-page'
import { AuthNavigation } from './features/auth/auth-navigation'
import { AuthProvider } from './features/auth/auth-provider'
import { AuthRouteGuard } from './features/auth/auth-route-guard'
import { AuthSignupPage } from './features/auth/auth-signup-page'
import { useAuth } from './features/auth/use-auth'
import { AdminLayout } from './layouts/admin-layout'
import { MemberLayout } from './layouts/member-layout'
import './app.css'

function PublicLayout() {
  return (
    <div className="public-app-shell">
      <AuthNavigation />
      <div id="main-content" tabIndex={-1}>
        <Outlet />
      </div>
    </div>
  )
}

function HomePage() {
  const { account } = useAuth()

  if (account?.role === 'MEMBER') {
    return <MemberHomePage />
  }

  return (
    <main>
      <h1>마장 예약</h1>
      <nav aria-label="주요 메뉴">
        {account?.role === 'ADMIN' ? <Link to="/admin">관리자</Link> : null}
        {!account ? <Link to="/login">로그인 후 예약하기</Link> : null}
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
        <Link to="/admin/monthly-ride-statistics">월간 기승 현황</Link>
        <Link to="/admin/weekly-operations-calendar">주간 운영 캘린더</Link>
        <Link to="/admin/members">회원 및 기승 승인</Link>
        <Link to="/admin/family-groups">가족 그룹 관리</Link>
        <Link to="/admin/coupons/new">쿠폰 등록</Link>
        <Link to="/admin/timeslots">시간대 및 정원</Link>
        <Link to="/admin/schedule-configuration">정규 시간표 및 정기 휴일</Link>
        <Link to="/admin/schedule-closures">날짜 휴무 및 개별 휴강</Link>
        <Link to="/admin/reservations">예약 승인 및 입금 확인</Link>
        <Link to="/admin/attendance">수업 완료 및 노쇼</Link>
        <Link to="/admin/audit-logs">예약 감사 이력</Link>
      </nav>
    </main>
  )
}

function AdminManualReservationRoute() {
  const { account } = useAuth()
  return account ? <AdminManualReservationPage key={account.subject} accountSubject={account.subject} /> : null
}

export default function App() {
  return (
    <AuthProvider>
      <a className="skip-link" href="#main-content">본문 콘텐츠로 건너뛰기</a>
      <Routes>
        <Route element={<PublicLayout />}>
          <Route path="/" element={<HomePage />} />
          <Route path="/login" element={<AuthLoginPage />} />
          <Route path="/signup" element={<AuthSignupPage />} />
          <Route path="/forbidden" element={<AuthAccessDeniedPage />} />
        </Route>

        <Route element={<AuthRouteGuard requiredRole="MEMBER" />}>
          <Route element={<MemberLayout />}>
            <Route path="/reservations" element={<ReservationCalendarPage />} />
            <Route path="/reservations/new" element={<ReservationApplicationPage />} />
            <Route path="/my/reservations" element={<MyReservationsPage />} />
            <Route path="/my/reservations/:reservationId/change" element={<ReservationChangePage />} />
            <Route path="/my/reservations/:reservationId/cancel" element={<ReservationCancelPage />} />
            <Route path="/my/coupons" element={<MyCouponsPage />} />
          </Route>
        </Route>

        <Route element={<AuthRouteGuard requiredRole="ADMIN" />}>
          <Route element={<AdminLayout />}>
            <Route path="/admin" element={<AdminHomePage />} />
            <Route path="/admin/dashboard" element={<AdminDashboardPage />} />
            <Route path="/admin/monthly-ride-statistics" element={<AdminMonthlyRideStatisticsPage />} />
            <Route path="/admin/weekly-operations-calendar" element={<AdminWeeklyOperationsCalendarPage />} />
            <Route path="/admin/members" element={<AdminMembersPage />} />
            <Route path="/admin/family-groups" element={<AdminFamilyGroupsPage />} />
            <Route path="/admin/coupons/new" element={<AdminCouponRegistrationPage />} />
            <Route path="/admin/timeslots" element={<AdminTimeSlotsPage />} />
            <Route path="/admin/schedule-configuration" element={<AdminScheduleConfigurationPage />} />
            <Route path="/admin/schedule-closures" element={<AdminScheduleClosuresPage />} />
            <Route path="/admin/reservations" element={<AdminReservationsPage />} />
            <Route path="/admin/reservations/new" element={<AdminManualReservationRoute />} />
            <Route path="/admin/attendance" element={<AdminAttendancePage />} />
            <Route path="/admin/audit-logs" element={<AdminAuditPage />} />
          </Route>
        </Route>
      </Routes>
    </AuthProvider>
  )
}
