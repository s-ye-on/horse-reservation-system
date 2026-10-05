import { useEffect, useId, useRef, useState } from 'react'
import { keepPreviousData, useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router'
import type { AdminReservationResponse, TimeSlotResponse } from '@horse/api-client'
import {
  adminReservationsApi,
  getAdminReservationErrorKind,
  getAdminReservationCommandErrorMessage,
  type AdminReservationsApi,
} from './admin-reservations.api'
import './admin-reservations-page.css'
import { AdminReservationActionDialog } from './admin-reservation-action-dialog'

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
  reservation: AdminReservationResponse
  kind: ActionKind
}

const ACTION_LABELS: Record<ActionKind, string> = { confirm: '예약 확정', reject: '예약 반려', restore: '예약 복구', change: '예약 시간 변경', cancel: '예약 취소 처리' }

interface ReservationCommand {
  reservationId: number
  kind: BaseActionKind
  note?: string
}

function getErrorMessage(error: unknown) {
  const kind = getAdminReservationErrorKind(error)
  if (kind === 'unauthorized') return '로그인이 필요합니다. 다시 로그인해 주세요.'
  if (kind === 'forbidden') return '관리자 권한이 없어 예약을 처리할 수 없습니다.'
  if (kind === 'not-found') return '대상 예약이나 시간대를 찾을 수 없습니다. 최신 목록을 확인해 주세요.'
  if (kind === 'validation') return '입력한 사유 또는 메모를 다시 확인해 주세요.'
  if (kind === 'conflict') return '예약 상태가 변경됐거나 복구 가능한 정원이 없습니다. 최신 목록을 확인해 주세요.'
  return '예약을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

export function AdminReservationsPage({ api = adminReservationsApi }: { api?: AdminReservationsApi }) {
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()
  const requestedReservationId = searchParams.get('reservationId')
  const focusedReservationId = requestedReservationId && /^\d+$/.test(requestedReservationId)
    ? Number(requestedReservationId)
    : undefined
  const commandLocked = useRef(false)
  const resultHeading = useRef<HTMLHeadingElement>(null)
  const [pages, setPages] = useState<Record<ActionableStatus, number>>(INITIAL_PAGES)
  const [selectedAction, setSelectedAction] = useState<SelectedAction>()
  const [note, setNote] = useState('')
  const [localError, setLocalError] = useState<string>()
  const [result, setResult] = useState<{ message: string; error?: boolean }>()
  const [adjustmentPending, setAdjustmentPending] = useState(false)
  const queries = useQueries({
    queries: ACTIONABLE_STATUSES.map((status) => ({
      queryKey: [...RESERVATIONS_KEY, status, pages[status]],
      queryFn: () => api.getReservations(status, pages[status], PAGE_SIZE),
      placeholderData: keepPreviousData,
    })),
  })
  const focusedReservationQuery = useQuery({
    queryKey: [...RESERVATIONS_KEY, 'focused', focusedReservationId],
    queryFn: () => api.getReservation(focusedReservationId as number),
    enabled: focusedReservationId !== undefined,
  })

  useEffect(() => { if (result && !selectedAction) resultHeading.current?.focus() }, [result, selectedAction])

  const finishOperation = async (reservation: AdminReservationResponse, kind: ActionKind) => {
    await queryClient.invalidateQueries({ queryKey: RESERVATIONS_KEY })
    if (kind === 'change') await queryClient.invalidateQueries({ queryKey: ['admin', 'reservation-adjustment', 'time-slots'] })
    setSelectedAction(undefined)
    setNote('')
    setLocalError(undefined)
    setResult({ message: `${reservation.memberName}님의 예약 #${reservation.reservationId} ${ACTION_LABELS[kind]} 처리가 반영되었습니다.` })
  }

  const failOperation = async (error: unknown) => {
    const message = await getAdminReservationCommandErrorMessage(error) ?? getErrorMessage(error)
    await queryClient.refetchQueries({ queryKey: RESERVATIONS_KEY })
    await queryClient.invalidateQueries({ queryKey: ['admin', 'reservation-adjustment'] })
    setSelectedAction(undefined)
    setResult({ message, error: true })
  }

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
      if (operation.kind === 'confirm') await api.confirm(operation.reservationId)
      if (operation.kind === 'reject') await api.reject(operation.reservationId, operation.note ?? '')
      if (operation.kind === 'restore') await api.restore(operation.reservationId, operation.note ?? '')
    },
    onSuccess: async () => {
      if (selectedAction) await finishOperation(selectedAction.reservation, selectedAction.kind)
    },
    onError: failOperation,
  })

  const chooseAction = (reservation: AdminReservationResponse, kind: ActionKind) => {
    setSelectedAction({ reservation, kind })
    setNote('')
    setLocalError(undefined)
    setResult(undefined)
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

        {result ? <section className={`admin-reservations-result${result.error ? ' result-error' : ''}`} role={result.error ? 'alert' : 'status'}>
          <h2 ref={resultHeading} tabIndex={-1}>이번 처리 결과</h2><p>{result.message}</p>
        </section> : null}
        <nav className="admin-reservations-overview" aria-label="예약 상태별 목록 바로가기">
          {ACTIONABLE_STATUSES.map((status, index) => <a key={status} href={`#${status}-title`}><span>{STATUS_META[status].label}</span><strong>{queries[index].isError ? '확인 필요' : `${queries[index].data?.totalElements ?? 0}건`}</strong></a>)}
        </nav>

        {focusedReservationId !== undefined ? (
          <section className="admin-reservations-focus" aria-labelledby="focused-reservation-heading">
            <div className="admin-reservations-focus-heading">
              <div><h2 id="focused-reservation-heading">예약 정리 대상</h2><p>정규 시간표에서 선택한 예약입니다.</p></div>
              <strong>#{focusedReservationId}</strong>
            </div>
            {focusedReservationQuery.isPending ? <p role="status">선택한 예약을 불러오는 중입니다.</p> : null}
            {focusedReservationQuery.isError ? <div className="admin-reservations-empty" role="alert">
              <p>{getErrorMessage(focusedReservationQuery.error)}</p>
              <button type="button" onClick={() => { void focusedReservationQuery.refetch() }}>다시 시도</button>
            </div> : null}
            {focusedReservationQuery.data ? <ReservationCard
              reservation={focusedReservationQuery.data}
              pending={command.isPending || adjustmentPending}
              onChooseAction={chooseAction}
            /> : null}
          </section>
        ) : null}

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
                        pending={command.isPending || adjustmentPending}
                        onChooseAction={chooseAction}
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
        {selectedAction ? <AdminReservationActionDialog title={ACTION_LABELS[selectedAction.kind]} pending={command.isPending || adjustmentPending}
          onClose={() => { setSelectedAction(undefined); setNote(''); setLocalError(undefined) }}>
          <ReservationFacts reservation={selectedAction.reservation} />
          <ActionConfirmation action={selectedAction.kind} reservationId={selectedAction.reservation.reservationId}
            reservation={selectedAction.reservation} note={note} pending={command.isPending} localError={localError}
            onChangeNote={(value) => { setNote(value); setLocalError(undefined) }}
            onCancel={() => { setSelectedAction(undefined); setNote(''); setLocalError(undefined) }} onSubmit={runCommand} api={api}
            onPending={setAdjustmentPending} onFailure={failOperation}
            onOperationSuccess={() => finishOperation(selectedAction.reservation, selectedAction.kind)} />
        </AdminReservationActionDialog> : null}
      </div>
    </main>
  )
}

function ReservationCard({
  reservation,
  pending,
  onChooseAction,
}: {
  reservation: AdminReservationResponse
  pending: boolean
  onChooseAction(reservation: AdminReservationResponse, kind: ActionKind): void
}) {
  const warning = WARNING_META[reservation.approvalWarning as keyof typeof WARNING_META]
  const adjustable = reservation.status === 'pending_admin_approval'
    || reservation.status === 'pending_payment'
    || reservation.status === 'confirmed'

  return (
    <article className={`admin-reservation-card${warning ? ` warning-${reservation.approvalWarning}` : ''}`}>
      <div className="admin-reservation-card-top">
        <div>
          <h3>{reservation.memberName ?? '이름 없음'}</h3>
          <span className="admin-reservation-number">예약 #{reservation.reservationId}</span>
        </div>
        {warning ? <span className={`admin-reservation-warning ${reservation.approvalWarning}`}>{warning.label}</span> : null}
      </div>

      <dl className="admin-reservation-details">
        <div><dt>수업</dt><dd>{CLASS_LABELS[reservation.classType ?? ''] ?? '수업 정보 확인 필요'}</dd></div>
        <div><dt>일시</dt><dd>{formatLesson(reservation.lessonDate, reservation.startTime)}</dd></div>
        <div><dt>상태</dt><dd><span className={`admin-reservation-status status-${reservation.status}`}>{statusLabel(reservation.status)}</span></dd></div>
        <div><dt>결제</dt><dd>{reservation.paymentSource === 'coupon' ? '쿠폰' : '1회 결제'}</dd></div>
        {reservation.paymentDueAt ? <div><dt>입금 마감</dt><dd>{formatDateTime(reservation.paymentDueAt)}</dd></div> : null}
      </dl>

      {reservation.coupon ? (
        <p className="admin-reservation-coupon">
          {couponLabel(reservation.coupon.couponType)}
        </p>
      ) : null}

      <details className="admin-reservation-metadata"><summary>연락처·결제 상세</summary><dl className="admin-reservation-details">
        <div><dt>전화번호</dt><dd>{reservation.memberPhone ? <a href={`tel:${reservation.memberPhone}`}>{reservation.memberPhone}</a> : '전화번호 없음'}</dd></div>
        <div><dt>신청 시각</dt><dd>{formatDateTime(reservation.approvalRequestedAt)}</dd></div>
        {reservation.coupon ? <><div><dt>쿠폰 정보</dt><dd>잔여 {reservation.coupon.remainingCount}회 · 점유 {reservation.coupon.heldCount}회</dd></div>
          <div><dt>쿠폰 상태</dt><dd>{({ active: '유효', depleted: '소진', expired: '만료' } as Record<string, string>)[reservation.coupon.status] ?? '확인 필요'}</dd></div>
          <div><dt>쿠폰 번호</dt><dd>#{reservation.coupon.couponId}</dd></div>
          <div><dt>쿠폰 만료</dt><dd>{reservation.coupon.expiresAt ? formatDateTime(reservation.coupon.expiresAt) : '만료일 없음'}</dd></div></> : null}
        {reservation.adminMemo ? <div><dt>관리자 메모</dt><dd>{reservation.adminMemo}</dd></div> : null}
      </dl></details>
        <div className="admin-reservation-actions">
          {reservation.status === 'pending_admin_approval' ? <button type="button" disabled={pending || !reservation.actions.approve.allowed} onClick={() => onChooseAction(reservation, 'confirm')}>쿠폰 예약 확정</button> : null}
          {reservation.status === 'pending_payment' ? <button type="button" disabled={pending || !reservation.actions.approve.allowed} onClick={() => onChooseAction(reservation, 'confirm')}>입금 확인 및 확정</button> : null}
          {reservation.status === 'payment_expired' ? <button type="button" disabled={pending} onClick={() => onChooseAction(reservation, 'restore')}>만료 예약 복구</button> : null}
          {reservation.status === 'pending_admin_approval' || reservation.status === 'pending_payment' ? <button className="secondary" type="button" disabled={pending} onClick={() => onChooseAction(reservation, 'reject')}>반려</button> : null}
          {adjustable ? <button className="secondary" type="button" disabled={pending || !reservation.actions.change.allowed} onClick={() => onChooseAction(reservation, 'change')}>시간 변경</button> : null}
          {adjustable ? <button className="danger" type="button" disabled={pending || !reservation.actions.cancel.allowed} onClick={() => onChooseAction(reservation, 'cancel')}>예약 취소</button> : null}
        </div>
        {(['approve', 'change', 'cancel'] as const).filter((kind) =>
          (kind !== 'approve' || reservation.status === 'pending_admin_approval' || reservation.status === 'pending_payment')
          && (kind === 'approve' || adjustable) && !reservation.actions[kind].allowed).map((kind) =>
          <p key={kind} className="admin-reservation-blocked">{({ approve: '예약 확정', change: '시간 변경', cancel: '예약 취소' })[kind]}: {blockedReasonLabel(reservation.actions[kind].blockedReason)}</p>)}
    </article>
  )
}

function ActionConfirmation({ action, reservationId, note, pending, localError, onChangeNote, onCancel, onSubmit, reservation, api, onOperationSuccess, onFailure, onPending }: {
  action: ActionKind
  reservationId: number
  note: string
  pending: boolean
  localError?: string
  onChangeNote(value: string): void
  onCancel(): void
  onSubmit(command: ReservationCommand): void
  reservation: AdminReservationResponse
  api: AdminReservationsApi
  onOperationSuccess(): Promise<void>
  onFailure(error: unknown): Promise<void>
  onPending(pending: boolean): void
}) {
  const fieldId = useId()
  if (action === 'change' || action === 'cancel') {
    return <ReservationAdjustmentPanel action={action} reservation={reservation} api={api} onCancel={onCancel} onSuccess={onOperationSuccess} onFailure={onFailure} onPending={onPending} />
  }
  const isConfirm = action === 'confirm'
  const label = action === 'confirm' ? '예약 확정' : action === 'reject' ? '예약 반려' : '예약 복구'
  return (
    <div className="admin-reservation-confirmation">
      <p>{action === 'confirm' && reservation.paymentSource === 'single_payment' ? '입금 내역을 확인한 뒤 예약을 확정합니다.' : `${label} 처리를 진행하시겠습니까?`}</p>
      {action === 'reject' ? <p className="admin-reservation-notice">신청을 반려하고 예약 점유를 해제합니다. 쿠폰 처리는 서버가 적용합니다.</p> : null}
      {action === 'restore' ? <p className="admin-reservation-notice">입금 내역을 확인해 주세요. 정원·일정·중복 예약 조건은 서버가 다시 검증합니다.</p> : null}
      {!isConfirm ? (
        <label>{action === 'reject' ? '반려 사유' : '복구 메모'}
          <textarea
            aria-label={action === 'reject' ? '반려 사유' : '복구 메모'}
            value={note}
            maxLength={MAX_MEMO_LENGTH}
            id={fieldId}
            aria-invalid={Boolean(localError)}
            aria-describedby={`${fieldId}-help ${fieldId}-error`}
            disabled={pending}
            onChange={(event) => onChangeNote(event.target.value)}
          />
          <span id={`${fieldId}-help`}>필수 · {note.length}/{MAX_MEMO_LENGTH}자</span>
          <span id={`${fieldId}-error`} className="admin-reservation-field-error" role={localError ? 'alert' : undefined}>{localError}</span>
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

function ReservationAdjustmentPanel({ action, reservation, api, onCancel, onSuccess, onFailure, onPending }: {
  action: AdjustmentActionKind
  reservation: AdminReservationResponse
  api: AdminReservationsApi
  onCancel(): void
  onSuccess(): Promise<void>
  onFailure(error: unknown): Promise<void>
  onPending(pending: boolean): void
}) {
  const reservationId = reservation.reservationId as number
  const [memo, setMemo] = useState('')
  const [targetTimeSlotId, setTargetTimeSlotId] = useState('')
  const [responsibility, setResponsibility] = useState('member')
  const [couponAction, setCouponAction] = useState('')
  const [localError, setLocalError] = useState<string>()
  const [fieldError, setFieldError] = useState<'memo' | 'target' | 'coupon'>()
  const [review, setReview] = useState(false)
  const locked = useRef(false)
  const fieldId = useId()
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
    if (cancellationPreview.data?.couponAction) {
      const recommendation = cancellationPreview.data.couponAction
      setCouponAction((current) => current || recommendation)
      setReview(false)
    }
  }, [responsibility, cancellationPreview.dataUpdatedAt, cancellationPreview.data?.couponAction])

  const operation = useMutation({
    mutationFn: async () => {
      if (action === 'change') await api.change(reservationId, Number(targetTimeSlotId), memo.trim())
      if (action === 'cancel') await api.cancel(reservationId, responsibility, couponAction, memo.trim())
    },
    onSuccess,
    onError: onFailure,
  })
  useEffect(() => {
    onPending(operation.isPending)
    return () => onPending(false)
  }, [operation.isPending, onPending])
  const availableTimeSlots = (timeSlotsQuery.data ?? []).filter((slot) => isAvailableTarget(reservation, slot))
  const previewReady = cancellationPreview.isSuccess && !cancellationPreview.isFetching && !cancellationPreview.isError
  const submit = () => {
    if (locked.current) return
    const trimmedMemo = memo.trim()
    if (!trimmedMemo || trimmedMemo.length > MAX_MEMO_LENGTH) {
      setFieldError('memo')
      setLocalError(`관리자 메모는 1자 이상 ${MAX_MEMO_LENGTH}자 이하로 입력해 주세요.`)
      return
    }
    if (action === 'change' && !availableTimeSlots.some((slot) => String(slot.id) === targetTimeSlotId)) {
      setReview(false)
      setFieldError('target')
      setLocalError('변경할 시간대를 선택해 주세요.')
      return
    }
    if (action === 'cancel' && !couponAction) {
      setFieldError('coupon')
      setLocalError('최종 쿠폰 처리를 선택해 주세요.')
      return
    }
    setLocalError(undefined)
    setFieldError(undefined)
    if (!review) { setReview(true); return }
    if (action === 'cancel' && !previewReady) return
    locked.current = true
    operation.mutate(undefined, { onSettled: () => { locked.current = false } })
  }

  return (
    <div className="admin-reservation-confirmation admin-reservation-adjustment">
      <h4>{action === 'change' ? '예약 시간 변경' : '예약 취소 처리'}</h4>
      {review ? <p className="admin-reservation-notice">입력한 처리 내용을 확인해 주세요. 실제 실행 시 서버가 상태와 정책을 다시 검증합니다.</p> : null}
      <fieldset disabled={operation.isPending || review}>
      {action === 'change' ? (
        <label>변경 시간대
          <select aria-label="변경 시간대" value={targetTimeSlotId} aria-invalid={fieldError === 'target'} aria-describedby={`${fieldId}-target-error`} disabled={timeSlotsQuery.isPending || operation.isPending} onChange={(event) => { setTargetTimeSlotId(event.target.value); setFieldError(undefined); setLocalError(undefined) }}>
            <option value="">시간대를 선택하세요</option>
            {availableTimeSlots.map((slot) => <option key={slot.id} value={slot.id}>{formatLesson(slot.lessonDate, slot.startTime)}</option>)}
          </select>
          {timeSlotsQuery.isPending ? <small>시간대를 불러오는 중입니다.</small> : null}
          {timeSlotsQuery.isError ? <small role="alert">시간대를 불러오지 못했습니다.</small> : null}
          {!timeSlotsQuery.isPending && availableTimeSlots.length === 0 ? <small>변경 가능한 열린 시간대가 없습니다.</small> : null}
          <span id={`${fieldId}-target-error`} className="admin-reservation-field-error" role={fieldError === 'target' ? 'alert' : undefined}>{fieldError === 'target' ? localError : ''}</span>
        </label>
      ) : (
        <>
          <label>취소 책임
            <select aria-label="취소 책임" value={responsibility} disabled={operation.isPending} onChange={(event) => { setResponsibility(event.target.value); setCouponAction(''); setLocalError(undefined); setFieldError(undefined) }}>
              <option value="member">회원 사유</option>
              <option value="stable">마장 사유</option>
              <option value="exception">운영 예외</option>
            </select>
          </label>
          {cancellationPreview.isFetching ? <p role="status">권장 쿠폰 처리를 확인하는 중입니다.</p> : null}
          {cancellationPreview.isError ? <div role="alert"><p>권장 쿠폰 처리를 확인하지 못했습니다. 최신 권장안을 다시 확인해 주세요.</p><button type="button" onClick={() => { void cancellationPreview.refetch() }}>권장안 다시 조회</button></div> : null}
          {previewReady && cancellationPreview.data ? (
            <div className="admin-reservation-recommendation">
              <span>서버 권장 처리</span>
              <strong>{couponActionLabel(cancellationPreview.data.couponAction)}</strong>
            </div>
          ) : null}
          <label>최종 쿠폰 처리
            <select aria-label="최종 쿠폰 처리" value={couponAction} aria-invalid={fieldError === 'coupon'} aria-describedby={`${fieldId}-coupon-error`} disabled={operation.isPending || !previewReady} onChange={(event) => { setCouponAction(event.target.value); setFieldError(undefined); setLocalError(undefined) }}>
              <option value="">처리를 선택하세요</option>
              {reservation.paymentSource === 'coupon' ? <><option value="return">쿠폰 반환</option><option value="deduct">1회 차감</option></> : <option value="none">처리 없음</option>}
            </select>
            <span id={`${fieldId}-coupon-error`} className="admin-reservation-field-error" role={fieldError === 'coupon' ? 'alert' : undefined}>{fieldError === 'coupon' ? localError : ''}</span>
          </label>
          {previewReady && couponAction && cancellationPreview.data?.couponAction && couponAction !== cancellationPreview.data.couponAction ? <p className="admin-reservation-override">권장안과 다른 최종 처리를 선택했습니다.</p> : null}
          <p className="admin-reservation-notice">{reservation.paymentSource === 'coupon' ? '쿠폰 반환 후 사용 가능 횟수는 최신 쿠폰 상태를 따릅니다.' : '별도 쿠폰 처리 없음. 자동 환불을 실행하는 작업이 아닙니다.'}</p>
        </>
      )}
      <label>관리자 메모
        <textarea aria-label="관리자 메모" value={memo} maxLength={MAX_MEMO_LENGTH} aria-invalid={fieldError === 'memo'} aria-describedby={`${fieldId}-memo-help ${fieldId}-memo-error`} disabled={operation.isPending} onChange={(event) => { setMemo(event.target.value); setFieldError(undefined); setLocalError(undefined) }} />
        <span id={`${fieldId}-memo-help`}>필수 · {memo.length}/{MAX_MEMO_LENGTH}자</span>
        <span id={`${fieldId}-memo-error`} className="admin-reservation-field-error" role={fieldError === 'memo' ? 'alert' : undefined}>{fieldError === 'memo' ? localError : ''}</span>
      </label>
      </fieldset>
      {review ? <dl className="admin-reservation-dialog-facts">
        {action === 'change' ? <div><dt>변경 후 시간대</dt><dd>{formatLesson(availableTimeSlots.find((slot) => String(slot.id) === targetTimeSlotId)?.lessonDate, availableTimeSlots.find((slot) => String(slot.id) === targetTimeSlotId)?.startTime)}</dd></div> : <>
          <div><dt>취소 책임</dt><dd>{({ member: '회원 사유', stable: '마장 사유', exception: '운영 예외' } as Record<string, string>)[responsibility]}</dd></div>
          <div><dt>최종 쿠폰 처리</dt><dd>{couponActionLabel(couponAction)}</dd></div></>}
        <div><dt>관리자 메모</dt><dd>{memo.trim()}</dd></div>
      </dl> : null}
      {operation.isError ? <p className="admin-reservations-error" role="alert">{getErrorMessage(operation.error)}</p> : null}
      <div className="admin-reservation-actions">
        <button className="secondary" type="button" disabled={operation.isPending} onClick={onCancel}>돌아가기</button>
        {review ? <button type="button" className="secondary" disabled={operation.isPending} onClick={() => setReview(false)}>입력 수정</button> : null}
        <button type="button" disabled={operation.isPending || (action === 'cancel' && !previewReady) || (action === 'change' && (timeSlotsQuery.isPending || timeSlotsQuery.isError))} onClick={submit}>
          {operation.isPending ? '처리 중' : !review ? '처리 내용 확인' : action === 'change' ? '시간 변경 확인' : '예약 취소 확인'}
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
  return '확인 필요'
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
  if (type === 'general') return '일반 기승 쿠폰'
  if (type === 'dressage') return '마장마술 쿠폰'
  if (type === 'jumping') return '장애물 쿠폰'
  return '쿠폰 종류 확인 필요'
}

function statusLabel(status: string) {
  return STATUS_META[status as ActionableStatus]?.label ?? ({ completed: '수업 완료', no_show: '노쇼', cancelled: '예약 취소', rejected: '반려됨', approval_expired: '승인 만료' } as Record<string, string>)[status] ?? '상태 확인 필요'
}

function blockedReasonLabel(reason?: string | null) {
  if (reason === 'RESERVATION_LESSON_ALREADY_STARTED') return '이미 수업이 시작되어 처리할 수 없습니다.'
  if (reason === 'RESERVATION_PAYMENT_EXPIRED') return '입금 확인 기한이 지났습니다. 최신 상태를 확인해 주세요.'
  return '현재 예약 상태에서는 처리할 수 없습니다.'
}

function ReservationFacts({ reservation }: { reservation: AdminReservationResponse }) {
  return <dl className="admin-reservation-dialog-facts">
    <div><dt>회원 · 예약 번호</dt><dd>{reservation.memberName} · #{reservation.reservationId}</dd></div>
    <div><dt>현재 상태</dt><dd>{statusLabel(reservation.status)}</dd></div>
    <div><dt>수업 시간</dt><dd>{formatLesson(reservation.lessonDate, reservation.startTime)}</dd></div>
    <div><dt>수업</dt><dd>{CLASS_LABELS[reservation.classType] ?? '확인 필요'}</dd></div>
    <div><dt>결제 방식</dt><dd>{reservation.paymentSource === 'coupon' ? couponLabel(reservation.coupon?.couponType) : '단건 결제'}</dd></div>
  </dl>
}

function ReservationsState({ message, error = false }: { message: string; error?: boolean }) {
  return <main className="admin-reservations-page"><div className="admin-reservations-shell"><section className="admin-reservations-state" role={error ? 'alert' : 'status'}>{message}</section></div></main>
}
