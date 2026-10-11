import { ResponseError } from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { getReservationChangeErrorKind, reservationChangeApi } from './reservation-change.api'

afterEach(() => vi.unstubAllGlobals())

describe('예약 변경 대상 조회 API', () => {
  it('목록_페이지_없이_URL_ID의_예약_단건을_조회한다', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({
      reservationId: 10482, classType: 'ROUND_BEGINNER', lessonDate: '2026-11-20', startTime: '09:00:00', status: 'confirmed',
    })))
    vi.stubGlobal('fetch', fetch)
    const result = await reservationChangeApi.getMyReservation(10482)
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(fetch.mock.calls[0][0]).toBe('/api/me/reservations/10482')
    expect(fetch.mock.calls[0][1].method).toBe('GET')
    expect(result.reservationId).toBe(10482)
    expect(result.lessonDate).toEqual(new Date('2026-11-20'))
  })

  it.each(['RESERVATION_NOT_FOUND', 'RESERVATION_MEMBER_MISMATCH'])('서버_404_%s를_숨기거나_목록으로_대체하지_않는다', async (code) => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ code }), { status: 404 }))
    vi.stubGlobal('fetch', fetch)
    await expect(reservationChangeApi.getMyReservation(999)).rejects.toBeInstanceOf(ResponseError)
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(getReservationChangeErrorKind(new ResponseError(new Response(null, { status: 404 }), code))).toBe('not-found')
  })
})
