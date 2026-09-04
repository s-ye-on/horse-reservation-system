import { useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import type { CouponRegistrationRequest, CouponResponse } from '@horse/api-client'
import {
  adminCouponsApi,
  getAdminCouponErrorKind,
  getAdminCouponErrorMessage,
  type AdminCouponsApi,
  type CouponType,
} from './admin-coupons.api'
import './admin-coupon-registration-page.css'

const COUPON_TYPES: ReadonlyArray<{ type: CouponType; label: string; description: string }> = [
  { type: 'general', label: '일반', description: '일반 기승 클래스' },
  { type: 'dressage', label: '마장마술', description: '승인 회원 전용' },
  { type: 'jumping', label: '장애물', description: '승인 회원 전용' },
]
type RegistrationMode = 'new' | 'existing'

const COUPON_STATUS_LABELS: Readonly<Record<string, string>> = {
  active: '사용 가능',
  depleted: '모두 사용',
  expired: '기간 만료',
}

interface AdminCouponRegistrationPageProps {
  api?: AdminCouponsApi
}

function getRequestErrorMessage(error: unknown) {
  const kind = getAdminCouponErrorKind(error)
  if (kind === 'forbidden') return '관리자 권한이 없어 쿠폰을 등록할 수 없습니다.'
  if (kind === 'not-found') return '선택한 회원을 찾을 수 없습니다. 회원 목록을 다시 확인해 주세요.'
  if (kind === 'validation') return '쿠폰 정보가 올바르지 않습니다. 입력 내용을 확인해 주세요.'
  return '쿠폰을 등록하지 못했습니다. 처리 결과를 확인한 뒤 다시 시도해 주세요.'
}

function getCouponTypeLabel(type?: string) {
  return COUPON_TYPES.find((couponType) => couponType.type === type)?.label ?? type ?? '-'
}

function getCouponStatusLabel(status?: string) {
  return status ? COUPON_STATUS_LABELS[status] ?? status : '-'
}

function formatCouponDate(date: Date) {
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: 'numeric', day: 'numeric',
  }).format(date)
}

function getSeoulDateInputValue(date = new Date()) {
  const parts = new Intl.DateTimeFormat('en', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(date)
  const value = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return `${value.year}-${value.month}-${value.day}`
}

function parsePositiveInteger(value: string) {
  if (!/^\d+$/.test(value)) return undefined
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed > 0 ? parsed : undefined
}

function parseNonNegativeInteger(value: string) {
  if (!/^\d+$/.test(value)) return undefined
  const parsed = Number(value)
  return Number.isSafeInteger(parsed) && parsed >= 0 ? parsed : undefined
}

export function AdminCouponRegistrationPage({ api = adminCouponsApi }: AdminCouponRegistrationPageProps) {
  const [memberId, setMemberId] = useState<number>()
  const [couponType, setCouponType] = useState<CouponType>()
  const [registrationMode, setRegistrationMode] = useState<RegistrationMode>('new')
  const [totalCount, setTotalCount] = useState('10')
  const [usedCount, setUsedCount] = useState('')
  const [firstUsedDate, setFirstUsedDate] = useState('')
  const [formError, setFormError] = useState<string>()
  const [requestError, setRequestError] = useState<string>()
  const [createdCoupon, setCreatedCoupon] = useState<CouponResponse>()
  const membersQuery = useQuery({ queryKey: ['admin', 'coupon-members'], queryFn: api.getMembers })
  const members = membersQuery.data ?? []
  const selectedMember = members.find((member) => member.id === memberId)
  const registration = useMutation({
    mutationFn: ({ targetMemberId, request }: {
      targetMemberId: number
      request: CouponRegistrationRequest
    }) => api.registerCoupon(targetMemberId, request),
    onSuccess: (coupon) => {
      setCreatedCoupon(coupon)
      setFormError(undefined)
      setRequestError(undefined)
    },
    onError: async (error) => setRequestError(await getAdminCouponErrorMessage(error)),
  })

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (memberId === undefined || couponType === undefined) {
      setFormError('회원과 쿠폰 종류를 모두 선택해 주세요.')
      return
    }
    const parsedTotalCount = parsePositiveInteger(totalCount)
    if (parsedTotalCount === undefined) {
      setFormError('총 횟수는 1회 이상의 정수로 입력해 주세요.')
      return
    }

    let parsedUsedCount = 0
    let parsedFirstUsedDate: Date | undefined
    if (registrationMode === 'existing') {
      const existingUsedCount = parseNonNegativeInteger(usedCount)
      if (existingUsedCount === undefined || existingUsedCount === 0) {
        setFormError('기존 사용 중 쿠폰의 사용 횟수는 1회 이상의 정수로 입력해 주세요.')
        return
      }
      if (existingUsedCount > parsedTotalCount) {
        setFormError('사용 횟수는 총 횟수보다 클 수 없습니다.')
        return
      }
      if (!firstUsedDate) {
        setFormError('기존 사용 중 쿠폰의 실제 최초 사용일을 입력해 주세요.')
        return
      }
      if (firstUsedDate > getSeoulDateInputValue()) {
        setFormError('실제 최초 사용일은 오늘 이후일 수 없습니다.')
        return
      }
      parsedUsedCount = existingUsedCount
      parsedFirstUsedDate = new Date(`${firstUsedDate}T00:00:00.000Z`)
    }

    setFormError(undefined)
    setRequestError(undefined)
    setCreatedCoupon(undefined)
    registration.mutate({
      targetMemberId: memberId,
      request: {
        type: couponType,
        totalCount: parsedTotalCount,
        usedCount: parsedUsedCount,
        firstUsedDate: parsedFirstUsedDate,
      },
    })
  }

  if (membersQuery.isPending) return <CouponState message="회원 목록을 불러오는 중입니다." />
  if (membersQuery.isError) {
    return <CouponState error message={getRequestErrorMessage(membersQuery.error)} />
  }

  return (
    <main className="admin-coupon-page">
      <div className="admin-coupon-shell">
        <header className="admin-coupon-header">
          <p className="admin-coupon-eyebrow">COUPON OPERATIONS</p>
          <h1>쿠폰 등록</h1>
        </header>

        {members.length === 0 ? <CouponState message="쿠폰을 등록할 회원이 없습니다." embedded /> : (
          <form className="admin-coupon-form" onSubmit={submit} noValidate>
            <fieldset className="admin-coupon-fieldset" disabled={registration.isPending}>
              <legend>1. 회원 선택</legend>
              <label className="admin-coupon-label" htmlFor="coupon-member">등록 대상</label>
              <select
                className="admin-coupon-select"
                id="coupon-member"
                value={memberId ?? ''}
                onChange={(event) => setMemberId(event.target.value ? Number(event.target.value) : undefined)}
              >
                <option value="">회원을 선택하세요</option>
                {members.map((member) => <option key={member.id} value={member.id}>{member.name} · {member.phone}</option>)}
              </select>
              {selectedMember ? (
                <div className="admin-coupon-member-summary" aria-label="선택 회원 정보">
                  <p><strong>{selectedMember.name}</strong></p>
                  <p>일반 기승 {selectedMember.generalRideCount ?? 0}회</p>
                  <span>{selectedMember.phone}</span>
                  <span>회원 #{selectedMember.id}</span>
                </div>
              ) : null}
            </fieldset>

            <fieldset className="admin-coupon-fieldset" disabled={registration.isPending}>
              <legend>2. 쿠폰 종류</legend>
              <div className="admin-coupon-options">
                {COUPON_TYPES.map((option) => (
                  <div className="admin-coupon-option" key={option.type}>
                    <input
                      id={`coupon-${option.type}`}
                      type="radio"
                      name="couponType"
                      value={option.type}
                      checked={couponType === option.type}
                      onChange={() => setCouponType(option.type)}
                    />
                    <label htmlFor={`coupon-${option.type}`}>
                      <strong>{option.label}</strong>
                      <span>{option.description}</span>
                    </label>
                  </div>
                ))}
              </div>
            </fieldset>

            <fieldset className="admin-coupon-fieldset" disabled={registration.isPending}>
              <legend>3. 등록 방식</legend>
              <div className="admin-coupon-options admin-coupon-mode-options">
                <div className="admin-coupon-option">
                  <input
                    id="coupon-mode-new"
                    type="radio"
                    name="registrationMode"
                    checked={registrationMode === 'new'}
                    onChange={() => {
                      setRegistrationMode('new')
                      setUsedCount('')
                      setFirstUsedDate('')
                      setFormError(undefined)
                      setRequestError(undefined)
                    }}
                  />
                  <label htmlFor="coupon-mode-new">
                    <strong>신규 쿠폰 발행</strong>
                    <span>Horse에서 새로 발행하며 사용 횟수는 0회입니다.</span>
                  </label>
                </div>
                <div className="admin-coupon-option">
                  <input
                    id="coupon-mode-existing"
                    type="radio"
                    name="registrationMode"
                    checked={registrationMode === 'existing'}
                    onChange={() => {
                      setRegistrationMode('existing')
                      setFormError(undefined)
                      setRequestError(undefined)
                    }}
                  />
                  <label htmlFor="coupon-mode-existing">
                    <strong>기존 쿠폰 등록</strong>
                    <span>Horse 도입 전부터 사용하던 현재 상태를 등록합니다.</span>
                  </label>
                </div>
              </div>

              <div className="admin-coupon-count-grid">
                <label className="admin-coupon-input-label" htmlFor="coupon-total-count">
                  총 사용 가능 횟수
                  <input
                    className="admin-coupon-input"
                    id="coupon-total-count"
                    type="number"
                    min="1"
                    step="1"
                    inputMode="numeric"
                    value={totalCount}
                    onChange={(event) => setTotalCount(event.target.value)}
                    required
                  />
                </label>
                {registrationMode === 'existing' ? (
                  <>
                    <label className="admin-coupon-input-label" htmlFor="coupon-used-count">
                      이미 사용한 횟수
                      <input
                        className="admin-coupon-input"
                        id="coupon-used-count"
                        type="number"
                        min="1"
                        step="1"
                        inputMode="numeric"
                        value={usedCount}
                        onChange={(event) => setUsedCount(event.target.value)}
                        required
                      />
                    </label>
                    <label className="admin-coupon-input-label" htmlFor="coupon-first-used-date">
                      실제 최초 사용일
                      <input
                        className="admin-coupon-input"
                        id="coupon-first-used-date"
                        type="date"
                        max={getSeoulDateInputValue()}
                        value={firstUsedDate}
                        onChange={(event) => setFirstUsedDate(event.target.value)}
                        required
                      />
                    </label>
                  </>
                ) : null}
              </div>

              {registrationMode === 'existing'
                && parsePositiveInteger(totalCount) !== undefined
                && parseNonNegativeInteger(usedCount) !== undefined
                && Number(usedCount) <= Number(totalCount) ? (
                  <p className="admin-coupon-current-state" aria-live="polite">
                    등록 후 남은 횟수 <strong>{Number(totalCount) - Number(usedCount)}회</strong>
                  </p>
                ) : null}
            </fieldset>
            {formError ? <p className="admin-coupon-error" role="alert">{formError}</p> : null}
            {requestError ? <p className="admin-coupon-error" role="alert">{requestError}</p> : null}
            <button className="admin-coupon-submit" type="submit" disabled={registration.isPending}>
              {registration.isPending ? '등록 중...' : '쿠폰 등록'}
            </button>
          </form>
        )}

        {createdCoupon ? (
          <section className="admin-coupon-success" aria-live="polite">
            <h2>쿠폰이 등록되었습니다</h2>
            <dl>
              <div><dt>종류</dt><dd>{getCouponTypeLabel(createdCoupon.type)}</dd></div>
              <div><dt>총 횟수</dt><dd>{createdCoupon.totalCount ?? 0}회</dd></div>
              <div><dt>남은 횟수</dt><dd>{createdCoupon.remainingCount ?? 0}회</dd></div>
              <div><dt>상태</dt><dd>{getCouponStatusLabel(createdCoupon.status)}</dd></div>
              {createdCoupon.firstUsedAt ? (
                <div><dt>최초 사용일</dt><dd>{formatCouponDate(createdCoupon.firstUsedAt)}</dd></div>
              ) : null}
            </dl>
          </section>
        ) : null}
      </div>
    </main>
  )
}

function CouponState({ message, error = false, embedded = false }: {
  message: string
  error?: boolean
  embedded?: boolean
}) {
  const content = <section className="admin-coupon-state" role={error ? 'alert' : undefined}>{message}</section>
  if (embedded) return content
  return <main className="admin-coupon-page"><div className="admin-coupon-shell">{content}</div></main>
}
