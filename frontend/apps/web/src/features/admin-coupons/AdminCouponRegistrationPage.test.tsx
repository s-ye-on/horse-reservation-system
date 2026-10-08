import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { ResponseError, type AdminMemberPageResponse, type AdminMemberResponse, type CouponResponse } from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import type { AdminCouponsApi } from './admin-coupons.api'
import { AdminCouponRegistrationPage } from './admin-coupon-registration-page'

const MEMBER: AdminMemberResponse = {
  id: 3, name: '이기승', phone: '010-2222-3333', generalRideCount: 8,
  progressionValue: 26, progressionClass: 'LARGE_ARENA_TROT', effectiveClass: 'LARGE_ARENA_TROT',
  progressionManagementStartedAt: new Date('2026-08-01T00:00:00+09:00'),
  progressionBaselineClass: null, progressionBaselineThreshold: null,
  progressionBaselineActualRideCount: null, specialApprovalProgressionCredit: 18,
  promotionHoldClass: null,
  dressageRideCount: 0, jumpingRideCount: 0, dressageApproved: true, jumpingApproved: false,
  canUseLargeArena: true,
}
const OTHER_MEMBER: AdminMemberResponse = {
  id: 4, name: '김초보', phone: '010-9876-5432', generalRideCount: 1,
  progressionValue: 1, progressionClass: 'ROUND_BEGINNER', effectiveClass: 'ROUND_BEGINNER',
  progressionManagementStartedAt: new Date('2026-08-01T00:00:00+09:00'),
  progressionBaselineClass: null, progressionBaselineThreshold: null,
  progressionBaselineActualRideCount: null, specialApprovalProgressionCredit: 0,
  promotionHoldClass: null,
  dressageRideCount: 0, jumpingRideCount: 0, dressageApproved: false, jumpingApproved: false,
  canUseLargeArena: false,
}
const COUPON: CouponResponse = {
  id: 9, memberId: 3, type: 'dressage', totalCount: 10, remainingCount: 10, heldCount: 0,
  firstUsedAt: null, expiresAt: null, freeChangeUsed: false, status: 'active',
  createdBy: 'admin', createdAt: new Date('2026-07-29T01:00:00Z'),
}
const IMPORTED_COUPON: CouponResponse = {
  ...COUPON,
  id: 10,
  totalCount: 10,
  remainingCount: 7,
  firstUsedAt: new Date('2026-07-03T00:00:00+09:00'),
  expiresAt: new Date('2026-10-03T00:00:00+09:00'),
}

afterEach(cleanup)

function createApi(overrides: Partial<AdminCouponsApi> = {}): AdminCouponsApi {
  return {
    getMembers: vi.fn().mockResolvedValue(memberPage([MEMBER])),
    registerCoupon: vi.fn().mockResolvedValue(COUPON),
    ...overrides,
  }
}

function memberPage(content: AdminMemberResponse[], page = 0, totalElements = content.length): AdminMemberPageResponse {
  const totalPages = Math.ceil(totalElements / 20)
  return { content, page, size: 20, totalElements, totalPages, hasNext: page + 1 < totalPages }
}

function searchableMembers() {
  return vi.fn((page: number, _size: number, query?: string) => Promise.resolve(memberPage(
    query === '김초보' ? [OTHER_MEMBER] : query === '22223333' ? [MEMBER] : query ? [] : [MEMBER, OTHER_MEMBER], page,
  )))
}

function renderPage(api: AdminCouponsApi) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  return render(<AdminCouponRegistrationPage api={api} />, { wrapper: Wrapper })
}

async function selectCoupon() {
  fireEvent.change(await screen.findByLabelText('회원 검색'), { target: { value: '22223333' } })
  fireEvent.click(await screen.findByRole('option', { name: /이기승/ }))
  fireEvent.click(screen.getByRole('radio', { name: /마장마술/ }))
}

describe('AdminCouponRegistrationPage', () => {
  it('신규_쿠폰의_총_횟수를_지정해_등록하고_응답을_표시한다', async () => {
    const registerCoupon = vi.fn().mockResolvedValue({ ...COUPON, totalCount: 20, remainingCount: 20 })
    renderPage(createApi({ registerCoupon }))
    await selectCoupon()
    fireEvent.change(screen.getByLabelText('총 사용 가능 횟수'), { target: { value: '20' } })
    fireEvent.click(screen.getByRole('button', { name: '쿠폰 등록' }))

    await waitFor(() => expect(registerCoupon).toHaveBeenCalledWith(3, {
      type: 'dressage', totalCount: 20, usedCount: 0, firstUsedDate: undefined,
    }))
    expect(await screen.findByRole('heading', { name: '쿠폰이 등록되었습니다' })).toBeInTheDocument()
    expect(screen.getAllByText('20회', { selector: 'dd' })).toHaveLength(2)
    expect(screen.getByText('사용 가능')).toBeInTheDocument()
  })

  it('기존_사용_중_쿠폰의_사용_횟수와_최초_사용일을_등록한다', async () => {
    const registerCoupon = vi.fn().mockResolvedValue(IMPORTED_COUPON)
    renderPage(createApi({ registerCoupon }))
    await selectCoupon()
    fireEvent.click(screen.getByRole('radio', { name: /기존 쿠폰 등록/ }))
    fireEvent.change(screen.getByLabelText('이미 사용한 횟수'), { target: { value: '3' } })
    fireEvent.change(screen.getByLabelText('실제 최초 사용일'), { target: { value: '2026-07-03' } })

    expect(screen.getByText(/등록 후 남은 횟수/)).toHaveTextContent('7회')
    fireEvent.click(screen.getByRole('button', { name: '쿠폰 등록' }))

    await waitFor(() => expect(registerCoupon).toHaveBeenCalledWith(3, {
      type: 'dressage',
      totalCount: 10,
      usedCount: 3,
      firstUsedDate: new Date('2026-07-03T00:00:00.000Z'),
    }))
    expect(await screen.findByText('7회', { selector: 'dd' })).toBeInTheDocument()
    expect(screen.getByText('2026. 7. 3.')).toBeInTheDocument()
  })

  it('사용_중인_기존_쿠폰은_최초_사용일_없이_등록할_수_없다', async () => {
    const registerCoupon = vi.fn()
    renderPage(createApi({ registerCoupon }))
    await selectCoupon()
    fireEvent.click(screen.getByRole('radio', { name: /기존 쿠폰 등록/ }))
    fireEvent.change(screen.getByLabelText('이미 사용한 횟수'), { target: { value: '3' } })
    fireEvent.click(screen.getByRole('button', { name: '쿠폰 등록' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('실제 최초 사용일')
    expect(registerCoupon).not.toHaveBeenCalled()
  })

  it('사용_횟수가_총_횟수보다_크면_등록할_수_없다', async () => {
    const registerCoupon = vi.fn()
    renderPage(createApi({ registerCoupon }))
    await selectCoupon()
    fireEvent.click(screen.getByRole('radio', { name: /기존 쿠폰 등록/ }))
    fireEvent.change(screen.getByLabelText('총 사용 가능 횟수'), { target: { value: '5' } })
    fireEvent.change(screen.getByLabelText('이미 사용한 횟수'), { target: { value: '6' } })
    fireEvent.change(screen.getByLabelText('실제 최초 사용일'), { target: { value: '2026-07-03' } })
    fireEvent.click(screen.getByRole('button', { name: '쿠폰 등록' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('총 횟수보다 클 수 없습니다')
    expect(registerCoupon).not.toHaveBeenCalled()
  })

  it('미래의_최초_사용일로_기존_쿠폰을_등록할_수_없다', async () => {
    const registerCoupon = vi.fn()
    renderPage(createApi({ registerCoupon }))
    await selectCoupon()
    fireEvent.click(screen.getByRole('radio', { name: /기존 쿠폰 등록/ }))
    fireEvent.change(screen.getByLabelText('이미 사용한 횟수'), { target: { value: '3' } })
    fireEvent.change(screen.getByLabelText('실제 최초 사용일'), { target: { value: '2999-01-01' } })
    fireEvent.click(screen.getByRole('button', { name: '쿠폰 등록' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('오늘 이후일 수 없습니다')
    expect(registerCoupon).not.toHaveBeenCalled()
  })

  it('이미_만료된_기존_쿠폰의_서버_오류를_운영자에게_안내한다', async () => {
    const response = new Response(JSON.stringify({
      message: '이미 만료된 기존 쿠폰은 등록할 수 없습니다.',
    }), { status: 400, headers: { 'Content-Type': 'application/json' } })
    renderPage(createApi({
      registerCoupon: vi.fn().mockRejectedValue(new ResponseError(response, 'expired coupon')),
    }))
    await selectCoupon()
    fireEvent.click(screen.getByRole('radio', { name: /기존 쿠폰 등록/ }))
    fireEvent.change(screen.getByLabelText('이미 사용한 횟수'), { target: { value: '3' } })
    fireEvent.change(screen.getByLabelText('실제 최초 사용일'), { target: { value: '2026-07-03' } })
    fireEvent.click(screen.getByRole('button', { name: '쿠폰 등록' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('이미 만료된 기존 쿠폰')
  })

  it('필수_선택이_없으면_API를_호출하지_않는다', async () => {
    const registerCoupon = vi.fn()
    renderPage(createApi({ registerCoupon }))
    fireEvent.click(await screen.findByRole('button', { name: '쿠폰 등록' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('모두 선택')
    expect(registerCoupon).not.toHaveBeenCalled()
  })

  it('등록_중에는_입력과_버튼을_잠가_중복_요청을_막는다', async () => {
    const registerCoupon = vi.fn().mockReturnValue(new Promise<CouponResponse>(() => undefined))
    renderPage(createApi({ registerCoupon }))
    await selectCoupon()
    const button = screen.getByRole('button', { name: '쿠폰 등록' })
    fireEvent.click(button)
    await waitFor(() => expect(button).toBeDisabled())
    fireEvent.click(button)
    expect(registerCoupon).toHaveBeenCalledTimes(1)
    expect(screen.getByLabelText('회원 검색')).toBeDisabled()
  })

  it('회원_이름이나_전화번호로_검색하고_결과에서_선택한다', async () => {
    const getMembers = searchableMembers()
    renderPage(createApi({ getMembers }))
    const search = await screen.findByLabelText('회원 검색')

    fireEvent.change(search, { target: { value: '김초보' } })
    expect(await screen.findByRole('option', { name: /김초보/ })).toBeInTheDocument()
    expect(getMembers).toHaveBeenLastCalledWith(0, 20, '김초보')
    expect(screen.queryByRole('option', { name: /이기승/ })).not.toBeInTheDocument()

    fireEvent.change(search, { target: { value: '22223333' } })
    fireEvent.click(await screen.findByRole('option', { name: /이기승/ }))
    expect(screen.getByLabelText('선택 회원 정보')).toHaveTextContent('이기승')

    fireEvent.change(search, { target: { value: '김초보' } })
    await screen.findByRole('option', { name: /김초보/ })
    expect(screen.getByLabelText('선택 회원 정보')).toHaveTextContent('이기승')
  })

  it('키보드로_검색_결과를_이동하고_회원을_선택한다', async () => {
    renderPage(createApi({ getMembers: searchableMembers() }))
    const search = await screen.findByLabelText('회원 검색')
    await waitFor(() => expect(screen.getByText('전체 회원 2명 · 현재 페이지 2명')).toBeInTheDocument())
    fireEvent.focus(search)
    fireEvent.keyDown(search, { key: 'ArrowDown' })
    fireEvent.keyDown(search, { key: 'Enter' })
    expect(screen.getByLabelText('선택 회원 정보')).toHaveTextContent('김초보')
    expect(search).toHaveAttribute('aria-expanded', 'false')
  })

  it('검색_결과가_없으면_빈_상태를_표시한다', async () => {
    renderPage(createApi({ getMembers: searchableMembers() }))
    fireEvent.change(await screen.findByLabelText('회원 검색'), { target: { value: '없는 회원' } })
    expect(await screen.findByText('검색 조건과 일치하는 회원이 없습니다.')).toBeInTheDocument()
    expect(screen.getByText('검색 결과 0명 · 현재 페이지 0명')).toBeInTheDocument()
  })

  it('최초_페이지에_없는_회원도_전체_검색하고_전화번호_검색어를_서버에_그대로_전달한다', async () => {
    const getMembers = vi.fn((page: number, _size: number, query?: string) => Promise.resolve(
      memberPage(query ? [OTHER_MEMBER] : [MEMBER], page, query ? 1 : 41),
    ))
    renderPage(createApi({ getMembers }))
    await waitFor(() => expect(getMembers).toHaveBeenCalledWith(0, 20, undefined))
    const search = screen.getByLabelText('회원 검색')
    fireEvent.change(search, { target: { value: ' 010-9876 5432 ' } })
    fireEvent.click(await screen.findByRole('option', { name: /김초보/ }))
    expect(getMembers).toHaveBeenLastCalledWith(0, 20, '010-9876 5432')
    expect(screen.getByLabelText('선택 회원 정보')).toHaveTextContent('김초보')
    expect(screen.getByText('검색 결과 1명 · 현재 페이지 1명')).toBeInTheDocument()
  })

  it('검색_결과_페이지를_이동하고_검색어_변경은_첫_페이지로_이동하며_쿠폰_초안을_보존한다', async () => {
    const getMembers = vi.fn((page: number, _size: number, query?: string) => Promise.resolve(
      memberPage(page ? [OTHER_MEMBER] : [MEMBER], page, query === '새 검색' ? 1 : 41),
    ))
    const registerCoupon = vi.fn().mockResolvedValue(COUPON)
    renderPage(createApi({ getMembers, registerCoupon }))
    await selectCoupon()
    fireEvent.click(screen.getByRole('radio', { name: /기존 쿠폰 등록/ }))
    fireEvent.change(screen.getByLabelText('총 사용 가능 횟수'), { target: { value: '25' } })
    fireEvent.change(screen.getByLabelText('이미 사용한 횟수'), { target: { value: '3' } })
    fireEvent.change(screen.getByLabelText('실제 최초 사용일'), { target: { value: '2026-07-03' } })
    const search = screen.getByLabelText('회원 검색')
    fireEvent.focus(search)
    fireEvent.click(screen.getByRole('button', { name: '다음' }))
    await screen.findByRole('option', { name: /김초보/ })
    expect(getMembers).toHaveBeenLastCalledWith(1, 20, '22223333')
    expect(within(screen.getByRole('navigation', { name: '회원 검색 결과 페이지' })).getByText('2 / 3')).toBeInTheDocument()
    expect(screen.getByLabelText('선택 회원 정보')).toHaveTextContent('이기승')
    fireEvent.change(search, { target: { value: '새 검색' } })
    await screen.findByRole('option', { name: /이기승/ })
    expect(getMembers).toHaveBeenLastCalledWith(0, 20, '새 검색')
    expect(getMembers).not.toHaveBeenCalledWith(1, 20, '새 검색')
    expect(screen.getByLabelText('총 사용 가능 횟수')).toHaveValue(25)
    expect(screen.getByLabelText('이미 사용한 횟수')).toHaveValue(3)
    expect(screen.getByLabelText('실제 최초 사용일')).toHaveValue('2026-07-03')
    expect(screen.getByRole('radio', { name: /마장마술/ })).toBeChecked()
    fireEvent.change(search, { target: { value: '' } })
    await waitFor(() => expect(getMembers).toHaveBeenLastCalledWith(0, 20, undefined))
    fireEvent.click(screen.getByRole('button', { name: '쿠폰 등록' }))
    await waitFor(() => expect(registerCoupon).toHaveBeenCalledWith(3, {
      type: 'dressage', totalCount: 25, usedCount: 3, firstUsedDate: new Date('2026-07-03T00:00:00.000Z'),
    }))
  })

  it('Escape는_검색어와_선택을_보존하고_검색창_Enter는_쿠폰을_등록하지_않는다', async () => {
    const registerCoupon = vi.fn()
    renderPage(createApi({ registerCoupon }))
    await selectCoupon()
    const search = screen.getByLabelText('회원 검색')
    fireEvent.focus(search)
    expect(fireEvent.keyDown(search, { key: 'Escape' })).toBe(false)
    expect(search).toHaveValue('22223333')
    expect(search).toHaveAttribute('aria-expanded', 'false')
    fireEvent.keyDown(search, { key: 'Enter' })
    expect(registerCoupon).not.toHaveBeenCalled()
    fireEvent.keyDown(search, { key: 'Enter', isComposing: true, keyCode: 229 })
    expect(registerCoupon).not.toHaveBeenCalled()
  })

  it('검색_중에는_이전_결과를_선택하지_않고_늦은_응답이_최신_검색을_덮지_않는다', async () => {
    let resolveOld!: (page: AdminMemberPageResponse) => void
    const getMembers = vi.fn((_page: number, _size: number, query?: string) => query === '이전'
      ? new Promise<AdminMemberPageResponse>((resolve) => { resolveOld = resolve })
      : Promise.resolve(memberPage(query ? [OTHER_MEMBER] : [MEMBER])))
    const registerCoupon = vi.fn()
    renderPage(createApi({ getMembers, registerCoupon }))
    await waitFor(() => expect(screen.getByText('전체 회원 1명 · 현재 페이지 1명')).toBeInTheDocument())
    const search = screen.getByLabelText('회원 검색')
    fireEvent.change(search, { target: { value: '이전' } })
    fireEvent.keyDown(search, { key: 'Enter' })
    expect(screen.queryByRole('option')).not.toBeInTheDocument()
    expect(screen.queryByLabelText('선택 회원 정보')).not.toBeInTheDocument()
    await waitFor(() => expect(getMembers).toHaveBeenCalledWith(0, 20, '이전'))
    fireEvent.change(search, { target: { value: '최신' } })
    await screen.findByRole('option', { name: /김초보/ })
    resolveOld(memberPage([MEMBER]))
    await waitFor(() => expect(screen.queryByRole('option', { name: /이기승/ })).not.toBeInTheDocument())
    expect(screen.getByRole('option', { name: /김초보/ })).toBeInTheDocument()
    expect(registerCoupon).not.toHaveBeenCalled()
  })

  it('검색_실패와_빈_결과에서도_선택과_입력을_보존하고_검색만_다시_조회한다', async () => {
    const getMembers = vi.fn().mockResolvedValueOnce(memberPage([MEMBER]))
      .mockResolvedValueOnce(memberPage([MEMBER]))
      .mockRejectedValueOnce(new Error('network')).mockResolvedValue(memberPage([]))
    renderPage(createApi({ getMembers }))
    await selectCoupon()
    fireEvent.change(screen.getByLabelText('총 사용 가능 횟수'), { target: { value: '30' } })
    fireEvent.change(screen.getByLabelText('회원 검색'), { target: { value: '다른 회원' } })
    expect(await screen.findByRole('alert')).toHaveTextContent('회원 목록을 불러오지 못했습니다')
    expect(screen.getByLabelText('선택 회원 정보')).toHaveTextContent('이기승')
    expect(screen.getByLabelText('총 사용 가능 횟수')).toHaveValue(30)
    fireEvent.click(screen.getByRole('button', { name: '회원 목록 다시 조회' }))
    await waitFor(() => expect(getMembers).toHaveBeenCalledTimes(4))
    fireEvent.focus(screen.getByLabelText('회원 검색'))
    expect(await screen.findByText('검색 조건과 일치하는 회원이 없습니다.')).toBeInTheDocument()
    expect(screen.getByLabelText('선택 회원 정보')).toHaveTextContent('이기승')
  })

  it('페이지_로딩의_중복_이동을_막고_조회_실패시_검색창으로_포커스를_돌린다', async () => {
    let rejectPage!: (error: Error) => void
    const getMembers = vi.fn((page: number) => page === 0
      ? Promise.resolve(memberPage([MEMBER], 0, 41))
      : new Promise<AdminMemberPageResponse>((_resolve, reject) => { rejectPage = reject }))
    renderPage(createApi({ getMembers }))
    await waitFor(() => expect(screen.getByText('전체 회원 41명 · 현재 페이지 1명')).toBeInTheDocument())
    const search = screen.getByLabelText('회원 검색')
    fireEvent.focus(search)
    const next = screen.getByRole('button', { name: '다음' })
    next.focus()
    fireEvent.click(next)
    await waitFor(() => expect(next).toHaveAttribute('aria-disabled', 'true'))
    expect(next).toHaveFocus()
    fireEvent.click(next)
    expect(getMembers).toHaveBeenCalledTimes(2)
    rejectPage(new Error('network'))
    await screen.findByRole('alert')
    expect(search).toHaveFocus()
  })

  it('지연된_회원_검색_실패가_다른_쿠폰_입력의_포커스를_빼앗지_않는다', async () => {
    let rejectSearch!: (error: Error) => void
    const getMembers = vi.fn((_page: number, _size: number, query?: string) => query
      ? new Promise<AdminMemberPageResponse>((_resolve, reject) => { rejectSearch = reject })
      : Promise.resolve(memberPage([MEMBER])))
    renderPage(createApi({ getMembers }))
    await waitFor(() => expect(screen.getByText('전체 회원 1명 · 현재 페이지 1명')).toBeInTheDocument())
    const search = screen.getByLabelText('회원 검색')
    search.focus()
    fireEvent.change(search, { target: { value: '검색 실패' } })
    await waitFor(() => expect(getMembers).toHaveBeenCalledWith(0, 20, '검색 실패'))
    const count = screen.getByLabelText('총 사용 가능 횟수')
    count.focus()
    rejectSearch(new Error('network'))
    await screen.findByRole('alert')
    expect(count).toHaveFocus()
  })

  it.each([
    [403, '관리자 권한'],
    [404, '회원을 찾을 수 없습니다'],
    [400, '올바르지 않습니다'],
  ])('%i_오류를_구분해_표시한다', async (status, message) => {
    const error = new ResponseError(new Response(null, { status }), 'request failed')
    renderPage(createApi({ registerCoupon: vi.fn().mockRejectedValue(error) }))
    await selectCoupon()
    fireEvent.click(screen.getByRole('button', { name: '쿠폰 등록' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
  })

  it('320px_화면에서도_회원과_쿠폰_입력을_사용할_수_있다', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 320 })
    renderPage(createApi())
    expect(await screen.findByLabelText('회원 검색')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /일반/ })).toBeInTheDocument()
    expect(screen.getByLabelText('총 사용 가능 횟수')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '쿠폰 등록' })).toBeInTheDocument()
  })
})
