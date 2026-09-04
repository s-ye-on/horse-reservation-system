import { useEffect, useRef, useState } from 'react'
import { keepPreviousData, useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import type { AdminReservationResponse, TimeSlotResponse } from '@horse/api-client'
import {
  adminReservationsApi,
  getAdminReservationErrorKind,
  type AdminReservationsApi,
} from './admin-reservations.api'
import './admin-reservations-page.css'

const RESERVATIONS_KEY = ['admin', 'actionable-reservations'] as const
const MAX_MEMO_LENGTH = 500

const STATUS_META = {
  pending_admin_approval: { label: '쿠폰 승인대기', description: '쿠폰 점유를 확인하고 예약을 확정합니다.' },
  pending_payment: { label: '입금대기', description: '입금 내역을 확인한 뒤 예약을 확정합니다.' },
  payment_expired: { label: '입금만료', description: '현재 정원이 남아 있는 경우에만 복구할 수 있습니다.' },
  confirmed: { label: '예약 확정', description: '확정된 예약의 시간 변경과 취소를 처리합니다.' },
} as const

type ActionableStatus = keyof typeof STATUS_META

const ACTIONABLE_STATUSES = Object.keys(STATUS_META) as ActionableStatus[]
const PAGE_SIZE = 20
const INITIAL_PAGES: Record<ActionableStatus, number> = {
  pending_admin_approval: 0,
  pending_payment: 0,
  payment_expired: 0,
  confirmed: 0,
}

const WARNING_META = {
  critical: { label: '긴급', rank: 0 },
  warning: { label: '확인 필요', rank: 1 },
  normal: { label: '정상', rank: 2 },
} as const

const CLASS_LABELS: Record<string, string> = {
  FIRST_RIDE: '왕초보',
  ROUND_BEGINNER: '원형초보',
  ROUND_TROT: '원형 속보',
  LARGE_ARENA_BEGINNER: '대마장초보',
  LARGE_ARENA_TROT: '대마장 속보',
  CANTER_BEGINNER: '구보초보',
  CANTER: '구보',
  DRESSAGE: '마장마술',
  JUMPING: '장애물',
}

type BaseActionKind = 'confirm' | 'reject' | 'restore'
type AdjustmentActionKind = 'change' | 'cancel'
type ActionKind = BaseActionKind | AdjustmentActionKind

interface SelectedAction {
  reservationId: number
  kind: ActionKind
}

interface ReservationCommand {
  reservationId: number
  kind: BaseActionKind
  note?: string
}

function getErrorMessage(error: unknown) {
  const kind = getAdminReservationErrorKind(error)
  if (kind === 'forbidden') return '관리자 권한이 없어 예약을 처리할 수 없습니다.'
  if (kind === 'validation') return '입력한 사유 또는 메모를 다시 확인해 주세요.'
  if (kind === 'conflict') return '예약 상태가 변경됐거나 복구 가능한 정원이 없습니다. 최신 목록을 확인해 주세요.'
  return '예약을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

export function AdminReservationsPage({ api = adminReservationsApi }: { api?: AdminReservationsApi }) {
  const queryClient = useQueryClient()
  const commandLocked = useRef(false)
  const [pages, setPages] = useState<Record<ActionableStatus, number>>(INITIAL_PAGES)
  const [selectedAction, setSelectedAction] = useState<SelectedAction>()
  const [note, setNote] = useState('')
  const [localError, setLocalError] = useState<string>()
  const queries = useQueries({
    queries: ACTIONABLE_STATUSES.map((status) => ({
      queryKey: [...RESERVATIONS_KEY, status, pages[status]],
      queryFn: () => api.getReservations(status, pages[status], PAGE_SIZE),
      placeholderData: keepPreviousData,
    })),
  })

  useEffect(() => {
    ACTIONABLE_STATUSES.forEach((status, index) => {
      const totalPages = queries[index].data?.totalPages
      if (totalPages === undefined) return
      const validPage = totalPages === 0 ? 0 : Math.min(pages[status], totalPages - 1)
      if (validPage !== pages[status]) {
        setPages((current) => ({ ...current, [status]: validPage }))
      }
    })
  }, [pages, queries])

  const command = useMutation({
    mutationFn: async (operation: ReservationCommand) => {
      try {
        if (operation.kind === 'confirm') await api.confirm(operation.reservationId)
        if (operation.kind === 'reject') await api.reject(operation.reservationId, operation.note ?? '')
        if (operation.kind === 'restore') await api.restore(operation.reservationId, operation.note ?? '')
      } catch (error) {
        await queryClient.refetchQueries({ queryKey: RESERVATIONS_KEY })
        throw error
      }
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: RESERVATIONS_KEY })
      setSelectedAction(undefined)
      setNote('')
      setLocalError(undefined)
    },
  })

  const chooseAction = (reservationId: number, kind: ActionKind) => {
    setSelectedAction({ reservationId, kind })
    setNote('')
    setLocalError(undefined)
    command.reset()
  }

  const runCommand = (operation: ReservationCommand) => {
    if (commandLocked.current) return
    if (operation.kind !== 'confirm') {
      const trimmed = operation.note?.trim() ?? ''
      if (!trimmed || trimmed.length > MAX_MEMO_LENGTH) {
        setLocalError(`사유와 메모는 1자 이상 ${MAX_MEMO_LENGTH}자 이하로 입력해 주세요.`)
        return
      }
      operation.note = trimmed
    }
    commandLocked.current = true
    setLocalError(undefined)
    command.mutate(operation, { onSettled: () => { commandLocked.current = false } })
  }

  if (queries.some((query) => query.isPending)) {
    return <ReservationsState message="처리할 예약을 불러오는 중입니다." />
  }

  const total = queries.reduce((sum, query) => sum + (query.data?.totalElements ?? 0), 0)

  return (
    <main className="admin-reservations-page">
      <div className="admin-reservations-shell">
        <header className="admin-reservations-header">
          <div>
            <p className="admin-reservations-eyebrow">RESERVATION OPERATIONS</p>
            <h1>예약 운영 관리</h1>
            <p>승인과 입금 확인부터 확정 예약의 변경·취소까지 현재 상태에 맞게 처리합니다.</p>
          </div>
          <span className="admin-reservations-total">운영 대상 {total}건</span>
        </header>

        {command.isError ? <p className="admin-reservations-error" role="alert">{getErrorMessage(command.error)}</p> : null}
        {localError ? <p className="admin-reservations-error" role="alert">{localError}</p> : null}

        <div className="admin-reservations-columns">
          {ACTIONABLE_STATUSES.map((status, index) => {
            const meta = STATUS_META[status]
            const query = queries[index]
            const reservations = query.data?.content ?? []
            const totalPages = query.data?.totalPages ?? 0
            return (
              <section
                className="admin-reservations-section"
                key={status}
                aria-labelledby={`${status}-title`}
                aria-busy={query.isFetching}
              >
                <div className="admin-reservations-section-title">
                  <div><h2 id={`${status}-title`}>{meta.label}</h2><p>{meta.description}</p></div>
                  <strong>{query.data?.totalElements ?? 0}</strong>
                </div>
                {query.isError ? (
                  <div className="admin-reservations-empty" role="alert">
                    <p>{getErrorMessage(query.error)}</p>
                    <button type="button" onClick={() => { void query.refetch() }}>다시 시도</button>
                  </div>
                ) : reservations.length === 0 ? (
                  <p className="admin-reservations-empty">처리할 예약이 없습니다.</p>
                ) : (
                  <div className="admin-reservations-list">
                    {reservations.map((reservation) => (
                      <ReservationCard
                        key={reservation.reservationId}
                        reservation={reservation}
                        selectedAction={selectedAction}
                        note={note}
                        pending={command.isPending}
                        onChooseAction={chooseAction}
                        onChangeNote={setNote}
                        onCancel={() => { setSelectedAction(undefined); setNote(''); setLocalError(undefined) }}
                        onSubmit={runCommand}
                        api={api}
                        onOperationSuccess={async () => {
                          await queryClient.invalidateQueries({ queryKey: RESERVATIONS_KEY })
                          setSelectedAction(undefined)
                        }}
                      />
                    ))}
                  </div>
                )}
                {totalPages > 0 ? (
                  <nav className="admin-reservations-pagination" aria-label={`${meta.label} 페이지`}>
                    <button
                      type="button"
                      disabled={pages[status] === 0 || query.isFetching}
                      onClick={() => setPages((current) => ({ ...current, [status]: current[status] - 1 }))}
                    >이전</button>
                    <span aria-live="polite">{pages[status] + 1} / {totalPages}</span>
                    <button
                      type="button"
                      disabled={pages[status] + 1 >= totalPages || !query.data?.hasNext || query.isFetching}
                      onClick={() => setPages((current) => ({ ...current, [status]: current[status] + 1 }))}
                    >다음</button>
                  </nav>
                ) : null}
                {query.isFetching ? <p className="admin-reservations-page-loading" role="status">페이지 이동 중입니다.</p> : null}
              </section>
            )
          })}
        </div>
      </div>
    </main>
  )
}

function ReservationCard({
  reservation,
  selectedAction,
  note,
  pending,
  onChooseAction,
  onChangeNote,
  onCancel,
  onSubmit,
  api,
  onOperationSuccess,
}: {
  reservation: AdminReservationResponse
  selectedAction?: SelectedAction
  note: string
  pending: boolean
  onChooseAction(reservationId: number, kind: ActionKind): void
  onChangeNote(value: string): void
  onCancel(): void
  onSubmit(command: ReservationCommand): void
  api: AdminReservationsApi
  onOperationSuccess(): Promise<void>
}) {
  const reservationId = reservation.reservationId as number
  const action = selectedAction?.reservationId === reservationId ? selectedAction.kind : undefined
  const warning = WARNING_META[reservation.approvalWarning as keyof typeof WARNING_META]

  return (
    <article className={`admin-reservation-card${warning ? ` warning-${reservation.approvalWarning}` : ''}`}>
      <div className="admin-reservation-card-top">
        <div>
          <h3>{reservation.memberName ?? '이름 없음'}</h3>
          <a href={`tel:${reservation.memberPhone ?? ''}`}>{reservation.memberPhone ?? '전화번호 없음'}</a>
        </div>
        {warning ? <span className={`admin-reservation-warning ${reservation.approvalWarning}`}>{warning.label}</span> : null}
      </div>

      <dl className="admin-reservation-details">
        <div><dt>수업</dt><dd>{CLASS_LABELS[reservation.classType ?? ''] ?? reservation.classType ?? '-'}</dd></div>
        <div><dt>일시</dt><dd>{formatLesson(reservation.lessonDate, reservation.startTime)}</dd></div>
        <div><dt>결제</dt><dd>{reservation.paymentSource === 'coupon' ? '쿠폰' : '1회 결제'}</dd></div>
        {reservation.paymentDueAt ? <div><dt>입금 마감</dt><dd>{formatDateTime(reservation.paymentDueAt)}</dd></div> : null}
      </dl>

      {reservation.coupon ? (
        <p className="admin-reservation-coupon">
          {couponLabel(reservation.coupon.couponType)} #{reservation.coupon.couponId}
          <span>잔여 {reservation.coupon.remainingCount ?? 0}회 · 점유 {reservation.coupon.heldCount ?? 0}회</span>
        </p>
      ) : null}

      {!action ? (
        <div className="admin-reservation-actions">
          {reservation.status === 'pending_admin_approval' ? <button type="button" disabled={pending} onClick={() => onChooseAction(reservationId, 'confirm')}>쿠폰 예약 확정</button> : null}
          {reservation.status === 'pending_payment' ? <button type="button" disabled={pending} onClick={() => onChooseAction(reservationId, 'confirm')}>입금 확인 및 확정</button> : null}
          {reservation.status === 'payment_expired' ? <button type="button" disabled={pending} onClick={() => onChooseAction(reservationId, 'restore')}>만료 예약 복구</button> : null}
          {reservation.status === 'pending_admin_approval' || reservation.status === 'pending_payment' ? <button className="secondary" type="button" disabled={pending} onClick={() => onChooseAction(reservationId, 'reject')}>반려</button> : null}
          {reservation.status !== 'payment_expired' ? <button className="secondary" type="button" disabled={pending} onClick={() => onChooseAction(reservationId, 'change')}>시간 변경</button> : null}
          {reservation.status !== 'payment_expired' ? <button className="danger" type="button" disabled={pending} onClick={() => onChooseAction(reservationId, 'cancel')}>예약 취소</button> : null}
        </div>
      ) : (
        <ActionConfirmation
          action={action}
          reservationId={reservationId}
          note={note}
          pending={pending}
          onChangeNote={onChangeNote}
          onCancel={onCancel}
          onSubmit={onSubmit}
          reservation={reservation}
          api={api}
          onOperationSuccess={onOperationSuccess}
        />
      )}
    </article>
  )
}

function ActionConfirmation({ action, reservationId, note, pending, onChangeNote, onCancel, onSubmit, reservation, api, onOperationSuccess }: {
  action: ActionKind
  reservationId: number
  note: string
  pending: boolean
  onChangeNote(value: string): void
  onCancel(): void
  onSubmit(command: ReservationCommand): void
  reservation: AdminReservationResponse
  api: AdminReservationsApi
  onOperationSuccess(): Promise<void>
}) {
  if (action === 'change' || action === 'cancel') {
    return <ReservationAdjustmentPanel action={action} reservation={reservation} api={api} onCancel={onCancel} onSuccess={onOperationSuccess} />
  }
  const isConfirm = action === 'confirm'
  const label = action === 'confirm' ? '예약 확정' : action === 'reject' ? '예약 반려' : '예약 복구'
  return (
    <div className="admin-reservation-confirmation">
      <p>{label} 처리를 진행하시겠습니까?</p>
      {!isConfirm ? (
        <label>{action === 'reject' ? '반려 사유' : '복구 메모'}
          <textarea
            aria-label={action === 'reject' ? '반려 사유' : '복구 메모'}
            value={note}
            maxLength={MAX_MEMO_LENGTH}
            disabled={pending}
            onChange={(event) => onChangeNote(event.target.value)}
          />
          <span>{note.length}/{MAX_MEMO_LENGTH}</span>
        </label>
      ) : null}
      <div className="admin-reservation-actions">
        <button className="secondary" type="button" disabled={pending} onClick={onCancel}>돌아가기</button>
        <button type="button" disabled={pending} onClick={() => onSubmit({ reservationId, kind: action, note })}>
          {pending ? '처리 중' : `${label} 확인`}
        </button>
      </div>
    </div>
  )
}

function ReservationAdjustmentPanel({ action, reservation, api, onCancel, onSuccess }: {
  action: AdjustmentActionKind
  reservation: AdminReservationResponse
  api: AdminReservationsApi
  onCancel(): void
  onSuccess(): Promise<void>
}) {
  const reservationId = reservation.reservationId as number
  const [memo, setMemo] = useState('')
  const [targetTimeSlotId, setTargetTimeSlotId] = useState('')
  const [responsibility, setResponsibility] = useState('member')
  const [couponAction, setCouponAction] = useState('')
  const [localError, setLocalError] = useState<string>()
  const timeSlotsQuery = useQuery({
    queryKey: ['admin', 'reservation-adjustment', 'time-slots'],
    queryFn: api.getTimeSlots,
    enabled: action === 'change',
  })
  const cancellationPreview = useQuery({
    queryKey: ['admin', 'reservation-adjustment', 'cancellation-preview', reservationId, responsibility],
    queryFn: () => api.previewCancellation(reservationId, responsibility),
    enabled: action === 'cancel',
    retry: false,
  })
  useEffect(() => {
    if (cancellationPreview.data?.couponAction) setCouponAction(cancellationPreview.data.couponAction)
  }, [cancellationPreview.data?.couponAction])

  const operation = useMutation({
    mutationFn: async () => {
      if (action === 'change') await api.change(reservationId, Number(targetTimeSlotId), memo.trim())
      if (action === 'cancel') await api.cancel(reservationId, responsibility, couponAction, memo.trim())
    },
    onSuccess,
  })
  const availableTimeSlots = (timeSlotsQuery.data ?? []).filter((slot) => isAvailableTarget(reservation, slot))
  const submit = () => {
    const trimmedMemo = memo.trim()
    if (!trimmedMemo || trimmedMemo.length > MAX_MEMO_LENGTH) {
      setLocalError(`관리자 메모는 1자 이상 ${MAX_MEMO_LENGTH}자 이하로 입력해 주세요.`)
      return
    }
    if (action === 'change' && !targetTimeSlotId) {
      setLocalError('변경할 시간대를 선택해 주세요.')
      return
    }
    if (action === 'cancel' && !couponAction) {
      setLocalError('최종 쿠폰 처리를 선택해 주세요.')
      return
    }
    setLocalError(undefined)
    operation.mutate()
  }

  return (
    <div className="admin-reservation-confirmation admin-reservation-adjustment">
      <h4>{action === 'change' ? '예약 시간 변경' : '예약 취소 처리'}</h4>
      {action === 'change' ? (
        <label>변경 시간대
          <select aria-label="변경 시간대" value={targetTimeSlotId} disabled={timeSlotsQuery.isPending || operation.isPending} onChange={(event) => setTargetTimeSlotId(event.target.value)}>
            <option value="">시간대를 선택하세요</option>
            {availableTimeSlots.map((slot) => <option key={slot.id} value={slot.id}>{formatLesson(slot.lessonDate, slot.startTime)}</option>)}
          </select>
          {timeSlotsQuery.isPending ? <small>시간대를 불러오는 중입니다.</small> : null}
          {timeSlotsQuery.isError ? <small role="alert">시간대를 불러오지 못했습니다.</small> : null}
          {!timeSlotsQuery.isPending && availableTimeSlots.length === 0 ? <small>변경 가능한 열린 시간대가 없습니다.</small> : null}
        </label>
      ) : (
        <>
          <label>취소 책임
            <select aria-label="취소 책임" value={responsibility} disabled={operation.isPending} onChange={(event) => setResponsibility(event.target.value)}>
              <option value="member">회원 사유</option>
              <option value="stable">마장 사유</option>
              <option value="exception">운영 예외</option>
            </select>
          </label>
          {cancellationPreview.isPending ? <p>권장 쿠폰 처리를 확인하는 중입니다.</p> : null}
          {cancellationPreview.isError ? <p role="alert">권장 쿠폰 처리를 확인하지 못했습니다.</p> : null}
          {cancellationPreview.data ? (
            <div className="admin-reservation-recommendation">
              <span>서버 권장 처리</span>
              <strong>{couponActionLabel(cancellationPreview.data.couponAction)}</strong>
            </div>
          ) : null}
          <label>최종 쿠폰 처리
            <select aria-label="최종 쿠폰 처리" value={couponAction} disabled={operation.isPending || cancellationPreview.isPending} onChange={(event) => setCouponAction(event.target.value)}>
              {reservation.paymentSource === 'coupon' ? <><option value="return">쿠폰 반환</option><option value="deduct">1회 차감</option></> : <option value="none">처리 없음</option>}
            </select>
          </label>
          {cancellationPreview.data?.couponAction && couponAction !== cancellationPreview.data.couponAction ? <p className="admin-reservation-override">권장안과 다른 최종 처리를 선택했습니다.</p> : null}
        </>
      )}
      <label>관리자 메모
        <textarea aria-label="관리자 메모" value={memo} maxLength={MAX_MEMO_LENGTH} disabled={operation.isPending} onChange={(event) => setMemo(event.target.value)} />
        <span>{memo.length}/{MAX_MEMO_LENGTH}</span>
      </label>
      {localError ? <p className="admin-reservations-error" role="alert">{localError}</p> : null}
      {operation.isError ? <p className="admin-reservations-error" role="alert">{getErrorMessage(operation.error)}</p> : null}
      <div className="admin-reservation-actions">
        <button className="secondary" type="button" disabled={operation.isPending} onClick={onCancel}>돌아가기</button>
        <button type="button" disabled={operation.isPending || (action === 'cancel' && !cancellationPreview.data)} onClick={submit}>
          {operation.isPending ? '처리 중' : action === 'change' ? '시간 변경 확인' : '예약 취소 확인'}
        </button>
      </div>
    </div>
  )
}

function isAvailableTarget(reservation: AdminReservationResponse, slot: TimeSlotResponse) {
  if (!slot.id || slot.closed || !slot.lessonDate) return false
  return !(formatIsoDate(slot.lessonDate) === formatIsoDate(reservation.lessonDate) && slot.startTime === reservation.startTime)
}

function formatIsoDate(date?: Date) {
  if (!date) return ''
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).format(date)
}

function couponActionLabel(action?: string) {
  if (action === 'return') return '쿠폰 반환'
  if (action === 'deduct') return '1회 차감'
  if (action === 'none') return '처리 없음'
  return action ?? '-'
}

function formatLesson(date?: Date, startTime?: string) {
  if (!date) return '-'
  const lessonDate = new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul', month: 'long', day: 'numeric', weekday: 'short',
  }).format(date)
  return `${lessonDate} ${startTime?.slice(0, 5) ?? ''}`.trim()
}

function formatDateTime(date: Date) {
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(date)
}

function couponLabel(type?: string) {
  if (type === 'GENERAL') return '일반 쿠폰'
  if (type === 'DRESSAGE') return '마장마술 쿠폰'
  if (type === 'JUMPING') return '장애물 쿠폰'
  return type ?? '쿠폰'
}

function ReservationsState({ message, error = false }: { message: string; error?: boolean }) {
  return <main className="admin-reservations-page"><div className="admin-reservations-shell"><section className="admin-reservations-state" role={error ? 'alert' : undefined}>{message}</section></div></main>
}
