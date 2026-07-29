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
  const [dateReason, setDateReason] = useState('')
  const [slotReason, setSlotReason] = useState('')
  const [dateMemos, setDateMemos] = useState<Record<number, string>>({})
  const [slotMemos, setSlotMemos] = useState<Record<number, string>>({})
  const [itemResults, setItemResults] = useState<Record<string, ItemResult>>({})
  const [pending, setPending] = useState<PendingConfirmation>()
  const [error, setError] = useState<ScheduleClosureApiError>()
  const [success, setSuccess] = useState<string>()
  const commandGuard = useRef(false)
  const dialogRef = useRef<HTMLElement>(null)
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
  const selectedTimeSlot = sortedTimeSlots.find((slot) => slot.id === timeSlotId)

  useEffect(() => {
    if (!selectedTimeSlotId && sortedTimeSlots[0]?.id !== undefined) {
      setSelectedTimeSlotId(String(sortedTimeSlots[0].id))
    }
  }, [selectedTimeSlotId, sortedTimeSlots])

  useLayoutEffect(() => {
    if (pending) {
      dialogHeadingRef.current?.focus()
      return
    }
    returnFocusRef.current?.focus()
    returnFocusRef.current = null
  }, [pending])

  useEffect(() => {
    if (!pending) return
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
      if (!first || !last) return
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
    return () => window.removeEventListener('keydown', handleKeyDown)
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
              : `취소 완료 · 쿠폰 ${response.couponAction ?? 'NONE'}`,
          },
        }))
      } else {
        setSuccess(operationSuccessMessage(operation, result))
      }
      await refresh()
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

  const confirm = () => {
    if (!pending || commandGuard.current) return
    commandGuard.current = true
    mutation.mutate(pending.operation)
  }

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
      <div className="schedule-closures-shell">
        <header className="schedule-closures-header">
          <div>
            <p className="schedule-closures-eyebrow">CLOSURE OPERATIONS</p>
            <h1>날짜 휴무 및 개별 휴강</h1>
          </div>
          <button type="button" className="secondary" disabled={mutation.isPending} onClick={retryLatest}>최신 상태 새로고침</button>
        </header>

        <section className="schedule-closures-policy" aria-label="운영 원칙">
          <strong>예약은 자동 취소되지 않습니다.</strong>
          <span>신규 유입을 먼저 막은 뒤 고객에게 연락하고 예약별 전용 취소를 실행합니다.</span>
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
            개별 TimeSlot 휴강
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
              busy={mutation.isPending}
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
              timeSlots={sortedTimeSlots}
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
              busy={mutation.isPending || timeSlotsQuery.isPending}
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
          >
            <h2 id="closure-confirmation-heading" ref={dialogHeadingRef} tabIndex={-1}>{pending.title}</h2>
            <p>{pending.description}</p>
            {pending.warning ? <p className="schedule-closures-dialog-warning">{pending.warning}</p> : null}
            <div className="schedule-closures-actions">
              <button type="button" className="secondary" disabled={mutation.isPending} onClick={() => setPending(undefined)}>취소</button>
              <button type="button" disabled={mutation.isPending} onClick={confirm}>
                {mutation.isPending ? '처리 중' : pending.confirmLabel}
              </button>
            </div>
          </section>
        </div>
      ) : null}
    </main>
  )
}

function DateClosurePanel({
  scheduleDate,
  onScheduleDateChange,
  reason,
  onReasonChange,
  scheduleDateQuery,
  impactQuery,
  memos,
  onMemoChange,
  itemResults,
  busy,
  onConfirm,
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
  if (scheduleDateQuery.isPending || impactQuery.isPending) return <PanelState message="날짜 운영 상태를 불러오는 중입니다." />
  if (scheduleDateQuery.isError || impactQuery.isError) return <PanelState error message="날짜 운영 상태를 불러오지 못했습니다. 최신 상태를 다시 조회해 주세요." />

  const dateState = scheduleDateQuery.data
  const impact = impactQuery.data
  const sortedReservations = [...impact.reservations].sort((left, right) => left.reservationId - right.reservationId)
  const closing = dateState.status === 'CLOSING'
  const closed = dateState.status === 'CLOSED'

  const requireReason = () => {
    if (!reason.trim()) return false
    return true
  }

  return (
    <section className="schedule-closures-panel" aria-labelledby="date-closure-heading">
      <div className="schedule-closures-section-heading">
        <div><h2 id="date-closure-heading">날짜 전체 휴무</h2><p>미래 날짜 전체의 신규 유입을 차단하고 기존 예약을 개별 정리합니다.</p></div>
        <StatusBadge status={dateState.status} />
      </div>
      <div className="schedule-closures-controls">
        <label>대상 날짜<input type="date" value={scheduleDate} disabled={busy} onChange={(event) => onScheduleDateChange(event.target.value)} /></label>
        <label>운영 사유<input value={reason} maxLength={500} disabled={busy} onChange={(event) => onReasonChange(event.target.value)} placeholder="예: 시설 점검" /></label>
      </div>
      <dl className="schedule-closures-summary">
        <div><dt>현재 상태</dt><dd>{dateState.status}</dd></div>
        <div><dt>복귀 상태</dt><dd>{dateState.resumeStatus ?? '-'}</dd></div>
        <div><dt>최초 영향</dt><dd>{impact.initialReservationCount}건</dd></div>
        <div><dt>남은 예약</dt><dd>{impact.remainingReservationCount}건</dd></div>
      </dl>
      <ProgressText value={impact.progressPercent} resolved={impact.resolvedReservationCount} total={impact.initialReservationCount} />
      <p className="schedule-closures-hint">
        CLOSING과 CLOSED에서는 회원 예약, 예약 변경, 관리자 수동 예약과 TimeSlot 추가가 차단됩니다.
      </p>
      <div className="schedule-closures-actions">
        {!closing && !closed ? (
          <>
            <button
              type="button"
              disabled={busy || !reason.trim()}
              onClick={() => {
                if (!requireReason()) return
                onConfirm({
                  title: '날짜 휴무 처리 시작',
                  description: `${formatDate(dateState.scheduleDate)}의 활성 예약 ${impact.activeReservationCount}건을 확인했습니다.`,
                  warning: 'CLOSING 전환 후 예약은 자동 취소되지 않으며 예약별로 정리해야 합니다.',
                  confirmLabel: 'CLOSING 시작',
                  operation: {
                    kind: 'date-start',
                    scheduleDate: toApiDate(scheduleDate),
                    reason: reason.trim(),
                    expectedVersion: dateState.version,
                  },
                })
              }}
            >영향 확인 후 휴무 시작</button>
            {impact.activeReservationCount === 0 ? (
              <button
                type="button"
                disabled={busy || !reason.trim()}
                onClick={() => onConfirm({
                  title: '예약 없는 날짜 즉시 휴무',
                  description: '활성 예약 0건을 서버가 같은 트랜잭션에서 다시 검사한 뒤 CLOSED로 확정합니다.',
                  confirmLabel: '즉시 CLOSED 확정',
                  operation: {
                    kind: 'date-start',
                    scheduleDate: toApiDate(scheduleDate),
                    reason: reason.trim(),
                    expectedVersion: dateState.version,
                  },
                })}
              >활성 예약 없음 · 즉시 CLOSED 확정</button>
            ) : null}
          </>
        ) : null}
        {closing ? (
          <>
            <button
              type="button"
              disabled={busy || impact.remainingReservationCount > 0 || !reason.trim()}
              onClick={() => onConfirm({
                title: '날짜 휴무 확정',
                description: '현재 활성 예약 0건을 서버가 다시 검사한 뒤 CLOSED로 전환합니다.',
                confirmLabel: 'CLOSED 확정',
                operation: {
                  kind: 'date-complete',
                  scheduleDate: toApiDate(scheduleDate),
                  reason: reason.trim(),
                  expectedVersion: impact.version,
                },
              })}
            >CLOSED 확정</button>
            <button
              type="button"
              className="secondary"
              disabled={busy || !reason.trim()}
              onClick={() => onConfirm({
                title: '날짜 휴무 처리 취소',
                description: `날짜 상태를 ${impact.resumeStatus ?? '기존 상태'}로 복귀합니다.`,
                warning: '이미 취소되거나 이동된 예약은 자동 복구되지 않습니다.',
                confirmLabel: 'CLOSING 취소',
                operation: {
                  kind: 'date-resume',
                  scheduleDate: toApiDate(scheduleDate),
                  reason: reason.trim(),
                  expectedVersion: impact.version,
                },
              })}
            >휴무 처리 취소</button>
          </>
        ) : null}
      </div>
      {closed ? <p className="schedule-closures-strong-warning">CLOSED 확정 후 임의 재개는 현재 승인된 API 범위에 포함되지 않습니다.</p> : null}
      <ImpactList
        title={closing ? '정리할 날짜 예약' : '휴무 시작 전 영향 예약'}
        items={sortedReservations.map(dateImpactView)}
        memos={memos}
        itemResults={itemResults}
        keyPrefix="date"
        busy={busy}
        cancellationEnabled={closing}
        onMemoChange={onMemoChange}
        onCancel={(item, memo) => onConfirm({
          title: `예약 #${item.reservationId} 휴무 취소`,
          description: `${item.memberName} 회원의 예약을 마장 책임으로 취소합니다.`,
          warning: item.couponId ? '쿠폰 점유는 원래 Coupon으로 RETURN됩니다.' : '1회 결제 예약은 Coupon NONE으로 처리됩니다.',
          confirmLabel: '예약 취소 확정',
          operation: {
            kind: 'date-cancel-reservation',
            scheduleDate: toApiDate(scheduleDate),
            reservationId: item.reservationId,
            memo,
          },
        })}
      />
    </section>
  )
}

function TimeSlotClosurePanel({
  timeSlots,
  selectedId,
  onSelectedIdChange,
  selectedTimeSlot,
  closure,
  closurePending,
  closureError,
  reason,
  onReasonChange,
  memos,
  onMemoChange,
  itemResults,
  busy,
  timeSlotsError,
  onConfirm,
}: {
  timeSlots: TimeSlotResponse[]
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
  if (timeSlotsError) return <PanelState error message="TimeSlot 목록을 불러오지 못했습니다." />
  const impacts = [...(closure?.impacts ?? [])].sort((left, right) => left.reservationId - right.reservationId)
  const inProgress = closure?.status === 'IN_PROGRESS'
  const completed = closure?.status === 'COMPLETED'
  const reopened = completed && !closure.adminClosed
  const canStart = !closure || closure.status === 'WITHDRAWN'

  return (
    <section className="schedule-closures-panel" aria-labelledby="slot-closure-heading">
      <div className="schedule-closures-section-heading">
        <div><h2 id="slot-closure-heading">개별 TimeSlot 휴강</h2><p>휴강 시작 시 고정된 예약 목록을 고객 연락 후 한 건씩 정리합니다.</p></div>
        <StatusBadge status={closure?.status ?? '미시작'} />
      </div>
      <div className="schedule-closures-controls">
        <label>대상 TimeSlot<select value={selectedId} disabled={busy} onChange={(event) => onSelectedIdChange(event.target.value)}>
          {timeSlots.length === 0 ? <option value="">선택할 TimeSlot 없음</option> : null}
          {timeSlots.map((slot) => <option key={slot.id} value={slot.id}>{formatTimeSlot(slot)}</option>)}
        </select></label>
        <label>운영 사유<input value={reason} maxLength={500} disabled={busy} onChange={(event) => onReasonChange(event.target.value)} placeholder="예: 강사 사정" /></label>
      </div>
      {closurePending ? <PanelState embedded message="고정 영향 목록을 불러오는 중입니다." /> : null}
      {closureError ? <PanelState embedded error message="휴강 작업을 불러오지 못했습니다. 최신 상태를 다시 조회해 주세요." /> : null}
      {closure ? (
        <>
          <dl className="schedule-closures-summary">
            <div><dt>휴강 상태</dt><dd>{closure.status}</dd></div>
            <div><dt>고정 분모</dt><dd>{closure.totalCount}건</dd></div>
            <div><dt>해결</dt><dd>{closure.resolvedCount}건</dd></div>
            <div><dt>미해결</dt><dd>{closure.unresolvedCount}건</dd></div>
          </dl>
          <ProgressText value={closure.progressPercent} resolved={closure.resolvedCount} total={closure.totalCount} />
        </>
      ) : <PanelState embedded message="선택한 TimeSlot에 휴강 작업이 없습니다." />}
      <div className="schedule-closures-actions">
        {canStart && selectedTimeSlot?.id !== undefined ? (
          <button
            type="button"
            disabled={busy || !reason.trim()}
            onClick={() => onConfirm({
              title: '개별 TimeSlot 휴강 시작',
              description: `${formatTimeSlot(selectedTimeSlot)}의 신규 예약 유입을 즉시 차단합니다.`,
              warning: '시작 시점의 활성 예약이 고정 목록으로 저장되며 자동 취소되지 않습니다.',
              confirmLabel: '휴강 시작',
              operation: { kind: 'slot-start', timeSlotId: selectedTimeSlot.id as number, reason: reason.trim() },
            })}
          >휴강 시작</button>
        ) : null}
        {inProgress && closure ? (
          <>
            <button
              type="button"
              disabled={busy || closure.unresolvedCount > 0 || !reason.trim()}
              onClick={() => onConfirm({
                title: '개별 휴강 정리 완료',
                description: '고정 영향 전체 해결과 현재 활성 예약 0건을 서버가 다시 검사합니다.',
                confirmLabel: '휴강 완료',
                operation: { kind: 'slot-complete', timeSlotId: closure.timeSlotId, reason: reason.trim(), expectedVersion: closure.version },
              })}
            >휴강 정리 완료</button>
            <button
              type="button"
              className="secondary"
              disabled={busy || closure.resolvedCount > 0 || !reason.trim()}
              aria-describedby={closure.resolvedCount > 0 ? 'withdraw-blocked-reason' : undefined}
              onClick={() => onConfirm({
                title: '개별 휴강 철회',
                description: '처리된 영향 예약이 0건인 상태에서만 adminClosed를 해제합니다.',
                confirmLabel: '휴강 철회',
                operation: { kind: 'slot-withdraw', timeSlotId: closure.timeSlotId, reason: reason.trim(), expectedVersion: closure.version },
              })}
            >휴강 철회</button>
          </>
        ) : null}
        {completed && closure.adminClosed ? (
          <button
            type="button"
            disabled={busy || !reason.trim()}
            onClick={() => onConfirm({
              title: '완료된 TimeSlot 재개',
              description: 'adminClosed만 해제하며 다른 마감 원인과 날짜 상태가 계속 우선합니다.',
              warning: '취소되거나 이동된 기존 예약은 자동 복구되지 않습니다.',
              confirmLabel: '예약 재개',
              operation: { kind: 'slot-reopen', timeSlotId: closure.timeSlotId, reason: reason.trim(), expectedVersion: closure.version },
            })}
          >완료 후 재개</button>
        ) : null}
      </div>
      {inProgress && closure && closure.resolvedCount > 0 ? (
        <p id="withdraw-blocked-reason" className="schedule-closures-strong-warning">
          이미 {closure.resolvedCount}건이 해결되어 휴강 철회가 불가능합니다.
        </p>
      ) : null}
      {completed && closure.adminClosed ? (
        <p className="schedule-closures-strong-warning">
          재개해도 취소·이동된 예약은 복구되지 않으며 정기 휴일, Template 비활성화와 날짜 CLOSING/CLOSED가 우선합니다.
        </p>
      ) : null}
      {reopened ? (
        <p className="schedule-closures-hint" role="status">
          개별 휴강은 재개되었습니다. {closure.closed
            ? '다른 마감 원인 또는 날짜 운영 상태로 신규 예약은 계속 차단됩니다.'
            : '현재 TimeSlot의 신규 예약 차단이 해제되었습니다.'}
        </p>
      ) : null}
      <ImpactList
        title="휴강 시작 시 고정된 영향 예약"
        items={impacts.map(timeSlotImpactView)}
        memos={memos}
        itemResults={itemResults}
        keyPrefix="slot"
        busy={busy}
        cancellationEnabled={Boolean(inProgress)}
        onMemoChange={onMemoChange}
        onCancel={(item, memo) => onConfirm({
          title: `예약 #${item.reservationId} 휴강 취소`,
          description: `${item.memberName} 회원에게 연락한 결과를 반영해 예약을 취소합니다.`,
          warning: item.couponId ? '쿠폰 점유는 원래 Coupon으로 RETURN됩니다.' : '1회 결제 예약은 Coupon NONE으로 처리됩니다.',
          confirmLabel: '예약 취소 확정',
          operation: {
            kind: 'slot-cancel-reservation',
            timeSlotId: closure?.timeSlotId as number,
            reservationId: item.reservationId,
            memo,
          },
        })}
      />
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
                  <div><h4>예약 #{item.reservationId} · {item.memberName}</h4><p>{item.memberPhone} · {item.classType}</p></div>
                  <StatusBadge status={item.resolved ? '해결됨' : item.currentStatus} />
                </div>
                <dl className="schedule-closures-impact-details">
                  <div><dt>결제</dt><dd>{paymentLabel(item)}</dd></div>
                  <div><dt>현재 상태</dt><dd>{item.currentStatus}</dd></div>
                  <div><dt>이동</dt><dd>{item.moved ? '다른 TimeSlot로 이동' : '아니오'}</dd></div>
                </dl>
                {result ? <p id={`${keyPrefix}-result-${item.reservationId}`} className={`schedule-closures-item-result${result.error ? ' error' : ''}`} role={result.error ? 'alert' : 'status'}>{result.message}</p> : null}
                {!item.resolved && cancellationEnabled ? (
                  <div className="schedule-closures-item-action">
                    <label>취소 메모<textarea value={memo} maxLength={500} disabled={busy} onChange={(event) => onMemoChange(item.reservationId, event.target.value)} /></label>
                    <button
                      type="button"
                      disabled={busy || !memo.trim()}
                      aria-describedby={result ? `${keyPrefix}-result-${item.reservationId}` : undefined}
                      onClick={() => onCancel(item, memo.trim())}
                    >연락 후 취소 확정</button>
                  </div>
                ) : null}
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
    <progress max={100} value={value}>{value}%</progress>
    <span>{resolved}/{total}건 해결 · {value}%</span>
  </div>
}

function StatusBadge({ status }: { status: string }) {
  return <span className="schedule-closures-status" data-status={status}>{status}</span>
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
      ? '활성 예약 0건을 재검사하고 날짜를 CLOSED로 확정했습니다.'
      : '날짜를 CLOSING으로 전환했습니다. 영향 예약을 개별 정리해 주세요.'
  }
  if (operation.kind === 'date-complete') return '활성 예약 0건을 재검사하고 날짜를 CLOSED로 확정했습니다.'
  if (operation.kind === 'date-resume') return 'CLOSING을 취소하고 승인된 이전 상태로 복귀했습니다.'
  if (operation.kind === 'slot-start') return '개별 휴강을 시작하고 영향 예약 목록을 고정했습니다.'
  if (operation.kind === 'slot-complete') return '모든 영향 해결과 활성 예약 0건을 재검사하고 휴강을 완료했습니다.'
  if (operation.kind === 'slot-withdraw') return '처리된 영향 예약이 없음을 확인하고 휴강을 철회했습니다.'
  if (operation.kind === 'slot-reopen') return 'adminClosed를 해제했습니다. 기존 예약은 복구되지 않습니다.'
  return ''
}

function paymentLabel(item: ImpactView) {
  return item.couponId !== undefined
    ? `쿠폰 #${item.couponId} · 취소 시 RETURN`
    : `${item.paymentSource} · Coupon NONE`
}

function formatDate(value: Date) {
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'long',
    timeZone: 'Asia/Seoul',
  }).format(value)
}

function formatTimeSlot(slot: TimeSlotResponse) {
  return `${formatDate(slot.lessonDate as Date)} ${slot.startTime?.slice(0, 5) ?? '-'} · #${slot.id}`
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
