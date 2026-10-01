import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import {
  AdminReservationQueryControllerApi,
  ResponseError,
  type AdminReservationResponse,
  type ReservationCompletionResponse,
} from '@horse/api-client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { adminAttendanceApi, type AdminAttendanceApi } from './admin-attendance.api'
import { AdminAttendancePage } from './admin-attendance-page'

const GENERAL: AdminReservationResponse = {
  reservationId: 31,
  memberId: 31,
  memberName: '김일반',
  memberPhone: '010-3131-3131',
  classType: 'ROUND_TROT',
  lessonDate: new Date(),
  startTime: '09:00:00',
  status: 'confirmed',
  paymentSource: 'coupon',
  coupon: { couponId: 71, couponType: 'GENERAL', status: 'active', remainingCount: 5, heldCount: 1, expiresAt: null },
  paymentDueAt: null,
  approvalRequestedAt: new Date('2026-07-01T01:00:00Z'),
  adminConfirmedAt: new Date('2026-07-01T02:00:00Z'),
  rejectedAt: null,
  rejectedBy: null,
  rejectionReason: null,
  cancelledAt: null,
  cancellationResponsibility: null,
  couponAction: null,
  adminMemo: null,
  approvalWarning: null,
  createdAt: new Date('2026-07-01T01:00:00Z'),
  updatedAt: new Date('2026-07-01T02:00:00Z'),
  displayGroup: 'PAST',
  actions: {
    complete: { allowed: true, blockedReason: null }, noShow: { allowed: true, blockedReason: null },
    change: { allowed: false, blockedReason: null }, cancel: { allowed: false, blockedReason: null },
    approve: { allowed: false, blockedReason: null },
  },
}

const DRESSAGE: AdminReservationResponse = {
  ...GENERAL,
  reservationId: 32,
  memberName: '이마술',
  classType: 'DRESSAGE',
  coupon: { couponId: 72, couponType: 'DRESSAGE', status: 'active', remainingCount: 7, heldCount: 1, expiresAt: null },
}

const JUMPING: AdminReservationResponse = {
  ...GENERAL,
  reservationId: 33,
  memberName: '박장애물',
  classType: 'JUMPING',
  paymentSource: 'single_payment',
  coupon: null,
}

const OVERDUE: AdminReservationResponse = {
  ...GENERAL,
  reservationId: 34,
  memberName: '최미처리',
  lessonDate: new Date(Date.now() - 48 * 60 * 60 * 1000),
}

const UPCOMING: AdminReservationResponse = {
  ...GENERAL,
  reservationId: 35,
  memberName: '정예정',
  lessonDate: new Date(Date.now() + 48 * 60 * 60 * 1000),
  displayGroup: 'UPCOMING',
  actions: {
    complete: { allowed: false, blockedReason: 'RESERVATION_LESSON_NOT_STARTED' },
    noShow: { allowed: false, blockedReason: 'RESERVATION_LESSON_NOT_STARTED' },
    change: { allowed: true, blockedReason: null }, cancel: { allowed: true, blockedReason: null },
    approve: { allowed: false, blockedReason: null },
  },
}

beforeEach(() => {
  // jsdom does not implement native modal dialogs; browser tests cover the top layer.
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value: function () { this.setAttribute('open', '') } })
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value: function () { this.removeAttribute('open') } })
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function createApi(overrides: Partial<AdminAttendanceApi> = {}): AdminAttendanceApi {
  return {
    getConfirmedReservations: vi.fn().mockResolvedValue([GENERAL, DRESSAGE, JUMPING]),
    processBulk: vi.fn().mockResolvedValue({ requestedCount: 3, succeededCount: 3, failedCount: 0, items: [] }),
    complete: vi.fn().mockResolvedValue({ reservationId: 31, status: 'completed', paymentSource: 'coupon', couponId: 71, generalRideCount: 12, dressageRideCount: 3, jumpingRideCount: 2 }),
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
      .mockResolvedValue({
        content: [GENERAL], page: 0, size: 100, totalElements: 1, totalPages: 1, hasNext: false,
      })

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
    fireEvent.click(within(card).getByText('연락처·결제 정보 보기'))
    expect(within(card).getByText('쿠폰 예약')).toBeInTheDocument()
    expect(within(card).getByText('쿠폰 번호 71 · 잔여 5회 · 예약 처리 중 1회')).toBeInTheDocument()
  })

  it('과거와_현재_예약을_각_날짜와_시작_시각으로_구분한다', async () => {
    renderPage(createApi({ getConfirmedReservations: vi.fn().mockResolvedValue([GENERAL, OVERDUE]) }))

    const todaySection = (await cardFor('김일반')).closest('section') as HTMLElement
    const overdueSection = (await cardFor('최미처리')).closest('section') as HTMLElement
    expect(todaySection).not.toBe(overdueSection)
    expect(within(todaySection).getByRole('heading', { level: 2 })).toHaveTextContent('09:00')
    expect(within(overdueSection).getByRole('heading', { level: 2 })).toHaveTextContent('09:00')
    expect(within(todaySection).getByRole('heading', { name: '김일반' })).toBeInTheDocument()
    expect(within(overdueSection).getByRole('heading', { name: '최미처리' })).toBeInTheDocument()
  })

  it('시작_전_수업은_처리_버튼_없이_서버_차단_이유를_표시한다', async () => {
    renderPage(createApi({ getConfirmedReservations: vi.fn().mockResolvedValue([UPCOMING]) }))
    const card = await cardFor('정예정')

    expect(within(card).getByRole('button', { name: '수업 완료' })).toBeDisabled()
    expect(within(card).getByRole('button', { name: '노쇼 입력' })).toBeDisabled()
    expect(within(card).getByRole('checkbox')).toBeDisabled()
    expect(within(card).getByText('아직 수업 시작 전이라 처리할 수 없습니다.')).toBeInTheDocument()
  })

  it.each([
    [GENERAL, { reservationId: 31, status: 'completed', paymentSource: 'coupon', generalRideCount: 12, dressageRideCount: 3, jumpingRideCount: 2, couponId: 71 }, '일반 12회'],
    [DRESSAGE, { reservationId: 32, status: 'completed', paymentSource: 'coupon', generalRideCount: 11, dressageRideCount: 4, jumpingRideCount: 2, couponId: 72 }, '마장마술 4회'],
    [JUMPING, { reservationId: 33, status: 'completed', paymentSource: 'single_payment', generalRideCount: 11, dressageRideCount: 3, jumpingRideCount: 3, couponId: null }, '장애물 3회'],
  ] as Array<[AdminReservationResponse, ReservationCompletionResponse, string]>)('%s_수업_완료_응답의_탑승_이력을_표시한다', async (reservation, response, expected) => {
    const complete = vi.fn().mockResolvedValue(response)
    renderPage(createApi({ getConfirmedReservations: vi.fn().mockResolvedValue([reservation]), complete }))
    const card = await cardFor(reservation.memberName as string)
    fireEvent.click(within(card).getByRole('button', { name: '수업 완료' }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).queryByRole('textbox')).not.toBeInTheDocument()
    fireEvent.click(within(dialog).getByRole('button', { name: '이 예약 결과 기록' }))
    await waitFor(() => expect(complete).toHaveBeenCalledWith(reservation.reservationId))
    expect(await screen.findByText('수업 완료 기록 성공')).toBeInTheDocument()
    expect(screen.getByText(new RegExp(expected))).toBeInTheDocument()
    expect(screen.queryByText('상태 completed')).not.toBeInTheDocument()
  })

  it('쿠폰_예약_노쇼는_차감과_반환만_허용하고_메모와_함께_제출한다', async () => {
    const noShow = vi.fn().mockResolvedValue({ reservationId: 31, status: 'no_show', couponId: 71, couponAction: 'return' })
    renderPage(createApi({ noShow }))
    const card = await cardFor('김일반')
    fireEvent.click(within(card).getByRole('button', { name: '노쇼 입력' }))
    const couponSelect = within(card).getByLabelText('김일반 쿠폰 처리')
    expect(within(couponSelect).getByRole('option', { name: '쿠폰 1회 차감' })).toBeInTheDocument()
    expect(within(couponSelect).getByRole('option', { name: '쿠폰 점유 반환' })).toBeInTheDocument()
    expect(within(couponSelect).queryByRole('option', { name: '쿠폰 처리 없음' })).not.toBeInTheDocument()
    fireEvent.change(couponSelect, { target: { value: 'return' } })
    fireEvent.change(within(card).getByLabelText('김일반 관리자 메모'), { target: { value: '질병 사유 예외 반환' } })
    fireEvent.click(within(card).getByRole('button', { name: '노쇼 확인' }))
    fireEvent.click(screen.getByRole('button', { name: '이 예약 결과 기록' }))
    await waitFor(() => expect(noShow).toHaveBeenCalledWith(31, 'return', '질병 사유 예외 반환'))
    const result = await screen.findByRole('status', { name: '이번 처리 결과' })
    expect(within(result).getByText('쿠폰 점유 반환')).toBeInTheDocument()
  })

  it('일회_결제_노쇼는_쿠폰_처리_없음만_허용한다', async () => {
    const noShow = vi.fn().mockResolvedValue({ reservationId: 33, status: 'no_show', couponAction: 'none' })
    renderPage(createApi({ noShow }))
    const card = await cardFor('박장애물')
    fireEvent.click(within(card).getByRole('button', { name: '노쇼 입력' }))
    expect(within(card).queryByRole('combobox')).not.toBeInTheDocument()
    expect(within(card).getByText('단건 결제 예약 · 별도 쿠폰 처리 없음')).toBeInTheDocument()
    fireEvent.change(within(card).getByLabelText('박장애물 관리자 메모'), { target: { value: '당일 미방문' } })
    fireEvent.click(within(card).getByRole('button', { name: '노쇼 확인' }))
    fireEvent.click(screen.getByRole('button', { name: '이 예약 결과 기록' }))
    await waitFor(() => expect(noShow).toHaveBeenCalledWith(33, 'none', '당일 미방문'))
  })

  it.each([[409, '최신 상태'], [403, '관리자 권한']])('%i_오류는_목록을_최신화하고_중복_제출을_막는다', async (status, message) => {
    const error = new ResponseError(new Response(null, { status }), 'failed')
    const getConfirmedReservations = vi.fn().mockResolvedValue([GENERAL])
    const complete = vi.fn().mockRejectedValue(error)
    renderPage(createApi({ getConfirmedReservations, complete }))
    const card = await cardFor('김일반')
    fireEvent.click(within(card).getByRole('button', { name: '수업 완료' }))
    const submit = screen.getByRole('button', { name: '이 예약 결과 기록' })
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
    fireEvent.click(within(card).getByRole('button', { name: '노쇼 입력' }))
    fireEvent.change(within(card).getByLabelText('김일반 쿠폰 처리'), { target: { value: 'deduct' } })
    fireEvent.click(within(card).getByRole('button', { name: '노쇼 확인' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('1자 이상 500자 이하')
    expect(noShow).not.toHaveBeenCalled()
  })

  it('320px_화면에서도_완료와_노쇼_작업을_사용할_수_있다', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 320 })
    renderPage(createApi({ getConfirmedReservations: vi.fn().mockResolvedValue([GENERAL]) }))
    expect(await screen.findByRole('button', { name: '수업 완료' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '노쇼 입력' })).toBeInTheDocument()
  })

  it('조회에서_허용돼도_Command의_휴강_거부를_표시하고_다시_조회한다', async () => {
    const getConfirmedReservations = vi.fn().mockResolvedValue([GENERAL])
    const complete = vi.fn().mockRejectedValue(new ResponseError(new Response(JSON.stringify({ code: 'TIMESLOT_CLOSURE_COMMAND_NOT_ALLOWED' }), { status: 409 }), 'closure'))
    renderPage(createApi({ getConfirmedReservations, complete }))
    fireEvent.click(within(await cardFor('김일반')).getByRole('button', { name: '수업 완료' }))
    fireEvent.click(screen.getByRole('button', { name: '이 예약 결과 기록' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('현재 휴강 처리된 수업 시간입니다.')
    await waitFor(() => expect(getConfirmedReservations).toHaveBeenCalledTimes(2))
  })

  it('loading_error_empty를_구분한다', async () => {
    const view = renderPage(createApi({ getConfirmedReservations: vi.fn(() => new Promise<AdminReservationResponse[]>(() => {})) }))
    expect(screen.getByRole('status')).toHaveTextContent('불러오는 중')
    view.unmount()
    const failed = renderPage(createApi({ getConfirmedReservations: vi.fn().mockRejectedValue(new Error('offline')) }))
    expect(await screen.findByRole('alert')).toHaveTextContent('불러오지 못했습니다')
    failed.unmount()
    renderPage(createApi({ getConfirmedReservations: vi.fn().mockResolvedValue([]) }))
    expect(await screen.findByRole('heading', { name: '현재 불러온 확정 예약이 없습니다.' })).toBeInTheDocument()
  })
})
