import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import App from './app'

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
  })
})
