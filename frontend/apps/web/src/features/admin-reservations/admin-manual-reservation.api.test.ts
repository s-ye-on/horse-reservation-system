import { afterEach, describe, expect, it, vi } from 'vitest'
import { ResponseError } from '@horse/api-client'
import { adminManualReservationApi, describeManualReservationError } from './admin-manual-reservation.api'

afterEach(() => vi.unstubAllGlobals())

describe('관리자 수동 예약 adapter', () => {
  it('generated_Client에_실제_payload와_Idempotency_Key를_그대로_전달한다', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ reservationId: 1, classType: 'FIRST_RIDE', lessonDate: '2030-08-12', startTime: '09:00:00', status: 'pending_payment', paymentSource: 'single_payment', coupon: null, paymentDueAt: '2030-08-12T08:47:00+09:00' }), { status: 201 }))
    vi.stubGlobal('fetch', fetch)
    const payload = { memberId: 7, timeSlotId: 100, classType: 'FIRST_RIDE', reason: '전화 접수' }
    const response = await adminManualReservationApi.create(payload, 'request-key')
    expect(fetch.mock.calls[0][0]).toBe('/api/admin/reservations')
    const options = fetch.mock.calls[0][1] as RequestInit
    expect(options.method).toBe('POST')
    expect(new Headers(options.headers).get('Idempotency-Key')).toBe('request-key')
    expect(JSON.parse(options.body as string)).toEqual(payload)
    expect(response.paymentDueAt).toEqual(new Date('2030-08-12T08:47:00+09:00'))
  })
  it('실패_요청을_adapter가_자동_재전송하지_않는다', async () => {
    const fetch = vi.fn().mockRejectedValue(new TypeError('network')); vi.stubGlobal('fetch', fetch)
    await expect(adminManualReservationApi.create({ memberId: 1, timeSlotId: 2, classType: 'FIRST_RIDE', reason: '접수' }, 'key')).rejects.toThrow()
    expect(fetch).toHaveBeenCalledTimes(1)
  })
  it.each([[409, 'TIMESLOT_CLOSED'], [400, 'RESERVATION_INVALID_RIDING_CLASS'], [404, 'MEMBER_NOT_FOUND']])('알려진_업무_거절_%i_%s는_수정_가능하다', async (status, code) => {
    expect((await describeManualReservationError(new ResponseError(new Response(JSON.stringify({ code }), { status }), 'failed'))).uncertain).toBe(false)
  })
  it.each([[503, 'SCHEDULE_CONFIG_SYNC_IN_PROGRESS'], [409, 'RESERVATION_IDEMPOTENCY_KEY_CONFLICT'], [409, 'UNKNOWN'], [408, ''], [429, ''], [500, '']])('불확실한_%i_%s는_키를_유지한다', async (status, code) => {
    const detail = await describeManualReservationError(new ResponseError(new Response(JSON.stringify({ code }), { status }), 'failed'))
    expect(detail.uncertain).toBe(true)
    if (code) expect(detail.message).not.toContain(code)
  })
  it('null_오류_body도_안전하게_처리한다', async () => {
    expect((await describeManualReservationError(new ResponseError(new Response('null', { status: 409 }), 'failed'))).uncertain).toBe(true)
  })
})
