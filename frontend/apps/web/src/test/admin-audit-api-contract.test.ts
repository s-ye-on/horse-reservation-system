import { AdminReservationAuditQueryControllerApi, querystring } from '@horse/api-client'
import { describe, expect, it } from 'vitest'

describe('AdminReservationAuditQueryControllerApi contract', () => {
  it('감사_이력_조회_조건을_평면_Query_Parameter로_직렬화한다', async () => {
    const api = new AdminReservationAuditQueryControllerApi()

    const request = await api.getAuditLogsRequestOpts({
      keyword: '010-7788',
      reservationId: 42,
      occurredDateFrom: new Date('2026-07-01T00:00:00.000Z'),
      occurredDateTo: new Date('2026-07-31T00:00:00.000Z'),
      actorType: 'admin',
      changeType: 'schedule_changed',
      page: 0,
      size: 20,
    })

    expect(request.path).toBe('/api/admin/audit-logs')
    expect(request.method).toBe('GET')
    expect(request.query).toEqual({
      keyword: '010-7788',
      reservationId: 42,
      occurredDateFrom: '2026-07-01',
      occurredDateTo: '2026-07-31',
      actorType: 'admin',
      changeType: 'schedule_changed',
      page: 0,
      size: 20,
    })
    const serialized = querystring(request.query ?? {})
    expect(serialized).toContain('occurredDateFrom=2026-07-01')
    expect(serialized).not.toContain('request%5Bkeyword%5D')
  })
})
