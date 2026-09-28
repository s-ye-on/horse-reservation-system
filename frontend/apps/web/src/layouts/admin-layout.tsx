import { NavLink, Outlet, useLocation, useNavigate } from 'react-router'
import { AppLogo } from '../components/app-logo'
import { AuthNavigation } from '../features/auth/auth-navigation'

const ADMIN_NAVIGATION_ITEMS = [
  { to: '/admin', label: '운영 홈' },
  { to: '/admin/dashboard', label: '운영 대시보드' },
  { to: '/admin/monthly-ride-statistics', label: '월간 기승 현황' },
  { to: '/admin/weekly-operations-calendar', label: '주간 운영 캘린더' },
  { to: '/admin/members', label: '회원 및 기승 승인' },
  { to: '/admin/family-groups', label: '가족 그룹 관리' },
  { to: '/admin/coupons/new', label: '쿠폰 등록' },
  { to: '/admin/timeslots', label: '시간대 및 정원' },
  { to: '/admin/schedule-configuration', label: '정규 시간표 및 정기 휴일' },
  { to: '/admin/schedule-closures', label: '날짜 휴무 및 개별 휴강' },
  { to: '/admin/reservations', label: '예약 승인 및 입금 확인' },
  { to: '/admin/attendance', label: '수업 완료 및 노쇼' },
  { to: '/admin/audit-logs', label: '예약 감사 이력' },
] as const

export function AdminLayout() {
  const location = useLocation()
  const navigate = useNavigate()
  const currentPath = ADMIN_NAVIGATION_ITEMS.find(({ to }) => to === location.pathname)?.to ?? '/admin'

  return (
    <div className="admin-app-shell">
      <aside className="admin-layout-sidebar">
        <div>
          <AppLogo />
          <div className="admin-layout-heading">
            <span>Management Console</span>
            <strong>마장 운영 관리</strong>
          </div>
          <nav className="admin-layout-navigation" aria-label="관리자 전체 메뉴">
            {ADMIN_NAVIGATION_ITEMS.map(({ to, label }) => (
              <NavLink key={to} to={to} end={to === '/admin'}>
                {label}
              </NavLink>
            ))}
          </nav>
        </div>
        <p className="admin-layout-footer">승마클럽 관리 본부</p>
      </aside>

      <div className="admin-layout-workspace">
        <AuthNavigation variant="admin" />
        <nav className="admin-layout-mobile-navigation" aria-label="모바일 관리자 메뉴">
          <label htmlFor="admin-mobile-navigation">관리 업무 이동</label>
          <select
            id="admin-mobile-navigation"
            value={currentPath}
            onChange={(event) => navigate(event.target.value)}
          >
            {ADMIN_NAVIGATION_ITEMS.map(({ to, label }) => (
              <option key={to} value={to}>{label}</option>
            ))}
          </select>
        </nav>
        <div className="admin-layout-content" id="main-content" tabIndex={-1}>
          <Outlet />
        </div>
      </div>
    </div>
  )
}
