import { afterEach, describe, expect, it, vi } from 'vitest'
import { adminCouponsApi } from './admin-coupons.api'

afterEach(() => vi.unstubAllGlobals())

describe('쿠폰 등록 API adapter', () => {
  it('기존_전체_회원_API에_페이지와_검색어를_전달하고_페이지_메타데이터를_유지한다', async () => {
    const page = { content: [], page: 1, size: 20, totalElements: 41, totalPages: 3, hasNext: true }
    const fetch = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify(page))))
    vi.stubGlobal('fetch', fetch)
    await adminCouponsApi.getMembers(0, 20)
    const result = await adminCouponsApi.getMembers(1, 20, '010-1234 5678')
    expect(fetch.mock.calls[0][0]).toBe('/api/admin/members?page=0&size=20')
    const url = new URL(fetch.mock.calls[1][0] as string, 'http://localhost')
    expect(url.pathname).toBe('/api/admin/members')
    expect(Object.fromEntries(url.searchParams)).toEqual({ page: '1', size: '20', query: '010-1234 5678' })
    expect(result).toEqual(page)
  })

  it('기존_쿠폰_등록_요청과_날짜_계약을_변경하지_않는다', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ id: 9, type: 'general' }), { status: 201 }))
    vi.stubGlobal('fetch', fetch)
    await adminCouponsApi.registerCoupon(51, {
      type: 'general', totalCount: 10, usedCount: 3, firstUsedDate: new Date('2026-07-03T00:00:00Z'),
    })
    expect(fetch.mock.calls[0][0]).toBe('/api/admin/members/51/coupons')
    const request = fetch.mock.calls[0][1] as RequestInit
    expect(request.method).toBe('POST')
    expect(JSON.parse(request.body as string)).toEqual({ type: 'general', totalCount: 10, usedCount: 3, firstUsedDate: '2026-07-03' })
  })
})
