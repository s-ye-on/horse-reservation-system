import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import type { AdminReservationResponse, AdminReservationSummaryResponse } from '@horse/api-client'
import { ResponseError } from '@horse/api-client'
import type { AdminDashboardApi, DashboardReservationStatus } from './admin-dashboard.api'
import { AdminDashboardPage } from './admin-dashboard-page'

const SUMMARY: AdminReservationSummaryResponse = {
  lessonDateFrom: new Date('2026-07-17T00:00:00.000Z'),
  lessonDateTo: new Date('2026-07-17T00:00:00.000Z'),
  totalCount: 7,
  statusCounts: [
    { status: 'pending_admin_approval', count: 3 },
    { status: 'pending_payment', count: 2 },
    { status: 'payment_expired', count: 1 },
    { status: 'confirmed', count: 1 },
  ],
  dailyCounts: [],
}

const APPROVAL_RESERVATION: AdminReservationResponse = {
  reservationId: 11,
  memberId: 11,
  memberName: '김승인',
  memberPhone: '010-1111-2222',
  classType: 'ROUND_BEGINNER',
  lessonDate: new Date('2026-07-17T00:00:00.000Z'),
  startTime: '09:00:00',
  status: 'pending_admin_approval',
  paymentSource: 'coupon',
  coupon: {
    couponId: 11, couponType: 'GENERAL', status: 'active', remainingCount: 5,
    heldCount: 1, expiresAt: null,
  },
  paymentDueAt: null,
  approvalRequestedAt: new Date('2026-07-17T01:00:00Z'),
  adminConfirmedAt: null,
  rejectedAt: null,
  rejectedBy: null,
  rejectionReason: null,
  cancelledAt: null,
  cancellationResponsibility: null,
  couponAction: null,
  adminMemo: null,
  approvalWarning: 'critical',
  createdAt: new Date('2026-07-17T01:00:00Z'),
  updatedAt: new Date('2026-07-17T01:00:00Z'),
  displayGroup: 'UPCOMING',
  actions: {
    change: { allowed: true, blockedReason: null },
    cancel: { allowed: true, blockedReason: null },
    complete: { allowed: false, blockedReason: null },
    noShow: { allowed: false, blockedReason: null },
    approve: { allowed: true, blockedReason: null },
  },
}

afterEach(() => cleanup())

function createApi(overrides: Partial<AdminDashboardApi> = {}): AdminDashboardApi {
  return {
    getSummary: vi.fn().mockResolvedValue(SUMMARY),
    getReservations: vi.fn().mockResolvedValue([APPROVAL_RESERVATION]),
    ...overrides,
  }
}

function renderPage(api: AdminDashboardApi) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  )
  return render(<AdminDashboardPage api={api} />, { wrapper })
}

describe('AdminDashboardPage', () => {
  it('오늘_집계와_승인대기_경고_예약을_표시한다', async () => {
    const api = createApi()

    renderPage(api)

    expect(await screen.findByRole('heading', { name: '관리자 운영 대시보드' })).toBeInTheDocument()
    expect(await screen.findByText('선택 기간 전체')).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: /쿠폰 승인대기 3/ })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: /입금대기 2/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /입금만료 1/ })).toBeInTheDocument()
    expect(await screen.findByText('김승인')).toBeInTheDocument()
    expect(screen.getByText('긴급')).toBeInTheDocument()
    expect(api.getReservations).toHaveBeenCalledWith('pending_admin_approval', '2026-07-17', '2026-07-17')
  })

  it('상태를_선택하면_같은_날짜_범위로_예약을_조회한다', async () => {
    const getReservations = vi.fn(async (status: DashboardReservationStatus) => status === 'pending_payment'
      ? [{ ...APPROVAL_RESERVATION, reservationId: 12, memberName: '이입금', status: 'pending_payment', approvalWarning: null }]
      : [APPROVAL_RESERVATION])
    const api = createApi({ getReservations })
    renderPage(api)
    await screen.findByText('김승인')

    fireEvent.click(screen.getByRole('button', { name: /입금대기 2/ }))

    expect(await screen.findByText('이입금')).toBeInTheDocument()
    expect(getReservations).toHaveBeenLastCalledWith('pending_payment', '2026-07-17', '2026-07-17')
  })

  it('입력한_기간을_집계에_적용한다', async () => {
    const api = createApi()
    renderPage(api)
    await screen.findByText('김승인')

    fireEvent.change(screen.getByLabelText('시작일'), { target: { value: '2026-07-20' } })
    fireEvent.change(screen.getByLabelText('종료일'), { target: { value: '2026-07-25' } })
    fireEvent.click(screen.getByRole('button', { name: '기간 적용' }))

    await waitFor(() => expect(api.getSummary).toHaveBeenLastCalledWith('2026-07-20', '2026-07-25'))
  })

  it('예약이_없을_때_빈_상태를_표시한다', async () => {
    renderPage(createApi({ getReservations: vi.fn().mockResolvedValue([]) }))

    expect(await screen.findByText('선택한 기간과 상태에 해당하는 예약이 없습니다.')).toBeInTheDocument()
  })

  it('집계_실패를_빈_상태와_구분해_표시한다', async () => {
    renderPage(createApi({ getSummary: vi.fn().mockRejectedValue(new Error('network')) }))

    expect(await screen.findByRole('alert')).toHaveTextContent('운영 현황을 불러오지 못했습니다.')
  })

  it('기간과_상태에_접근가능한_이름을_제공한다', async () => {
    renderPage(createApi())

    expect(await screen.findByLabelText('시작일')).toBeInTheDocument()
    expect(screen.getByLabelText('종료일')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '기간 적용' })).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: /입금만료 1/ })).toBeInTheDocument()
  })
  it('서버_전체집계와_최대100건의_표시건수를_분리한다', async () => {
    renderPage(createApi({
      getSummary: vi.fn().mockResolvedValue({ ...SUMMARY, totalCount: 500, statusCounts: [{ status: 'pending_admin_approval', count: 220 }] }),
      getReservations: vi.fn().mockResolvedValue(Array.from({ length: 100 }, (_, index) => ({ ...APPROVAL_RESERVATION, reservationId: index + 1 }))),
    }))
    expect(await screen.findByRole('button', { name: '쿠폰 승인대기 220건' })).toBeInTheDocument()
    expect(screen.getByText('500')).toBeInTheDocument()
    expect(await screen.findByText('불러온 예약 100건 · 최대 100건 표시')).toBeInTheDocument()
    expect(screen.queryByText(/긴급 100|예약률|가동률|매출/)).not.toBeInTheDocument()
    expect(screen.getByText(/입금 마감은 예약 후 2시간과 수업 시작 시각/)).toBeInTheDocument()
  })
  it('loading은_0건으로_표시하지_않으며_기간폼을_유지한다', async () => {
    renderPage(createApi({ getSummary: vi.fn(() => new Promise<AdminReservationSummaryResponse>(() => {})) }))
    expect(screen.getByRole('status')).toHaveTextContent('불러오는 중')
    expect(screen.getByLabelText('시작일')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '오늘' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /쿠폰 승인대기/ })).not.toBeInTheDocument()
    expect(screen.queryByText(/0건/)).not.toBeInTheDocument()
  })
  it('집계_실패에도_기간폼과_오늘_재조회를_유지하여_복구한다', async () => {
    const api = createApi({ getSummary: vi.fn().mockRejectedValueOnce(new Error('network')).mockResolvedValue(SUMMARY) })
    renderPage(api)
    await screen.findByRole('alert')
    expect(screen.getByLabelText('종료일')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '오늘' })).toBeInTheDocument()
    expect(screen.queryByText(/0건/)).not.toBeInTheDocument()
    expect(api.getReservations).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: '다시 조회' }))
    expect(await screen.findByText('김승인')).toBeInTheDocument()
    expect(api.getSummary).toHaveBeenCalledTimes(2)
  })
  it('역전된_날짜는_field_error로_연결하고_기존_적용범위를_유지한다', async () => {
    const api = createApi(); renderPage(api); await screen.findByText('김승인')
    fireEvent.change(screen.getByLabelText('시작일'), { target: { value: '2026-07-25' } })
    fireEvent.change(screen.getByLabelText('종료일'), { target: { value: '2026-07-20' } })
    fireEvent.click(screen.getByRole('button', { name: '기간 적용' }))
    expect(screen.getByRole('alert')).toHaveTextContent('종료일은 시작일보다 빠를 수 없습니다')
    expect(screen.getByLabelText('종료일')).toHaveFocus()
    expect(screen.getByLabelText('종료일')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('종료일').getAttribute('aria-describedby')).toContain('dashboard-period-error')
    expect(api.getSummary).toHaveBeenCalledTimes(1)
    fireEvent.change(screen.getByLabelText('종료일'), { target: { value: '2026-07-26' } })
    expect(screen.getByLabelText('종료일')).toHaveAttribute('aria-invalid', 'false')
  })
  it('한쪽_날짜는_그대로_요청하고_목록에는_서버확정_하루를_전달한다', async () => {
    const api = createApi({ getSummary: vi.fn().mockResolvedValueOnce(SUMMARY).mockResolvedValue({ ...SUMMARY,
      lessonDateFrom: new Date('2026-07-20'), lessonDateTo: new Date('2026-07-20') }) })
    renderPage(api); await screen.findByText('김승인')
    fireEvent.change(screen.getByLabelText('종료일'), { target: { value: '2026-07-20' } })
    fireEvent.click(screen.getByRole('button', { name: '기간 적용' }))
    await waitFor(() => expect(api.getSummary).toHaveBeenLastCalledWith(undefined, '2026-07-20'))
    await waitFor(() => expect(api.getReservations).toHaveBeenLastCalledWith('pending_admin_approval', '2026-07-20', '2026-07-20'))
    expect(screen.getByRole('heading', { name: '2026-07-20' })).toBeInTheDocument()
  })
  it('같은_기간_재조회는_집계뿐_아니라_선택목록도_갱신한다', async () => {
    const api = createApi({ getReservations: vi.fn().mockResolvedValueOnce([APPROVAL_RESERVATION]).mockResolvedValue([{ ...APPROVAL_RESERVATION, memberName: '최신 회원' }]) })
    renderPage(api); await screen.findByText('김승인')
    fireEvent.click(screen.getByRole('button', { name: '기간 적용' }))
    expect(await screen.findByText('최신 회원')).toBeInTheDocument()
    expect(api.getSummary).toHaveBeenCalledTimes(2)
    expect(api.getReservations).toHaveBeenCalledTimes(2)
  })
  it('서버400_후_수정한_draft에_이전오류를_붙이지_않고_오늘로_복구한다', async () => {
    const api = createApi({ getSummary: vi.fn().mockRejectedValueOnce(new ResponseError(new Response('{}', { status: 400 }), 'bad')).mockResolvedValue(SUMMARY) })
    renderPage(api)
    await waitFor(() => expect(screen.getByLabelText('시작일')).toHaveAttribute('aria-invalid', 'true'))
    fireEvent.change(screen.getByLabelText('시작일'), { target: { value: '2026-07-17' } })
    expect(screen.getByLabelText('시작일')).toHaveAttribute('aria-invalid', 'false')
    fireEvent.click(screen.getByRole('button', { name: '오늘' }))
    expect(await screen.findByText('김승인')).toBeInTheDocument()
    expect(api.getSummary).toHaveBeenLastCalledWith(undefined, undefined)
  })
  it('목록_오류는_집계와_분리하고_0건으로_표시하지_않으며_재조회한다', async () => {
    const api = createApi({ getReservations: vi.fn().mockRejectedValueOnce(new Error('network')).mockResolvedValue([]) })
    renderPage(api); await screen.findByRole('alert')
    expect(screen.getByRole('button', { name: '쿠폰 승인대기 3건' })).toBeInTheDocument()
    expect(screen.queryByText(/불러온 예약 0건/)).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '다시 조회' }))
    expect(await screen.findByText('선택한 기간과 상태에 해당하는 예약이 없습니다.')).toBeInTheDocument()
  })
  it('집계_재조회_실패시_이전_cached_집계와_목록을_최신값처럼_표시하지_않는다', async () => {
    const api = createApi({ getSummary: vi.fn().mockResolvedValueOnce(SUMMARY).mockRejectedValue(new Error('network')) })
    renderPage(api); await screen.findByText('김승인')
    fireEvent.click(screen.getByRole('button', { name: '기간 적용' }))
    await screen.findByRole('alert')
    expect(screen.queryByRole('button', { name: '쿠폰 승인대기 3건' })).not.toBeInTheDocument()
    expect(screen.queryByText('김승인')).not.toBeInTheDocument()
    expect(screen.getByLabelText('시작일')).toBeInTheDocument()
    expect(api.getReservations).toHaveBeenCalledTimes(1)
  })
  it('알수없는_enum은_추정하지_않고_raw값과_ID를_숨긴다', async () => {
    renderPage(createApi({ getReservations: vi.fn().mockResolvedValue([{ ...APPROVAL_RESERVATION, classType: 'UNKNOWN_CLASS', status: 'UNKNOWN_STATUS',
      paymentSource: 'UNKNOWN_PAYMENT', approvalWarning: 'UNKNOWN_WARNING' }]) }))
    await screen.findByText('김승인')
    expect(screen.getByText('수업 정보 확인 필요')).toBeInTheDocument()
    expect(screen.getByText('결제 정보 확인 필요')).toBeInTheDocument()
    expect(screen.getByText('경고 정보 확인 필요')).toBeInTheDocument()
    expect(screen.queryByText(/UNKNOWN_|예약 #11/)).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: '예약 운영 전체 보기' })).toHaveAttribute('href', '/admin/reservations')
    expect(screen.queryByRole('button', { name: /확인 후 적용|노쇼|승인하기/ })).not.toBeInTheDocument()
  })
})
