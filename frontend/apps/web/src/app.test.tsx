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
    expect(screen.getByRole('link', { name: '관리자' })).toBeInTheDocument()
  })
})

