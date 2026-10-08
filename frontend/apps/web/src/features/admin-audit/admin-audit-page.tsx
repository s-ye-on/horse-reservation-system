import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import type { AdminReservationAuditResponse } from '@horse/api-client'
import {
  adminAuditApi,
  getAdminAuditErrorKind,
  type AdminAuditApi,
  type AdminAuditCriteria,
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
  admin_reservation_created: '관리자 예약 생성',
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
  approval_expired: '승인 만료',
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
  if (kind === 'unauthorized') return '로그인이 필요합니다.'
  if (kind === 'forbidden') return '관리자 권한이 없어 감사 이력을 조회할 수 없습니다.'
  if (kind === 'validation') return '감사 이력 조회 조건을 다시 확인해 주세요.'
  return '감사 이력을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

function downloadErrorMessage(error: unknown) {
  const kind = getAdminAuditErrorKind(error)
  if (kind === 'unauthorized') return '로그인이 필요합니다.'
  if (kind === 'forbidden') return '관리자 권한이 없어 CSV를 다운로드할 수 없습니다.'
  if (kind === 'validation') return 'CSV 다운로드 조건을 다시 확인해 주세요.'
  return 'CSV를 다운로드하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

export function AdminAuditPage({ api = adminAuditApi }: { api?: AdminAuditApi }) {
  const [draftFilters, setDraftFilters] = useState<AuditFilterForm>(EMPTY_FILTERS)
  const [appliedFilters, setAppliedFilters] = useState<AuditFilterForm>(EMPTY_FILTERS)
  const [page, setPage] = useState(0)
  const [localError, setLocalError] = useState<string>()
  const [idError, setIdError] = useState<string>()
  const [downloadError, setDownloadError] = useState<string>()
  const [downloadStatus, setDownloadStatus] = useState<string>()
  const [isDownloading, setIsDownloading] = useState(false)
  const downloadInFlight = useRef(false)
  const query = useQuery({
    queryKey: ['admin', 'audit-logs', appliedFilters, page],
    queryFn: () => api.getAuditLogs(toApiFilters(appliedFilters, page)),
    placeholderData: keepPreviousData,
  })

  useEffect(() => {
    const totalPages = query.data?.totalPages
    if (totalPages === undefined || query.isPlaceholderData || !query.isSuccess) return
    const validPage = totalPages === 0 ? 0 : Math.min(page, totalPages - 1)
    if (validPage !== page) setPage(validPage)
  }, [page, query.data?.totalPages, query.isPlaceholderData, query.isSuccess])

  const applyFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    const incompleteInput = Array.from(event.currentTarget.querySelectorAll<HTMLInputElement>('input'))
      .find((input) => input.validity.badInput)
    if (incompleteInput) {
      if (incompleteInput.id === 'audit-reservation-id') setIdError('예약 번호는 1 이상의 정수로 입력해 주세요.')
      else setLocalError('날짜를 완전하게 입력해 주세요.')
      incompleteInput.focus()
      return
    }
    const reservationId = draftFilters.reservationId.trim()
    if (reservationId && (!/^\d+$/.test(reservationId) || !Number.isSafeInteger(Number(reservationId)) || Number(reservationId) < 1)) {
      setIdError('예약 번호는 1 이상의 정수로 입력해 주세요.')
      document.getElementById('audit-reservation-id')?.focus()
      return
    }
    setIdError(undefined)
    if (draftFilters.occurredDateFrom && draftFilters.occurredDateTo
      && draftFilters.occurredDateFrom > draftFilters.occurredDateTo) {
      setLocalError('시작일은 종료일보다 늦을 수 없습니다.')
      document.getElementById('audit-date-from')?.focus()
      return
    }
    setLocalError(undefined)
    setDownloadError(undefined)
    setDownloadStatus(undefined)
    const next = normalizeFilters(draftFilters)
    if (page === 0 && JSON.stringify(next) === JSON.stringify(appliedFilters)) void query.refetch()
    setAppliedFilters(next)
    setPage(0)
  }

  const resetFilters = () => {
    setDraftFilters(EMPTY_FILTERS)
    setAppliedFilters(EMPTY_FILTERS)
    setLocalError(undefined)
    setIdError(undefined)
    setDownloadError(undefined)
    setDownloadStatus(undefined)
    if (page === 0 && JSON.stringify(appliedFilters) === JSON.stringify(EMPTY_FILTERS)) void query.refetch()
    setPage(0)
  }

  const downloadCsv = async () => {
    if (downloadInFlight.current) return
    downloadInFlight.current = true
    setIsDownloading(true)
    setDownloadError(undefined)
    setDownloadStatus(undefined)
    const context = describeFilters(appliedFilters)

    let objectUrl: string | undefined
    let downloadLink: HTMLAnchorElement | undefined
    let downloadTriggered = false
    try {
      const download = await api.downloadAuditLogs(toApiCriteria(appliedFilters))
      objectUrl = URL.createObjectURL(download.blob)
      downloadLink = document.createElement('a')
      downloadLink.href = objectUrl
      downloadLink.download = download.fileName
      downloadLink.hidden = true
      document.body.append(downloadLink)
      downloadLink.click()
      downloadTriggered = true
      setDownloadStatus(`CSV 다운로드를 시작했습니다. 다운로드 조건: ${context}`)
    } catch (error) {
      setDownloadError(`${downloadErrorMessage(error)} 요청 조건: ${context}`)
    } finally {
      downloadLink?.remove()
      if (objectUrl) {
        const urlToRevoke = objectUrl
        if (downloadTriggered) {
          window.setTimeout(() => URL.revokeObjectURL(urlToRevoke), 0)
        } else {
          URL.revokeObjectURL(urlToRevoke)
        }
      }
      downloadInFlight.current = false
      setIsDownloading(false)
    }
  }

  const totalElements = query.data?.totalElements ?? 0
  const totalPages = query.data?.totalPages ?? 0
  const content = query.data?.content ?? []
  const hasCurrentResult = query.isSuccess && !query.isPlaceholderData
  const filtersChanged = JSON.stringify(normalizeFilters(draftFilters)) !== JSON.stringify(appliedFilters)
  const previousPageUnavailable = page === 0 || query.isFetching || query.isPlaceholderData
  const nextPageUnavailable = page + 1 >= totalPages || !query.data?.hasNext || query.isFetching || query.isPlaceholderData

  return (
    <main className="admin-audit-page">
      <div className="admin-audit-shell">
        <header className="admin-audit-header">
          <div>
            <p>RESERVATION AUDIT</p>
            <h1>예약 감사 이력</h1>
            <span>관리자 예약 생성·변경·취소·노쇼·복구의 처리 근거를 확인합니다.</span>
          </div>
          <Link to="/admin">관리자 메뉴</Link>
        </header>

        <form className="admin-audit-filters" onSubmit={applyFilters} noValidate aria-label="감사 이력 조회 조건">
          <label className="admin-audit-keyword">회원 검색
            <input
              type="search"
              placeholder="이름 또는 전화번호"
              value={draftFilters.keyword}
              onChange={(event) => setDraftFilters((filters) => ({ ...filters, keyword: event.target.value }))}
            />
          </label>
          <label>예약 번호
            <input
              id="audit-reservation-id"
              type="number"
              inputMode="numeric"
              min="1"
              placeholder="예: 152"
              value={draftFilters.reservationId}
              aria-invalid={!!idError}
              aria-describedby={idError ? 'audit-id-error' : undefined}
              onChange={(event) => { setIdError(undefined); setDraftFilters((filters) => ({ ...filters, reservationId: event.target.value })) }}
            />
            {idError ? <span id="audit-id-error" className="admin-audit-field-error" role="alert">{idError}</span> : null}
          </label>
          <label>시작일
            <input
              type="date"
              id="audit-date-from"
              aria-invalid={!!localError}
              aria-describedby={localError ? 'audit-date-error audit-date-help' : 'audit-date-help'}
              value={draftFilters.occurredDateFrom}
              onChange={(event) => { setLocalError(undefined); setDraftFilters((filters) => ({ ...filters, occurredDateFrom: event.target.value })) }}
            />
            {localError ? <span id="audit-date-error" className="admin-audit-field-error" role="alert">{localError}</span> : null}
          </label>
          <label>종료일
            <input
              type="date"
              aria-invalid={!!localError}
              aria-describedby={localError ? 'audit-date-error audit-date-help' : 'audit-date-help'}
              value={draftFilters.occurredDateTo}
              onChange={(event) => { setLocalError(undefined); setDraftFilters((filters) => ({ ...filters, occurredDateTo: event.target.value })) }}
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
              <option value="admin_reservation_created">관리자 예약 생성</option>
              <option value="reservation_cancelled">예약 취소</option>
              <option value="no_show_processed">노쇼 처리</option>
              <option value="payment_restored">입금만료 복구</option>
            </select>
          </label>
          <p className="admin-audit-help" id="audit-date-help">기간은 수업일이 아닌 처리 발생일 기준입니다. 비워두면 전체 기간을 조회합니다.</p>
          <div className="admin-audit-filter-actions">
            <button type="submit">조건 적용</button>
            <button type="button" className="secondary" onClick={resetFilters}>초기화</button>
          </div>
        </form>

        {filtersChanged ? <p className="admin-audit-help">입력한 조건을 적용하면 목록과 CSV 다운로드 조건이 변경됩니다.</p> : null}

        <section className="admin-audit-results" aria-labelledby="admin-audit-results-title">
          <p className="admin-audit-announcement" aria-live="polite" aria-atomic="true">
            {hasCurrentResult && !query.isFetching ? `감사 이력 조회 완료. 전체 ${totalElements}건, 현재 페이지 ${content.length}건.` : ''}
          </p>
          <div className="admin-audit-results-heading">
            <div>
              <h2 id="admin-audit-results-title">처리 이력</h2>
              <p>최신 처리부터 페이지당 20건을 표시합니다.</p>
            </div>
            <div className="admin-audit-results-actions">
              {hasCurrentResult ? <strong>전체 {totalElements}건 · 현재 페이지 {content.length}건</strong> : null}
              <button
                type="button"
                disabled={isDownloading}
                onClick={() => { void downloadCsv() }}
                aria-describedby="audit-export-help audit-applied-filters"
              >
                {isDownloading ? 'CSV 준비 중...' : '현재 조건 CSV 다운로드'}
              </button>
            </div>
          </div>
          <p className="admin-audit-applied" id="audit-applied-filters"><strong>적용 조건</strong> {describeFilters(appliedFilters)}</p>
          <p className="admin-audit-help" id="audit-export-help">CSV는 현재 페이지가 아닌 적용 조건의 전체 이력을 내려받습니다. 별도 조회이므로 목록과 파일의 결과가 달라질 수 있습니다.</p>
          {downloadStatus ? <p className="admin-audit-download-status" role="status">{downloadStatus}</p> : null}

          {downloadError ? (
            <div className="admin-audit-download-error" role="alert">
              <span>{downloadError}</span>
              <button type="button" disabled={isDownloading} onClick={() => { void downloadCsv() }}>
                다시 시도
              </button>
            </div>
          ) : null}

          {query.isPending || query.isPlaceholderData ? <AuditState message="감사 이력을 불러오는 중입니다." /> : null}
          {query.isError ? (
            <AuditState
              error
              message={errorMessage(query.error)}
              onRetry={() => { void query.refetch() }}
            />
          ) : null}
          {hasCurrentResult && content.length === 0
            ? <AuditState message="조회 조건에 해당하는 예약 감사 이력이 없습니다." />
            : null}
          {hasCurrentResult && content.length > 0 ? (
            <ul className="admin-audit-list" aria-label="예약 감사 이력">
              {content.map((auditLog) => (
                <li key={auditLog.auditLogId}><AuditLogRow auditLog={auditLog} /></li>
              ))}
            </ul>
          ) : null}

          {query.isSuccess && totalPages > 0 ? (
            <nav className="admin-audit-pagination" aria-label="감사 이력 페이지">
              <button type="button" aria-disabled={previousPageUnavailable} onClick={() => { if (!previousPageUnavailable) setPage((current) => current - 1) }}>이전</button>
              <span aria-live="polite">{query.isPlaceholderData ? '페이지 조회 중' : `${page + 1} / ${totalPages} 페이지`}</span>
              <button type="button" aria-disabled={nextPageUnavailable} onClick={() => { if (!nextPageUnavailable) setPage((current) => current + 1) }}>다음</button>
            </nav>
          ) : null}
          {query.isFetching && !query.isPending && !query.isPlaceholderData ? <p className="admin-audit-page-loading" role="status">감사 이력을 갱신하는 중입니다.</p> : null}
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

      <p className="admin-audit-memo"><span>처리 사유</span>{auditLog.memo ?? '기록된 사유 없음'}</p>
      <details className="admin-audit-details">
        <summary>처리 상세 · 예약 #{auditLog.reservationId ?? '-'}</summary>
        <dl className="admin-audit-meta">
          <div><dt>처리 주체</dt><dd>{label(ACTOR_LABELS, auditLog.actorType)}</dd></div>
          <div><dt>쿠폰 처리</dt><dd>{label(COUPON_ACTION_LABELS, auditLog.couponAction)}</dd></div>
        </dl>
      </details>
    </article>
  )
}

function toApiFilters(filters: AuditFilterForm, page: number): AdminAuditFilters {
  return {
    ...toApiCriteria(filters),
    page,
    size: PAGE_SIZE,
  }
}

function toApiCriteria(filters: AuditFilterForm): AdminAuditCriteria {
  return {
    keyword: filters.keyword || undefined,
    reservationId: filters.reservationId ? Number(filters.reservationId) : undefined,
    occurredDateFrom: filters.occurredDateFrom || undefined,
    occurredDateTo: filters.occurredDateTo || undefined,
    actorType: filters.actorType || undefined,
    changeType: filters.changeType || undefined,
  }
}

function normalizeFilters(filters: AuditFilterForm): AuditFilterForm {
  return { ...filters, keyword: filters.keyword.trim(), reservationId: filters.reservationId.trim() }
}

function label(labels: Record<string, string>, value?: string) {
  return value ? labels[value] ?? '기타 기록' : '-'
}

function describeFilters(filters: AuditFilterForm) {
  return [
    filters.occurredDateFrom || filters.occurredDateTo ? `${filters.occurredDateFrom || '시작 제한 없음'} ~ ${filters.occurredDateTo || '종료 제한 없음'}` : '전체 기간',
    filters.keyword ? `회원: ${filters.keyword}` : '전체 회원',
    filters.reservationId ? `예약 #${filters.reservationId}` : '',
    filters.actorType ? label(ACTOR_LABELS, filters.actorType) : '',
    filters.changeType ? label(CHANGE_TYPE_LABELS, filters.changeType) : '전체 작업',
  ].filter(Boolean).join(' · ')
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
