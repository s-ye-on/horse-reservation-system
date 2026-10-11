import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { ResponseError, type MemberReservationResponse } from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import type { ReservationCancelApi } from './reservation-cancel.api'
import { ReservationCancelPage } from './reservation-cancel-page'

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

function createApi(overrides: Partial<ReservationCancelApi> = {}): ReservationCancelApi {
  return {
    getMyReservation: vi.fn().mockResolvedValue(RESERVATION),
    previewCancellation: vi.fn().mockResolvedValue({ reservationId: 12, timing: 'after_cutoff_weekday', responsibility: 'member', couponAction: 'deduct' }),
    cancelReservation: vi.fn().mockResolvedValue({ reservationId: 12, status: 'cancelled', responsibility: 'MEMBER', couponAction: 'DEDUCT', cancelledAt: new Date('2026-07-19T22:00:00+09:00'), changed: true }),
    ...overrides,
  }
}

function renderPage(api: ReservationCancelApi, reservationId = '12', client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[`/my/reservations/${reservationId}/cancel`]}>
        <Routes><Route path="/my/reservations/:reservationId/cancel" element={children} /></Routes>
      </MemoryRouter>
    </QueryClientProvider>
  )
  return { ...render(<ReservationCancelPage api={api} />, { wrapper: Wrapper }), client }
}

describe('ReservationCancelPage', () => {
  it('첫_20건에_없는_예약도_URL의_ID로_직접_조회한다', async () => {
    const getMyReservations = vi.fn().mockResolvedValue(Array.from({ length: 20 }, (_, index) => ({
      ...RESERVATION, reservationId: 100 + index,
    })))
    const getMyReservation = vi.fn().mockResolvedValue(RESERVATION)
    const api = { ...createApi(), getMyReservations, getMyReservation }
    renderPage(api)
    expect(await screen.findByLabelText(/취소 사유/)).toBeInTheDocument()
    expect(getMyReservation).toHaveBeenCalledWith(12)
    expect(getMyReservations).not.toHaveBeenCalled()
  })

  it.each(['RESERVATION_NOT_FOUND', 'RESERVATION_MEMBER_MISMATCH'])('조회_404_%s는_예약_정보를_노출하지_않고_작업을_중단한다', async (code) => {
    const api = createApi({ getMyReservation: vi.fn().mockRejectedValue(new ResponseError(new Response(JSON.stringify({ code }), { status: 404 }), code)) })
    renderPage(api, '999')
    expect(await screen.findByRole('alert')).toHaveTextContent('취소할 예약을 찾을 수 없습니다')
    expect(api.getMyReservation).toHaveBeenCalledWith(999)
    expect(api.previewCancellation).not.toHaveBeenCalled()
    expect(api.cancelReservation).not.toHaveBeenCalled()
  })

  it.each(['0', '-1', 'invalid'])('잘못된_URL_ID_%s는_조회하지_않는다', async (id) => {
    const api = createApi()
    renderPage(api, id)
    expect(await screen.findByRole('alert')).toHaveTextContent('예약 번호가 올바르지 않습니다')
    expect(api.getMyReservation).not.toHaveBeenCalled()
  })

  it('예약_ID가_다르면_같은_query_client에서도_별도로_조회한다', async () => {
    const api = createApi({ getMyReservation: vi.fn(async (id: number) => ({ ...RESERVATION, reservationId: id, classType: id === 12 ? 'ROUND_BEGINNER' : 'DRESSAGE' })) })
    const view = renderPage(api)
    expect(await screen.findByText('원형초보')).toBeInTheDocument()
    view.unmount()
    renderPage(api, '13', view.client)
    expect(await screen.findByText('마장마술')).toBeInTheDocument()
    expect(api.getMyReservation).toHaveBeenCalledWith(13)
  })

  it('서버_preview의_마감_기준과_예상_쿠폰_차감을_표시한다', async () => {
    renderPage(createApi())
    expect(await screen.findByText('취소 마감 후')).toBeInTheDocument()
    expect(screen.getByText('1회 차감')).toBeInTheDocument()
    expect(screen.getByText('회원 사유')).toBeInTheDocument()
  })

  it('취소_사유를_입력하기_전에는_실행할_수_없다', async () => {
    renderPage(createApi())
    const button = await screen.findByRole('button', { name: '예약 취소 확정' })
    expect(button).toBeDisabled()
    fireEvent.change(screen.getByLabelText(/취소 사유/), { target: { value: '개인 일정' } })
    expect(button).toBeEnabled()
  })

  it('취소_성공_후_상태_시각과_최종_쿠폰_처리를_표시한다', async () => {
    const getMyReservation = vi.fn()
      .mockResolvedValueOnce(RESERVATION)
      .mockResolvedValue({ ...RESERVATION, status: 'cancelled' })
    const api = createApi({ getMyReservation })
    const { client } = renderPage(api)
    const invalidate = vi.spyOn(client, 'invalidateQueries')
    fireEvent.change(await screen.findByLabelText(/취소 사유/), { target: { value: '  몸 상태가 좋지 않습니다.  ' } })
    const button = await screen.findByRole('button', { name: '예약 취소 확정' })
    await waitFor(() => expect(button).toBeEnabled())
    fireEvent.click(button)
    const result = (await screen.findByText('예약이 취소되었습니다')).parentElement as HTMLElement
    expect(within(result).getByText('상태 예약 취소')).toBeInTheDocument()
    expect(within(result).getByText('최종 쿠폰 처리 1회 차감')).toBeInTheDocument()
    expect(within(result).getByText(/취소 시각/)).toBeInTheDocument()
    expect(api.cancelReservation).toHaveBeenCalledWith(12, '몸 상태가 좋지 않습니다.')
    expect(getMyReservation).toHaveBeenCalledTimes(1)
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['member', 'reservations'], refetchType: 'none' })
  })

  it('preview_로딩_상태를_표시한다', async () => {
    renderPage(createApi({ previewCancellation: vi.fn(() => new Promise<never>(() => undefined)) }))
    expect(await screen.findByText('취소 정책을 확인하는 중입니다.')).toBeInTheDocument()
  })

  it('서버가_취소를_허용하지_않은_예약은_상태와_무관하게_차단한다', async () => {
    renderPage(createApi({
      getMyReservation: vi.fn().mockResolvedValue({
        ...RESERVATION,
        actions: {
          ...RESERVATION.actions,
          cancel: { allowed: false, blockedReason: 'RESERVATION_INVALID_STATUS' },
        },
      }),
    }))
    expect(await screen.findByRole('alert')).toHaveTextContent('현재 상태에서는 예약을 취소할 수 없습니다')
  })

  it('정책_충돌과_API_오류를_안내한다', async () => {
    const conflict = new ResponseError(new Response(null, { status: 409 }), 'conflict')
    renderPage(createApi({ previewCancellation: vi.fn().mockRejectedValue(conflict) }))
    expect(await screen.findByRole('alert')).toHaveTextContent('예약 상태가 변경되어 취소할 수 없습니다')
  })

  it('320px_화면에서도_사유와_취소_버튼을_사용할_수_있다', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 320 })
    renderPage(createApi())
    expect(await screen.findByLabelText(/취소 사유/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '예약 취소 확정' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '취소하지 않기' })).toHaveAttribute('href', '/my/reservations')
  })
})
