import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import type { ReactNode } from 'react'
import { ResponseError, type ScheduleDateClosureImpactResponse, type TimeSlotClosureResponse } from '@horse/api-client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AdminScheduleClosuresApi } from './admin-schedule-closures.api'
import { AdminScheduleClosuresPage } from './admin-schedule-closures-page'

const DATE_STATE = {
  scheduleDate: new Date('2026-08-01T00:00:00.000Z'),
  status: 'NORMAL' as const,
  appliedConfigVersion: 7,
  version: 3,
}
const DATE_IMPACT: ScheduleDateClosureImpactResponse = {
  scheduleDate: DATE_STATE.scheduleDate,
  status: 'NORMAL',
  resumeStatus: null,
  version: 3,
  initialReservationCount: 2,
  activeReservationCount: 2,
  resolvedReservationCount: 0,
  remainingReservationCount: 2,
  progressPercent: 0,
  reservations: [
    {
      reservationId: 20,
      memberId: 2,
      memberName: '김회원',
      memberPhone: '010-2222-2222',
      classType: 'ROUND_TROT',
      status: 'CONFIRMED',
      paymentSource: 'COUPON',
      couponId: 9,
    },
    {
      reservationId: 10,
      memberId: 1,
      memberName: '이회원',
      memberPhone: '010-1111-1111',
      classType: 'FIRST_RIDE',
      status: 'PENDING_PAYMENT',
      paymentSource: 'SINGLE_PAYMENT',
      couponId: null,
    },
  ],
  changed: false,
}
const TIME_SLOT = {
  id: 31,
  lessonDate: new Date('2026-08-02T00:00:00.000Z'),
  startTime: '10:00:00',
  totalCapacity: 8,
  roundArenaCapacity: 4,
  classCapacities: {},
  closed: false,
}
const SLOT_CLOSURE: TimeSlotClosureResponse = {
  timeSlotId: 31,
  adminClosed: true,
  closed: true,
  status: 'IN_PROGRESS',
  reason: '우천',
  startedAt: new Date('2026-07-30T01:00:00.000Z'),
  completedAt: null,
  withdrawnAt: null,
  version: 2,
  totalCount: 2,
  resolvedCount: 1,
  unresolvedCount: 1,
  progressPercent: 50,
  impacts: [
    {
      reservationId: 20,
      reservationStatusAtStart: 'CONFIRMED',
      currentStatus: 'CONFIRMED',
      resolved: false,
      moved: false,
      memberId: 2,
      memberName: '김회원',
      memberPhone: '010-2222-2222',
      classType: 'ROUND_TROT',
      paymentSource: 'COUPON',
      couponId: 9,
    },
    {
      reservationId: 10,
      reservationStatusAtStart: 'PENDING_PAYMENT',
      currentStatus: 'CANCELLED',
      resolved: true,
      moved: false,
      memberId: 1,
      memberName: '이회원',
      memberPhone: '010-1111-1111',
      classType: 'FIRST_RIDE',
      paymentSource: 'SINGLE_PAYMENT',
      couponId: null,
    },
  ],
}

function createApi(overrides: Partial<AdminScheduleClosuresApi> = {}): AdminScheduleClosuresApi {
  return {
    getScheduleDate: vi.fn().mockResolvedValue(DATE_STATE),
    getDateImpact: vi.fn().mockResolvedValue(DATE_IMPACT),
    startDateClosing: vi.fn().mockResolvedValue({ ...DATE_IMPACT, status: 'CLOSING', changed: true }),
    cancelDateReservation: vi.fn().mockResolvedValue({
      reservationId: 10,
      status: 'CANCELLED',
      responsibility: 'STABLE',
      couponAction: 'NONE',
      changed: true,
    }),
    completeDateClosing: vi.fn().mockResolvedValue({
      ...DATE_IMPACT,
      status: 'CLOSED',
      activeReservationCount: 0,
      remainingReservationCount: 0,
      changed: true,
    }),
    cancelDateClosing: vi.fn().mockResolvedValue({ ...DATE_IMPACT, status: 'NORMAL', changed: true }),
    getTimeSlots: vi.fn().mockResolvedValue([TIME_SLOT]),
    getTimeSlotClosure: vi.fn().mockResolvedValue(SLOT_CLOSURE),
    startTimeSlotClosure: vi.fn().mockResolvedValue(SLOT_CLOSURE),
    cancelTimeSlotReservation: vi.fn().mockResolvedValue({
      reservationId: 20,
      status: 'CANCELLED',
      responsibility: 'STABLE',
      couponAction: 'RETURN',
      changed: true,
    }),
    completeTimeSlotClosure: vi.fn().mockResolvedValue({ ...SLOT_CLOSURE, status: 'COMPLETED' }),
    withdrawTimeSlotClosure: vi.fn().mockResolvedValue({ ...SLOT_CLOSURE, status: 'WITHDRAWN' }),
    reopenTimeSlot: vi.fn().mockResolvedValue({ ...SLOT_CLOSURE, status: 'COMPLETED', adminClosed: false }),
    ...overrides,
  }
}

function renderPage(api: AdminScheduleClosuresApi) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>
  return { ...render(<AdminScheduleClosuresPage api={api} />, { wrapper: Wrapper }), client }
}

describe('Frozen closure workflow regression', () => {
  it('일정_설정_SYNCING은_휴무_휴강_Command의_전역_잠금이_아니다', async () => {
    const api = createApi({ getTimeSlotClosure: vi.fn().mockResolvedValue(null) })
    const { client } = renderPage(api)
    await screen.findByText('2건 중 0건 처리 · 0%')
    await act(async () => {
      client.setQueryData(['admin', 'schedule-configuration', 'sync'], { status: 'SYNCING', pendingVersion: 8 })
    })
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '일정 반영 중 휴무' } })
    fireEvent.click(screen.getByRole('button', { name: '날짜 휴무 시작' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '날짜 휴무 시작' }))
    await waitFor(() => expect(api.startDateClosing).toHaveBeenCalled())
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    fireEvent.click(screen.getByRole('tab', { name: '개별 수업 휴강' }))
    await screen.findByText('아직 영향 예약이 확정되지 않았습니다. 휴강 시작 후 확인할 수 있습니다.')
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '일정 반영 중 휴강' } })
    fireEvent.click(screen.getByRole('button', { name: '휴강 시작 검토' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '휴강 시작' }))
    await waitFor(() => expect(api.startTimeSlotClosure).toHaveBeenCalledWith(31, '일정 반영 중 휴강'))
  })

  it('일부_처리_후_진행_취소는_새_impact를_조회하고_이전_진행률을_보존하지_않는다', async () => {
    let impact: ScheduleDateClosureImpactResponse = {
      ...DATE_IMPACT, status: 'CLOSING', initialReservationCount: 3,
      resolvedReservationCount: 1, progressPercent: 33,
    }
    const getDateImpact = vi.fn().mockImplementation(async () => impact)
    const api = createApi({
      getScheduleDate: vi.fn().mockImplementation(async () => ({ ...DATE_STATE, status: impact.status })),
      getDateImpact,
      cancelDateClosing: vi.fn().mockImplementation(async () => {
        impact = { ...DATE_IMPACT, version: 4 }
        return impact
      }),
    })
    renderPage(api)
    await screen.findByText('3건 중 1건 처리 · 33%')
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '운영 재개' } })
    fireEvent.click(screen.getByRole('button', { name: '휴무 진행 취소' }))
    const calls = getDateImpact.mock.calls.length
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '진행 취소 확정' }))
    await screen.findByText('2건 중 0건 처리 · 0%')
    expect(getDateImpact.mock.calls.length).toBeGreaterThan(calls)
    expect(screen.queryByText('3건 중 1건 처리 · 33%')).not.toBeInTheDocument()
    expect(screen.getAllByRole('heading', { level: 4 })).toHaveLength(2)
    expect(screen.queryByText(/날짜 상태 버전/)).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1 })).toHaveFocus()
  })

  it('열린_dialog가_참조한_version이_바뀌면_confirm을_잠근다', async () => {
    const api = createApi()
    const { client } = renderPage(api)
    await screen.findByText('2건 중 0건 처리 · 0%')
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '휴무' } })
    fireEvent.click(screen.getByRole('button', { name: '날짜 휴무 시작' }))
    const dialog = await screen.findByRole('dialog')
    await act(async () => {
      client.setQueriesData({ queryKey: ['admin', 'schedule-closures', 'date-impact'] }, { ...DATE_IMPACT, version: 4 })
    })
    expect(within(dialog).getByRole('button', { name: '날짜 휴무 시작' })).toBeDisabled()
    expect(within(dialog).getByRole('alert')).toHaveTextContent(/최신 상태/)
    expect(api.startDateClosing).not.toHaveBeenCalled()
  })

  it('confirm_직전_재조회에서_version이_달라지면_Command를_실행하지_않는다', async () => {
    const api = createApi({ getDateImpact: vi.fn().mockResolvedValueOnce(DATE_IMPACT).mockResolvedValue({ ...DATE_IMPACT, version: 4 }) })
    renderPage(api)
    await screen.findByText('2건 중 0건 처리 · 0%')
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '휴무' } })
    fireEvent.click(screen.getByRole('button', { name: '날짜 휴무 시작' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '날짜 휴무 시작' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('다른 변경이 먼저 반영되었습니다.')
    expect(api.startDateClosing).not.toHaveBeenCalled()
  })

  it('최신_상태_확인_중_Escape로_닫으면_뒤늦게_Command를_전송하지_않는다', async () => {
    let finishCheck!: (value: ScheduleDateClosureImpactResponse) => void
    const checking = new Promise<ScheduleDateClosureImpactResponse>((resolve) => { finishCheck = resolve })
    const api = createApi({ getDateImpact: vi.fn().mockResolvedValueOnce(DATE_IMPACT).mockReturnValueOnce(checking) })
    renderPage(api)
    await screen.findByText('2건 중 0건 처리 · 0%')
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '휴무' } })
    fireEvent.click(screen.getByRole('button', { name: '날짜 휴무 시작' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '날짜 휴무 시작' }))
    await waitFor(() => expect(api.getDateImpact).toHaveBeenCalledTimes(2))
    fireEvent.keyDown(window, { key: 'Escape' })
    await act(async () => { finishCheck(DATE_IMPACT) })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(api.startDateClosing).not.toHaveBeenCalled()
  })

  it('개별_휴강_시작_전에는_영향_예약_숫자가_없고_서버_snapshot_후에만_나타난다', async () => {
    let closure: TimeSlotClosureResponse | null = null
    const api = createApi({
      getTimeSlotClosure: vi.fn().mockImplementation(async () => closure),
      startTimeSlotClosure: vi.fn().mockImplementation(async () => { closure = SLOT_CLOSURE; return closure }),
    })
    renderPage(api)
    fireEvent.click(screen.getByRole('tab', { name: '개별 수업 휴강' }))
    await screen.findByText('아직 영향 예약이 확정되지 않았습니다. 휴강 시작 후 확인할 수 있습니다.')
    expect(screen.queryByLabelText('영향 예약 처리 진행률')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { level: 4 })).not.toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '우천' } })
    fireEvent.click(screen.getByRole('button', { name: '휴강 시작 검토' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '휴강 시작' }))
    await screen.findByText('2건 중 1건 처리 · 50%')
    expect(api.startTimeSlotClosure).toHaveBeenCalledWith(31, '우천')
    expect(screen.getByRole('button', { name: '휴강 철회' })).toBeDisabled()
  })

  it('moved는_상태를_만들지_않고_변경으로_해결된_사실을_표시한다', async () => {
    renderPage(createApi({ getTimeSlotClosure: vi.fn().mockResolvedValue({
      ...SLOT_CLOSURE, impacts: [{ ...SLOT_CLOSURE.impacts[0], resolved: true, moved: true }],
    }) }))
    fireEvent.click(screen.getByRole('tab', { name: '개별 수업 휴강' }))
    expect(await screen.findByText('다른 시간으로 변경되어 처리 완료')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '예약 한 건 취소' })).not.toBeInTheDocument()
  })

  it('반려나_기한_만료로_해결된_예약을_취소된_것으로_표시하지_않는다', async () => {
    renderPage(createApi({ getTimeSlotClosure: vi.fn().mockResolvedValue({
      ...SLOT_CLOSURE, impacts: [{ ...SLOT_CLOSURE.impacts[0], currentStatus: 'payment_expired', resolved: true, moved: false }],
    }) }))
    fireEvent.click(screen.getByRole('tab', { name: '개별 수업 휴강' }))
    const card = (await screen.findByRole('heading', { name: /예약 번호 20/ })).closest('article')!
    expect(within(card).queryByText('취소 처리 완료')).not.toBeInTheDocument()
    fireEvent.click(within(card).getByText('예약 상세 및 처리'))
    expect(within(card).getByText('입금 기한 만료')).toBeInTheDocument()
    expect(within(card).queryByRole('button', { name: '예약 한 건 취소' })).not.toBeInTheDocument()
  })

  it.each([true, false])('재오픈은_서버_closed=%s_결과를_표시한다', async (closed) => {
    renderPage(createApi({ getTimeSlotClosure: vi.fn().mockResolvedValue({
      ...SLOT_CLOSURE, status: 'COMPLETED', adminClosed: false, closed,
    }) }))
    fireEvent.click(screen.getByRole('tab', { name: '개별 수업 휴강' }))
    expect(await screen.findByText(closed
      ? '관리자 휴강은 해제됐지만 다른 마감 원인으로 계속 마감됩니다.'
      : '관리자 휴강이 해제되어 현재 예약 가능합니다.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '관리자 휴강 해제' })).not.toBeInTheDocument()
  })

  it('철회된_snapshot은_더_이상_처리_목록에_표시하지_않는다', async () => {
    renderPage(createApi({ getTimeSlotClosure: vi.fn().mockResolvedValue({ ...SLOT_CLOSURE, status: 'WITHDRAWN', adminClosed: false }) }))
    fireEvent.click(screen.getByRole('tab', { name: '개별 수업 휴강' }))
    await screen.findByRole('heading', { name: '휴강 철회' })
    expect(screen.queryByRole('heading', { level: 4 })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('영향 예약 처리 진행률')).not.toBeInTheDocument()
  })

  it('시작된_수업_시간은_휴강_시작을_활성화하지_않는다', async () => {
    renderPage(createApi({ getTimeSlots: vi.fn().mockResolvedValue([{ ...TIME_SLOT, lessonDate: new Date('2026-07-29') }]), getTimeSlotClosure: vi.fn().mockResolvedValue(null) }))
    fireEvent.click(screen.getByRole('tab', { name: '개별 수업 휴강' }))
    await screen.findByText('아직 영향 예약이 확정되지 않았습니다. 휴강 시작 후 확인할 수 있습니다.')
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '우천' } })
    expect(screen.getByRole('button', { name: '휴강 시작 검토' })).toBeDisabled()
  })

  it('날짜로_기존_수업_시간_조회_결과만_좁히고_빈_날짜는_선택을_해제한다', async () => {
    const api = createApi({ getTimeSlots: vi.fn().mockResolvedValue([
      TIME_SLOT, { ...TIME_SLOT, id: 32, lessonDate: new Date('2026-08-03') },
    ]), getTimeSlotClosure: vi.fn().mockResolvedValue(null) })
    renderPage(api)
    fireEvent.click(screen.getByRole('tab', { name: '개별 수업 휴강' }))
    await screen.findByText('아직 영향 예약이 확정되지 않았습니다. 휴강 시작 후 확인할 수 있습니다.')
    fireEvent.change(screen.getByLabelText('대상 날짜'), { target: { value: '2026-08-03' } })
    await waitFor(() => expect(screen.getByLabelText('대상 수업 시간')).toHaveValue('32'))
    expect(screen.getAllByRole('option')).toHaveLength(1)
    fireEvent.change(screen.getByLabelText('대상 날짜'), { target: { value: '2026-08-04' } })
    expect(await screen.findByText('선택할 수업 시간이 없습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '휴강 시작 검토' })).not.toBeInTheDocument()
    expect(api.getTimeSlots).toHaveBeenCalledTimes(1)
  })
})

beforeEach(() => { vi.spyOn(Date, 'now').mockReturnValue(new Date('2026-07-30T01:00:00Z').getTime()) })
afterEach(() => { cleanup(); vi.restoreAllMocks() })

describe('AdminScheduleClosuresPage', () => {
  it('휴무_탭은_ARIA로_연결되고_방향키와_Home_End로_순환한다', async () => {
    renderPage(createApi())

    const dateTab = await screen.findByRole('tab', { name: '날짜 전체 휴무' })
    const slotTab = screen.getByRole('tab', { name: '개별 수업 휴강' })
    expect(dateTab).toHaveAttribute('aria-controls', 'date-closure-panel')
    expect(dateTab).toHaveAttribute('aria-selected', 'true')
    expect(dateTab).toHaveAttribute('tabindex', '0')
    expect(slotTab).toHaveAttribute('aria-controls', 'time-slot-closure-panel')
    expect(slotTab).toHaveAttribute('tabindex', '-1')
    expect(screen.getByRole('tabpanel', { name: '날짜 전체 휴무' }))
      .toHaveAttribute('aria-labelledby', 'date-closure-tab')

    dateTab.focus()
    fireEvent.keyDown(dateTab, { key: 'ArrowRight' })
    expect(slotTab).toHaveFocus()
    expect(slotTab).toHaveAttribute('aria-selected', 'true')
    expect(slotTab).toHaveAttribute('tabindex', '0')
    expect(screen.getByRole('tabpanel', { name: '개별 수업 휴강' }))
      .toHaveAttribute('aria-labelledby', 'time-slot-closure-tab')

    fireEvent.keyDown(slotTab, { key: 'ArrowRight' })
    expect(dateTab).toHaveFocus()
    expect(dateTab).toHaveAttribute('aria-selected', 'true')
    expect(slotTab).toHaveAttribute('tabindex', '-1')
    expect(screen.getByRole('tabpanel', { name: '날짜 전체 휴무' }))
      .toHaveAttribute('aria-labelledby', 'date-closure-tab')
    fireEvent.keyDown(dateTab, { key: 'ArrowLeft' })
    expect(slotTab).toHaveFocus()
    expect(slotTab).toHaveAttribute('aria-selected', 'true')
    expect(dateTab).toHaveAttribute('tabindex', '-1')
    expect(screen.getByRole('tabpanel', { name: '개별 수업 휴강' }))
      .toHaveAttribute('aria-labelledby', 'time-slot-closure-tab')
    fireEvent.keyDown(slotTab, { key: 'Home' })
    expect(dateTab).toHaveFocus()
    expect(dateTab).toHaveAttribute('aria-selected', 'true')
    expect(slotTab).toHaveAttribute('tabindex', '-1')
    expect(screen.getByRole('tabpanel', { name: '날짜 전체 휴무' }))
      .toHaveAttribute('aria-labelledby', 'date-closure-tab')
    fireEvent.keyDown(dateTab, { key: 'End' })
    expect(slotTab).toHaveFocus()
    expect(slotTab).toHaveAttribute('aria-selected', 'true')
    expect(dateTab).toHaveAttribute('tabindex', '-1')
    expect(screen.getByRole('tabpanel', { name: '개별 수업 휴강' }))
      .toHaveAttribute('aria-labelledby', 'time-slot-closure-tab')
  })

  it('날짜_휴무_영향을_ID순으로_확인하고_CLOSING을_한_번만_시작한다', async () => {
    const startDateClosing = vi.fn().mockResolvedValue({ ...DATE_IMPACT, status: 'CLOSING', changed: true })
    renderPage(createApi({ startDateClosing }))

    expect(await screen.findByRole('heading', { name: '미래 날짜 전체 휴무' })).toBeInTheDocument()
    const impacts = await screen.findAllByRole('heading', { level: 4 })
    expect(impacts[0]).toHaveTextContent('예약 번호 10')
    expect(impacts[1]).toHaveTextContent('예약 번호 20')
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '시설 점검' } })
    const trigger = screen.getByRole('button', { name: '날짜 휴무 시작' })
    trigger.focus()
    fireEvent.click(trigger)
    const dialog = await screen.findByRole('dialog', { name: '날짜 휴무 처리 시작' })
    expect(within(dialog).getByText(/현재 점유 예약 2건/)).toBeInTheDocument()
    expect(within(dialog).getByRole('heading')).toHaveFocus()
    const confirm = within(dialog).getByRole('button', { name: '날짜 휴무 시작' })
    fireEvent.click(confirm)
    fireEvent.click(confirm)
    await waitFor(() => expect(startDateClosing).toHaveBeenCalledTimes(1))
    expect(startDateClosing).toHaveBeenCalledWith(expect.any(Date), '시설 점검', 3)
  })

  it('날짜_CLOSING에서_예약별_NONE과_RETURN_취소를_표시하고_부분_실패를_재시도한다', async () => {
    const closingImpact = {
      ...DATE_IMPACT,
      status: 'CLOSING' as const,
      resumeStatus: 'NORMAL' as const,
    }
    const cancelDateReservation = vi.fn()
      .mockRejectedValueOnce(new Error('temporary'))
      .mockResolvedValueOnce({ reservationId: 10, couponAction: 'NONE', changed: true })
      .mockResolvedValueOnce({ reservationId: 10, couponAction: 'NONE', changed: false })
      .mockResolvedValueOnce({ reservationId: 20, couponAction: 'RETURN', changed: true })
    renderPage(createApi({
      getScheduleDate: vi.fn().mockResolvedValue({ ...DATE_STATE, status: 'CLOSING', resumeStatus: 'NORMAL' }),
      getDateImpact: vi.fn().mockResolvedValue(closingImpact),
      cancelDateReservation,
    }))

    expect(await screen.findByText('2건 중 0건 처리 · 0%')).toBeInTheDocument()
    const firstCard = screen.getByRole('heading', { name: /예약 번호 10/ }).closest('article') as HTMLElement
    fireEvent.click(within(firstCard).getByText('예약 상세 및 처리'))
    fireEvent.change(within(firstCard).getByLabelText('관리자 메모'), { target: { value: '고객 연락 완료' } })
    fireEvent.click(within(firstCard).getByRole('button', { name: '예약 한 건 취소' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '예약 한 건 취소' }))
    expect(await within(firstCard).findByRole('alert')).toHaveTextContent('휴무 운영 상태를 처리하지 못했습니다.')
    fireEvent.click(within(firstCard).getByRole('button', { name: '예약 한 건 취소' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '예약 한 건 취소' }))
    expect(await within(firstCard).findByRole('status')).toHaveTextContent('별도 쿠폰 처리가 없습니다.')
    fireEvent.click(within(firstCard).getByRole('button', { name: '예약 한 건 취소' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '예약 한 건 취소' }))
    expect(await within(firstCard).findByRole('status')).toHaveTextContent('이미 다른 종료 작업으로 처리된 예약입니다.')

    const couponCard = screen.getByRole('heading', { name: /예약 번호 20/ }).closest('article') as HTMLElement
    fireEvent.click(within(couponCard).getByText('예약 상세 및 처리'))
    fireEvent.change(within(couponCard).getByLabelText('관리자 메모'), { target: { value: '쿠폰 반환' } })
    fireEvent.click(within(couponCard).getByRole('button', { name: '예약 한 건 취소' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '예약 한 건 취소' }))
    expect(await within(couponCard).findByRole('status')).toHaveTextContent('쿠폰이 반환되었습니다.')
  })

  it('날짜_CLOSING은_남은_예약_0건에서만_CLOSED_확정하고_resumeStatus로_복귀한다', async () => {
    const completeDateClosing = vi.fn().mockResolvedValue({})
    const cancelDateClosing = vi.fn().mockResolvedValue({})
    renderPage(createApi({
      getScheduleDate: vi.fn().mockResolvedValue({ ...DATE_STATE, status: 'CLOSING', resumeStatus: 'NORMAL' }),
      getDateImpact: vi.fn().mockResolvedValue({
        ...DATE_IMPACT,
        status: 'CLOSING',
        resumeStatus: 'NORMAL',
        initialReservationCount: 2,
        activeReservationCount: 0,
        resolvedReservationCount: 2,
        remainingReservationCount: 0,
        progressPercent: 100,
        reservations: [],
      }),
      completeDateClosing,
      cancelDateClosing,
    }))

    expect(await screen.findByText('2건 중 2건 처리 · 100%')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '정리 완료' } })
    fireEvent.click(screen.getByRole('button', { name: '휴무 완료' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '휴무 완료' }))
    await waitFor(() => expect(completeDateClosing).toHaveBeenCalledWith(expect.any(Date), '정리 완료', 3))

    fireEvent.click(screen.getByRole('button', { name: '휴무 진행 취소' }))
    expect(await screen.findByText(/휴무 시작 전 운영 상태로 되돌립니다/)).toBeInTheDocument()
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: '진행 취소 확정' }))
    await waitFor(() => expect(cancelDateClosing).toHaveBeenCalledWith(expect.any(Date), '정리 완료', 3))
  })

  it('활성_예약이_없는_미래_날짜는_서버_재검사_후_즉시_CLOSED로_확정한다', async () => {
    const startDateClosing = vi.fn().mockResolvedValue({ ...DATE_IMPACT, status: 'CLOSED', changed: true })
    renderPage(createApi({
      getDateImpact: vi.fn().mockResolvedValue({
        ...DATE_IMPACT,
        initialReservationCount: 0,
        activeReservationCount: 0,
        resolvedReservationCount: 0,
        remainingReservationCount: 0,
        progressPercent: 100,
        reservations: [],
      }),
      startDateClosing,
    }))

    await screen.findByRole('heading', { name: '미래 날짜 전체 휴무' })
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '예약 없는 임시 휴무' } })
    fireEvent.click(screen.getByRole('button', { name: '날짜 휴무 시작' }))
    const dialog = await screen.findByRole('dialog', { name: '날짜 휴무 처리 시작' })
    expect(within(dialog).getByText(/예약이 없으면 휴무가 바로 완료/)).toBeInTheDocument()
    fireEvent.click(within(dialog).getByRole('button', { name: '날짜 휴무 시작' }))
    await waitFor(() => expect(startDateClosing).toHaveBeenCalledWith(expect.any(Date), '예약 없는 임시 휴무', 3))
    expect(await screen.findByText('날짜 휴무가 완료되었습니다. 신규 예약 차단이 유지됩니다.')).toHaveAttribute('role', 'status')
  })

  it('개별_휴강은_고정_분모와_해결된_예약을_유지하고_쿠폰_RETURN을_표시한다', async () => {
    const cancelTimeSlotReservation = vi.fn().mockResolvedValue({ reservationId: 20, couponAction: 'RETURN', changed: true })
    renderPage(createApi({ cancelTimeSlotReservation }))
    fireEvent.click(await screen.findByRole('tab', { name: '개별 수업 휴강' }))

    expect(await screen.findByText('2건 중 1건 처리 · 50%')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /예약 번호 10/ }).closest('article')).toHaveClass('resolved')
    expect(screen.getByText('이미 1건이 처리되어 휴강 철회가 불가능합니다.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '휴강 철회' })).toBeDisabled()
    const activeCard = screen.getByRole('heading', { name: /예약 번호 20/ }).closest('article') as HTMLElement
    fireEvent.click(within(activeCard).getByText('예약 상세 및 처리'))
    fireEvent.change(within(activeCard).getByLabelText('관리자 메모'), { target: { value: '고객 연락 완료' } })
    fireEvent.click(within(activeCard).getByRole('button', { name: '예약 한 건 취소' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '예약 한 건 취소' }))
    expect(await within(activeCard).findByRole('status')).toHaveTextContent('쿠폰이 반환되었습니다.')
  })

  it('TimeSlot을_수업_날짜_시각_ID순으로_안정적으로_표시한다', async () => {
    renderPage(createApi({
      getTimeSlots: vi.fn().mockResolvedValue([
        { ...TIME_SLOT, id: 33, lessonDate: new Date('2026-08-03T00:00:00.000Z'), startTime: '09:00:00' },
        { ...TIME_SLOT, id: 32, lessonDate: new Date('2026-08-02T00:00:00.000Z'), startTime: '11:00:00' },
        TIME_SLOT,
      ]),
    }))
    fireEvent.click(await screen.findByRole('tab', { name: '개별 수업 휴강' }))
    const options = await screen.findAllByRole('option')
    expect(options.map((option) => option.textContent)).toEqual([
      expect.stringContaining('2026년 8월 2일 10:00 · 정원 8명'),
      expect.stringContaining('2026년 8월 2일 11:00 · 정원 8명'),
      expect.stringContaining('2026년 8월 3일 09:00 · 정원 8명'),
    ])
  })

  it('휴강_작업이_없으면_사유를_확인하고_고정_Impact_생성을_시작한다', async () => {
    const startTimeSlotClosure = vi.fn().mockResolvedValue(SLOT_CLOSURE)
    renderPage(createApi({
      getTimeSlotClosure: vi.fn().mockResolvedValue(null),
      startTimeSlotClosure,
    }))
    fireEvent.click(await screen.findByRole('tab', { name: '개별 수업 휴강' }))
    expect(await screen.findByText('아직 영향 예약이 확정되지 않았습니다. 휴강 시작 후 확인할 수 있습니다.')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '강사 사정' } })
    fireEvent.click(screen.getByRole('button', { name: '휴강 시작 검토' }))
    const dialog = await screen.findByRole('dialog', { name: '개별 수업 시간 휴강 시작' })
    expect(within(dialog).getByText(/영향 예약 목록이 확정/)).toBeInTheDocument()
    fireEvent.click(within(dialog).getByRole('button', { name: '휴강 시작' }))
    await waitFor(() => expect(startTimeSlotClosure).toHaveBeenCalledWith(31, '강사 사정'))
  })

  it('처리된_예약이_없으면_휴강을_철회하고_완료된_휴강은_미복구_경고_후_재개한다', async () => {
    const withdrawTimeSlotClosure = vi.fn().mockResolvedValue({})
    const reopenTimeSlot = vi.fn().mockResolvedValue({})
    const untouchedClosure = { ...SLOT_CLOSURE, resolvedCount: 0, unresolvedCount: 2, progressPercent: 0 }
    const { client } = renderPage(createApi({
      getTimeSlotClosure: vi.fn().mockResolvedValue(untouchedClosure),
      withdrawTimeSlotClosure,
      reopenTimeSlot,
    }))
    fireEvent.click(await screen.findByRole('tab', { name: '개별 수업 휴강' }))
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '운영 재개' } })
    fireEvent.click(await screen.findByRole('button', { name: '휴강 철회' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '휴강 철회' }))
    await waitFor(() => expect(withdrawTimeSlotClosure).toHaveBeenCalledWith(31, '운영 재개', 2))

    client.setQueryData(['admin', 'schedule-closures', 'timeslot-closure', 31], {
      ...SLOT_CLOSURE,
      status: 'COMPLETED',
      resolvedCount: 2,
      unresolvedCount: 0,
      progressPercent: 100,
    })
    fireEvent.click(await screen.findByRole('button', { name: '관리자 휴강 해제' }))
    const dialog = await screen.findByRole('dialog', { name: '관리자 휴강 설정 해제' })
    expect(within(dialog).getByText(/다른 마감 원인이 남으면/)).toBeInTheDocument()
    expect(within(dialog).getByText(/취소되거나 이동된 기존 예약은 자동 복구되지 않습니다/)).toBeInTheDocument()
    fireEvent.click(within(dialog).getByRole('button', { name: '관리자 휴강 해제' }))
    await waitFor(() => expect(reopenTimeSlot).toHaveBeenCalledWith(31, '운영 재개', 2))

    client.setQueryData(['admin', 'schedule-closures', 'timeslot-closure', 31], {
      ...SLOT_CLOSURE,
      status: 'COMPLETED',
      adminClosed: false,
      closed: true,
      resolvedCount: 2,
      unresolvedCount: 0,
      progressPercent: 100,
    })
    expect(await screen.findByText(/다른 마감 원인 또는 날짜 운영 상태로 신규 예약은 계속 차단/)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '관리자 휴강 해제' })).not.toBeInTheDocument()
  })

  it('고정_대상이_모두_해결된_경우에만_휴강_완료를_확정한다', async () => {
    const completeTimeSlotClosure = vi.fn().mockResolvedValue({})
    renderPage(createApi({
      getTimeSlotClosure: vi.fn().mockResolvedValue({
        ...SLOT_CLOSURE,
        resolvedCount: 2,
        unresolvedCount: 0,
        progressPercent: 100,
        impacts: SLOT_CLOSURE.impacts.map((impact) => ({ ...impact, resolved: true, currentStatus: 'CANCELLED' })),
      }),
      completeTimeSlotClosure,
    }))
    fireEvent.click(await screen.findByRole('tab', { name: '개별 수업 휴강' }))
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '정리 완료' } })
    fireEvent.click(await screen.findByRole('button', { name: '휴강 완료' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '휴강 완료' }))
    await waitFor(() => expect(completeTimeSlotClosure).toHaveBeenCalledWith(31, '정리 완료', 2))
  })

  it('409_충돌은_구조화된_안내와_최신_상태_재조회를_제공한다', async () => {
    const response = new Response(JSON.stringify({
      code: 'SCHEDULE_VERSION_CONFLICT',
      message: '휴무 상태가 변경되었습니다. 최신 상태를 다시 조회해 주세요.',
      status: 409,
      timestamp: '2026-07-28T10:00:00+09:00',
      path: '/api/admin/schedule-dates/2026-08-01/closing',
      fieldErrors: [],
      details: { currentVersion: 4 },
    }), { status: 409, headers: { 'Content-Type': 'application/json' } })
    const startDateClosing = vi.fn().mockRejectedValue(new ResponseError(response, 'conflict'))
    const getScheduleDate = vi.fn().mockResolvedValue(DATE_STATE)
    renderPage(createApi({ startDateClosing, getScheduleDate }))
    await screen.findByRole('heading', { name: '미래 날짜 전체 휴무' })
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '충돌 확인' } })
    fireEvent.click(screen.getByRole('button', { name: '날짜 휴무 시작' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '날짜 휴무 시작' }))

    const alert = await screen.findByRole('alert')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(alert).toHaveTextContent('휴무 상태가 변경되었습니다. 최신 상태를 다시 조회해 주세요.')
    expect(alert).toHaveAttribute('data-error-code', 'SCHEDULE_VERSION_CONFLICT')
    const callsBeforeRefresh = getScheduleDate.mock.calls.length
    fireEvent.click(within(alert).getByRole('button', { name: '다시 조회' }))
    await waitFor(() => expect(getScheduleDate.mock.calls.length).toBeGreaterThan(callsBeforeRefresh))
  })

  it('503_SYNCING은_정확한_구조화_메시지와_재조회_동작을_제공한다', async () => {
    const response = new Response(JSON.stringify({
      code: 'SCHEDULE_CONFIG_SYNC_IN_PROGRESS',
      message: '시간표를 갱신하고 있습니다. 잠시 후 다시 시도해 주세요.',
      status: 503,
      timestamp: '2026-07-28T10:00:00+09:00',
      path: '/api/admin/schedule-dates/2026-08-01/closing',
      fieldErrors: [],
      details: {},
    }), { status: 503, headers: { 'Content-Type': 'application/json' } })
    renderPage(createApi({
      startDateClosing: vi.fn().mockRejectedValue(new ResponseError(response, 'syncing')),
    }))
    await screen.findByRole('heading', { name: '미래 날짜 전체 휴무' })
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '동기화 중 확인' } })
    fireEvent.click(screen.getByRole('button', { name: '날짜 휴무 시작' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '날짜 휴무 시작' }))

    const alert = await screen.findByRole('alert')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(alert).toHaveTextContent('시간표를 갱신하고 있습니다. 잠시 후 다시 시도해 주세요.')
    expect(alert).toHaveAttribute('data-error-code', 'SCHEDULE_CONFIG_SYNC_IN_PROGRESS')
    expect(within(alert).getByRole('button', { name: '다시 조회' })).toBeInTheDocument()
  })

  it('확인_dialog는_키보드_focus를_가두고_Escape_후_trigger로_복귀한다', async () => {
    renderPage(createApi())
    await screen.findByRole('heading', { name: '미래 날짜 전체 휴무' })
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '키보드 확인' } })
    const trigger = screen.getByRole('button', { name: '날짜 휴무 시작' })
    trigger.focus()
    fireEvent.click(trigger)
    const dialog = await screen.findByRole('dialog')
    const heading = within(dialog).getByRole('heading', { name: '날짜 휴무 처리 시작' })
    const cancel = within(dialog).getByRole('button', { name: '취소' })
    const confirm = within(dialog).getByRole('button', { name: '날짜 휴무 시작' })
    expect(heading).toHaveFocus()
    fireEvent.keyDown(window, { key: 'Tab', shiftKey: true })
    expect(confirm).toHaveFocus()
    confirm.focus()
    fireEvent.keyDown(window, { key: 'Tab' })
    expect(cancel).toHaveFocus()
    fireEvent.keyDown(window, { key: 'Tab', shiftKey: true })
    expect(confirm).toHaveFocus()
    fireEvent.keyDown(window, { key: 'Escape' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
  })
})
