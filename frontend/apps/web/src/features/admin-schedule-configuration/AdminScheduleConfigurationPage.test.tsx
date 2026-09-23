import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { ResponseError, type RecurringHolidayResponse, type ScheduleSynchronizationResponse, type ScheduleTemplateResponse } from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { AdminScheduleConfigurationApi } from './admin-schedule-configuration.api'
import { AdminScheduleConfigurationPage } from './admin-schedule-configuration-page'

const CLASS_CAPACITIES = {
  FIRST_RIDE: 2,
  ROUND_BEGINNER: 2,
  ROUND_TROT: 2,
  LARGE_ARENA_BEGINNER: 3,
  LARGE_ARENA_TROT: 3,
  CANTER_BEGINNER: 3,
  CANTER: 3,
  DRESSAGE: 1,
  JUMPING: 1,
}
const TEMPLATE: ScheduleTemplateResponse = {
  templateId: 1,
  dayOfWeek: 'TUESDAY',
  startTime: '09:00:00',
  endTime: '09:45:00',
  totalCapacity: 8,
  roundArenaCapacity: 4,
  classCapacities: CLASS_CAPACITIES,
  active: true,
  version: 0,
}
const HOLIDAY: RecurringHolidayResponse = {
  holidayId: 2,
  dayOfWeek: 'MONDAY',
  effectiveFrom: new Date('2026-01-01T00:00:00.000Z'),
  effectiveTo: null,
  reason: '정기 휴무',
  active: true,
  version: 0,
}
const ACTIVE_SYNC: ScheduleSynchronizationResponse = {
  status: 'ACTIVE',
  activeVersion: 7,
  pendingVersion: null,
  horizonStart: new Date('2026-07-28T00:00:00.000Z'),
  horizonEnd: new Date('2026-10-28T00:00:00.000Z'),
  totalDateCount: 93,
  appliedDateCount: 93,
  remainingDateCount: 0,
  progressPercent: 100,
  syncStartedAt: null,
  lastCompletedAt: null,
  lastFailedAt: null,
  lastFailureCode: null,
  lastFailureSummary: null,
  longRunning: false,
}
const CONFIGURATION_QUERY_KEY = ['admin', 'schedule-configuration'] as const

function createApi(overrides: Partial<AdminScheduleConfigurationApi> = {}): AdminScheduleConfigurationApi {
  return {
    getTemplates: vi.fn().mockResolvedValue([TEMPLATE]),
    previewTemplate: vi.fn().mockResolvedValue({ affectedDateCount: 13, existingTimeSlotCount: 12, activeReservationCount: 2 }),
    createTemplate: vi.fn().mockResolvedValue({}),
    updateTemplate: vi.fn().mockResolvedValue({}),
    deleteTemplate: vi.fn().mockResolvedValue({}),
    getFutureOccupyingReservations: vi.fn().mockResolvedValue({ templateId: 1, reservationCount: 0, reservations: [] }),
    getHolidays: vi.fn().mockResolvedValue([HOLIDAY]),
    previewHoliday: vi.fn().mockResolvedValue({
      previous: { affectedDateCount: 0, templateTimeSlotCount: 0, activeReservationCount: 0 },
      current: { affectedDateCount: 13, templateTimeSlotCount: 91, activeReservationCount: 3 },
      combined: { affectedDateCount: 13, templateTimeSlotCount: 91, activeReservationCount: 3 },
    }),
    createHoliday: vi.fn().mockResolvedValue({}),
    updateHoliday: vi.fn().mockResolvedValue({}),
    changeHolidayActivation: vi.fn().mockResolvedValue({}),
    getSynchronization: vi.fn().mockResolvedValue(ACTIVE_SYNC),
    retrySynchronization: vi.fn().mockResolvedValue({}),
    ...overrides,
  }
}

function renderPage(api: AdminScheduleConfigurationApi) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => <MemoryRouter><QueryClientProvider client={client}>{children}</QueryClientProvider></MemoryRouter>
  return { ...render(<AdminScheduleConfigurationPage api={api} />, { wrapper: Wrapper }), client }
}

afterEach(cleanup)

describe('AdminScheduleConfigurationPage', () => {
  it('정규_시간표와_월요일_휴일을_조회하고_검색한다', async () => {
    renderPage(createApi())
    expect(await screen.findByRole('heading', { name: '정규 시간표 및 정기 휴일' })).toBeInTheDocument()
    expect(screen.getByText('기본 정기 휴일은 월요일입니다.')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '화요일 09:00~09:45' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '월요일 · 정기 휴무' })).toBeInTheDocument()
    expect(screen.getByLabelText('구보초보 정원')).toBeInTheDocument()
    expect(screen.getByLabelText('구보 정원')).toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('검색'), { target: { value: '없는 설정' } })
    expect(screen.getAllByText(/조건에 맞는 .* 없습니다/)).toHaveLength(2)
  })

  it('저장된_정규_시간표를_요일과_시간에_맞춰_클래스별_정원과_함께_표시한다', async () => {
    const mondayTemplate = {
      ...TEMPLATE,
      templateId: 2,
      dayOfWeek: 'MONDAY' as const,
      startTime: '10:00:00',
      endTime: '10:45:00',
      classCapacities: { ...CLASS_CAPACITIES, FIRST_RIDE: 4, DRESSAGE: 0 },
    }
    const thursdayTemplate = {
      ...TEMPLATE,
      templateId: 3,
      dayOfWeek: 'THURSDAY' as const,
      startTime: '14:00:00',
      endTime: '14:45:00',
      active: false,
      classCapacities: { ...CLASS_CAPACITIES, JUMPING: 2 },
    }
    const getTemplates = vi.fn().mockResolvedValue([thursdayTemplate, TEMPLATE, mondayTemplate])
    renderPage(createApi({ getTemplates }))

    const overview = await screen.findByRole('region', { name: '주간 정규 시간표 상세' })
    expect(getTemplates).toHaveBeenCalledTimes(1)
    expect(within(overview).getByRole('heading', { name: '월요일' })).toBeInTheDocument()
    expect(within(overview).getByRole('heading', { name: '일요일' })).toBeInTheDocument()

    const mondayCard = within(overview).getByLabelText('월요일 10:00 정규 시간표')
    expect(mondayCard).toHaveTextContent('전체 8명 · 원형 4명')
    expect(mondayCard.querySelector('[data-class-capacity="FIRST_RIDE"]')).toHaveTextContent('왕초보4명')
    expect(mondayCard.querySelector('[data-class-capacity="DRESSAGE"]')).toHaveTextContent('마장마술0명')

    expect(within(overview).queryByLabelText('목요일 14:00 정규 시간표')).not.toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '목요일 14:00~14:45' })).not.toBeInTheDocument()
    expect(within(overview).queryByLabelText(/수요일 .* 정규 시간표/)).not.toBeInTheDocument()
    expect(overview.querySelectorAll('[data-regular-template-id]')).toHaveLength(2)
    expect(overview.querySelectorAll('[data-weekly-cell]')).toHaveLength(2)
  })

  it('저장된_정규_시간표가_없으면_가짜_수업_없이_빈_상태를_표시한다', async () => {
    renderPage(createApi({ getTemplates: vi.fn().mockResolvedValue([]) }))

    expect(await screen.findByText('등록된 정규 시간표가 없습니다.')).toBeInTheDocument()
    expect(document.querySelectorAll('[data-regular-template-id]')).toHaveLength(0)
  })

  it('미래_점유_예약이_남은_inactive_시간표만_예약_정리_필요에_표시한다', async () => {
    const inactiveWithReservations = { ...TEMPLATE, templateId: 9, active: false, dayOfWeek: 'FRIDAY' as const, startTime: '04:30:00', endTime: '05:15:00' }
    const inactiveWithoutReservations = { ...TEMPLATE, templateId: 10, active: false, dayOfWeek: 'SATURDAY' as const }
    const getFutureOccupyingReservations = vi.fn((templateId: number) => Promise.resolve(templateId === 9 ? {
      templateId,
      reservationCount: 1,
      reservations: [{
        reservationId: 77,
        lessonDate: new Date('2026-10-02T00:00:00.000Z'),
        startTime: '04:30:00',
        endTime: '05:15:00',
        memberId: 7,
        memberName: '김정리',
        memberPhone: '010-1234-5678',
        ridingClass: 'ROUND_BEGINNER' as const,
        status: 'confirmed' as const,
      }],
    } : { templateId, reservationCount: 0, reservations: [] }))
    renderPage(createApi({
      getTemplates: vi.fn().mockResolvedValue([TEMPLATE, inactiveWithReservations, inactiveWithoutReservations]),
      getFutureOccupyingReservations,
    }))

    const cleanupSection = await screen.findByRole('region', { name: '예약 정리 필요' })
    expect(cleanupSection).toHaveTextContent('금요일 04:30 운영 종료')
    expect(cleanupSection).toHaveTextContent('김정리')
    expect(cleanupSection).toHaveTextContent('원형초보')
    expect(cleanupSection).toHaveTextContent('예약 확정')
    expect(within(cleanupSection).getByRole('link', { name: '예약 확인' })).toHaveAttribute('href', '/admin/reservations?reservationId=77')
    expect(cleanupSection).not.toHaveTextContent('토요일 09:00 운영 종료')
    expect(getFutureOccupyingReservations).toHaveBeenCalledTimes(2)
  })

  it('inactive_시간표의_미래_점유_예약이_0건이면_운영_종료_목록을_남기지_않는다', async () => {
    renderPage(createApi({
      getTemplates: vi.fn().mockResolvedValue([{ ...TEMPLATE, active: false }]),
      getFutureOccupyingReservations: vi.fn().mockResolvedValue({ templateId: 1, reservationCount: 0, reservations: [] }),
    }))

    expect(await screen.findByText('등록된 정규 시간표가 없습니다.')).toBeInTheDocument()
    await waitFor(() => expect(screen.queryByRole('region', { name: '예약 정리 필요' })).not.toBeInTheDocument())
  })

  it('시간표와_휴일을_요일_시간_식별자_순으로_안정적으로_표시한다', async () => {
    const wednesdayTemplate = {
      ...TEMPLATE,
      templateId: 3,
      dayOfWeek: 'WEDNESDAY' as const,
      startTime: '10:00:00',
      endTime: '10:45:00',
    }
    const tuesdayLaterTemplate = {
      ...TEMPLATE,
      templateId: 2,
      startTime: '10:00:00',
      endTime: '10:45:00',
    }
    const laterMondayHoliday = {
      ...HOLIDAY,
      holidayId: 4,
      effectiveFrom: new Date('2026-02-01T00:00:00.000Z'),
      reason: '후순위 휴일',
    }
    renderPage(createApi({
      getTemplates: vi.fn().mockResolvedValue([wednesdayTemplate, tuesdayLaterTemplate, TEMPLATE]),
      getHolidays: vi.fn().mockResolvedValue([laterMondayHoliday, HOLIDAY]),
    }))

    const firstTemplate = await screen.findByRole('heading', { name: '화요일 09:00~09:45' })
    const secondTemplate = screen.getByRole('heading', { name: '화요일 10:00~10:45' })
    const thirdTemplate = screen.getByRole('heading', { name: '수요일 10:00~10:45' })
    expect(firstTemplate.compareDocumentPosition(secondTemplate) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(secondTemplate.compareDocumentPosition(thirdTemplate) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    const firstHoliday = screen.getByRole('heading', { name: '월요일 · 정기 휴무' })
    const secondHoliday = screen.getByRole('heading', { name: '월요일 · 후순위 휴일' })
    expect(firstHoliday.compareDocumentPosition(secondHoliday) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
  })

  it('정규_시간표는_서버_영향_미리보기와_version_확인_후_한_번만_생성한다', async () => {
    const createTemplate = vi.fn().mockResolvedValue({})
    renderPage(createApi({ createTemplate }))
    const section = await screen.findByRole('region', { name: '정규 시간표' })
    fireEvent.change(within(section).getByLabelText('변경 사유'), { target: { value: '오전 수업 추가' } })
    fireEvent.click(within(section).getByRole('button', { name: '영향 미리보기' }))
    const dialog = await screen.findByRole('dialog', { name: '정규 시간표 생성 영향 확인' })
    expect(within(dialog).getByText('13일')).toBeInTheDocument()
    expect(within(dialog).getByText('2건')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '정규 시간표 생성 영향 확인' })).toHaveFocus()
    const confirm = within(dialog).getByRole('button', { name: '변경 확정' })
    fireEvent.click(confirm)
    fireEvent.click(confirm)
    await waitFor(() => expect(createTemplate).toHaveBeenCalledTimes(1))
    expect(createTemplate).toHaveBeenCalledWith(expect.objectContaining({
      dayOfWeek: 'TUESDAY',
      startTime: '09:00:00',
      endTime: '09:45:00',
      expectedConfigVersion: 7,
      reason: '오전 수업 추가',
    }))
  })

  it('45분과_정기_휴일_기간을_클라이언트에서_보조_검증한다', async () => {
    const api = createApi()
    renderPage(api)
    const templateSection = await screen.findByRole('region', { name: '정규 시간표' })
    fireEvent.change(within(templateSection).getByLabelText('종료 시간'), { target: { value: '10:00' } })
    fireEvent.change(within(templateSection).getByLabelText('변경 사유'), { target: { value: '잘못된 길이' } })
    fireEvent.click(within(templateSection).getByRole('button', { name: '영향 미리보기' }))
    const templateError = screen.getByRole('alert')
    expect(templateError).toHaveTextContent('정규 수업은 45분이며 자정을 넘을 수 없습니다.')
    expect(within(templateSection).getByRole('group', { name: '정규 시간표 생성' }).closest('form')).toHaveAttribute(
      'aria-describedby',
      templateError.id,
    )
    expect(api.previewTemplate).not.toHaveBeenCalled()
    const holidaySection = screen.getByRole('region', { name: '정기 휴일' })
    fireEvent.change(within(holidaySection).getByLabelText('적용 시작일'), { target: { value: '2026-08-10' } })
    fireEvent.change(within(holidaySection).getByLabelText('적용 종료일 (선택)'), { target: { value: '2026-08-01' } })
    fireEvent.change(within(holidaySection).getByLabelText('휴무 사유'), { target: { value: '정기 점검' } })
    fireEvent.change(within(holidaySection).getByLabelText('변경 사유'), { target: { value: '운영 변경' } })
    fireEvent.click(within(holidaySection).getByRole('button', { name: '영향 미리보기' }))
    expect(screen.getByRole('alert')).toHaveTextContent('적용 종료일은 시작일보다 빠를 수 없습니다.')
    expect(api.previewHoliday).not.toHaveBeenCalled()
  })

  it('미리보기_dialog를_Escape로_닫고_변경을_실행하지_않는다', async () => {
    const api = createApi()
    renderPage(api)
    const section = await screen.findByRole('region', { name: '정규 시간표' })
    fireEvent.change(within(section).getByLabelText('변경 사유'), { target: { value: '확인 취소' } })
    const trigger = within(section).getByRole('button', { name: '영향 미리보기' })
    trigger.focus()
    fireEvent.click(trigger)
    const dialog = await screen.findByRole('dialog')
    const cancel = within(dialog).getByRole('button', { name: '취소' })
    const confirm = within(dialog).getByRole('button', { name: '변경 확정' })
    confirm.focus()
    fireEvent.keyDown(window, { key: 'Tab' })
    expect(cancel).toHaveFocus()
    fireEvent.keyDown(window, { key: 'Tab', shiftKey: true })
    expect(confirm).toHaveFocus()
    fireEvent.keyDown(window, { key: 'Escape' })
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
    expect(api.createTemplate).not.toHaveBeenCalled()
  })

  it('초기_조회가_실패하면_운영_오류_상태를_표시한다', async () => {
    renderPage(createApi({ getTemplates: vi.fn().mockRejectedValue(new Error('조회 실패')) }))
    expect(await screen.findByText('일정 설정을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.')).toHaveAttribute(
      'role',
      'status',
    )
  })

  it('SYNCING_중에는_설정_변경을_막고_진행률과_재시도를_제공한다', async () => {
    const retrySynchronization = vi.fn().mockResolvedValue({})
    renderPage(createApi({
      getSynchronization: vi.fn().mockResolvedValue({
        ...ACTIVE_SYNC,
        status: 'SYNCING',
        pendingVersion: 8,
        appliedDateCount: 40,
        remainingDateCount: 53,
        progressPercent: 43,
      }),
      retrySynchronization,
    }))
    expect(await screen.findByText('시간표 갱신 중')).toBeInTheDocument()
    expect(screen.getByText('40/93일 · 43%')).toBeInTheDocument()
    expect(screen.getByLabelText('시작 시간')).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: '동기화 재시도' }))
    await waitFor(() => expect(retrySynchronization).toHaveBeenCalledWith(8))
  })

  it('동기화_시작과_최근_실패_정보를_서울_시간으로_표시한다', async () => {
    renderPage(createApi({
      getSynchronization: vi.fn().mockResolvedValue({
        ...ACTIVE_SYNC,
        status: 'SYNCING',
        pendingVersion: 8,
        appliedDateCount: 40,
        remainingDateCount: 53,
        progressPercent: 43,
        syncStartedAt: new Date('2026-07-28T00:30:00.000Z'),
        lastFailedAt: new Date('2026-07-27T23:00:00.000Z'),
        lastFailureCode: 'SCHEDULE_SYNC_FAILED',
        lastFailureSummary: '화요일 occurrence 적용 실패',
        longRunning: true,
      }),
    }))

    expect(await screen.findByText(/시작 26\. 7\. 28\. 오전 9:30/)).toBeInTheDocument()
    expect(screen.getByText('동기화가 예상보다 오래 걸리고 있습니다.')).toBeInTheDocument()
    expect(screen.getByText(/최근 실패 26\. 7\. 28\. 오전 8:00 · SCHEDULE_SYNC_FAILED · 화요일 occurrence 적용 실패/)).toBeInTheDocument()
  })

  it('미리보기_요청은_처리_중에_한_번만_전송한다', async () => {
    let resolvePreview: ((value: { affectedDateCount: number; existingTimeSlotCount: number; activeReservationCount: number }) => void) | undefined
    const previewTemplate = vi.fn().mockReturnValue(new Promise((resolve) => {
      resolvePreview = resolve
    }))
    renderPage(createApi({ previewTemplate }))
    const section = await screen.findByRole('region', { name: '정규 시간표' })
    fireEvent.change(within(section).getByLabelText('변경 사유'), { target: { value: '중복 방지' } })
    const previewButton = within(section).getByRole('button', { name: '영향 미리보기' })
    fireEvent.click(previewButton)
    fireEvent.click(previewButton)
    expect(previewTemplate).toHaveBeenCalledTimes(1)
    expect(previewButton).toBeDisabled()
    resolvePreview?.({ affectedDateCount: 1, existingTimeSlotCount: 0, activeReservationCount: 0 })
    expect(await screen.findByRole('dialog')).toBeInTheDocument()
  })

  it('정규_시간표는_대상과_남은_예약을_확인한_뒤_DELETE_계약으로_삭제한다', async () => {
    const deleteTemplate = vi.fn().mockResolvedValue({})
    const getFutureOccupyingReservations = vi.fn().mockResolvedValue({
      templateId: 1,
      reservationCount: 2,
      reservations: [],
    })
    const { client } = renderPage(createApi({ deleteTemplate, getFutureOccupyingReservations }))
    const section = await screen.findByRole('region', { name: '정규 시간표' })
    expect(screen.queryByLabelText('운영 변경 사유')).not.toBeInTheDocument()
    fireEvent.click(within(section).getByRole('button', { name: '삭제' }))
    const dialog = await screen.findByRole('dialog', { name: '정규 시간표 삭제 영향 확인' })
    expect(dialog).toHaveTextContent('화요일 09:00')
    expect(dialog).toHaveTextContent('2건')
    expect(dialog).toHaveTextContent('기존 예약은 자동 취소되지 않으므로')
    const reasonInput = within(dialog).getByLabelText('삭제 사유')
    expect(reasonInput).toHaveFocus()
    expect(reasonInput).toBeRequired()
    expect(reasonInput).toHaveAttribute('maxlength', '500')
    fireEvent.click(within(dialog).getByRole('button', { name: '삭제 확정' }))
    expect(within(dialog).getByRole('alert')).toHaveTextContent('삭제 사유를 입력해 주세요.')
    expect(reasonInput).toHaveAttribute('aria-invalid', 'true')
    expect(deleteTemplate).not.toHaveBeenCalled()
    fireEvent.change(reasonInput, { target: { value: ' 잘못 생성한 시간표 ' } })
    client.setQueryData([...CONFIGURATION_QUERY_KEY, 'sync'], { ...ACTIVE_SYNC, activeVersion: 8 })
    fireEvent.click(within(dialog).getByRole('button', { name: '삭제 확정' }))
    await waitFor(() => expect(deleteTemplate).toHaveBeenCalledWith(1, 7, '잘못 생성한 시간표'))
    await waitFor(() => expect(screen.getByRole('heading', { name: '정규 시간표' })).toHaveFocus())
  })

  it('정규_시간표_수정은_대상_폼으로_이동하고_첫_입력에_포커스한다', async () => {
    renderPage(createApi())
    const section = await screen.findByRole('region', { name: '정규 시간표' })
    const form = within(section).getByText('정규 시간표 생성').closest('form') as HTMLFormElement
    const scrollIntoView = vi.fn()
    Object.defineProperty(form, 'scrollIntoView', { configurable: true, value: scrollIntoView })

    fireEvent.click(within(section).getByRole('button', { name: '수정' }))

    expect(scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' })
    expect(within(form).getByText('화요일 09:00 정규 시간표 수정')).toBeInTheDocument()
    expect(within(form).getByLabelText('요일')).toHaveFocus()
  })

  it('정기_휴일_활성_상태_변경_사유도_확인_dialog에서_입력한다', async () => {
    const changeHolidayActivation = vi.fn().mockResolvedValue({})
    renderPage(createApi({ changeHolidayActivation }))
    const section = await screen.findByRole('region', { name: '정기 휴일' })

    fireEvent.click(within(section).getByRole('button', { name: '비활성화' }))
    const dialog = await screen.findByRole('dialog', { name: '정기 휴일 비활성화 영향 확인' })
    const reasonInput = within(dialog).getByLabelText('비활성화 사유')
    expect(reasonInput).toHaveFocus()
    fireEvent.click(within(dialog).getByRole('button', { name: '변경 확정' }))
    expect(within(dialog).getByRole('alert')).toHaveTextContent('활성 상태 변경 사유를 입력해 주세요.')
    expect(changeHolidayActivation).not.toHaveBeenCalled()

    fireEvent.change(reasonInput, { target: { value: ' 겨울 휴무 해제 ' } })
    fireEvent.click(within(dialog).getByRole('button', { name: '변경 확정' }))
    await waitFor(() => expect(changeHolidayActivation).toHaveBeenCalledWith(2, false, 7, '겨울 휴무 해제'))
  })

  it('미리보기_후_SYNCING으로_전환되면_확정을_막고_정확한_503_안내를_표시한다', async () => {
    const { client } = renderPage(createApi())
    const section = await screen.findByRole('region', { name: '정규 시간표' })
    fireEvent.change(within(section).getByLabelText('변경 사유'), { target: { value: '상태 전환' } })
    fireEvent.click(within(section).getByRole('button', { name: '영향 미리보기' }))
    const dialog = await screen.findByRole('dialog')
    client.setQueryData([...CONFIGURATION_QUERY_KEY, 'sync'], { ...ACTIVE_SYNC, status: 'SYNCING', pendingVersion: 8 })
    const alert = await within(dialog).findByRole('alert')
    expect(alert).toHaveTextContent('시간표를 갱신하고 있습니다. 잠시 후 다시 시도해 주세요.')
    expect(alert).toHaveAttribute('data-error-code', 'SCHEDULE_CONFIG_SYNC_IN_PROGRESS')
    expect(within(dialog).getByRole('button', { name: '변경 확정' })).toBeDisabled()
  })

  it('서버의_구조화된_SYNCING_503_메시지를_표시한다', async () => {
    const response = new Response(JSON.stringify({
      code: 'SCHEDULE_CONFIG_SYNC_IN_PROGRESS',
      message: '시간표를 갱신하고 있습니다. 잠시 후 다시 시도해 주세요.',
      status: 503,
      timestamp: '2026-07-28T08:00:00+09:00',
      path: '/api/admin/schedule-templates/preview',
      fieldErrors: [],
      details: {},
    }), { status: 503, headers: { 'Content-Type': 'application/json' } })
    renderPage(createApi({ previewTemplate: vi.fn().mockRejectedValue(new ResponseError(response, 'syncing')) }))
    const section = await screen.findByRole('region', { name: '정규 시간표' })
    fireEvent.change(within(section).getByLabelText('변경 사유'), { target: { value: '동기화 충돌' } })
    fireEvent.click(within(section).getByRole('button', { name: '영향 미리보기' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('시간표를 갱신하고 있습니다. 잠시 후 다시 시도해 주세요.')
    expect(screen.getByRole('alert')).toHaveAttribute('data-error-code', 'SCHEDULE_CONFIG_SYNC_IN_PROGRESS')
  })

  it('서버의_구조화된_409_충돌_메시지를_표시한다', async () => {
    const response = new Response(JSON.stringify({
      code: 'SCHEDULE_CONFIG_VERSION_CONFLICT',
      message: '시간표 설정이 변경되었습니다. 다시 조회한 뒤 시도해 주세요.',
      status: 409,
      timestamp: '2026-07-28T08:00:00+09:00',
      path: '/api/admin/schedule-templates/preview',
      fieldErrors: [],
      details: {},
    }), { status: 409, headers: { 'Content-Type': 'application/json' } })
    renderPage(createApi({ previewTemplate: vi.fn().mockRejectedValue(new ResponseError(response, 'conflict')) }))
    const section = await screen.findByRole('region', { name: '정규 시간표' })
    fireEvent.change(within(section).getByLabelText('변경 사유'), { target: { value: '버전 충돌' } })
    fireEvent.click(within(section).getByRole('button', { name: '영향 미리보기' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('시간표 설정이 변경되었습니다. 다시 조회한 뒤 시도해 주세요.')
    expect(screen.getByRole('alert')).toHaveAttribute('data-error-code', 'SCHEDULE_CONFIG_VERSION_CONFLICT')
  })

  it('정원_점유_충돌은_문제_수업과_현재_점유_요청_정원을_표시한다', async () => {
    const response = new Response(JSON.stringify({
      code: 'TIMESLOT_CAPACITY_BELOW_OCCUPANCY',
      message: '현재 예약보다 작은 정원으로 변경할 수 없습니다.',
      status: 409,
      timestamp: '2026-09-23T10:00:00+09:00',
      path: '/api/admin/schedule-templates',
      fieldErrors: [],
      details: {
        lessonDate: '2026-10-06',
        startTime: '09:00:00',
        totalOccupied: 5,
        roundArenaOccupied: 3,
        classOccupied: { ROUND_BEGINNER: 3 },
        requestedTotalCapacity: 4,
        requestedRoundArenaCapacity: 2,
        requestedClassCapacities: { ROUND_BEGINNER: 2 },
      },
    }), { status: 409, headers: { 'Content-Type': 'application/json' } })
    renderPage(createApi({ createTemplate: vi.fn().mockRejectedValue(new ResponseError(response, 'capacity conflict')) }))
    const section = await screen.findByRole('region', { name: '정규 시간표' })
    fireEvent.change(within(section).getByLabelText('변경 사유'), { target: { value: '정원 축소' } })
    fireEvent.click(within(section).getByRole('button', { name: '영향 미리보기' }))
    const dialog = await screen.findByRole('dialog')
    fireEvent.click(within(dialog).getByRole('button', { name: '변경 확정' }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveAttribute('data-error-code', 'TIMESLOT_CAPACITY_BELOW_OCCUPANCY')
    expect(alert).toHaveTextContent('2026년 10월 6일')
    expect(alert).toHaveTextContent('09:00 수업')
    expect(alert).toHaveTextContent('현재 5명 / 요청 4명')
    expect(alert).toHaveTextContent('현재 3명 / 요청 2명')
    expect(alert).toHaveTextContent('원형초보: 현재 3명 / 요청 2명')
  })
})
