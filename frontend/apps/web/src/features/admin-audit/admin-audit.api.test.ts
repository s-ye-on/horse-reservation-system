import {
  AdminReservationAuditExportControllerApi,
  TextApiResponse,
} from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  adminAuditApi,
  parseAuditCsvFileName,
  type AdminAuditFilters,
} from './admin-audit.api'

const FILTERS: AdminAuditFilters = {
  keyword: '7788',
  reservationId: 42,
  occurredDateFrom: '2026-07-01',
  occurredDateTo: '2026-07-31',
  actorType: 'admin',
  changeType: 'schedule_changed',
  page: 3,
  size: 20,
}

afterEach(() => vi.restoreAllMocks())

describe('adminAuditApi', () => {
  it('현재_필터만_전달하고_CSV_Blob과_서버_파일명을_반환한다', async () => {
    const response = new Response('audit,csv', {
      status: 200,
      headers: {
        'content-type': 'text/csv;charset=UTF-8',
        'content-disposition': "attachment; filename*=UTF-8''%EC%98%88%EC%95%BD%20%EA%B0%90%EC%82%AC.csv",
      },
    })
    const exportRequest = vi
      .spyOn(AdminReservationAuditExportControllerApi.prototype, 'export1Raw')
      .mockResolvedValue(new TextApiResponse(response))

    const result = await adminAuditApi.downloadAuditLogs(FILTERS)

    expect(exportRequest).toHaveBeenCalledWith({
      keyword: '7788',
      reservationId: 42,
      occurredDateFrom: new Date('2026-07-01T00:00:00.000Z'),
      occurredDateTo: new Date('2026-07-31T00:00:00.000Z'),
      actorType: 'admin',
      changeType: 'schedule_changed',
    })
    expect(result.fileName).toBe('예약 감사.csv')
    expect(result.blob).toBeInstanceOf(Blob)
    expect(result.blob.type).toBe('text/csv;charset=utf-8')
  })

  it('CSV가_아닌_성공_응답은_다운로드_대상으로_반환하지_않는다', async () => {
    const response = new Response('{"code":"UNEXPECTED"}', {
      status: 200,
      headers: { 'content-type': 'application/json' },
    })
    vi.spyOn(AdminReservationAuditExportControllerApi.prototype, 'export1Raw')
      .mockResolvedValue(new TextApiResponse(response))

    await expect(adminAuditApi.downloadAuditLogs(FILTERS))
      .rejects.toThrow('CSV 응답 형식이 올바르지 않습니다.')
  })
})

describe('parseAuditCsvFileName', () => {
  it('RFC5987_인코딩_한글과_공백_파일명을_해석한다', () => {
    expect(parseAuditCsvFileName(
      "attachment; filename*=UTF-8''%EC%98%88%EC%95%BD%20%EA%B0%90%EC%82%AC.csv",
    )).toBe('예약 감사.csv')
    expect(parseAuditCsvFileName(
      "attachment; filename*=UTF-8'ko'%EC%98%88%EC%95%BD%20%EA%B0%90%EC%82%AC.csv",
    )).toBe('예약 감사.csv')
  })

  it('따옴표_파일명과_경로_문자를_안전한_CSV_파일명으로_제한한다', () => {
    expect(parseAuditCsvFileName('attachment; filename="../../audit report"'))
      .toBe('audit report.csv')
    expect(parseAuditCsvFileName('attachment; filename="CON.csv..."'))
      .toBe('_CON.csv')
  })

  it('파일명_헤더가_없거나_유효하지_않으면_fallback을_사용한다', () => {
    expect(parseAuditCsvFileName(null)).toBe('reservation-audit.csv')
    expect(parseAuditCsvFileName("attachment; filename*=UTF-8''%E0%A4%A"))
      .toBe('reservation-audit.csv')
  })
})
