import { useState } from 'react'
import type { FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import type { AdminReservationResponse, AdminReservationSummaryResponse } from '@horse/api-client'
import {
  adminDashboardApi,
  getAdminDashboardErrorKind,
  type AdminDashboardApi,
  type DashboardReservationStatus,
} from './admin-dashboard.api'
import './admin-dashboard-page.css'

const STATUS_META: Record<DashboardReservationStatus, { label: string; description: string }> = {
  pending_admin_approval: { label: '쿠폰 승인대기', description: '관리자 확인을 기다리는 쿠폰 예약' },
  pending_payment: { label: '입금대기', description: '입금 확인 전 정원을 점유한 예약' },
  payment_expired: { label: '입금만료', description: '2시간이 지나 정원이 반환된 예약' },
}

const WARNING_LABELS: Record<string, string> = {
  normal: '정상',
  warning: '확인 필요',
  critical: '긴급',
}

const CLASS_LABELS: Record<string, string> = {
  FIRST_RIDE: '왕초보',
  ROUND_BEGINNER: '원형초보',
  ROUND_TROT: '원형 속보',
  LARGE_ARENA_BEGINNER: '대마장초보',
  LARGE_ARENA_TROT: '대마장 속보',
  DRESSAGE: '마장마술',
  JUMPING: '장애물',
}

interface DateRange {
  lessonDateFrom?: string
  lessonDateTo?: string
}

function errorMessage(error: unknown) {
  const kind = getAdminDashboardErrorKind(error)
  if (kind === 'forbidden') return '관리자 권한이 없어 운영 현황을 조회할 수 없습니다.'
  if (kind === 'validation') return '조회할 날짜 범위를 다시 확인해 주세요.'
  return '운영 현황을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

export function AdminDashboardPage({ api = adminDashboardApi }: { api?: AdminDashboardApi }) {
  const [draftRange, setDraftRange] = useState<DateRange>({})
  const [appliedRange, setAppliedRange] = useState<DateRange>({})
  const [selectedStatus, setSelectedStatus] = useState<DashboardReservationStatus>('pending_admin_approval')
  const summaryQuery = useQuery({
    queryKey: ['admin', 'dashboard-summary', appliedRange],
    queryFn: () => api.getSummary(appliedRange.lessonDateFrom, appliedRange.lessonDateTo),
  })
  const resolvedRange = summaryQuery.data ? summaryDateRange(summaryQuery.data) : undefined
  const reservationsQuery = useQuery({
    queryKey: ['admin', 'dashboard-reservations', selectedStatus, resolvedRange],
    queryFn: () => api.getReservations(
      selectedStatus,
      resolvedRange?.lessonDateFrom ?? '',
      resolvedRange?.lessonDateTo ?? '',
    ),
    enabled: Boolean(resolvedRange),
  })

  const applyDateRange = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setAppliedRange(draftRange)
  }

  if (summaryQuery.isPending) return <DashboardState message="운영 현황을 불러오는 중입니다." />
  if (summaryQuery.isError) return <DashboardState error message={errorMessage(summaryQuery.error)} />

  const summary = summaryQuery.data
  const period = summaryDateRange(summary)

  return (
    <main className="admin-dashboard-page">
      <div className="admin-dashboard-shell">
        <header className="admin-dashboard-header">
          <div>
            <p>DAILY OPERATIONS</p>
            <h1>관리자 운영 대시보드</h1>
            <span>{formatPeriod(period)} 예약 현황을 확인합니다.</span>
          </div>
          <Link to="/admin/reservations">예약 운영 전체 보기</Link>
        </header>

        <form className="admin-dashboard-filter" onSubmit={applyDateRange}>
          <label>시작일
            <input
              type="date"
              value={draftRange.lessonDateFrom ?? ''}
              onChange={(event) => setDraftRange((range) => ({ ...range, lessonDateFrom: event.target.value || undefined }))}
            />
          </label>
          <label>종료일
            <input
              type="date"
              value={draftRange.lessonDateTo ?? ''}
              onChange={(event) => setDraftRange((range) => ({ ...range, lessonDateTo: event.target.value || undefined }))}
            />
          </label>
          <button type="submit">기간 적용</button>
          <button type="button" className="secondary" onClick={() => { setDraftRange({}); setAppliedRange({}) }}>오늘</button>
        </form>

        <section className="admin-dashboard-metrics" aria-label="예약 상태 집계">
          <article className="admin-dashboard-total">
            <span>선택 기간 전체</span>
            <strong>{summary.totalCount ?? 0}</strong>
            <small>예약</small>
          </article>
          {(Object.keys(STATUS_META) as DashboardReservationStatus[]).map((status) => {
            const meta = STATUS_META[status]
            const selected = selectedStatus === status
            const count = statusCount(summary, status)
            return (
              <button
                type="button"
                className={selected ? 'selected' : ''}
                aria-label={`${meta.label} ${count}건`}
                aria-pressed={selected}
                key={status}
                onClick={() => setSelectedStatus(status)}
              >
                <span>{meta.label}</span>
                <strong>{count}</strong>
                <small>{meta.description}</small>
              </button>
            )
          })}
        </section>

        <section className="admin-dashboard-reservations" aria-labelledby="dashboard-reservation-title">
          <div className="admin-dashboard-section-heading">
            <div>
              <h2 id="dashboard-reservation-title">{STATUS_META[selectedStatus].label}</h2>
              <p>{STATUS_META[selectedStatus].description}</p>
            </div>
            <strong>{reservationsQuery.data?.length ?? 0}건</strong>
          </div>

          {reservationsQuery.isPending ? <DashboardState embedded message="선택한 예약을 불러오는 중입니다." /> : null}
          {reservationsQuery.isError ? <DashboardState embedded error message={errorMessage(reservationsQuery.error)} /> : null}
          {reservationsQuery.isSuccess && reservationsQuery.data.length === 0
            ? <DashboardState embedded message="선택한 기간과 상태에 해당하는 예약이 없습니다." />
            : null}
          {reservationsQuery.isSuccess && reservationsQuery.data.length > 0 ? (
            <div className="admin-dashboard-list">
              {reservationsQuery.data.map((reservation) => (
                <DashboardReservationRow reservation={reservation} key={reservation.reservationId} />
              ))}
            </div>
          ) : null}
        </section>
      </div>
    </main>
  )
}

function DashboardReservationRow({ reservation }: { reservation: AdminReservationResponse }) {
  const warning = reservation.approvalWarning
  return (
    <article className="admin-dashboard-reservation-row">
      <div>
        <strong>{reservation.memberName ?? '이름 없음'}</strong>
        <a href={`tel:${reservation.memberPhone ?? ''}`}>{reservation.memberPhone ?? '전화번호 없음'}</a>
      </div>
      <dl>
        <div><dt>수업</dt><dd>{CLASS_LABELS[reservation.classType ?? ''] ?? reservation.classType ?? '-'}</dd></div>
        <div><dt>일시</dt><dd>{formatLesson(reservation.lessonDate, reservation.startTime)}</dd></div>
        <div><dt>결제</dt><dd>{reservation.paymentSource === 'coupon' ? '쿠폰' : '1회 결제'}</dd></div>
      </dl>
      {warning ? <span className={`admin-dashboard-warning ${warning}`}>{WARNING_LABELS[warning] ?? warning}</span> : null}
    </article>
  )
}

function statusCount(summary: AdminReservationSummaryResponse, status: string) {
  return summary.statusCounts?.find((item) => item.status === status)?.count ?? 0
}

function summaryDateRange(summary: AdminReservationSummaryResponse) {
  return {
    lessonDateFrom: dateValue(summary.lessonDateFrom),
    lessonDateTo: dateValue(summary.lessonDateTo),
  }
}

function dateValue(value?: Date) {
  return value?.toISOString().slice(0, 10) ?? ''
}

function formatPeriod(range: { lessonDateFrom: string; lessonDateTo: string }) {
  return range.lessonDateFrom === range.lessonDateTo
    ? range.lessonDateFrom
    : `${range.lessonDateFrom} ~ ${range.lessonDateTo}`
}

function formatLesson(date?: Date, startTime?: string) {
  if (!date) return '-'
  const lessonDate = new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul', month: 'long', day: 'numeric', weekday: 'short',
  }).format(date)
  return `${lessonDate} ${startTime?.slice(0, 5) ?? ''}`.trim()
}

function DashboardState({ message, error = false, embedded = false }: {
  message: string
  error?: boolean
  embedded?: boolean
}) {
  const content = <section className="admin-dashboard-state" role={error ? 'alert' : 'status'}>{message}</section>
  return embedded ? content : <main className="admin-dashboard-page"><div className="admin-dashboard-shell">{content}</div></main>
}
