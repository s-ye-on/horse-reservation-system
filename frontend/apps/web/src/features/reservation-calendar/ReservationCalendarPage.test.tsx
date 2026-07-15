import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { ResponseError, type MemberAvailableTimeSlotsResponse } from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import type { ReservationCalendarApi } from './reservation-calendar.api'
import { ReservationCalendarPage } from './reservation-calendar-page'

const NOW = new Date('2026-07-15T00:00:00Z')
const TIME_SLOTS: MemberAvailableTimeSlotsResponse = {
  date: new Date('2026-07-15'),
  classType: 'ROUND_BEGINNER',
  timeSlots: [
    { timeSlotId: 1, lessonDate: new Date('2026-07-15'), startTime: '09:00:00', closed: false, reservable: true, remainingCapacity: 2 },
    { timeSlotId: 2, lessonDate: new Date('2026-07-15'), startTime: '11:00:00', closed: true, reservable: false, remainingCapacity: 4, unavailableReason: 'CLOSED' },
    { timeSlotId: 3, lessonDate: new Date('2026-07-15'), startTime: '14:00:00', closed: false, reservable: false, remainingCapacity: 0, unavailableReason: 'FULL' },
  ],
}

afterEach(cleanup)

function createApi(overrides: Partial<ReservationCalendarApi> = {}): ReservationCalendarApi {
  return {
    getAvailableClasses: vi.fn().mockResolvedValue({
      currentGeneralGrade: 'ROUND_BEGINNER',
      dressageApproved: true,
      jumpingApproved: false,
      canUseLargeArena: true,
      availableRidingClasses: ['ROUND_BEGINNER', 'FIRST_RIDE', 'DRESSAGE'],
    }),
    getAvailableTimeSlots: vi.fn().mockResolvedValue(TIME_SLOTS),
    ...overrides,
  }
}

function renderPage(api: ReservationCalendarApi) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}><MemoryRouter>{children}</MemoryRouter></QueryClientProvider>
  return render(<ReservationCalendarPage api={api} now={NOW} />, { wrapper: Wrapper })
}

describe('ReservationCalendarPage', () => {
  it('서버가_허용한_클래스와_현재_등급만_표시한다', async () => {
    renderPage(createApi())
    expect((await screen.findByText('원형초보', { selector: 'strong' })).parentElement).toHaveTextContent('현재 등급은 원형초보입니다.')
    const group = screen.getByRole('radiogroup', { name: '예약 가능 클래스' })
    expect(within(group).getByRole('radio', { name: '원형초보' })).toBeInTheDocument()
    expect(within(group).getByRole('radio', { name: '왕초보' })).toBeInTheDocument()
    expect(within(group).getByRole('radio', { name: '마장마술' })).toBeInTheDocument()
    expect(within(group).queryByRole('radio', { name: '장애물' })).not.toBeInTheDocument()
  })

  it('선택한_클래스와_서울_날짜로_시간대를_조회한다', async () => {
    const getAvailableTimeSlots = vi.fn().mockResolvedValue(TIME_SLOTS)
    renderPage(createApi({ getAvailableTimeSlots }))
    await waitFor(() => expect(getAvailableTimeSlots).toHaveBeenCalledWith('2026-07-15', 'ROUND_BEGINNER'))
    fireEvent.click(screen.getByRole('radio', { name: '마장마술' }))
    await waitFor(() => expect(getAvailableTimeSlots).toHaveBeenCalledWith('2026-07-15', 'DRESSAGE'))
    fireEvent.click(screen.getByRole('button', { name: '다음 날짜' }))
    await waitFor(() => expect(getAvailableTimeSlots).toHaveBeenCalledWith('2026-07-16', 'DRESSAGE'))
  })

  it('예약_가능_운영_마감_만석과_잔여_정원을_구분한다', async () => {
    renderPage(createApi())
    expect(await screen.findByText('잔여 2자리')).toBeInTheDocument()
    expect(screen.getByText('운영 마감')).toBeInTheDocument()
    expect(screen.getByText('예약 마감')).toBeInTheDocument()
  })

  it('예약_가능한_시간대만_신청_화면으로_이동한다', async () => {
    renderPage(createApi())
    const links = await screen.findAllByRole('link')
    const reservationLinks = links.filter((link) => link.getAttribute('href')?.startsWith('/reservations/new'))
    expect(reservationLinks).toHaveLength(1)
    expect(reservationLinks[0]).toHaveAttribute('href', '/reservations/new?timeSlotId=1&classType=ROUND_BEGINNER&date=2026-07-15')
    expect(screen.getByText('운영 마감').closest('[aria-disabled="true"]')).toBeInTheDocument()
  })

  it('시간대가_없는_날짜는_빈_상태로_표시한다', async () => {
    renderPage(createApi({ getAvailableTimeSlots: vi.fn().mockResolvedValue({ ...TIME_SLOTS, timeSlots: [] }) }))
    expect(await screen.findByText('이 날짜에는 등록된 수업 시간이 없습니다.')).toBeInTheDocument()
  })

  it.each([[403, '다시 로그인'], [500, '불러오지 못했습니다']])('%i_조회_실패를_구분한다', async (status, message) => {
    const error = new ResponseError(new Response(null, { status }), 'failed')
    renderPage(createApi({ getAvailableClasses: vi.fn().mockRejectedValue(error) }))
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
  })

  it('클래스와_시간대_로딩을_구분해_표시한다', async () => {
    renderPage(createApi({ getAvailableTimeSlots: vi.fn(() => new Promise<MemberAvailableTimeSlotsResponse>(() => undefined)) }))
    expect(await screen.findByText('시간대를 불러오는 중입니다.')).toBeInTheDocument()
  })

  it.each([320, 768])('%ipx_화면에서_키보드로_클래스와_날짜를_탐색할_수_있다', async (width) => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: width })
    renderPage(createApi())
    expect(await screen.findByRole('radio', { name: '원형초보' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByRole('button', { name: '이전 날짜' })).toBeDisabled()
    expect(screen.getByLabelText('수업 날짜')).toHaveAttribute('min', '2026-07-15')
    expect(screen.getByLabelText('수업 날짜')).toHaveAttribute('max', '2026-10-15')
  })
})
