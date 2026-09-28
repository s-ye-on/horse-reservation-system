import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import type {
  MemberCouponPageResponse,
  MemberCouponUsagePageResponse,
  MemberReservationPageResponse,
  MemberReservationResponse,
} from '@horse/api-client'
import type { ReactNode } from 'react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { MyCouponsApi } from '../my-coupons/my-coupons.api'
import type { MyReservationsApi } from '../my-reservations/my-reservations.api'
import { MemberHomePage } from './member-home-page'

const UPCOMING_RESERVATION: MemberReservationResponse = {
  reservationId: 10482,
  classType: 'LARGE_ARENA_TROT',
  lessonDate: new Date('2026-10-03T00:00:00+09:00'),
  startTime: '10:00:00',
  status: 'confirmed',
  paymentSource: 'coupon',
  coupon: null,
  paymentDueAt: null,
  rejectionReason: null,
  couponAction: null,
  approvalRequestedAt: new Date('2026-09-20T10:00:00+09:00'),
  adminConfirmedAt: new Date('2026-09-20T11:00:00+09:00'),
  rejectedAt: null,
  cancelledAt: null,
  displayGroup: 'UPCOMING',
  actions: {
    change: { allowed: true, blockedReason: null },
    cancel: { allowed: true, blockedReason: null },
    complete: { allowed: false, blockedReason: null },
    noShow: { allowed: false, blockedReason: null },
    approve: { allowed: false, blockedReason: null },
  },
}

afterEach(cleanup)

function reservationPage(content: MemberReservationResponse[]): MemberReservationPageResponse {
  return {
    content,
    page: 0,
    size: 1,
    totalElements: content.length,
    totalPages: content.length === 0 ? 0 : 1,
    hasNext: false,
  }
}

function couponPage(totalElements = 7): MemberCouponPageResponse {
  return {
    content: [],
    page: 0,
    size: 20,
    totalElements,
    totalPages: totalElements === 0 ? 0 : 1,
    hasNext: false,
  }
}

function createReservationsApi(
  overrides: Partial<MyReservationsApi> = {},
): MyReservationsApi {
  return {
    getMyReservations: vi.fn().mockResolvedValue(reservationPage([UPCOMING_RESERVATION])),
    ...overrides,
  }
}

function createCouponsApi(overrides: Partial<MyCouponsApi> = {}): MyCouponsApi {
  const emptyUsagePage: MemberCouponUsagePageResponse = {
    content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false,
  }
  return {
    getCoupons: vi.fn().mockResolvedValue(couponPage()),
    getUsageLogs: vi.fn().mockResolvedValue(emptyUsagePage),
    ...overrides,
  }
}

function renderPage(
  reservationsApi: MyReservationsApi,
  couponsApi: MyCouponsApi,
) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  )
  return render(
    <MemberHomePage reservationsApi={reservationsApi} couponsApi={couponsApi} />,
    { wrapper: Wrapper },
  )
}

describe('MemberHomePage', () => {
  it('가장_가까운_예정_예약과_서버가_허용한_행동을_표시한다', async () => {
    const reservationsApi = createReservationsApi()
    const couponsApi = createCouponsApi()
    renderPage(reservationsApi, couponsApi)

    expect(await screen.findByRole('heading', { name: /2026년 10월 3일/ })).toBeInTheDocument()
    expect(screen.getByText('대마장 속보')).toBeInTheDocument()
    expect(screen.getByText('예약 확정')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '예약 변경' })).toHaveAttribute(
      'href',
      '/my/reservations/10482/change',
    )
    expect(screen.getByRole('link', { name: '예약 취소' })).toHaveAttribute(
      'href',
      '/my/reservations/10482/cancel',
    )
    expect(reservationsApi.getMyReservations).toHaveBeenCalledWith({
      displayGroup: 'UPCOMING',
      page: 0,
      size: 1,
    })
  })

  it('쿠폰_페이지의_totalElements만_전체_보유_수로_사용한다', async () => {
    const couponsApi = createCouponsApi({
      getCoupons: vi.fn().mockResolvedValue(couponPage(37)),
    })
    renderPage(createReservationsApi(), couponsApi)

    await screen.findByText(/등록된 쿠폰 수/)
    const couponPanel = screen.getByRole('complementary', { name: '내 쿠폰' })
    expect(within(couponPanel).getByText('37')).toBeInTheDocument()
    expect(within(couponPanel).getByText(/등록된 쿠폰 수/)).toBeInTheDocument()
    expect(couponsApi.getCoupons).toHaveBeenCalledWith(0, 20)
    expect(couponsApi.getUsageLogs).not.toHaveBeenCalled()
  })

  it('예정_예약이_없으면_빈_상태와_예약_진입점을_제공한다', async () => {
    renderPage(
      createReservationsApi({ getMyReservations: vi.fn().mockResolvedValue(reservationPage([])) }),
      createCouponsApi({ getCoupons: vi.fn().mockResolvedValue(couponPage(0)) }),
    )

    expect(await screen.findByRole('heading', { name: '예정된 수업이 없습니다' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '수업 예약하기' })).toHaveAttribute('href', '/reservations')
    expect(screen.getByText('0')).toBeInTheDocument()
  })

  it('한_영역의_오류가_다른_영역의_요약을_막지_않는다', async () => {
    renderPage(
      createReservationsApi({ getMyReservations: vi.fn().mockRejectedValue(new Error('failed')) }),
      createCouponsApi({ getCoupons: vi.fn().mockResolvedValue(couponPage(4)) }),
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('예정 예약을 불러오지 못했습니다.')
    expect(screen.getByRole('complementary', { name: '내 쿠폰' })).toHaveTextContent('4장')
  })

  it('실제_회원_route만_빠른_메뉴로_제공한다', async () => {
    renderPage(createReservationsApi(), createCouponsApi())
    await screen.findByRole('heading', { name: /2026년 10월 3일/ })

    const quickMenu = screen.getByRole('region', { name: '빠른 메뉴' })
    expect(within(quickMenu).getByRole('link', { name: /수업 예약/ })).toHaveAttribute('href', '/reservations')
    expect(within(quickMenu).getByRole('link', { name: /내 예약/ })).toHaveAttribute('href', '/my/reservations')
    expect(within(quickMenu).getByRole('link', { name: /내 쿠폰/ })).toHaveAttribute('href', '/my/coupons')
  })

  it('예약과_쿠폰을_각각_불러오는_동안_로딩_상태를_알린다', () => {
    const never = new Promise<never>(() => undefined)
    renderPage(
      createReservationsApi({ getMyReservations: vi.fn().mockReturnValue(never) }),
      createCouponsApi({ getCoupons: vi.fn().mockReturnValue(never) }),
    )

    expect(screen.getByText('예정 예약을 불러오는 중입니다.')).toBeInTheDocument()
    expect(screen.getByText('쿠폰 현황을 불러오는 중입니다.')).toBeInTheDocument()
  })
})
