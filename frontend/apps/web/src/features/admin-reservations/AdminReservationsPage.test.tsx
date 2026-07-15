import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { ResponseError, type AdminReservationResponse } from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import type { AdminReservationsApi } from './admin-reservations.api'
import { AdminReservationsPage } from './admin-reservations-page'

const RESERVATIONS: AdminReservationResponse[] = [
  {
    reservationId: 1,
    memberName: '김긴급',
    memberPhone: '010-1111-1111',
    classType: 'ROUND_BEGINNER',
    lessonDate: new Date('2026-08-12'),
    startTime: '09:00:00',
    status: 'pending_admin_approval',
    paymentSource: 'coupon',
    approvalWarning: 'critical',
    coupon: { couponId: 21, couponType: 'GENERAL', remainingCount: 4, heldCount: 1 },
  },
  {
    reservationId: 2,
    memberName: '이확인',
    memberPhone: '010-2222-2222',
    classType: 'ROUND_TROT',
    lessonDate: new Date('2026-08-10'),
    startTime: '11:00:00',
    status: 'pending_admin_approval',
    paymentSource: 'coupon',
    approvalWarning: 'warning',
    coupon: { couponId: 22, couponType: 'GENERAL', remainingCount: 8, heldCount: 1 },
  },
  {
    reservationId: 3,
    memberName: '박정상',
    memberPhone: '010-3333-3333',
    classType: 'LARGE_ARENA_TROT',
    lessonDate: new Date('2026-08-09'),
    startTime: '14:00:00',
    status: 'pending_admin_approval',
    paymentSource: 'coupon',
    approvalWarning: 'normal',
    coupon: { couponId: 23, couponType: 'GENERAL', remainingCount: 9, heldCount: 1 },
  },
  {
    reservationId: 4,
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
    reservationId: 5,
    memberName: '정만료',
    memberPhone: '010-5555-5555',
    classType: 'DRESSAGE',
    lessonDate: new Date('2026-08-13'),
    startTime: '16:00:00',
    status: 'payment_expired',
    paymentSource: 'single_payment',
  },
]

afterEach(cleanup)

function createApi(overrides: Partial<AdminReservationsApi> = {}): AdminReservationsApi {
  return {
    getActionableReservations: vi.fn().mockResolvedValue(RESERVATIONS),
    confirm: vi.fn().mockResolvedValue(undefined),
    reject: vi.fn().mockResolvedValue(undefined),
    restore: vi.fn().mockResolvedValue(undefined),
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
  it('세_상태와_서버_경고를_구분하고_긴급도_순으로_표시한다', async () => {
    renderPage(createApi())

    const approvalSection = (await screen.findByRole('heading', { name: '쿠폰 승인대기' })).closest('section') as HTMLElement
    expect(screen.getByRole('heading', { name: '입금대기' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '입금만료' })).toBeInTheDocument()
    expect(within(approvalSection).getByText('긴급')).toBeInTheDocument()
    expect(within(approvalSection).getByText('확인 필요')).toBeInTheDocument()
    expect(within(approvalSection).getByText('정상')).toBeInTheDocument()
    const cards = within(approvalSection).getAllByRole('article')
    expect(within(cards[0]).getByRole('heading', { name: '김긴급' })).toBeInTheDocument()
    expect(within(cards[1]).getByRole('heading', { name: '이확인' })).toBeInTheDocument()
    expect(within(cards[2]).getByRole('heading', { name: '박정상' })).toBeInTheDocument()
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
    const getActionableReservations = vi.fn().mockResolvedValue(RESERVATIONS)
    const confirm = vi.fn().mockRejectedValue(error)
    renderPage(createApi({ getActionableReservations, confirm }))
    const card = await cardFor('김긴급')
    fireEvent.click(within(card).getByRole('button', { name: '쿠폰 예약 확정' }))
    const submit = within(card).getByRole('button', { name: '예약 확정 확인' })
    fireEvent.click(submit)
    fireEvent.click(submit)
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(confirm).toHaveBeenCalledTimes(1)
    expect(getActionableReservations).toHaveBeenCalledTimes(2)
  })

  it('320px_화면에서도_상태별_작업을_사용할_수_있다', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 320 })
    renderPage(createApi())
    expect((await screen.findAllByRole('button', { name: '쿠폰 예약 확정' })).length).toBeGreaterThan(0)
    expect(screen.getByRole('button', { name: '입금 확인 및 확정' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '만료 예약 복구' })).toBeInTheDocument()
  })
})
