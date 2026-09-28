import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { ResponseError, type MemberReservationResponse } from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import type { ReservationChangeApi } from './reservation-change.api'
import { ReservationChangePage } from './reservation-change-page'

const RESERVATION: MemberReservationResponse = {
  reservationId: 12,
  classType: 'ROUND_BEGINNER',
  lessonDate: new Date('2026-07-20T00:00:00+09:00'),
  startTime: '09:00:00',
  status: 'confirmed',
  paymentSource: 'coupon',
  coupon: {
    couponId: 41, couponType: 'GENERAL', status: 'active', remainingCount: 5,
    heldCount: 1, availableCount: 4, expiresAt: new Date('2026-10-01'),
  },
  paymentDueAt: null,
  rejectionReason: null,
  couponAction: null,
  approvalRequestedAt: new Date('2026-07-01T01:00:00Z'),
  adminConfirmedAt: new Date('2026-07-01T02:00:00Z'),
  rejectedAt: null,
  cancelledAt: null,
  displayGroup: 'UPCOMING',
  actions: {
    change: { allowed: true, blockedReason: null },
    cancel: { allowed: true, blockedReason: null },
    complete: { allowed: false, blockedReason: null },
    noShow: { allowed: false, blockedReason: null },
    approve: { allowed: false, blockedReason: null },
  },
}

afterEach(cleanup)

function createApi(overrides: Partial<ReservationChangeApi> = {}): ReservationChangeApi {
  return {
    getMyReservations: vi.fn().mockResolvedValue([RESERVATION]),
    getAvailableTimeSlots: vi.fn().mockResolvedValue({
      date: new Date('2026-07-21T00:00:00+09:00'),
      classType: 'ROUND_BEGINNER',
      timeSlots: [
        { timeSlotId: 31, lessonDate: new Date('2026-07-21T00:00:00+09:00'), startTime: '10:00:00', closed: false, reservable: true, remainingCapacity: 2, unavailableReason: null },
        { timeSlotId: 32, lessonDate: new Date('2026-07-21T00:00:00+09:00'), startTime: '11:00:00', closed: false, reservable: false, remainingCapacity: 0, unavailableReason: 'FULL' },
      ],
    }),
    previewChange: vi.fn().mockResolvedValue({
      reservationId: 12, targetTimeSlotId: 31, targetLessonDate: new Date('2026-07-21T00:00:00+09:00'),
      targetStartTime: '10:00:00', timing: 'after_cutoff_weekday', couponAction: 'free_change_used', freeChangeUsed: true,
    }),
    changeReservation: vi.fn().mockResolvedValue({
      reservationId: 12, lessonDate: new Date('2026-07-21T00:00:00+09:00'), startTime: '10:00:00',
      status: 'confirmed', couponAction: 'FREE_CHANGE_USED', freeChangeUsed: true, changed: true,
    }),
    ...overrides,
  }
}

function renderPage(api: ReservationChangeApi) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={['/my/reservations/12/change']}>
        <Routes><Route path="/my/reservations/:reservationId/change" element={children} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
  return render(<ReservationChangePage api={api} today="2026-07-16" />, { wrapper: Wrapper })
}

describe('ReservationChangePage', () => {
  it('현재_클래스를_유지하고_3개월_이내_날짜를_선택한다', async () => {
    renderPage(createApi())
    expect(await screen.findByText('원형초보')).toBeInTheDocument()
    const date = screen.getByLabelText('날짜')
    expect(date).toHaveAttribute('min', '2026-07-16')
    expect(date).toHaveAttribute('max', '2026-10-16')
    expect(screen.queryByRole('combobox', { name: '클래스' })).not.toBeInTheDocument()
  })

  it('서버_preview의_쿠폰_처리와_무료_변경권을_표시한다', async () => {
    const api = createApi()
    renderPage(api)
    fireEvent.click(await screen.findByText('10:00'))
    expect(await screen.findByText('변경 마감 후')).toBeInTheDocument()
    expect(screen.getByText('무료 변경권 사용')).toBeInTheDocument()
    expect(screen.getByText('1회 사용')).toBeInTheDocument()
    expect(api.previewChange).toHaveBeenCalledWith(12, 31)
  })

  it('변경_성공_후_새_일시와_무료_변경권_사용을_표시한다', async () => {
    const api = createApi()
    renderPage(api)
    fireEvent.click(await screen.findByText('10:00'))
    const button = await screen.findByRole('button', { name: '이 시간으로 변경' })
    await waitFor(() => expect(button).toBeEnabled())
    fireEvent.click(button)
    const result = (await screen.findByText('예약이 변경되었습니다')).parentElement as HTMLElement
    expect(within(result).getByText(/2026년 7월 21일.*10:00/)).toBeInTheDocument()
    expect(within(result).getByText(/무료 변경권 사용/)).toBeInTheDocument()
    expect(api.changeReservation).toHaveBeenCalledWith(12, 31, undefined)
  })

  it('시간대_로딩과_빈_목록을_표시한다', async () => {
    const pendingApi = createApi({ getAvailableTimeSlots: vi.fn(() => new Promise<never>(() => undefined)) })
    const view = renderPage(pendingApi)
    expect(await screen.findByText('변경 가능한 수업 시간을 확인하는 중입니다.')).toBeInTheDocument()
    view.unmount()

    renderPage(createApi({ getAvailableTimeSlots: vi.fn().mockResolvedValue({ timeSlots: [] }) }))
    expect(await screen.findByText('선택한 날짜에 변경 가능한 시간이 없습니다.')).toBeInTheDocument()
  })

  it('정책_거부와_API_오류를_구분한다', async () => {
    const conflict = new ResponseError(new Response(null, { status: 409 }), 'conflict')
    renderPage(createApi({ previewChange: vi.fn().mockRejectedValue(conflict) }))
    fireEvent.click(await screen.findByText('10:00'))
    expect(await screen.findByRole('alert')).toHaveTextContent('변경 정책상 처리할 수 없습니다')
  })

  it('서버가_변경을_허용하지_않은_예약은_상태와_무관하게_차단한다', async () => {
    renderPage(createApi({
      getMyReservations: vi.fn().mockResolvedValue([{
        ...RESERVATION,
        actions: {
          ...RESERVATION.actions,
          change: { allowed: false, blockedReason: 'RESERVATION_CHANGE_NOT_ALLOWED' },
        },
      }]),
    }))
    expect(await screen.findByRole('alert')).toHaveTextContent('현재 상태에서는 예약을 변경할 수 없습니다')
  })

  it('320px_화면에서도_시간과_실행_버튼을_사용할_수_있다', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 320 })
    renderPage(createApi())
    fireEvent.click(await screen.findByText('10:00'))
    await waitFor(() => expect(screen.getByRole('button', { name: '이 시간으로 변경' })).toBeEnabled())
    expect(screen.getByRole('link', { name: '변경하지 않기' })).toHaveAttribute('href', '/my/reservations')
  })
})
