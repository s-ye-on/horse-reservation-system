import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, useNavigate } from 'react-router'
import { ResponseError } from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { useEffect, type ReactNode } from 'react'
import type { ReservationApplicationApi } from './reservation-application.api'
import { ReservationApplicationPage } from './reservation-application-page'

const ROUTE = '/reservations/new?timeSlotId=12&classType=ROUND_BEGINNER&date=2026-08-10'

afterEach(cleanup)

function createApi(overrides: Partial<ReservationApplicationApi> = {}): ReservationApplicationApi {
  return {
    getSelectedTimeSlot: vi.fn().mockResolvedValue({
      timeSlotId: 12,
      lessonDate: new Date('2026-08-10'),
      startTime: '09:00:00',
      closed: false,
      reservable: true,
      remainingCapacity: 2,
    }),
    apply: vi.fn().mockResolvedValue({
      reservationId: 81,
      classType: 'ROUND_BEGINNER',
      lessonDate: new Date('2026-08-10'),
      startTime: '09:00:00',
      status: 'pending_admin_approval',
      paymentSource: 'coupon',
      coupon: { couponId: 44, expiresAt: new Date('2026-10-10T23:59:59+09:00'), remainingCount: 6, heldCount: 2, availableCount: 4 },
    }),
    ...overrides,
  }
}

function renderPage(api: ReservationApplicationApi, route = ROUTE) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}><MemoryRouter initialEntries={[route]}>{children}</MemoryRouter></QueryClientProvider>
  return render(<ReservationApplicationPage api={api} />, { wrapper: Wrapper })
}

function MutableRoutePage({
  api,
  route,
}: {
  api: ReservationApplicationApi
  route: string
}) {
  const navigate = useNavigate()
  useEffect(() => {
    void navigate(route)
  }, [navigate, route])
  return <ReservationApplicationPage api={api} />
}

function renderMutableRoutePage(api: ReservationApplicationApi, route: string) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const tree = (currentRoute: string) => (
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[route]}>
        <MutableRoutePage api={api} route={currentRoute} />
      </MemoryRouter>
    </QueryClientProvider>
  )
  const result = render(tree(route))
  return {
    ...result,
    navigateTo: (nextRoute: string) => result.rerender(tree(nextRoute)),
  }
}

describe('ReservationApplicationPage', () => {
  it('달력에서_전달한_수업을_서버에서_확인해_표시한다', async () => {
    const getSelectedTimeSlot = vi.fn().mockResolvedValue({ timeSlotId: 12, startTime: '09:00:00', reservable: true, remainingCapacity: 2 })
    renderPage(createApi({ getSelectedTimeSlot }))
    expect(await screen.findByText('원형초보')).toBeInTheDocument()
    expect(screen.getByText('2026년 8월 10일 월요일')).toBeInTheDocument()
    expect(screen.getByText('09:00')).toBeInTheDocument()
    expect(screen.getByText('잔여석').nextElementSibling).toHaveTextContent('2자리')
    expect(getSelectedTimeSlot).toHaveBeenCalledWith('2026-08-10', 'ROUND_BEGINNER', 12)
  })

  it('쿠폰_신청_응답을_사용자_용어로_표시한다', async () => {
    const apply = vi.fn().mockResolvedValue({
      reservationId: 81,
      status: 'pending_admin_approval',
      paymentSource: 'coupon',
      coupon: { couponId: 44, expiresAt: new Date('2026-10-10T23:59:59+09:00'), remainingCount: 6, heldCount: 2, availableCount: 4 },
    })
    renderPage(createApi({ apply }))
    fireEvent.click(await screen.findByRole('button', { name: '예약 신청' }))
    await waitFor(() => expect(apply).toHaveBeenCalledWith(
      12,
      'ROUND_BEGINNER',
      expect.any(String),
    ))
    expect(await screen.findByText('관리자 승인을 기다리고 있습니다')).toBeInTheDocument()
    expect(screen.getByText('예약 번호 81 · 관리자 승인대기')).toBeInTheDocument()
    expect(screen.queryByText(/pending_admin_approval/)).not.toBeInTheDocument()
    expect(screen.getByText('현재 남은 횟수').nextElementSibling).toHaveTextContent('6회')
    expect(screen.getByText('예약 처리 중 횟수').nextElementSibling).toHaveTextContent('2회')
    expect(screen.getByText('지금 예약 가능한 횟수').nextElementSibling).toHaveTextContent('4회')
    expect(screen.getByText('2026. 10. 10.')).toBeInTheDocument()
  })

  it('미사용_쿠폰은_만료일_확정_시점을_안내한다', async () => {
    renderPage(createApi({ apply: vi.fn().mockResolvedValue({ reservationId: 82, status: 'pending_admin_approval', paymentSource: 'coupon', coupon: { couponId: 45, remainingCount: 10, heldCount: 1, availableCount: 9 } }) }))
    fireEvent.click(await screen.findByRole('button', { name: '예약 신청' }))
    expect(await screen.findByText('첫 사용 완료 후 확정')).toBeInTheDocument()
  })

  it('일회_결제_응답은_입금_마감과_이후_연락_안내를_표시한다', async () => {
    renderPage(createApi({ apply: vi.fn().mockResolvedValue({
      reservationId: 83,
      status: 'pending_payment',
      paymentSource: 'single_payment',
      paymentDueAt: new Date('2026-08-01T14:00:00+09:00'),
    }) }))
    fireEvent.click(await screen.findByRole('button', { name: '예약 신청' }))
    expect(await screen.findByText('입금 확인을 기다리고 있습니다')).toBeInTheDocument()
    expect(screen.getByText('안내된 기한까지 입금해 주세요')).toBeInTheDocument()
    expect(screen.getByText('예약 번호 83 · 입금 확인 대기')).toBeInTheDocument()
    expect(screen.queryByText(/pending_payment/)).not.toBeInTheDocument()
    expect(screen.getByText(/입금 마감/)).toHaveTextContent('2026. 8. 1. 오후 02:00')
    expect(screen.getByText(/관리자에게 연락/)).toBeInTheDocument()
  })

  it('같은_화면의_중복_제출을_막는다', async () => {
    const apply = vi.fn().mockResolvedValue({ reservationId: 81, status: 'pending_admin_approval', paymentSource: 'coupon' })
    renderPage(createApi({ apply }))
    const button = await screen.findByRole('button', { name: '예약 신청' })
    fireEvent.click(button)
    fireEvent.click(button)
    await waitFor(() => expect(apply).toHaveBeenCalledTimes(1))
  })

  it('응답을_받지_못한_같은_논리_신청은_같은_멱등성_key로_재시도한다', async () => {
    const apply = vi.fn()
      .mockRejectedValueOnce(new TypeError('network failed'))
      .mockResolvedValueOnce({
        reservationId: 81,
        status: 'pending_admin_approval',
        paymentSource: 'coupon',
      })
    renderPage(createApi({ apply }))
    const button = await screen.findByRole('button', { name: '예약 신청' })

    fireEvent.click(button)
    await screen.findByRole('alert')
    fireEvent.click(button)

    await waitFor(() => expect(apply).toHaveBeenCalledTimes(2))
    expect(apply.mock.calls[0]?.[2]).toBe(apply.mock.calls[1]?.[2])
    expect(await screen.findByText('관리자 승인을 기다리고 있습니다')).toBeInTheDocument()
  })

  it('선택한_예약_본문이_바뀌면_새로운_멱등성_key를_사용한다', async () => {
    const apply = vi.fn()
      .mockRejectedValueOnce(new TypeError('network failed'))
      .mockResolvedValueOnce({
        reservationId: 82,
        status: 'pending_admin_approval',
        paymentSource: 'coupon',
      })
    const getSelectedTimeSlot = vi.fn().mockResolvedValue({
      timeSlotId: 12,
      startTime: '09:00:00',
      reservable: true,
      remainingCapacity: 2,
    })
    const api = createApi({ apply, getSelectedTimeSlot })
    const page = renderMutableRoutePage(api, ROUTE)
    fireEvent.click(await screen.findByRole('button', { name: '예약 신청' }))
    await screen.findByRole('alert')

    page.navigateTo(
      '/reservations/new?timeSlotId=13&classType=FIRST_RIDE&date=2026-08-11',
    )
    await waitFor(() => expect(getSelectedTimeSlot).toHaveBeenLastCalledWith(
      '2026-08-11',
      'FIRST_RIDE',
      13,
    ))
    fireEvent.click(await screen.findByRole('button', { name: '예약 신청' }))

    await waitFor(() => expect(apply).toHaveBeenCalledTimes(2))
    expect(apply.mock.calls[0]?.[2]).not.toBe(apply.mock.calls[1]?.[2])
  })

  it.each([[409, '방금 마감'], [401, '다시 로그인']])('%i_신청_오류를_구분한다', async (status, message) => {
    const error = new ResponseError(new Response(null, { status }), 'failed')
    renderPage(createApi({ apply: vi.fn().mockRejectedValue(error) }))
    fireEvent.click(await screen.findByRole('button', { name: '예약 신청' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
  })

  it('예약할_수_없는_시간대는_신청을_막는다', async () => {
    const apply = vi.fn()
    renderPage(createApi({ getSelectedTimeSlot: vi.fn().mockResolvedValue({ timeSlotId: 12, startTime: '09:00:00', reservable: false, unavailableReason: 'FULL' }), apply }))
    expect(await screen.findByRole('button', { name: '예약 신청' })).toBeDisabled()
    expect(apply).not.toHaveBeenCalled()
  })

  it('잘못된_선택_정보는_API_호출_전에_거부한다', async () => {
    const getSelectedTimeSlot = vi.fn()
    renderPage(createApi({ getSelectedTimeSlot }), '/reservations/new?timeSlotId=bad')
    expect(await screen.findByRole('alert')).toHaveTextContent('올바르지 않습니다')
    expect(getSelectedTimeSlot).not.toHaveBeenCalled()
  })

  it('320px_화면에서도_신청과_다른_시간_선택을_사용할_수_있다', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 320 })
    renderPage(createApi())
    expect(await screen.findByRole('button', { name: '예약 신청' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '다른 시간 선택' })).toHaveAttribute('href', '/reservations')
  })
})
