import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
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

describe('App', () => {
  it('비로그인_사용자에게_로그인_진입점을_제공한다', () => {
    authState.account = null
    render(
      <MemoryRouter>
        <App />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: '마장 예약' })).toBeInTheDocument()
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

    expect(screen.getByRole('link', { name: '예약 승인 및 입금 확인' })).toHaveAttribute('href', '/admin/reservations')
    expect(screen.getByRole('link', { name: '수업 완료 및 노쇼' })).toHaveAttribute('href', '/admin/attendance')
    expect(screen.getByRole('link', { name: '예약 감사 이력' })).toHaveAttribute('href', '/admin/audit-logs')
    expect(screen.getByRole('link', { name: '가족 그룹 관리' })).toHaveAttribute('href', '/admin/family-groups')
    expect(screen.getByRole('link', { name: '정규 시간표 및 정기 휴일' })).toHaveAttribute(
      'href',
      '/admin/schedule-configuration',
    )
    expect(screen.getByRole('link', { name: '날짜 휴무 및 개별 휴강' })).toHaveAttribute(
      'href',
      '/admin/schedule-closures',
    )
  })

  it('회원_공통_메뉴에_쿠폰_잔여_조회_진입점을_제공한다', () => {
    authState.account = {
      subject: 'member-subject', memberId: 1, email: 'member@horse.test', role: 'MEMBER', status: 'ACTIVE',
    }
    render(
      <MemoryRouter>
        <App />
      </MemoryRouter>,
    )

    const memberNavigation = screen.getByRole('navigation', { name: '회원 메뉴' })
    expect(within(memberNavigation).getByRole('link', { name: '내 쿠폰' })).toHaveAttribute('href', '/my/coupons')
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
