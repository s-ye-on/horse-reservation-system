import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { ResponseError, type MemberReservationPageResponse, type MemberReservationResponse } from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import type { MyReservationsApi, MyReservationsQuery } from './my-reservations.api'
import { MyReservationsPage } from './my-reservations-page'

const ALLOWED_ACTIONS: MemberReservationResponse['actions'] = {
  change: { allowed: true, blockedReason: null },
  cancel: { allowed: true, blockedReason: null },
  complete: { allowed: false, blockedReason: null },
  noShow: { allowed: false, blockedReason: null },
  approve: { allowed: false, blockedReason: null },
}

const PAST_ACTIONS: MemberReservationResponse['actions'] = {
  change: { allowed: false, blockedReason: 'RESERVATION_LESSON_ALREADY_STARTED' },
  cancel: { allowed: false, blockedReason: 'RESERVATION_LESSON_ALREADY_STARTED' },
  complete: { allowed: false, blockedReason: null },
  noShow: { allowed: false, blockedReason: null },
  approve: { allowed: false, blockedReason: null },
}

const RESERVATIONS: MemberReservationResponse[] = [
  reservation(1, 'pending_admin_approval', 'UPCOMING', '2026-08-10', ALLOWED_ACTIONS),
  reservation(2, 'pending_payment', 'UPCOMING', '2026-08-11', ALLOWED_ACTIONS, 'single_payment'),
  reservation(3, 'confirmed', 'UPCOMING', '2026-08-12', ALLOWED_ACTIONS),
  reservation(4, 'approval_expired', 'UPCOMING', '2026-08-13', {
    ...PAST_ACTIONS,
    change: { allowed: false, blockedReason: 'RESERVATION_INVALID_STATUS' },
    cancel: { allowed: false, blockedReason: 'RESERVATION_INVALID_STATUS' },
  }),
  reservation(8, 'no_show', 'PAST', '2026-07-18', PAST_ACTIONS),
  reservation(7, 'cancelled', 'PAST', '2026-07-17', PAST_ACTIONS),
  reservation(6, 'rejected', 'PAST', '2026-07-16', PAST_ACTIONS),
  reservation(5, 'completed', 'PAST', '2026-07-15', PAST_ACTIONS),
]

afterEach(cleanup)

function reservation(
  reservationId: number,
  status: string,
  displayGroup: string,
  lessonDate: string,
  actions: MemberReservationResponse['actions'],
  paymentSource = 'coupon',
): MemberReservationResponse {
  return {
    reservationId,
    classType: reservationId === 6 ? 'DRESSAGE' : reservationId === 7 ? 'JUMPING' : 'ROUND_BEGINNER',
    lessonDate: new Date(lessonDate),
    startTime: '09:00:00',
    status,
    displayGroup,
    actions,
    paymentSource,
    paymentDueAt: paymentSource === 'single_payment' ? new Date('2026-08-01T14:00:00+09:00') : null,
    rejectionReason: status === 'rejected' ? '해당 수업 운영이 어렵습니다.' : null,
    couponAction: status === 'completed' || status === 'no_show' ? 'deduct' : status === 'cancelled' ? 'return' : null,
    coupon: paymentSource === 'coupon'
      ? { couponId: 40 + reservationId, couponType: 'GENERAL', status: 'active', remainingCount: 5, heldCount: 1, availableCount: 4, expiresAt: new Date('2026-10-01') }
      : null,
    approvalRequestedAt: new Date('2026-07-01T01:00:00Z'),
    adminConfirmedAt: null,
    rejectedAt: status === 'rejected' ? new Date('2026-07-16T01:00:00Z') : null,
    cancelledAt: status === 'cancelled' ? new Date('2026-07-17T01:00:00Z') : null,
  }
}

function reservationPage(
  content: MemberReservationResponse[],
  page = 0,
  totalPages = content.length === 0 ? 0 : 1,
  totalElements = content.length,
): MemberReservationPageResponse {
  return { content, page, size: 20, totalElements, totalPages, hasNext: page + 1 < totalPages }
}

function createApi(overrides: Partial<MyReservationsApi> = {}): MyReservationsApi {
  return {
    getMyReservations: vi.fn((query: MyReservationsQuery) => {
      const content = RESERVATIONS.filter((reservation) =>
        reservation.displayGroup === query.displayGroup
        && (query.status === undefined || reservation.status === query.status))
      return Promise.resolve(reservationPage(content))
    }),
    ...overrides,
  }
}

function renderPage(api: MyReservationsApi) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}><MemoryRouter>{children}</MemoryRouter></QueryClientProvider>
  return render(<MyReservationsPage api={api} />, { wrapper: Wrapper })
}

describe('MyReservationsPage', () => {
  it('예정_예약을_기본_탭으로_표시한다', async () => {
    renderPage(createApi())
    const upcomingTab = await screen.findByRole('tab', { name: /예정 예약/ })
    expect(upcomingTab).toHaveAttribute('aria-selected', 'true')
    expect(await screen.findAllByRole('article')).toHaveLength(4)
    expect(screen.getByText('관리자 승인대기')).toBeInTheDocument()
    expect(screen.queryByText('수업 완료')).not.toBeInTheDocument()
  })

  it('방향키로_지난_예약_탭을_선택하고_서버_순서를_유지한다', async () => {
    renderPage(createApi())
    const upcomingTab = await screen.findByRole('tab', { name: /예정 예약/ })
    fireEvent.keyDown(upcomingTab, { key: 'ArrowRight' })

    expect(screen.getByRole('tab', { name: /지난 예약/ })).toHaveAttribute('aria-selected', 'true')
    const cards = await screen.findAllByRole('article')
    expect(within(cards[0]).getByText('노쇼')).toBeInTheDocument()
    expect(within(cards[3]).getByText('수업 완료')).toBeInTheDocument()
  })

  it('예정_예약을_상태별로_필터링한다', async () => {
    renderPage(createApi())
    await screen.findByRole('tab', { name: /예정 예약/ })
    fireEvent.click(screen.getByRole('button', { name: '입금대기' }))

    expect(await screen.findAllByRole('article')).toHaveLength(1)
    expect(screen.getByText('입금 확인 대기')).toBeInTheDocument()
    expect(screen.getByText('입금 기한')).toBeInTheDocument()
  })

  it('서버가_허용한_예약만_변경과_취소_링크를_제공한다', async () => {
    renderPage(createApi())
    expect(await screen.findAllByRole('link', { name: '예약 변경' })).toHaveLength(3)
    expect(screen.getAllByRole('link', { name: '취소하기' })).toHaveLength(3)
    expect(screen.getByText('현재 예약 상태에서는 변경하거나 취소할 수 없습니다.')).toBeInTheDocument()
  })

  it('지난_예약에는_행동_버튼_대신_서버_차단_이유를_표시한다', async () => {
    renderPage(createApi())
    fireEvent.click(await screen.findByRole('tab', { name: /지난 예약/ }))

    expect(await screen.findAllByText('수업 시작 이후에는 예약을 변경하거나 취소할 수 없습니다.')).toHaveLength(4)
    expect(screen.queryByRole('link', { name: '예약 변경' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '취소하기' })).not.toBeInTheDocument()
  })

  it('반려와_취소_및_쿠폰_처리_결과를_구분한다', async () => {
    renderPage(createApi())
    fireEvent.click(await screen.findByRole('tab', { name: /지난 예약/ }))

    expect(await screen.findByText('예약 반려')).toBeInTheDocument()
    expect(screen.getByText('예약 취소')).toBeInTheDocument()
    expect(screen.getByText('해당 수업 운영이 어렵습니다.')).toBeInTheDocument()
    expect(screen.getAllByText('1회 차감')).toHaveLength(2)
    expect(screen.getByText('반환')).toBeInTheDocument()
  })

  it('서버_Page와_필터를_사용하고_탭_건수는_totalElements로_표시한다', async () => {
    const upcoming = RESERVATIONS.filter((reservation) => reservation.displayGroup === 'UPCOMING')
    const past = RESERVATIONS.filter((reservation) => reservation.displayGroup === 'PAST')
    const getMyReservations = vi.fn((query: MyReservationsQuery) => {
      const source = query.displayGroup === 'UPCOMING' ? upcoming : past
      const filtered = query.status === undefined
        ? source
        : source.filter((reservation) => reservation.status === query.status)
      const content = query.size === 1
        ? filtered.slice(0, 1)
        : filtered.slice(query.page, query.page + 1)
      const totalPages = filtered.length
      return Promise.resolve(reservationPage(content, query.page, totalPages, filtered.length))
    })
    renderPage(createApi({ getMyReservations }))

    expect(await screen.findByRole('tab', { name: new RegExp(`예정 예약\\s*${upcoming.length}`) })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: new RegExp(`지난 예약\\s*${past.length}`) })).toBeInTheDocument()
    const navigation = screen.getByRole('navigation', { name: '예정 예약 페이지' })
    expect(within(navigation).getByRole('button', { name: '이전' })).toBeDisabled()
    fireEvent.click(within(navigation).getByRole('button', { name: '다음' }))
    expect(await within(navigation).findByText(`2 / ${upcoming.length} 페이지`)).toBeInTheDocument()
    expect(getMyReservations).toHaveBeenCalledWith(expect.objectContaining({
      displayGroup: 'UPCOMING',
      page: 1,
      status: undefined,
    }))

    fireEvent.click(screen.getByRole('button', { name: '입금대기' }))
    await waitFor(() => expect(getMyReservations).toHaveBeenCalledWith(expect.objectContaining({
      displayGroup: 'UPCOMING',
      page: 0,
      status: 'pending_payment',
    })))

    fireEvent.click(screen.getByRole('tab', { name: new RegExp(`지난 예약\\s*${past.length}`) }))
    await waitFor(() => expect(getMyReservations).toHaveBeenCalledWith(expect.objectContaining({
      displayGroup: 'PAST',
      page: 0,
      status: undefined,
    })))
  })

  it('늦게_도착한_이전_탭_응답이_현재_탭을_덮지_않는다', async () => {
    const upcoming = RESERVATIONS.filter((reservation) => reservation.displayGroup === 'UPCOMING')
    const past = RESERVATIONS.filter((reservation) => reservation.displayGroup === 'PAST')
    let resolveUpcoming: ((value: MemberReservationPageResponse) => void) | undefined
    let resolvePast: ((value: MemberReservationPageResponse) => void) | undefined
    const getMyReservations = vi.fn((query: MyReservationsQuery) => {
      if (query.size === 1) {
        const source = query.displayGroup === 'UPCOMING' ? upcoming : past
        return Promise.resolve(reservationPage(source.slice(0, 1), 0, source.length, source.length))
      }
      return new Promise<MemberReservationPageResponse>((resolve) => {
        if (query.displayGroup === 'UPCOMING') resolveUpcoming = resolve
        else resolvePast = resolve
      })
    })
    renderPage(createApi({ getMyReservations }))

    fireEvent.click(await screen.findByRole('tab', { name: /지난 예약/ }))
    resolvePast?.(reservationPage(past))
    expect(await screen.findByText('노쇼')).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /지난 예약/ })).toHaveAttribute('aria-selected', 'true')

    resolveUpcoming?.(reservationPage(upcoming))
    await waitFor(() => {
      expect(screen.getByRole('tab', { name: /지난 예약/ })).toHaveAttribute('aria-selected', 'true')
      expect(screen.getAllByRole('article')).toHaveLength(past.length)
    })
  })

  it('빈_예약_목록을_표시한다', async () => {
    renderPage(createApi({ getMyReservations: vi.fn().mockResolvedValue(reservationPage([])) }))
    expect(await screen.findByText('아직 예약 내역이 없습니다.')).toBeInTheDocument()
  })

  it('탭_건수_조회_실패가_활성_예약_목록을_막지_않고_재시도된다', async () => {
    const upcoming = RESERVATIONS.filter((reservation) => reservation.displayGroup === 'UPCOMING')
    const past = RESERVATIONS.filter((reservation) => reservation.displayGroup === 'PAST')
    let pastCountRequestCount = 0
    const getMyReservations = vi.fn((query: MyReservationsQuery) => {
      const source = query.displayGroup === 'UPCOMING' ? upcoming : past
      if (query.size === 1 && query.displayGroup === 'PAST') {
        pastCountRequestCount += 1
        if (pastCountRequestCount === 1) return Promise.reject(new Error('count failed'))
      }
      return Promise.resolve(reservationPage(
        query.size === 1 ? source.slice(0, 1) : source,
        0,
        1,
        source.length,
      ))
    })
    renderPage(createApi({ getMyReservations }))

    expect(await screen.findByText('관리자 승인대기')).toBeInTheDocument()
    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent('예약 건수를 불러오지 못했습니다.')
    expect(screen.getByRole('tab', { name: /지난 예약—/ })).toBeInTheDocument()
    fireEvent.click(within(alert).getByRole('button', { name: '다시 시도' }))

    expect(await screen.findByRole('tab', { name: new RegExp(`지난 예약\\s*${past.length}`) })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it.each([[401, '다시 로그인'], [500, '불러오지 못했습니다']])('%i_조회_오류를_구분한다', async (status, message) => {
    const error = new ResponseError(new Response(null, { status }), 'failed')
    renderPage(createApi({ getMyReservations: vi.fn().mockRejectedValue(error) }))
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
  })

  it('320px_화면에서도_탭과_새_예약_링크를_확인할_수_있다', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 320 })
    renderPage(createApi())
    expect(await screen.findByRole('tab', { name: /예정 예약/ })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: /지난 예약/ })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '새 예약' })).toHaveAttribute('href', '/reservations')
  })
})
