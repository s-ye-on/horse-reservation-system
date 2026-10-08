import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { ResponseError, type AdminMonthlyRideStatisticsResponse } from '@horse/api-client'
import type {
  AdminMonthlyRideStatisticsApi,
  MonthlyRideType,
} from './admin-monthly-ride-statistics.api'
import { AdminMonthlyRideStatisticsPage } from './admin-monthly-ride-statistics-page'

const NOW = new Date('2026-08-31T15:30:00.000Z')

afterEach(() => cleanup())

function response(
  overrides: Partial<AdminMonthlyRideStatisticsResponse> = {},
): AdminMonthlyRideStatisticsResponse {
  return {
    month: '2026-09',
    rideType: 'ALL',
    totalCompletedRideCount: 12,
    topCompletedRideCount: 5,
    leaders: [{ memberId: 11, memberName: '김기승', completedRideCount: 5 }],
    ...overrides,
  }
}

function createApi(
  getStatistics: AdminMonthlyRideStatisticsApi['getStatistics'] = vi.fn().mockResolvedValue(response()),
): AdminMonthlyRideStatisticsApi {
  return { getStatistics }
}

function renderPage(api: AdminMonthlyRideStatisticsApi) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  )
  return render(<AdminMonthlyRideStatisticsPage api={api} now={NOW} />, { wrapper })
}

describe('AdminMonthlyRideStatisticsPage', () => {
  it('서울_현재_월의_전체_완료_기승과_단독_1위를_표시한다', async () => {
    const getStatistics = vi.fn().mockResolvedValue(response())
    renderPage(createApi(getStatistics))

    expect(await screen.findByRole('heading', { name: '월간 기승 현황' })).toBeInTheDocument()
    await waitFor(() => expect(getStatistics).toHaveBeenCalledWith('2026-09', 'ALL'))
    expect(screen.getByLabelText('조회 월')).toHaveValue('2026-09')
    expect(screen.getByRole('button', { name: '전체 기승' })).toHaveAttribute('aria-pressed', 'true')
    expect(metric('총 기승 횟수')).toHaveTextContent('12')
    expect(metric('최다 기승 횟수')).toHaveTextContent('5')
    expect(screen.getByRole('heading', { name: '최다 기승 회원' })).toBeInTheDocument()
    expect(screen.getByText('김기승')).toBeInTheDocument()
    expect(screen.getByText('5회')).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('2026년 9월 전체 기승 조회 완료. 총 기승 횟수 12회, 최다 기승 횟수 5회, 최다 회원 1명.')
    expect(screen.getByRole('status')).toHaveAttribute('aria-atomic', 'true')
  })

  it('이전_다음_월로_이동하고_이번_달로_복귀한다', async () => {
    const getStatistics = vi.fn().mockImplementation((month: string, rideType: MonthlyRideType) => (
      Promise.resolve(response({ month, rideType }))
    ))
    renderPage(createApi(getStatistics))
    await screen.findByText('김기승')

    fireEvent.click(screen.getByRole('button', { name: '이전 달' }))
    await waitFor(() => expect(getStatistics).toHaveBeenLastCalledWith('2026-08', 'ALL'))
    fireEvent.click(screen.getByRole('button', { name: '다음 달' }))
    await waitFor(() => expect(getStatistics).toHaveBeenLastCalledWith('2026-09', 'ALL'))
    fireEvent.change(screen.getByLabelText('조회 월'), { target: { value: '2025-12' } })
    await waitFor(() => expect(getStatistics).toHaveBeenLastCalledWith('2025-12', 'ALL'))
    fireEvent.click(screen.getByRole('button', { name: '이번 달' }))
    await waitFor(() => expect(getStatistics).toHaveBeenLastCalledWith('2026-09', 'ALL'))
  })

  it.each([
    ['일반 기승', 'GENERAL'],
    ['마장마술', 'DRESSAGE'],
    ['장애물', 'JUMPING'],
    ['전체 기승', 'ALL'],
  ] as const)('%s_선택은_같은_조건의_전체_집계를_갱신한다', async (label, rideType) => {
    const getStatistics = vi.fn().mockImplementation((month: string, selectedType: MonthlyRideType) => (
      Promise.resolve(response({
        month,
        rideType: selectedType,
        totalCompletedRideCount: selectedType === rideType ? 7 : 12,
        topCompletedRideCount: selectedType === rideType ? 3 : 5,
        leaders: [{ memberId: 21, memberName: `${label} 회원`, completedRideCount: 3 }],
      }))
    ))
    renderPage(createApi(getStatistics))
    await screen.findByRole('heading', { name: '최다 기승 회원' })

    fireEvent.click(screen.getByRole('button', { name: label }))

    await waitFor(() => expect(getStatistics).toHaveBeenLastCalledWith('2026-09', rideType))
    expect(await screen.findByText(`${label} 회원`)).toBeInTheDocument()
    expect(metric('총 기승 횟수')).toHaveTextContent('7')
    expect(metric('최다 기승 횟수')).toHaveTextContent('3')
  })

  it('공동_최다_기승_회원을_모두_표시한다', async () => {
    renderPage(createApi(vi.fn().mockResolvedValue(response({
      totalCompletedRideCount: 16,
      topCompletedRideCount: 6,
      leaders: [
        { memberId: 11, memberName: '김공동', completedRideCount: 6 },
        { memberId: 12, memberName: '이공동', completedRideCount: 6 },
      ],
    }))))

    expect(await screen.findByRole('heading', { name: '공동 최다 기승 회원' })).toBeInTheDocument()
    expect(screen.getByText('김공동')).toBeInTheDocument()
    expect(screen.getByText('이공동')).toBeInTheDocument()
    expect(screen.getAllByText('6회')).toHaveLength(2)
  })

  it('0건은_가짜_1위_없이_빈_상태로_표시한다', async () => {
    renderPage(createApi(vi.fn().mockResolvedValue(response({
      totalCompletedRideCount: 0,
      topCompletedRideCount: 0,
      leaders: [],
    }))))

    expect(await screen.findByText('선택한 월과 기승 종류에 완료된 수업이 없습니다.')).toBeInTheDocument()
    expect(metric('총 기승 횟수')).toHaveTextContent('0')
    expect(screen.queryByRole('heading', { name: /최다 기승 회원/ })).not.toBeInTheDocument()
  })

  it('로딩과_API_오류를_빈_상태와_구분하고_재시도한다', async () => {
    const getStatistics = vi.fn()
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce(response())
    renderPage(createApi(getStatistics))

    expect(screen.getByRole('status')).toHaveTextContent('불러오는 중')
    expect(await screen.findByRole('alert')).toHaveTextContent('불러오지 못했습니다')
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))

    expect(await screen.findByText('김기승')).toBeInTheDocument()
    expect(getStatistics).toHaveBeenCalledTimes(2)
  })

  it('내부_enum을_노출하지_않고_선택_상태와_조회_월_설명을_연결한다', async () => {
    renderPage(createApi())
    await screen.findByText('김기승')

    expect(screen.getByRole('button', { name: '이전 달' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다음 달' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '이번 달' })).toBeInTheDocument()
    expect(screen.getByLabelText('조회 월')).toBeInTheDocument()
    expect(screen.getByLabelText('조회 월')).toHaveAttribute('aria-describedby', 'monthly-statistics-period-help')
    expect(screen.getByRole('button', { name: '일반 기승' })).toBeInTheDocument()
    expect(screen.queryByText(/^ALL$/)).not.toBeInTheDocument()
    expect(screen.queryByText(/^GENERAL$/)).not.toBeInTheDocument()
    expect(screen.queryByText(/^DRESSAGE$/)).not.toBeInTheDocument()
    expect(screen.queryByText(/^JUMPING$/)).not.toBeInTheDocument()
  })

  it('서버_총계와_최고_횟수를_목록으로_재계산하지_않고_동률_전원을_표시한다', async () => {
    const leaders = Array.from({ length: 12 }, (_, index) => ({
      memberId: 1000 + index,
      memberName: index < 2 ? '동명이인' : `긴회원이름${'a'.repeat(100)}${index}`,
      completedRideCount: 30,
    }))
    renderPage(createApi(vi.fn().mockResolvedValue(response({
      totalCompletedRideCount: 98765, topCompletedRideCount: 30, leaders,
    }))))
    await screen.findByRole('heading', { name: '공동 최다 기승 회원' })
    expect(metric('총 기승 횟수')).toHaveTextContent('98765')
    expect(metric('최다 기승 횟수')).toHaveTextContent('30')
    expect(screen.getAllByRole('listitem')).toHaveLength(12)
    expect(screen.getAllByText('동명이인')).toHaveLength(2)
    expect(screen.getByText('최다 회원 12명')).toBeInTheDocument()
    expect(screen.queryByText('1000')).not.toBeInTheDocument()
    expect(screen.queryByText(/증감률|매출|평균|예약률/)).not.toBeInTheDocument()
  })

  it.each([
    [400, '조회할 월과 기승 종류를 다시 확인해 주세요.'],
    [401, '관리자 로그인이 필요합니다.'],
    [403, '월간 기승 현황을 조회할 권한이 없습니다.'],
    [500, '월간 기승 현황을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'],
  ])('오류_%s는_0건으로_표시하지_않고_조회_조건_변경으로_복구한다', async (status, message) => {
    const getStatistics = vi.fn().mockRejectedValueOnce(new ResponseError(new Response(null, { status }), 'raw-code'))
      .mockResolvedValue(response({ month: '2026-08', rideType: 'DRESSAGE' }))
    renderPage(createApi(getStatistics))
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.queryByText('총 기승 횟수')).not.toBeInTheDocument()
    expect(screen.getByLabelText('조회 월')).toBeEnabled()
    expect(screen.getByRole('button', { name: '이전 달' })).toBeEnabled()
    fireEvent.click(screen.getByRole('button', { name: '이전 달' }))
    await screen.findByText('김기승')
    fireEvent.click(screen.getByRole('button', { name: '마장마술' }))
    await waitFor(() => expect(getStatistics).toHaveBeenLastCalledWith('2026-08', 'DRESSAGE'))
    expect(screen.getByRole('heading', { name: '2026년 8월 마장마술' })).toBeInTheDocument()
    expect(screen.queryByText(/raw-code|HTTP/)).not.toBeInTheDocument()
  })

  it('12월과_1월_경계를_이동하고_서버_응답의_조회_월과_종류를_표시한다', async () => {
    const getStatistics = vi.fn().mockImplementation((month: string, rideType: MonthlyRideType) => (
      Promise.resolve(response({ month, rideType }))
    ))
    renderPage(createApi(getStatistics))
    await screen.findByText('김기승')
    fireEvent.change(screen.getByLabelText('조회 월'), { target: { value: '2025-12' } })
    await waitFor(() => expect(getStatistics).toHaveBeenLastCalledWith('2025-12', 'ALL'))
    fireEvent.click(screen.getByRole('button', { name: '다음 달' }))
    await screen.findByRole('heading', { name: '2026년 1월 전체 기승' })
    fireEvent.click(screen.getByRole('button', { name: '이전 달' }))
    await screen.findByRole('heading', { name: '2025년 12월 전체 기승' })
  })

  it('빠른_종류_변경_뒤_늦은_이전_응답이_현재_결과를_덮지_않는다', async () => {
    let resolveOld!: (value: AdminMonthlyRideStatisticsResponse) => void
    const getStatistics = vi.fn().mockImplementation((month: string, rideType: MonthlyRideType) => (
      rideType === 'GENERAL'
        ? new Promise<AdminMonthlyRideStatisticsResponse>((resolve) => { resolveOld = resolve })
        : Promise.resolve(response({ month, rideType, leaders: [{ memberId: 1, memberName: '현재 결과', completedRideCount: 5 }] }))
    ))
    renderPage(createApi(getStatistics))
    await screen.findByText('현재 결과')
    fireEvent.click(screen.getByRole('button', { name: '일반 기승' }))
    expect(screen.getByRole('status')).toHaveTextContent('불러오는 중')
    expect(screen.queryByText('총 기승 횟수')).not.toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '장애물' }))
    await screen.findByText('현재 결과')
    await act(async () => {
      resolveOld(response({ rideType: 'GENERAL', leaders: [{ memberId: 2, memberName: '이전 결과', completedRideCount: 5 }] }))
    })
    await waitFor(() => expect(screen.getByRole('heading', { name: '2026년 9월 장애물' })).toBeInTheDocument())
    expect(screen.queryByText('이전 결과')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '장애물' })).toHaveAttribute('aria-pressed', 'true')
  })
})

function metric(label: string) {
  return screen.getByText(label).closest('article') as HTMLElement
}
