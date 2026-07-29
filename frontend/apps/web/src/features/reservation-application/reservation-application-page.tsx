import { useRef } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import type { ReservationApplicationResponse } from '@horse/api-client'
import {
  getReservationApplicationErrorKind,
  reservationApplicationApi,
  type ReservationApplicationApi,
} from './reservation-application.api'
import './reservation-application-page.css'

const CLASS_LABELS: Record<string, string> = {
  FIRST_RIDE: '왕초보', ROUND_BEGINNER: '원형초보', ROUND_TROT: '원형 속보',
  LARGE_ARENA_BEGINNER: '대마장초보', LARGE_ARENA_TROT: '대마장 속보',
  DRESSAGE: '마장마술', JUMPING: '장애물',
}

function errorMessage(error: unknown) {
  const kind = getReservationApplicationErrorKind(error)
  if (kind === 'unauthorized') return '회원 인증을 확인할 수 없습니다. 다시 로그인해 주세요.'
  if (kind === 'validation') return '선택한 예약 정보를 확인할 수 없습니다. 달력에서 다시 선택해 주세요.'
  if (kind === 'conflict') return '선택한 시간이 방금 마감되었거나 사용할 수 있는 쿠폰 상태가 변경되었습니다. 다른 시간을 선택해 주세요.'
  return '예약을 신청하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

export function ReservationApplicationPage({ api = reservationApplicationApi }: { api?: ReservationApplicationApi }) {
  const [searchParams] = useSearchParams()
  const submissionLocked = useRef(false)
  const idempotencyRequest = useRef<{
    timeSlotId: number
    classType: string
    key: string
  } | null>(null)
  const timeSlotId = Number(searchParams.get('timeSlotId'))
  const classType = searchParams.get('classType') ?? ''
  const date = searchParams.get('date') ?? ''
  const validSelection = Number.isSafeInteger(timeSlotId) && timeSlotId > 0 && Boolean(classType) && /^\d{4}-\d{2}-\d{2}$/.test(date)

  const timeSlotQuery = useQuery({
    queryKey: ['member', 'reservation-selection', date, classType, timeSlotId],
    queryFn: () => api.getSelectedTimeSlot(date, classType, timeSlotId),
    enabled: validSelection,
  })
  const application = useMutation({
    mutationFn: (key: string) => api.apply(timeSlotId, classType, key),
  })

  const submit = () => {
    if (submissionLocked.current || !timeSlotQuery.data?.reservable) return
    submissionLocked.current = true
    const previousRequest = idempotencyRequest.current
    const requestKey = previousRequest?.timeSlotId === timeSlotId
      && previousRequest.classType === classType
      ? previousRequest.key
      : crypto.randomUUID()
    idempotencyRequest.current = { timeSlotId, classType, key: requestKey }
    application.mutate(requestKey, {
      onSettled: () => {
        submissionLocked.current = false
      },
    })
  }

  if (!validSelection) return <ApplicationState error message="예약 선택 정보가 올바르지 않습니다." />
  if (timeSlotQuery.isPending) return <ApplicationState message="선택한 수업을 확인하는 중입니다." />
  if (timeSlotQuery.isError) return <ApplicationState error message={errorMessage(timeSlotQuery.error)} />
  if (!timeSlotQuery.data) return <ApplicationState error message="선택한 시간대를 찾을 수 없습니다. 달력에서 다시 선택해 주세요." />

  return (
    <main className="reservation-application-page">
      <div className="reservation-application-shell">
        <header><p>BOOKING CONFIRMATION</p><h1>예약 신청 확인</h1></header>
        <section className="reservation-application-summary">
          <h2>선택한 수업</h2>
          <dl>
            <div><dt>클래스</dt><dd>{CLASS_LABELS[classType] ?? classType}</dd></div>
            <div><dt>날짜</dt><dd>{formatDate(date)}</dd></div>
            <div><dt>시간</dt><dd>{timeSlotQuery.data.startTime?.slice(0, 5) ?? '-'}</dd></div>
            <div><dt>현재 정원</dt><dd>{timeSlotQuery.data.reservable ? `잔여 ${timeSlotQuery.data.remainingCapacity ?? 0}자리` : unavailableLabel(timeSlotQuery.data.unavailableReason)}</dd></div>
          </dl>
          <p>쿠폰이 있으면 자동으로 점유하며, 없으면 1회 결제 입금대기로 신청됩니다. 모든 예약은 관리자 확인 후 확정됩니다.</p>
        </section>

        {application.isError ? <p className="reservation-application-error" role="alert">{errorMessage(application.error)}</p> : null}
        {application.data ? <ApplicationResult response={application.data} /> : (
          <div className="reservation-application-actions">
            <Link to="/reservations">다른 시간 선택</Link>
            <button type="button" disabled={!timeSlotQuery.data.reservable || application.isPending} onClick={submit}>{application.isPending ? '신청 중' : '예약 신청'}</button>
          </div>
        )}
      </div>
    </main>
  )
}

function ApplicationResult({ response }: { response: ReservationApplicationResponse }) {
  const isCoupon = response.paymentSource === 'coupon'
  return (
    <section className="reservation-application-result" aria-live="polite">
      <span className="reservation-application-result-mark">신청 완료</span>
      <h2>{isCoupon ? '관리자 승인을 기다리고 있습니다' : '입금 확인을 기다리고 있습니다'}</h2>
      <p>예약 #{response.reservationId} · 상태 {response.status}</p>
      {isCoupon && response.coupon ? (
        <dl>
          <div><dt>선택 쿠폰</dt><dd>#{response.coupon.couponId}</dd></div>
          <div><dt>만료일</dt><dd>{response.coupon.expiresAt ? formatDateTime(response.coupon.expiresAt, false) : '첫 사용 완료 후 확정'}</dd></div>
          <div><dt>현재 잔여</dt><dd>{response.coupon.remainingCount ?? 0}회</dd></div>
          <div><dt>임시 점유</dt><dd>{response.coupon.heldCount ?? 0}회</dd></div>
          <div><dt>점유 후 사용 가능</dt><dd>{response.coupon.availableCount ?? 0}회</dd></div>
        </dl>
      ) : null}
      {!isCoupon ? (
        <div className="reservation-application-payment">
          <strong>2시간 이내 입금해 주세요</strong>
          <span>입금 마감 {response.paymentDueAt ? formatDateTime(response.paymentDueAt) : '-'}</span>
          <p>마감 후에는 예약이 자동 만료됩니다. 입금 확인이 늦어진 경우 관리자에게 연락해 주세요.</p>
        </div>
      ) : null}
      <Link to="/my/reservations">내 예약 확인</Link>
    </section>
  )
}

function unavailableLabel(reason?: string | null) {
  if (reason === 'CLOSED') return '운영 마감'
  if (reason === 'FULL') return '예약 마감'
  return '예약 불가'
}

function formatDate(date: string) {
  return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' }).format(new Date(`${date}T00:00:00+09:00`))
}

function formatDateTime(date: Date, includeTime = true) {
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: 'numeric', day: 'numeric',
    ...(includeTime ? { hour: '2-digit', minute: '2-digit' } : {}),
  }).format(date)
}

function ApplicationState({ message, error = false }: { message: string; error?: boolean }) {
  return <main className="reservation-application-page"><div className="reservation-application-shell"><section className="reservation-application-state" role={error ? 'alert' : undefined}><p>{message}</p><Link to="/reservations">예약 달력으로 돌아가기</Link></section></div></main>
}
