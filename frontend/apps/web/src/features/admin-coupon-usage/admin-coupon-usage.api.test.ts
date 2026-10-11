import { AdminCouponUsageExportControllerApi, ResponseError, TextApiResponse } from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { adminCouponUsageApi, couponUsageDownloadErrorMessage } from './admin-coupon-usage.api'

afterEach(() => { vi.restoreAllMocks(); vi.unstubAllGlobals() })

describe('adminCouponUsageApi', () => {
  it('실제_export_GET으로_다섯_조건만_전달하고_페이지_없이_전체_결과를_요청한다', async () => {
    const bytes = new TextEncoder().encode('\uFEFF"member_name"\r\n"김하늘"\r\n')
    const fetch = vi.fn().mockResolvedValue(new Response(bytes, { headers: {
      'content-type': 'text/csv;charset=UTF-8',
      'content-disposition': 'attachment; filename="coupon-usage.csv"',
    } }))
    vi.stubGlobal('fetch', fetch)
    const criteria = { keyword: '7788', couponId: 7, reservationId: 42, occurredDateFrom: '2026-07-01', occurredDateTo: '2026-07-31', page: 2, size: 20 }
    const result = await adminCouponUsageApi.downloadUsageLogs(criteria)
    expect(fetch).toHaveBeenCalledTimes(1)
    const url = new URL(fetch.mock.calls[0][0], 'http://localhost')
    expect(url.pathname).toBe('/api/admin/coupon-usage-logs/export')
    expect(Object.fromEntries(url.searchParams)).toEqual({ keyword: '7788', couponId: '7', reservationId: '42', occurredDateFrom: '2026-07-01', occurredDateTo: '2026-07-31' })
    expect(fetch.mock.calls[0][1].method).toBe('GET')
    expect(result.fileName).toBe('coupon-usage.csv')
    expect(result.blob.type).toBe('text/csv;charset=utf-8')
    expect(Array.from(new Uint8Array(await result.blob.arrayBuffer()))).toEqual(Array.from(bytes))
  })

  it('빈_조건은_생략하고_서버_인코딩_파일명을_사용한다', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response('\uFEFF"header"\r\n', { headers: {
      'content-type': 'text/csv;charset=UTF-8',
      'content-disposition': "attachment; filename*=UTF-8''%EC%BF%A0%ED%8F%B0%20%EC%82%AC%EC%9A%A9.csv",
    } }))
    vi.stubGlobal('fetch', fetch)
    const result = await adminCouponUsageApi.downloadUsageLogs({})
    expect(fetch.mock.calls[0][0]).toBe('/api/admin/coupon-usage-logs/export')
    expect(result.fileName).toBe('쿠폰 사용.csv')
  })

  it('파일명_헤더가_없으면_기존_Backend_파일명을_사용한다', async () => {
    vi.spyOn(AdminCouponUsageExportControllerApi.prototype, '_exportRaw').mockResolvedValue(new TextApiResponse(new Response('csv', { headers: { 'content-type': 'text/csv' } })))
    expect((await adminCouponUsageApi.downloadUsageLogs({})).fileName).toBe('coupon-usage.csv')
  })

  it.each(['application/json', 'text/html', ''])('CSV가_아닌_%s_성공_응답은_다운로드하지_않는다', async (contentType) => {
    vi.spyOn(AdminCouponUsageExportControllerApi.prototype, '_exportRaw').mockResolvedValue(new TextApiResponse(new Response('not csv', { headers: { 'content-type': contentType } })))
    await expect(adminCouponUsageApi.downloadUsageLogs({})).rejects.toThrow('CSV 응답 형식이 올바르지 않습니다.')
  })

  it.each([400, 401, 403, 500])('HTTP_%s_실패를_전파하고_자동_재시도하지_않는다', async (status) => {
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status }))
    vi.stubGlobal('fetch', fetch)
    await expect(adminCouponUsageApi.downloadUsageLogs({})).rejects.toBeInstanceOf(ResponseError)
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(couponUsageDownloadErrorMessage(new ResponseError(new Response(null, { status }), 'internal'))).not.toContain('internal')
  })
})
