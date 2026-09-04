import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { ResponseError, type AdminReservationPageResponse, type AdminReservationResponse } from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import type { AdminReservationsApi } from './admin-reservations.api'
import { AdminReservationsPage } from './admin-reservations-page'

const BASE_RESERVATION: AdminReservationResponse = {
  reservationId: 0,
  memberId: 0,
  memberName: '',
  memberPhone: '',
  classType: 'FIRST_RIDE',
  lessonDate: new Date('2026-08-01'),
  startTime: '09:00:00',
  status: 'pending_admin_approval',
  paymentSource: 'coupon',
  coupon: null,
  paymentDueAt: null,
  approvalRequestedAt: new Date('2026-07-29T01:00:00Z'),
  adminConfirmedAt: null,
  rejectedAt: null,
  rejectedBy: null,
  rejectionReason: null,
  cancelledAt: null,
  cancellationResponsibility: null,
  couponAction: null,
  adminMemo: null,
  approvalWarning: null,
  createdAt: new Date('2026-07-29T01:00:00Z'),
  updatedAt: new Date('2026-07-29T01:00:00Z'),
  displayGroup: 'UPCOMING',
  actions: {
    change: { allowed: true, blockedReason: null },
    cancel: { allowed: true, blockedReason: null },
    complete: { allowed: false, blockedReason: null },
    noShow: { allowed: false, blockedReason: null },
    approve: { allowed: true, blockedReason: null },
  },
}

const RESERVATIONS: AdminReservationResponse[] = [
  {
    ...BASE_RESERVATION,
    reservationId: 1,
    memberId: 1,
    memberName: '김긴급',
    memberPhone: '010-1111-1111',
    classType: 'ROUND_BEGINNER',
    lessonDate: new Date('2026-08-12'),
    startTime: '09:00:00',
    status: 'pending_admin_approval',
    paymentSource: 'coupon',
    approvalWarning: 'critical',
    coupon: { couponId: 21, couponType: 'GENERAL', status: 'active', remainingCount: 4, heldCount: 1, expiresAt: null },
  },
  {
    ...BASE_RESERVATION,
    reservationId: 2,
    memberId: 2,
    memberName: '이확인',
    memberPhone: '010-2222-2222',
    classType: 'ROUND_TROT',
    lessonDate: new Date('2026-08-10'),
    startTime: '11:00:00',
    status: 'pending_admin_approval',
    paymentSource: 'coupon',
    approvalWarning: 'warning',
    coupon: { couponId: 22, couponType: 'GENERAL', status: 'active', remainingCount: 8, heldCount: 1, expiresAt: null },
  },
  {
    ...BASE_RESERVATION,
    reservationId: 3,
    memberId: 3,
    memberName: '박정상',
    memberPhone: '010-3333-3333',
    classType: 'LARGE_ARENA_TROT',
    lessonDate: new Date('2026-08-09'),
    startTime: '14:00:00',
    status: 'pending_admin_approval',
    paymentSource: 'coupon',
    approvalWarning: 'normal',
    coupon: { couponId: 23, couponType: 'GENERAL', status: 'active', remainingCount: 9, heldCount: 1, expiresAt: null },
  },
  {
    ...BASE_RESERVATION,
    reservationId: 4,
    memberId: 4,
    memberName: '최입금',
    memberPhone: '010-4444-4444',
    classType: 'FIRST_RIDE',
    lessonDate: new Date('2026-08-11'),
    startTime: '10:00:00',
    status: 'pending_payment',
    paymentSource: 'single_payment',
    paymentDueAt: new Date('2026-08-01T12:00:00+09:00'),
  },
  {
    ...BASE_RESERVATION,
    reservationId: 5,
    memberId: 5,
    memberName: '정만료',
    memberPhone: '010-5555-5555',
    classType: 'DRESSAGE',
    lessonDate: new Date('2026-08-13'),
    startTime: '16:00:00',
    status: 'payment_expired',
    paymentSource: 'single_payment',
  },
  {
    ...BASE_RESERVATION,
    reservationId: 6,
    memberId: 6,
    memberName: '한확정',
    memberPhone: '010-6666-6666',
    classType: 'ROUND_BEGINNER',
    lessonDate: new Date('2026-08-14'),
    startTime: '09:00:00',
    status: 'confirmed',
    paymentSource: 'coupon',
    coupon: { couponId: 26, couponType: 'GENERAL', status: 'active', remainingCount: 7, heldCount: 1, expiresAt: null },
  },
]

afterEach(cleanup)

function reservationPage(
  content: AdminReservationResponse[],
  page = 0,
  totalPages = content.length === 0 ? 0 : 1,
  totalElements = content.length,
): AdminReservationPageResponse {
  return {
    content,
    page,
    size: 20,
    totalElements,
    totalPages,
    hasNext: page + 1 < totalPages,
  }
}

function createApi(overrides: Partial<AdminReservationsApi> = {}): AdminReservationsApi {
  return {
    getReservations: vi.fn((status: string) => Promise.resolve(
      reservationPage(RESERVATIONS.filter((reservation) => reservation.status === status)),
    )),
    confirm: vi.fn().mockResolvedValue(undefined),
    reject: vi.fn().mockResolvedValue(undefined),
    restore: vi.fn().mockResolvedValue(undefined),
    getTimeSlots: vi.fn().mockResolvedValue([
      { id: 100, lessonDate: new Date('2026-08-14'), startTime: '09:00:00', totalCapacity: 8, roundArenaCapacity: 4, classCapacities: {}, closed: false, createdAt: new Date('2026-07-29T01:00:00Z'), updatedAt: new Date('2026-07-29T01:00:00Z') },
      { id: 101, lessonDate: new Date('2026-08-15'), startTime: '10:00:00', totalCapacity: 8, roundArenaCapacity: 4, classCapacities: {}, closed: false, createdAt: new Date('2026-07-29T01:00:00Z'), updatedAt: new Date('2026-07-29T01:00:00Z') },
      { id: 102, lessonDate: new Date('2026-08-16'), startTime: '11:00:00', totalCapacity: 8, roundArenaCapacity: 4, classCapacities: {}, closed: true, createdAt: new Date('2026-07-29T01:00:00Z'), updatedAt: new Date('2026-07-29T01:00:00Z') },
    ]),
    previewCancellation: vi.fn((_reservationId, responsibility) => Promise.resolve({
      reservationId: 6,
      timing: 'AFTER_CUTOFF',
      responsibility,
      couponAction: responsibility === 'member' ? 'deduct' : 'return',
    })),
    change: vi.fn().mockResolvedValue(undefined),
    cancel: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  }
}

function renderPage(api: AdminReservationsApi) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>
  return render(<AdminReservationsPage api={api} />, { wrapper: Wrapper })
}

async function cardFor(memberName: string) {
  return (await screen.findByRole('heading', { name: memberName })).closest('article') as HTMLElement
}

describe('AdminReservationsPage', () => {
  it('네_상태와_서버_경고를_구분하고_긴급도_순으로_표시한다', async () => {
    renderPage(createApi())

    const approvalSection = (await screen.findByRole('heading', { name: '쿠폰 승인대기' })).closest('section') as HTMLElement
    expect(screen.getByRole('heading', { name: '입금대기' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '입금만료' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '예약 확정' })).toBeInTheDocument()
    expect(within(approvalSection).getByText('긴급')).toBeInTheDocument()
    expect(within(approvalSection).getByText('확인 필요')).toBeInTheDocument()
    expect(within(approvalSection).getByText('정상')).toBeInTheDocument()
    const cards = within(approvalSection).getAllByRole('article')
    expect(within(cards[0]).getByRole('heading', { name: '김긴급' })).toBeInTheDocument()
    expect(within(cards[0]).getByText(/일반 쿠폰 #21/)).toBeInTheDocument()
    expect(within(cards[1]).getByRole('heading', { name: '이확인' })).toBeInTheDocument()
    expect(within(cards[2]).getByRole('heading', { name: '박정상' })).toBeInTheDocument()
  })

  it('상태별_Page를_독립적으로_이동하고_페이지_경계를_표시한다', async () => {
    const getReservations = vi.fn((status: string, page: number) => {
      if (status !== 'pending_admin_approval') {
        return Promise.resolve(reservationPage(
          RESERVATIONS.filter((reservation) => reservation.status === status),
        ))
      }
      return Promise.resolve(page === 0
        ? reservationPage([RESERVATIONS[0]], 0, 2, 2)
        : reservationPage([RESERVATIONS[1]], 1, 2, 2))
    })
    renderPage(createApi({ getReservations }))

    const navigation = await screen.findByRole('navigation', { name: '쿠폰 승인대기 페이지' })
    expect(within(navigation).getByRole('button', { name: '이전' })).toBeDisabled()
    expect(within(navigation).getByText('1 / 2')).toBeInTheDocument()
    fireEvent.click(within(navigation).getByRole('button', { name: '다음' }))

    expect(await screen.findByRole('heading', { name: '이확인' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '김긴급' })).not.toBeInTheDocument()
    const secondPageNavigation = screen.getByRole('navigation', { name: '쿠폰 승인대기 페이지' })
    expect(within(secondPageNavigation).getByText('2 / 2')).toBeInTheDocument()
    expect(within(secondPageNavigation).getByRole('button', { name: '다음' })).toBeDisabled()
    fireEvent.click(within(secondPageNavigation).getByRole('button', { name: '이전' }))
    expect(await screen.findByRole('heading', { name: '김긴급' })).toBeInTheDocument()
  })

  it('상태별_조회_오류를_재시도한다', async () => {
    let approvalRequestCount = 0
    const getReservations = vi.fn((status: string) => {
      if (status === 'pending_admin_approval') {
        approvalRequestCount += 1
        if (approvalRequestCount === 1) {
          return Promise.reject(new ResponseError(new Response(null, { status: 500 }), 'failed'))
        }
      }
      return Promise.resolve(reservationPage(
        RESERVATIONS.filter((reservation) => reservation.status === status),
      ))
    })
    renderPage(createApi({ getReservations }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('예약을 처리하지 못했습니다')
    fireEvent.click(within(alert).getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByRole('heading', { name: '김긴급' })).toBeInTheDocument()
  })

  it('쿠폰_예약을_확정하고_반려_사유를_확인한_뒤_전송한다', async () => {
    const confirm = vi.fn().mockResolvedValue(undefined)
    const reject = vi.fn().mockResolvedValue(undefined)
    renderPage(createApi({ confirm, reject }))

    const criticalCard = await cardFor('김긴급')
    fireEvent.click(within(criticalCard).getByRole('button', { name: '쿠폰 예약 확정' }))
    fireEvent.click(within(criticalCard).getByRole('button', { name: '예약 확정 확인' }))
    await waitFor(() => expect(confirm).toHaveBeenCalledWith(1))

    const warningCard = await cardFor('이확인')
    fireEvent.click(within(warningCard).getByRole('button', { name: '반려' }))
    fireEvent.change(within(warningCard).getByLabelText('반려 사유'), { target: { value: '회원 요청 확인 필요' } })
    fireEvent.click(within(warningCard).getByRole('button', { name: '예약 반려 확인' }))
    await waitFor(() => expect(reject).toHaveBeenCalledWith(2, '회원 요청 확인 필요'))
  })

  it('입금대기_예약은_입금_확인_절차로_확정한다', async () => {
    const confirm = vi.fn().mockResolvedValue(undefined)
    renderPage(createApi({ confirm }))
    const card = await cardFor('최입금')
    fireEvent.click(within(card).getByRole('button', { name: '입금 확인 및 확정' }))
    fireEvent.click(within(card).getByRole('button', { name: '예약 확정 확인' }))
    await waitFor(() => expect(confirm).toHaveBeenCalledWith(4))
  })

  it('입금만료_예약은_필수_메모와_함께_복구한다', async () => {
    const restore = vi.fn().mockResolvedValue(undefined)
    renderPage(createApi({ restore }))
    const card = await cardFor('정만료')
    fireEvent.click(within(card).getByRole('button', { name: '만료 예약 복구' }))
    fireEvent.change(within(card).getByLabelText('복구 메모'), { target: { value: '입금 내역을 늦게 확인함' } })
    fireEvent.click(within(card).getByRole('button', { name: '예약 복구 확인' }))
    await waitFor(() => expect(restore).toHaveBeenCalledWith(5, '입금 내역을 늦게 확인함'))
  })

  it.each([[409, '최신 목록'], [403, '관리자 권한']])('%i_처리_오류는_목록을_최신화하고_중복_제출을_막는다', async (status, message) => {
    const error = new ResponseError(new Response(null, { status }), 'failed')
    const getReservations = vi.fn((status: string) => Promise.resolve(
      reservationPage(RESERVATIONS.filter((reservation) => reservation.status === status)),
    ))
    const confirm = vi.fn().mockRejectedValue(error)
    renderPage(createApi({ getReservations, confirm }))
    const card = await cardFor('김긴급')
    fireEvent.click(within(card).getByRole('button', { name: '쿠폰 예약 확정' }))
    const submit = within(card).getByRole('button', { name: '예약 확정 확인' })
    fireEvent.click(submit)
    fireEvent.click(submit)
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(getReservations).toHaveBeenCalledTimes(8)
  })

  it('확정_예약을_열린_시간대와_필수_메모로_변경한다', async () => {
    const change = vi.fn().mockResolvedValue(undefined)
    renderPage(createApi({ change }))
    const card = await cardFor('한확정')
    expect(within(card).queryByRole('button', { name: '반려' })).not.toBeInTheDocument()
    fireEvent.click(within(card).getByRole('button', { name: '시간 변경' }))
    const timeSlot = await within(card).findByLabelText('변경 시간대')
    await within(timeSlot).findByRole('option', { name: /8월 15일/ })
    expect(within(timeSlot).queryByRole('option', { name: /8월 16일/ })).not.toBeInTheDocument()
    fireEvent.change(timeSlot, { target: { value: '101' } })
    fireEvent.change(within(card).getByLabelText('관리자 메모'), { target: { value: ' 회원 요청으로 변경 ' } })
    fireEvent.click(within(card).getByRole('button', { name: '시간 변경 확인' }))
    await waitFor(() => expect(change).toHaveBeenCalledWith(6, 101, '회원 요청으로 변경'))
  })

  it('취소_책임별_서버_권장안을_확인하고_최종_처리를_재정의한다', async () => {
    const previewCancellation = vi.fn((_reservationId, responsibility) => Promise.resolve({
      reservationId: 6,
      timing: 'AFTER_CUTOFF',
      responsibility,
      couponAction: responsibility === 'stable' ? 'return' : 'deduct',
    }))
    const cancel = vi.fn().mockResolvedValue(undefined)
    renderPage(createApi({ previewCancellation, cancel }))
    const card = await cardFor('한확정')
    fireEvent.click(within(card).getByRole('button', { name: '예약 취소' }))
    expect(await within(card).findByText('1회 차감')).toBeInTheDocument()
    fireEvent.change(within(card).getByLabelText('취소 책임'), { target: { value: 'stable' } })
    await waitFor(() => expect(previewCancellation).toHaveBeenCalledWith(6, 'stable'))
    const recommendation = within(card).getByText('서버 권장 처리').parentElement as HTMLElement
    await waitFor(() => expect(recommendation).toHaveTextContent('쿠폰 반환'))
    fireEvent.change(within(card).getByLabelText('최종 쿠폰 처리'), { target: { value: 'deduct' } })
    expect(within(card).getByText('권장안과 다른 최종 처리를 선택했습니다.')).toBeInTheDocument()
    fireEvent.change(within(card).getByLabelText('관리자 메모'), { target: { value: '마장 판단으로 차감' } })
    fireEvent.click(within(card).getByRole('button', { name: '예약 취소 확인' }))
    await waitFor(() => expect(cancel).toHaveBeenCalledWith(6, 'stable', 'deduct', '마장 판단으로 차감'))
  })

  it('일회_결제_예약_취소는_쿠폰_처리_없음만_선택한다', async () => {
    renderPage(createApi({
      previewCancellation: vi.fn().mockResolvedValue({ reservationId: 4, timing: 'BEFORE_CUTOFF', responsibility: 'member', couponAction: 'none' }),
    }))
    const card = await cardFor('최입금')
    fireEvent.click(within(card).getByRole('button', { name: '예약 취소' }))
    const couponAction = await within(card).findByLabelText('최종 쿠폰 처리')
    expect(within(couponAction).getAllByRole('option')).toHaveLength(1)
    expect(within(couponAction).getByRole('option', { name: '처리 없음' })).toBeInTheDocument()
  })

  it.each([[409, '최신 목록'], [403, '관리자 권한']])('%i_변경_오류를_운영자에게_안내한다', async (status, message) => {
    const error = new ResponseError(new Response(null, { status }), 'failed')
    renderPage(createApi({ change: vi.fn().mockRejectedValue(error) }))
    const card = await cardFor('한확정')
    fireEvent.click(within(card).getByRole('button', { name: '시간 변경' }))
    const timeSlot = await within(card).findByLabelText('변경 시간대')
    await within(timeSlot).findByRole('option', { name: /8월 15일/ })
    fireEvent.change(timeSlot, { target: { value: '101' } })
    fireEvent.change(within(card).getByLabelText('관리자 메모'), { target: { value: '변경 시도' } })
    fireEvent.click(within(card).getByRole('button', { name: '시간 변경 확인' }))
    expect(await within(card).findByRole('alert')).toHaveTextContent(message)
  })

  it('320px_화면에서도_상태별_작업을_사용할_수_있다', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 320 })
    renderPage(createApi())
    expect((await screen.findAllByRole('button', { name: '쿠폰 예약 확정' })).length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: '입금 확인 및 확정' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '만료 예약 복구' })).toBeInTheDocument()
    expect((screen.getAllByRole('button', { name: '시간 변경' })).length).toBeGreaterThan(0)
    expect((screen.getAllByRole('button', { name: '예약 취소' })).length).toBeGreaterThan(0)
  })
})
