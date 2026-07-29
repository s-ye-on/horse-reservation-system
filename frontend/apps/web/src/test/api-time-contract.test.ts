import {
  AdminReservationResponseFromJSON,
  ReservationApplicationResponseFromJSON,
  TimeSlotResponseFromJSON,
} from '@horse/api-client'
import { describe, expect, it } from 'vitest'

const seoulDateTime = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Seoul',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

describe('generated API time contract', () => {
  it('RFC3339_offset을_절대_시점으로_해석하고_서울_운영_시각으로_표시한다', () => {
    const response = AdminReservationResponseFromJSON({
      createdAt: '2026-07-22T10:00:00+09:00',
      updatedAt: '2026-07-22T01:00:00Z',
      coupon: null,
    })

    expect(response.createdAt.toISOString()).toBe('2026-07-22T01:00:00.000Z')
    expect(response.updatedAt.toISOString()).toBe('2026-07-22T01:00:00.000Z')
    expect(seoulDateTime.format(response.createdAt)).toContain('10:00')
    expect(response.coupon).toBeNull()
  })

  it('날짜와_시각_전용_값은_한국_현지_벽시계_의미를_유지한다', () => {
    const response = TimeSlotResponseFromJSON({
      lessonDate: '2026-07-22',
      startTime: '13:30:00',
      createdAt: '2026-07-01T09:00:00+09:00',
      updatedAt: '2026-07-01T09:00:00+09:00',
    })

    expect(new Intl.DateTimeFormat('en-CA', {
      timeZone: 'Asia/Seoul',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).format(response.lessonDate)).toBe('2026-07-22')
    expect(response.startTime).toBe('13:30:00')
  })

  it('nullable_응답_키는_생략하지_않고_null로_유지한다', () => {
    const response = ReservationApplicationResponseFromJSON({
      reservationId: 1,
      classType: 'ROUND_BEGINNER',
      lessonDate: '2026-07-22',
      startTime: '13:30:00',
      status: 'pending_payment',
      paymentSource: 'single_payment',
      coupon: null,
      paymentDueAt: null,
    })

    expect(response.coupon).toBeNull()
    expect(response.paymentDueAt).toBeNull()
  })
})
