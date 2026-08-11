import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import type { MemberCouponResponse, MemberCouponUsageResponse } from '@horse/api-client'
import { isMyCouponsUnauthorized, myCouponsApi, type MyCouponsApi } from './my-coupons.api'
import './my-coupons-page.css'

const TYPE_LABELS: Record<string, string> = { general: '일반 10회권', dressage: '마장마술 10회권', jumping: '장애물 10회권' }
const PAGE_SIZE = 20
const STATUS_META: Record<string, { label: string; tone: string }> = {
  active: { label: '사용 가능', tone: 'active' },
  expired: { label: '기간 만료', tone: 'expired' },
  depleted: { label: '모두 사용', tone: 'depleted' },
}
const ACTION_LABELS: Record<string, string> = {
  held: '예약 임시 점유',
  confirmed: '예약 점유 확정',
  used: '수업 완료 사용',
  released: '점유 반환',
  deducted: '관리자 차감',
  expired: '유효기간 만료',
  free_change_used: '무료 변경권 사용',
}

export function MyCouponsPage({ api = myCouponsApi }: { api?: MyCouponsApi }) {
  const [couponPage, setCouponPage] = useState(0)
  const [usagePage, setUsagePage] = useState(0)
  const couponsQuery = useQuery({
    queryKey: ['member', 'coupons', couponPage],
    queryFn: () => api.getCoupons(couponPage, PAGE_SIZE),
    placeholderData: keepPreviousData,
  })
  const usageQuery = useQuery({
    queryKey: ['member', 'coupon-usage', usagePage],
    queryFn: () => api.getUsageLogs(usagePage, PAGE_SIZE),
    placeholderData: keepPreviousData,
  })
  const coupons = couponsQuery.data?.content ?? []
  const usageLogs = usageQuery.data?.content ?? []

  useEffect(() => {
    const totalPages = couponsQuery.data?.totalPages
    if (totalPages === undefined) return
    const validPage = totalPages === 0 ? 0 : Math.min(couponPage, totalPages - 1)
    if (validPage !== couponPage) setCouponPage(validPage)
  }, [couponPage, couponsQuery.data?.totalPages])

  useEffect(() => {
    const totalPages = usageQuery.data?.totalPages
    if (totalPages === undefined) return
    const validPage = totalPages === 0 ? 0 : Math.min(usagePage, totalPages - 1)
    if (validPage !== usagePage) setUsagePage(validPage)
  }, [usagePage, usageQuery.data?.totalPages])

  if (couponsQuery.isPending || usageQuery.isPending) {
    return <CouponsState message="쿠폰과 사용 내역을 불러오는 중입니다." />
  }

  const unauthorizedError = couponsQuery.error ?? usageQuery.error
  if ((couponsQuery.isError || usageQuery.isError) && couponsQuery.data === undefined && usageQuery.data === undefined) {
    return (
      <CouponsState
        error
        message={isMyCouponsUnauthorized(unauthorizedError) ? '회원 인증을 확인할 수 없습니다. 다시 로그인해 주세요.' : '쿠폰 내역을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'}
        onRetry={() => { void Promise.all([couponsQuery.refetch(), usageQuery.refetch()]) }}
      />
    )
  }

  return (
    <main className="my-coupons-page">
      <div className="my-coupons-shell">
        <header className="my-coupons-header">
          <div><p>MY PASSES</p><h1>쿠폰 및 사용 내역</h1><span>예약 점유와 실제 사용을 구분해 확인할 수 있습니다.</span></div>
          <Link to="/reservations">수업 예약</Link>
        </header>

        <section className="my-coupons-section" aria-labelledby="coupon-list-title" aria-busy={couponsQuery.isFetching}>
          <div className="my-coupons-section-heading">
            <h2 id="coupon-list-title">보유 쿠폰</h2>
            <span>{couponsQuery.data?.totalElements ?? 0}장</span>
          </div>
          {couponsQuery.isError ? (
            <CouponsState
              embedded
              error
              message="쿠폰 목록을 불러오지 못했습니다."
              onRetry={() => { void couponsQuery.refetch() }}
            />
          ) : coupons.length === 0 ? (
            <CouponsState embedded message="보유한 쿠폰이 없습니다. 쿠폰 등록은 관리자에게 문의해 주세요." />
          ) : (
            <div className="my-coupon-list">{coupons.map((coupon) => <CouponCard key={coupon.couponId} coupon={coupon} />)}</div>
          )}
          <CouponPagination
            label="보유 쿠폰"
            page={couponPage}
            totalPages={couponsQuery.data?.totalPages ?? 0}
            hasNext={couponsQuery.data?.hasNext ?? false}
            loading={couponsQuery.isFetching}
            onPrevious={() => setCouponPage((current) => current - 1)}
            onNext={() => setCouponPage((current) => current + 1)}
          />
        </section>

        <section className="my-coupons-section" aria-labelledby="usage-list-title" aria-busy={usageQuery.isFetching}>
          <div className="my-coupons-section-heading">
            <h2 id="usage-list-title">사용 내역</h2>
            <span>{usageQuery.data?.totalElements ?? 0}건</span>
          </div>
          {usageQuery.isError ? (
            <CouponsState
              embedded
              error
              message="쿠폰 사용 내역을 불러오지 못했습니다."
              onRetry={() => { void usageQuery.refetch() }}
            />
          ) : usageLogs.length === 0 ? (
            <CouponsState embedded message="아직 쿠폰 사용 내역이 없습니다." />
          ) : (
            <ol className="my-coupon-usage-list">{usageLogs.map((usage) => <UsageItem key={usage.usageLogId} usage={usage} />)}</ol>
          )}
          <CouponPagination
            label="쿠폰 사용 내역"
            page={usagePage}
            totalPages={usageQuery.data?.totalPages ?? 0}
            hasNext={usageQuery.data?.hasNext ?? false}
            loading={usageQuery.isFetching}
            onPrevious={() => setUsagePage((current) => current - 1)}
            onNext={() => setUsagePage((current) => current + 1)}
          />
        </section>
      </div>
    </main>
  )
}

interface CouponPaginationProps {
  label: string
  page: number
  totalPages: number
  hasNext: boolean
  loading: boolean
  onPrevious: () => void
  onNext: () => void
}

function CouponPagination({
  label,
  page,
  totalPages,
  hasNext,
  loading,
  onPrevious,
  onNext,
}: CouponPaginationProps) {
  if (totalPages === 0) return null

  return (
    <nav className="my-coupons-pagination" aria-label={`${label} 페이지`}>
      <button type="button" disabled={page === 0 || loading} onClick={onPrevious}>이전</button>
      <span aria-live="polite">{page + 1} / {totalPages} 페이지</span>
      <button type="button" disabled={page + 1 >= totalPages || !hasNext || loading} onClick={onNext}>다음</button>
      {loading ? <small role="status">페이지 이동 중입니다.</small> : null}
    </nav>
  )
}

function CouponCard({ coupon }: { coupon: MemberCouponResponse }) {
  const status = STATUS_META[coupon.status ?? ''] ?? { label: coupon.status ?? '-', tone: 'depleted' }
  return (
    <article className="my-coupon-card">
      <div className="my-coupon-card-heading"><div><p>COUPON #{coupon.couponId}</p><h3>{TYPE_LABELS[coupon.type ?? ''] ?? coupon.type ?? '-'}</h3></div><span className={`my-coupon-status ${status.tone}`}>{status.label}</span></div>
      <div className="my-coupon-counts">
        <div><span>총 횟수</span><strong>{coupon.totalCount ?? 0}</strong></div>
        <div><span>잔여</span><strong>{coupon.remainingCount ?? 0}</strong></div>
        <div><span>예약 점유</span><strong>{coupon.heldCount ?? 0}</strong></div>
        <div className="available"><span>사용 가능</span><strong>{coupon.availableCount ?? 0}</strong></div>
      </div>
      <dl className="my-coupon-dates">
        <div><dt>첫 기승일</dt><dd>{coupon.firstUsedAt ? formatDate(coupon.firstUsedAt) : '첫 사용 전'}</dd></div>
        <div><dt>만료일</dt><dd>{coupon.expiresAt ? formatDate(coupon.expiresAt) : '첫 사용 후 확정'}</dd></div>
        <div><dt>무료 변경권</dt><dd>{coupon.freeChangeUsed ? '사용 완료' : '사용 가능'}</dd></div>
      </dl>
    </article>
  )
}

function UsageItem({ usage }: { usage: MemberCouponUsageResponse }) {
  return (
    <li className="my-coupon-usage-item">
      <div><span className={`my-coupon-action ${usage.action ?? ''}`}>{ACTION_LABELS[usage.action ?? ''] ?? usage.action ?? '-'}</span><strong>쿠폰 #{usage.couponId}</strong></div>
      <p>{usage.reservationId ? `예약 #${usage.reservationId}` : '관련 예약 없음'}{usage.countDelta ? ` · 횟수 ${usage.countDelta > 0 ? '+' : ''}${usage.countDelta}` : ''}</p>
      <time>{usage.occurredAt ? formatDateTime(usage.occurredAt) : '-'}</time>
    </li>
  )
}

function formatDate(date: Date) {
  return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: 'long', day: 'numeric' }).format(date)
}
function formatDateTime(date: Date) {
  return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date)
}
function CouponsState({
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
    <section className="my-coupons-state" role={error ? 'alert' : undefined}>
      <p>{message}</p>
      {onRetry ? <button type="button" onClick={onRetry}>다시 시도</button> : null}
    </section>
  )
  return embedded ? content : <main className="my-coupons-page"><div className="my-coupons-shell">{content}</div></main>
}
