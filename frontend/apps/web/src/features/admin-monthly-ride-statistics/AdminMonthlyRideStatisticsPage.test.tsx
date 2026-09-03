import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import type { AdminMonthlyRideStatisticsResponse } from '@horse/api-client'
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

  it('내부_enum을_노출하지_않고_320px에서도_핵심_조작을_제공한다', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 320 })
    renderPage(createApi())
    await screen.findByText('김기승')

    expect(screen.getByRole('button', { name: '이전 달' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다음 달' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '이번 달' })).toBeInTheDocument()
    expect(screen.getByLabelText('조회 월')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '일반 기승' })).toBeInTheDocument()
    expect(screen.queryByText(/^ALL$/)).not.toBeInTheDocument()
    expect(screen.queryByText(/^GENERAL$/)).not.toBeInTheDocument()
    expect(screen.queryByText(/^DRESSAGE$/)).not.toBeInTheDocument()
    expect(screen.queryByText(/^JUMPING$/)).not.toBeInTheDocument()
  })
})

function metric(label: string) {
  return screen.getByText(label).closest('article') as HTMLElement
}
