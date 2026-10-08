import { afterEach, describe, expect, it, vi } from 'vitest'
import { ResponseError } from '@horse/api-client'
import { adminDashboardApi, getAdminDashboardErrorKind } from './admin-dashboard.api'

afterEach(() => vi.unstubAllGlobals())
describe('Dashboard read-only adapter contract', () => {
  it('집계는_서버날짜와_count를_변경하지_않고_선택목록은_동일범위_첫100건을_조회한다', async () => {
    const fetch = vi.fn().mockImplementation((url: string, _request: RequestInit) => Promise.resolve(new Response(JSON.stringify(url.includes('/summary')
      ? { lessonDateFrom: '2030-09-01', lessonDateTo: '2030-09-02', totalCount: 520,
        statusCounts: [{ status: 'pending_payment', count: 210 }], dailyCounts: [] }
      : { content: [], page: 0, size: 100, totalElements: 210, totalPages: 3, hasNext: true }))))
    vi.stubGlobal('fetch', fetch)
    const summary = await adminDashboardApi.getSummary('2030-09-01', '2030-09-02')
    expect(summary.totalCount).toBe(520)
    expect(summary.statusCounts[0].count).toBe(210)
    expect(summary.lessonDateFrom).toEqual(new Date('2030-09-01'))
    expect(await adminDashboardApi.getReservations('pending_payment', '2030-09-01', '2030-09-02')).toEqual([])
    const summaryUrl = new URL(fetch.mock.calls[0][0], 'http://localhost')
    const listUrl = new URL(fetch.mock.calls[1][0], 'http://localhost')
    expect(summaryUrl.pathname).toBe('/api/admin/reservations/summary')
    expect(Object.fromEntries(summaryUrl.searchParams)).toEqual({ lessonDateFrom: '2030-09-01', lessonDateTo: '2030-09-02' })
    expect(listUrl.pathname).toBe('/api/admin/reservations')
    expect(Object.fromEntries(listUrl.searchParams)).toEqual({ status: 'pending_payment', lessonDateFrom: '2030-09-01', lessonDateTo: '2030-09-02', page: '0', size: '100' })
    expect(fetch.mock.calls.every(([, request]) => request.method === 'GET')).toBe(true)
  })
  it('날짜가_없거나_한쪽만_있으면_서버의_기간해석을_유지한다', async () => {
    const fetch = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify({ lessonDateFrom: '2030-09-01', lessonDateTo: '2030-09-01', totalCount: 0, statusCounts: [], dailyCounts: [] }))))
    vi.stubGlobal('fetch', fetch)
    await adminDashboardApi.getSummary()
    await adminDashboardApi.getSummary(undefined, '2030-09-01')
    expect(fetch.mock.calls[0][0]).toBe('/api/admin/reservations/summary')
    expect(fetch.mock.calls[1][0]).toBe('/api/admin/reservations/summary?lessonDateTo=2030-09-01')
  })
  it.each([[400, 'validation'], [403, 'forbidden'], [500, 'unknown']])('%s_오류를_업무유형으로_구분한다', (status, kind) => {
    expect(getAdminDashboardErrorKind(new ResponseError(new Response('{}', { status }), 'error'))).toBe(kind)
  })
})
