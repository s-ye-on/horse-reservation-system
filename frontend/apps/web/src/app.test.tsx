import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it, vi } from 'vitest'
import App from './app'

vi.mock('./features/admin-schedule-configuration/admin-schedule-configuration-page', () => ({
  AdminScheduleConfigurationPage: () => <main><h1>정규 시간표 및 정기 휴일</h1></main>,
}))
vi.mock('./features/admin-schedule-closures/admin-schedule-closures-page', () => ({
  AdminScheduleClosuresPage: () => <main><h1>날짜 휴무 및 개별 휴강</h1></main>,
}))

describe('App', () => {
  it('renders the reservation entry points', () => {
    render(
      <MemoryRouter>
        <App />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: '마장 예약' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '회원 예약' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '내 예약' })).toHaveAttribute('href', '/my/reservations')
    expect(screen.getByRole('link', { name: '내 쿠폰' })).toHaveAttribute('href', '/my/coupons')
    expect(screen.getByRole('link', { name: '관리자' })).toBeInTheDocument()
  })

  it('관리자_메뉴에_예약_승인_진입점을_제공한다', () => {
    render(
      <MemoryRouter initialEntries={['/admin']}>
        <App />
      </MemoryRouter>,
    )

    expect(screen.getByRole('link', { name: '예약 승인 및 입금 확인' })).toHaveAttribute('href', '/admin/reservations')
    expect(screen.getByRole('link', { name: '수업 완료 및 노쇼' })).toHaveAttribute('href', '/admin/attendance')
    expect(screen.getByRole('link', { name: '예약 감사 이력' })).toHaveAttribute('href', '/admin/audit-logs')
    expect(screen.getByRole('link', { name: '정규 시간표 및 정기 휴일' })).toHaveAttribute(
      'href',
      '/admin/schedule-configuration',
    )
    expect(screen.getByRole('link', { name: '날짜 휴무 및 개별 휴강' })).toHaveAttribute(
      'href',
      '/admin/schedule-closures',
    )
  })

  it('정규_시간표_관리_route를_렌더링한다', () => {
    render(
      <MemoryRouter initialEntries={['/admin/schedule-configuration']}>
        <App />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: '정규 시간표 및 정기 휴일' })).toBeInTheDocument()
  })

  it('날짜_휴무와_개별_휴강_route를_렌더링한다', () => {
    render(
      <MemoryRouter initialEntries={['/admin/schedule-closures']}>
        <App />
      </MemoryRouter>,
    )

    expect(screen.getByRole('heading', { name: '날짜 휴무 및 개별 휴강' })).toBeInTheDocument()
  })
})
