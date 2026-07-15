import { useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { AdminReservationResponse } from '@horse/api-client'
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
} as const

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
  DRESSAGE: '마장마술',
  JUMPING: '장애물',
}

type ActionKind = 'confirm' | 'reject' | 'restore'

interface SelectedAction {
  reservationId: number
  kind: ActionKind
}

interface ReservationCommand {
  reservationId: number
  kind: ActionKind
  note?: string
}

function getErrorMessage(error: unknown) {
  const kind = getAdminReservationErrorKind(error)
  if (kind === 'forbidden') return '관리자 권한이 없어 예약을 처리할 수 없습니다.'
  if (kind === 'validation') return '입력한 사유 또는 메모를 다시 확인해 주세요.'
  if (kind === 'conflict') return '예약 상태가 변경됐거나 복구 가능한 정원이 없습니다. 최신 목록을 확인해 주세요.'
  return '예약을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

function compareReservations(left: AdminReservationResponse, right: AdminReservationResponse) {
  const warningRank = (reservation: AdminReservationResponse) =>
    WARNING_META[reservation.approvalWarning as keyof typeof WARNING_META]?.rank ?? 3
  const warning = warningRank(left) - warningRank(right)
  if (warning !== 0) return warning
  const date = String(left.lessonDate ?? '').localeCompare(String(right.lessonDate ?? ''))
  if (date !== 0) return date
  const time = String(left.startTime ?? '').localeCompare(String(right.startTime ?? ''))
  if (time !== 0) return time
  return (left.reservationId ?? 0) - (right.reservationId ?? 0)
}

export function AdminReservationsPage({ api = adminReservationsApi }: { api?: AdminReservationsApi }) {
  const queryClient = useQueryClient()
  const commandLocked = useRef(false)
  const [selectedAction, setSelectedAction] = useState<SelectedAction>()
  const [note, setNote] = useState('')
  const [localError, setLocalError] = useState<string>()
  const query = useQuery({ queryKey: RESERVATIONS_KEY, queryFn: api.getActionableReservations })

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

  const grouped = useMemo(() => {
    const source = query.data ?? []
    return Object.fromEntries(Object.keys(STATUS_META).map((status) => [
      status,
      source.filter((reservation) => reservation.status === status).toSorted(compareReservations),
    ])) as Record<keyof typeof STATUS_META, AdminReservationResponse[]>
  }, [query.data])

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

  if (query.isPending) return <ReservationsState message="처리할 예약을 불러오는 중입니다." />
  if (query.isError) return <ReservationsState error message={getErrorMessage(query.error)} />

  const total = query.data.length

  return (
    <main className="admin-reservations-page">
      <div className="admin-reservations-shell">
        <header className="admin-reservations-header">
          <div>
            <p className="admin-reservations-eyebrow">RESERVATION OPERATIONS</p>
            <h1>예약 승인 및 입금 확인</h1>
            <p>승인대기 경고와 결제 상태를 확인하고 현재 상태에 맞는 작업을 처리합니다.</p>
          </div>
          <span className="admin-reservations-total">처리 대상 {total}건</span>
        </header>

        {command.isError ? <p className="admin-reservations-error" role="alert">{getErrorMessage(command.error)}</p> : null}
        {localError ? <p className="admin-reservations-error" role="alert">{localError}</p> : null}

        <div className="admin-reservations-columns">
          {(Object.keys(STATUS_META) as Array<keyof typeof STATUS_META>).map((status) => {
            const meta = STATUS_META[status]
            const reservations = grouped[status]
            return (
              <section className="admin-reservations-section" key={status} aria-labelledby={`${status}-title`}>
                <div className="admin-reservations-section-title">
                  <div><h2 id={`${status}-title`}>{meta.label}</h2><p>{meta.description}</p></div>
                  <strong>{reservations.length}</strong>
                </div>
                {reservations.length === 0 ? <p className="admin-reservations-empty">처리할 예약이 없습니다.</p> : (
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
                      />
                    ))}
                  </div>
                )}
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
}: {
  reservation: AdminReservationResponse
  selectedAction?: SelectedAction
  note: string
  pending: boolean
  onChooseAction(reservationId: number, kind: ActionKind): void
  onChangeNote(value: string): void
  onCancel(): void
  onSubmit(command: ReservationCommand): void
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
          {reservation.status !== 'payment_expired' ? <button className="secondary" type="button" disabled={pending} onClick={() => onChooseAction(reservationId, 'reject')}>반려</button> : null}
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
        />
      )}
    </article>
  )
}

function ActionConfirmation({ action, reservationId, note, pending, onChangeNote, onCancel, onSubmit }: {
  action: ActionKind
  reservationId: number
  note: string
  pending: boolean
  onChangeNote(value: string): void
  onCancel(): void
  onSubmit(command: ReservationCommand): void
}) {
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
  if (type === 'GENERAL') return '일반 10회권'
  if (type === 'DRESSAGE') return '마장마술 10회권'
  if (type === 'JUMPING') return '장애물 10회권'
  return type ?? '쿠폰'
}

function ReservationsState({ message, error = false }: { message: string; error?: boolean }) {
  return <main className="admin-reservations-page"><div className="admin-reservations-shell"><section className="admin-reservations-state" role={error ? 'alert' : undefined}>{message}</section></div></main>
}
