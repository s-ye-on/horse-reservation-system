import { ResponseError } from '@horse/api-client'
import { describe, expect, it } from 'vitest'
import { getAdminReservationCommandErrorMessage, getAdminReservationErrorKind } from './admin-reservations.api'

describe('admin reservation error presentation', () => {
  it.each([[401, 'unauthorized'], [403, 'forbidden'], [404, 'not-found'], [400, 'validation'], [409, 'conflict'], [500, 'unknown']])('HTTP %i를_업무_오류_분류로_변환한다', (status, kind) => {
    expect(getAdminReservationErrorKind(new ResponseError(new Response(null, { status }), 'failed'))).toBe(kind)
  })
  it.each([
    ['TIMESLOT_CLOSURE_COMMAND_NOT_ALLOWED', '휴강 처리된 수업'],
    ['COUPON_HOLD_STATE_CONFLICT', '쿠폰 점유 상태'],
    ['RESERVATION_LESSON_ALREADY_STARTED', '이미 수업이 시작'],
    ['TIMESLOT_CAPACITY_EXCEEDED', '정원이 마감'],
  ])('%s를_raw_code없이_설명한다', async (code, message) => {
    const error = new ResponseError(new Response(JSON.stringify({ code }), { status: 409 }), 'failed')
    const text = await getAdminReservationCommandErrorMessage(error)
    expect(text).toContain(message)
    expect(text).not.toContain(code)
    expect(await error.response.json()).toEqual({ code })
  })
  it('알수없는_코드와_잘못된_body는_일반_안내로_넘긴다', async () => {
    expect(await getAdminReservationCommandErrorMessage(new Error('private detail'))).toBeUndefined()
    expect(await getAdminReservationCommandErrorMessage(new ResponseError(new Response('not json', { status: 500 }), 'failed'))).toBeUndefined()
    expect(await getAdminReservationCommandErrorMessage(new ResponseError(new Response(JSON.stringify({ code: 'UNKNOWN' }), { status: 409 }), 'failed'))).toBeUndefined()
  })
})
