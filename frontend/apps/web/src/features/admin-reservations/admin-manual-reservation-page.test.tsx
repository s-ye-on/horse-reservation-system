import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { ResponseError, type AdminMemberResponse, type AdminReservationResponse, type ReservationApplicationResponse } from '@horse/api-client'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { AdminManualReservationPage } from './admin-manual-reservation-page'
import type { AdminManualReservationApi } from './admin-manual-reservation.api'
import { readManualReservationAttempt, saveManualReservationAttempt, type ManualReservationAttempt } from './admin-manual-reservation-attempt'

const member: AdminMemberResponse = { id: 7, name: '김회원', phone: '010-1111-2222', generalRideCount: 1, dressageRideCount: 0, jumpingRideCount: 0, progressionValue: 1, progressionClass: 'ROUND_BEGINNER', effectiveClass: 'ROUND_BEGINNER', progressionManagementStartedAt: null, progressionBaselineClass: null, progressionBaselineThreshold: null, progressionBaselineActualRideCount: null, specialApprovalProgressionCredit: 0, promotionHoldClass: null, dressageApproved: false, jumpingApproved: false, canUseLargeArena: false }
const response: ReservationApplicationResponse = { reservationId: 500, classType: 'FIRST_RIDE', lessonDate: new Date('2030-08-12'), startTime: '09:00:00', status: 'confirmed', paymentSource: 'coupon', coupon: { couponId: 91, expiresAt: null, remainingCount: 10, heldCount: 1, availableCount: 9 }, paymentDueAt: null }
const attempt: ManualReservationAttempt = { key: 'saved-key', request: { memberId: 7, timeSlotId: 100, classType: 'FIRST_RIDE', reason: '전화 접수' }, memberName: member.name, memberPhone: member.phone, lessonDate: '2030-08-12', startTime: '09:00:00' }

beforeAll(() => {
  HTMLDialogElement.prototype.showModal = function () { this.setAttribute('open', '') }
  HTMLDialogElement.prototype.close = function () { this.removeAttribute('open') }
})
afterEach(() => { cleanup(); sessionStorage.clear(); vi.restoreAllMocks(); vi.unstubAllGlobals() })

function createApi(overrides: Partial<AdminManualReservationApi> = {}): AdminManualReservationApi {
  return {
    getMembers: vi.fn().mockResolvedValue({ content: [member], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false }),
    getMember: vi.fn().mockResolvedValue(member),
    getTimeSlots: vi.fn().mockResolvedValue([{ id: 100, lessonDate: new Date('2030-08-12'), startTime: '09:00:00', totalCapacity: 8, roundArenaCapacity: 4, classCapacities: {}, closed: false, createdAt: new Date(), updatedAt: new Date() }, { id: 101, lessonDate: new Date('2030-08-12'), startTime: '10:00:00', totalCapacity: 8, roundArenaCapacity: 4, classCapacities: {}, closed: true, createdAt: new Date(), updatedAt: new Date() }]),
    getReservation: vi.fn().mockResolvedValue({ status: 'confirmed' } as AdminReservationResponse),
    create: vi.fn().mockResolvedValue(response), ...overrides,
  }
}
function renderPage(api = createApi(), accountSubject = 'admin-one') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const invalidate = vi.spyOn(client, 'invalidateQueries')
  return { ...render(<MemoryRouter><QueryClientProvider client={client}><AdminManualReservationPage api={api} accountSubject={accountSubject} /></QueryClientProvider></MemoryRouter>), client, invalidate }
}
async function review(reason = ' 전화 접수 ') {
  fireEvent.click(await screen.findByRole('button', { name: /김회원/ }))
  await waitFor(() => expect(screen.getByRole('button', { name: '처리 내용 확인' })).toBeEnabled())
  fireEvent.change(screen.getByLabelText('수업 시간'), { target: { value: '100' } })
  fireEvent.change(screen.getByLabelText('수업 클래스'), { target: { value: 'FIRST_RIDE' } })
  fireEvent.change(screen.getByLabelText('관리자 사유'), { target: { value: reason } })
  fireEvent.click(screen.getByRole('button', { name: '처리 내용 확인' }))
}
function error(status: number, code: string, fieldErrors?: unknown) { return new ResponseError(new Response(JSON.stringify({ code, fieldErrors }), { status }), 'failed') }

describe('관리자 수동 예약', () => {
  it('명시적인_회원_선택과_실제_후보_조회만_사용한다', async () => {
    const api = createApi(); renderPage(api)
    await screen.findByRole('button', { name: /김회원/ })
    expect(api.getMembers).toHaveBeenCalledWith(0, 20)
    expect(screen.getByRole('button', { name: '처리 내용 확인' })).toBeDisabled()
    await review()
    expect(screen.getByRole('dialog')).toHaveAccessibleName('수동 예약 생성 확인')
    expect(screen.getByRole('heading', { name: '수동 예약 생성 확인' })).toHaveFocus()
    expect(api.create).not.toHaveBeenCalled()
    expect(screen.queryByText(/잔여 좌석|예약 가능 클래스/)).not.toBeInTheDocument()
    expect(screen.queryByRole('combobox', { name: '결제 방식' })).not.toBeInTheDocument()
  })
  it('추가_입력_없이_서버_확정_결과와_최신_상태를_분리하고_관련_쿼리를_갱신한다', async () => {
    const api = createApi(); const { invalidate } = renderPage(api); await review()
    fireEvent.click(screen.getByRole('button', { name: '예약 생성 확인' }))
    const heading = await screen.findByRole('heading', { name: '예약이 확정되었습니다' })
    await waitFor(() => expect(heading).toHaveFocus())
    expect(api.create).toHaveBeenCalledWith(attempt.request, expect.any(String))
    expect(api.getReservation).toHaveBeenCalledWith(500)
    expect(screen.getByText(/현재 상태: 예약 확정/)).toBeInTheDocument()
    expect(readManualReservationAttempt('admin-one')).toBeUndefined()
    for (const queryKey of [['admin', 'actionable-reservations'], ['admin', 'timeslots'], ['admin', 'reservation-adjustment', 'time-slots']]) expect(invalidate).toHaveBeenCalledWith({ queryKey })
    expect(screen.getByRole('link', { name: '생성 예약 확인' })).toHaveAttribute('href', '/admin/reservations?reservationId=500')
  })
  it('입금대기_응답의_실제_마감_시각을_한국_시간으로_표시한다', async () => {
    renderPage(createApi({ create: vi.fn().mockResolvedValue({ ...response, status: 'pending_payment', paymentSource: 'single_payment', coupon: null, paymentDueAt: new Date('2030-08-12T08:47:00+09:00') }) })); await review()
    fireEvent.click(screen.getByRole('button', { name: '예약 생성 확인' }))
    await screen.findByRole('heading', { name: '입금 확인을 기다리고 있습니다' })
    expect(screen.getByText(/08:47/)).toBeInTheDocument()
    expect(screen.queryByText('처리된 쿠폰 정보')).not.toBeInTheDocument()
  })
  it.each(['', '   ', 'x'.repeat(501)])('사유_검증_오류를_필드에_연결한다', async (reason) => {
    const api = createApi(); renderPage(api); await review(reason)
    expect(screen.getByLabelText('관리자 사유')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('관리자 사유')).toHaveAttribute('aria-describedby', 'manual-reason-help manual-reason-error')
    expect(screen.getByRole('alert')).toHaveTextContent('500자')
    expect(api.create).not.toHaveBeenCalled()
  })
  it('pending_중_중복_제출은_한번만_실행한다', async () => {
    let finish!: (value: ReservationApplicationResponse) => void
    const api = createApi({ create: vi.fn(() => new Promise<ReservationApplicationResponse>((resolve) => { finish = resolve })) }); renderPage(api); await review()
    const submit = screen.getByRole('button', { name: '예약 생성 확인' }); fireEvent.click(submit); fireEvent.click(submit)
    await waitFor(() => expect(api.create).toHaveBeenCalledTimes(1))
    finish(response); await screen.findByRole('heading', { name: '예약이 확정되었습니다' })
  })
  it('응답_유실은_입력과_키를_보존하고_동일_요청으로_수동_확인한다', async () => {
    const create = vi.fn().mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce(response)
    renderPage(createApi({ create })); await review(); fireEvent.click(screen.getByRole('button', { name: '예약 생성 확인' }))
    await screen.findByRole('button', { name: '동일 요청 결과 확인' })
    expect(screen.queryByLabelText('관리자 사유')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '동일 요청 결과 확인' }))
    await screen.findByRole('heading', { name: '예약이 확정되었습니다' })
    expect(create.mock.calls[1]).toEqual(create.mock.calls[0])
  })
  it('복원_요청은_자동_전송하지_않고_현재_후보가_없어도_그대로_재전송한다', async () => {
    saveManualReservationAttempt('admin-one', attempt)
    const api = createApi({ getTimeSlots: vi.fn().mockResolvedValue([]) }); renderPage(api)
    expect(api.create).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: '동일 요청 결과 확인' }))
    await screen.findByRole('heading', { name: '예약이 확정되었습니다' })
    expect(api.create).toHaveBeenCalledWith(attempt.request, 'saved-key')
  })
  it('불확실한_요청_뒤_401과_키_충돌에서도_요청을_폐기하지_않는다', async () => {
    saveManualReservationAttempt('admin-one', attempt)
    const create = vi.fn().mockRejectedValueOnce(error(401, 'AUTH_INVALID_TOKEN')).mockRejectedValueOnce(error(409, 'RESERVATION_IDEMPOTENCY_KEY_CONFLICT'))
    renderPage(createApi({ create }))
    fireEvent.click(screen.getByRole('button', { name: '동일 요청 결과 확인' }))
    await screen.findByText(/다시 로그인한 뒤/)
    expect(readManualReservationAttempt('admin-one')?.key).toBe('saved-key')
    fireEvent.click(screen.getByRole('button', { name: '동일 요청 결과 확인' }))
    await screen.findByText(/기존 생성 요청과 충돌/)
    expect(create.mock.calls[1]).toEqual(create.mock.calls[0])
    expect(readManualReservationAttempt('admin-one')?.key).toBe('saved-key')
  })
  it('알려진_업무_거절은_최신_후보_조회후_입력_수정과_새로운_시도를_허용한다', async () => {
    const create = vi.fn().mockRejectedValueOnce(error(409, 'TIMESLOT_CLOSED')).mockResolvedValueOnce(response)
    const api = createApi({ create }); renderPage(api); await review(); fireEvent.click(screen.getByRole('button', { name: '예약 생성 확인' }))
    await screen.findByText(/대상 시간대가 마감/)
    await waitFor(() => expect(screen.getByRole('button', { name: '처리 내용 확인' })).toBeEnabled())
    expect(api.getTimeSlots).toHaveBeenCalledTimes(2)
    expect(readManualReservationAttempt('admin-one')).toBeUndefined()
    fireEvent.change(screen.getByLabelText('관리자 사유'), { target: { value: '재확인 접수' } })
    fireEvent.click(screen.getByRole('button', { name: '처리 내용 확인' })); fireEvent.click(screen.getByRole('button', { name: '예약 생성 확인' }))
    await screen.findByRole('heading', { name: '예약이 확정되었습니다' })
    expect(create.mock.calls[1][1]).not.toBe(create.mock.calls[0][1])
  })
  it('생성후_현재_조회_실패가_생성_실패로_바뀌지_않고_새_예약에는_새_키를_사용한다', async () => {
    const create = vi.fn().mockResolvedValue(response); renderPage(createApi({ create, getReservation: vi.fn().mockRejectedValue(new Error('query')) })); await review()
    fireEvent.click(screen.getByRole('button', { name: '예약 생성 확인' })); await screen.findByText(/생성은 반영되었지만/)
    expect(screen.getByRole('heading', { name: '예약이 확정되었습니다' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '새 예약 추가' })); await review()
    fireEvent.click(screen.getByRole('button', { name: '예약 생성 확인' })); await screen.findByRole('heading', { name: '예약이 확정되었습니다' })
    expect(create.mock.calls[1][1]).not.toBe(create.mock.calls[0][1])
  })
  it('저장_실패시_POST를_실행하지_않는다', async () => {
    const api = createApi(); renderPage(api); await review()
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('blocked') })
    fireEvent.click(screen.getByRole('button', { name: '예약 생성 확인' })); await screen.findByText(/생성하지 않았습니다/)
    expect(api.create).not.toHaveBeenCalled()
  })
  it('다른_관리자에게_저장된_요청을_노출하거나_전송하지_않는다', async () => {
    saveManualReservationAttempt('admin-one', attempt)
    const api = createApi(); renderPage(api, 'admin-two'); await screen.findByRole('button', { name: /김회원/ })
    expect(screen.queryByRole('button', { name: '동일 요청 결과 확인' })).not.toBeInTheDocument()
    expect(api.create).not.toHaveBeenCalled()
  })
  it('서버_회원_페이지를_이동해도_선택_회원을_보존한다', async () => {
    const second = { ...member, id: 8, name: '이회원' }
    const api = createApi({ getMembers: vi.fn((page) => Promise.resolve({ content: [page === 0 ? member : second], page, size: 20, totalElements: 21, totalPages: 2, hasNext: page === 0 })) }); renderPage(api)
    fireEvent.click(await screen.findByRole('button', { name: /김회원/ })); fireEvent.click(screen.getByRole('button', { name: '다음' }))
    await screen.findByRole('button', { name: /이회원/ }); expect(api.getMembers).toHaveBeenCalledWith(1, 20)
    expect(screen.getByText('김회원')).toBeInTheDocument()
    expect(within(screen.getByRole('navigation', { name: '회원 목록 페이지' })).getByText('2 / 2')).toBeInTheDocument()
  })
  it('이전_화면의_늦은_성공이_새_예약의_미확정_요청을_지우지_않는다', async () => {
    let finishOld!: (value: ReservationApplicationResponse) => void
    let finishNew!: (value: ReservationApplicationResponse) => void
    const create = vi.fn()
      .mockImplementationOnce(() => new Promise<ReservationApplicationResponse>((resolve) => { finishOld = resolve }))
      .mockResolvedValueOnce(response)
      .mockImplementationOnce(() => new Promise<ReservationApplicationResponse>((resolve) => { finishNew = resolve }))
    const api = createApi({ create })
    const old = renderPage(api); await review(); fireEvent.click(screen.getByRole('button', { name: '예약 생성 확인' }))
    await waitFor(() => expect(create).toHaveBeenCalledTimes(1)); old.unmount()
    renderPage(api); fireEvent.click(screen.getByRole('button', { name: '동일 요청 결과 확인' }))
    await screen.findByRole('heading', { name: '예약이 확정되었습니다' })
    fireEvent.click(screen.getByRole('button', { name: '새 예약 추가' })); await review('새 예약 시도')
    fireEvent.click(screen.getByRole('button', { name: '예약 생성 확인' }))
    await waitFor(() => expect(create).toHaveBeenCalledTimes(3))
    const current = readManualReservationAttempt('admin-one')
    finishOld(response)
    await waitFor(() => expect(old.client.getMutationCache().getAll()[0].state.status).toBe('success'))
    expect(readManualReservationAttempt('admin-one')).toEqual(current)
    finishNew(response); await screen.findByRole('heading', { name: '예약이 확정되었습니다' })
  })
  it('이전_화면의_늦은_업무_오류가_동일키_재전송의_저장정보를_지우지_않는다', async () => {
    let rejectOld!: (value: unknown) => void
    let finishRetry!: (value: ReservationApplicationResponse) => void
    const create = vi.fn()
      .mockImplementationOnce(() => new Promise<ReservationApplicationResponse>((_resolve, reject) => { rejectOld = reject }))
      .mockImplementationOnce(() => new Promise<ReservationApplicationResponse>((resolve) => { finishRetry = resolve }))
    const api = createApi({ create })
    const old = renderPage(api); await review(); fireEvent.click(screen.getByRole('button', { name: '예약 생성 확인' }))
    await waitFor(() => expect(create).toHaveBeenCalledTimes(1)); old.unmount()
    renderPage(api); fireEvent.click(screen.getByRole('button', { name: '동일 요청 결과 확인' }))
    await waitFor(() => expect(create).toHaveBeenCalledTimes(2))
    const current = readManualReservationAttempt('admin-one')
    rejectOld(error(409, 'TIMESLOT_CLOSED'))
    await waitFor(() => expect(old.client.getMutationCache().getAll()[0].state.status).toBe('error'))
    expect(readManualReservationAttempt('admin-one')).toEqual(current)
    expect(create.mock.calls[0]).toEqual(create.mock.calls[1])
    finishRetry(response); await screen.findByRole('heading', { name: '예약이 확정되었습니다' })
  })
})
