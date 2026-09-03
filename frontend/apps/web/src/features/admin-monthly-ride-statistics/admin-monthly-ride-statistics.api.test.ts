import { ResponseError } from '@horse/api-client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearWebAccessToken, setWebAccessToken } from '../../api/web-access-token-memory'
import {
  adminMonthlyRideStatisticsApi,
  getAdminMonthlyRideStatisticsErrorKind,
} from './admin-monthly-ride-statistics.api'

const STATISTICS = {
  month: '2026-09',
  rideType: 'DRESSAGE',
  totalCompletedRideCount: 8,
  topCompletedRideCount: 4,
  leaders: [{ memberId: 11, memberName: '김마장', completedRideCount: 4 }],
}

beforeEach(() => setWebAccessToken('monthly-statistics-token'))

afterEach(() => {
  clearWebAccessToken()
  vi.unstubAllGlobals()
})

describe('adminMonthlyRideStatisticsApi', () => {
  it('생성_Client로_월과_기승_종류와_Bearer를_전달한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(STATISTICS), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await adminMonthlyRideStatisticsApi.getStatistics('2026-09', 'DRESSAGE')

    expect(result).toEqual(STATISTICS)
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const requestUrl = new URL(url, 'http://localhost')
    expect(requestUrl.pathname).toBe('/api/admin/reservations/monthly-ride-statistics')
    expect(Object.fromEntries(requestUrl.searchParams)).toEqual({ month: '2026-09', rideType: 'DRESSAGE' })
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer monthly-statistics-token')
  })

  it('HTTP_오류를_화면용_오류_종류로_분류한다', () => {
    expect(getAdminMonthlyRideStatisticsErrorKind(responseError(401))).toBe('unauthorized')
    expect(getAdminMonthlyRideStatisticsErrorKind(responseError(403))).toBe('forbidden')
    expect(getAdminMonthlyRideStatisticsErrorKind(responseError(400))).toBe('validation')
    expect(getAdminMonthlyRideStatisticsErrorKind(new Error('network'))).toBe('unknown')
  })
})

function responseError(status: number) {
  return new ResponseError(new Response(null, { status }), 'failed')
}
