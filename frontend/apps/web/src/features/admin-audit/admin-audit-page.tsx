import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import type { AdminReservationAuditResponse } from '@horse/api-client'
import {
  adminAuditApi,
  getAdminAuditErrorKind,
  type AdminAuditApi,
  type AdminAuditFilters,
} from './admin-audit.api'
import './admin-audit-page.css'

const PAGE_SIZE = 20

const ACTOR_LABELS: Record<string, string> = {
  admin: '관리자',
  member: '회원',
  system: '시스템',
}

const CHANGE_TYPE_LABELS: Record<string, string> = {
  payment_restored: '입금만료 복구',
  no_show_processed: '노쇼 처리',
  schedule_changed: '일정 변경',
  reservation_cancelled: '예약 취소',
}

const STATUS_LABELS: Record<string, string> = {
  pending_admin_approval: '승인대기',
  pending_payment: '입금대기',
  payment_expired: '입금만료',
  confirmed: '예약확정',
  completed: '수업완료',
  rejected: '반려',
  cancelled: '취소',
  no_show: '노쇼',
}

const COUPON_ACTION_LABELS: Record<string, string> = {
  none: '처리 없음',
  free_change_used: '무료 변경권 사용',
  deduct: '쿠폰 차감',
  return: '쿠폰 반환',
}

interface AuditFilterForm {
  keyword: string
  reservationId: string
  occurredDateFrom: string
  occurredDateTo: string
  actorType: string
  changeType: string
}

const EMPTY_FILTERS: AuditFilterForm = {
  keyword: '',
  reservationId: '',
  occurredDateFrom: '',
  occurredDateTo: '',
  actorType: '',
  changeType: '',
}

function errorMessage(error: unknown) {
  const kind = getAdminAuditErrorKind(error)
  if (kind === 'forbidden') return '관리자 권한이 없어 감사 이력을 조회할 수 없습니다.'
  if (kind === 'validation') return '감사 이력 조회 조건을 다시 확인해 주세요.'
  return '감사 이력을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

export function AdminAuditPage({ api = adminAuditApi }: { api?: AdminAuditApi }) {
  const [draftFilters, setDraftFilters] = useState<AuditFilterForm>(EMPTY_FILTERS)
  const [appliedFilters, setAppliedFilters] = useState<AuditFilterForm>(EMPTY_FILTERS)
  const [page, setPage] = useState(0)
  const [localError, setLocalError] = useState<string>()
  const query = useQuery({
    queryKey: ['admin', 'audit-logs', appliedFilters, page],
    queryFn: () => api.getAuditLogs(toApiFilters(appliedFilters, page)),
    placeholderData: keepPreviousData,
  })

  useEffect(() => {
    const totalPages = query.data?.totalPages
    if (totalPages === undefined) return
    const validPage = totalPages === 0 ? 0 : Math.min(page, totalPages - 1)
    if (validPage !== page) setPage(validPage)
  }, [page, query.data?.totalPages])

  const applyFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (draftFilters.occurredDateFrom && draftFilters.occurredDateTo
      && draftFilters.occurredDateFrom > draftFilters.occurredDateTo) {
      setLocalError('시작일은 종료일보다 늦을 수 없습니다.')
      return
    }
    setLocalError(undefined)
    setAppliedFilters(normalizeFilters(draftFilters))
    setPage(0)
  }

  const resetFilters = () => {
    setDraftFilters(EMPTY_FILTERS)
    setAppliedFilters(EMPTY_FILTERS)
    setLocalError(undefined)
    setPage(0)
  }

  const totalElements = query.data?.totalElements ?? 0
  const totalPages = query.data?.totalPages ?? 0
  const content = query.data?.content ?? []

  return (
    <main className="admin-audit-page">
      <div className="admin-audit-shell">
        <header className="admin-audit-header">
          <div>
            <p>RESERVATION AUDIT</p>
            <h1>예약 감사 이력</h1>
            <span>예약 변경·취소·노쇼·복구의 처리 근거와 전후 상태를 확인합니다.</span>
          </div>
          <Link to="/admin">관리자 메뉴</Link>
        </header>

        <form className="admin-audit-filters" onSubmit={applyFilters}>
          <label className="admin-audit-keyword">회원 검색
            <input
              type="search"
              placeholder="이름 또는 전화번호"
              value={draftFilters.keyword}
              onChange={(event) => setDraftFilters((filters) => ({ ...filters, keyword: event.target.value }))}
            />
          </label>
          <label>예약 ID
            <input
              type="number"
              inputMode="numeric"
              min="1"
              placeholder="예: 152"
              value={draftFilters.reservationId}
              onChange={(event) => setDraftFilters((filters) => ({ ...filters, reservationId: event.target.value }))}
            />
          </label>
          <label>시작일
            <input
              type="date"
              value={draftFilters.occurredDateFrom}
              onChange={(event) => setDraftFilters((filters) => ({ ...filters, occurredDateFrom: event.target.value }))}
            />
          </label>
          <label>종료일
            <input
              type="date"
              value={draftFilters.occurredDateTo}
              onChange={(event) => setDraftFilters((filters) => ({ ...filters, occurredDateTo: event.target.value }))}
            />
          </label>
          <label>처리 주체
            <select
              value={draftFilters.actorType}
              onChange={(event) => setDraftFilters((filters) => ({ ...filters, actorType: event.target.value }))}
            >
              <option value="">전체</option>
              <option value="admin">관리자</option>
              <option value="member">회원</option>
              <option value="system">시스템</option>
            </select>
          </label>
          <label>변경 유형
            <select
              value={draftFilters.changeType}
              onChange={(event) => setDraftFilters((filters) => ({ ...filters, changeType: event.target.value }))}
            >
              <option value="">전체</option>
              <option value="schedule_changed">일정 변경</option>
              <option value="reservation_cancelled">예약 취소</option>
              <option value="no_show_processed">노쇼 처리</option>
              <option value="payment_restored">입금만료 복구</option>
            </select>
          </label>
          <div className="admin-audit-filter-actions">
            <button type="submit">조건 적용</button>
            <button type="button" className="secondary" onClick={resetFilters}>초기화</button>
          </div>
        </form>

        {localError ? <p className="admin-audit-error" role="alert">{localError}</p> : null}

        <section className="admin-audit-results" aria-labelledby="admin-audit-results-title">
          <div className="admin-audit-results-heading">
            <div>
              <h2 id="admin-audit-results-title">처리 이력</h2>
              <p>최신 처리부터 표시합니다.</p>
            </div>
            <strong>전체 {totalElements}건</strong>
          </div>

          {query.isPending ? <AuditState message="감사 이력을 불러오는 중입니다." /> : null}
          {query.isError ? (
            <AuditState
              error
              message={errorMessage(query.error)}
              onRetry={() => { void query.refetch() }}
            />
          ) : null}
          {query.isSuccess && content.length === 0
            ? <AuditState message="조회 조건에 해당하는 예약 감사 이력이 없습니다." />
            : null}
          {query.isSuccess && content.length > 0 ? (
            <div className="admin-audit-list">
              {content.map((auditLog) => (
                <AuditLogRow auditLog={auditLog} key={auditLog.auditLogId} />
              ))}
            </div>
          ) : null}

          {query.isSuccess && totalPages > 0 ? (
            <nav className="admin-audit-pagination" aria-label="감사 이력 페이지">
              <button type="button" disabled={page === 0 || query.isFetching} onClick={() => setPage((current) => current - 1)}>이전</button>
              <span aria-live="polite">{page + 1} / {totalPages} 페이지</span>
              <button type="button" disabled={page + 1 >= totalPages || !query.data?.hasNext || query.isFetching} onClick={() => setPage((current) => current + 1)}>다음</button>
            </nav>
          ) : null}
          {query.isFetching && !query.isPending ? <p className="admin-audit-page-loading" role="status">페이지 이동 중입니다.</p> : null}
        </section>
      </div>
    </main>
  )
}

function AuditLogRow({ auditLog }: { auditLog: AdminReservationAuditResponse }) {
  return (
    <article className="admin-audit-row">
      <div className="admin-audit-row-primary">
        <div>
          <strong>{auditLog.memberName ?? '이름 없음'}</strong>
          <span>예약 #{auditLog.reservationId ?? '-'}</span>
        </div>
        <span className="admin-audit-type">{label(CHANGE_TYPE_LABELS, auditLog.changeType)}</span>
        <time dateTime={auditLog.occurredAt?.toISOString()}>{formatOccurredAt(auditLog.occurredAt)}</time>
      </div>

      <div className="admin-audit-transition">
        <div>
          <span>이전</span>
          <strong>{label(STATUS_LABELS, auditLog.fromStatus)}</strong>
          <small>{formatSchedule(auditLog.fromLessonDate, auditLog.fromStartTime)}</small>
        </div>
        <b aria-hidden="true">→</b>
        <div>
          <span>이후</span>
          <strong>{label(STATUS_LABELS, auditLog.toStatus)}</strong>
          <small>{formatSchedule(auditLog.toLessonDate, auditLog.toStartTime)}</small>
        </div>
      </div>

      <dl className="admin-audit-meta">
        <div><dt>처리 주체</dt><dd>{label(ACTOR_LABELS, auditLog.actorType)}</dd></div>
        <div><dt>인증 주체</dt><dd>{auditLog.actorAuthSubject ?? '-'}</dd></div>
        <div><dt>쿠폰 처리</dt><dd>{label(COUPON_ACTION_LABELS, auditLog.couponAction)}</dd></div>
      </dl>
      <p className="admin-audit-memo"><span>처리 사유</span>{auditLog.memo ?? '기록된 사유 없음'}</p>
    </article>
  )
}

function toApiFilters(filters: AuditFilterForm, page: number): AdminAuditFilters {
  return {
    keyword: filters.keyword || undefined,
    reservationId: filters.reservationId ? Number(filters.reservationId) : undefined,
    occurredDateFrom: filters.occurredDateFrom || undefined,
    occurredDateTo: filters.occurredDateTo || undefined,
    actorType: filters.actorType || undefined,
    changeType: filters.changeType || undefined,
    page,
    size: PAGE_SIZE,
  }
}

function normalizeFilters(filters: AuditFilterForm): AuditFilterForm {
  return { ...filters, keyword: filters.keyword.trim(), reservationId: filters.reservationId.trim() }
}

function label(labels: Record<string, string>, value?: string) {
  return value ? labels[value] ?? value : '-'
}

function formatSchedule(date?: Date, startTime?: string) {
  if (!date) return '-'
  const dateText = date.toISOString().slice(0, 10).replaceAll('-', '.')
  return `${dateText} ${startTime?.slice(0, 5) ?? ''}`.trim()
}

function formatOccurredAt(value?: Date) {
  if (!value) return '-'
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(value)
}

function AuditState({ message, error = false, onRetry }: { message: string; error?: boolean; onRetry?: () => void }) {
  return (
    <div className="admin-audit-state" role={error ? 'alert' : 'status'}>
      <p>{message}</p>
      {onRetry ? <button type="button" onClick={onRetry}>다시 시도</button> : null}
    </div>
  )
}
