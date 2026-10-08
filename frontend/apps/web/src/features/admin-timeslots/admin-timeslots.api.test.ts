import { afterEach, describe, expect, it, vi } from 'vitest'
import { ResponseError } from '@horse/api-client'
import { adminTimeSlotsApi, readTimeSlotError } from './admin-timeslots.api'

afterEach(() => vi.unstubAllGlobals())
const capacities = { totalCapacity: 7, roundArenaCapacity: 3, classCapacities: { FIRST_RIDE: 2, ROUND_BEGINNER: 2,
  ROUND_TROT: 2, LARGE_ARENA_BEGINNER: 4, LARGE_ARENA_TROT: 4, CANTER_BEGINNER: 4, CANTER: 4, DRESSAGE: 4, JUMPING: 4 } }
describe('TimeSlot adapter contract', () => {
  it('기존_GET_POST_PUT_PATCH_계약만_사용하고_preview_version_reason을_추가하지_않는다', async () => {
    const fetch = vi.fn().mockImplementation((_url, request: RequestInit) => Promise.resolve(new Response(JSON.stringify(request.method === 'GET' ? [] : {}))))
    vi.stubGlobal('fetch', fetch)
    await adminTimeSlotsApi.getTimeSlots()
    await adminTimeSlotsApi.createTimeSlot({ ...capacities, lessonDate: new Date('2030-08-10'), startTime: '09:00' })
    await adminTimeSlotsApi.changeCapacity(4, capacities)
    await adminTimeSlotsApi.changeClosedStatus(4, false)
    expect(fetch.mock.calls.map(([url]) => url)).toEqual(['/api/admin/timeslots', '/api/admin/timeslots', '/api/admin/timeslots/4/capacity', '/api/admin/timeslots/4'])
    expect(fetch.mock.calls.map(([, request]) => (request as RequestInit).method)).toEqual(['GET', 'POST', 'PUT', 'PATCH'])
    expect(JSON.parse(fetch.mock.calls[1][1].body)).toEqual({ ...capacities, lessonDate: '2030-08-10', startTime: '09:00' })
    expect(JSON.parse(fetch.mock.calls[2][1].body)).toEqual(capacities)
    expect(JSON.parse(fetch.mock.calls[3][1].body)).toEqual({ closed: false })
  })
  it.each([
    ['SCHEDULE_CONFIG_SYNC_IN_PROGRESS', 503, '일정 설정을 반영 중'],
    ['SCHEDULE_DATE_NOT_RESERVABLE', 409, '휴무 처리 중'],
    ['TIMESLOT_CAPACITY_BELOW_OCCUPANCY', 409, '현재 예약 인원보다'],
    ['TIMESLOT_CLOSURE_WITHDRAWAL_NOT_ALLOWED', 409, '이미 처리된 예약'],
    ['TIMESLOT_LESSON_ALREADY_STARTED', 409, '이미 시작된 수업'],
    ['TIMESLOT_ALREADY_EXISTS', 409, '같은 날짜와 시작 시각'],
  ])('%s는_raw_code_없이_업무_오류로_표시한다', async (code, status, message) => {
    const feedback = await readTimeSlotError(new ResponseError(new Response(JSON.stringify({ code }), { status }), 'failed'))
    expect(feedback.message).toContain(message)
    expect(feedback.message).not.toContain(code)
  })
  it('서버_입력오류를_해당_field와_연결한다', async () => {
    expect(await readTimeSlotError(new ResponseError(new Response(JSON.stringify({ code: 'TIMESLOT_INVALID_TOTAL_CAPACITY' }), { status: 400 }), 'failed')))
      .toEqual({ field: 'totalCapacity', message: '전체 정원은 0명에서 8명 사이여야 합니다.' })
  })
  it.each([[401, '로그인이 필요'], [403, '관리자 권한'], [404, '대상을 찾을 수'], [409, '현재 운영 상태']])
   ('%s_응답에_code가_없어도_사용자_오류를_제공한다', async (status, message) => {
      const error = new ResponseError(new Response('null', { status }), 'failed')
      expect((await readTimeSlotError(error)).message).toContain(message)
    })
})
