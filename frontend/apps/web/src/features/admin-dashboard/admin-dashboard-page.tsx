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
  payment_expired: { label: '입금만료', description: '입금 마감이 지나 정원 점유가 해제된 예약' },
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
  CANTER_BEGINNER: '구보초보',
  CANTER: '구보',
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
  const [rangeError, setRangeError] = useState<string>()
  const [selectedStatus, setSelectedStatus] = useState<DashboardReservationStatus>('pending_admin_approval')
  const summaryQuery = useQuery({
    queryKey: ['admin', 'dashboard-summary', appliedRange],
    queryFn: () => api.getSummary(appliedRange.lessonDateFrom, appliedRange.lessonDateTo),
  })
  const resolvedRange = summaryQuery.isSuccess ? summaryDateRange(summaryQuery.data) : undefined
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
    if (draftRange.lessonDateFrom && draftRange.lessonDateTo && draftRange.lessonDateFrom > draftRange.lessonDateTo) {
      setRangeError('종료일은 시작일보다 빠를 수 없습니다.')
      document.getElementById('dashboard-date-to')?.focus()
      return
    }
    setRangeError(undefined)
    if (sameRange(draftRange, appliedRange)) void refreshSummary()
    else setAppliedRange(draftRange)
  }
  const refreshSummary = async () => {
    const response = await summaryQuery.refetch()
    if (response.isSuccess && resolvedRange && sameRange(resolvedRange, summaryDateRange(response.data))) {
      await reservationsQuery.refetch()
    }
  }
  const dateError = rangeError ?? (summaryQuery.isError && sameRange(draftRange, appliedRange)
    && getAdminDashboardErrorKind(summaryQuery.error) === 'validation' ? '조회할 날짜 범위를 다시 확인해 주세요.' : undefined)
  const summary = summaryQuery.isSuccess ? summaryQuery.data : undefined

  return (
    <main className="admin-dashboard-page">
      <div className="admin-dashboard-shell">
        <header className="admin-dashboard-header">
          <div>
            <p>DAILY OPERATIONS</p>
            <h1>관리자 운영 대시보드</h1>
            <span>수업 날짜 기준으로 예약 상태와 확인이 필요한 예약을 조회합니다.</span>
          </div>
        </header>

        <form className="admin-dashboard-filter" onSubmit={applyDateRange} noValidate aria-label="수업 날짜 조회 기간">
          <div className="admin-dashboard-date-field"><label htmlFor="dashboard-date-from">시작일</label>
            <input
              id="dashboard-date-from"
              type="date"
              value={draftRange.lessonDateFrom ?? ''}
              aria-invalid={Boolean(dateError)}
              aria-describedby={dateError ? 'dashboard-period-help dashboard-period-error' : 'dashboard-period-help'}
              onChange={(event) => { setRangeError(undefined); setDraftRange((range) => ({ ...range, lessonDateFrom: event.target.value || undefined })) }}
            />
          </div>
          <div className="admin-dashboard-date-field"><label htmlFor="dashboard-date-to">종료일</label>
            <input
              id="dashboard-date-to"
              type="date"
              value={draftRange.lessonDateTo ?? ''}
              aria-invalid={Boolean(dateError)}
              aria-describedby={dateError ? 'dashboard-period-help dashboard-period-error' : 'dashboard-period-help'}
              onChange={(event) => { setRangeError(undefined); setDraftRange((range) => ({ ...range, lessonDateTo: event.target.value || undefined })) }}
            />
          </div>
          <div className="admin-dashboard-filter-actions"><button type="submit" disabled={summaryQuery.isFetching}>기간 적용</button>
          <button type="button" className="secondary" onClick={() => {
            setDraftRange({}); setRangeError(undefined)
            if (sameRange(appliedRange, {})) void refreshSummary()
            else setAppliedRange({})
          }}>오늘</button></div>
          <p id="dashboard-period-help" className="admin-dashboard-help">날짜를 비우면 오늘, 한쪽만 입력하면 해당 하루를 조회합니다.</p>
          {dateError ? <p id="dashboard-period-error" className="admin-dashboard-field-error" role="alert">{dateError}</p> : null}
        </form>

        {summaryQuery.isPending ? <DashboardState message="운영 현황을 불러오는 중입니다." /> : null}
        {summaryQuery.isError ? <DashboardState error message={errorMessage(summaryQuery.error)} retry={() => void refreshSummary()} pending={summaryQuery.isFetching} /> : null}
        {summary ? <>
        {summaryQuery.isFetching ? <p className="admin-dashboard-help" role="status">집계를 갱신 중입니다. 기존 조회 결과를 표시하고 있습니다.</p> : null}
        <div className="admin-dashboard-period"><h2>{formatPeriod(summaryDateRange(summary))}</h2><span>수업 날짜 기준</span></div>
        <section className="admin-dashboard-metrics" aria-label="예약 상태 집계">
          <article className="admin-dashboard-total">
            <span>선택 기간 전체</span>
            <strong>{summary.totalCount ?? 0}</strong>
            <small>모든 예약 상태 포함</small>
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
        <p className="admin-dashboard-help">입금 마감은 예약 후 2시간과 수업 시작 시각 중 먼저 도래하는 시점입니다.</p>

        <section className="admin-dashboard-reservations" aria-labelledby="dashboard-reservation-title">
          <div className="admin-dashboard-section-heading">
            <div>
              <h2 id="dashboard-reservation-title">{STATUS_META[selectedStatus].label}</h2>
              <p>{STATUS_META[selectedStatus].description}</p>
            </div>
            {reservationsQuery.isSuccess ? <span className="admin-dashboard-list-count">불러온 예약 {reservationsQuery.data.length}건 · 최대 100건 표시</span> : null}
          </div>
          <p className="admin-dashboard-help">집계와 목록은 각각 조회한 결과로, 조회 사이의 변경에 따라 건수가 다를 수 있습니다.</p>

          {reservationsQuery.isPending ? <DashboardState message="선택한 예약을 불러오는 중입니다." /> : null}
          {reservationsQuery.isFetching && !reservationsQuery.isPending ? <p className="admin-dashboard-help" role="status">예약 목록을 갱신 중입니다. 기존 조회 결과를 표시하고 있습니다.</p> : null}
          {reservationsQuery.isError ? <DashboardState error message={errorMessage(reservationsQuery.error)} retry={() => void reservationsQuery.refetch()} pending={reservationsQuery.isFetching} /> : null}
          {reservationsQuery.isSuccess && reservationsQuery.data.length === 0
            ? <DashboardState message="선택한 기간과 상태에 해당하는 예약이 없습니다." />
            : null}
          {reservationsQuery.isSuccess && reservationsQuery.data.length > 0 ? (
            <ul className="admin-dashboard-list">
              {reservationsQuery.data.map((reservation) => (
                <li key={reservation.reservationId}><DashboardReservationRow reservation={reservation} /></li>
              ))}
            </ul>
          ) : null}
        </section>
        </> : null}
        <footer className="admin-dashboard-navigation"><Link to="/admin/reservations">예약 운영 전체 보기</Link><p>승인·입금 확인·변경·취소는 예약 운영 화면에서 진행합니다.</p></footer>
      </div>
    </main>
  )
}

function DashboardReservationRow({ reservation }: { reservation: AdminReservationResponse }) {
  const warning = reservation.approvalWarning
  const warningLabel = warning ? WARNING_LABELS[warning] ?? '경고 정보 확인 필요' : undefined
  const status = STATUS_META[reservation.status as DashboardReservationStatus]?.label ?? '예약 상태 확인 필요'
  return (
    <article className="admin-dashboard-reservation-row" aria-label={`${reservation.memberName ?? '이름 없음'} 예약`}>
      <header><h3>{reservation.memberName ?? '이름 없음'}</h3><span className="admin-dashboard-status">{status}</span>
        {warningLabel ? <span className={`admin-dashboard-warning ${WARNING_LABELS[warning ?? ''] ? warning : 'unknown'}`}>{warningLabel}</span> : null}</header>
      <dl>
        <div><dt>수업</dt><dd>{CLASS_LABELS[reservation.classType ?? ''] ?? '수업 정보 확인 필요'}</dd></div>
        <div><dt>일시</dt><dd>{formatLesson(reservation.lessonDate, reservation.startTime)}</dd></div>
        <div><dt>결제</dt><dd>{reservation.paymentSource === 'coupon' ? '쿠폰 예약' : reservation.paymentSource === 'single_payment' ? '단건 결제' : '결제 정보 확인 필요'}</dd></div>
        <div><dt>연락처</dt><dd>{reservation.memberPhone ? <a href={`tel:${reservation.memberPhone}`}>{reservation.memberPhone}</a> : '전화번호 없음'}</dd></div>
      </dl>
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

function sameRange(left: DateRange, right: DateRange) {
  return left.lessonDateFrom === right.lessonDateFrom && left.lessonDateTo === right.lessonDateTo
}

function DashboardState({ message, error = false, retry, pending = false }: {
  message: string
  error?: boolean
  retry?(): void
  pending?: boolean
}) {
  return <div className={`admin-dashboard-state${error ? ' error' : ''}`} role={error ? 'alert' : 'status'}><p>{message}</p>
    {retry ? <button type="button" className="secondary" disabled={pending} onClick={retry}>{pending ? '조회 중' : '다시 조회'}</button> : null}</div>
}
