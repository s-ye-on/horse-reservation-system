import { useRef, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { triggerCsvDownload } from '../../api/csv-download'
import { adminCouponUsageApi, couponUsageDownloadErrorMessage, type AdminCouponUsageApi, type AdminCouponUsageCriteria } from './admin-coupon-usage.api'
import './admin-coupon-usage-page.css'

interface UsageFilters {
  keyword: string
  couponId: string
  reservationId: string
  occurredDateFrom: string
  occurredDateTo: string
}

const EMPTY_FILTERS: UsageFilters = { keyword: '', couponId: '', reservationId: '', occurredDateFrom: '', occurredDateTo: '' }
const ID_FIELDS = ['couponId', 'reservationId'] as const
type FieldErrors = Partial<Record<keyof UsageFilters, string>>

function normalizeFilters(filters: UsageFilters): UsageFilters {
  return { ...filters, keyword: filters.keyword.trim(), couponId: filters.couponId.trim(), reservationId: filters.reservationId.trim() }
}

function toCriteria(filters: UsageFilters): AdminCouponUsageCriteria {
  return {
    keyword: filters.keyword || undefined,
    couponId: filters.couponId ? Number(filters.couponId) : undefined,
    reservationId: filters.reservationId ? Number(filters.reservationId) : undefined,
    occurredDateFrom: filters.occurredDateFrom || undefined,
    occurredDateTo: filters.occurredDateTo || undefined,
  }
}

function describeFilters(filters: UsageFilters) {
  const parts = [
    filters.keyword ? `회원: ${filters.keyword}` : '',
    filters.couponId ? `쿠폰 번호: ${filters.couponId}` : '',
    filters.reservationId ? `예약 번호: ${filters.reservationId}` : '',
    filters.occurredDateFrom || filters.occurredDateTo
      ? `처리 발생일: ${filters.occurredDateFrom || '시작 제한 없음'} ~ ${filters.occurredDateTo || '종료 제한 없음'}` : '',
  ].filter(Boolean)
  return parts.length ? parts.join(' · ') : '전체 회원 · 전체 쿠폰 · 전체 예약 · 전체 기간'
}

export function AdminCouponUsagePage({ api = adminCouponUsageApi }: { api?: AdminCouponUsageApi }) {
  const [draft, setDraft] = useState<UsageFilters>(EMPTY_FILTERS)
  const [applied, setApplied] = useState<UsageFilters>(EMPTY_FILTERS)
  const [errors, setErrors] = useState<FieldErrors>({})
  const [pending, setPending] = useState(false)
  const [status, setStatus] = useState<string>()
  const [error, setError] = useState<string>()
  const inFlight = useRef(false)
  const changed = JSON.stringify(normalizeFilters(draft)) !== JSON.stringify(applied)

  const update = (field: keyof UsageFilters, value: string) => {
    setDraft((current) => ({ ...current, [field]: value }))
    setErrors((current) => ({ ...current, [field]: undefined }))
  }

  const applyFilters = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (inFlight.current) return
    const next = normalizeFilters(draft)
    const nextErrors: FieldErrors = {}
    for (const field of ID_FIELDS) {
      const value = next[field]
      const input = event.currentTarget.elements.namedItem(`usage-${field}`) as HTMLInputElement
      if (input.validity.badInput || (value && (!/^\d+$/.test(value) || !Number.isSafeInteger(Number(value)) || Number(value) < 1))) {
        nextErrors[field] = '번호는 1 이상의 정수로 입력해 주세요.'
      }
    }
    for (const field of ['occurredDateFrom', 'occurredDateTo'] as const) {
      const input = event.currentTarget.elements.namedItem(`usage-${field}`) as HTMLInputElement
      if (input.validity.badInput) nextErrors[field] = '날짜를 완전하게 입력해 주세요.'
    }
    if (next.occurredDateFrom && next.occurredDateTo && next.occurredDateFrom > next.occurredDateTo) {
      nextErrors.occurredDateFrom = '시작일은 종료일보다 늦을 수 없습니다.'
    }
    setErrors(nextErrors)
    const first = Object.keys(nextErrors)[0]
    if (first) { document.getElementById(`usage-${first}`)?.focus(); return }
    setApplied(next)
    setStatus(undefined)
    setError(undefined)
  }

  const downloadCsv = async () => {
    if (inFlight.current) return
    inFlight.current = true
    setPending(true)
    setStatus(undefined)
    setError(undefined)
    const context = describeFilters(applied)
    try {
      triggerCsvDownload(await api.downloadUsageLogs(toCriteria(applied)))
      setStatus(`CSV 다운로드를 시작했습니다. 다운로드 조건: ${context}`)
    } catch (downloadError) {
      setError(`${couponUsageDownloadErrorMessage(downloadError)} 요청 조건: ${context}`)
    } finally {
      inFlight.current = false
      setPending(false)
    }
  }

  return (
    <main className="coupon-usage-page">
      <header className="coupon-usage-header">
        <div><p>COUPON USAGE</p><h1>쿠폰 사용 이력 CSV</h1><span>처리 발생일과 회원·쿠폰·예약 조건으로 전체 사용 이력을 내려받습니다.</span></div>
        <Link to="/admin/coupons/new">쿠폰 등록</Link>
      </header>
      <form className="coupon-usage-filters" aria-label="쿠폰 사용 이력 다운로드 조건" onSubmit={applyFilters} noValidate>
        <label htmlFor="usage-keyword">회원 검색<input id="usage-keyword" type="search" placeholder="이름 또는 전화번호" value={draft.keyword} onChange={(event) => update('keyword', event.target.value)} /></label>
        {ID_FIELDS.map((field) => (
          <label key={field} htmlFor={`usage-${field}`}><span id={`usage-${field}-label`}>{field === 'couponId' ? '쿠폰 번호' : '예약 번호'}</span>
            <input id={`usage-${field}`} name={`usage-${field}`} aria-labelledby={`usage-${field}-label`} type="number" inputMode="numeric" min="1" step="1" value={draft[field]} aria-invalid={!!errors[field]} aria-describedby={errors[field] ? `usage-${field}-error` : undefined} onChange={(event) => update(field, event.target.value)} />
            {errors[field] ? <span id={`usage-${field}-error`} className="coupon-usage-field-error" role="alert">{errors[field]}</span> : null}
          </label>
        ))}
        {(['occurredDateFrom', 'occurredDateTo'] as const).map((field) => (
          <label key={field} htmlFor={`usage-${field}`}><span id={`usage-${field}-label`}>{field === 'occurredDateFrom' ? '처리 시작일' : '처리 종료일'}</span>
            <input id={`usage-${field}`} name={`usage-${field}`} aria-labelledby={`usage-${field}-label`} type="date" value={draft[field]} aria-invalid={!!errors[field]} aria-describedby={`usage-date-help${errors[field] ? ` usage-${field}-error` : ''}`} onChange={(event) => update(field, event.target.value)} />
            {errors[field] ? <span id={`usage-${field}-error`} className="coupon-usage-field-error" role="alert">{errors[field]}</span> : null}
          </label>
        ))}
        <p className="coupon-usage-help" id="usage-date-help">기간은 수업일이 아닌 처리 발생일 기준입니다. 비워둔 조건은 제한 없이 적용합니다.</p>
        <div className="coupon-usage-filter-actions">
          <button type="submit" disabled={pending}>조건 적용</button>
          <button type="button" className="secondary" disabled={pending} onClick={() => { setDraft(EMPTY_FILTERS); setApplied(EMPTY_FILTERS); setErrors({}); setError(undefined); setStatus(undefined) }}>초기화</button>
        </div>
      </form>
      {changed ? <p className="coupon-usage-help">변경한 입력은 조건 적용 후 다운로드에 반영됩니다.</p> : null}
      <section className="coupon-usage-export" aria-labelledby="usage-export-title">
        <h2 id="usage-export-title">CSV 다운로드</h2>
        <p className="coupon-usage-applied" id="usage-applied"><strong>적용 조건</strong> {describeFilters(applied)}</p>
        <p className="coupon-usage-help" id="usage-export-help">페이지 단위로 나누지 않고 적용 조건의 전체 결과를 다운로드합니다. 결과가 없으면 헤더만 있는 CSV를 받습니다.</p>
        <button type="button" className="secondary" disabled={pending} aria-describedby="usage-applied usage-export-help" onClick={() => { void downloadCsv() }}>{pending ? 'CSV 준비 중...' : '현재 조건 쿠폰 사용 이력 CSV 다운로드'}</button>
        {pending ? <p role="status" className="coupon-usage-help">CSV를 준비하고 있습니다.</p> : null}
        {status ? <p role="status" className="coupon-usage-feedback">{status}</p> : null}
        {error ? <p role="alert" className="coupon-usage-feedback error">{error}</p> : null}
      </section>
    </main>
  )
}
