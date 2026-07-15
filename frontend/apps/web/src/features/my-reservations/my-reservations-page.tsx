import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import type { MemberReservationResponse } from '@horse/api-client'
import { isMyReservationsUnauthorized, myReservationsApi, type MyReservationsApi } from './my-reservations.api'
import './my-reservations-page.css'

const STATUS_META: Record<string, { label: string; description: string; tone: string }> = {
  pending_admin_approval: { label: '관리자 승인대기', description: '정원을 점유한 상태로 관리자 확인을 기다리고 있습니다.', tone: 'waiting' },
  pending_payment: { label: '입금 확인 대기', description: '입금 확인 전까지 정원을 점유합니다.', tone: 'waiting' },
  payment_expired: { label: '입금 기한 만료', description: '입금 확인이 늦었다면 관리자에게 연락해 주세요.', tone: 'attention' },
  confirmed: { label: '예약 확정', description: '관리자가 예약을 확정했습니다.', tone: 'confirmed' },
  completed: { label: '수업 완료', description: '수업과 쿠폰 처리가 완료되었습니다.', tone: 'done' },
  rejected: { label: '예약 반려', description: '관리자가 예약 신청을 승인하지 않았습니다.', tone: 'attention' },
  cancelled: { label: '예약 취소', description: '접수된 예약이 취소되었습니다.', tone: 'muted' },
  no_show: { label: '노쇼', description: '노쇼 처리 결과를 확인해 주세요.', tone: 'attention' },
}

const CLASS_LABELS: Record<string, string> = {
  FIRST_RIDE: '왕초보', ROUND_BEGINNER: '원형초보', ROUND_TROT: '원형 속보',
  LARGE_ARENA_BEGINNER: '대마장초보', LARGE_ARENA_TROT: '대마장 속보',
  DRESSAGE: '마장마술', JUMPING: '장애물',
}

const CHANGEABLE_STATUSES = new Set(['pending_admin_approval', 'pending_payment', 'confirmed'])

export function MyReservationsPage({ api = myReservationsApi }: { api?: MyReservationsApi }) {
  const query = useQuery({ queryKey: ['member', 'reservations'], queryFn: api.getMyReservations })
  if (query.isPending) return <ReservationsState message="내 예약을 불러오는 중입니다." />
  if (query.isError) return <ReservationsState error message={isMyReservationsUnauthorized(query.error) ? '회원 인증을 확인할 수 없습니다. 다시 로그인해 주세요.' : '예약 내역을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'} />

  return (
    <main className="my-reservations-page">
      <div className="my-reservations-shell">
        <header className="my-reservations-header">
          <div><p>MY LESSONS</p><h1>내 예약</h1><span>예정 예약부터 지난 수업까지 서버 정렬 순서대로 표시합니다.</span></div>
          <Link to="/reservations">새 예약</Link>
        </header>
        {query.data.length === 0 ? <ReservationsState embedded message="아직 예약 내역이 없습니다." /> : (
          <section className="my-reservations-list" aria-label="내 예약 목록">
            {query.data.map((reservation) => <ReservationItem key={reservation.reservationId} reservation={reservation} />)}
          </section>
        )}
      </div>
    </main>
  )
}

function ReservationItem({ reservation }: { reservation: MemberReservationResponse }) {
  const status = STATUS_META[reservation.status ?? ''] ?? { label: reservation.status ?? '-', description: '예약 상태를 확인해 주세요.', tone: 'muted' }
  return (
    <article className="my-reservation-card">
      <div className="my-reservation-card-heading">
        <div><p>{formatDate(reservation.lessonDate)}</p><h2>{CLASS_LABELS[reservation.classType ?? ''] ?? reservation.classType ?? '-'}</h2><span>{reservation.startTime?.slice(0, 5) ?? '-'}</span></div>
        <span className={`my-reservation-status ${status.tone}`}>{status.label}</span>
      </div>
      <p className="my-reservation-description">{status.description}</p>
      <dl className="my-reservation-details">
        <div><dt>예약 번호</dt><dd>#{reservation.reservationId}</dd></div>
        <div><dt>결제 방식</dt><dd>{reservation.paymentSource === 'coupon' ? '쿠폰' : '1회 결제'}</dd></div>
        {reservation.paymentDueAt ? <div><dt>입금 기한</dt><dd>{formatDateTime(reservation.paymentDueAt)}</dd></div> : null}
      </dl>
      {reservation.coupon ? (
        <div className="my-reservation-coupon">
          <strong>{reservation.status === 'pending_admin_approval' || reservation.status === 'confirmed' ? '사용 예정 쿠폰' : '연결 쿠폰'} #{reservation.coupon.couponId}</strong>
          <span>잔여 {reservation.coupon.remainingCount ?? 0}회 · 점유 후 사용 가능 {reservation.coupon.availableCount ?? 0}회</span>
          {reservation.coupon.expiresAt ? <small>만료 {formatDate(reservation.coupon.expiresAt)}</small> : null}
        </div>
      ) : null}
      {reservation.rejectionReason ? <p className="my-reservation-notice"><strong>반려 사유</strong>{reservation.rejectionReason}</p> : null}
      {reservation.couponAction ? <p className="my-reservation-notice"><strong>최종 쿠폰 처리</strong>{couponActionLabel(reservation.couponAction)}</p> : null}
      {reservation.reservationId && CHANGEABLE_STATUSES.has(reservation.status ?? '') ? (
        <div className="my-reservation-actions">
          <Link to={`/my/reservations/${reservation.reservationId}/change`}>예약 변경</Link>
        </div>
      ) : null}
    </article>
  )
}

function couponActionLabel(action: string) {
  if (action === 'deduct') return '1회 차감'
  if (action === 'return') return '반환'
  if (action === 'none') return '처리 없음'
  return action
}

function formatDate(date?: Date) {
  if (!date) return '-'
  return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: 'long', day: 'numeric', weekday: 'short' }).format(date)
}

function formatDateTime(date: Date) {
  return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date)
}

function ReservationsState({ message, error = false, embedded = false }: { message: string; error?: boolean; embedded?: boolean }) {
  const content = <section className="my-reservations-state" role={error ? 'alert' : undefined}>{message}</section>
  return embedded ? content : <main className="my-reservations-page"><div className="my-reservations-shell">{content}</div></main>
}
