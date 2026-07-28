import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import type { ReactNode } from 'react'
import { ResponseError, type ScheduleDateClosureImpactResponse, type TimeSlotClosureResponse } from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
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

afterEach(cleanup)

describe('AdminScheduleClosuresPage', () => {
  it('휴무_탭은_ARIA로_연결되고_방향키와_Home_End로_순환한다', async () => {
    renderPage(createApi())

    const dateTab = await screen.findByRole('tab', { name: '날짜 전체 휴무' })
    const slotTab = screen.getByRole('tab', { name: '개별 TimeSlot 휴강' })
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
    expect(screen.getByRole('tabpanel', { name: '개별 TimeSlot 휴강' }))
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
    expect(screen.getByRole('tabpanel', { name: '개별 TimeSlot 휴강' }))
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
    expect(screen.getByRole('tabpanel', { name: '개별 TimeSlot 휴강' }))
      .toHaveAttribute('aria-labelledby', 'time-slot-closure-tab')
  })

  it('날짜_휴무_영향을_ID순으로_확인하고_CLOSING을_한_번만_시작한다', async () => {
    const startDateClosing = vi.fn().mockResolvedValue({ ...DATE_IMPACT, status: 'CLOSING', changed: true })
    renderPage(createApi({ startDateClosing }))

    expect(await screen.findByRole('heading', { name: '날짜 전체 휴무' })).toBeInTheDocument()
    const impacts = screen.getAllByRole('heading', { level: 4 })
    expect(impacts[0]).toHaveTextContent('예약 #10')
    expect(impacts[1]).toHaveTextContent('예약 #20')
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '시설 점검' } })
    const trigger = screen.getByRole('button', { name: '영향 확인 후 휴무 시작' })
    trigger.focus()
    fireEvent.click(trigger)
    const dialog = await screen.findByRole('dialog', { name: '날짜 휴무 처리 시작' })
    expect(within(dialog).getByText(/활성 예약 2건/)).toBeInTheDocument()
    expect(within(dialog).getByRole('heading')).toHaveFocus()
    const confirm = within(dialog).getByRole('button', { name: 'CLOSING 시작' })
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

    expect(await screen.findByText('0/2건 해결 · 0%')).toBeInTheDocument()
    const firstCard = screen.getByRole('heading', { name: /예약 #10/ }).closest('article') as HTMLElement
    fireEvent.change(within(firstCard).getByLabelText('취소 메모'), { target: { value: '고객 연락 완료' } })
    fireEvent.click(within(firstCard).getByRole('button', { name: '연락 후 취소 확정' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '예약 취소 확정' }))
    expect(await within(firstCard).findByRole('alert')).toHaveTextContent('휴무 운영 상태를 처리하지 못했습니다.')
    fireEvent.click(within(firstCard).getByRole('button', { name: '연락 후 취소 확정' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '예약 취소 확정' }))
    expect(await within(firstCard).findByRole('status')).toHaveTextContent('쿠폰 NONE')
    fireEvent.click(within(firstCard).getByRole('button', { name: '연락 후 취소 확정' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '예약 취소 확정' }))
    expect(await within(firstCard).findByRole('status')).toHaveTextContent('이미 다른 종료 작업으로 처리된 예약입니다.')

    const couponCard = screen.getByRole('heading', { name: /예약 #20/ }).closest('article') as HTMLElement
    fireEvent.change(within(couponCard).getByLabelText('취소 메모'), { target: { value: '쿠폰 반환' } })
    fireEvent.click(within(couponCard).getByRole('button', { name: '연락 후 취소 확정' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '예약 취소 확정' }))
    expect(await within(couponCard).findByRole('status')).toHaveTextContent('쿠폰 RETURN')
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

    expect(await screen.findByText('2/2건 해결 · 100%')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '정리 완료' } })
    fireEvent.click(screen.getByRole('button', { name: 'CLOSED 확정' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'CLOSED 확정' }))
    await waitFor(() => expect(completeDateClosing).toHaveBeenCalledWith(expect.any(Date), '정리 완료', 3))

    fireEvent.click(screen.getByRole('button', { name: '휴무 처리 취소' }))
    expect(await screen.findByText(/날짜 상태를 NORMAL로 복귀/)).toBeInTheDocument()
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'CLOSING 취소' }))
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

    await screen.findByRole('heading', { name: '날짜 전체 휴무' })
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '예약 없는 임시 휴무' } })
    fireEvent.click(screen.getByRole('button', { name: '활성 예약 없음 · 즉시 CLOSED 확정' }))
    const dialog = await screen.findByRole('dialog', { name: '예약 없는 날짜 즉시 휴무' })
    expect(within(dialog).getByText(/활성 예약 0건을 서버가 같은 트랜잭션에서 다시 검사/)).toBeInTheDocument()
    fireEvent.click(within(dialog).getByRole('button', { name: '즉시 CLOSED 확정' }))
    await waitFor(() => expect(startDateClosing).toHaveBeenCalledWith(expect.any(Date), '예약 없는 임시 휴무', 3))
    expect(await screen.findByText('활성 예약 0건을 재검사하고 날짜를 CLOSED로 확정했습니다.')).toHaveAttribute('role', 'status')
  })

  it('개별_휴강은_고정_분모와_해결된_예약을_유지하고_쿠폰_RETURN을_표시한다', async () => {
    const cancelTimeSlotReservation = vi.fn().mockResolvedValue({ reservationId: 20, couponAction: 'RETURN', changed: true })
    renderPage(createApi({ cancelTimeSlotReservation }))
    fireEvent.click(await screen.findByRole('tab', { name: '개별 TimeSlot 휴강' }))

    expect(await screen.findByText('1/2건 해결 · 50%')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: /예약 #10/ }).closest('article')).toHaveClass('resolved')
    expect(screen.getByText('이미 1건이 해결되어 휴강 철회가 불가능합니다.')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '휴강 철회' })).toBeDisabled()
    const activeCard = screen.getByRole('heading', { name: /예약 #20/ }).closest('article') as HTMLElement
    fireEvent.change(within(activeCard).getByLabelText('취소 메모'), { target: { value: '고객 연락 완료' } })
    fireEvent.click(within(activeCard).getByRole('button', { name: '연락 후 취소 확정' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '예약 취소 확정' }))
    expect(await within(activeCard).findByRole('status')).toHaveTextContent('쿠폰 RETURN')
  })

  it('TimeSlot을_수업_날짜_시각_ID순으로_안정적으로_표시한다', async () => {
    renderPage(createApi({
      getTimeSlots: vi.fn().mockResolvedValue([
        { ...TIME_SLOT, id: 33, lessonDate: new Date('2026-08-03T00:00:00.000Z'), startTime: '09:00:00' },
        { ...TIME_SLOT, id: 32, lessonDate: new Date('2026-08-02T00:00:00.000Z'), startTime: '11:00:00' },
        TIME_SLOT,
      ]),
    }))
    fireEvent.click(await screen.findByRole('tab', { name: '개별 TimeSlot 휴강' }))
    const options = await screen.findAllByRole('option')
    expect(options.map((option) => option.textContent)).toEqual([
      expect.stringContaining('2026년 8월 2일 10:00 · #31'),
      expect.stringContaining('2026년 8월 2일 11:00 · #32'),
      expect.stringContaining('2026년 8월 3일 09:00 · #33'),
    ])
  })

  it('휴강_작업이_없으면_사유를_확인하고_고정_Impact_생성을_시작한다', async () => {
    const startTimeSlotClosure = vi.fn().mockResolvedValue(SLOT_CLOSURE)
    renderPage(createApi({
      getTimeSlotClosure: vi.fn().mockResolvedValue(null),
      startTimeSlotClosure,
    }))
    fireEvent.click(await screen.findByRole('tab', { name: '개별 TimeSlot 휴강' }))
    expect(await screen.findByText('선택한 TimeSlot에 휴강 작업이 없습니다.')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '강사 사정' } })
    fireEvent.click(screen.getByRole('button', { name: '휴강 시작' }))
    const dialog = await screen.findByRole('dialog', { name: '개별 TimeSlot 휴강 시작' })
    expect(within(dialog).getByText(/고정 목록으로 저장/)).toBeInTheDocument()
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
    fireEvent.click(await screen.findByRole('tab', { name: '개별 TimeSlot 휴강' }))
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
    expect(await screen.findByText(/재개해도 취소·이동된 예약은 복구되지 않으며/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '완료 후 재개' }))
    const dialog = await screen.findByRole('dialog', { name: '완료된 TimeSlot 재개' })
    expect(within(dialog).getByText('취소되거나 이동된 기존 예약은 자동 복구되지 않습니다.')).toBeInTheDocument()
    fireEvent.click(within(dialog).getByRole('button', { name: '예약 재개' }))
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
    expect(screen.queryByRole('button', { name: '완료 후 재개' })).not.toBeInTheDocument()
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
    fireEvent.click(await screen.findByRole('tab', { name: '개별 TimeSlot 휴강' }))
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '정리 완료' } })
    fireEvent.click(await screen.findByRole('button', { name: '휴강 정리 완료' }))
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
    await screen.findByRole('heading', { name: '날짜 전체 휴무' })
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '충돌 확인' } })
    fireEvent.click(screen.getByRole('button', { name: '영향 확인 후 휴무 시작' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'CLOSING 시작' }))

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
    await screen.findByRole('heading', { name: '날짜 전체 휴무' })
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '동기화 중 확인' } })
    fireEvent.click(screen.getByRole('button', { name: '영향 확인 후 휴무 시작' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'CLOSING 시작' }))

    const alert = await screen.findByRole('alert')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(alert).toHaveTextContent('시간표를 갱신하고 있습니다. 잠시 후 다시 시도해 주세요.')
    expect(alert).toHaveAttribute('data-error-code', 'SCHEDULE_CONFIG_SYNC_IN_PROGRESS')
    expect(within(alert).getByRole('button', { name: '다시 조회' })).toBeInTheDocument()
  })

  it('확인_dialog는_키보드_focus를_가두고_Escape_후_trigger로_복귀한다', async () => {
    renderPage(createApi())
    await screen.findByRole('heading', { name: '날짜 전체 휴무' })
    fireEvent.change(screen.getByLabelText('운영 사유'), { target: { value: '키보드 확인' } })
    const trigger = screen.getByRole('button', { name: '영향 확인 후 휴무 시작' })
    trigger.focus()
    fireEvent.click(trigger)
    const dialog = await screen.findByRole('dialog')
    const heading = within(dialog).getByRole('heading', { name: '날짜 휴무 처리 시작' })
    const cancel = within(dialog).getByRole('button', { name: '취소' })
    const confirm = within(dialog).getByRole('button', { name: 'CLOSING 시작' })
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
