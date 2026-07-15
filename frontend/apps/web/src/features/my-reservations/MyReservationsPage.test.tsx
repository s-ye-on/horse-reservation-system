import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { ResponseError, type MemberReservationResponse } from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import type { MyReservationsApi } from './my-reservations.api'
import { MyReservationsPage } from './my-reservations-page'

const STATUSES = [
  'pending_admin_approval', 'pending_payment', 'payment_expired', 'confirmed',
  'completed', 'rejected', 'cancelled', 'no_show',
] as const

const RESERVATIONS: MemberReservationResponse[] = STATUSES.map((status, index) => ({
  reservationId: index + 1,
  classType: index === 5 ? 'DRESSAGE' : index === 6 ? 'JUMPING' : 'ROUND_BEGINNER',
  lessonDate: new Date(`2026-08-${String(10 + index).padStart(2, '0')}`),
  startTime: `${String(9 + index).padStart(2, '0')}:00:00`,
  status,
  paymentSource: index === 1 || index === 2 ? 'single_payment' : 'coupon',
  paymentDueAt: index === 1 || index === 2 ? new Date('2026-08-01T14:00:00+09:00') : undefined,
  rejectionReason: status === 'rejected' ? '해당 수업 운영이 어렵습니다.' : undefined,
  couponAction: status === 'completed' ? 'deduct' : status === 'cancelled' ? 'return' : status === 'no_show' ? 'deduct' : undefined,
  coupon: index === 1 || index === 2 ? undefined : { couponId: 40 + index, couponType: 'GENERAL', remainingCount: 5, heldCount: 1, availableCount: 4, expiresAt: new Date('2026-10-01') },
}))

afterEach(cleanup)

function createApi(overrides: Partial<MyReservationsApi> = {}): MyReservationsApi {
  return { getMyReservations: vi.fn().mockResolvedValue(RESERVATIONS), ...overrides }
}

function renderPage(api: MyReservationsApi) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}><MemoryRouter>{children}</MemoryRouter></QueryClientProvider>
  return render(<MyReservationsPage api={api} />, { wrapper: Wrapper })
}

describe('MyReservationsPage', () => {
  it('예약의_클래스_일시_결제와_상태를_표시한다', async () => {
    renderPage(createApi())
    const cards = await screen.findAllByRole('article')
    expect(within(cards[0]).getByRole('heading', { name: '원형초보' })).toBeInTheDocument()
    expect(within(cards[0]).getByText('09:00')).toBeInTheDocument()
    expect(within(cards[0]).getByText('쿠폰')).toBeInTheDocument()
    expect(within(cards[0]).getByText('관리자 승인대기')).toBeInTheDocument()
  })

  it('예약_상태_여덟_개를_각각_다른_문구로_표시한다', async () => {
    renderPage(createApi())
    for (const label of ['관리자 승인대기', '입금 확인 대기', '입금 기한 만료', '예약 확정', '수업 완료', '예약 반려', '예약 취소', '노쇼']) {
      expect(await screen.findByText(label)).toBeInTheDocument()
    }
  })

  it('반려와_취소를_구분하고_반려_사유를_표시한다', async () => {
    renderPage(createApi())
    expect(await screen.findByText('예약 반려')).toBeInTheDocument()
    expect(screen.getByText('예약 취소')).toBeInTheDocument()
    expect(screen.getByText('해당 수업 운영이 어렵습니다.')).toBeInTheDocument()
  })

  it('쿠폰_예정_사용과_최종_처리_결과를_표시한다', async () => {
    renderPage(createApi())
    const coupon = (await screen.findByText(/사용 예정 쿠폰 #40/)).parentElement
    expect(coupon).toHaveTextContent('점유 후 사용 가능 4회')
    expect(screen.getAllByText('최종 쿠폰 처리')).toHaveLength(3)
    expect(screen.getAllByText('1회 차감')).toHaveLength(2)
    expect(screen.getByText('반환')).toBeInTheDocument()
  })

  it('입금_기한과_만료_후_관리자_연락을_안내한다', async () => {
    renderPage(createApi())
    expect((await screen.findAllByText('입금 기한')).length).toBeGreaterThan(0)
    expect(screen.getByText('입금 확인이 늦었다면 관리자에게 연락해 주세요.')).toBeInTheDocument()
  })

  it('서버가_반환한_예약_순서를_유지한다', async () => {
    renderPage(createApi())
    const cards = await screen.findAllByRole('article')
    expect(within(cards[0]).getByText('관리자 승인대기')).toBeInTheDocument()
    expect(within(cards[7]).getByText('노쇼')).toBeInTheDocument()
  })

  it('빈_예약_목록을_표시한다', async () => {
    renderPage(createApi({ getMyReservations: vi.fn().mockResolvedValue([]) }))
    expect(await screen.findByText('아직 예약 내역이 없습니다.')).toBeInTheDocument()
  })

  it.each([[401, '다시 로그인'], [500, '불러오지 못했습니다']])('%i_조회_오류를_구분한다', async (status, message) => {
    const error = new ResponseError(new Response(null, { status }), 'failed')
    renderPage(createApi({ getMyReservations: vi.fn().mockRejectedValue(error) }))
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
  })

  it('320px_화면에서도_전체_상태와_새_예약_링크를_확인할_수_있다', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 320 })
    renderPage(createApi())
    expect(await screen.findByText('관리자 승인대기')).toBeInTheDocument()
    expect(screen.getByText('노쇼')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '새 예약' })).toHaveAttribute('href', '/reservations')
  })

  it('활성_예약에만_변경_링크를_표시한다', async () => {
    renderPage(createApi())
    const links = await screen.findAllByRole('link', { name: '예약 변경' })
    expect(links).toHaveLength(3)
    expect(links[0]).toHaveAttribute('href', '/my/reservations/1/change')
  })

  it('활성_예약에만_취소_링크를_표시한다', async () => {
    renderPage(createApi())
    const links = await screen.findAllByRole('link', { name: '취소하기' })
    expect(links).toHaveLength(3)
    expect(links[0]).toHaveAttribute('href', '/my/reservations/1/cancel')
  })
})
