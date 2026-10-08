import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { ResponseError, type TimeSlotResponse } from '@horse/api-client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { AdminTimeSlotsApi } from './admin-timeslots.api'
import { AdminTimeSlotsPage } from './admin-timeslots-page'

const CLASS_CAPACITIES = { FIRST_RIDE: 2, ROUND_BEGINNER: 2, ROUND_TROT: 2, LARGE_ARENA_BEGINNER: 3,
  LARGE_ARENA_TROT: 3, CANTER_BEGINNER: 3, CANTER: 3, DRESSAGE: 1, JUMPING: 1 }
const SLOT: TimeSlotResponse = { id: 4, lessonDate: new Date('2030-08-10'), startTime: '09:00:00',
  totalCapacity: 8, roundArenaCapacity: 4, classCapacities: CLASS_CAPACITIES, closed: false,
  createdAt: new Date('2030-07-29T01:00:00Z'), updatedAt: new Date('2030-07-29T01:00:00Z') }
beforeEach(() => {
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value: function (this: HTMLDialogElement) { this.setAttribute('open', '') } })
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value: function (this: HTMLDialogElement) { this.removeAttribute('open') } })
})
afterEach(() => {
  cleanup(); vi.restoreAllMocks()
  Reflect.deleteProperty(HTMLDialogElement.prototype, 'showModal')
  Reflect.deleteProperty(HTMLDialogElement.prototype, 'close')
})
function createApi(overrides: Partial<AdminTimeSlotsApi> = {}): AdminTimeSlotsApi {
  return { getTimeSlots: vi.fn().mockResolvedValue([SLOT]), createTimeSlot: vi.fn().mockResolvedValue({ ...SLOT, id: 5 }),
    changeCapacity: vi.fn().mockResolvedValue(SLOT), changeClosedStatus: vi.fn().mockResolvedValue({ ...SLOT, closed: true }), ...overrides }
}
function renderPage(api: AdminTimeSlotsApi) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={client}><MemoryRouter><AdminTimeSlotsPage api={api} /></MemoryRouter></QueryClientProvider>)
}
async function editCapacity() {
  fireEvent.click(await screen.findByRole('button', { name: '정원 수정' }))
  return screen.getByRole('dialog', { name: '시간대 정원 편집' })
}
async function statusRequest(closed: boolean) {
  const card = await screen.findByRole('article')
  fireEvent.click(within(card).getByText('마감·재개 요청'))
  fireEvent.click(within(card).getByRole('button', { name: closed ? '신규 예약 마감' : '관리자 마감 철회·재개' }))
  return screen.getByRole('dialog')
}
function apiError(status: number, code: string) {
  return new ResponseError(new Response(JSON.stringify({ code }), { status }), 'failed')
}
describe('AdminTimeSlotsPage', () => {
  it('날짜별_시간대와_정원을_표시하고_예약가능_잔여자리_단일클래스를_추정하지_않는다', async () => {
    renderPage(createApi())
    const card = await screen.findByRole('article')
    expect(within(card).getByRole('heading', { name: '09:00' })).toBeInTheDocument()
    expect(within(card).getByText('전체 정원').nextElementSibling).toHaveTextContent('8명')
    expect(within(card).getByText('원형마장 정원').nextElementSibling).toHaveTextContent('4명')
    fireEvent.click(within(card).getByText('9개 클래스별 정원'))
    expect(within(card).getByText('마장마술').nextElementSibling).toHaveTextContent('1명')
    expect(screen.queryByText('예약 가능')).not.toBeInTheDocument()
    expect(screen.queryByText(/잔여|점유 수/)).not.toBeInTheDocument()
  })
  it('생성은_필수입력과_확인후에만_서버를_호출하며_정원합계는_강제하지_않는다', async () => {
    const api = createApi()
    renderPage(api)
    fireEvent.click(screen.getByRole('button', { name: '수동 시간대 생성' }))
    fireEvent.click(screen.getByRole('button', { name: '변경 내용 확인' }))
    expect(screen.getByLabelText('수업 날짜')).toHaveAttribute('aria-invalid', 'true')
    expect(api.createTimeSlot).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText('수업 날짜'), { target: { value: '2030-08-10' } })
    fireEvent.change(screen.getByLabelText('시작 시각'), { target: { value: '11:00' } })
    fireEvent.change(screen.getByLabelText('전체 정원'), { target: { value: '1' } })
    fireEvent.change(screen.getByLabelText('원형마장 정원'), { target: { value: '0' } })
    fireEvent.change(screen.getByLabelText('마장마술 정원'), { target: { value: '8' } })
    fireEvent.click(screen.getByRole('button', { name: '변경 내용 확인' }))
    expect(screen.getByRole('dialog', { name: '시간대 생성 확인' })).toBeInTheDocument()
    expect(api.createTimeSlot).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: '확인 후 적용' }))
    await waitFor(() => expect(api.createTimeSlot).toHaveBeenCalledWith({ lessonDate: new Date('2030-08-10'), startTime: '11:00',
      totalCapacity: 1, roundArenaCapacity: 0, classCapacities: expect.objectContaining({ DRESSAGE: 8 }) }))
    expect(await screen.findByRole('status', { name: '이번 처리 결과' })).toHaveTextContent('수동 시간대가 생성되었습니다')
    await waitFor(() => expect(api.getTimeSlots).toHaveBeenCalledTimes(2))
  })
  it.each([
    ['전체 정원', '9'], ['전체 정원', ''], ['원형마장 정원', '5'], ['왕초보 정원', '-1'], ['장애물 정원', '1.5'],
  ])('%s_%s_입력오류를_field와_연결한다', async (label, value) => {
    const api = createApi(); renderPage(api); await editCapacity()
    fireEvent.change(screen.getByLabelText(label), { target: { value } })
    fireEvent.click(screen.getByRole('button', { name: '변경 내용 확인' }))
    const input = screen.getByLabelText(label)
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input.getAttribute('aria-describedby')).toContain(`${input.id}-error`)
    expect(api.changeCapacity).not.toHaveBeenCalled()
  })
  it('원형정원이_전체보다_크면_거부하고_모든_클래스_정원을_한번에_전송한다', async () => {
    const api = createApi(); renderPage(api); await editCapacity()
    fireEvent.change(screen.getByLabelText('전체 정원'), { target: { value: '3' } })
    fireEvent.click(screen.getByRole('button', { name: '변경 내용 확인' }))
    expect(screen.getByLabelText('원형마장 정원')).toHaveAttribute('aria-invalid', 'true')
    fireEvent.change(screen.getByLabelText('원형마장 정원'), { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: '변경 내용 확인' }))
    expect(screen.getByText(/정규 시간표 정원 동기화에서 제외/)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '확인 후 적용' }))
    await waitFor(() => expect(api.changeCapacity).toHaveBeenCalledWith(4, { totalCapacity: 3, roundArenaCapacity: 2, classCapacities: CLASS_CAPACITIES }))
    expect(await screen.findByRole('heading', { name: '이번 처리 결과' })).toHaveFocus()
  })
  it.each([
    [503, 'SCHEDULE_CONFIG_SYNC_IN_PROGRESS', '일정 설정을 반영 중'],
    [409, 'SCHEDULE_DATE_NOT_RESERVABLE', '휴무 처리 중'],
    [409, 'TIMESLOT_CAPACITY_BELOW_OCCUPANCY', '현재 예약 인원보다'],
  ])('정원_변경_%s_%s_서버거부후_입력보존과_최신조회를_수행한다', async (status, code, message) => {
    const api = createApi({ changeCapacity: vi.fn().mockRejectedValue(apiError(status, code)) })
    renderPage(api); await editCapacity()
    fireEvent.change(screen.getByLabelText('전체 정원'), { target: { value: '7' } })
    fireEvent.click(screen.getByRole('button', { name: '변경 내용 확인' }))
    fireEvent.click(screen.getByRole('button', { name: '확인 후 적용' }))
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.getByLabelText('전체 정원')).toHaveValue(7)
    await waitFor(() => expect(api.getTimeSlots).toHaveBeenCalledTimes(2))
    expect(api.changeCapacity).toHaveBeenCalledTimes(1)
  })
  it('closed_true에서도_두_명시적_요청을_유지하고_확인전에는_호출하지_않는다', async () => {
    const api = createApi({ getTimeSlots: vi.fn().mockResolvedValue([{ ...SLOT, closed: true }]) })
    renderPage(api)
    const dialog = await statusRequest(true)
    expect(dialog).toHaveTextContent('기존 예약은 자동 취소되지')
    expect(api.changeClosedStatus).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: '확인 후 적용' }))
    await waitFor(() => expect(api.changeClosedStatus).toHaveBeenCalledWith(4, true))
    expect(await screen.findByRole('status', { name: '이번 처리 결과' })).toHaveTextContent('휴무·휴강 관리')
    await statusRequest(false)
    expect(screen.getByRole('dialog')).toHaveTextContent('영향 예약을 아직 처리하지 않은 경우')
  })
  it('오류후_최신조회가_끝나기전_재확인을_막고_새_서버정원과_보존입력을_비교한다', async () => {
    let resolve!: (slots: TimeSlotResponse[]) => void
    const api = createApi({
      getTimeSlots: vi.fn().mockResolvedValueOnce([SLOT]).mockImplementationOnce(() => new Promise<TimeSlotResponse[]>((done) => { resolve = done })),
      changeCapacity: vi.fn().mockRejectedValue(apiError(409, 'TIMESLOT_CAPACITY_BELOW_OCCUPANCY')),
    })
    renderPage(api); await editCapacity()
    fireEvent.change(screen.getByLabelText('전체 정원'), { target: { value: '7' } })
    fireEvent.click(screen.getByRole('button', { name: '변경 내용 확인' }))
    fireEvent.click(screen.getByRole('button', { name: '확인 후 적용' }))
    await screen.findByRole('alert')
    const review = screen.getByRole('button', { name: '변경 내용 확인' })
    expect(review).toBeDisabled()
    fireEvent.submit(review.closest('form')!)
    expect(screen.queryByRole('dialog', { name: '정원 변경 확인' })).not.toBeInTheDocument()
    resolve([{ ...SLOT, totalCapacity: 6 }])
    await waitFor(() => expect(review).toBeEnabled())
    expect(screen.getByLabelText('전체 정원')).toHaveValue(7)
    fireEvent.click(review)
    expect(screen.getByRole('dialog', { name: '정원 변경 확인' })).toHaveTextContent('6 → 7명')
    expect(api.changeCapacity).toHaveBeenCalledTimes(1)
  })
  it.each([true, false])('철회재개_성공후_closed_%s_서버결과만_표현한다', async (closed) => {
    const api = createApi({ changeClosedStatus: vi.fn().mockResolvedValue({ ...SLOT, closed }) })
    renderPage(api); await statusRequest(false)
    fireEvent.click(screen.getByRole('button', { name: '확인 후 적용' }))
    const result = await screen.findByRole('status', { name: '이번 처리 결과' })
    expect(result).toHaveTextContent(closed ? '여전히 신규 예약 마감' : '마감 표시가 해제')
    expect(result).not.toHaveTextContent('예약 가능합니다')
    expect(api.changeClosedStatus).toHaveBeenCalledWith(4, false)
  })
  it('철회_불가_오류후_최신조회를_하고_자동재시도하지_않는다', async () => {
    const api = createApi({ changeClosedStatus: vi.fn().mockRejectedValue(apiError(409, 'TIMESLOT_CLOSURE_WITHDRAWAL_NOT_ALLOWED')) })
    renderPage(api); await statusRequest(false)
    fireEvent.click(screen.getByRole('button', { name: '확인 후 적용' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('이미 처리된 예약')
    expect(screen.getByRole('alert')).toHaveFocus()
    await waitFor(() => expect(api.getTimeSlots).toHaveBeenCalledTimes(2))
    expect(api.changeClosedStatus).toHaveBeenCalledTimes(1)
  })
  it('중복제출과_optimistic_마감결과를_막는다', async () => {
    let resolve!: (slot: TimeSlotResponse) => void
    const api = createApi({ changeClosedStatus: vi.fn(() => new Promise<TimeSlotResponse>((done) => { resolve = done })) })
    renderPage(api); await statusRequest(true)
    const confirm = screen.getByRole('button', { name: '확인 후 적용' })
    fireEvent.click(confirm); fireEvent.click(confirm)
    await waitFor(() => expect(api.changeClosedStatus).toHaveBeenCalledTimes(1))
    expect(screen.queryByRole('status', { name: '이번 처리 결과' })).not.toBeInTheDocument()
    resolve({ ...SLOT, closed: true })
    expect(await screen.findByRole('status', { name: '이번 처리 결과' })).toBeInTheDocument()
  })
  it('성공후_GET실패는_생성실패와_구분하고_결과를_보존한다', async () => {
    const api = createApi({ getTimeSlots: vi.fn().mockResolvedValueOnce([SLOT]).mockRejectedValue(new Error('query')) })
    renderPage(api); await statusRequest(true)
    fireEvent.click(screen.getByRole('button', { name: '확인 후 적용' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('요청은 처리되었지만 최신 시간대 목록')
    expect(screen.getByRole('status', { name: '이번 처리 결과' })).toBeInTheDocument()
  })
  it('빈목록과_조회오류를_구분한다', async () => {
    renderPage(createApi({ getTimeSlots: vi.fn().mockResolvedValue([]) }))
    expect(await screen.findByText('아직 시작하지 않은 등록 시간대가 없습니다.')).toBeInTheDocument()
    cleanup()
    renderPage(createApi({ getTimeSlots: vi.fn().mockRejectedValue(new Error('query')) }))
    expect(await screen.findByRole('alert')).toHaveTextContent('시간대 목록을 불러오지 못했습니다')
  })
})
