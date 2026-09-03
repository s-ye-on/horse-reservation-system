import { ResponseError } from '@horse/api-client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearWebAccessToken, setWebAccessToken } from '../../api/web-access-token-memory'
import {
  adminWeeklyOperationsCalendarApi,
  getAdminWeeklyOperationsCalendarErrorKind,
} from './admin-weekly-operations-calendar.api'

const CALENDAR = {
  referenceDate: '2026-09-01',
  weekStartDate: '2026-08-31',
  weekEndDate: '2026-09-06',
  timeSlots: [],
}

beforeEach(() => setWebAccessToken('weekly-calendar-token'))

afterEach(() => {
  clearWebAccessToken()
  vi.unstubAllGlobals()
})

describe('adminWeeklyOperationsCalendarApi', () => {
  it('생성_Client로_기준일과_Bearer를_전달한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(new Response(JSON.stringify(CALENDAR), {
      status: 200,
      headers: { 'Content-Type': 'application/json' },
    }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await adminWeeklyOperationsCalendarApi.getCalendar('2026-09-01')

    expect(result.referenceDate).toEqual(new Date('2026-09-01'))
    expect(result.weekStartDate).toEqual(new Date('2026-08-31'))
    expect(result.weekEndDate).toEqual(new Date('2026-09-06'))
    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const requestUrl = new URL(url, 'http://localhost')
    expect(requestUrl.pathname).toBe('/api/admin/reservations/weekly-operations-calendar')
    expect(Object.fromEntries(requestUrl.searchParams)).toEqual({ referenceDate: '2026-09-01' })
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer weekly-calendar-token')
  })

  it('HTTP_오류를_화면용_오류_종류로_분류한다', () => {
    expect(getAdminWeeklyOperationsCalendarErrorKind(responseError(401))).toBe('unauthorized')
    expect(getAdminWeeklyOperationsCalendarErrorKind(responseError(403))).toBe('forbidden')
    expect(getAdminWeeklyOperationsCalendarErrorKind(responseError(400))).toBe('validation')
    expect(getAdminWeeklyOperationsCalendarErrorKind(new Error('network'))).toBe('unknown')
  })
})

function responseError(status: number) {
  return new ResponseError(new Response(null, { status }), 'failed')
}
