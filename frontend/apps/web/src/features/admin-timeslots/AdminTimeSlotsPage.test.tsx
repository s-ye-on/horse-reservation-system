import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { ResponseError, type TimeSlotResponse } from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import type { AdminTimeSlotsApi } from './admin-timeslots.api'
import { AdminTimeSlotsPage } from './admin-timeslots-page'

const CLASS_CAPACITIES = {
  FIRST_RIDE: 2,
  ROUND_BEGINNER: 2,
  ROUND_TROT: 2,
  LARGE_ARENA_BEGINNER: 3,
  LARGE_ARENA_TROT: 3,
  CANTER_BEGINNER: 3,
  CANTER: 3,
  DRESSAGE: 1,
  JUMPING: 1,
}
const SLOT: TimeSlotResponse = {
  id: 4,
  lessonDate: new Date('2026-08-10'),
  startTime: '09:00:00',
  totalCapacity: 8,
  roundArenaCapacity: 4,
  classCapacities: CLASS_CAPACITIES,
  closed: false,
  createdAt: new Date('2026-07-29T01:00:00Z'),
  updatedAt: new Date('2026-07-29T01:00:00Z'),
}

afterEach(cleanup)

function createApi(overrides: Partial<AdminTimeSlotsApi> = {}): AdminTimeSlotsApi {
  return {
    getTimeSlots: vi.fn().mockResolvedValue([SLOT]),
    createTimeSlot: vi.fn().mockResolvedValue({ ...SLOT, id: 5 }),
    changeCapacity: vi.fn().mockResolvedValue(SLOT),
    changeClosedStatus: vi.fn().mockResolvedValue({ ...SLOT, closed: true }),
    ...overrides,
  }
}

function renderPage(api: AdminTimeSlotsApi) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const Wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={client}>{children}</QueryClientProvider>
  return render(<AdminTimeSlotsPage api={api} />, { wrapper: Wrapper })
}

describe('AdminTimeSlotsPage', () => {
  it('시간대의_마감과_전체_원형_클래스별_정원을_표시한다', async () => {
    renderPage(createApi())
    expect(await screen.findByRole('heading', { name: /8월 10일/ })).toBeInTheDocument()
    expect(screen.getByText('전체 8명')).toBeInTheDocument()
    expect(screen.getByText('원형 4명')).toBeInTheDocument()
    expect(screen.getByText('구보초보').nextElementSibling).toHaveTextContent('3명')
    expect(screen.getByText('구보').nextElementSibling).toHaveTextContent('3명')
    expect(screen.getByText('마장마술').nextElementSibling).toHaveTextContent('1명')
    expect(screen.getByText('예약 가능')).toBeInTheDocument()
  })

  it('시간대를_생성하고_서버_응답을_목록에_추가한다', async () => {
    const created = { ...SLOT, id: 5, startTime: '11:00:00' }
    const createTimeSlot = vi.fn().mockResolvedValue(created)
    renderPage(createApi({ createTimeSlot }))
    fireEvent.change(await screen.findByLabelText('수업 날짜'), { target: { value: '2026-08-10' } })
    fireEvent.change(screen.getByLabelText('시작 시간'), { target: { value: '11:00' } })
    fireEvent.click(screen.getByRole('button', { name: '시간대 생성' }))
    await waitFor(() => expect(createTimeSlot).toHaveBeenCalled())
    expect(await screen.findByRole('heading', { name: /11:00/ })).toBeInTheDocument()
  })

  it('정원을_수정하고_마감_응답을_화면에_반영한다', async () => {
    const changeCapacity = vi.fn().mockResolvedValue({ ...SLOT, totalCapacity: 7 })
    const changeClosedStatus = vi.fn().mockResolvedValue({ ...SLOT, totalCapacity: 7, closed: true })
    renderPage(createApi({ changeCapacity, changeClosedStatus }))
    fireEvent.click(await screen.findByRole('button', { name: '정원 수정' }))
    const totalInputs = screen.getAllByLabelText('전체 정원')
    fireEvent.change(totalInputs[1], { target: { value: '7' } })
    fireEvent.click(screen.getByRole('button', { name: '정원 저장' }))
    await waitFor(() => expect(changeCapacity).toHaveBeenCalledWith(4, expect.objectContaining({ totalCapacity: 7 })))
    fireEvent.click(await screen.findByRole('button', { name: '신규 예약 마감' }))
    await waitFor(() => expect(changeClosedStatus).toHaveBeenCalledWith(4, true))
    expect(await screen.findByText('마감')).toBeInTheDocument()
  })

  it('정원_입력_오류는_API_호출_전에_표시한다', async () => {
    const createTimeSlot = vi.fn()
    renderPage(createApi({ createTimeSlot }))
    fireEvent.change(await screen.findByLabelText('수업 날짜'), { target: { value: '2026-08-10' } })
    fireEvent.change(screen.getByLabelText('시작 시간'), { target: { value: '09:00' } })
    fireEvent.change(screen.getAllByLabelText('전체 정원')[0], { target: { value: '9' } })
    fireEvent.click(screen.getByRole('button', { name: '시간대 생성' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('0명에서 8명')
    expect(createTimeSlot).not.toHaveBeenCalled()
  })

  it.each([[409, '충돌'], [403, '관리자 권한']])('%i_오류를_구분하고_중복_제출을_막는다', async (status, message) => {
    const error = new ResponseError(new Response(null, { status }), 'failed')
    const changeClosedStatus = vi.fn().mockRejectedValue(error)
    renderPage(createApi({ changeClosedStatus }))
    const button = await screen.findByRole('button', { name: '신규 예약 마감' })
    fireEvent.click(button)
    fireEvent.click(button)
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(changeClosedStatus).toHaveBeenCalledTimes(1)
  })

  it('320px_화면에서도_생성과_마감_제어를_사용할_수_있다', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 320 })
    renderPage(createApi())
    expect(await screen.findByRole('button', { name: '시간대 생성' })).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: '신규 예약 마감' })).toBeInTheDocument()
  })
})
