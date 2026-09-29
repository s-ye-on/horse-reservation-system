import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import type { MemberCouponResponse, MemberCouponUsageResponse } from '@horse/api-client'
import { isMyCouponsUnauthorized, myCouponsApi, type MyCouponsApi } from './my-coupons.api'
import './my-coupons-page.css'

const TYPE_LABELS: Record<string, string> = { general: '일반 기승', dressage: '마장마술', jumping: '장애물' }
const PAGE_SIZE = 20
const STATUS_META: Record<string, { label: string; tone: string }> = {
  active: { label: '사용 가능', tone: 'active' },
  expired: { label: '기간 만료', tone: 'expired' },
  depleted: { label: '모두 사용', tone: 'depleted' },
}
const ACTION_LABELS: Record<string, string> = {
  held: '예약 처리 시작',
  confirmed: '예약 확정',
  used: '수업 완료로 사용',
  released: '예약 취소로 반환',
  deducted: '관리자 처리로 사용',
  expired: '유효기간 만료',
  free_change_used: '무료 변경 사용',
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

  return (
    <main className="my-coupons-page">
      <div className="my-coupons-shell">
        <header className="my-coupons-header">
          <div><p>MY COUPONS</p><h1>보유 쿠폰 및 이용 현황</h1><span>쿠폰별 잔여 횟수와 예약 처리 중인 횟수, 변동 내역을 확인하세요.</span></div>
          <Link to="/reservations">수업 예약</Link>
        </header>

        <section className="my-coupons-section" aria-labelledby="coupon-list-title" aria-busy={couponsQuery.isFetching}>
          <div className="my-coupons-section-heading">
            <h2 id="coupon-list-title">보유 쿠폰</h2>
            <span>{couponsQuery.data?.totalElements ?? 0}장</span>
          </div>
          {couponsQuery.isPending ? (
            <CouponsState embedded loading message="보유 쿠폰을 불러오는 중입니다." />
          ) : couponsQuery.isError && couponsQuery.data === undefined ? (
            <CouponsState
              embedded
              error
              message={couponErrorMessage(couponsQuery.error, '보유 쿠폰을 불러오지 못했습니다.')}
              onRetry={() => { void couponsQuery.refetch() }}
            />
          ) : (
            <>
              {couponsQuery.isError ? <CouponsRefreshError message="쿠폰 목록을 새로고침하지 못해 이전 내용을 표시합니다." /> : null}
              {coupons.length === 0 ? (
                <CouponsState embedded message="보유한 쿠폰이 없습니다. 쿠폰 등록은 관리자에게 문의해 주세요." />
              ) : (
                <div className="my-coupon-list">{coupons.map((coupon) => <CouponCard key={coupon.couponId} coupon={coupon} />)}</div>
              )}
            </>
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
            <h2 id="usage-list-title">쿠폰 변동 내역</h2>
            <span>{usageQuery.data?.totalElements ?? 0}건</span>
          </div>
          {usageQuery.isPending ? (
            <CouponsState embedded loading message="쿠폰 변동 내역을 불러오는 중입니다." />
          ) : usageQuery.isError && usageQuery.data === undefined ? (
            <CouponsState
              embedded
              error
              message={couponErrorMessage(usageQuery.error, '쿠폰 변동 내역을 불러오지 못했습니다.')}
              onRetry={() => { void usageQuery.refetch() }}
            />
          ) : (
            <>
              {usageQuery.isError ? <CouponsRefreshError message="변동 내역을 새로고침하지 못해 이전 내용을 표시합니다." /> : null}
              {usageLogs.length === 0 ? (
                <CouponsState embedded message="아직 쿠폰 변동 내역이 없습니다." />
              ) : (
                <div className="my-coupon-history-scroll">
                  <table className="my-coupon-history-table">
                    <caption className="my-coupons-visually-hidden">쿠폰 변동 내역</caption>
                    <thead><tr><th scope="col">일시</th><th scope="col">쿠폰</th><th scope="col">변동 내용</th><th scope="col">관련 예약</th><th scope="col">횟수 변화</th></tr></thead>
                    <tbody>{usageLogs.map((usage) => <UsageRow key={usage.usageLogId} usage={usage} />)}</tbody>
                  </table>
                </div>
              )}
            </>
          )}
          <CouponPagination
            label="쿠폰 변동 내역"
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
  const statusKey = normalizeKey(coupon.status)
  const status = STATUS_META[statusKey] ?? { label: '상태 확인 필요', tone: 'depleted' }
  const typeLabel = TYPE_LABELS[normalizeKey(coupon.type)] ?? '기타'
  const completedCount = statusKey === 'expired'
    ? null
    : Math.max(0, (coupon.totalCount ?? 0) - (coupon.remainingCount ?? 0))
  return (
    <article className="my-coupon-card">
      <div className="my-coupon-card-heading"><div><p>쿠폰 번호 {coupon.couponId}</p><h3>{typeLabel} 쿠폰</h3><span>총 {coupon.totalCount ?? 0}회 중 잔여 {coupon.remainingCount ?? 0}회</span></div><span className={`my-coupon-status ${status.tone}`}>{status.label}</span></div>
      <div className="my-coupon-counts">
        <div className="available"><span>사용 가능</span><strong>{coupon.availableCount ?? 0}회</strong></div>
        <div><span>예약 처리 중</span><strong>{coupon.heldCount ?? 0}회</strong></div>
        <div><span>사용 완료</span><strong>{completedCount === null ? '이력 확인' : `${completedCount}회`}</strong></div>
      </div>
      <dl className="my-coupon-dates">
        <div><dt>첫 기승일</dt><dd>{coupon.firstUsedAt ? formatDate(coupon.firstUsedAt) : '첫 사용 전'}</dd></div>
        <div><dt>만료일</dt><dd>{coupon.expiresAt ? formatDate(coupon.expiresAt) : '첫 사용 후 확정'}</dd></div>
        <div><dt>무료 변경</dt><dd>{coupon.freeChangeUsed ? '사용 완료' : '사용 가능'}</dd></div>
      </dl>
      {completedCount === null ? <p className="my-coupon-expired-note">만료된 미사용 횟수가 있어 사용 완료 횟수는 아래 변동 내역에서 확인해 주세요.</p> : null}
    </article>
  )
}

function UsageRow({ usage }: { usage: MemberCouponUsageResponse }) {
  const actionKey = normalizeKey(usage.action)
  return (
    <tr>
      <td data-label="일시"><time dateTime={usage.occurredAt?.toISOString()}>{usage.occurredAt ? formatDateTime(usage.occurredAt) : '-'}</time></td>
      <td data-label="쿠폰">쿠폰 번호 {usage.couponId}</td>
      <td data-label="변동 내용"><span className={`my-coupon-action ${actionKey}`}>{ACTION_LABELS[actionKey] ?? '쿠폰 처리'}</span></td>
      <td data-label="관련 예약">{usage.reservationId ? `예약 번호 ${usage.reservationId}` : '관련 예약 없음'}</td>
      <td data-label="횟수 변화">{formatUsageCount(actionKey, usage.countDelta ?? 0)}</td>
    </tr>
  )
}

function formatUsageCount(action: string, countDelta: number) {
  if (action === 'held' || action === 'released') return `예약 처리 중 ${formatSignedCount(countDelta)}`
  if (action === 'used' || action === 'deducted' || action === 'expired') return `잔여 ${formatSignedCount(countDelta)}`
  if (action === 'confirmed') return '예약 상태만 변경'
  if (action === 'free_change_used') return '무료 변경 사용'
  return countDelta === 0 ? '횟수 변화 없음' : formatSignedCount(countDelta)
}

function formatSignedCount(value: number) {
  return `${value > 0 ? '+' : ''}${value}회`
}

function normalizeKey(value?: string) {
  return value?.toLowerCase() ?? ''
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
  loading = false,
  embedded = false,
  onRetry,
}: {
  message: string
  error?: boolean
  loading?: boolean
  embedded?: boolean
  onRetry?: () => void
}) {
  const content = (
    <section className="my-coupons-state" role={error ? 'alert' : loading ? 'status' : undefined}>
      <p>{message}</p>
      {onRetry ? <button type="button" onClick={onRetry}>다시 시도</button> : null}
    </section>
  )
  return embedded ? content : <main className="my-coupons-page"><div className="my-coupons-shell">{content}</div></main>
}

function CouponsRefreshError({ message }: { message: string }) {
  return <p className="my-coupons-refresh-error" role="alert">{message}</p>
}

function couponErrorMessage(error: unknown, fallback: string) {
  return isMyCouponsUnauthorized(error) ? '회원 인증을 확인할 수 없습니다. 다시 로그인해 주세요.' : fallback
}
