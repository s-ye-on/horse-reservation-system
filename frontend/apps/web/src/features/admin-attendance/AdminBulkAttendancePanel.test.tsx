import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import {
  AdminBulkReservationAttendanceControllerApi,
  type AdminReservationResponse,
  type BulkReservationAttendanceResponse,
} from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { adminAttendanceApi, type AdminAttendanceApi } from './admin-attendance.api'
import { AdminBulkAttendancePanel } from './admin-bulk-attendance-panel'

const COUPON_RESERVATION: AdminReservationResponse = {
  reservationId: 41,
  memberName: '김쿠폰',
  memberPhone: '010-4141-4141',
  classType: 'ROUND_TROT',
  lessonDate: new Date('2026-08-10T00:00:00.000Z'),
  startTime: '09:00:00',
  status: 'confirmed',
  paymentSource: 'coupon',
  coupon: { couponId: 81, couponType: 'GENERAL', remainingCount: 4, heldCount: 1 },
}

const PAYMENT_RESERVATION: AdminReservationResponse = {
  ...COUPON_RESERVATION,
  reservationId: 42,
  memberName: '이결제',
  paymentSource: 'single_payment',
  coupon: undefined,
}

const NEXT_SLOT_RESERVATION: AdminReservationResponse = {
  ...COUPON_RESERVATION,
  reservationId: 43,
  memberName: '박다음',
  lessonDate: new Date('2026-08-11T00:00:00.000Z'),
  startTime: '10:00:00',
}

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

describe('AdminBulkAttendancePanel', () => {
  it('선택한_날짜와_시간대의_확정_예약만_표시한다', async () => {
    renderPanel(createApi(), [COUPON_RESERVATION, PAYMENT_RESERVATION, NEXT_SLOT_RESERVATION])

    expect(screen.getByText('김쿠폰')).toBeInTheDocument()
    expect(screen.getByText('이결제')).toBeInTheDocument()
    expect(screen.queryByText('박다음')).not.toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('수업 날짜'), { target: { value: '2026-08-11' } })

    expect(await screen.findByText('박다음')).toBeInTheDocument()
    expect(screen.getByLabelText('시작 시간')).toHaveValue('10:00:00')
    expect(screen.queryByText('김쿠폰')).not.toBeInTheDocument()
  })

  it('선택한_예약을_기본_수업_완료로_일괄_제출한다', async () => {
    const processBulk = vi.fn().mockResolvedValue(successResponse())
    const api = createApi({ processBulk })
    const { onProcessed } = renderPanel(api)

    fireEvent.click(screen.getByRole('button', { name: '선택 예약 일괄 처리' }))

    await waitFor(() => expect(processBulk).toHaveBeenCalledWith('2026-08-10', '09:00:00', [
      { reservationId: 41, action: 'complete', memo: undefined },
      { reservationId: 42, action: 'complete', memo: undefined },
    ]))
    expect(onProcessed).toHaveBeenCalledTimes(1)
  })

  it('노쇼는_예약_유형에_맞는_쿠폰_처리와_필수_메모를_제출한다', async () => {
    const processBulk = vi.fn().mockResolvedValue(successResponse())
    renderPanel(createApi({ processBulk }))
    fireEvent.change(screen.getByLabelText('김쿠폰 출석 결과'), { target: { value: 'no_show' } })

    const couponSelect = screen.getByLabelText('김쿠폰 쿠폰 처리')
    expect(within(couponSelect).getByRole('option', { name: '1회 차감' })).toBeInTheDocument()
    expect(within(couponSelect).getByRole('option', { name: '점유 반환' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '선택 예약 일괄 처리' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('1자 이상 500자 이하')
    expect(processBulk).not.toHaveBeenCalled()

    fireEvent.change(couponSelect, { target: { value: 'return' } })
    fireEvent.change(screen.getByLabelText('김쿠폰 관리자 메모'), { target: { value: '질병 예외 반환' } })
    fireEvent.change(screen.getByLabelText('이결제 출석 결과'), { target: { value: 'no_show' } })
    expect(screen.getByLabelText('이결제 쿠폰 처리')).toHaveValue('none')
    fireEvent.change(screen.getByLabelText('이결제 관리자 메모'), { target: { value: '당일 미방문' } })
    fireEvent.click(screen.getByRole('button', { name: '선택 예약 일괄 처리' }))

    await waitFor(() => expect(processBulk).toHaveBeenCalledWith('2026-08-10', '09:00:00', [
      { reservationId: 41, action: 'no_show', couponAction: 'return', memo: '질병 예외 반환' },
      { reservationId: 42, action: 'no_show', couponAction: 'none', memo: '당일 미방문' },
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

    fireEvent.click(screen.getByRole('button', { name: '선택 예약 일괄 처리' }))

    expect(await screen.findByText('성공 1건 · 실패 1건')).toBeInTheDocument()
    expect(screen.getByText('처리 완료 · completed')).toBeInTheDocument()
    expect(screen.getByText(/RESERVATION_INVALID_STATUS/)).toBeInTheDocument()
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
    fireEvent.click(screen.getByRole('button', { name: '선택 예약 일괄 처리' }))
    expect(await screen.findByText('성공 2건 · 실패 0건')).toBeInTheDocument()

    view.rerender(
      <QueryClientProvider client={client}>
        <AdminBulkAttendancePanel reservations={[]} api={api} onProcessed={onProcessed} />
      </QueryClientProvider>,
    )

    expect(screen.getByText('일괄 처리할 확정 예약이 없습니다.')).toBeInTheDocument()
    expect(screen.getByText('성공 2건 · 실패 0건')).toBeInTheDocument()
  })

  it('처리_중에는_중복_제출을_막는다', async () => {
    let resolveRequest: ((response: BulkReservationAttendanceResponse) => void) | undefined
    const processBulk = vi.fn(() => new Promise<BulkReservationAttendanceResponse>((resolve) => {
      resolveRequest = resolve
    }))
    renderPanel(createApi({ processBulk }))
    const submit = screen.getByRole('button', { name: '선택 예약 일괄 처리' })

    fireEvent.click(submit)
    fireEvent.click(submit)

    await waitFor(() => expect(processBulk).toHaveBeenCalledTimes(1))
    resolveRequest?.(successResponse())
    expect(await screen.findByText('성공 2건 · 실패 0건')).toBeInTheDocument()
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

    expect(screen.getByLabelText('수업 날짜')).toBeInTheDocument()
    expect(screen.getByLabelText('시작 시간')).toBeInTheDocument()
    expect(screen.getByLabelText('김쿠폰 출석 결과')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '선택 예약 일괄 처리' })).toBeInTheDocument()
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
