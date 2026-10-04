import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import {
  AdminBulkReservationAttendanceControllerApi,
  type AdminReservationResponse,
  type BulkReservationAttendanceResponse,
} from '@horse/api-client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { adminAttendanceApi, type AdminAttendanceApi } from './admin-attendance.api'
import { AdminBulkAttendancePanel } from './admin-bulk-attendance-panel'

const COUPON_RESERVATION: AdminReservationResponse = {
  reservationId: 41,
  memberId: 41,
  memberName: '김쿠폰',
  memberPhone: '010-4141-4141',
  classType: 'ROUND_TROT',
  lessonDate: new Date('2026-08-10T00:00:00.000Z'),
  startTime: '09:00:00',
  status: 'confirmed',
  paymentSource: 'coupon',
  coupon: { couponId: 81, couponType: 'general', status: 'active', remainingCount: 4, heldCount: 1, expiresAt: null },
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

const PAYMENT_RESERVATION: AdminReservationResponse = {
  ...COUPON_RESERVATION,
  reservationId: 42,
  memberName: '이결제',
  paymentSource: 'single_payment',
  coupon: null,
}

const NEXT_SLOT_RESERVATION: AdminReservationResponse = {
  ...COUPON_RESERVATION,
  reservationId: 43,
  memberName: '박다음',
  lessonDate: new Date('2026-08-11T00:00:00.000Z'),
  startTime: '10:00:00',
}

const BLOCKED_RESERVATION: AdminReservationResponse = {
  ...NEXT_SLOT_RESERVATION,
  reservationId: 44,
  memberName: '정예정',
  actions: {
    complete: { allowed: false, blockedReason: 'RESERVATION_LESSON_NOT_STARTED' },
    noShow: { allowed: false, blockedReason: 'RESERVATION_LESSON_NOT_STARTED' },
    change: { allowed: true, blockedReason: null }, cancel: { allowed: true, blockedReason: null },
    approve: { allowed: false, blockedReason: null },
  },
}

beforeEach(() => {
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value: function () { this.setAttribute('open', '') } })
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value: function () { this.removeAttribute('open') } })
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function createApi(overrides: Partial<AdminAttendanceApi> = {}): AdminAttendanceApi {
  return {
    getConfirmedReservations: vi.fn().mockResolvedValue([]),
    processBulk: vi.fn().mockResolvedValue(successResponse()),
    complete: vi.fn(),
    noShow: vi.fn(),
    ...overrides,
  }
}

function renderPanel(api: AdminAttendanceApi, reservations = [COUPON_RESERVATION, PAYMENT_RESERVATION]) {
  const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  const onProcessed = vi.fn().mockResolvedValue(undefined)
  const view = render(<AdminBulkAttendancePanel reservations={reservations} api={api} onProcessed={onProcessed} />, {
    wrapper: Wrapper,
  })
  return { onProcessed, ...view }
}

function selectForCompletion() {
  for (const checkbox of screen.getAllByRole('checkbox')) if (!(checkbox as HTMLInputElement).disabled) fireEvent.click(checkbox)
  for (const select of screen.getAllByRole('combobox')) fireEvent.change(select, { target: { value: 'complete' } })
}

function confirmBulk() {
  fireEvent.click(screen.getByRole('button', { name: '선택 예약 함께 확인' }))
  fireEvent.click(screen.getByRole('button', { name: /선택 \d+건 결과 기록/ }))
}

describe('AdminBulkAttendancePanel', () => {
  it('확정_예약을_날짜와_시작_시각별로_분리한다', async () => {
    renderPanel(createApi(), [COUPON_RESERVATION, PAYMENT_RESERVATION, NEXT_SLOT_RESERVATION])

    expect(screen.getByText('김쿠폰')).toBeInTheDocument()
    expect(screen.getByText('이결제')).toBeInTheDocument()
    const first = screen.getByRole('heading', { name: '김쿠폰' }).closest('section')
    expect(screen.getByRole('heading', { name: '이결제' }).closest('section')).toBe(first)
    const next = screen.getByRole('heading', { name: '박다음' }).closest('section')
    expect(next).not.toBe(first)
    expect(within(next as HTMLElement).getByRole('heading', { level: 2 })).toHaveTextContent('2026.08.11 · 10:00')
  })

  it('서버가_출석_처리를_허용한_예약만_일괄_대상으로_표시한다', () => {
    renderPanel(createApi(), [COUPON_RESERVATION, BLOCKED_RESERVATION])

    expect(screen.getByText('김쿠폰')).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: /정예정/ })).toBeDisabled()
    expect(screen.getByRole('checkbox', { name: /김쿠폰/ })).toBeEnabled()
  })

  it('선택한_예약을_기본_수업_완료로_일괄_제출한다', async () => {
    const processBulk = vi.fn().mockResolvedValue(successResponse())
    const api = createApi({ processBulk })
    const { onProcessed } = renderPanel(api)

    selectForCompletion()
    confirmBulk()

    await waitFor(() => expect(processBulk).toHaveBeenCalledWith('2026-08-10', '09:00:00', [
      { reservationId: 41, action: 'complete' },
      { reservationId: 42, action: 'complete' },
    ]))
    expect(onProcessed).toHaveBeenCalledTimes(1)
  })

  it('노쇼는_예약_유형에_맞는_쿠폰_처리와_필수_메모를_제출한다', async () => {
    const processBulk = vi.fn().mockResolvedValue(successResponse())
    renderPanel(createApi({ processBulk }))
    selectForCompletion()
    fireEvent.change(screen.getByLabelText('김쿠폰 처리 결과'), { target: { value: 'no_show' } })

    const couponSelect = screen.getByLabelText('김쿠폰 쿠폰 처리')
    expect(within(couponSelect).getByRole('option', { name: '쿠폰 1회 차감' })).toBeInTheDocument()
    expect(within(couponSelect).getByRole('option', { name: '쿠폰 점유 반환' })).toBeInTheDocument()
    fireEvent.change(couponSelect, { target: { value: 'return' } })
    fireEvent.click(screen.getByRole('button', { name: '선택 예약 함께 확인' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('1자 이상 500자 이하')
    expect(processBulk).not.toHaveBeenCalled()

    fireEvent.change(couponSelect, { target: { value: 'return' } })
    fireEvent.change(screen.getByLabelText('김쿠폰 관리자 메모'), { target: { value: '질병 예외 반환' } })
    fireEvent.change(screen.getByLabelText('이결제 처리 결과'), { target: { value: 'no_show' } })
    expect(screen.queryByLabelText('이결제 쿠폰 처리')).not.toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('이결제 관리자 메모'), { target: { value: '당일 미방문' } })
    confirmBulk()

    await waitFor(() => expect(processBulk).toHaveBeenCalledWith('2026-08-10', '09:00:00', [
      { reservationId: 41, action: 'no_show', couponAction: 'return', memo: '질병 예외 반환' },
      { reservationId: 42, action: 'no_show', couponAction: 'none', memo: '당일 미방문' },
    ]))
  })

  it('일괄_노쇼_확인에서_예약별_쿠폰_종류와_차감_반환_없음을_표시한다', async () => {
    const dressage = { ...COUPON_RESERVATION, reservationId: 43, memberName: '박마술', classType: 'DRESSAGE', coupon: { ...COUPON_RESERVATION.coupon!, couponId: 83, couponType: 'dressage' } }
    const processBulk = vi.fn().mockResolvedValue(successResponse())
    renderPanel(createApi({ processBulk }), [COUPON_RESERVATION, dressage, PAYMENT_RESERVATION])
    for (const checkbox of screen.getAllByRole('checkbox')) fireEvent.click(checkbox)
    for (const [name, couponAction] of [['김쿠폰', 'deduct'], ['박마술', 'return'], ['이결제', 'none']]) {
      fireEvent.change(screen.getByLabelText(`${name} 처리 결과`), { target: { value: 'no_show' } })
      if (couponAction !== 'none') fireEvent.change(screen.getByLabelText(`${name} 쿠폰 처리`), { target: { value: couponAction } })
      fireEvent.change(screen.getByLabelText(`${name} 관리자 메모`), { target: { value: `${name} 미방문` } })
    }
    fireEvent.click(screen.getByRole('button', { name: '선택 예약 함께 확인' }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByText('일반 기승 쿠폰 · 쿠폰 1회 차감')).toBeInTheDocument()
    expect(within(dialog).getByText('마장마술 쿠폰 · 쿠폰 점유 반환')).toBeInTheDocument()
    expect(within(dialog).getByText('단건 결제 · 별도 쿠폰 처리 없음')).toBeInTheDocument()
    expect(within(dialog).queryByText(/쿠폰 번호/)).not.toBeInTheDocument()
    fireEvent.click(within(dialog).getByRole('button', { name: '선택 3건 결과 기록' }))
    await waitFor(() => expect(processBulk).toHaveBeenCalledWith('2026-08-10', '09:00:00', [
      { reservationId: 41, action: 'no_show', couponAction: 'deduct', memo: '김쿠폰 미방문' },
      { reservationId: 43, action: 'no_show', couponAction: 'return', memo: '박마술 미방문' },
      { reservationId: 42, action: 'no_show', couponAction: 'none', memo: '이결제 미방문' },
    ]))
  })

  it('부분_성공의_항목별_상태와_오류를_표시한다', async () => {
    const response: BulkReservationAttendanceResponse = {
      requestedCount: 2,
      succeededCount: 1,
      failedCount: 1,
      items: [
        { reservationId: 41, action: 'complete', success: true, status: 'completed', errorCode: '', errorMessage: '' },
        { reservationId: 42, action: 'complete', success: false, status: '', errorCode: 'RESERVATION_INVALID_STATUS', errorMessage: '현재 예약 상태에서는 요청한 처리를 할 수 없습니다.' },
      ],
    }
    renderPanel(createApi({ processBulk: vi.fn().mockResolvedValue(response) }))

    selectForCompletion()
    confirmBulk()

    expect(await screen.findByText('요청 2건 · 성공 1건 · 실패 1건')).toBeInTheDocument()
    expect(screen.getByText('수업 완료 기록 성공')).toBeInTheDocument()
    expect(screen.getByRole('alert')).toHaveTextContent('예약 상태가 변경되었습니다.')
    expect(screen.queryByText(/RESERVATION_INVALID_STATUS/)).not.toBeInTheDocument()
  })

  it('처리_후_확정_목록이_비어도_방금_처리한_결과를_유지한다', async () => {
    const api = createApi()
    const onProcessed = vi.fn().mockResolvedValue(undefined)
    const client = new QueryClient({ defaultOptions: { mutations: { retry: false } } })
    const view = render(
      <QueryClientProvider client={client}>
        <AdminBulkAttendancePanel
          reservations={[COUPON_RESERVATION, PAYMENT_RESERVATION]}
          api={api}
          onProcessed={onProcessed}
        />
      </QueryClientProvider>,
    )
    selectForCompletion()
    confirmBulk()
    expect(await screen.findByText('요청 2건 · 성공 2건 · 실패 0건')).toBeInTheDocument()

    view.rerender(
      <QueryClientProvider client={client}>
        <AdminBulkAttendancePanel reservations={[]} api={api} onProcessed={onProcessed} />
      </QueryClientProvider>,
    )

    expect(screen.getByText('현재 불러온 확정 예약이 없습니다.')).toBeInTheDocument()
    expect(screen.getByText('요청 2건 · 성공 2건 · 실패 0건')).toBeInTheDocument()
  })

  it('처리_중에는_중복_제출을_막는다', async () => {
    let resolveRequest: ((response: BulkReservationAttendanceResponse) => void) | undefined
    const processBulk = vi.fn(() => new Promise<BulkReservationAttendanceResponse>((resolve) => {
      resolveRequest = resolve
    }))
    renderPanel(createApi({ processBulk }))
    selectForCompletion()
    fireEvent.click(screen.getByRole('button', { name: '선택 예약 함께 확인' }))
    const submit = screen.getByRole('button', { name: '선택 2건 결과 기록' })
    fireEvent.click(submit)
    fireEvent.click(submit)

    await waitFor(() => expect(processBulk).toHaveBeenCalledTimes(1))
    resolveRequest?.(successResponse())
    expect(await screen.findByText('요청 2건 · 성공 2건 · 실패 0건')).toBeInTheDocument()
  })

  it('생성_클라이언트에_수업_날짜와_항목을_평면_요청으로_전달한다', async () => {
    const processBulkAttendance = vi
      .spyOn(AdminBulkReservationAttendanceControllerApi.prototype, 'processBulkAttendance')
      .mockResolvedValue(successResponse())
    const items = [{ reservationId: 41, action: 'complete' }]

    await adminAttendanceApi.processBulk('2026-08-10', '09:00:00', items)

    expect(processBulkAttendance).toHaveBeenCalledWith({
      bulkReservationAttendanceRequest: {
        lessonDate: new Date('2026-08-10T00:00:00.000Z'),
        startTime: '09:00:00',
        items,
      },
    })
  })

  it('320px_화면에서도_시간대와_출석_결과를_선택할_수_있다', () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 320 })
    renderPanel(createApi())

    expect(screen.getByRole('heading', { name: '2026.08.10 · 09:00' })).toBeInTheDocument()
    selectForCompletion()
    expect(screen.getByLabelText('김쿠폰 처리 결과')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '선택 예약 함께 확인' })).toBeEnabled()
  })

  it('최대_8건만_선택하고_서로_다른_action_availability를_개별_검증한다', () => {
    const entries = Array.from({ length: 9 }, (_, index) => ({ ...COUPON_RESERVATION, reservationId: 100 + index, memberName: `회원${index}` }))
    renderPanel(createApi(), entries)
    for (const checkbox of screen.getAllByRole('checkbox').slice(0, 8)) fireEvent.click(checkbox)
    expect(screen.getAllByRole('checkbox')[8]).toBeDisabled()
    expect(screen.getByText('8건 선택')).toBeInTheDocument()
  })

  it('한_action만_허용된_예약도_선택하되_다른_action은_막는다', () => {
    const reservation = { ...COUPON_RESERVATION, actions: { ...COUPON_RESERVATION.actions, noShow: { allowed: false, blockedReason: 'RESERVATION_INVALID_STATUS' } } }
    renderPanel(createApi(), [reservation])
    fireEvent.click(screen.getByRole('checkbox'))
    expect(screen.getByRole('option', { name: '노쇼' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '노쇼 입력' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '수업 완료' })).toBeEnabled()
  })
})

function successResponse(): BulkReservationAttendanceResponse {
  return {
    requestedCount: 2,
    succeededCount: 2,
    failedCount: 0,
    items: [
      { reservationId: 41, action: 'complete', success: true, status: 'completed', errorCode: '', errorMessage: '' },
      { reservationId: 42, action: 'complete', success: true, status: 'completed', errorCode: '', errorMessage: '' },
    ],
  }
}
