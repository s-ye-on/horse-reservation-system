import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type {
  AdminReservationResponse,
  ReservationCompletionResponse,
  ReservationNoShowResponse,
} from '@horse/api-client'
import {
  adminAttendanceApi,
  getAdminAttendanceErrorKind,
  type AdminAttendanceApi,
} from './admin-attendance.api'
import { AdminBulkAttendancePanel } from './admin-bulk-attendance-panel'
import {
  attendanceBlockedMessage,
  isAttendanceProcessable,
  isLessonToday,
} from './reservation-attendance-eligibility'
import './admin-attendance-page.css'

const ATTENDANCE_KEY = ['admin', 'confirmed-reservations'] as const
const MAX_MEMO_LENGTH = 500

const CLASS_LABELS: Record<string, string> = {
  FIRST_RIDE: '왕초보', ROUND_BEGINNER: '원형초보', ROUND_TROT: '원형 속보',
  LARGE_ARENA_BEGINNER: '대마장초보', LARGE_ARENA_TROT: '대마장 속보',
  DRESSAGE: '마장마술', JUMPING: '장애물',
}

type AttendanceAction = 'complete' | 'no-show'
type AttendanceResult =
  | { kind: 'complete'; response: ReservationCompletionResponse }
  | { kind: 'no-show'; response: ReservationNoShowResponse }

interface SelectedAction {
  reservationId: number
  kind: AttendanceAction
}

interface AttendanceCommand {
  reservationId: number
  kind: AttendanceAction
  couponAction?: string
  memo?: string
}

function getErrorMessage(error: unknown) {
  const kind = getAdminAttendanceErrorKind(error)
  if (kind === 'forbidden') return '관리자 권한이 없어 출석을 처리할 수 없습니다.'
  if (kind === 'validation') return '쿠폰 처리 또는 관리자 메모를 다시 확인해 주세요.'
  if (kind === 'conflict') return '예약 상태가 이미 변경되었습니다. 최신 목록을 확인해 주세요.'
  return '출석을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

export function AdminAttendancePage({ api = adminAttendanceApi }: { api?: AdminAttendanceApi }) {
  const queryClient = useQueryClient()
  const commandLocked = useRef(false)
  const [selectedAction, setSelectedAction] = useState<SelectedAction>()
  const [couponAction, setCouponAction] = useState('deduct')
  const [memo, setMemo] = useState('')
  const [localError, setLocalError] = useState<string>()
  const [result, setResult] = useState<AttendanceResult>()
  const query = useQuery({ queryKey: ATTENDANCE_KEY, queryFn: api.getConfirmedReservations })

  const command = useMutation({
    mutationFn: async (operation: AttendanceCommand): Promise<AttendanceResult> => {
      try {
        if (operation.kind === 'complete') {
          return { kind: 'complete', response: await api.complete(operation.reservationId) }
        }
        return {
          kind: 'no-show',
          response: await api.noShow(operation.reservationId, operation.couponAction ?? '', operation.memo ?? ''),
        }
      } catch (error) {
        await queryClient.refetchQueries({ queryKey: ATTENDANCE_KEY })
        throw error
      }
    },
    onSuccess: async (nextResult) => {
      setResult(nextResult)
      setSelectedAction(undefined)
      setMemo('')
      setLocalError(undefined)
      await queryClient.invalidateQueries({ queryKey: ATTENDANCE_KEY })
    },
  })

  const chooseAction = (reservation: AdminReservationResponse, kind: AttendanceAction) => {
    setSelectedAction({ reservationId: reservation.reservationId as number, kind })
    setCouponAction(reservation.paymentSource === 'coupon' ? 'deduct' : 'none')
    setMemo('')
    setLocalError(undefined)
    command.reset()
  }

  const runCommand = (operation: AttendanceCommand) => {
    if (commandLocked.current) return
    if (operation.kind === 'no-show') {
      const trimmed = operation.memo?.trim() ?? ''
      if (!trimmed || trimmed.length > MAX_MEMO_LENGTH) {
        setLocalError(`관리자 메모는 1자 이상 ${MAX_MEMO_LENGTH}자 이하로 입력해 주세요.`)
        return
      }
      operation.memo = trimmed
    }
    commandLocked.current = true
    setLocalError(undefined)
    command.mutate(operation, { onSettled: () => { commandLocked.current = false } })
  }

  if (query.isPending) return <AttendanceState message="확정 예약을 불러오는 중입니다." />
  if (query.isError) return <AttendanceState error message={getErrorMessage(query.error)} />

  const processableReservations = query.data.filter(isAttendanceProcessable)
  const todayReservations = processableReservations.filter((reservation) => isLessonToday(reservation))
  const overdueReservations = processableReservations.filter((reservation) => !isLessonToday(reservation))
  const upcomingReservations = query.data.filter((reservation) => !isAttendanceProcessable(reservation))

  return (
    <main className="admin-attendance-page">
      <div className="admin-attendance-shell">
        <header className="admin-attendance-header">
          <div><p>LESSON OPERATIONS</p><h1>수업 완료 및 노쇼</h1><span>시작한 수업만 처리하고, 예정 수업은 처리 가능 시각을 확인합니다.</span></div>
          <strong>처리 가능 {processableReservations.length}건</strong>
        </header>

        <AdminBulkAttendancePanel
          reservations={processableReservations}
          api={api}
          onProcessed={() => queryClient.invalidateQueries({ queryKey: ATTENDANCE_KEY })}
        />

        {result ? <AttendanceResultPanel result={result} /> : null}
        {command.isError ? <p className="admin-attendance-error" role="alert">{getErrorMessage(command.error)}</p> : null}
        {localError ? <p className="admin-attendance-error" role="alert">{localError}</p> : null}

        {query.data.length === 0 ? <AttendanceState embedded message="조회된 확정 예약이 없습니다." /> : null}
        <AttendanceSection title="오늘 처리 가능" description="오늘 시작한 수업입니다." reservations={todayReservations} emptyMessage="오늘 처리할 수업이 없습니다.">
          {(reservation) => (
            <AttendanceCard
              key={reservation.reservationId}
              reservation={reservation}
              selectedAction={selectedAction}
              couponAction={couponAction}
              memo={memo}
              pending={command.isPending}
              onChooseAction={chooseAction}
              onChangeCouponAction={setCouponAction}
              onChangeMemo={setMemo}
              onCancel={() => { setSelectedAction(undefined); setMemo(''); setLocalError(undefined) }}
              onSubmit={runCommand}
            />
          )}
        </AttendanceSection>
        <AttendanceSection title="지난 미처리" description="수업 시간이 지났지만 아직 출석 결과가 없는 예약입니다." reservations={overdueReservations} emptyMessage="지난 미처리 예약이 없습니다.">
          {(reservation) => (
            <AttendanceCard
              key={reservation.reservationId}
              reservation={reservation}
              selectedAction={selectedAction}
              couponAction={couponAction}
              memo={memo}
              pending={command.isPending}
              onChooseAction={chooseAction}
              onChangeCouponAction={setCouponAction}
              onChangeMemo={setMemo}
              onCancel={() => { setSelectedAction(undefined); setMemo(''); setLocalError(undefined) }}
              onSubmit={runCommand}
            />
          )}
        </AttendanceSection>
        <AttendanceSection title="시작 전 수업" description="수업 시작 시각이 지나면 완료 또는 노쇼 처리가 열립니다." reservations={upcomingReservations} emptyMessage="시작 전 수업이 없습니다.">
          {(reservation) => (
            <AttendanceCard
              key={reservation.reservationId}
              reservation={reservation}
              selectedAction={selectedAction}
              couponAction={couponAction}
              memo={memo}
              pending={command.isPending}
              onChooseAction={chooseAction}
              onChangeCouponAction={setCouponAction}
              onChangeMemo={setMemo}
              onCancel={() => { setSelectedAction(undefined); setMemo(''); setLocalError(undefined) }}
              onSubmit={runCommand}
            />
          )}
        </AttendanceSection>
      </div>
    </main>
  )
}

function AttendanceSection({ title, description, reservations, emptyMessage, children }: {
  title: string
  description: string
  reservations: AdminReservationResponse[]
  emptyMessage: string
  children(reservation: AdminReservationResponse): React.ReactNode
}) {
  return (
    <section className="admin-attendance-section" aria-labelledby={`attendance-${title.replaceAll(' ', '-')}`}>
      <div className="admin-attendance-section-heading">
        <div><h2 id={`attendance-${title.replaceAll(' ', '-')}`}>{title}</h2><p>{description}</p></div>
        <strong>{reservations.length}건</strong>
      </div>
      {reservations.length === 0 ? <p className="admin-attendance-section-empty">{emptyMessage}</p> : (
        <div className="admin-attendance-list">
          {reservations.map((reservation) => children(reservation))}
        </div>
      )}
    </section>
  )
}

/*
 * 단건과 일괄 처리는 모두 서버의 actions 판정을 사용한다. 이 컴포넌트는 허용된
 * 행동만 명령 UI로 바꾸고, 차단된 행동의 시간 정책을 재계산하지 않는다.
 */
function AttendanceCard({ reservation, selectedAction, couponAction, memo, pending, onChooseAction, onChangeCouponAction, onChangeMemo, onCancel, onSubmit }: {
  reservation: AdminReservationResponse
  selectedAction?: SelectedAction
  couponAction: string
  memo: string
  pending: boolean
  onChooseAction(reservation: AdminReservationResponse, kind: AttendanceAction): void
  onChangeCouponAction(value: string): void
  onChangeMemo(value: string): void
  onCancel(): void
  onSubmit(command: AttendanceCommand): void
}) {
  const reservationId = reservation.reservationId as number
  const action = selectedAction?.reservationId === reservationId ? selectedAction.kind : undefined
  const isCoupon = reservation.paymentSource === 'coupon'
  const canComplete = reservation.actions?.complete?.allowed === true
  const canNoShow = reservation.actions?.noShow?.allowed === true
  return (
    <article className="admin-attendance-card">
      <div className="admin-attendance-card-heading">
        <div><h3>{reservation.memberName ?? '이름 없음'}</h3><a href={`tel:${reservation.memberPhone ?? ''}`}>{reservation.memberPhone ?? '전화번호 없음'}</a></div>
        <span>{CLASS_LABELS[reservation.classType ?? ''] ?? reservation.classType ?? '-'}</span>
      </div>
      <dl>
        <div><dt>수업 일시</dt><dd>{formatLesson(reservation.lessonDate, reservation.startTime)}</dd></div>
        <div><dt>결제 방식</dt><dd>{isCoupon ? '쿠폰' : '1회 결제'}</dd></div>
        {reservation.coupon ? <div><dt>사용 쿠폰</dt><dd>#{reservation.coupon.couponId} · 잔여 {reservation.coupon.remainingCount ?? 0}회</dd></div> : null}
      </dl>
      {!canComplete && !canNoShow ? (
        <p className="admin-attendance-blocked">{attendanceBlockedMessage(reservation)}</p>
      ) : !action ? (
        <div className="admin-attendance-actions">
          {canComplete ? <button type="button" disabled={pending} onClick={() => onChooseAction(reservation, 'complete')}>수업 완료</button> : null}
          {canNoShow ? <button className="secondary" type="button" disabled={pending} onClick={() => onChooseAction(reservation, 'no-show')}>노쇼 처리</button> : null}
        </div>
      ) : (
        <div className="admin-attendance-confirmation">
          <p>{action === 'complete' ? '수업을 완료 처리하시겠습니까?' : '노쇼 처리와 쿠폰 결과를 확인해 주세요.'}</p>
          {action === 'no-show' ? <>
            <label>쿠폰 처리
              <select aria-label="쿠폰 처리" value={couponAction} disabled={pending} onChange={(event) => onChangeCouponAction(event.target.value)}>
                {isCoupon ? <><option value="deduct">1회 차감</option><option value="return">점유 반환</option></> : <option value="none">쿠폰 처리 없음</option>}
              </select>
            </label>
            <label>관리자 메모
              <textarea aria-label="관리자 메모" value={memo} maxLength={MAX_MEMO_LENGTH} disabled={pending} onChange={(event) => onChangeMemo(event.target.value)} />
              <span>{memo.length}/{MAX_MEMO_LENGTH}</span>
            </label>
          </> : null}
          <div className="admin-attendance-actions">
            <button className="secondary" type="button" disabled={pending} onClick={onCancel}>돌아가기</button>
            <button type="button" disabled={pending} onClick={() => onSubmit({ reservationId, kind: action, couponAction, memo })}>{pending ? '처리 중' : action === 'complete' ? '완료 처리 확인' : '노쇼 처리 확인'}</button>
          </div>
        </div>
      )}
    </article>
  )
}

function AttendanceResultPanel({ result }: { result: AttendanceResult }) {
  if (result.kind === 'complete') {
    const response = result.response
    return <section className="admin-attendance-result" aria-live="polite"><strong>수업 완료 반영</strong><span>상태 {response.status}</span><span>일반 {response.generalRideCount ?? 0}회</span><span>마장마술 {response.dressageRideCount ?? 0}회</span><span>장애물 {response.jumpingRideCount ?? 0}회</span>{response.couponId ? <span>쿠폰 #{response.couponId} 차감</span> : <span>쿠폰 처리 없음</span>}</section>
  }
  const response = result.response
  return <section className="admin-attendance-result" aria-live="polite"><strong>노쇼 반영</strong><span>상태 {response.status}</span><span>쿠폰 처리 {couponActionLabel(response.couponAction)}</span>{response.couponId ? <span>쿠폰 #{response.couponId}</span> : null}</section>
}

function couponActionLabel(action?: string) {
  if (action === 'deduct') return '1회 차감'
  if (action === 'return') return '점유 반환'
  if (action === 'none') return '없음'
  return action ?? '-'
}

function formatLesson(date?: Date, startTime?: string) {
  if (!date) return '-'
  const lessonDate = new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: 'long', day: 'numeric', weekday: 'short' }).format(date)
  return `${lessonDate} ${startTime?.slice(0, 5) ?? ''}`.trim()
}

function AttendanceState({ message, error = false, embedded = false }: { message: string; error?: boolean; embedded?: boolean }) {
  const content = <section className="admin-attendance-state" role={error ? 'alert' : undefined}>{message}</section>
  return embedded ? content : <main className="admin-attendance-page"><div className="admin-attendance-shell">{content}</div></main>
}
