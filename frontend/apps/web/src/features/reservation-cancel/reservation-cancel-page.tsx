import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import type { ReservationCancelResponse } from '@horse/api-client'
import {
  getReservationCancelErrorKind,
  reservationCancelApi,
  type ReservationCancelApi,
} from './reservation-cancel.api'
import './reservation-cancel-page.css'

const ACTIVE_STATUSES = new Set(['pending_admin_approval', 'pending_payment', 'confirmed'])
const CLASS_LABELS: Record<string, string> = {
  FIRST_RIDE: '왕초보', ROUND_BEGINNER: '원형초보', ROUND_TROT: '원형 속보',
  LARGE_ARENA_BEGINNER: '대마장초보', LARGE_ARENA_TROT: '대마장 속보',
  DRESSAGE: '마장마술', JUMPING: '장애물',
}

export function ReservationCancelPage({ api = reservationCancelApi }: { api?: ReservationCancelApi }) {
  const params = useParams()
  const reservationId = Number(params.reservationId)
  const validReservationId = Number.isSafeInteger(reservationId) && reservationId > 0
  const [reason, setReason] = useState('')
  const queryClient = useQueryClient()
  const reservationsQuery = useQuery({
    queryKey: ['member', 'reservations'],
    queryFn: api.getMyReservations,
    enabled: validReservationId,
  })
  const reservation = reservationsQuery.data?.find((item) => item.reservationId === reservationId)
  const canCancel = reservation && ACTIVE_STATUSES.has(reservation.status ?? '')
  const previewQuery = useQuery({
    queryKey: ['member', 'reservation-cancel', 'preview', reservationId],
    queryFn: () => api.previewCancellation(reservationId),
    enabled: Boolean(canCancel),
    retry: false,
  })
  const cancelMutation = useMutation({
    mutationFn: () => api.cancelReservation(reservationId, reason.trim()),
    onSuccess: () => queryClient.invalidateQueries({
      queryKey: ['member', 'reservations'],
      refetchType: 'none',
    }),
  })
  const validReason = reason.trim().length > 0 && reason.trim().length <= 500

  if (!validReservationId) return <CancelState error message="예약 번호가 올바르지 않습니다." />
  if (reservationsQuery.isPending) return <CancelState message="예약 정보를 불러오는 중입니다." />
  if (reservationsQuery.isError) return <CancelState error message={cancelErrorMessage(reservationsQuery.error, '예약 정보를 불러오지 못했습니다.')} />
  if (!reservation) return <CancelState error message="취소할 예약을 찾을 수 없습니다." />
  if (!canCancel) return <CancelState error message="현재 상태에서는 예약을 취소할 수 없습니다." />

  return (
    <main className="reservation-cancel-page">
      <div className="reservation-cancel-shell">
        <header className="reservation-cancel-header">
          <div><p>CANCEL LESSON</p><h1>예약 취소</h1><span>실행 시점의 서버 판정이 최종 쿠폰 처리에 적용됩니다.</span></div>
          <Link to="/my/reservations">내 예약</Link>
        </header>

        <section className="reservation-cancel-summary" aria-label="취소할 예약">
          <h2>취소할 예약</h2>
          <dl>
            <div><dt>클래스</dt><dd>{CLASS_LABELS[reservation.classType ?? ''] ?? reservation.classType ?? '-'}</dd></div>
            <div><dt>날짜</dt><dd>{formatDate(reservation.lessonDate)}</dd></div>
            <div><dt>시간</dt><dd>{reservation.startTime?.slice(0, 5) ?? '-'}</dd></div>
          </dl>
        </section>

        {previewQuery.isPending ? <p className="reservation-cancel-inline-state">취소 정책을 확인하는 중입니다.</p> : null}
        {previewQuery.isError ? <p className="reservation-cancel-error" role="alert">{cancelErrorMessage(previewQuery.error, '취소 예상 결과를 확인하지 못했습니다.')}</p> : null}
        {previewQuery.data ? (
          <section className="reservation-cancel-preview" aria-live="polite">
            <h2>취소 전 확인</h2>
            <dl>
              <div><dt>취소 기준</dt><dd>{timingLabel(previewQuery.data.timing)}</dd></div>
              <div><dt>취소 책임</dt><dd>회원 사유</dd></div>
              <div><dt>예상 쿠폰 처리</dt><dd>{couponActionLabel(previewQuery.data.couponAction)}</dd></div>
            </dl>
            <p>관리자 판단으로 예외 반환이 필요한 경우 직접 취소하지 말고 관리자에게 연락해 주세요.</p>
          </section>
        ) : null}

        <section className="reservation-cancel-reason">
          <label htmlFor="cancel-reason">취소 사유 <span>{reason.length}/500</span></label>
          <textarea id="cancel-reason" required maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} aria-describedby="cancel-reason-help" />
          <small id="cancel-reason-help">취소 사유를 한 글자 이상 입력해 주세요.</small>
        </section>

        {cancelMutation.isError ? <p className="reservation-cancel-error" role="alert">{cancelErrorMessage(cancelMutation.error, '예약을 취소하지 못했습니다.')}</p> : null}
        {cancelMutation.data ? <CancelResult response={cancelMutation.data} /> : (
          <div className="reservation-cancel-actions">
            <Link to="/my/reservations">취소하지 않기</Link>
            <button type="button" disabled={!previewQuery.data || !validReason || cancelMutation.isPending} onClick={() => cancelMutation.mutate()}>{cancelMutation.isPending ? '처리 중' : '예약 취소 확정'}</button>
          </div>
        )}
      </div>
    </main>
  )
}

function CancelResult({ response }: { response: ReservationCancelResponse }) {
  return (
    <section className="reservation-cancel-result" aria-live="polite">
      <strong>예약이 취소되었습니다</strong>
      <p>상태 {response.status === 'cancelled' ? '예약 취소' : response.status}</p>
      <span>최종 쿠폰 처리 {couponActionLabel(response.couponAction)}</span>
      <small>취소 시각 {formatDateTime(response.cancelledAt)}</small>
      <Link to="/my/reservations">취소된 예약 확인</Link>
    </section>
  )
}

function CancelState({ message, error = false }: { message: string; error?: boolean }) {
  return <main className="reservation-cancel-page"><div className="reservation-cancel-shell"><section className="reservation-cancel-state" role={error ? 'alert' : undefined}><p>{message}</p><Link to="/my/reservations">내 예약으로 돌아가기</Link></section></div></main>
}

function cancelErrorMessage(error: unknown, fallback: string) {
  const kind = getReservationCancelErrorKind(error)
  if (kind === 'unauthorized') return '회원 인증을 확인할 수 없습니다. 다시 로그인해 주세요.'
  if (kind === 'validation') return '예약 또는 취소 사유를 다시 확인해 주세요.'
  if (kind === 'conflict') return '예약 상태가 변경되어 취소할 수 없습니다. 내 예약을 다시 확인해 주세요.'
  return `${fallback} 잠시 후 다시 시도해 주세요.`
}

function timingLabel(timing?: string) {
  const normalized = timing?.toUpperCase()
  if (normalized === 'BEFORE_CUTOFF') return '취소 마감 전'
  if (normalized?.startsWith('AFTER_CUTOFF')) return '취소 마감 후'
  return timing ?? '-'
}

function couponActionLabel(action?: string) {
  if (action === 'DEDUCT' || action === 'deduct') return '1회 차감'
  if (action === 'RETURN' || action === 'return') return '쿠폰 반환'
  if (action === 'NONE' || action === 'none') return '처리 없음'
  return action ?? '-'
}

function formatDate(date?: Date) {
  if (!date) return '-'
  return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: 'long', day: 'numeric', weekday: 'short' }).format(date)
}

function formatDateTime(date?: Date) {
  if (!date) return '-'
  return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date)
}
