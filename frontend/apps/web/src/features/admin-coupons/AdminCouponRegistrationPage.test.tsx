import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { ResponseError, type AdminMemberResponse, type CouponResponse } from '@horse/api-client'
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
    getMembers: vi.fn().mockResolvedValue([MEMBER]),
    registerCoupon: vi.fn().mockResolvedValue(COUPON),
    ...overrides,
  }
}

function renderPage(api: AdminCouponsApi) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  return render(<AdminCouponRegistrationPage api={api} />, { wrapper: Wrapper })
}

async function selectCoupon() {
  fireEvent.change(await screen.findByLabelText('등록 대상'), { target: { value: '3' } })
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
    expect(screen.getByLabelText('등록 대상')).toBeDisabled()
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
    expect(await screen.findByLabelText('등록 대상')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /일반/ })).toBeInTheDocument()
    expect(screen.getByLabelText('총 사용 가능 횟수')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '쿠폰 등록' })).toBeInTheDocument()
  })
})
