import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { ResponseError, type MemberCouponResponse, type MemberCouponUsageResponse } from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import type { MyCouponsApi } from './my-coupons.api'
import { MyCouponsPage } from './my-coupons-page'

const COUPONS: MemberCouponResponse[] = [
  { couponId: 1, type: 'general', totalCount: 10, remainingCount: 10, heldCount: 1, availableCount: 9, freeChangeUsed: false, status: 'active' },
  { couponId: 2, type: 'dressage', totalCount: 10, remainingCount: 6, heldCount: 2, availableCount: 4, firstUsedAt: new Date('2026-07-01'), expiresAt: new Date('2026-10-01'), freeChangeUsed: true, status: 'expired' },
  { couponId: 3, type: 'jumping', totalCount: 10, remainingCount: 0, heldCount: 0, availableCount: 0, firstUsedAt: new Date('2026-06-01'), expiresAt: new Date('2026-09-01'), freeChangeUsed: false, status: 'depleted' },
]

const ACTIONS = ['held', 'confirmed', 'used', 'released', 'deducted', 'expired', 'free_change_used'] as const
const USAGE_LOGS: MemberCouponUsageResponse[] = ACTIONS.map((action, index) => ({
  usageLogId: index + 1,
  couponId: index < 4 ? 1 : 2,
  reservationId: action === 'expired' ? undefined : 100 + index,
  action,
  countDelta: action === 'used' || action === 'deducted' ? -1 : 0,
  occurredAt: new Date(`2026-07-${String(10 + index).padStart(2, '0')}T10:00:00+09:00`),
  actorType: action === 'held' ? 'member' : 'admin',
}))

afterEach(cleanup)

function createApi(overrides: Partial<MyCouponsApi> = {}): MyCouponsApi {
  return { getOverview: vi.fn().mockResolvedValue({ coupons: COUPONS, usageLogs: USAGE_LOGS }), ...overrides }
}

function renderPage(api: MyCouponsApi) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}><MemoryRouter>{children}</MemoryRouter></QueryClientProvider>
  return render(<MyCouponsPage api={api} />, { wrapper: Wrapper })
}

describe('MyCouponsPage', () => {
  it('쿠폰별_종류와_총_잔여_점유_사용_가능_횟수를_표시한다', async () => {
    renderPage(createApi())
    const cards = await screen.findAllByRole('article')
    const counts = cards[0].querySelector('.my-coupon-counts') as HTMLElement
    expect(within(cards[0]).getByRole('heading', { name: '일반 10회권' })).toBeInTheDocument()
    expect(within(counts).getByText('총 횟수').nextElementSibling).toHaveTextContent('10')
    expect(within(counts).getByText('잔여').nextElementSibling).toHaveTextContent('10')
    expect(within(counts).getByText('예약 점유').nextElementSibling).toHaveTextContent('1')
    expect(within(counts).getByText('사용 가능').nextElementSibling).toHaveTextContent('9')
  })

  it('첫_사용_전과_첫_기승일_만료일을_구분한다', async () => {
    renderPage(createApi())
    expect(await screen.findByText('첫 사용 전')).toBeInTheDocument()
    expect(screen.getByText('첫 사용 후 확정')).toBeInTheDocument()
    expect(screen.getByText('2026년 7월 1일')).toBeInTheDocument()
    expect(screen.getByText('2026년 10월 1일')).toBeInTheDocument()
  })

  it('무료_변경권과_세_쿠폰_상태를_표시한다', async () => {
    renderPage(createApi())
    expect((await screen.findAllByText('사용 가능')).length).toBeGreaterThan(1)
    expect(screen.getByText('사용 완료')).toBeInTheDocument()
    expect(screen.getByText('기간 만료')).toBeInTheDocument()
    expect(screen.getByText('모두 사용')).toBeInTheDocument()
  })

  it('일곱_사용_이력을_발생_시각과_관련_예약으로_표시한다', async () => {
    renderPage(createApi())
    for (const label of ['예약 임시 점유', '예약 점유 확정', '수업 완료 사용', '점유 반환', '관리자 차감', '유효기간 만료', '무료 변경권 사용']) {
      expect(await screen.findByText(label)).toBeInTheDocument()
    }
    expect(screen.getByText('예약 #100')).toBeInTheDocument()
    expect(screen.getByText('관련 예약 없음')).toBeInTheDocument()
    expect(screen.getAllByText(/2026\. 7\./)).toHaveLength(7)
  })

  it('차감_로그의_횟수_변화를_표시한다', async () => {
    renderPage(createApi())
    expect((await screen.findAllByText(/횟수 -1/)).length).toBe(2)
  })

  it('쿠폰과_사용_이력이_없는_상태를_각각_표시한다', async () => {
    renderPage(createApi({ getOverview: vi.fn().mockResolvedValue({ coupons: [], usageLogs: [] }) }))
    expect(await screen.findByText('등록된 쿠폰이 없습니다.')).toBeInTheDocument()
    expect(screen.getByText('아직 쿠폰 사용 내역이 없습니다.')).toBeInTheDocument()
  })

  it.each([[401, '다시 로그인'], [500, '불러오지 못했습니다']])('%i_조회_오류를_구분한다', async (status, message) => {
    const error = new ResponseError(new Response(null, { status }), 'failed')
    renderPage(createApi({ getOverview: vi.fn().mockRejectedValue(error) }))
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
  })

  it('320px_화면에서도_쿠폰_요약과_사용_내역을_확인할_수_있다', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 320 })
    renderPage(createApi())
    expect(await screen.findByRole('heading', { name: '일반 10회권' })).toBeInTheDocument()
    expect(screen.getByText('무료 변경권 사용')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '수업 예약' })).toHaveAttribute('href', '/reservations')
  })
})
