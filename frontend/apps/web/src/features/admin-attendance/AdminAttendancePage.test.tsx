import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import {
  AdminReservationQueryControllerApi,
  ResponseError,
  type AdminReservationResponse,
  type ReservationCompletionResponse,
} from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { adminAttendanceApi, type AdminAttendanceApi } from './admin-attendance.api'
import { AdminAttendancePage } from './admin-attendance-page'

const GENERAL: AdminReservationResponse = {
  reservationId: 31,
  memberName: '김일반',
  memberPhone: '010-3131-3131',
  classType: 'ROUND_TROT',
  lessonDate: new Date('2026-08-10'),
  startTime: '09:00:00',
  status: 'confirmed',
  paymentSource: 'coupon',
  coupon: { couponId: 71, couponType: 'GENERAL', remainingCount: 5, heldCount: 1 },
}

const DRESSAGE: AdminReservationResponse = {
  ...GENERAL,
  reservationId: 32,
  memberName: '이마술',
  classType: 'DRESSAGE',
  coupon: { couponId: 72, couponType: 'DRESSAGE', remainingCount: 7, heldCount: 1 },
}

const JUMPING: AdminReservationResponse = {
  ...GENERAL,
  reservationId: 33,
  memberName: '박장애물',
  classType: 'JUMPING',
  paymentSource: 'single_payment',
  coupon: undefined,
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function createApi(overrides: Partial<AdminAttendanceApi> = {}): AdminAttendanceApi {
  return {
    getConfirmedReservations: vi.fn().mockResolvedValue([GENERAL, DRESSAGE, JUMPING]),
    processBulk: vi.fn().mockResolvedValue({ requestedCount: 3, succeededCount: 3, failedCount: 0, items: [] }),
    complete: vi.fn().mockResolvedValue({ reservationId: 31, status: 'completed', generalRideCount: 12, dressageRideCount: 3, jumpingRideCount: 2 }),
    noShow: vi.fn().mockResolvedValue({ reservationId: 31, status: 'no_show', paymentSource: 'coupon', couponId: 71, couponAction: 'deduct', adminMemo: '노쇼' }),
    ...overrides,
  }
}

function renderPage(api: AdminAttendanceApi) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>
  return render(<AdminAttendancePage api={api} />, { wrapper: Wrapper })
}

async function cardFor(memberName: string) {
  return (await screen.findByRole('heading', { name: memberName })).closest('article') as HTMLElement
}

describe('AdminAttendancePage', () => {
  it('출석_목록은_과거_확정_예약을_포함하도록_날짜_하한을_명시한다', async () => {
    const getReservations = vi
      .spyOn(AdminReservationQueryControllerApi.prototype, 'getReservations')
      .mockResolvedValue({ content: [GENERAL], page: 0, size: 100, totalElements: 1, totalPages: 1 })

    await adminAttendanceApi.getConfirmedReservations()

    expect(getReservations).toHaveBeenCalledWith({
      status: 'confirmed',
      lessonDateFrom: new Date('1970-01-01T00:00:00.000Z'),
      page: 0,
      size: 100,
    })
  })

  it('확정_예약의_회원_수업_결제와_쿠폰_요약을_표시한다', async () => {
    renderPage(createApi())
    const card = await cardFor('김일반')
    expect(within(card).getByText('원형 속보')).toBeInTheDocument()
    expect(within(card).getByText('쿠폰')).toBeInTheDocument()
    expect(within(card).getByText('#71 · 잔여 5회')).toBeInTheDocument()
  })

  it.each([
    [GENERAL, { reservationId: 31, status: 'completed', generalRideCount: 12, dressageRideCount: 3, jumpingRideCount: 2, couponId: 71 }, '일반 12회'],
    [DRESSAGE, { reservationId: 32, status: 'completed', generalRideCount: 11, dressageRideCount: 4, jumpingRideCount: 2, couponId: 72 }, '마장마술 4회'],
    [JUMPING, { reservationId: 33, status: 'completed', generalRideCount: 11, dressageRideCount: 3, jumpingRideCount: 3 }, '장애물 3회'],
  ] as Array<[AdminReservationResponse, ReservationCompletionResponse, string]>)('%s_수업_완료_응답의_탑승_이력을_표시한다', async (reservation, response, expected) => {
    const complete = vi.fn().mockResolvedValue(response)
    renderPage(createApi({ getConfirmedReservations: vi.fn().mockResolvedValue([reservation]), complete }))
    const card = await cardFor(reservation.memberName as string)
    fireEvent.click(within(card).getByRole('button', { name: '수업 완료' }))
    fireEvent.click(within(card).getByRole('button', { name: '완료 처리 확인' }))
    await waitFor(() => expect(complete).toHaveBeenCalledWith(reservation.reservationId))
    expect(await screen.findByText('상태 completed')).toBeInTheDocument()
    expect(screen.getByText(expected)).toBeInTheDocument()
  })

  it('쿠폰_예약_노쇼는_차감과_반환만_허용하고_메모와_함께_제출한다', async () => {
    const noShow = vi.fn().mockResolvedValue({ reservationId: 31, status: 'no_show', couponId: 71, couponAction: 'return' })
    renderPage(createApi({ noShow }))
    const card = await cardFor('김일반')
    fireEvent.click(within(card).getByRole('button', { name: '노쇼 처리' }))
    const couponSelect = within(card).getByLabelText('쿠폰 처리')
    expect(within(couponSelect).getByRole('option', { name: '1회 차감' })).toBeInTheDocument()
    expect(within(couponSelect).getByRole('option', { name: '점유 반환' })).toBeInTheDocument()
    expect(within(couponSelect).queryByRole('option', { name: '쿠폰 처리 없음' })).not.toBeInTheDocument()
    fireEvent.change(couponSelect, { target: { value: 'return' } })
    fireEvent.change(within(card).getByLabelText('관리자 메모'), { target: { value: '질병 사유 예외 반환' } })
    fireEvent.click(within(card).getByRole('button', { name: '노쇼 처리 확인' }))
    await waitFor(() => expect(noShow).toHaveBeenCalledWith(31, 'return', '질병 사유 예외 반환'))
    expect(await screen.findByText('쿠폰 처리 점유 반환')).toBeInTheDocument()
  })

  it('일회_결제_노쇼는_쿠폰_처리_없음만_허용한다', async () => {
    const noShow = vi.fn().mockResolvedValue({ reservationId: 33, status: 'no_show', couponAction: 'none' })
    renderPage(createApi({ noShow }))
    const card = await cardFor('박장애물')
    fireEvent.click(within(card).getByRole('button', { name: '노쇼 처리' }))
    const couponSelect = within(card).getByLabelText('쿠폰 처리')
    expect(within(couponSelect).getAllByRole('option')).toHaveLength(1)
    expect(couponSelect).toHaveValue('none')
    fireEvent.change(within(card).getByLabelText('관리자 메모'), { target: { value: '당일 미방문' } })
    fireEvent.click(within(card).getByRole('button', { name: '노쇼 처리 확인' }))
    await waitFor(() => expect(noShow).toHaveBeenCalledWith(33, 'none', '당일 미방문'))
  })

  it.each([[409, '최신 목록'], [403, '관리자 권한']])('%i_오류는_목록을_최신화하고_중복_제출을_막는다', async (status, message) => {
    const error = new ResponseError(new Response(null, { status }), 'failed')
    const getConfirmedReservations = vi.fn().mockResolvedValue([GENERAL])
    const complete = vi.fn().mockRejectedValue(error)
    renderPage(createApi({ getConfirmedReservations, complete }))
    const card = await cardFor('김일반')
    fireEvent.click(within(card).getByRole('button', { name: '수업 완료' }))
    const submit = within(card).getByRole('button', { name: '완료 처리 확인' })
    fireEvent.click(submit)
    fireEvent.click(submit)
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(complete).toHaveBeenCalledTimes(1)
    expect(getConfirmedReservations).toHaveBeenCalledTimes(2)
  })

  it('노쇼_메모가_없으면_API를_호출하지_않는다', async () => {
    const noShow = vi.fn()
    renderPage(createApi({ noShow }))
    const card = await cardFor('김일반')
    fireEvent.click(within(card).getByRole('button', { name: '노쇼 처리' }))
    fireEvent.click(within(card).getByRole('button', { name: '노쇼 처리 확인' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('1자 이상 500자 이하')
    expect(noShow).not.toHaveBeenCalled()
  })

  it('320px_화면에서도_완료와_노쇼_작업을_사용할_수_있다', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 320 })
    renderPage(createApi({ getConfirmedReservations: vi.fn().mockResolvedValue([GENERAL]) }))
    expect(await screen.findByRole('button', { name: '수업 완료' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '노쇼 처리' })).toBeInTheDocument()
  })
})
