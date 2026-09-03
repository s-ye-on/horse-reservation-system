import {
  AdminReservationQueryControllerApi,
  GetMonthlyRideStatisticsRideTypeEnum,
  querystring,
} from '@horse/api-client'
import { describe, expect, it } from 'vitest'

describe('AdminReservationQueryControllerApi contract', () => {
  it('관리자_예약_조회_조건을_평면_Query_Parameter로_직렬화한다', async () => {
    const api = new AdminReservationQueryControllerApi()

    const request = await api.getReservationsRequestOpts({
      status: 'pending_admin_approval',
      lessonDateFrom: new Date('2026-08-01T00:00:00.000Z'),
      lessonDateTo: new Date('2026-08-31T00:00:00.000Z'),
      classType: 'ROUND_BEGINNER',
      keyword: '010-1234',
      page: 0,
      size: 20,
    })

    expect(request.query).toEqual({
      status: 'pending_admin_approval',
      lessonDateFrom: '2026-08-01',
      lessonDateTo: '2026-08-31',
      classType: 'ROUND_BEGINNER',
      keyword: '010-1234',
      page: 0,
      size: 20,
    })
    const serialized = querystring(request.query ?? {})
    expect(serialized).toContain('status=pending_admin_approval')
    expect(serialized).not.toContain('request%5Bstatus%5D')
  })

  it('월간_완료_기승_통계의_월과_종류를_Query_Parameter로_직렬화한다', async () => {
    const api = new AdminReservationQueryControllerApi()

    const request = await api.getMonthlyRideStatisticsRequestOpts({
      month: '2026-08',
      rideType: GetMonthlyRideStatisticsRideTypeEnum.Dressage,
    })

    expect(request.query).toEqual({
      month: '2026-08',
      rideType: 'DRESSAGE',
    })
  })

  it('주간_운영_캘린더의_기준일을_시간대_변환_없는_날짜로_직렬화한다', async () => {
    const api = new AdminReservationQueryControllerApi()

    const request = await api.getWeeklyOperationsCalendarRequestOpts({
      referenceDate: '2026-08-31',
    })

    expect(request.query).toEqual({ referenceDate: '2026-08-31' })
  })
})
