import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import type { ReactNode } from 'react'
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
    changeTemplateActivation: vi.fn().mockResolvedValue({}),
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
  const Wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>
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
    fireEvent.change(screen.getByLabelText('검색'), { target: { value: '없는 설정' } })
    expect(screen.getAllByText(/조건에 맞는 .* 없습니다/)).toHaveLength(2)
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

  it('활성_상태_변경은_미리보기_시점의_config_version을_확정에_사용한다', async () => {
    const changeTemplateActivation = vi.fn().mockResolvedValue({})
    const { client } = renderPage(createApi({ changeTemplateActivation }))
    const section = await screen.findByRole('region', { name: '정규 시간표' })
    fireEvent.change(screen.getByLabelText('활성 상태 변경 사유'), { target: { value: '계절 운영 변경' } })
    fireEvent.click(within(section).getByRole('button', { name: '비활성화' }))
    const dialog = await screen.findByRole('dialog')
    client.setQueryData([...CONFIGURATION_QUERY_KEY, 'sync'], { ...ACTIVE_SYNC, activeVersion: 8 })
    fireEvent.click(within(dialog).getByRole('button', { name: '변경 확정' }))
    await waitFor(() => expect(changeTemplateActivation).toHaveBeenCalledWith(1, false, 7, '계절 운영 변경'))
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
})
