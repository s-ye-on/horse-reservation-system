import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import type {
  AdminWeeklyOperationsCalendarResponse,
  AdminWeeklyOperationsReservationResponse,
  AdminWeeklyOperationsTimeSlotResponse,
} from '@horse/api-client'
import type { AdminWeeklyOperationsCalendarApi } from './admin-weekly-operations-calendar.api'
import { AdminWeeklyOperationsCalendarPage } from './admin-weekly-operations-calendar-page'

const NOW = new Date('2026-08-31T15:30:00.000Z')

afterEach(() => cleanup())

function reservation(
  overrides: Partial<AdminWeeklyOperationsReservationResponse> = {},
): AdminWeeklyOperationsReservationResponse {
  return {
    reservationId: 101,
    memberId: 11,
    memberName: '김기승',
    ridingClass: 'ROUND_TROT',
    status: 'confirmed',
    ...overrides,
  }
}

function timeSlot(
  overrides: Partial<AdminWeeklyOperationsTimeSlotResponse> = {},
): AdminWeeklyOperationsTimeSlotResponse {
  return {
    timeSlotId: 1,
    lessonDate: new Date('2026-08-31'),
    startTime: '10:00:00',
    endTime: '11:00:00',
    totalCapacity: 4,
    roundArenaCapacity: 2,
    classCapacities: {},
    closed: false,
    reservations: [reservation()],
    ...overrides,
  }
}

function response(
  overrides: Partial<AdminWeeklyOperationsCalendarResponse> = {},
): AdminWeeklyOperationsCalendarResponse {
  return {
    referenceDate: new Date('2026-09-01'),
    weekStartDate: new Date('2026-08-31'),
    weekEndDate: new Date('2026-09-06'),
    timeSlots: [timeSlot()],
    ...overrides,
  }
}

function createApi(
  getCalendar: AdminWeeklyOperationsCalendarApi['getCalendar'] = vi.fn().mockResolvedValue(response()),
): AdminWeeklyOperationsCalendarApi {
  return { getCalendar }
}

function renderPage(api: AdminWeeklyOperationsCalendarApi) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  )
  return render(<AdminWeeklyOperationsCalendarPage api={api} now={NOW} />, { wrapper })
}

describe('AdminWeeklyOperationsCalendarPage', () => {
  it('서울_현재일이_속한_월요일부터_일요일까지_조회한다', async () => {
    const getCalendar = vi.fn().mockResolvedValue(response())
    renderPage(createApi(getCalendar))

    expect(await screen.findByRole('heading', { name: '주간 운영 캘린더' })).toBeInTheDocument()
    await waitFor(() => expect(getCalendar).toHaveBeenCalledWith('2026-09-01'))
    expect(screen.getByRole('heading', { name: '2026. 8. 31. - 2026. 9. 6.' })).toBeInTheDocument()
    const board = screen.getByRole('region', { name: '주간 운영 시간표' })
    expect(within(board).getByRole('columnheader', { name: /8월 31일 \(월\)/ })).toBeInTheDocument()
    expect(within(board).getByRole('columnheader', { name: /9월 6일 \(일\)/ })).toBeInTheDocument()
    expect(within(board).getByRole('table')).toHaveAccessibleName(/실제 운영 시간대와 표시 예약/)
    expect(board).toHaveAttribute('tabindex', '0')
  })

  it('이전_다음_주로_이동하고_오늘은_현재_주로_복귀한다', async () => {
    const getCalendar = vi.fn().mockImplementation((referenceDate: string) => (
      Promise.resolve(response({ referenceDate: new Date(referenceDate) }))
    ))
    renderPage(createApi(getCalendar))
    await screen.findByRole('region', { name: '주간 운영 시간표' })

    fireEvent.click(screen.getByRole('button', { name: '이전 주' }))
    await waitFor(() => expect(getCalendar).toHaveBeenLastCalledWith('2026-08-25'))
    fireEvent.click(screen.getByRole('button', { name: '다음 주' }))
    await waitFor(() => expect(getCalendar).toHaveBeenLastCalledWith('2026-09-01'))
    fireEvent.click(screen.getByRole('button', { name: '다음 주' }))
    await waitFor(() => expect(getCalendar).toHaveBeenLastCalledWith('2026-09-08'))
    fireEvent.click(screen.getByRole('button', { name: '오늘' }))
    await waitFor(() => expect(getCalendar).toHaveBeenLastCalledWith('2026-09-01'))
  })

  it('실제_빈_TimeSlot은_예약_없음으로_표시하고_가짜_슬롯은_만들지_않는다', async () => {
    renderPage(createApi(vi.fn().mockResolvedValue(response({
      timeSlots: [
        timeSlot({ timeSlotId: 1, reservations: [] }),
        timeSlot({
          timeSlotId: 2,
          lessonDate: new Date('2026-09-02'),
          startTime: '14:00:00',
          endTime: '15:00:00',
        }),
      ],
    }))))

    const board = await screen.findByRole('region', { name: '주간 운영 시간표' })
    expect(within(board).getByText('현재 표시 대상 예약이 없습니다.')).toBeInTheDocument()
    expect(board.querySelectorAll('[data-timeslot-id]')).toHaveLength(2)
    expect(board.querySelector('[data-timeslot-id="1"]')).toHaveTextContent('10:00–11:00')
    expect(board.querySelector('[data-timeslot-id="2"]')).toHaveTextContent('14:00–15:00')
    const emptyCell = board.querySelector('[data-calendar-cell="2026-09-01"][data-calendar-start-time="10:00:00"]')!
    expect(emptyCell).toHaveTextContent('표시할 운영 시간대가 없습니다.')
    expect(emptyCell.querySelector('article')).toBeNull()
    expect(board).not.toHaveTextContent('휴무')
    expect(board).not.toHaveTextContent('예약 가능')
  })

  it('같은_TimeSlot의_여러_회원과_클래스와_원래_상태를_모두_표시한다', async () => {
    const reservations: AdminWeeklyOperationsReservationResponse[] = [
      reservation({ reservationId: 1, memberName: '김승인', ridingClass: 'ROUND_BEGINNER', status: 'pending_admin_approval' }),
      reservation({ reservationId: 2, memberName: '이입금', ridingClass: 'DRESSAGE', status: 'pending_payment' }),
      reservation({ reservationId: 3, memberName: '박확정', ridingClass: 'JUMPING', status: 'confirmed' }),
      reservation({ reservationId: 4, memberName: '최완료', ridingClass: 'CANTER', status: 'completed' }),
    ]
    renderPage(createApi(vi.fn().mockResolvedValue(response({
      timeSlots: [timeSlot({ reservations, closed: true })],
    }))))

    const board = await screen.findByRole('region', { name: '주간 운영 시간표' })
    const slot = within(board).getByRole('article', { name: /8월 31일.*10:00 수업 시간대/ })
    expect(within(slot).getByText('신규 예약 마감')).toBeInTheDocument()
    expect(within(slot).getByText('김승인')).toBeInTheDocument()
    expect(within(slot).getByText('원형초보')).toBeInTheDocument()
    expect(within(slot).getByText('쿠폰 승인대기')).toBeInTheDocument()
    expect(within(slot).getByText('이입금')).toBeInTheDocument()
    expect(within(slot).getByText('마장마술')).toBeInTheDocument()
    expect(within(slot).getByText('입금대기')).toBeInTheDocument()
    expect(within(slot).getByText('박확정')).toBeInTheDocument()
    expect(within(slot).getByText('장애물')).toBeInTheDocument()
    expect(within(slot).getByText('예약 확정')).toBeInTheDocument()
    expect(within(slot).getByText('최완료')).toBeInTheDocument()
    expect(within(slot).getByText('구보')).toBeInTheDocument()
    expect(within(slot).getByText('수업 완료')).toBeInTheDocument()
    expect(slot.querySelectorAll('[data-reservation-status]')).toHaveLength(reservations.length)
  })

  it('시간_행을_정렬하고_같은_날짜와_시간의_실제_슬롯을_한_셀에_모두_유지한다', async () => {
    renderPage(createApi(vi.fn().mockResolvedValue(response({
      timeSlots: [
        timeSlot({ timeSlotId: 31, startTime: '14:00:00', endTime: '15:00:00' }),
        timeSlot({ timeSlotId: 32, startTime: '09:00:00', endTime: '10:00:00' }),
        timeSlot({ timeSlotId: 33, startTime: '09:00:00', endTime: '10:30:00' }),
      ],
    }))))

    const board = await screen.findByRole('region', { name: '주간 운영 시간표' })
    const timeHeadings = within(board).getAllByRole('rowheader')
      .map((element) => element.textContent?.trim() ?? '')
      .filter((text) => /^\d{2}:\d{2}$/.test(text))
    expect(timeHeadings).toEqual([
      '09:00',
      '14:00',
    ])
    const nineAmCell = board.querySelector('[data-calendar-cell="2026-08-31"][data-calendar-start-time="09:00:00"]') as HTMLElement
    const headerIds = nineAmCell.getAttribute('headers')!.split(' ')
    expect(headerIds).toHaveLength(2)
    expect(headerIds.map((id) => document.getElementById(id)?.getAttribute('scope'))).toEqual(['row', 'col'])
    expect(within(nineAmCell).getAllByRole('article')).toHaveLength(2)
    expect(within(nineAmCell).getAllByRole('article').map((slot) => slot.getAttribute('data-timeslot-id'))).toEqual([
      '32',
      '33',
    ])
  })

  it('운영_TimeSlot이_0건이면_예약_없음과_다른_주간_빈_상태를_표시한다', async () => {
    renderPage(createApi(vi.fn().mockResolvedValue(response({ timeSlots: [] }))))

    expect(await screen.findByText('이 주에 표시할 운영 시간대가 없습니다.')).toBeInTheDocument()
    expect(screen.queryByText('현재 표시 대상 예약이 없습니다.')).not.toBeInTheDocument()
    expect(screen.queryAllByRole('article')).toHaveLength(0)
  })

  it('로딩과_API_오류를_빈_상태와_구분하고_재시도한다', async () => {
    const getCalendar = vi.fn()
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce(response())
    renderPage(createApi(getCalendar))

    expect(screen.getByRole('status')).toHaveTextContent('불러오는 중')
    expect(await screen.findByRole('alert')).toHaveTextContent('불러오지 못했습니다')
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))

    const board = await screen.findByRole('region', { name: '주간 운영 시간표' })
    expect(within(board).getByText('김기승')).toBeInTheDocument()
    expect(getCalendar).toHaveBeenCalledTimes(2)
  })

  it('320px에서도_주_이동과_실제_슬롯_정보를_조작하고_읽을_수_있다', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 320 })
    renderPage(createApi())
    const board = await screen.findByRole('region', { name: '날짜별 운영 시간대' })
    const mondayGroup = within(board).getByRole('region', { name: '8월 31일 (월)' })

    expect(screen.getByRole('button', { name: '이전 주' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다음 주' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '오늘' })).toBeInTheDocument()
    expect(within(board).getByRole('heading', { name: '8월 31일 (월)' })).toBeInTheDocument()
    expect(within(mondayGroup).getByRole('article', { name: '8월 31일 (월) 10:00 수업 시간대' })).toBeInTheDocument()
    expect(within(mondayGroup).getByText(/전체 정원/)).toHaveTextContent('전체 정원 4명')
    expect(within(mondayGroup).getByText(/원형마장/)).toHaveTextContent('원형마장 2명')
    const emptyDay = within(board).getByRole('region', { name: '9월 1일 (화)' })
    expect(within(emptyDay).getByText('표시할 운영 시간대가 없습니다.')).toBeInTheDocument()
    expect(screen.queryByText(/^confirmed$/)).not.toBeInTheDocument()
    expect(screen.queryByText(/^ROUND_TROT$/)).not.toBeInTheDocument()
  })

  it('클래스별_정원은_실제_map과_9개_라벨을_사용하고_0도_유지한다', async () => {
    renderPage(createApi(vi.fn().mockResolvedValue(response({ timeSlots: [timeSlot({
      classCapacities: { FIRST_RIDE: 0, ROUND_BEGINNER: 1, ROUND_TROT: 2,
        LARGE_ARENA_BEGINNER: 3, LARGE_ARENA_TROT: 4, CANTER_BEGINNER: 5,
        CANTER: 6, DRESSAGE: 7, JUMPING: 8 },
    })] }))))
    const board = await screen.findByRole('region', { name: '주간 운영 시간표' })
    const button = within(board).getByRole('button', { name: '8월 31일 (월) 10:00 클래스별 정원' })
    const panel = document.getElementById(button.getAttribute('aria-controls')!)!
    expect(panel).toHaveAttribute('hidden')
    fireEvent.click(button)
    expect(button).toHaveAttribute('aria-expanded', 'true')
    expect(panel).not.toHaveAttribute('hidden')
    expect(within(panel).getAllByRole('listitem').map((item) => item.textContent)).toEqual([
      '왕초보0명', '원형초보1명', '원형 속보2명', '대마장초보3명', '대마장 속보4명',
      '구보초보5명', '구보6명', '마장마술7명', '장애물8명',
    ])
    expect(board).not.toHaveTextContent(/잔여|점유|예약 가능/)
    const ids = [...document.querySelectorAll('[id]')].map((element) => element.id)
    expect(new Set(ids).size).toBe(ids.length)
    fireEvent.click(button)
    expect(panel).toHaveAttribute('hidden')
  })

  it('서버의_실제_종료시각을_표시하며_슬롯에는_조회_disclosure만_제공한다', async () => {
    renderPage(createApi(vi.fn().mockResolvedValue(response({ timeSlots: [timeSlot({
      startTime: '09:15:00', endTime: '09:47:00', totalCapacity: 8, roundArenaCapacity: 4,
    })] }))))
    const board = await screen.findByRole('region', { name: '주간 운영 시간표' })
    expect(within(board).getByText('09:15–09:47')).toBeInTheDocument()
    expect(within(board).getAllByRole('button')).toHaveLength(1)
    expect(within(board).getByText('김기승')).toBeInTheDocument()
    expect(board).not.toHaveTextContent('신규 예약 마감')
  })

  it('빈_정원_map과_알_수_없는_키도_중립적으로_표시한다', async () => {
    renderPage(createApi(vi.fn().mockResolvedValue(response({ timeSlots: [
      timeSlot({ timeSlotId: 1, classCapacities: {} }),
      timeSlot({ timeSlotId: 2, startTime: '14:00:00', classCapacities: { FUTURE_CLASS: 0 } }),
    ] }))))
    const board = await screen.findByRole('region', { name: '주간 운영 시간표' })
    for (const button of within(board).getAllByRole('button')) fireEvent.click(button)
    expect(within(board).getByText('클래스별 정원 정보가 없습니다.')).toBeInTheDocument()
    expect(within(board).getByText('클래스 확인 필요')).toBeInTheDocument()
    expect(within(board).getByText('0명')).toBeInTheDocument()
    expect(board).not.toHaveTextContent('FUTURE_CLASS')
  })
})
