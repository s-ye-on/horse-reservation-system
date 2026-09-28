import { useQuery } from '@tanstack/react-query'
import type { MemberReservationResponse } from '@horse/api-client'
import { Link } from 'react-router'
import { myCouponsApi, type MyCouponsApi } from '../my-coupons/my-coupons.api'
import { myReservationsApi, type MyReservationsApi } from '../my-reservations/my-reservations.api'
import './member-home-page.css'

const RESERVATION_STATUS: Record<string, { label: string; message: string; tone: string }> = {
  pending_admin_approval: {
    label: '관리자 승인대기',
    message: '관리자 확인을 기다리고 있습니다.',
    tone: 'waiting',
  },
  pending_payment: {
    label: '입금 확인 대기',
    message: '입금 확인이 완료되면 예약이 확정됩니다.',
    tone: 'waiting',
  },
  payment_expired: {
    label: '입금 기한 만료',
    message: '필요한 후속 조치는 내 예약에서 확인해 주세요.',
    tone: 'attention',
  },
  approval_expired: {
    label: '승인 기한 만료',
    message: '예약 처리 결과를 내 예약에서 확인해 주세요.',
    tone: 'attention',
  },
  confirmed: {
    label: '예약 확정',
    message: '예정된 수업입니다.',
    tone: 'confirmed',
  },
  completed: { label: '수업 완료', message: '수업이 완료되었습니다.', tone: 'done' },
  rejected: { label: '예약 반려', message: '반려 사유를 내 예약에서 확인해 주세요.', tone: 'attention' },
  cancelled: { label: '예약 취소', message: '취소된 예약입니다.', tone: 'muted' },
  no_show: { label: '노쇼', message: '처리 결과를 내 예약에서 확인해 주세요.', tone: 'attention' },
}

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

const COUPON_PAGE_SIZE = 20

interface MemberHomePageProps {
  reservationsApi?: MyReservationsApi
  couponsApi?: MyCouponsApi
}

export function MemberHomePage({
  reservationsApi = myReservationsApi,
  couponsApi = myCouponsApi,
}: MemberHomePageProps) {
  const reservationQuery = useQuery({
    queryKey: ['member', 'reservations', 'count', 'UPCOMING'],
    queryFn: () => reservationsApi.getMyReservations({
      displayGroup: 'UPCOMING',
      page: 0,
      size: 1,
    }),
  })
  const couponQuery = useQuery({
    queryKey: ['member', 'coupons', 0],
    queryFn: () => couponsApi.getCoupons(0, COUPON_PAGE_SIZE),
  })

  const nearestReservation = reservationQuery.data?.content[0]

  return (
    <main className="member-home-page">
      <header className="member-home-heading">
        <p>MEMBER HOME</p>
        <h1>회원 홈</h1>
        <span>가장 가까운 예약과 쿠폰 현황을 확인하세요.</span>
      </header>

      <section className="member-home-overview" aria-label="예약 및 쿠폰 요약">
        <div className="member-home-reservation" aria-busy={reservationQuery.isPending}>
          <span className="member-home-eyebrow">가장 가까운 예약</span>
          {reservationQuery.isPending ? (
            <HomePanelState message="예정 예약을 불러오는 중입니다." />
          ) : reservationQuery.isError ? (
            <HomePanelState
              error
              message="예정 예약을 불러오지 못했습니다."
              onRetry={() => { void reservationQuery.refetch() }}
            />
          ) : nearestReservation ? (
            <NearestReservation reservation={nearestReservation} />
          ) : (
            <div className="member-home-empty">
              <h2>예정된 수업이 없습니다</h2>
              <p>예약 가능한 수업을 확인하고 다음 기승을 준비해 보세요.</p>
              <Link className="member-home-primary-link" to="/reservations">수업 예약하기</Link>
            </div>
          )}
        </div>

        <aside className="member-home-coupon" aria-labelledby="member-home-coupon-title" aria-busy={couponQuery.isPending}>
          <span className="member-home-eyebrow">쿠폰 요약</span>
          <h2 id="member-home-coupon-title">내 쿠폰</h2>
          {couponQuery.isPending ? (
            <HomePanelState message="쿠폰 현황을 불러오는 중입니다." />
          ) : couponQuery.isError ? (
            <HomePanelState
              error
              message="쿠폰 현황을 불러오지 못했습니다."
              onRetry={() => { void couponQuery.refetch() }}
            />
          ) : (
            <div className="member-home-coupon-summary">
              <strong>{couponQuery.data?.totalElements ?? 0}<small>장</small></strong>
              <p>현재 계정에 등록된 쿠폰 수입니다. 잔여 횟수와 사용 내역은 내 쿠폰에서 확인할 수 있습니다.</p>
              <Link className="member-home-secondary-link" to="/my/coupons">내 쿠폰 확인</Link>
            </div>
          )}
        </aside>
      </section>

      <section className="member-home-quick-section" aria-labelledby="member-home-quick-title">
        <div className="member-home-section-heading">
          <p>QUICK MENU</p>
          <h2 id="member-home-quick-title">빠른 메뉴</h2>
        </div>
        <div className="member-home-quick-grid">
          <QuickLink
            to="/reservations"
            eyebrow="Lesson Booking"
            title="수업 예약"
            description="예약 가능한 수업 종류와 시간, 잔여석을 확인하고 신청합니다."
            action="수업 일정 보기"
          />
          <QuickLink
            to="/my/reservations"
            eyebrow="My Schedule"
            title="내 예약"
            description="예정된 수업과 지난 예약을 확인하고 가능한 변경·취소 절차로 이동합니다."
            action="예약 현황 보기"
          />
          <QuickLink
            to="/my/coupons"
            eyebrow="My Coupons"
            title="내 쿠폰"
            description="보유 쿠폰의 잔여 횟수와 사용 내역을 확인합니다."
            action="쿠폰 내역 보기"
          />
        </div>
      </section>
    </main>
  )
}

function NearestReservation({ reservation }: { reservation: MemberReservationResponse }) {
  const status = RESERVATION_STATUS[reservation.status] ?? {
    label: '상태 확인 필요',
    message: '현재 예약 상태는 내 예약에서 확인해 주세요.',
    tone: 'muted',
  }
  const classLabel = CLASS_LABELS[reservation.classType] ?? '수업'
  const canChange = reservation.actions?.change?.allowed === true
  const canCancel = reservation.actions?.cancel?.allowed === true

  return (
    <article className="member-home-reservation-content">
      <h2>{formatDate(reservation.lessonDate)}</h2>
      <div className="member-home-reservation-meta">
        <span>수업 종류 <strong>{classLabel}</strong></span>
        <span>수업 시간 <strong>{reservation.startTime.slice(0, 5)}</strong></span>
      </div>
      <div className="member-home-status-row">
        <span className={`member-home-status ${status.tone}`}>{status.label}</span>
        <span>{status.message}</span>
      </div>
      <div className="member-home-notice">
        <strong>안내 사항</strong>
        <p>변경·취소 가능 여부와 처리 결과는 내 예약에서 확인한 뒤 진행해 주세요.</p>
      </div>
      <div className="member-home-reservation-actions">
        {canChange ? <Link to={`/my/reservations/${reservation.reservationId}/change`}>예약 변경</Link> : null}
        {canCancel ? <Link className="danger" to={`/my/reservations/${reservation.reservationId}/cancel`}>예약 취소</Link> : null}
        <Link className="details" to="/my/reservations">내 예약 확인</Link>
      </div>
    </article>
  )
}

function QuickLink({
  to,
  eyebrow,
  title,
  description,
  action,
}: {
  to: string
  eyebrow: string
  title: string
  description: string
  action: string
}) {
  return (
    <Link className="member-home-quick-link" to={to}>
      <span className="member-home-eyebrow">{eyebrow}</span>
      <h3>{title}</h3>
      <p>{description}</p>
      <strong>{action} <span aria-hidden="true">→</span></strong>
    </Link>
  )
}

function HomePanelState({
  message,
  error = false,
  onRetry,
}: {
  message: string
  error?: boolean
  onRetry?: () => void
}) {
  return (
    <div className={`member-home-panel-state${error ? ' error' : ''}`} role={error ? 'alert' : 'status'}>
      <p>{message}</p>
      {onRetry ? <button type="button" onClick={onRetry}>다시 시도</button> : null}
    </div>
  )
}

function formatDate(date: Date) {
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    weekday: 'short',
  }).format(date)
}
