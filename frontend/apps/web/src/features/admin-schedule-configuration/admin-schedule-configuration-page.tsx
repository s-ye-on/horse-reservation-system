import { useEffect, useLayoutEffect, useMemo, useRef, useState, type CSSProperties, type FormEvent, type RefObject } from 'react'
import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import type {
  RecurringHolidayImpactResponse,
  RecurringHolidayRequest,
  RecurringHolidayResponse,
  ScheduleSynchronizationResponse,
  ScheduleTemplateImpactResponse,
  ScheduleTemplateFutureReservationResponse,
  ScheduleTemplateFutureReservationsResponse,
  ScheduleTemplateRequest,
  ScheduleTemplateResponse,
} from '@horse/api-client'
import {
  adminScheduleConfigurationApi,
  readScheduleApiError,
  ScheduleClientError,
  type AdminScheduleConfigurationApi,
  type ScheduleApiError,
} from './admin-schedule-configuration.api'
import './admin-schedule-configuration-page.css'

const CONFIGURATION_KEY = ['admin', 'schedule-configuration'] as const
const DAY_OPTIONS = [
  ['MONDAY', '월요일'], ['TUESDAY', '화요일'], ['WEDNESDAY', '수요일'],
  ['THURSDAY', '목요일'], ['FRIDAY', '금요일'], ['SATURDAY', '토요일'], ['SUNDAY', '일요일'],
] as const
const DAY_ORDER = new Map(DAY_OPTIONS.map(([day], index) => [day, index]))
const CLASS_FIELDS = [
  ['FIRST_RIDE', '왕초보'], ['ROUND_BEGINNER', '원형초보'], ['ROUND_TROT', '원형 속보'],
  ['LARGE_ARENA_BEGINNER', '대마장초보'], ['LARGE_ARENA_TROT', '대마장 속보'],
  ['CANTER_BEGINNER', '구보초보'], ['CANTER', '구보'],
  ['DRESSAGE', '마장마술'], ['JUMPING', '장애물'],
] as const

type Day = typeof DAY_OPTIONS[number][0]
type StatusFilter = 'ALL' | 'ACTIVE' | 'INACTIVE'

interface TemplateForm {
  templateId?: number
  dayOfWeek: Day
  startTime: string
  endTime: string
  totalCapacity: string
  roundArenaCapacity: string
  classCapacities: Record<string, string>
  reason: string
}

interface HolidayForm {
  holidayId?: number
  dayOfWeek: Day
  effectiveFrom: string
  effectiveTo: string
  holidayReason: string
  changeReason: string
}

type PendingChange =
  | { type: 'template-save'; label: string; request: ScheduleTemplateRequest; templateId?: number; impact: ScheduleTemplateImpactResponse }
  | { type: 'template-delete'; label: string; template: ScheduleTemplateResponse; reason: string; expectedConfigVersion: number; futureReservations: ScheduleTemplateFutureReservationsResponse }
  | { type: 'holiday-save'; label: string; request: RecurringHolidayRequest; holidayId?: number; impact: RecurringHolidayImpactResponse }
  | { type: 'holiday-activation'; label: string; holiday: RecurringHolidayResponse; nextActive: boolean; reason: string; expectedConfigVersion: number; impact: RecurringHolidayImpactResponse }

interface ReservationCleanupItem {
  template: ScheduleTemplateResponse
  futureReservations: ScheduleTemplateFutureReservationsResponse
}

const RESERVATION_STATUS_LABELS: Record<string, string> = {
  pending_admin_approval: '관리자 승인 대기',
  pending_payment: '입금 대기',
  confirmed: '예약 확정',
}

const emptyClasses = () => Object.fromEntries(CLASS_FIELDS.map(([key]) => [key, '0']))
const emptyTemplate = (): TemplateForm => ({
  dayOfWeek: 'TUESDAY',
  startTime: '09:00',
  endTime: '09:45',
  totalCapacity: '8',
  roundArenaCapacity: '4',
  classCapacities: emptyClasses(),
  reason: '',
})
const emptyHoliday = (): HolidayForm => ({
  dayOfWeek: 'MONDAY',
  effectiveFrom: '',
  effectiveTo: '',
  holidayReason: '',
  changeReason: '',
})

export function AdminScheduleConfigurationPage({ api = adminScheduleConfigurationApi }: { api?: AdminScheduleConfigurationApi }) {
  const queryClient = useQueryClient()
  const [templateForm, setTemplateForm] = useState<TemplateForm>(emptyTemplate)
  const [holidayForm, setHolidayForm] = useState<HolidayForm>(emptyHoliday)
  const [search, setSearch] = useState('')
  const [dayFilter, setDayFilter] = useState<Day | 'ALL'>('ALL')
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL')
  const [pending, setPending] = useState<PendingChange>()
  const [error, setError] = useState<ScheduleApiError>()
  const [success, setSuccess] = useState<string>()
  const [pendingReasonError, setPendingReasonError] = useState<string>()
  const [previewing, setPreviewing] = useState(false)
  const commandGuard = useRef(false)
  const previewGuard = useRef(false)
  const dialogHeadingRef = useRef<HTMLHeadingElement>(null)
  const dialogReasonRef = useRef<HTMLInputElement>(null)
  const dialogRef = useRef<HTMLElement>(null)
  const templateFormRef = useRef<HTMLFormElement>(null)
  const templateHeadingRef = useRef<HTMLHeadingElement>(null)
  const returnFocusRef = useRef<HTMLElement | null>(null)

  const templatesQuery = useQuery({ queryKey: [...CONFIGURATION_KEY, 'templates'], queryFn: api.getTemplates })
  const holidaysQuery = useQuery({ queryKey: [...CONFIGURATION_KEY, 'holidays'], queryFn: api.getHolidays })
  const syncQuery = useQuery({
    queryKey: [...CONFIGURATION_KEY, 'sync'],
    queryFn: api.getSynchronization,
    refetchInterval: (query) => query.state.data?.status === 'SYNCING' ? 2_000 : false,
  })
  const syncing = syncQuery.data?.status === 'SYNCING'
  const templates = useMemo(() => templatesQuery.data ?? [], [templatesQuery.data])
  const activeTemplates = useMemo(() => templates.filter((template) => template.active), [templates])
  const inactiveTemplates = useMemo(() => templates.filter((template) => !template.active), [templates])
  const cleanupQueries = useQueries({
    queries: inactiveTemplates.map((template) => ({
      queryKey: [...CONFIGURATION_KEY, 'future-occupying-reservations', template.templateId],
      queryFn: () => api.getFutureOccupyingReservations(template.templateId),
    })),
  })
  const cleanupItems = inactiveTemplates.flatMap((template, index): ReservationCleanupItem[] => {
    const futureReservations = cleanupQueries[index]?.data
    return futureReservations && futureReservations.reservationCount > 0
      ? [{ template, futureReservations }]
      : []
  })
  const cleanupLoading = cleanupQueries.some((query) => query.isPending)
  const cleanupError = cleanupQueries.some((query) => query.isError)

  useLayoutEffect(() => {
    if (pending) {
      if (pending.type === 'template-delete' || pending.type === 'holiday-activation') {
        dialogReasonRef.current?.focus()
      } else {
        dialogHeadingRef.current?.focus()
      }
      return
    }
    returnFocusRef.current?.focus()
    returnFocusRef.current = null
  }, [pending])

  useEffect(() => {
    if (!pending) return
    const close = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setPendingReasonError(undefined)
        setPending(undefined)
        return
      }
      if (event.key !== 'Tab') return
      const dialog = dialogRef.current
      if (!dialog) return
      const focusable = Array.from(dialog.querySelectorAll<HTMLElement>(
        'button:not(:disabled), [href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
      ))
      if (focusable.length === 0) {
        event.preventDefault()
        dialogHeadingRef.current?.focus()
        return
      }
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
    window.addEventListener('keydown', close)
    return () => window.removeEventListener('keydown', close)
  }, [pending])

  const openPending = (change: PendingChange) => {
    returnFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
    setPendingReasonError(undefined)
    setPending(change)
  }

  const closePending = () => {
    setPendingReasonError(undefined)
    setPending(undefined)
  }

  const refresh = async () => queryClient.invalidateQueries({ queryKey: CONFIGURATION_KEY })
  const mutation = useMutation({
    mutationFn: (change: PendingChange) => executeChange(change, api, syncQuery.data),
    onSuccess: async (_, change) => {
      if (change.type === 'template-delete') returnFocusRef.current = templateHeadingRef.current
      closePending()
      setError(undefined)
      setSuccess(`${change.label} 요청을 반영했습니다. 시간표 동기화 상태를 확인해 주세요.`)
      setTemplateForm(emptyTemplate())
      setHolidayForm(emptyHoliday())
      await refresh()
    },
    onError: async (reason) => {
      setError(await readScheduleApiError(reason))
      await refresh()
    },
    onSettled: () => {
      commandGuard.current = false
    },
  })
  const retryMutation = useMutation({
    mutationFn: (pendingVersion: number) => api.retrySynchronization(pendingVersion),
    onSuccess: async () => {
      setError(undefined)
      setSuccess('동일한 설정 버전의 동기화를 다시 실행했습니다.')
      await refresh()
    },
    onError: async (reason) => setError(await readScheduleApiError(reason)),
  })

  const filteredTemplates = useMemo(() => activeTemplates
    .filter((template) => {
      const keyword = search.trim().toLowerCase()
      const matchesKeyword = !keyword || `${dayLabel(template.dayOfWeek)} ${template.startTime} ${template.endTime}`.toLowerCase().includes(keyword)
      return matchesKeyword && matchesDay(template.dayOfWeek, dayFilter)
    })
    .sort((left, right) => compareDay(left.dayOfWeek, right.dayOfWeek)
      || left.startTime.localeCompare(right.startTime)
      || left.templateId - right.templateId), [activeTemplates, dayFilter, search])

  const filteredHolidays = useMemo(() => (holidaysQuery.data ?? [])
    .filter((holiday) => {
      const keyword = search.trim().toLowerCase()
      const matchesKeyword = !keyword || `${dayLabel(holiday.dayOfWeek)} ${holiday.reason}`.toLowerCase().includes(keyword)
      return matchesKeyword && matchesDay(holiday.dayOfWeek, dayFilter) && matchesStatus(holiday.active, statusFilter)
    })
    .sort((left, right) => compareDay(left.dayOfWeek, right.dayOfWeek)
      || left.effectiveFrom.getTime() - right.effectiveFrom.getTime()
      || left.holidayId - right.holidayId), [dayFilter, holidaysQuery.data, search, statusFilter])

  const submitTemplate = async (event: FormEvent) => {
    event.preventDefault()
    setSuccess(undefined)
    const parsed = parseTemplate(templateForm, syncQuery.data)
    if (typeof parsed === 'string') return setError({ message: parsed })
    if (previewGuard.current) return
    previewGuard.current = true
    setPreviewing(true)
    try {
      const impact = await api.previewTemplate({
        templateId: templateForm.templateId,
        dayOfWeek: parsed.dayOfWeek,
        startTime: parsed.startTime,
      })
      setError(undefined)
      openPending({
        type: 'template-save',
        label: templateForm.templateId ? '정규 시간표 변경' : '정규 시간표 생성',
        templateId: templateForm.templateId,
        request: parsed,
        impact,
      })
    } catch (reason) {
      setError(await readScheduleApiError(reason))
    } finally {
      previewGuard.current = false
      setPreviewing(false)
    }
  }

  const submitHoliday = async (event: FormEvent) => {
    event.preventDefault()
    setSuccess(undefined)
    const parsed = parseHoliday(holidayForm, syncQuery.data)
    if (typeof parsed === 'string') return setError({ message: parsed })
    if (previewGuard.current) return
    previewGuard.current = true
    setPreviewing(true)
    try {
      const impact = await api.previewHoliday({
        holidayId: holidayForm.holidayId,
        dayOfWeek: parsed.dayOfWeek,
        effectiveFrom: parsed.effectiveFrom,
        effectiveTo: parsed.effectiveTo,
      })
      setError(undefined)
      openPending({
        type: 'holiday-save',
        label: holidayForm.holidayId ? '정기 휴일 변경' : '정기 휴일 생성',
        holidayId: holidayForm.holidayId,
        request: parsed,
        impact,
      })
    } catch (reason) {
      setError(await readScheduleApiError(reason))
    } finally {
      previewGuard.current = false
      setPreviewing(false)
    }
  }

  const previewTemplateDelete = async (template: ScheduleTemplateResponse) => {
    const expectedConfigVersion = syncQuery.data?.activeVersion
    if (syncQuery.data?.status === 'SYNCING' || expectedConfigVersion === undefined) {
      return setError(syncInProgressError())
    }
    if (previewGuard.current) return
    previewGuard.current = true
    setPreviewing(true)
    try {
      const futureReservations = await api.getFutureOccupyingReservations(template.templateId)
      openPending({
        type: 'template-delete',
        label: '정규 시간표 삭제',
        template,
        reason: '',
        expectedConfigVersion,
        futureReservations,
      })
      setError(undefined)
    } catch (reason) {
      setError(await readScheduleApiError(reason))
    } finally {
      previewGuard.current = false
      setPreviewing(false)
    }
  }

  const previewHolidayActivation = async (holiday: RecurringHolidayResponse) => {
    const expectedConfigVersion = syncQuery.data?.activeVersion
    if (syncQuery.data?.status === 'SYNCING' || expectedConfigVersion === undefined) {
      return setError(syncInProgressError())
    }
    if (previewGuard.current) return
    previewGuard.current = true
    setPreviewing(true)
    try {
      const impact = await api.previewHoliday({
        holidayId: holiday.holidayId,
        dayOfWeek: holiday.dayOfWeek,
        effectiveFrom: holiday.effectiveFrom,
        effectiveTo: holiday.effectiveTo ?? undefined,
      })
      openPending({
        type: 'holiday-activation',
        label: `정기 휴일 ${holiday.active ? '비활성화' : '활성화'}`,
        holiday,
        nextActive: !holiday.active,
        reason: '',
        expectedConfigVersion,
        impact,
      })
      setError(undefined)
    } catch (reason) {
      setError(await readScheduleApiError(reason))
    } finally {
      previewGuard.current = false
      setPreviewing(false)
    }
  }

  const confirmPending = () => {
    if (!pending || commandGuard.current) return
    if ((pending.type === 'template-delete' || pending.type === 'holiday-activation') && !pending.reason.trim()) {
      setPendingReasonError(pending.type === 'template-delete' ? '삭제 사유를 입력해 주세요.' : '활성 상태 변경 사유를 입력해 주세요.')
      dialogReasonRef.current?.focus()
      return
    }
    if ((pending.type === 'template-delete' || pending.type === 'holiday-activation') && pending.reason.trim().length > 500) {
      setPendingReasonError('사유는 500자 이하로 입력해 주세요.')
      dialogReasonRef.current?.focus()
      return
    }
    commandGuard.current = true
    mutation.mutate(
      pending.type === 'template-delete' || pending.type === 'holiday-activation'
        ? { ...pending, reason: pending.reason.trim() }
        : pending,
    )
  }

  const updatePendingReason = (reason: string) => {
    setPending((current) => {
      if (!current || (current.type !== 'template-delete' && current.type !== 'holiday-activation')) return current
      return { ...current, reason }
    })
    if (reason.trim()) setPendingReasonError(undefined)
  }

  const editTemplate = (template: ScheduleTemplateResponse) => {
    setTemplateForm(templateToForm(template))
    templateFormRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'start' })
    templateFormRef.current?.querySelector<HTMLElement>('select, input')?.focus()
  }

  const loading = templatesQuery.isPending || holidaysQuery.isPending || syncQuery.isPending
  const queryError = templatesQuery.error ?? holidaysQuery.error ?? syncQuery.error
  if (loading) return <PageState message="시간표 운영 설정을 불러오는 중입니다." />
  if (queryError) return <QueryErrorState error={queryError} />
  const errorId = error ? 'schedule-config-error' : undefined

  return (
    <main className="schedule-config-page">
      <div className="schedule-config-shell">
        <header className="schedule-config-header">
          <div><p className="schedule-config-eyebrow">SCHEDULE CONFIGURATION</p><h1>정규 시간표 및 정기 휴일</h1></div>
          <SynchronizationStatus
            synchronization={syncQuery.data as ScheduleSynchronizationResponse}
            retrying={retryMutation.isPending}
            onRetry={(version) => retryMutation.mutate(version)}
          />
        </header>
        <section className="schedule-config-notice" aria-label="운영 기준">
          <strong>기본 정기 휴일은 월요일입니다.</strong>
          <span>월요일 정규 시간표는 날짜별 운영 예외 시 사용되므로 휴일 규칙과 별도로 유지됩니다.</span>
        </section>
        {error ? <div id="schedule-config-error" className="schedule-config-alert error" role="alert" data-error-code={error.code}>
          <p>{error.message}</p>
          {error.capacityConflict ? <CapacityConflictDetails details={error.capacityConflict} /> : null}
        </div> : null}
        {success ? <p className="schedule-config-alert success" role="status">{success}</p> : null}
        {(cleanupLoading || cleanupError || cleanupItems.length > 0) ? (
          <ReservationCleanupSection
            items={cleanupItems}
            loading={cleanupLoading}
            failed={cleanupError}
            onRetry={() => queryClient.invalidateQueries({ queryKey: [...CONFIGURATION_KEY, 'future-occupying-reservations'] })}
          />
        ) : null}
        <RegularScheduleWeeklyOverview templates={activeTemplates} />
        <section className="schedule-config-filters" aria-label="시간표 설정 검색">
          <TextInput label="검색" value={search} onChange={setSearch} placeholder="시간 또는 휴일 사유" />
          <label>요일<select value={dayFilter} onChange={(event) => setDayFilter(event.target.value as Day | 'ALL')}>
            <option value="ALL">전체 요일</option>
            {DAY_OPTIONS.map(([value, label]) => <option value={value} key={value}>{label}</option>)}
          </select></label>
          <label>정기 휴일 상태<select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as StatusFilter)}>
            <option value="ALL">전체 상태</option><option value="ACTIVE">활성</option><option value="INACTIVE">비활성</option>
          </select></label>
        </section>
        <div className="schedule-config-columns">
          <section className="schedule-config-section" aria-labelledby="template-heading">
            <div className="schedule-config-section-heading"><div><h2 id="template-heading" ref={templateHeadingRef} tabIndex={-1}>정규 시간표</h2><p>{filteredTemplates.length}개 표시</p></div></div>
            <TemplateFormView formRef={templateFormRef} value={templateForm} disabled={syncing || previewing || mutation.isPending} errorId={errorId} onChange={setTemplateForm} onSubmit={submitTemplate} onCancel={() => setTemplateForm(emptyTemplate())} />
            <div className="schedule-config-list">
              {filteredTemplates.length === 0 ? <PageState embedded message="조건에 맞는 정규 시간표가 없습니다." /> : filteredTemplates.map((template) => (
                <TemplateCard key={template.templateId} template={template} disabled={syncing || previewing || mutation.isPending} onEdit={() => editTemplate(template)} onDelete={() => previewTemplateDelete(template)} />
              ))}
            </div>
          </section>
          <section className="schedule-config-section" aria-labelledby="holiday-heading">
            <div className="schedule-config-section-heading"><div><h2 id="holiday-heading">정기 휴일</h2><p>{filteredHolidays.length}개 표시</p></div></div>
            <HolidayFormView value={holidayForm} disabled={syncing || previewing || mutation.isPending} errorId={errorId} onChange={setHolidayForm} onSubmit={submitHoliday} onCancel={() => setHolidayForm(emptyHoliday())} />
            <div className="schedule-config-list">
              {filteredHolidays.length === 0 ? <PageState embedded message="조건에 맞는 정기 휴일이 없습니다." /> : filteredHolidays.map((holiday) => (
                <HolidayCard key={holiday.holidayId} holiday={holiday} disabled={syncing || previewing || mutation.isPending} onEdit={() => setHolidayForm(holidayToForm(holiday))} onActivation={() => previewHolidayActivation(holiday)} />
              ))}
            </div>
          </section>
        </div>
      </div>
      {pending ? (
        <div className="schedule-config-dialog-backdrop">
          <section ref={dialogRef} className="schedule-config-dialog" role="dialog" aria-modal="true" aria-labelledby="change-preview-heading">
            <h2 id="change-preview-heading" ref={dialogHeadingRef} tabIndex={-1}>{pending.label} 영향 확인</h2>
            <ImpactSummary change={pending} />
            {pending.type === 'template-delete' ? (
              <p>삭제하면 신규 예약이 즉시 차단됩니다. 기존 예약은 자동 취소되지 않으므로 예약 운영 화면에서 별도로 처리해 주세요.</p>
            ) : <p>미리보기 이후 설정 version이 달라지면 서버가 변경을 거부합니다.</p>}
            {pending.type === 'template-delete' || pending.type === 'holiday-activation' ? (
              <label className="schedule-config-dialog-field" htmlFor="schedule-change-reason">
                {pending.type === 'template-delete' ? '삭제 사유' : `${pending.nextActive ? '활성화' : '비활성화'} 사유`}
                <input
                  id="schedule-change-reason"
                  ref={dialogReasonRef}
                  value={pending.reason}
                  required
                  maxLength={500}
                  placeholder={pending.type === 'template-delete' ? '예: 잘못 생성한 시간표' : '예: 계절 운영 변경'}
                  aria-invalid={pendingReasonError ? 'true' : undefined}
                  aria-describedby={pendingReasonError ? 'schedule-change-reason-error' : undefined}
                  onChange={(event) => updatePendingReason(event.target.value)}
                />
                {pendingReasonError ? <span id="schedule-change-reason-error" className="schedule-config-field-error" role="alert">{pendingReasonError}</span> : null}
              </label>
            ) : null}
            {syncing ? <p className="schedule-config-alert error" role="alert" data-error-code="SCHEDULE_CONFIG_SYNC_IN_PROGRESS">시간표를 갱신하고 있습니다. 잠시 후 다시 시도해 주세요.</p> : null}
            <div className="schedule-config-actions">
              <button type="button" className="secondary" disabled={mutation.isPending} onClick={closePending}>취소</button>
              <button type="button" className={pending.type === 'template-delete' ? 'danger' : undefined} disabled={syncing || mutation.isPending} onClick={confirmPending}>{mutation.isPending ? '반영 중' : pending.type === 'template-delete' ? '삭제 확정' : '변경 확정'}</button>
            </div>
          </section>
        </div>
      ) : null}
    </main>
  )
}

function RegularScheduleWeeklyOverview({ templates }: { templates: ScheduleTemplateResponse[] }) {
  const sortedTemplates = useMemo(() => templates.filter((template) => template.active).sort((left, right) =>
    compareDay(left.dayOfWeek, right.dayOfWeek)
      || left.startTime.localeCompare(right.startTime)
      || left.templateId - right.templateId), [templates])
  const startTimes = useMemo(() => (
    [...new Set(sortedTemplates.map((template) => template.startTime))].sort()
  ), [sortedTemplates])
  const templatesByCell = useMemo(() => {
    const grouped = new Map<string, ScheduleTemplateResponse[]>()
    for (const template of sortedTemplates) {
      const key = `${template.dayOfWeek}|${template.startTime}`
      grouped.set(key, [...(grouped.get(key) ?? []), template])
    }
    return grouped
  }, [sortedTemplates])

  return (
    <section className="schedule-config-weekly-overview" aria-labelledby="regular-weekly-heading">
      <div className="schedule-config-weekly-heading">
        <div>
          <h2 id="regular-weekly-heading">주간 정규 시간표</h2>
          <p>저장된 요일별 수업 시간과 클래스별 정원을 한눈에 확인합니다.</p>
        </div>
        <strong>{sortedTemplates.length}개 시간표</strong>
      </div>
      {sortedTemplates.length === 0 ? (
        <PageState embedded message="등록된 정규 시간표가 없습니다." />
      ) : (
        <div className="schedule-config-weekly-scroll" role="region" aria-label="주간 정규 시간표 상세" tabIndex={0}>
          <div className="schedule-config-weekly-grid">
            <h3 className="schedule-config-weekly-corner">시간</h3>
            {DAY_OPTIONS.map(([day, label], dayIndex) => (
              <h3
                className="schedule-config-weekly-day"
                data-weekly-day={day}
                key={day}
                style={{ gridColumn: dayIndex + 2, gridRow: 1 }}
              >
                {label}
              </h3>
            ))}
            {startTimes.map((startTime, timeIndex) => (
              <h3
                className="schedule-config-weekly-time"
                data-weekly-time={startTime}
                key={startTime}
                style={{ gridColumn: 1, gridRow: timeIndex + 2 }}
              >
                {shortTime(startTime)}
              </h3>
            ))}
            {DAY_OPTIONS.flatMap(([day, label], dayIndex) => startTimes.flatMap((startTime, timeIndex) => {
              const cellTemplates = templatesByCell.get(`${day}|${startTime}`) ?? []
              if (cellTemplates.length === 0) return []
              const style: CSSProperties = { gridColumn: dayIndex + 2, gridRow: timeIndex + 2 }
              return [
                <div
                  className="schedule-config-weekly-cell"
                  data-weekly-cell={day}
                  data-weekly-start-time={startTime}
                  key={`${day}|${startTime}`}
                  style={style}
                >
                  {cellTemplates.map((template) => (
                    <RegularScheduleWeeklyCard dayLabel={label} key={template.templateId} template={template} />
                  ))}
                </div>,
              ]
            }))}
          </div>
        </div>
      )}
    </section>
  )
}

function RegularScheduleWeeklyCard({ dayLabel: weekday, template }: {
  dayLabel: string
  template: ScheduleTemplateResponse
}) {
  return (
    <article
      aria-label={`${weekday} ${shortTime(template.startTime)} 정규 시간표`}
      className={`schedule-config-weekly-card${template.active ? '' : ' inactive'}`}
      data-regular-template-id={template.templateId}
    >
      <header>
        <div>
          <span className="schedule-config-weekly-mobile-day">{weekday}</span>
          <strong>{shortTime(template.startTime)} - {shortTime(template.endTime)}</strong>
        </div>
        <Status active={template.active} />
      </header>
      <p className="schedule-config-weekly-capacity-summary">
        전체 {template.totalCapacity}명 · 원형 {template.roundArenaCapacity}명
      </p>
      <dl className="schedule-config-weekly-class-capacities">
        {CLASS_FIELDS.map(([ridingClass, label]) => {
          const capacity = template.classCapacities[ridingClass]
          return (
            <div className={capacity === 0 ? 'zero' : ''} data-class-capacity={ridingClass} key={ridingClass}>
              <dt>{label}</dt>
              <dd>{capacity}명</dd>
            </div>
          )
        })}
      </dl>
    </article>
  )
}

function syncInProgressError(): ScheduleApiError {
  return {
    status: 503,
    code: 'SCHEDULE_CONFIG_SYNC_IN_PROGRESS',
    message: '시간표를 갱신하고 있습니다. 잠시 후 다시 시도해 주세요.',
  }
}

async function executeChange(change: PendingChange, api: AdminScheduleConfigurationApi, sync?: ScheduleSynchronizationResponse) {
  if (!sync || sync.status === 'SYNCING') throw new ScheduleClientError(
    503,
    'SCHEDULE_CONFIG_SYNC_IN_PROGRESS',
    '시간표를 갱신하고 있습니다. 잠시 후 다시 시도해 주세요.',
  )
  if (change.type === 'template-save') {
    return change.templateId ? api.updateTemplate(change.templateId, change.request) : api.createTemplate(change.request)
  }
  if (change.type === 'template-delete') {
    return api.deleteTemplate(change.template.templateId, change.expectedConfigVersion, change.reason)
  }
  if (change.type === 'holiday-save') {
    return change.holidayId ? api.updateHoliday(change.holidayId, change.request) : api.createHoliday(change.request)
  }
  return api.changeHolidayActivation(change.holiday.holidayId, change.nextActive, change.expectedConfigVersion, change.reason)
}

function TemplateFormView({ formRef, value, disabled, errorId, onChange, onSubmit, onCancel }: {
  formRef: RefObject<HTMLFormElement | null>
  value: TemplateForm
  disabled: boolean
  errorId?: string
  onChange(value: TemplateForm): void
  onSubmit(event: FormEvent): void
  onCancel(): void
}) {
  const changeClass = (key: string, next: string) => onChange({ ...value, classCapacities: { ...value.classCapacities, [key]: next } })
  return <form ref={formRef} className="schedule-config-form" aria-describedby={errorId} onSubmit={onSubmit}><fieldset disabled={disabled}>
    <legend>{value.templateId ? `${dayLabel(value.dayOfWeek)} ${shortTime(value.startTime)} 정규 시간표 수정` : '정규 시간표 생성'}</legend>
    <div className="schedule-config-form-grid">
      <SelectDay label="요일" value={value.dayOfWeek} onChange={(dayOfWeek) => onChange({ ...value, dayOfWeek })} />
      <TextInput label="시작 시간" type="time" value={value.startTime} onChange={(startTime) => onChange({ ...value, startTime, endTime: plus45Minutes(startTime) })} />
      <TextInput label="종료 시간" type="time" value={value.endTime} onChange={(endTime) => onChange({ ...value, endTime })} />
      <TextInput label="전체 정원" type="number" value={value.totalCapacity} onChange={(totalCapacity) => onChange({ ...value, totalCapacity })} />
      <TextInput label="원형 정원" type="number" value={value.roundArenaCapacity} onChange={(roundArenaCapacity) => onChange({ ...value, roundArenaCapacity })} />
      <TextInput label="변경 사유" value={value.reason} onChange={(reason) => onChange({ ...value, reason })} />
    </div>
    <details><summary>클래스별 정원</summary><div className="schedule-config-class-grid">
      {CLASS_FIELDS.map(([key, label]) => <TextInput key={key} label={`${label} 정원`} type="number" value={value.classCapacities[key]} onChange={(next) => changeClass(key, next)} />)}
    </div></details>
    <div className="schedule-config-actions">
      {value.templateId ? <button className="secondary" type="button" onClick={onCancel}>수정 취소</button> : null}
      <button type="submit">영향 미리보기</button>
    </div>
  </fieldset></form>
}

function HolidayFormView({ value, disabled, errorId, onChange, onSubmit, onCancel }: {
  value: HolidayForm
  disabled: boolean
  errorId?: string
  onChange(value: HolidayForm): void
  onSubmit(event: FormEvent): void
  onCancel(): void
}) {
  return <form className="schedule-config-form" aria-describedby={errorId} onSubmit={onSubmit}><fieldset disabled={disabled}>
    <legend>{value.holidayId ? '정기 휴일 수정' : '정기 휴일 생성'}</legend>
    <div className="schedule-config-form-grid">
      <SelectDay label="휴무 요일" value={value.dayOfWeek} onChange={(dayOfWeek) => onChange({ ...value, dayOfWeek })} />
      <TextInput label="적용 시작일" type="date" value={value.effectiveFrom} onChange={(effectiveFrom) => onChange({ ...value, effectiveFrom })} />
      <TextInput label="적용 종료일 (선택)" type="date" value={value.effectiveTo} onChange={(effectiveTo) => onChange({ ...value, effectiveTo })} />
      <TextInput label="휴무 사유" value={value.holidayReason} onChange={(holidayReason) => onChange({ ...value, holidayReason })} />
      <TextInput label="변경 사유" value={value.changeReason} onChange={(changeReason) => onChange({ ...value, changeReason })} />
    </div>
    <div className="schedule-config-actions">
      {value.holidayId ? <button className="secondary" type="button" onClick={onCancel}>수정 취소</button> : null}
      <button type="submit">영향 미리보기</button>
    </div>
  </fieldset></form>
}

function TemplateCard({ template, disabled, onEdit, onDelete }: {
  template: ScheduleTemplateResponse
  disabled: boolean
  onEdit(): void
  onDelete(): void
}) {
  return <article className="schedule-config-card">
    <div className="schedule-config-card-heading"><h3>{dayLabel(template.dayOfWeek)} {template.startTime.slice(0, 5)}~{template.endTime.slice(0, 5)}</h3><Status active={template.active} /></div>
    <p>전체 {template.totalCapacity}명 · 원형 {template.roundArenaCapacity}명</p>
    <div className="schedule-config-actions"><button className="secondary" type="button" disabled={disabled} onClick={onEdit}>수정</button><button className="danger" type="button" disabled={disabled} onClick={onDelete}>삭제</button></div>
  </article>
}

function HolidayCard({ holiday, disabled, onEdit, onActivation }: {
  holiday: RecurringHolidayResponse
  disabled: boolean
  onEdit(): void
  onActivation(): void
}) {
  return <article className="schedule-config-card">
    <div className="schedule-config-card-heading"><h3>{dayLabel(holiday.dayOfWeek)} · {holiday.reason}</h3><Status active={holiday.active} /></div>
    <p>{apiDateToInput(holiday.effectiveFrom)} ~ {holiday.effectiveTo ? apiDateToInput(holiday.effectiveTo) : '종료일 없음'}</p>
    <div className="schedule-config-actions"><button className="secondary" type="button" disabled={disabled} onClick={onEdit}>수정</button><button type="button" disabled={disabled} onClick={onActivation}>{holiday.active ? '비활성화' : '활성화'}</button></div>
  </article>
}

function SynchronizationStatus({ synchronization, retrying, onRetry }: {
  synchronization: ScheduleSynchronizationResponse
  retrying: boolean
  onRetry(version: number): void
}) {
  const syncing = synchronization.status === 'SYNCING'
  return <aside className={`schedule-config-sync ${syncing ? 'syncing' : ''}`} aria-live="polite">
    <div><strong>{syncing ? '시간표 갱신 중' : '시간표 최신 상태'}</strong><span>설정 v{synchronization.activeVersion}</span></div>
    {syncing ? <>
      <progress max={100} value={synchronization.progressPercent}>{synchronization.progressPercent}%</progress>
      <span>{synchronization.appliedDateCount}/{synchronization.totalDateCount}일 · {synchronization.progressPercent}%</span>
      {synchronization.syncStartedAt ? <span>시작 {formatSeoulDateTime(synchronization.syncStartedAt)}</span> : null}
      {synchronization.longRunning ? <strong>동기화가 예상보다 오래 걸리고 있습니다.</strong> : null}
      {synchronization.pendingVersion !== undefined ? <button type="button" disabled={retrying} onClick={() => onRetry(synchronization.pendingVersion as number)}>동기화 재시도</button> : null}
    </> : null}
    {!syncing && synchronization.lastCompletedAt ? <span>최근 완료 {formatSeoulDateTime(synchronization.lastCompletedAt)}</span> : null}
    {synchronization.lastFailedAt ? (
      <span>
        최근 실패 {formatSeoulDateTime(synchronization.lastFailedAt)}
        {synchronization.lastFailureCode ? ` · ${synchronization.lastFailureCode}` : ''}
        {synchronization.lastFailureSummary ? ` · ${synchronization.lastFailureSummary}` : ''}
      </span>
    ) : null}
  </aside>
}

function compareDay(left: string, right: string) {
  return (DAY_ORDER.get(left as Day) ?? Number.MAX_SAFE_INTEGER)
    - (DAY_ORDER.get(right as Day) ?? Number.MAX_SAFE_INTEGER)
}

function formatSeoulDateTime(value: Date) {
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'short',
    timeStyle: 'short',
    timeZone: 'Asia/Seoul',
  }).format(value)
}

function CapacityConflictDetails({ details }: { details: NonNullable<ScheduleApiError['capacityConflict']> }) {
  const classConflicts = Object.entries(details.classOccupied)
    .filter(([ridingClass, occupied]) => occupied > (details.requestedClassCapacities[ridingClass] ?? 0))
  return <div className="schedule-config-conflict-details">
    <strong>{formatLessonDate(details.lessonDate)} {shortTime(details.startTime)} 수업의 현재 예약을 먼저 확인해 주세요.</strong>
    <dl>
      <div><dt>전체 정원</dt><dd>현재 {details.totalOccupied}명 / 요청 {details.requestedTotalCapacity}명</dd></div>
      <div><dt>원형 정원</dt><dd>현재 {details.roundArenaOccupied}명 / 요청 {details.requestedRoundArenaCapacity}명</dd></div>
    </dl>
    {classConflicts.length > 0 ? <ul>
      {classConflicts.map(([ridingClass, occupied]) => <li key={ridingClass}>
        {ridingClassLabel(ridingClass)}: 현재 {occupied}명 / 요청 {details.requestedClassCapacities[ridingClass] ?? 0}명
      </li>)}
    </ul> : null}
  </div>
}

function ReservationCleanupSection({ items, loading, failed, onRetry }: {
  items: ReservationCleanupItem[]
  loading: boolean
  failed: boolean
  onRetry(): void
}) {
  const total = items.reduce((sum, item) => sum + item.futureReservations.reservationCount, 0)
  return <section className="schedule-config-cleanup" aria-labelledby="reservation-cleanup-heading">
    <div className="schedule-config-section-heading">
      <div>
        <h2 id="reservation-cleanup-heading">예약 정리 필요</h2>
        <p>운영을 종료한 시간표에 남아 있는 예약입니다. 회원에게 안내한 뒤 기존 예약 관리에서 처리해 주세요.</p>
      </div>
      {total > 0 ? <strong>{total}건</strong> : null}
    </div>
    {loading ? <p role="status">남은 예약을 확인하고 있습니다.</p> : null}
    {failed ? <div className="schedule-config-cleanup-error" role="alert">
      <p>일부 시간표의 남은 예약을 불러오지 못했습니다.</p>
      <button type="button" className="secondary" onClick={onRetry}>다시 시도</button>
    </div> : null}
    <div className="schedule-config-cleanup-list">
      {items.map(({ template, futureReservations }) => <article key={template.templateId} className="schedule-config-cleanup-item">
        <header>
          <h3>{dayLabel(template.dayOfWeek)} {shortTime(template.startTime)} 운영 종료</h3>
          <strong>{futureReservations.reservationCount}건</strong>
        </header>
        <ul>
          {futureReservations.reservations.map((reservation) => <CleanupReservationRow key={reservation.reservationId} reservation={reservation} />)}
        </ul>
      </article>)}
    </div>
  </section>
}

function CleanupReservationRow({ reservation }: { reservation: ScheduleTemplateFutureReservationResponse }) {
  return <li>
    <div>
      <strong>{reservation.memberName}</strong>
      <span>{formatLessonDate(reservation.lessonDate)} {shortTime(reservation.startTime)} · {ridingClassLabel(reservation.ridingClass)} · {RESERVATION_STATUS_LABELS[reservation.status] ?? reservation.status}</span>
      <span>{reservation.memberPhone}</span>
    </div>
    <Link to={`/admin/reservations?reservationId=${reservation.reservationId}`}>예약 확인</Link>
  </li>
}

function formatLessonDate(value: Date) {
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  }).format(value)
}

function ridingClassLabel(value: string) {
  return CLASS_FIELDS.find(([key]) => key === value)?.[1] ?? value
}

function ImpactSummary({ change }: { change: PendingChange }) {
  if (change.type === 'template-delete') {
    return <dl className="schedule-config-impact">
      <div><dt>삭제 대상</dt><dd>{dayLabel(change.template.dayOfWeek)} {shortTime(change.template.startTime)}</dd></div>
      <div><dt>정리할 예약</dt><dd>{change.futureReservations.reservationCount}건</dd></div>
      <div><dt>처리 방식</dt><dd>신규 예약 차단</dd></div>
    </dl>
  }
  if (change.type === 'template-save') {
    const impact = change.impact as ScheduleTemplateImpactResponse
    return <dl className="schedule-config-impact"><div><dt>영향 날짜</dt><dd>{impact.affectedDateCount}일</dd></div><div><dt>기존 시간대</dt><dd>{impact.existingTimeSlotCount}개</dd></div><div><dt>활성 예약</dt><dd>{impact.activeReservationCount}건</dd></div></dl>
  }
  const impact = (change.impact as RecurringHolidayImpactResponse).combined
  return <dl className="schedule-config-impact"><div><dt>영향 날짜</dt><dd>{impact.affectedDateCount}일</dd></div><div><dt>자동 시간대</dt><dd>{impact.templateTimeSlotCount}개</dd></div><div><dt>활성 예약</dt><dd>{impact.activeReservationCount}건</dd></div></dl>
}

function SelectDay({ label, value, onChange }: { label: string; value: Day; onChange(value: Day): void }) {
  return <label>{label}<select value={value} onChange={(event) => onChange(event.target.value as Day)}>{DAY_OPTIONS.map(([option, text]) => <option key={option} value={option}>{text}</option>)}</select></label>
}

function TextInput({ label, value, type = 'text', placeholder, onChange }: { label: string; value: string; type?: string; placeholder?: string; onChange(value: string): void }) {
  return <label>{label}<input type={type} value={value} placeholder={placeholder} onChange={(event) => onChange(event.target.value)} /></label>
}

function Status({ active }: { active: boolean }) {
  return <span className={`schedule-config-status ${active ? '' : 'inactive'}`}>{active ? '활성' : '비활성'}</span>
}

function PageState({ message, embedded = false }: { message: string; embedded?: boolean }) {
  const content = <section className="schedule-config-state" role="status">{message}</section>
  return embedded ? content : <main className="schedule-config-page"><div className="schedule-config-shell">{content}</div></main>
}

function QueryErrorState({ error }: { error: unknown }) {
  const [message, setMessage] = useState('시간표 운영 설정을 불러오지 못했습니다.')
  useEffect(() => {
    void readScheduleApiError(error).then((result) => setMessage(result.message))
  }, [error])
  return <PageState message={message} />
}

function parseTemplate(form: TemplateForm, sync?: ScheduleSynchronizationResponse): ScheduleTemplateRequest | string {
  if (!sync || sync.status === 'SYNCING') return '시간표를 갱신하고 있습니다. 완료 후 다시 시도해 주세요.'
  if (!form.reason.trim()) return '변경 사유를 입력해 주세요.'
  if (!is45Minutes(form.startTime, form.endTime)) return '정규 수업은 45분이며 자정을 넘을 수 없습니다.'
  const total = Number(form.totalCapacity)
  const round = Number(form.roundArenaCapacity)
  const classes = Object.fromEntries(CLASS_FIELDS.map(([key]) => [key, Number(form.classCapacities[key])]))
  if (!Number.isInteger(total) || total < 0) return '전체 정원은 0명 이상의 정수여야 합니다.'
  if (!Number.isInteger(round) || round < 0 || round > total) return '원형 정원은 0명 이상의 정수이며 전체 정원을 넘을 수 없습니다.'
  if (Object.values(classes).some((capacity) => !Number.isInteger(capacity) || capacity < 0 || capacity > total)) return '클래스별 정원은 0명 이상이며 전체 정원을 넘을 수 없습니다.'
  return {
    dayOfWeek: form.dayOfWeek,
    startTime: `${form.startTime}:00`,
    endTime: `${form.endTime}:00`,
    totalCapacity: total,
    roundArenaCapacity: round,
    classCapacities: classes,
    expectedConfigVersion: sync.activeVersion,
    reason: form.reason.trim(),
  }
}

function parseHoliday(form: HolidayForm, sync?: ScheduleSynchronizationResponse): RecurringHolidayRequest | string {
  if (!sync || sync.status === 'SYNCING') return '시간표를 갱신하고 있습니다. 완료 후 다시 시도해 주세요.'
  if (!form.effectiveFrom) return '적용 시작일을 입력해 주세요.'
  if (form.effectiveTo && form.effectiveTo < form.effectiveFrom) return '적용 종료일은 시작일보다 빠를 수 없습니다.'
  if (!form.holidayReason.trim() || !form.changeReason.trim()) return '휴무 사유와 변경 사유를 모두 입력해 주세요.'
  return {
    dayOfWeek: form.dayOfWeek,
    effectiveFrom: inputToApiDate(form.effectiveFrom),
    effectiveTo: form.effectiveTo ? inputToApiDate(form.effectiveTo) : undefined,
    holidayReason: form.holidayReason.trim(),
    expectedConfigVersion: sync.activeVersion,
    changeReason: form.changeReason.trim(),
  }
}

function templateToForm(template: ScheduleTemplateResponse): TemplateForm {
  return {
    templateId: template.templateId,
    dayOfWeek: template.dayOfWeek,
    startTime: template.startTime.slice(0, 5),
    endTime: template.endTime.slice(0, 5),
    totalCapacity: String(template.totalCapacity),
    roundArenaCapacity: String(template.roundArenaCapacity),
    classCapacities: Object.fromEntries(CLASS_FIELDS.map(([key]) => [key, String(template.classCapacities[key] ?? 0)])),
    reason: '',
  }
}

function holidayToForm(holiday: RecurringHolidayResponse): HolidayForm {
  return {
    holidayId: holiday.holidayId,
    dayOfWeek: holiday.dayOfWeek,
    effectiveFrom: apiDateToInput(holiday.effectiveFrom),
    effectiveTo: holiday.effectiveTo ? apiDateToInput(holiday.effectiveTo) : '',
    holidayReason: holiday.reason,
    changeReason: '',
  }
}

function plus45Minutes(start: string) {
  if (!start) return ''
  const [hour, minute] = start.split(':').map(Number)
  const total = hour * 60 + minute + 45
  if (total >= 24 * 60) return ''
  return `${String(Math.floor(total / 60)).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`
}

function is45Minutes(start: string, end: string) {
  return Boolean(start && end && plus45Minutes(start) === end)
}

function inputToApiDate(value: string) {
  return new Date(`${value}T00:00:00.000Z`)
}

function apiDateToInput(value: Date) {
  return value.toISOString().slice(0, 10)
}

function dayLabel(day: string) {
  return DAY_OPTIONS.find(([value]) => value === day)?.[1] ?? day
}

function shortTime(value: string) {
  return value.slice(0, 5)
}

function matchesDay(day: string, filter: Day | 'ALL') {
  return filter === 'ALL' || day === filter
}

function matchesStatus(active: boolean, filter: StatusFilter) {
  return filter === 'ALL' || active === (filter === 'ACTIVE')
}
