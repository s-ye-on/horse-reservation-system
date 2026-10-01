import {
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
} from 'react'
import { useMutation, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query'
import type {
  ReservationCancelResponse,
  ScheduleDateClosureImpactResponse,
  ScheduleDateResponse,
  ScheduleImpactReservationResponse,
  TimeSlotClosureImpactResponse,
  TimeSlotClosureResponse,
  TimeSlotResponse,
} from '@horse/api-client'
import {
  adminScheduleClosuresApi,
  readScheduleClosureApiError,
  type AdminScheduleClosuresApi,
  type ScheduleClosureApiError,
} from './admin-schedule-closures.api'
import './admin-schedule-closures-page.css'

const CLOSURES_KEY = ['admin', 'schedule-closures'] as const
const CLOSURE_TABS = ['date', 'slot'] as const

type ClosureTab = typeof CLOSURE_TABS[number]

type Operation =
  | { kind: 'date-start'; scheduleDate: Date; reason: string; expectedVersion: number }
  | { kind: 'date-cancel-reservation'; scheduleDate: Date; reservationId: number; memo: string }
  | { kind: 'date-complete'; scheduleDate: Date; reason: string; expectedVersion: number }
  | { kind: 'date-resume'; scheduleDate: Date; reason: string; expectedVersion: number }
  | { kind: 'slot-start'; timeSlotId: number; reason: string }
  | { kind: 'slot-cancel-reservation'; timeSlotId: number; reservationId: number; memo: string }
  | { kind: 'slot-complete'; timeSlotId: number; reason: string; expectedVersion: number }
  | { kind: 'slot-withdraw'; timeSlotId: number; reason: string; expectedVersion: number }
  | { kind: 'slot-reopen'; timeSlotId: number; reason: string; expectedVersion: number }

interface PendingConfirmation {
  title: string
  description: string
  warning?: string
  confirmLabel: string
  operation: Operation
}

interface ItemResult {
  message: string
  error: boolean
}

export function AdminScheduleClosuresPage({ api = adminScheduleClosuresApi }: { api?: AdminScheduleClosuresApi }) {
  const queryClient = useQueryClient()
  const [tab, setTab] = useState<ClosureTab>('date')
  const [scheduleDate, setScheduleDate] = useState(defaultFutureDate)
  const [selectedTimeSlotId, setSelectedTimeSlotId] = useState('')
  const [timeSlotDate, setTimeSlotDate] = useState('')
  const [dateReason, setDateReason] = useState('')
  const [slotReason, setSlotReason] = useState('')
  const [dateMemos, setDateMemos] = useState<Record<number, string>>({})
  const [slotMemos, setSlotMemos] = useState<Record<number, string>>({})
  const [itemResults, setItemResults] = useState<Record<string, ItemResult>>({})
  const [pending, setPending] = useState<PendingConfirmation>()
  const [error, setError] = useState<ScheduleClosureApiError>()
  const [success, setSuccess] = useState<string>()
  const [checking, setChecking] = useState(false)
  const resultHeadingRef = useRef<HTMLHeadingElement>(null)
  const commandGuard = useRef(false)
  const dialogRef = useRef<HTMLElement>(null)
  const activeConfirmationRef = useRef<PendingConfirmation | undefined>(undefined)
  const dialogHeadingRef = useRef<HTMLHeadingElement>(null)
  const returnFocusRef = useRef<HTMLElement | null>(null)
  const dateTabRef = useRef<HTMLButtonElement>(null)
  const slotTabRef = useRef<HTMLButtonElement>(null)
  const apiDate = useMemo(() => toApiDate(scheduleDate), [scheduleDate])
  const timeSlotId = selectedTimeSlotId ? Number(selectedTimeSlotId) : undefined

  const scheduleDateQuery = useQuery({
    queryKey: [...CLOSURES_KEY, 'date', scheduleDate],
    queryFn: () => api.getScheduleDate(apiDate),
    enabled: Boolean(scheduleDate),
  })
  const dateImpactQuery = useQuery({
    queryKey: [...CLOSURES_KEY, 'date-impact', scheduleDate],
    queryFn: () => api.getDateImpact(apiDate),
    enabled: Boolean(scheduleDate),
  })
  const timeSlotsQuery = useQuery({
    queryKey: [...CLOSURES_KEY, 'timeslots'],
    queryFn: api.getTimeSlots,
  })
  const timeSlotClosureQuery = useQuery({
    queryKey: [...CLOSURES_KEY, 'timeslot-closure', timeSlotId],
    queryFn: () => api.getTimeSlotClosure(timeSlotId as number),
    enabled: timeSlotId !== undefined,
  })

  const sortedTimeSlots = useMemo(() => [...(timeSlotsQuery.data ?? [])].sort((left, right) => {
    const dateOrder = (left.lessonDate?.getTime() ?? Number.MAX_SAFE_INTEGER)
      - (right.lessonDate?.getTime() ?? Number.MAX_SAFE_INTEGER)
    return dateOrder || String(left.startTime).localeCompare(String(right.startTime)) || (left.id ?? 0) - (right.id ?? 0)
  }), [timeSlotsQuery.data])
  const visibleTimeSlots = useMemo(() => sortedTimeSlots.filter((slot) =>
    !timeSlotDate || slot.lessonDate?.toISOString().slice(0, 10) === timeSlotDate,
  ), [sortedTimeSlots, timeSlotDate])
  const selectedTimeSlot = sortedTimeSlots.find((slot) => slot.id === timeSlotId)

  useEffect(() => {
    if (!selectedTimeSlotId && visibleTimeSlots[0]?.id !== undefined) {
      setSelectedTimeSlotId(String(visibleTimeSlots[0].id))
    }
  }, [selectedTimeSlotId, visibleTimeSlots])

  useLayoutEffect(() => {
    activeConfirmationRef.current = pending
    if (pending) {
      dialogHeadingRef.current?.focus()
      return
    }
    if (returnFocusRef.current?.isConnected) returnFocusRef.current.focus()
    else resultHeadingRef.current?.focus()
    returnFocusRef.current = null
  }, [pending])

  useEffect(() => {
    if (!pending) return
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setPending(undefined)
        return
      }
      if (event.key !== 'Tab' || !dialogRef.current) return
      const focusable = Array.from(dialogRef.current.querySelectorAll<HTMLElement>(
        'button:not(:disabled), input:not(:disabled), textarea:not(:disabled), [tabindex]:not([tabindex="-1"])',
      ))
      const first = focusable[0]
      const last = focusable[focusable.length - 1]
      if (!first || !last) {
        event.preventDefault()
        dialogHeadingRef.current?.focus()
        return
      }
      if (
        event.shiftKey
        && (document.activeElement === first || document.activeElement === dialogHeadingRef.current)
      ) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => {
      window.removeEventListener('keydown', handleKeyDown)
      document.body.style.overflow = previousOverflow
    }
  }, [pending])

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: CLOSURES_KEY })
  }

  const mutation = useMutation({
    mutationFn: (operation: Operation) => executeOperation(api, operation),
    onSuccess: async (result, operation) => {
      setError(undefined)
      setPending(undefined)
      if (isReservationCancellation(operation)) {
        const response = result as ReservationCancelResponse
        const key = itemKey(operation)
        setItemResults((current) => ({
          ...current,
          [key]: {
            error: false,
            message: response.changed === false
              ? '이미 다른 종료 작업으로 처리된 예약입니다.'
              : response.couponAction.toUpperCase() === 'RETURN' ? '취소 처리 완료 · 쿠폰이 반환되었습니다.' : '취소 처리 완료 · 별도 쿠폰 처리가 없습니다.',
          },
        }))
      } else {
        setSuccess(operationSuccessMessage(operation, result))
      }
      await refresh()
      resultHeadingRef.current?.focus()
    },
    onError: async (reason, operation) => {
      const nextError = await readScheduleClosureApiError(reason)
      setPending(undefined)
      if (isReservationCancellation(operation)) {
        setItemResults((current) => ({
          ...current,
          [itemKey(operation)]: { error: true, message: nextError.message },
        }))
      } else {
        setError(nextError)
      }
      await refresh()
    },
    onSettled: () => {
      commandGuard.current = false
    },
  })

  const openConfirmation = (confirmation: PendingConfirmation) => {
    returnFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null
    setError(undefined)
    setSuccess(undefined)
    setPending(confirmation)
  }

  const confirm = async () => {
    if (!pending || commandGuard.current) return
    commandGuard.current = true
    setChecking(true)
    const operation = pending.operation
    try {
      // Refresh concurrency metadata without silently replacing the confirmed version.
      if ('expectedVersion' in operation) {
        const latest = 'scheduleDate' in operation
          ? await queryClient.fetchQuery({ queryKey: [...CLOSURES_KEY, 'date-impact', scheduleDate], queryFn: () => api.getDateImpact(operation.scheduleDate), staleTime: 0 })
          : await queryClient.fetchQuery({ queryKey: [...CLOSURES_KEY, 'timeslot-closure', operation.timeSlotId], queryFn: () => api.getTimeSlotClosure(operation.timeSlotId), staleTime: 0 })
        if (!latest || latest.version !== operation.expectedVersion) {
          setPending(undefined)
          setError({ status: 409, message: '다른 변경이 먼저 반영되었습니다. 최신 상태를 확인해 주세요.' })
          await refresh()
          commandGuard.current = false
          return
        }
      }
      if (activeConfirmationRef.current !== pending) {
        commandGuard.current = false
        return
      }
      mutation.mutate(operation)
    } catch (reason) {
      setError(await readScheduleClosureApiError(reason))
      setPending(undefined)
      commandGuard.current = false
    } finally {
      setChecking(false)
    }
  }

  const latestVersion = pending && 'expectedVersion' in pending.operation
    ? ('scheduleDate' in pending.operation ? dateImpactQuery.data?.version : timeSlotClosureQuery.data?.version)
    : undefined
  const staleConfirmation = pending && 'expectedVersion' in pending.operation
    && latestVersion !== undefined && latestVersion !== pending.operation.expectedVersion

  const retryLatest = async () => {
    setError(undefined)
    await refresh()
  }

  const selectTab = (nextTab: ClosureTab, moveFocus = false) => {
    setTab(nextTab)
    if (moveFocus) {
      const nextTabRef = nextTab === 'date' ? dateTabRef : slotTabRef
      nextTabRef.current?.focus()
    }
  }

  const handleTabKeyDown = (
    event: ReactKeyboardEvent<HTMLButtonElement>,
    currentTab: ClosureTab,
  ) => {
    const currentIndex = CLOSURE_TABS.indexOf(currentTab)
    let nextTab: ClosureTab | undefined

    if (event.key === 'ArrowRight') {
      nextTab = CLOSURE_TABS[(currentIndex + 1) % CLOSURE_TABS.length]
    } else if (event.key === 'ArrowLeft') {
      nextTab = CLOSURE_TABS[
        (currentIndex - 1 + CLOSURE_TABS.length) % CLOSURE_TABS.length
      ]
    } else if (event.key === 'Home') {
      nextTab = CLOSURE_TABS[0]
    } else if (event.key === 'End') {
      nextTab = CLOSURE_TABS[CLOSURE_TABS.length - 1]
    }

    if (nextTab) {
      event.preventDefault()
      selectTab(nextTab, true)
    }
  }

  return (
    <main className="schedule-closures-page">
      <div className="schedule-closures-shell" inert={Boolean(pending)}>
        <header className="schedule-closures-header">
          <div>
            <p className="schedule-closures-eyebrow">운영 예외 관리</p>
            <h1 ref={resultHeadingRef} tabIndex={-1}>날짜 휴무 및 개별 휴강</h1>
            <p className="schedule-closures-hint">이미 생성된 미래 운영 일정을 예외적으로 중단하고, 영향받은 예약을 한 건씩 확인·처리합니다.</p>
          </div>
          <button type="button" className="secondary" disabled={mutation.isPending} onClick={retryLatest}>최신 상태 새로고침</button>
        </header>

        <section className="schedule-closures-policy" aria-label="운영 원칙">
          <strong>예약은 자동 취소되지 않습니다.</strong>
          <span>신규 예약을 먼저 차단한 뒤 영향 예약을 한 건씩 확인·처리합니다.</span>
        </section>

        {error ? (
          <div className="schedule-closures-alert error" role="alert" data-error-code={error.code}>
            <span>{error.message}</span>
            {(error.status === 409 || error.status === 503) ? <button type="button" onClick={retryLatest}>다시 조회</button> : null}
          </div>
        ) : null}
        {success ? <p className="schedule-closures-alert success" role="status">{success}</p> : null}

        <div className="schedule-closures-tabs" role="tablist" aria-label="휴무 운영 유형">
          <button
            ref={dateTabRef}
            id="date-closure-tab"
            type="button"
            role="tab"
            aria-controls="date-closure-panel"
            aria-selected={tab === 'date'}
            tabIndex={tab === 'date' ? 0 : -1}
            onClick={() => selectTab('date')}
            onKeyDown={(event) => handleTabKeyDown(event, 'date')}
          >
            날짜 전체 휴무
          </button>
          <button
            ref={slotTabRef}
            id="time-slot-closure-tab"
            type="button"
            role="tab"
            aria-controls="time-slot-closure-panel"
            aria-selected={tab === 'slot'}
            tabIndex={tab === 'slot' ? 0 : -1}
            onClick={() => selectTab('slot')}
            onKeyDown={(event) => handleTabKeyDown(event, 'slot')}
          >
            개별 수업 휴강
          </button>
        </div>

        {tab === 'date' ? (
          <div
            id="date-closure-panel"
            role="tabpanel"
            aria-labelledby="date-closure-tab"
          >
            <DateClosurePanel
              scheduleDate={scheduleDate}
              onScheduleDateChange={setScheduleDate}
              reason={dateReason}
              onReasonChange={setDateReason}
              scheduleDateQuery={scheduleDateQuery}
              impactQuery={dateImpactQuery}
              memos={dateMemos}
              onMemoChange={(reservationId, memo) => setDateMemos((current) => ({ ...current, [reservationId]: memo }))}
              itemResults={itemResults}
              busy={mutation.isPending || checking}
              onConfirm={openConfirmation}
            />
          </div>
        ) : (
          <div
            id="time-slot-closure-panel"
            role="tabpanel"
            aria-labelledby="time-slot-closure-tab"
          >
            <TimeSlotClosurePanel
              timeSlots={visibleTimeSlots}
              selectedDate={timeSlotDate}
              onSelectedDateChange={(value) => {
                setTimeSlotDate(value)
                const candidate = sortedTimeSlots.find((slot) => !value || slot.lessonDate?.toISOString().slice(0, 10) === value)
                setSelectedTimeSlotId(candidate?.id === undefined ? '' : String(candidate.id))
              }}
              selectedId={selectedTimeSlotId}
              onSelectedIdChange={setSelectedTimeSlotId}
              selectedTimeSlot={selectedTimeSlot}
              closure={timeSlotClosureQuery.data}
              closurePending={timeSlotClosureQuery.isPending && timeSlotId !== undefined}
              closureError={timeSlotClosureQuery.isError}
              reason={slotReason}
              onReasonChange={setSlotReason}
              memos={slotMemos}
              onMemoChange={(reservationId, memo) => setSlotMemos((current) => ({ ...current, [reservationId]: memo }))}
              itemResults={itemResults}
              busy={mutation.isPending || checking || timeSlotsQuery.isPending}
              timeSlotsError={timeSlotsQuery.isError}
              onConfirm={openConfirmation}
            />
          </div>
        )}
      </div>

      {pending ? (
        <div className="schedule-closures-dialog-backdrop">
          <section
            ref={dialogRef}
            className="schedule-closures-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="closure-confirmation-heading"
            aria-describedby="closure-confirmation-description"
          >
            <h2 id="closure-confirmation-heading" ref={dialogHeadingRef} tabIndex={-1}>{pending.title}</h2>
            <p id="closure-confirmation-description">{pending.description}</p>
            <dl className="schedule-closures-dialog-facts">
              <div><dt>작업 사유 / 메모</dt><dd>{'reason' in pending.operation ? pending.operation.reason : pending.operation.memo}</dd></div>
            </dl>
            {pending.warning ? <p className="schedule-closures-dialog-warning">{pending.warning}</p> : null}
            {staleConfirmation ? <p role="alert">다른 변경이 먼저 반영되었습니다. 확인 창을 닫고 최신 상태를 확인해 주세요.</p> : null}
            <div className="schedule-closures-actions">
              <button type="button" className="secondary" disabled={mutation.isPending || checking} onClick={() => setPending(undefined)}>취소</button>
              <button type="button" disabled={mutation.isPending || checking || Boolean(staleConfirmation)} onClick={confirm}>
                {checking ? '최신 상태 확인 중' : mutation.isPending ? '처리 중' : pending.confirmLabel}
              </button>
            </div>
          </section>
        </div>
      ) : null}
    </main>
  )
}

function DateClosurePanel({
  scheduleDate, onScheduleDateChange, reason, onReasonChange, scheduleDateQuery, impactQuery,
  memos, onMemoChange, itemResults, busy, onConfirm,
}: {
  scheduleDate: string
  onScheduleDateChange(value: string): void
  reason: string
  onReasonChange(value: string): void
  scheduleDateQuery: UseQueryResult<ScheduleDateResponse>
  impactQuery: UseQueryResult<ScheduleDateClosureImpactResponse>
  memos: Record<number, string>
  onMemoChange(reservationId: number, memo: string): void
  itemResults: Record<string, ItemResult>
  busy: boolean
  onConfirm(confirmation: PendingConfirmation): void
}) {
  const [reasonTouched, setReasonTouched] = useState(false)
  const dateState = scheduleDateQuery.data
  const impact = impactQuery.data
  const closing = dateState?.status === 'CLOSING'
  const closed = dateState?.status === 'CLOSED'
  const reasonError = reasonTouched && !reason.trim() ? '작업 사유를 입력해 주세요.' : undefined
  const sortedReservations = [...(impact?.reservations ?? [])].sort((left, right) => left.reservationId - right.reservationId)

  return (
    <section className="schedule-closures-panel" aria-labelledby="date-closure-heading">
      <div className="schedule-closures-controls">
        <h2 id="date-closure-heading">미래 날짜 전체 휴무</h2>
        <p>날짜를 선택하면 현재 운영 상태와 점유 예약을 먼저 확인합니다.</p>
        <label htmlFor="closure-date">대상 날짜</label>
        <input id="closure-date" type="date" value={scheduleDate} disabled={busy}
          aria-describedby="closure-date-help" onChange={(event) => onScheduleDateChange(event.target.value)} />
        <small id="closure-date-help">새 휴무는 오늘 이후의 날짜에만 시작할 수 있습니다.</small>
        <label htmlFor="date-reason">운영 사유</label>
        <textarea id="date-reason" value={reason} maxLength={500} disabled={busy} aria-required="true"
          aria-invalid={Boolean(reasonError)} aria-describedby={reasonError ? 'date-reason-help date-reason-error' : 'date-reason-help'}
          onBlur={() => setReasonTouched(true)} onChange={(event) => onReasonChange(event.target.value)} />
        <small id="date-reason-help">작업 사유를 1~500자로 입력해 주세요.</small>
        {reasonError ? <p id="date-reason-error" className="schedule-closures-field-error" role="alert">{reasonError}</p> : null}
        <button type="button" className="secondary" disabled={busy || !scheduleDate || impactQuery.isFetching}
          onClick={() => { void scheduleDateQuery.refetch(); void impactQuery.refetch() }}>사전 영향 조회</button>
        <ul className="schedule-closures-effects">
          <li>휴무 시작 즉시 해당 날짜의 신규 예약을 차단합니다.</li>
          <li>기존 예약은 자동으로 취소·이동되지 않습니다.</li>
          <li>영향 예약은 한 건씩 확인·처리합니다.</li>
        </ul>
      </div>
      <div className="schedule-closures-operation">
        {scheduleDateQuery.isPending || impactQuery.isPending ? <PanelState embedded message="날짜 운영 상태를 불러오는 중입니다." />
          : scheduleDateQuery.isError || impactQuery.isError ? <PanelState embedded error message="날짜 운영 상태를 불러오지 못했습니다. 최신 상태를 다시 조회해 주세요." />
          : dateState && impact ? <>
            <section className="schedule-closures-operation-summary">
              <div className="schedule-closures-section-heading">
                <div><p className="schedule-closures-hint">선택한 날짜 · {formatDate(dateState.scheduleDate)}</p>
                  <h2>{closing ? '휴무 처리 중' : closed ? '휴무 완료' : '사전 영향 확인'}</h2></div>
                <StatusBadge status={dateState.status} />
              </div>
              <dl className="schedule-closures-summary">
                <div><dt>최초 영향 예약</dt><dd>{impact.initialReservationCount}건</dd></div>
                <div><dt>현재 점유 예약</dt><dd>{impact.activeReservationCount}건</dd></div>
                <div><dt>처리 완료</dt><dd>{impact.resolvedReservationCount}건</dd></div>
                <div><dt>남은 예약</dt><dd>{impact.remainingReservationCount}건</dd></div>
              </dl>
              <ProgressText value={impact.progressPercent} resolved={impact.resolvedReservationCount} total={impact.initialReservationCount} />
              <p className="schedule-closures-state">{closing || closed
                ? '해당 날짜의 신규 예약과 예약 시간 변경이 차단되어 있습니다.'
                : '휴무 시작 전입니다. 확정 전까지 신규 예약은 계속 가능합니다.'}</p>
              <div className="schedule-closures-actions">
                {!closing && !closed ? <button type="button"
                  disabled={busy || !reason.trim() || scheduleDate < defaultFutureDate() || impactQuery.isFetching || scheduleDateQuery.isFetching}
                  onClick={() => onConfirm({
                    title: '날짜 휴무 처리 시작',
                    description: `${formatDate(dateState.scheduleDate)}의 현재 점유 예약 ${impact.activeReservationCount}건을 확인했습니다.`,
                    warning: impact.activeReservationCount === 0 ? '예약이 없으면 휴무가 바로 완료될 수 있습니다.' : '기존 예약은 자동 취소되지 않습니다. 휴무 시작 후 한 건씩 처리해 주세요.',
                    confirmLabel: '날짜 휴무 시작',
                    operation: { kind: 'date-start', scheduleDate: toApiDate(scheduleDate), reason: reason.trim(), expectedVersion: impact.version },
                  })}>날짜 휴무 시작</button> : null}
                {closing ? <>
                  <button type="button" disabled={busy || impact.remainingReservationCount > 0 || !reason.trim() || impactQuery.isFetching}
                    onClick={() => onConfirm({
                      title: '날짜 휴무 완료', description: `${formatDate(dateState.scheduleDate)}의 남은 예약이 0건입니다.`,
                      warning: '완료 후에는 이 화면에서 날짜 휴무를 다시 열 수 없습니다.', confirmLabel: '휴무 완료',
                      operation: { kind: 'date-complete', scheduleDate: toApiDate(scheduleDate), reason: reason.trim(), expectedVersion: impact.version },
                    })}>휴무 완료</button>
                  <button type="button" className="danger" disabled={busy || !reason.trim() || impactQuery.isFetching}
                    onClick={() => onConfirm({
                      title: '휴무 진행 취소', description: `${formatDate(dateState.scheduleDate)}를 휴무 시작 전 운영 상태로 되돌립니다.`,
                      warning: '이미 취소되거나 이동된 예약은 자동으로 복구되지 않습니다. 진행 취소 후 현재 점유 예약을 다시 조회합니다.',
                      confirmLabel: '진행 취소 확정',
                      operation: { kind: 'date-resume', scheduleDate: toApiDate(scheduleDate), reason: reason.trim(), expectedVersion: impact.version },
                    })}>휴무 진행 취소</button>
                </> : null}
              </div>
              {closed ? <p className="schedule-closures-hint">신규 예약 차단이 유지됩니다. 이 화면에서는 날짜 휴무를 다시 열 수 없습니다.</p> : null}
            </section>
            <ImpactList title="현재 점유 예약" items={sortedReservations.map(dateImpactView)}
              memos={memos} itemResults={itemResults} keyPrefix="date" busy={busy}
              cancellationEnabled={closing && !impactQuery.isFetching} onMemoChange={onMemoChange}
              onCancel={(item, memo) => onConfirm({
                title: `예약 번호 ${item.reservationId} 취소 확인`,
                description: `${item.memberName} · ${classLabel(item.classType)} · ${formatDate(dateState.scheduleDate)}`,
                warning: cancellationNotice(item), confirmLabel: '예약 한 건 취소',
                operation: { kind: 'date-cancel-reservation', scheduleDate: toApiDate(scheduleDate), reservationId: item.reservationId, memo },
              })} />
          </> : null}
      </div>
    </section>
  )
}

function TimeSlotClosurePanel({
  timeSlots, selectedDate, onSelectedDateChange, selectedId, onSelectedIdChange, selectedTimeSlot, closure, closurePending, closureError,
  reason, onReasonChange, memos, onMemoChange, itemResults, busy, timeSlotsError, onConfirm,
}: {
  timeSlots: TimeSlotResponse[]
  selectedDate: string
  onSelectedDateChange(value: string): void
  selectedId: string
  onSelectedIdChange(value: string): void
  selectedTimeSlot?: TimeSlotResponse
  closure: TimeSlotClosureResponse | null | undefined
  closurePending: boolean
  closureError: boolean
  reason: string
  onReasonChange(value: string): void
  memos: Record<number, string>
  onMemoChange(reservationId: number, memo: string): void
  itemResults: Record<string, ItemResult>
  busy: boolean
  timeSlotsError: boolean
  onConfirm(confirmation: PendingConfirmation): void
}) {
  const [reasonTouched, setReasonTouched] = useState(false)
  const impacts = [...(closure?.impacts ?? [])].sort((left, right) => left.reservationId - right.reservationId)
  const inProgress = closure?.status === 'IN_PROGRESS'
  const completed = closure?.status === 'COMPLETED'
  const withdrawn = closure?.status === 'WITHDRAWN'
  const reopened = completed && !closure.adminClosed
  const canStart = !closure || withdrawn || reopened
  const currentClosed = closure?.closed ?? selectedTimeSlot?.closed
  const notStarted = selectedTimeSlot ? isFutureTimeSlot(selectedTimeSlot) : false
  const reasonError = reasonTouched && !reason.trim() ? '작업 사유를 입력해 주세요.' : undefined
  const controlsBusy = busy || closurePending || closureError

  return (
    <section className="schedule-closures-panel" aria-labelledby="slot-closure-heading">
      <div className="schedule-closures-controls">
        <h2 id="slot-closure-heading">미래 수업 시간 하나 휴강</h2>
        <p>아직 시작하지 않은 수업 시간을 선택합니다.</p>
        <label htmlFor="closure-slot-date">대상 날짜</label>
        <input id="closure-slot-date" type="date" value={selectedDate} disabled={busy || timeSlotsError}
          aria-describedby="closure-slot-date-help" onChange={(event) => onSelectedDateChange(event.target.value)} />
        <small id="closure-slot-date-help">날짜를 선택하면 해당 날짜의 수업 시간만 표시합니다. 비워 두면 전체 목록을 확인할 수 있습니다.</small>
        <label htmlFor="closure-time-slot">대상 수업 시간</label>
        <select id="closure-time-slot" value={selectedId} disabled={busy || timeSlotsError}
          onChange={(event) => onSelectedIdChange(event.target.value)}>
          {timeSlots.length === 0 ? <option value="">선택할 수업 시간 없음</option> : null}
          {timeSlots.map((slot) => <option key={slot.id} value={slot.id}>{formatTimeSlot(slot)}</option>)}
        </select>
        <label htmlFor="slot-reason">운영 사유</label>
        <textarea id="slot-reason" value={reason} maxLength={500} disabled={busy} aria-required="true"
          aria-invalid={Boolean(reasonError)} aria-describedby={reasonError ? 'slot-reason-help slot-reason-error' : 'slot-reason-help'}
          onBlur={() => setReasonTouched(true)} onChange={(event) => onReasonChange(event.target.value)} />
        <small id="slot-reason-help">작업 사유를 1~500자로 입력해 주세요.</small>
        {reasonError ? <p id="slot-reason-error" className="schedule-closures-field-error" role="alert">{reasonError}</p> : null}
        <p className="schedule-closures-state">휴강 시작 전에는 정확한 영향 예약 수를 표시하지 않습니다. 시작하는 순간 예약 목록이 확정됩니다.</p>
      </div>
      <div className="schedule-closures-operation">
        {timeSlotsError ? <PanelState embedded error message="수업 시간 목록을 불러오지 못했습니다." />
          : busy && !selectedTimeSlot ? <PanelState embedded message="수업 시간을 불러오는 중입니다." />
          : !selectedTimeSlot ? <PanelState embedded message="선택할 수업 시간이 없습니다." />
          : closurePending ? <PanelState embedded message="휴강 운영 상태를 불러오는 중입니다." />
          : closureError ? <PanelState embedded error message="휴강 작업을 불러오지 못했습니다. 최신 상태를 다시 조회해 주세요." />
          : <>
            <section className="schedule-closures-operation-summary">
              <div className="schedule-closures-section-heading">
                <div><p className="schedule-closures-hint">선택한 수업 시간 · {formatTimeSlot(selectedTimeSlot)}</p>
                  <h2>{inProgress ? '휴강 처리 중' : reopened ? '관리자 휴강 해제' : completed ? '휴강 완료' : withdrawn ? '휴강 철회' : '휴강 시작 전'}</h2></div>
                <StatusBadge status={closure?.status ?? '미시작'} />
              </div>
              <dl className="schedule-closures-summary">
                <div><dt>전체 정원</dt><dd>{selectedTimeSlot.totalCapacity ?? '-'}명</dd></div>
                <div><dt>현재 예약 상태</dt><dd>{currentClosed ? '현재 마감' : '예약 가능'}</dd></div>
                {closure && !withdrawn ? <>
                  <div><dt>처리 완료</dt><dd>{closure.resolvedCount}건</dd></div>
                  <div><dt>남은 예약</dt><dd>{closure.unresolvedCount}건</dd></div>
                </> : null}
              </dl>
              {closure && !withdrawn ? <ProgressText value={closure.progressPercent} resolved={closure.resolvedCount} total={closure.totalCount} /> : null}
              <p className="schedule-closures-state">{reopened
                ? currentClosed ? '관리자 휴강은 해제됐지만 다른 마감 원인으로 계속 마감됩니다.' : '관리자 휴강이 해제되어 현재 예약 가능합니다.'
                : inProgress || completed ? '신규 예약을 차단하고 있습니다. 기존 예약은 자동으로 취소·이동되지 않습니다.'
                  : '휴강을 시작하면 신규 예약을 차단하고 그 시점의 영향 예약을 확정합니다.'}</p>
              {!notStarted ? <p className="schedule-closures-strong-warning">이미 시작된 수업 시간은 휴강 시작·철회·해제를 할 수 없습니다.</p> : null}
              <div className="schedule-closures-actions">
                {canStart ? <button type="button" disabled={controlsBusy || !reason.trim() || !notStarted}
                  onClick={() => onConfirm({
                    title: '개별 수업 시간 휴강 시작', description: `${formatTimeSlot(selectedTimeSlot)}의 신규 예약을 차단합니다.`,
                    warning: '시작 시점의 영향 예약 목록이 확정됩니다. 기존 예약은 자동으로 취소되지 않습니다.',
                    confirmLabel: '휴강 시작',
                    operation: { kind: 'slot-start', timeSlotId: selectedTimeSlot.id as number, reason: reason.trim() },
                  })}>휴강 시작 검토</button> : null}
                {inProgress && closure ? <>
                  <button type="button" disabled={controlsBusy || closure.unresolvedCount > 0 || !reason.trim()}
                    onClick={() => onConfirm({
                      title: '개별 휴강 완료', description: `${formatTimeSlot(selectedTimeSlot)}의 남은 영향 예약이 0건입니다.`,
                      warning: '완료 후에도 관리자 휴강 설정은 유지됩니다.', confirmLabel: '휴강 완료',
                      operation: { kind: 'slot-complete', timeSlotId: closure.timeSlotId, reason: reason.trim(), expectedVersion: closure.version },
                    })}>휴강 완료</button>
                  <button type="button" className="danger" disabled={controlsBusy || closure.resolvedCount > 0 || !reason.trim() || !notStarted}
                    aria-describedby={closure.resolvedCount > 0 ? 'withdraw-blocked-reason' : undefined}
                    onClick={() => onConfirm({
                      title: '개별 휴강 철회', description: `${formatTimeSlot(selectedTimeSlot)}의 관리자 휴강을 철회합니다.`,
                      warning: '영향 예약이 하나도 처리되지 않은 경우에만 철회할 수 있습니다. 다른 마감 원인은 유지됩니다.',
                      confirmLabel: '휴강 철회',
                      operation: { kind: 'slot-withdraw', timeSlotId: closure.timeSlotId, reason: reason.trim(), expectedVersion: closure.version },
                    })}>휴강 철회</button>
                </> : null}
                {completed && closure.adminClosed ? <button type="button" className="secondary"
                  disabled={controlsBusy || !reason.trim() || !notStarted}
                  onClick={() => onConfirm({
                    title: '관리자 휴강 설정 해제', description: `${formatTimeSlot(selectedTimeSlot)}의 관리자 휴강 설정만 해제합니다.`,
                    warning: '다른 마감 원인이 남으면 계속 마감됩니다. 취소되거나 이동된 기존 예약은 자동 복구되지 않습니다.',
                    confirmLabel: '관리자 휴강 해제',
                    operation: { kind: 'slot-reopen', timeSlotId: closure.timeSlotId, reason: reason.trim(), expectedVersion: closure.version },
                  })}>관리자 휴강 해제</button> : null}
              </div>
              {inProgress && closure && closure.resolvedCount > 0 ? <p id="withdraw-blocked-reason" className="schedule-closures-strong-warning">
                이미 {closure.resolvedCount}건이 처리되어 휴강 철회가 불가능합니다.
              </p> : null}
              {reopened ? <p className="schedule-closures-hint" role="status">{closure.closed
                ? '다른 마감 원인 또는 날짜 운영 상태로 신규 예약은 계속 차단됩니다.'
                : '현재 수업 시간의 신규 예약 차단이 해제되었습니다.'}</p> : null}
            </section>
            {!withdrawn && closure ? <ImpactList title="휴강 시작 시 영향받은 예약" items={impacts.map(timeSlotImpactView)}
              memos={memos} itemResults={itemResults} keyPrefix="slot" busy={controlsBusy}
              cancellationEnabled={Boolean(inProgress)} onMemoChange={onMemoChange}
              onCancel={(item, memo) => onConfirm({
                title: `예약 번호 ${item.reservationId} 취소 확인`,
                description: `${item.memberName} · ${classLabel(item.classType)} · ${formatTimeSlot(selectedTimeSlot)}`,
                warning: cancellationNotice(item), confirmLabel: '예약 한 건 취소',
                operation: { kind: 'slot-cancel-reservation', timeSlotId: closure.timeSlotId, reservationId: item.reservationId, memo },
              })} /> : <PanelState embedded message="아직 영향 예약이 확정되지 않았습니다. 휴강 시작 후 확인할 수 있습니다." />}
          </>}
      </div>
    </section>
  )
}

interface ImpactView {
  reservationId: number
  memberName: string
  memberPhone: string
  classType: string
  currentStatus: string
  paymentSource: string
  couponId?: number | null
  resolved: boolean
  moved: boolean
}

function ImpactList({
  title,
  items,
  memos,
  itemResults,
  keyPrefix,
  busy,
  cancellationEnabled,
  onMemoChange,
  onCancel,
}: {
  title: string
  items: ImpactView[]
  memos: Record<number, string>
  itemResults: Record<string, ItemResult>
  keyPrefix: 'date' | 'slot'
  busy: boolean
  cancellationEnabled: boolean
  onMemoChange(reservationId: number, memo: string): void
  onCancel(item: ImpactView, memo: string): void
}) {
  return (
    <section className="schedule-closures-impact" aria-labelledby={`${keyPrefix}-impact-heading`}>
      <div className="schedule-closures-section-heading">
        <div><h3 id={`${keyPrefix}-impact-heading`}>{title}</h3><p>{items.length}건 표시</p></div>
      </div>
      {items.length === 0 ? <PanelState embedded message="영향 예약이 없습니다." /> : (
        <div className="schedule-closures-impact-list">
          {items.map((item) => {
            const result = itemResults[`${keyPrefix}-${item.reservationId}`]
            const memo = memos[item.reservationId] ?? ''
            return (
              <article className={`schedule-closures-impact-card${item.resolved ? ' resolved' : ''}`} key={item.reservationId}>
                <div className="schedule-closures-impact-heading">
                  <div><h4>예약 번호 {item.reservationId} · {item.memberName}</h4><p>{item.memberPhone} · {classLabel(item.classType)}</p></div>
                  <StatusBadge status={item.resolved ? '처리 완료' : item.currentStatus} reservation />
                </div>
                {item.resolved ? <p className="schedule-closures-resolved" role="status">{item.moved
                  ? '다른 시간으로 변경되어 처리 완료'
                  : item.currentStatus.toUpperCase() === 'CANCELLED' ? '취소 처리 완료' : '처리 완료'}</p> : null}
                <details>
                <summary>예약 상세 및 처리</summary>
                <dl className="schedule-closures-impact-details">
                  <div><dt>결제</dt><dd>{paymentLabel(item)}</dd></div>
                  <div><dt>현재 상태</dt><dd>{statusLabel(item.currentStatus, true)}</dd></div>
                  <div><dt>시간 변경</dt><dd>{item.moved ? '다른 시간으로 변경됨' : '없음'}</dd></div>
                </dl>
                {result ? <p id={`${keyPrefix}-result-${item.reservationId}`} className={`schedule-closures-item-result${result.error ? ' error' : ''}`} role={result.error ? 'alert' : 'status'}>{result.message}</p> : null}
                {!item.resolved && cancellationEnabled ? (
                  <div className="schedule-closures-item-action">
                    <label htmlFor={`${keyPrefix}-memo-${item.reservationId}`}>관리자 메모
                      <textarea id={`${keyPrefix}-memo-${item.reservationId}`} value={memo} maxLength={500} required disabled={busy}
                        aria-describedby={`${keyPrefix}-memo-help-${item.reservationId}${result ? ` ${keyPrefix}-result-${item.reservationId}` : ''}`}
                        aria-invalid={Boolean(result?.error)} onChange={(event) => onMemoChange(item.reservationId, event.target.value)} /></label>
                    <small id={`${keyPrefix}-memo-help-${item.reservationId}`}>예약 한 건만 처리합니다. 메모를 1~500자로 입력해 주세요.</small>
                    <button
                      type="button"
                      disabled={busy || !memo.trim()}
                      aria-describedby={result ? `${keyPrefix}-result-${item.reservationId}` : undefined}
                      onClick={() => onCancel(item, memo.trim())}
                    >예약 한 건 취소</button>
                  </div>
                ) : null}
                </details>
              </article>
            )
          })}
        </div>
      )}
    </section>
  )
}

function ProgressText({ value, resolved, total }: { value: number; resolved: number; total: number }) {
  return <div className="schedule-closures-progress">
    <progress max={100} value={value} aria-label="영향 예약 처리 진행률">{value}%</progress>
    <span>{total}건 중 {resolved}건 처리 · {value}%</span>
  </div>
}

function StatusBadge({ status, reservation = false }: { status: string; reservation?: boolean }) {
  return <span className="schedule-closures-status" data-status={status}>{statusLabel(status, reservation)}</span>
}

function PanelState({ message, error = false, embedded = false }: { message: string; error?: boolean; embedded?: boolean }) {
  const state = <div className={`schedule-closures-state${error ? ' error' : ''}`} role={error ? 'alert' : 'status'}>{message}</div>
  return embedded ? state : <section className="schedule-closures-panel">{state}</section>
}

function dateImpactView(item: ScheduleImpactReservationResponse): ImpactView {
  return {
    reservationId: item.reservationId,
    memberName: item.memberName,
    memberPhone: item.memberPhone,
    classType: item.classType,
    currentStatus: item.status,
    paymentSource: item.paymentSource,
    couponId: item.couponId,
    resolved: false,
    moved: false,
  }
}

function timeSlotImpactView(item: TimeSlotClosureImpactResponse): ImpactView {
  return {
    reservationId: item.reservationId,
    memberName: item.memberName,
    memberPhone: item.memberPhone,
    classType: item.classType,
    currentStatus: item.currentStatus,
    paymentSource: item.paymentSource,
    couponId: item.couponId,
    resolved: item.resolved,
    moved: item.moved,
  }
}

async function executeOperation(api: AdminScheduleClosuresApi, operation: Operation) {
  switch (operation.kind) {
    case 'date-start':
      return api.startDateClosing(operation.scheduleDate, operation.reason, operation.expectedVersion)
    case 'date-cancel-reservation':
      return api.cancelDateReservation(operation.scheduleDate, operation.reservationId, operation.memo)
    case 'date-complete':
      return api.completeDateClosing(operation.scheduleDate, operation.reason, operation.expectedVersion)
    case 'date-resume':
      return api.cancelDateClosing(operation.scheduleDate, operation.reason, operation.expectedVersion)
    case 'slot-start':
      return api.startTimeSlotClosure(operation.timeSlotId, operation.reason)
    case 'slot-cancel-reservation':
      return api.cancelTimeSlotReservation(operation.timeSlotId, operation.reservationId, operation.memo)
    case 'slot-complete':
      return api.completeTimeSlotClosure(operation.timeSlotId, operation.reason, operation.expectedVersion)
    case 'slot-withdraw':
      return api.withdrawTimeSlotClosure(operation.timeSlotId, operation.reason, operation.expectedVersion)
    case 'slot-reopen':
      return api.reopenTimeSlot(operation.timeSlotId, operation.reason, operation.expectedVersion)
  }
}

function isReservationCancellation(operation: Operation) {
  return operation.kind === 'date-cancel-reservation' || operation.kind === 'slot-cancel-reservation'
}

function itemKey(operation: Operation) {
  if (operation.kind === 'date-cancel-reservation') return `date-${operation.reservationId}`
  if (operation.kind === 'slot-cancel-reservation') return `slot-${operation.reservationId}`
  return ''
}

function operationSuccessMessage(operation: Operation, result: unknown) {
  if (operation.kind === 'date-start') {
    const response = result as ScheduleDateClosureImpactResponse
    return response.status === 'CLOSED'
      ? '날짜 휴무가 완료되었습니다. 신규 예약 차단이 유지됩니다.'
      : '날짜 휴무를 시작했습니다. 영향 예약을 한 건씩 처리해 주세요.'
  }
  if (operation.kind === 'date-complete') return '날짜 휴무가 완료되었습니다. 신규 예약 차단이 유지됩니다.'
  if (operation.kind === 'date-resume') return '휴무 진행을 취소했습니다. 현재 점유 예약을 다시 확인해 주세요. 이미 처리된 예약은 복구되지 않습니다.'
  if (operation.kind === 'slot-start') return '개별 휴강을 시작하고 영향 예약 목록을 고정했습니다.'
  if (operation.kind === 'slot-complete') return '영향 예약 처리를 확인하고 휴강을 완료했습니다.'
  if (operation.kind === 'slot-withdraw') return '처리된 영향 예약이 없음을 확인하고 휴강을 철회했습니다.'
  if (operation.kind === 'slot-reopen') return '관리자 휴강을 해제했습니다. 현재 예약 가능 여부를 확인해 주세요. 기존 예약은 복구되지 않습니다.'
  return ''
}

function paymentLabel(item: ImpactView) {
  return item.paymentSource.toUpperCase() === 'COUPON' ? '쿠폰 사용 예약' : '단건 결제 예약'
}

function cancellationNotice(item: ImpactView) {
  return item.paymentSource.toUpperCase() === 'COUPON'
    ? '이 예약 한 건만 취소하며 쿠폰이 반환됩니다. 이미 처리한 다른 예약은 변경되지 않습니다.'
    : '이 예약 한 건만 취소합니다. 별도 쿠폰 처리는 없습니다. 자동 환불이나 회원 알림은 실행되지 않습니다.'
}

function classLabel(value: string) {
  const labels: Record<string, string> = { FIRST_RIDE: '왕초보', ROUND_BEGINNER: '원형초보', ROUND_TROT: '원형 속보', LARGE_ARENA_BEGINNER: '대마장초보', LARGE_ARENA_TROT: '대마장 속보', CANTER_BEGINNER: '구보초보', CANTER: '구보', DRESSAGE: '마장마술', JUMPING: '장애물' }
  return labels[value.toUpperCase()] ?? '수업 종류 확인 필요'
}

function statusLabel(value: string, reservation = false) {
  if (reservation && value.toUpperCase() === 'COMPLETED') return '기승 완료'
  const labels: Record<string, string> = { NORMAL: '현재 운영 중', OPEN: '현재 운영 중', CLOSING: '휴무 처리 중', CLOSED: '휴무 완료', IN_PROGRESS: '휴강 처리 중', COMPLETED: '휴강 완료', WITHDRAWN: '휴강 철회', PENDING_ADMIN_APPROVAL: '관리자 승인 대기', PENDING_PAYMENT: '입금 확인 대기', CONFIRMED: '예약 확정', REJECTED: '예약 반려', CANCELLED: '예약 취소', PAYMENT_EXPIRED: '입금 기한 만료', APPROVAL_EXPIRED: '승인 기한 만료', NO_SHOW: '노쇼' }
  return labels[value.toUpperCase()] ?? (value === '처리 완료' || value === '미시작' ? value : '상태 확인 필요')
}

function isFutureTimeSlot(slot: TimeSlotResponse) {
  if (!slot.lessonDate || !slot.startTime) return false
  return new Date(`${slot.lessonDate.toISOString().slice(0, 10)}T${slot.startTime}+09:00`).getTime() > Date.now()
}

function formatDate(value: Date) {
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'long',
    timeZone: 'Asia/Seoul',
  }).format(value)
}

function formatTimeSlot(slot: TimeSlotResponse) {
  return `${formatDate(slot.lessonDate as Date)} ${slot.startTime?.slice(0, 5) ?? '-'} · 정원 ${slot.totalCapacity ?? '-'}명 · ${slot.closed ? '현재 마감' : '예약 가능'}`
}

function toApiDate(value: string) {
  return new Date(`${value}T00:00:00.000Z`)
}

function defaultFutureDate() {
  const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1_000)
  return new Intl.DateTimeFormat('en-CA', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    timeZone: 'Asia/Seoul',
  }).format(tomorrow)
}
