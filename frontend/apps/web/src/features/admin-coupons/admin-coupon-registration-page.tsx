import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react'
import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query'
import type { AdminMemberResponse, CouponRegistrationRequest, CouponResponse } from '@horse/api-client'
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

const MEMBER_PAGE_SIZE = 20
type RegistrationMode = 'new' | 'existing'

const COUPON_STATUS_LABELS: Readonly<Record<string, string>> = {
  active: '사용 가능',
  depleted: '모두 사용',
  expired: '기간 만료',
}

interface AdminCouponRegistrationPageProps {
  api?: AdminCouponsApi
}

function getMemberQueryErrorMessage(error: unknown) {
  const kind = getAdminCouponErrorKind(error)
  if (kind === 'forbidden') return '관리자 권한이 없어 회원을 조회할 수 없습니다.'
  if (kind === 'validation') return '검색어는 100자 이하로 입력해 주세요.'
  return '회원 목록을 불러오지 못했습니다. 다시 조회해 주세요.'
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
  const [selectedMember, setSelectedMember] = useState<AdminMemberResponse>()
  const [memberPage, setMemberPage] = useState(0)
  const [memberSearch, setMemberSearch] = useState('')
  const [searchQuery, setSearchQuery] = useState('')
  const [memberResultsOpen, setMemberResultsOpen] = useState(false)
  const [activeMemberIndex, setActiveMemberIndex] = useState(0)
  const [couponType, setCouponType] = useState<CouponType>()
  const [registrationMode, setRegistrationMode] = useState<RegistrationMode>('new')
  const [totalCount, setTotalCount] = useState('10')
  const [usedCount, setUsedCount] = useState('')
  const [firstUsedDate, setFirstUsedDate] = useState('')
  const [formError, setFormError] = useState<string>()
  const [requestError, setRequestError] = useState<string>()
  const [createdCoupon, setCreatedCoupon] = useState<CouponResponse>()
  const searchRef = useRef<HTMLInputElement>(null)
  const focusedElementRef = useRef<HTMLElement>(null)
  const resultsRef = useRef<HTMLUListElement>(null)
  const membersQuery = useQuery({
    queryKey: ['admin', 'coupon-members', memberPage, searchQuery],
    queryFn: () => api.getMembers(memberPage, MEMBER_PAGE_SIZE, searchQuery || undefined),
    placeholderData: keepPreviousData,
  })
  const searchPending = memberSearch.trim() !== searchQuery
  const membersLoading = searchPending || membersQuery.isFetching || membersQuery.isPlaceholderData
  const visibleMembers = membersLoading || membersQuery.isError ? [] : membersQuery.data?.content ?? []
  const activeMember = visibleMembers[activeMemberIndex]

  useEffect(() => {
    if (!searchPending) return
    const timer = window.setTimeout(() => {
      setSearchQuery(memberSearch.trim())
      setMemberPage(0)
    }, 300)
    return () => window.clearTimeout(timer)
  }, [memberSearch, searchPending])

  useEffect(() => {
    const list = resultsRef.current
    const option = list?.querySelector(`#coupon-member-${activeMember?.id}`)
    if (!memberResultsOpen || !list || !option) return
    // Scroll only the result list so keyboard navigation does not move the form.
    const listBounds = list.getBoundingClientRect()
    const optionBounds = option.getBoundingClientRect()
    if (optionBounds.top < listBounds.top) list.scrollTop -= listBounds.top - optionBounds.top
    else if (optionBounds.bottom > listBounds.bottom) list.scrollTop += optionBounds.bottom - listBounds.bottom
  }, [memberResultsOpen, activeMember])

  useEffect(() => {
    const previousFocus = focusedElementRef.current
    if (membersQuery.isError && previousFocus?.hasAttribute('data-member-pagination')
      && !previousFocus.isConnected && document.activeElement === document.body) {
      searchRef.current?.focus()
    }
  }, [membersQuery.isError])
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
    if (selectedMember === undefined || couponType === undefined) {
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
      targetMemberId: selectedMember.id,
      request: {
        type: couponType,
        totalCount: parsedTotalCount,
        usedCount: parsedUsedCount,
        firstUsedDate: parsedFirstUsedDate,
      },
    })
  }

  const selectMember = (member?: AdminMemberResponse) => {
    if (!member || membersLoading || membersQuery.isError) return
    setSelectedMember(member)
    setMemberResultsOpen(false)
    setFormError(undefined)
  }

  const handleMemberSearchKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.nativeEvent.isComposing || event.keyCode === 229) return
    if (event.key === 'Escape') {
      event.preventDefault()
      setMemberResultsOpen(false)
      return
    }
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      setMemberResultsOpen(true)
      setActiveMemberIndex((currentIndex) => {
        if (visibleMembers.length === 0) return 0
        const offset = event.key === 'ArrowDown' ? 1 : -1
        return (currentIndex + offset + visibleMembers.length) % visibleMembers.length
      })
      return
    }
    if (event.key === 'Enter') {
      event.preventDefault()
      if (memberResultsOpen) selectMember(activeMember)
      else setMemberResultsOpen(true)
    }
  }

  return (
    <main className="admin-coupon-page">
      <div className="admin-coupon-shell">
        <header className="admin-coupon-header">
          <p className="admin-coupon-eyebrow">COUPON OPERATIONS</p>
          <h1>쿠폰 등록</h1>
        </header>

          <form className="admin-coupon-form" onSubmit={submit} noValidate
            onFocusCapture={(event) => { focusedElementRef.current = event.target }}>
            <fieldset className="admin-coupon-fieldset" disabled={registration.isPending}>
              <legend>1. 회원 선택</legend>
              <div onBlur={(event) => {
                if (!event.currentTarget.contains(event.relatedTarget)) setMemberResultsOpen(false)
              }}>
              <label className="admin-coupon-label" htmlFor="coupon-member-search">회원 검색</label>
              <input
                ref={searchRef}
                className="admin-coupon-search"
                id="coupon-member-search"
                type="search"
                maxLength={100}
                value={memberSearch}
                placeholder="이름 또는 전화번호 입력"
                role="combobox"
                aria-controls={memberResultsOpen && visibleMembers.length > 0 ? 'coupon-member-results' : undefined}
                aria-describedby="coupon-member-search-help coupon-member-result-count"
                aria-expanded={memberResultsOpen}
                aria-autocomplete="list"
                aria-activedescendant={memberResultsOpen && activeMember ? `coupon-member-${activeMember.id}` : undefined}
                onFocus={() => setMemberResultsOpen(true)}
                onKeyDown={handleMemberSearchKeyDown}
                onChange={(event) => {
                  setMemberSearch(event.target.value)
                  setMemberResultsOpen(true)
                  setActiveMemberIndex(0)
                }}
              />
              <p className="admin-coupon-result-count" id="coupon-member-search-help">
                전체 회원의 이름 또는 전화번호로 검색합니다.
              </p>
              <p className="admin-coupon-result-count" id="coupon-member-result-count" aria-live="polite">
                {membersLoading ? '회원 목록을 불러오는 중입니다.' : membersQuery.isError
                  ? '회원 검색 결과를 확인하지 못했습니다.'
                  : `${searchQuery ? '검색 결과' : '전체 회원'} ${membersQuery.data?.totalElements ?? 0}명 · 현재 페이지 ${visibleMembers.length}명`}
              </p>
              {membersQuery.isError && !searchPending ? (
                <div className="admin-coupon-error" role="alert">
                  <p>{getMemberQueryErrorMessage(membersQuery.error)}</p>
                  <button type="button" onClick={() => {
                    searchRef.current?.focus()
                    void membersQuery.refetch()
                  }}>회원 목록 다시 조회</button>
                </div>
              ) : null}
              {memberResultsOpen && !membersLoading && !membersQuery.isError && visibleMembers.length > 0 ? (
                <ul
                  className="admin-coupon-member-results"
                  ref={resultsRef}
                  id="coupon-member-results"
                  role="listbox"
                  aria-label="회원 검색 결과"
                >
                  {visibleMembers.map((member) => (
                    <li
                      className="admin-coupon-member-result"
                      id={`coupon-member-${member.id}`}
                      key={member.id}
                      role="option"
                      aria-selected={member.id === selectedMember?.id}
                      data-active={member.id === activeMember?.id}
                      onMouseDown={(event) => event.preventDefault()}
                      onMouseEnter={() => setActiveMemberIndex(visibleMembers.indexOf(member))}
                      onClick={() => selectMember(member)}
                    >
                      <span>
                        <strong>{member.name}</strong>
                        <small>{member.phone}</small>
                      </span>
                      <small>일반 기승 {member.generalRideCount ?? 0}회</small>
                    </li>
                  ))}
                </ul>
              ) : memberResultsOpen && !membersLoading && !membersQuery.isError ? (
                <p className="admin-coupon-no-results">{searchQuery
                  ? '검색 조건과 일치하는 회원이 없습니다.'
                  : '쿠폰을 등록할 회원이 없습니다.'}</p>
              ) : null}
              {memberResultsOpen && !membersQuery.isError && (membersQuery.data?.totalPages ?? 0) > 1 ? (
                <nav className="admin-coupon-member-pagination" aria-label="회원 검색 결과 페이지">
                  <button type="button" data-member-pagination aria-disabled={membersLoading || memberPage === 0} onClick={() => {
                    if (membersLoading || memberPage === 0) return
                    setMemberPage((page) => page - 1)
                    setActiveMemberIndex(0)
                  }}>이전</button>
                  <span>{(membersQuery.data?.page ?? 0) + 1} / {membersQuery.data?.totalPages}</span>
                  <button type="button" data-member-pagination aria-disabled={membersLoading || !membersQuery.data?.hasNext} onClick={() => {
                    if (membersLoading || !membersQuery.data?.hasNext) return
                    setMemberPage((page) => page + 1)
                    setActiveMemberIndex(0)
                  }}>다음</button>
                </nav>
              ) : null}
              </div>
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
