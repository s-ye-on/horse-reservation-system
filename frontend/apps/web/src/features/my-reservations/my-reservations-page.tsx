import { useQuery } from '@tanstack/react-query'
import { useRef, useState, type KeyboardEvent } from 'react'
import { Link } from 'react-router'
import type { MemberReservationResponse, ReservationActionAvailabilityResponse } from '@horse/api-client'
import { isMyReservationsUnauthorized, myReservationsApi, type MyReservationsApi } from './my-reservations.api'
import './my-reservations-page.css'

const STATUS_META: Record<string, { label: string; description: string; tone: string }> = {
  pending_admin_approval: { label: '관리자 승인대기', description: '정원을 점유한 상태로 관리자 확인을 기다리고 있습니다.', tone: 'waiting' },
  pending_payment: { label: '입금 확인 대기', description: '입금 확인 전까지 정원을 점유합니다.', tone: 'waiting' },
  payment_expired: { label: '입금 기한 만료', description: '입금 확인이 늦었다면 관리자에게 연락해 주세요.', tone: 'attention' },
  approval_expired: { label: '승인 기한 만료', description: '수업 시작 전 승인되지 않아 예약이 만료되었습니다.', tone: 'attention' },
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

const RESERVATION_TABS = [
  { value: 'UPCOMING', label: '예정 예약' },
  { value: 'PAST', label: '지난 예약' },
] as const

const UPCOMING_FILTERS = [
  { value: 'all', label: '전체' },
  { value: 'pending_admin_approval', label: '승인대기' },
  { value: 'pending_payment', label: '입금대기' },
  { value: 'confirmed', label: '예약 확정' },
] as const

type ReservationTab = typeof RESERVATION_TABS[number]['value']
type UpcomingFilter = typeof UPCOMING_FILTERS[number]['value']

export function MyReservationsPage({ api = myReservationsApi }: { api?: MyReservationsApi }) {
  const [activeTab, setActiveTab] = useState<ReservationTab>('UPCOMING')
  const [upcomingFilter, setUpcomingFilter] = useState<UpcomingFilter>('all')
  const tabRefs = useRef<Array<HTMLButtonElement | null>>([])
  const query = useQuery({ queryKey: ['member', 'reservations'], queryFn: api.getMyReservations })
  if (query.isPending) return <ReservationsState message="내 예약을 불러오는 중입니다." />
  if (query.isError) return <ReservationsState error message={isMyReservationsUnauthorized(query.error) ? '회원 인증을 확인할 수 없습니다. 다시 로그인해 주세요.' : '예약 내역을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'} />

  const groupedReservations = query.data.filter((reservation) => reservation.displayGroup === activeTab)
  const visibleReservations = activeTab === 'UPCOMING' && upcomingFilter !== 'all'
    ? groupedReservations.filter((reservation) => reservation.status === upcomingFilter)
    : groupedReservations

  const selectTab = (tab: ReservationTab) => {
    setActiveTab(tab)
    setUpcomingFilter('all')
  }

  const handleTabKeyDown = (event: KeyboardEvent<HTMLButtonElement>, index: number) => {
    let nextIndex = index
    if (event.key === 'ArrowRight') nextIndex = (index + 1) % RESERVATION_TABS.length
    else if (event.key === 'ArrowLeft') nextIndex = (index - 1 + RESERVATION_TABS.length) % RESERVATION_TABS.length
    else if (event.key === 'Home') nextIndex = 0
    else if (event.key === 'End') nextIndex = RESERVATION_TABS.length - 1
    else return

    event.preventDefault()
    const nextTab = RESERVATION_TABS[nextIndex]
    selectTab(nextTab.value)
    tabRefs.current[nextIndex]?.focus()
  }

  return (
    <main className="my-reservations-page">
      <div className="my-reservations-shell">
        <header className="my-reservations-header">
          <div><p>MY LESSONS</p><h1>내 예약</h1><span>예정된 수업과 지난 예약을 나누어 확인할 수 있습니다.</span></div>
          <Link to="/reservations">새 예약</Link>
        </header>
        <div className="my-reservations-tabs" role="tablist" aria-label="예약 기간">
          {RESERVATION_TABS.map((tab, index) => (
            <button
              key={tab.value}
              ref={(element) => { tabRefs.current[index] = element }}
              id={`reservation-tab-${tab.value.toLowerCase()}`}
              type="button"
              role="tab"
              aria-selected={activeTab === tab.value}
              aria-controls={`reservation-panel-${tab.value.toLowerCase()}`}
              tabIndex={activeTab === tab.value ? 0 : -1}
              onClick={() => selectTab(tab.value)}
              onKeyDown={(event) => handleTabKeyDown(event, index)}
            >
              {tab.label}
              <span>{query.data.filter((reservation) => reservation.displayGroup === tab.value).length}</span>
            </button>
          ))}
        </div>
        <section
          id={`reservation-panel-${activeTab.toLowerCase()}`}
          className="my-reservations-panel"
          role="tabpanel"
          aria-labelledby={`reservation-tab-${activeTab.toLowerCase()}`}
          tabIndex={0}
        >
          {activeTab === 'UPCOMING' ? (
            <div className="my-reservations-filters" aria-label="예정 예약 상태 필터">
              {UPCOMING_FILTERS.map((filter) => (
                <button
                  key={filter.value}
                  type="button"
                  aria-pressed={upcomingFilter === filter.value}
                  onClick={() => setUpcomingFilter(filter.value)}
                >
                  {filter.label}
                </button>
              ))}
            </div>
          ) : null}
          {visibleReservations.length === 0 ? (
            <ReservationsState embedded message={query.data.length === 0 ? '아직 예약 내역이 없습니다.' : `${activeTab === 'UPCOMING' ? '예정' : '지난'} 예약이 없습니다.`} />
          ) : (
            <div className="my-reservations-list" aria-label={`${activeTab === 'UPCOMING' ? '예정' : '지난'} 예약 목록`}>
              {visibleReservations.map((reservation) => <ReservationItem key={reservation.reservationId} reservation={reservation} />)}
            </div>
          )}
        </section>
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
      {reservation.reservationId ? <ReservationActions reservation={reservation} /> : null}
    </article>
  )
}

function ReservationActions({ reservation }: { reservation: MemberReservationResponse }) {
  const change = reservation.actions?.change
  const cancel = reservation.actions?.cancel
  const blockedReason = actionBlockedReason(change, cancel)
  const reasonId = `reservation-${reservation.reservationId}-action-reason`

  if (!change?.allowed && !cancel?.allowed) {
    return <p id={reasonId} className="my-reservation-action-reason">{blockedReason}</p>
  }

  return (
    <div className="my-reservation-action-section">
      <div className="my-reservation-actions">
        {change?.allowed ? (
          <Link to={`/my/reservations/${reservation.reservationId}/change`}>예약 변경</Link>
        ) : (
          <button type="button" disabled aria-describedby={reasonId}>예약 변경</button>
        )}
        {cancel?.allowed ? (
          <Link to={`/my/reservations/${reservation.reservationId}/cancel`}>취소하기</Link>
        ) : (
          <button type="button" disabled aria-describedby={reasonId}>취소하기</button>
        )}
      </div>
      {blockedReason ? <p id={reasonId} className="my-reservation-action-reason">{blockedReason}</p> : null}
    </div>
  )
}

function actionBlockedReason(
  change?: ReservationActionAvailabilityResponse,
  cancel?: ReservationActionAvailabilityResponse,
) {
  const reason = change?.blockedReason ?? cancel?.blockedReason
  if (!reason) return '예약 변경 및 취소 가능 여부를 확인할 수 없습니다.'
  if (reason === 'RESERVATION_LESSON_ALREADY_STARTED') return '수업 시작 이후에는 예약을 변경하거나 취소할 수 없습니다.'
  if (reason === 'RESERVATION_INVALID_STATUS') return '현재 예약 상태에서는 변경하거나 취소할 수 없습니다.'
  if (reason === 'RESERVATION_CHANGE_NOT_ALLOWED') return '현재 예약 조건에서는 변경할 수 없습니다.'
  if (reason === 'RESERVATION_WEEKEND_SAME_DAY_CHANGE_NOT_ALLOWED') return '주말 수업은 당일 다른 시간으로 변경할 수 없습니다.'
  return '현재 예약은 변경하거나 취소할 수 없습니다.'
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
