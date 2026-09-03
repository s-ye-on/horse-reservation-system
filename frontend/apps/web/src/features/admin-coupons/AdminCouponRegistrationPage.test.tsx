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
  it('선택한_회원에게_10회권을_등록하고_응답을_표시한다', async () => {
    const registerCoupon = vi.fn().mockResolvedValue(COUPON)
    renderPage(createApi({ registerCoupon }))
    await selectCoupon()
    fireEvent.click(screen.getByRole('button', { name: '10회권 등록' }))

    await waitFor(() => expect(registerCoupon).toHaveBeenCalledWith(3, 'dressage'))
    expect(await screen.findByRole('heading', { name: '쿠폰이 등록되었습니다' })).toBeInTheDocument()
    expect(screen.getByText('10회', { selector: 'dd' })).toBeInTheDocument()
    expect(screen.getByText('active')).toBeInTheDocument()
  })

  it('필수_선택이_없으면_API를_호출하지_않는다', async () => {
    const registerCoupon = vi.fn()
    renderPage(createApi({ registerCoupon }))
    fireEvent.click(await screen.findByRole('button', { name: '10회권 등록' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('모두 선택')
    expect(registerCoupon).not.toHaveBeenCalled()
  })

  it('등록_중에는_입력과_버튼을_잠가_중복_요청을_막는다', async () => {
    const registerCoupon = vi.fn().mockReturnValue(new Promise<CouponResponse>(() => undefined))
    renderPage(createApi({ registerCoupon }))
    await selectCoupon()
    const button = screen.getByRole('button', { name: '10회권 등록' })
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
    fireEvent.click(screen.getByRole('button', { name: '10회권 등록' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
  })

  it('320px_화면에서도_회원과_쿠폰_입력을_사용할_수_있다', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 320 })
    renderPage(createApi())
    expect(await screen.findByLabelText('등록 대상')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: /일반/ })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '10회권 등록' })).toBeInTheDocument()
  })
})
