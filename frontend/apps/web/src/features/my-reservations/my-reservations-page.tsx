import { useQueries, useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState, type KeyboardEvent } from 'react'
import { Link } from 'react-router'
import type { MemberReservationResponse, ReservationActionAvailabilityResponse } from '@horse/api-client'
import { isMyReservationsUnauthorized, myReservationsApi, type MyReservationsApi } from './my-reservations.api'
import './my-reservations-page.css'

const STATUS_META: Record<string, { label: string; description: string; tone: string }> = {
  pending_admin_approval: { label: '관리자 승인대기', description: '관리자 확인 후 예약 결과가 확정됩니다.', tone: 'waiting' },
  pending_payment: { label: '입금 확인 대기', description: '안내된 기한까지 입금해 주세요.', tone: 'waiting' },
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
  CANTER_BEGINNER: '구보초보', CANTER: '구보',
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
const PAGE_SIZE = 20
const COUNT_PAGE_SIZE = 1

export function MyReservationsPage({ api = myReservationsApi }: { api?: MyReservationsApi }) {
  const [activeTab, setActiveTab] = useState<ReservationTab>('UPCOMING')
  const [upcomingFilter, setUpcomingFilter] = useState<UpcomingFilter>('all')
  const [pages, setPages] = useState<Record<ReservationTab, number>>({ UPCOMING: 0, PAST: 0 })
  const tabRefs = useRef<Array<HTMLButtonElement | null>>([])
  const status = activeTab === 'UPCOMING' && upcomingFilter !== 'all' ? upcomingFilter : undefined
  const page = pages[activeTab]
  const query = useQuery({
    queryKey: ['member', 'reservations', activeTab, status ?? 'all', page],
    queryFn: () => api.getMyReservations({ displayGroup: activeTab, status, page, size: PAGE_SIZE }),
    placeholderData: (previousData, previousQuery) => {
      const previousKey = previousQuery?.queryKey
      return previousKey?.[2] === activeTab && previousKey?.[3] === (status ?? 'all')
        ? previousData
        : undefined
    },
  })
  const countQueries = useQueries({
    queries: RESERVATION_TABS.map((tab) => ({
      queryKey: ['member', 'reservations', 'count', tab.value],
      queryFn: () => api.getMyReservations({
        displayGroup: tab.value,
        page: 0,
        size: COUNT_PAGE_SIZE,
      }),
    })),
  })
  const totalPages = query.data?.totalPages ?? 0
  const visibleReservations = query.data?.content ?? []

  useEffect(() => {
    if (totalPages === 0 && page !== 0) {
      setPages((current) => ({ ...current, [activeTab]: 0 }))
    } else if (totalPages > 0 && page >= totalPages) {
      setPages((current) => ({ ...current, [activeTab]: totalPages - 1 }))
    }
  }, [activeTab, page, totalPages])

  const retryAllQueries = () => {
    void Promise.all([query.refetch(), ...countQueries.map((countQuery) => countQuery.refetch())])
  }

  if (query.isError) {
    return (
      <ReservationsState
        error
        message={isMyReservationsUnauthorized(query.error) ? '회원 인증을 확인할 수 없습니다. 다시 로그인해 주세요.' : '예약 내역을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'}
        onRetry={retryAllQueries}
      />
    )
  }

  const tabCounts = Object.fromEntries(RESERVATION_TABS.map((tab, index) => [
    tab.value,
    countQueries[index].data?.totalElements,
  ])) as Record<ReservationTab, number | undefined>
  const countsAvailable = Object.values(tabCounts).every((count) => count !== undefined)
  const totalReservations = countsAvailable
    ? (tabCounts.UPCOMING ?? 0) + (tabCounts.PAST ?? 0)
    : undefined
  const countQueryFailed = countQueries.some((countQuery) => countQuery.isError)

  const selectTab = (tab: ReservationTab) => {
    setActiveTab(tab)
    setUpcomingFilter('all')
    setPages((current) => ({ ...current, [tab]: 0 }))
  }

  const selectUpcomingFilter = (filter: UpcomingFilter) => {
    setUpcomingFilter(filter)
    setPages((current) => ({ ...current, UPCOMING: 0 }))
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
              <span>{tabCounts[tab.value] ?? '—'}</span>
            </button>
          ))}
        </div>
        {countQueryFailed ? (
          <div className="my-reservations-count-error" role="alert">
            <span>예약 건수를 불러오지 못했습니다.</span>
            <button
              type="button"
              onClick={() => {
                void Promise.all(countQueries
                  .filter((countQuery) => countQuery.isError)
                  .map((countQuery) => countQuery.refetch()))
              }}
            >
              다시 시도
            </button>
          </div>
        ) : null}
        <section
          id={`reservation-panel-${activeTab.toLowerCase()}`}
          className="my-reservations-panel"
          role="tabpanel"
          aria-labelledby={`reservation-tab-${activeTab.toLowerCase()}`}
          aria-busy={query.isFetching}
          tabIndex={0}
        >
          {activeTab === 'UPCOMING' ? (
            <div className="my-reservations-filters" aria-label="예정 예약 상태 필터">
              {UPCOMING_FILTERS.map((filter) => (
                <button
                  key={filter.value}
                  type="button"
                  aria-pressed={upcomingFilter === filter.value}
                  onClick={() => selectUpcomingFilter(filter.value)}
                >
                  {filter.label}
                </button>
              ))}
            </div>
          ) : null}
          {query.isPending ? (
            <ReservationsState embedded message="예약 목록을 불러오는 중입니다." />
          ) : visibleReservations.length === 0 ? (
            <ReservationsState
              embedded
              message={totalReservations === 0
                ? '아직 예약 내역이 없습니다.'
                : `${activeTab === 'UPCOMING' ? '예정' : '지난'} 예약이 없습니다.`}
            />
          ) : (
            <div className="my-reservations-list" aria-label={`${activeTab === 'UPCOMING' ? '예정' : '지난'} 예약 목록`}>
              {visibleReservations.map((reservation) => <ReservationItem key={reservation.reservationId} reservation={reservation} />)}
            </div>
          )}
          {totalPages > 0 ? (
            <nav className="my-reservations-pagination" aria-label={`${activeTab === 'UPCOMING' ? '예정' : '지난'} 예약 페이지`}>
              <button
                type="button"
                disabled={page === 0 || query.isFetching}
                onClick={() => setPages((current) => ({ ...current, [activeTab]: current[activeTab] - 1 }))}
              >이전</button>
              <span aria-live="polite">{page + 1} / {totalPages} 페이지</span>
              <button
                type="button"
                disabled={page + 1 >= totalPages || !query.data?.hasNext || query.isFetching}
                onClick={() => setPages((current) => ({ ...current, [activeTab]: current[activeTab] + 1 }))}
              >다음</button>
            </nav>
          ) : null}
          {query.isFetching ? <p className="my-reservations-page-loading" role="status">페이지 이동 중입니다.</p> : null}
        </section>
      </div>
    </main>
  )
}

function ReservationItem({ reservation }: { reservation: MemberReservationResponse }) {
  const status = STATUS_META[reservation.status ?? ''] ?? { label: '상태 확인 필요', description: '예약 상태를 확인해 주세요.', tone: 'muted' }
  const classLabel = CLASS_LABELS[reservation.classType ?? ''] ?? '수업 종류 확인 필요'
  return (
    <article className="my-reservation-card">
      <div className="my-reservation-primary">
        <div className="my-reservation-card-heading">
          <div><p>{formatDate(reservation.lessonDate)}</p><h2>{classLabel}</h2><span>{reservation.startTime?.slice(0, 5) ?? '-'}</span></div>
          <span className={`my-reservation-status ${status.tone}`}>{status.label}</span>
        </div>
        <p className="my-reservation-description">{status.description}</p>
      </div>
      <div className="my-reservation-support">
        <dl className="my-reservation-details">
          <div><dt>예약 번호</dt><dd>#{reservation.reservationId}</dd></div>
          <div><dt>결제 방식</dt><dd>{reservation.paymentSource === 'coupon' ? '쿠폰' : '1회 결제'}</dd></div>
          {reservation.paymentDueAt ? <div><dt>입금 기한</dt><dd>{formatDateTime(reservation.paymentDueAt)}</dd></div> : null}
        </dl>
        {reservation.coupon ? (
          <div className="my-reservation-coupon">
            <strong>{reservation.status === 'pending_admin_approval' || reservation.status === 'confirmed' ? '사용 예정 쿠폰' : '연결 쿠폰'} #{reservation.coupon.couponId}</strong>
            <span>잔여 {reservation.coupon.remainingCount ?? 0}회 · 현재 사용 가능 {reservation.coupon.availableCount ?? 0}회</span>
            {reservation.coupon.expiresAt ? <small>만료 {formatDate(reservation.coupon.expiresAt)}</small> : null}
          </div>
        ) : null}
        {reservation.rejectionReason ? <p className="my-reservation-notice"><strong>반려 사유</strong>{reservation.rejectionReason}</p> : null}
        {reservation.couponAction ? <p className="my-reservation-notice"><strong>최종 쿠폰 처리</strong>{couponActionLabel(reservation.couponAction)}</p> : null}
      </div>
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
  return '처리 결과 확인 필요'
}

function formatDate(date?: Date) {
  if (!date) return '-'
  return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: 'long', day: 'numeric', weekday: 'short' }).format(date)
}

function formatDateTime(date: Date) {
  return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date)
}

function ReservationsState({
  message,
  error = false,
  embedded = false,
  onRetry,
}: {
  message: string
  error?: boolean
  embedded?: boolean
  onRetry?: () => void
}) {
  const content = (
    <section className="my-reservations-state" role={error ? 'alert' : undefined}>
      <p>{message}</p>
      {onRetry ? <button type="button" onClick={onRetry}>다시 시도</button> : null}
    </section>
  )
  return embedded ? content : <main className="my-reservations-page"><div className="my-reservations-shell">{content}</div></main>
}
