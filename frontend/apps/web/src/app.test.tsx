import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from './app'

const authState = vi.hoisted(() => ({
  account: null as null | { subject: string; memberId: number | null; email: string; role: string; status: string },
}))

vi.mock('./features/auth/auth-provider', () => ({
  AuthProvider: ({ children }: { children: React.ReactNode }) => children,
}))
vi.mock('./features/auth/use-auth', () => ({
  useAuth: () => ({
    account: authState.account,
    isAuthenticated: authState.account !== null,
    status: authState.account === null ? 'unauthenticated' : 'authenticated',
    login: vi.fn(),
    logout: vi.fn(),
    retrySessionRestore: vi.fn(),
  }),
}))

vi.mock('./features/admin-schedule-configuration/admin-schedule-configuration-page', () => ({
  AdminScheduleConfigurationPage: () => <main><h1>정규 시간표 및 정기 휴일</h1></main>,
}))
vi.mock('./features/admin-schedule-closures/admin-schedule-closures-page', () => ({
  AdminScheduleClosuresPage: () => <main><h1>날짜 휴무 및 개별 휴강</h1></main>,
}))
vi.mock('./features/admin-family-groups/admin-family-groups-page', () => ({
  AdminFamilyGroupsPage: () => <main><h1>가족 그룹 관리</h1></main>,
}))
vi.mock('./features/admin-monthly-ride-statistics/admin-monthly-ride-statistics-page', () => ({
  AdminMonthlyRideStatisticsPage: () => <main><h1>월간 기승 현황</h1></main>,
}))
vi.mock('./features/admin-weekly-operations-calendar/admin-weekly-operations-calendar-page', () => ({
  AdminWeeklyOperationsCalendarPage: () => <main><h1>주간 운영 캘린더</h1></main>,
}))
vi.mock('./features/reservation-calendar/reservation-calendar-page', () => ({
  ReservationCalendarPage: () => <main><h1>회원 예약</h1></main>,
}))
vi.mock('./features/member-home/member-home-page', () => ({
  MemberHomePage: () => <main><h1>회원 홈</h1></main>,
}))

afterEach(() => cleanup())

describe('App', () => {
  it('비로그인_사용자에게_로그인_진입점을_제공한다', () => {
    authState.account = null
    render(
      <MemoryRouter>
        <App />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: '마장 예약' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Unicorn Stable 홈' })).toHaveAttribute('href', '/')
    expect(screen.getByRole('link', { name: '본문 콘텐츠로 건너뛰기' })).toHaveAttribute('href', '#main-content')
    expect(screen.getByRole('link', { name: '로그인 후 예약하기' })).toHaveAttribute('href', '/login')
    expect(screen.queryByRole('link', { name: '관리자' })).not.toBeInTheDocument()
  })

  it('관리자_메뉴에_예약_승인_진입점을_제공한다', () => {
    authState.account = {
      subject: 'admin-subject', memberId: null, email: 'admin@horse.test', role: 'ADMIN', status: 'ACTIVE',
    }
    render(
      <MemoryRouter initialEntries={['/admin']}>
        <App />
      </MemoryRouter>,
    )

    const adminNavigation = screen.getByRole('navigation', { name: '관리자 전체 메뉴' })
    expect(within(adminNavigation).getByRole('link', { name: '예약 승인 및 입금 확인' })).toHaveAttribute('href', '/admin/reservations')
    expect(within(adminNavigation).getByRole('link', { name: '수업 완료 및 노쇼' })).toHaveAttribute('href', '/admin/attendance')
    expect(within(adminNavigation).getByRole('link', { name: '예약 감사 이력' })).toHaveAttribute('href', '/admin/audit-logs')
    expect(within(adminNavigation).getByRole('link', { name: '가족 그룹 관리' })).toHaveAttribute('href', '/admin/family-groups')
    expect(within(adminNavigation).getByRole('link', { name: '정규 시간표 및 정기 휴일' })).toHaveAttribute(
      'href',
      '/admin/schedule-configuration',
    )
    expect(within(adminNavigation).getByRole('link', { name: '날짜 휴무 및 개별 휴강' })).toHaveAttribute(
      'href',
      '/admin/schedule-closures',
    )
    expect(within(adminNavigation).getByRole('link', { name: '월간 기승 현황' })).toHaveAttribute(
      'href',
      '/admin/monthly-ride-statistics',
    )
    expect(within(adminNavigation).getByRole('link', { name: '주간 운영 캘린더' })).toHaveAttribute(
      'href',
      '/admin/weekly-operations-calendar',
    )
    expect(within(adminNavigation).getByRole('link', { name: '운영 홈' })).toHaveAttribute('aria-current', 'page')
  })

  it('관리자도_공개_홈에서_브랜드와_관리자_진입점을_유지한다', () => {
    authState.account = {
      subject: 'admin-subject', memberId: null, email: 'admin@horse.test', role: 'ADMIN', status: 'ACTIVE',
    }
    render(
      <MemoryRouter>
        <App />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: 'Unicorn Stable 홈' })).toHaveAttribute('href', '/')
    expect(within(screen.getByRole('navigation', { name: '관리자 메뉴' }))
      .getByRole('link', { name: '관리자' })).toHaveAttribute('href', '/admin')
  })

  it('회원_공통_메뉴에_쿠폰_잔여_조회_진입점을_제공한다', () => {
    authState.account = {
      subject: 'member-subject', memberId: 1, email: 'member@horse.test', role: 'MEMBER', status: 'ACTIVE',
    }
    render(
      <MemoryRouter initialEntries={['/reservations']}>
        <App />
      </MemoryRouter>,
    )

    const memberNavigation = screen.getByRole('navigation', { name: '회원 메뉴' })
    expect(within(memberNavigation).getByRole('link', { name: '내 쿠폰' })).toHaveAttribute('href', '/my/coupons')
    expect(within(memberNavigation).getByRole('link', { name: '수업 예약' })).toHaveAttribute('aria-current', 'page')
    expect(screen.getByRole('heading', { name: '회원 예약' })).toBeInTheDocument()
  })

  it('회원은_기존_홈_URL에서_회원_홈을_사용한다', () => {
    authState.account = {
      subject: 'member-subject', memberId: 1, email: 'member@horse.test', role: 'MEMBER', status: 'ACTIVE',
    }
    render(
      <MemoryRouter>
        <App />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: '회원 홈' })).toBeInTheDocument()
    expect(within(screen.getByRole('navigation', { name: '회원 메뉴' }))
      .getByRole('link', { name: '홈' })).toHaveAttribute('aria-current', 'page')
  })

  it('모바일_관리자_메뉴로_기존_route를_이동한다', () => {
    authState.account = {
      subject: 'admin-subject', memberId: null, email: 'admin@horse.test', role: 'ADMIN', status: 'ACTIVE',
    }
    render(
      <MemoryRouter initialEntries={['/admin/schedule-configuration']}>
        <App />
      </MemoryRouter>,
    )

    const mobileNavigation = screen.getByRole('navigation', { name: '모바일 관리자 메뉴' })
    const routeSelect = within(mobileNavigation).getByRole('combobox', { name: '관리 업무 이동' })
    expect(routeSelect).toHaveValue('/admin/schedule-configuration')

    fireEvent.change(routeSelect, { target: { value: '/admin/schedule-closures' } })

    expect(screen.getByRole('heading', { name: '날짜 휴무 및 개별 휴강' })).toBeInTheDocument()
    expect(routeSelect).toHaveValue('/admin/schedule-closures')
  })

  it('정규_시간표_관리_route를_렌더링한다', () => {
    authState.account = {
      subject: 'admin-subject', memberId: null, email: 'admin@horse.test', role: 'ADMIN', status: 'ACTIVE',
    }
    render(
      <MemoryRouter initialEntries={['/admin/schedule-configuration']}>
        <App />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: '정규 시간표 및 정기 휴일' })).toBeInTheDocument()
  })

  it('날짜_휴무와_개별_휴강_route를_렌더링한다', () => {
    authState.account = {
      subject: 'admin-subject', memberId: null, email: 'admin@horse.test', role: 'ADMIN', status: 'ACTIVE',
    }
    render(
      <MemoryRouter initialEntries={['/admin/schedule-closures']}>
        <App />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: '날짜 휴무 및 개별 휴강' })).toBeInTheDocument()
  })

  it('가족_그룹_관리_route를_렌더링한다', () => {
    authState.account = {
      subject: 'admin-subject', memberId: null, email: 'admin@horse.test', role: 'ADMIN', status: 'ACTIVE',
    }
    render(
      <MemoryRouter initialEntries={['/admin/family-groups']}>
        <App />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: '가족 그룹 관리' })).toBeInTheDocument()
  })

  it('월간_기승_현황_route를_렌더링한다', () => {
    authState.account = {
      subject: 'admin-subject', memberId: null, email: 'admin@horse.test', role: 'ADMIN', status: 'ACTIVE',
    }
    render(
      <MemoryRouter initialEntries={['/admin/monthly-ride-statistics']}>
        <App />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: '월간 기승 현황' })).toBeInTheDocument()
  })

  it('주간_운영_캘린더_route를_렌더링한다', () => {
    authState.account = {
      subject: 'admin-subject', memberId: null, email: 'admin@horse.test', role: 'ADMIN', status: 'ACTIVE',
    }
    render(
      <MemoryRouter initialEntries={['/admin/weekly-operations-calendar']}>
        <App />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: '주간 운영 캘린더' })).toBeInTheDocument()
  })

  it('MEMBER의_ADMIN_route_접근을_거부한다', () => {
    authState.account = {
      subject: 'member-subject', memberId: 1, email: 'member@horse.test', role: 'MEMBER', status: 'ACTIVE',
    }

    render(
      <MemoryRouter initialEntries={['/admin']}>
        <App />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: '접근 권한이 없습니다' })).toBeInTheDocument()
  })
})
