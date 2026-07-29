import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import type { AdminReservationResponse, AdminReservationSummaryResponse } from '@horse/api-client'
import type { AdminDashboardApi, DashboardReservationStatus } from './admin-dashboard.api'
import { AdminDashboardPage } from './admin-dashboard-page'

const SUMMARY: AdminReservationSummaryResponse = {
  lessonDateFrom: new Date('2026-07-17T00:00:00.000Z'),
  lessonDateTo: new Date('2026-07-17T00:00:00.000Z'),
  totalCount: 7,
  statusCounts: [
    { status: 'pending_admin_approval', count: 3 },
    { status: 'pending_payment', count: 2 },
    { status: 'payment_expired', count: 1 },
    { status: 'confirmed', count: 1 },
  ],
  dailyCounts: [],
}

const APPROVAL_RESERVATION: AdminReservationResponse = {
  reservationId: 11,
  memberId: 11,
  memberName: '김승인',
  memberPhone: '010-1111-2222',
  classType: 'ROUND_BEGINNER',
  lessonDate: new Date('2026-07-17T00:00:00.000Z'),
  startTime: '09:00:00',
  status: 'pending_admin_approval',
  paymentSource: 'coupon',
  coupon: {
    couponId: 11, couponType: 'GENERAL', status: 'active', remainingCount: 5,
    heldCount: 1, expiresAt: null,
  },
  paymentDueAt: null,
  approvalRequestedAt: new Date('2026-07-17T01:00:00Z'),
  adminConfirmedAt: null,
  rejectedAt: null,
  rejectedBy: null,
  rejectionReason: null,
  cancelledAt: null,
  cancellationResponsibility: null,
  couponAction: null,
  adminMemo: null,
  approvalWarning: 'critical',
  createdAt: new Date('2026-07-17T01:00:00Z'),
  updatedAt: new Date('2026-07-17T01:00:00Z'),
  displayGroup: 'UPCOMING',
  actions: {
    change: { allowed: true, blockedReason: null },
    cancel: { allowed: true, blockedReason: null },
    complete: { allowed: false, blockedReason: null },
    noShow: { allowed: false, blockedReason: null },
    approve: { allowed: true, blockedReason: null },
  },
}

afterEach(() => cleanup())

function createApi(overrides: Partial<AdminDashboardApi> = {}): AdminDashboardApi {
  return {
    getSummary: vi.fn().mockResolvedValue(SUMMARY),
    getReservations: vi.fn().mockResolvedValue([APPROVAL_RESERVATION]),
    ...overrides,
  }
}

function renderPage(api: AdminDashboardApi) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  )
  return render(<AdminDashboardPage api={api} />, { wrapper })
}

describe('AdminDashboardPage', () => {
  it('오늘_집계와_승인대기_경고_예약을_표시한다', async () => {
    const api = createApi()

    renderPage(api)

    expect(await screen.findByRole('heading', { name: '관리자 운영 대시보드' })).toBeInTheDocument()
    expect(screen.getByText('선택 기간 전체')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /쿠폰 승인대기 3/ })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: /입금대기 2/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /입금만료 1/ })).toBeInTheDocument()
    expect(await screen.findByText('김승인')).toBeInTheDocument()
    expect(screen.getByText('긴급')).toBeInTheDocument()
    expect(api.getReservations).toHaveBeenCalledWith('pending_admin_approval', '2026-07-17', '2026-07-17')
  })

  it('상태를_선택하면_같은_날짜_범위로_예약을_조회한다', async () => {
    const getReservations = vi.fn(async (status: DashboardReservationStatus) => status === 'pending_payment'
      ? [{ ...APPROVAL_RESERVATION, reservationId: 12, memberName: '이입금', status: 'pending_payment', approvalWarning: null }]
      : [APPROVAL_RESERVATION])
    const api = createApi({ getReservations })
    renderPage(api)
    await screen.findByText('김승인')

    fireEvent.click(screen.getByRole('button', { name: /입금대기 2/ }))

    expect(await screen.findByText('이입금')).toBeInTheDocument()
    expect(getReservations).toHaveBeenLastCalledWith('pending_payment', '2026-07-17', '2026-07-17')
  })

  it('입력한_기간을_집계에_적용한다', async () => {
    const api = createApi()
    renderPage(api)
    await screen.findByText('김승인')

    fireEvent.change(screen.getByLabelText('시작일'), { target: { value: '2026-07-20' } })
    fireEvent.change(screen.getByLabelText('종료일'), { target: { value: '2026-07-25' } })
    fireEvent.click(screen.getByRole('button', { name: '기간 적용' }))

    await waitFor(() => expect(api.getSummary).toHaveBeenLastCalledWith('2026-07-20', '2026-07-25'))
  })

  it('예약이_없을_때_빈_상태를_표시한다', async () => {
    renderPage(createApi({ getReservations: vi.fn().mockResolvedValue([]) }))

    expect(await screen.findByText('선택한 기간과 상태에 해당하는 예약이 없습니다.')).toBeInTheDocument()
  })

  it('집계_실패를_빈_상태와_구분해_표시한다', async () => {
    renderPage(createApi({ getSummary: vi.fn().mockRejectedValue(new Error('network')) }))

    expect(await screen.findByRole('alert')).toHaveTextContent('운영 현황을 불러오지 못했습니다.')
  })

  it('320px_화면에서도_기간과_상태를_선택할_수_있다', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 320 })
    renderPage(createApi())

    expect(await screen.findByLabelText('시작일')).toBeInTheDocument()
    expect(screen.getByLabelText('종료일')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '기간 적용' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /입금만료 1/ })).toBeInTheDocument()
  })
})
