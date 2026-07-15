import { useState, type FormEvent } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import type { CouponResponse } from '@horse/api-client'
import {
  adminCouponsApi,
  getAdminCouponErrorKind,
  type AdminCouponsApi,
  type CouponType,
} from './admin-coupons.api'
import './admin-coupon-registration-page.css'

const COUPON_TYPES: ReadonlyArray<{ type: CouponType; label: string; description: string }> = [
  { type: 'general', label: '일반', description: '일반 기승 클래스' },
  { type: 'dressage', label: '마장마술', description: '승인 회원 전용' },
  { type: 'jumping', label: '장애물', description: '승인 회원 전용' },
]

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

export function AdminCouponRegistrationPage({ api = adminCouponsApi }: AdminCouponRegistrationPageProps) {
  const [memberId, setMemberId] = useState<number>()
  const [couponType, setCouponType] = useState<CouponType>()
  const [formError, setFormError] = useState<string>()
  const [createdCoupon, setCreatedCoupon] = useState<CouponResponse>()
  const membersQuery = useQuery({ queryKey: ['admin', 'coupon-members'], queryFn: api.getMembers })
  const members = membersQuery.data ?? []
  const selectedMember = members.find((member) => member.id === memberId)
  const registration = useMutation({
    mutationFn: ({ targetMemberId, type }: { targetMemberId: number; type: CouponType }) =>
      api.registerCoupon(targetMemberId, type),
    onSuccess: (coupon) => {
      setCreatedCoupon(coupon)
      setFormError(undefined)
    },
  })

  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (memberId === undefined || couponType === undefined) {
      setFormError('회원과 쿠폰 종류를 모두 선택해 주세요.')
      return
    }
    setFormError(undefined)
    setCreatedCoupon(undefined)
    registration.mutate({ targetMemberId: memberId, type: couponType })
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
          <h1>10회권 쿠폰 등록</h1>
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

            <div className="admin-coupon-fixed-count">
              <p>등록 횟수</p>
              <strong>10회</strong>
            </div>
            {formError ? <p className="admin-coupon-error" role="alert">{formError}</p> : null}
            {registration.isError ? (
              <p className="admin-coupon-error" role="alert">{getRequestErrorMessage(registration.error)}</p>
            ) : null}
            <button className="admin-coupon-submit" type="submit" disabled={registration.isPending}>
              {registration.isPending ? '등록 중...' : '10회권 등록'}
            </button>
          </form>
        )}

        {createdCoupon ? (
          <section className="admin-coupon-success" aria-live="polite">
            <h2>쿠폰이 등록되었습니다</h2>
            <dl>
              <div><dt>종류</dt><dd>{getCouponTypeLabel(createdCoupon.type)}</dd></div>
              <div><dt>총 횟수</dt><dd>{createdCoupon.totalCount ?? 0}회</dd></div>
              <div><dt>상태</dt><dd>{createdCoupon.status ?? '-'}</dd></div>
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
