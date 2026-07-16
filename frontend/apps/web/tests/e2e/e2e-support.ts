import { createHmac } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import type { Browser } from '@playwright/test'

const API_BASE_URL = 'http://localhost:8080'
const WEB_BASE_URL = 'http://127.0.0.1:5173'
const DEFAULT_JWT_SECRET = 'local-development-jwt-secret-change-me-32-bytes'
const COUPON_MEMBER_SUBJECT = 'e2e-m1-29-coupon-member'
const SINGLE_PAYMENT_MEMBER_SUBJECT = 'e2e-m1-29-single-payment-member'
const COUPON_MEMBER_NAME = 'E2E 쿠폰 회원'
const SINGLE_PAYMENT_MEMBER_NAME = 'E2E 결제 회원'
const COUPON_START_TIME = '10:00:00'
const SINGLE_PAYMENT_START_TIME = '11:00:00'

export interface MvpOneFixture {
  couponMember: MemberFixture
  singlePaymentMember: MemberFixture
}

export interface MemberFixture {
  authSubject: string
  name: string
  lessonDate: string
  startTime: string
  timeSlotId: number
  couponId?: number
}

export interface MvpTwoFixture {
  beforeCutoffChange: ReservationPolicyFixture
  afterCutoffChange: ReservationPolicyFixture
  sameDayChange: ReservationPolicyFixture
  beforeCutoffCancel: ReservationPolicyFixture
  afterCutoffCancel: ReservationPolicyFixture
  adminCancel: ReservationPolicyFixture
  todayIsWeekend: boolean
}

export interface ReservationPolicyFixture extends MemberFixture {
  reservationId: number
  targetLessonDate?: string
  targetStartTime?: string
  targetTimeSlotId?: number
}

export interface ReservationPolicySnapshot {
  status: string
  lessonDate: string
  startTime: string
  remainingCount: number
  heldCount: number
  freeChangeUsed: boolean
  freeChangeLogCount: number
  changeLogCount: number
}

export async function createAuthenticatedPage(browser: Browser, authSubject: string, role: 'MEMBER' | 'ADMIN') {
  const context = await browser.newContext()
  const page = await context.newPage()
  const token = createJwt(authSubject, role)

  await page.route(`${API_BASE_URL}/**`, async (route) => {
    if (route.request().method() === 'OPTIONS') {
      await route.fulfill({
        status: 204,
        headers: corsHeaders(),
      })
      return
    }

    const response = await route.fetch({
      headers: {
        ...route.request().headers(),
        authorization: `Bearer ${token}`,
      },
    })
    await route.fulfill({
      response,
      headers: {
        ...response.headers(),
        ...corsHeaders(),
      },
    })
  })

  return page
}

export function prepareMvpOneFixture(): MvpOneFixture {
  const today = seoulDateKey()
  const couponLessonDate = addDays(today, 1)
  const singlePaymentLessonDate = addDays(today, 2)

  cleanupMvpOneFixture(couponLessonDate, singlePaymentLessonDate)
  runSql(`
    INSERT INTO members (auth_subject, name, phone, general_ride_count)
    VALUES
      ('${COUPON_MEMBER_SUBJECT}', '${COUPON_MEMBER_NAME}', '010-9000-0029', 1),
      ('${SINGLE_PAYMENT_MEMBER_SUBJECT}', '${SINGLE_PAYMENT_MEMBER_NAME}', '010-9000-1029', 1);

    INSERT INTO coupons (member_id, coupon_type, total_count, remaining_count, held_count, status)
    SELECT id, 'general', 10, 10, 0, 'active'
    FROM members
    WHERE auth_subject = '${COUPON_MEMBER_SUBJECT}';

    INSERT INTO time_slot_capacities (
      lesson_date,
      start_time,
      total_capacity,
      round_arena_capacity,
      class_capacity_json,
      is_closed
    ) VALUES
      ('${couponLessonDate}', '${COUPON_START_TIME}', 8, 4, '${classCapacityJson()}', FALSE),
      ('${singlePaymentLessonDate}', '${SINGLE_PAYMENT_START_TIME}', 8, 4, '${classCapacityJson()}', FALSE);
  `)

  const [couponTimeSlotId, singlePaymentTimeSlotId, couponId] = runSql(`
    SELECT
      (SELECT id FROM time_slot_capacities WHERE lesson_date = '${couponLessonDate}' AND start_time = '${COUPON_START_TIME}'),
      (SELECT id FROM time_slot_capacities WHERE lesson_date = '${singlePaymentLessonDate}' AND start_time = '${SINGLE_PAYMENT_START_TIME}'),
      (SELECT coupon.id FROM coupons coupon JOIN members member ON member.id = coupon.member_id WHERE member.auth_subject = '${COUPON_MEMBER_SUBJECT}');
  `).split('\t').map(Number)

  return {
    couponMember: {
      authSubject: COUPON_MEMBER_SUBJECT,
      name: COUPON_MEMBER_NAME,
      lessonDate: couponLessonDate,
      startTime: COUPON_START_TIME,
      timeSlotId: couponTimeSlotId,
      couponId,
    },
    singlePaymentMember: {
      authSubject: SINGLE_PAYMENT_MEMBER_SUBJECT,
      name: SINGLE_PAYMENT_MEMBER_NAME,
      lessonDate: singlePaymentLessonDate,
      startTime: SINGLE_PAYMENT_START_TIME,
      timeSlotId: singlePaymentTimeSlotId,
    },
  }
}

export function cleanupFixture(fixture: MvpOneFixture) {
  cleanupMvpOneFixture(fixture.couponMember.lessonDate, fixture.singlePaymentMember.lessonDate)
}

export function prepareMvpTwoFixture(): MvpTwoFixture {
  const today = seoulDateKey()
  const beforeLessonDate = addDays(today, 2)
  const beforeTargetDate = addDays(today, 3)
  const afterTargetDate = addDays(today, 4)
  const subjects = mvpTwoSubjects()

  cleanupMvpTwoFixture(today)
  runSql(`
    INSERT INTO members (auth_subject, name, phone, general_ride_count)
    VALUES
      ('${subjects.beforeChange}', 'E2E 마감전 변경', '010-9200-0001', 6),
      ('${subjects.afterChange}', 'E2E 무료 변경', '010-9200-0002', 6),
      ('${subjects.sameDayChange}', 'E2E 당일 변경', '010-9200-0003', 6),
      ('${subjects.beforeCancel}', 'E2E 마감전 취소', '010-9200-0004', 6),
      ('${subjects.afterCancel}', 'E2E 마감후 취소', '010-9200-0005', 6),
      ('${subjects.adminCancel}', 'E2E 관리자 취소', '010-9200-0006', 6);

    INSERT INTO coupons (
      member_id, coupon_type, total_count, remaining_count, held_count, status, created_by
    )
    SELECT id, 'general', 10, 10, 1, 'active', 'e2e-m2-11'
    FROM members
    WHERE auth_subject IN (${quoteList(Object.values(subjects))});

    INSERT INTO time_slot_capacities (
      lesson_date, start_time, total_capacity, round_arena_capacity, class_capacity_json, is_closed
    ) VALUES
      ('${beforeLessonDate}', '09:00:00', 8, 4, '${classCapacityJson()}', FALSE),
      ('${beforeTargetDate}', '09:00:00', 8, 4, '${classCapacityJson()}', FALSE),
      ('${today}', '10:00:00', 8, 4, '${classCapacityJson()}', FALSE),
      ('${afterTargetDate}', '10:00:00', 8, 4, '${classCapacityJson()}', FALSE),
      ('${today}', '11:00:00', 8, 4, '${classCapacityJson()}', FALSE),
      ('${today}', '12:00:00', 8, 4, '${classCapacityJson()}', FALSE),
      ('${beforeLessonDate}', '13:00:00', 8, 4, '${classCapacityJson()}', FALSE),
      ('${today}', '14:00:00', 8, 4, '${classCapacityJson()}', FALSE),
      ('${today}', '15:00:00', 8, 4, '${classCapacityJson()}', FALSE);

    INSERT INTO reservations (
      member_id, class_type, lesson_date, start_time, status, payment_source, coupon_id,
      approval_requested_at, admin_confirmed_at
    )
    SELECT member.id, 'ROUND_BEGINNER', '${beforeLessonDate}', '09:00:00',
      'confirmed', 'coupon', coupon.id, NOW(6), NOW(6)
    FROM members member JOIN coupons coupon ON coupon.member_id = member.id
    WHERE member.auth_subject = '${subjects.beforeChange}'
    UNION ALL
    SELECT member.id, 'ROUND_BEGINNER', '${today}', '10:00:00',
      'confirmed', 'coupon', coupon.id, NOW(6), NOW(6)
    FROM members member JOIN coupons coupon ON coupon.member_id = member.id
    WHERE member.auth_subject = '${subjects.afterChange}'
    UNION ALL
    SELECT member.id, 'ROUND_BEGINNER', '${today}', '11:00:00',
      'confirmed', 'coupon', coupon.id, NOW(6), NOW(6)
    FROM members member JOIN coupons coupon ON coupon.member_id = member.id
    WHERE member.auth_subject = '${subjects.sameDayChange}'
    UNION ALL
    SELECT member.id, 'ROUND_BEGINNER', '${beforeLessonDate}', '13:00:00',
      'confirmed', 'coupon', coupon.id, NOW(6), NOW(6)
    FROM members member JOIN coupons coupon ON coupon.member_id = member.id
    WHERE member.auth_subject = '${subjects.beforeCancel}'
    UNION ALL
    SELECT member.id, 'ROUND_BEGINNER', '${today}', '14:00:00',
      'confirmed', 'coupon', coupon.id, NOW(6), NOW(6)
    FROM members member JOIN coupons coupon ON coupon.member_id = member.id
    WHERE member.auth_subject = '${subjects.afterCancel}'
    UNION ALL
    SELECT member.id, 'ROUND_BEGINNER', '${today}', '15:00:00',
      'confirmed', 'coupon', coupon.id, NOW(6), NOW(6)
    FROM members member JOIN coupons coupon ON coupon.member_id = member.id
    WHERE member.auth_subject = '${subjects.adminCancel}';

    INSERT INTO coupon_usage_logs (
      coupon_id, reservation_id, member_id, action, count_delta, occurred_at, actor_type, memo
    )
    SELECT coupon.id, reservation.id, member.id, 'held', 1, NOW(6), 'member', 'M2-11 fixture'
    FROM members member
    JOIN coupons coupon ON coupon.member_id = member.id
    JOIN reservations reservation ON reservation.member_id = member.id
    WHERE member.auth_subject IN (${quoteList(Object.values(subjects))});
  `)

  const fixtures = Object.fromEntries(Object.entries(subjects).map(([key, subject]) => {
    const [reservationId, couponId] = runSql(`
      SELECT reservation.id, coupon.id
      FROM members member
      JOIN coupons coupon ON coupon.member_id = member.id
      JOIN reservations reservation ON reservation.member_id = member.id
      WHERE member.auth_subject = '${subject}';
    `).split('\t').map(Number)
    return [key, { authSubject: subject, reservationId, couponId }]
  })) as Record<keyof ReturnType<typeof mvpTwoSubjects>, Pick<ReservationPolicyFixture, 'authSubject' | 'reservationId' | 'couponId'>>

  return {
    beforeCutoffChange: {
      ...fixtures.beforeChange,
      name: 'E2E 마감전 변경', lessonDate: beforeLessonDate, startTime: '09:00:00',
      timeSlotId: findTimeSlot(beforeLessonDate, '09:00:00'),
      targetLessonDate: beforeTargetDate, targetStartTime: '09:00:00',
      targetTimeSlotId: findTimeSlot(beforeTargetDate, '09:00:00'),
    },
    afterCutoffChange: {
      ...fixtures.afterChange,
      name: 'E2E 무료 변경', lessonDate: today, startTime: '10:00:00',
      timeSlotId: findTimeSlot(today, '10:00:00'),
      targetLessonDate: afterTargetDate, targetStartTime: '10:00:00',
      targetTimeSlotId: findTimeSlot(afterTargetDate, '10:00:00'),
    },
    sameDayChange: {
      ...fixtures.sameDayChange,
      name: 'E2E 당일 변경', lessonDate: today, startTime: '11:00:00',
      timeSlotId: findTimeSlot(today, '11:00:00'),
      targetLessonDate: today, targetStartTime: '12:00:00',
      targetTimeSlotId: findTimeSlot(today, '12:00:00'),
    },
    beforeCutoffCancel: {
      ...fixtures.beforeCancel,
      name: 'E2E 마감전 취소', lessonDate: beforeLessonDate, startTime: '13:00:00',
      timeSlotId: findTimeSlot(beforeLessonDate, '13:00:00'),
    },
    afterCutoffCancel: {
      ...fixtures.afterCancel,
      name: 'E2E 마감후 취소', lessonDate: today, startTime: '14:00:00',
      timeSlotId: findTimeSlot(today, '14:00:00'),
    },
    adminCancel: {
      ...fixtures.adminCancel,
      name: 'E2E 관리자 취소', lessonDate: today, startTime: '15:00:00',
      timeSlotId: findTimeSlot(today, '15:00:00'),
    },
    todayIsWeekend: isWeekend(today),
  }
}

export function cleanupMvpTwoFixture(today: string) {
  const subjects = Object.values(mvpTwoSubjects())
  runSql(`
    DELETE change_log
    FROM reservation_change_logs change_log
    JOIN reservations reservation ON reservation.id = change_log.reservation_id
    JOIN members member ON member.id = reservation.member_id
    WHERE member.auth_subject IN (${quoteList(subjects)});

    DELETE usage_log
    FROM coupon_usage_logs usage_log
    JOIN members member ON member.id = usage_log.member_id
    WHERE member.auth_subject IN (${quoteList(subjects)});

    DELETE reservation
    FROM reservations reservation
    JOIN members member ON member.id = reservation.member_id
    WHERE member.auth_subject IN (${quoteList(subjects)});

    DELETE coupon
    FROM coupons coupon
    JOIN members member ON member.id = coupon.member_id
    WHERE member.auth_subject IN (${quoteList(subjects)});

    DELETE FROM members WHERE auth_subject IN (${quoteList(subjects)});

    DELETE FROM time_slot_capacities
    WHERE (lesson_date = '${addDays(today, 2)}' AND start_time IN ('09:00:00', '13:00:00'))
       OR (lesson_date = '${addDays(today, 3)}' AND start_time = '09:00:00')
       OR (lesson_date = '${addDays(today, 4)}' AND start_time = '10:00:00')
       OR (lesson_date = '${today}' AND start_time IN ('10:00:00', '11:00:00', '12:00:00', '14:00:00', '15:00:00'));
  `)
}

export function readReservationPolicySnapshot(reservationId: number): ReservationPolicySnapshot {
  const values = runSql(`
    SELECT reservation.status, reservation.lesson_date, reservation.start_time,
      coupon.remaining_count, coupon.held_count, coupon.free_change_used,
      (SELECT COUNT(*) FROM coupon_usage_logs usage_log
        WHERE usage_log.reservation_id = reservation.id AND usage_log.action = 'free_change_used'),
      (SELECT COUNT(*) FROM reservation_change_logs change_log
        WHERE change_log.reservation_id = reservation.id)
    FROM reservations reservation
    JOIN coupons coupon ON coupon.id = reservation.coupon_id
    WHERE reservation.id = ${reservationId};
  `).split('\t')
  return {
    status: values[0],
    lessonDate: values[1],
    startTime: values[2],
    remainingCount: Number(values[3]),
    heldCount: Number(values[4]),
    freeChangeUsed: values[5] === '1',
    freeChangeLogCount: Number(values[6]),
    changeLogCount: Number(values[7]),
  }
}

function cleanupMvpOneFixture(couponLessonDate: string, singlePaymentLessonDate: string) {
  runSql(`
    DELETE change_log
    FROM reservation_change_logs change_log
    JOIN reservations reservation ON reservation.id = change_log.reservation_id
    JOIN members member ON member.id = reservation.member_id
    WHERE member.auth_subject IN ('${COUPON_MEMBER_SUBJECT}', '${SINGLE_PAYMENT_MEMBER_SUBJECT}');

    DELETE usage_log
    FROM coupon_usage_logs usage_log
    JOIN members member ON member.id = usage_log.member_id
    WHERE member.auth_subject IN ('${COUPON_MEMBER_SUBJECT}', '${SINGLE_PAYMENT_MEMBER_SUBJECT}');

    DELETE reservation
    FROM reservations reservation
    JOIN members member ON member.id = reservation.member_id
    WHERE member.auth_subject IN ('${COUPON_MEMBER_SUBJECT}', '${SINGLE_PAYMENT_MEMBER_SUBJECT}');

    DELETE coupon
    FROM coupons coupon
    JOIN members member ON member.id = coupon.member_id
    WHERE member.auth_subject IN ('${COUPON_MEMBER_SUBJECT}', '${SINGLE_PAYMENT_MEMBER_SUBJECT}');

    DELETE FROM time_slot_capacities
    WHERE (lesson_date = '${couponLessonDate}' AND start_time = '${COUPON_START_TIME}')
       OR (lesson_date = '${singlePaymentLessonDate}' AND start_time = '${SINGLE_PAYMENT_START_TIME}');

    DELETE FROM members
    WHERE auth_subject IN ('${COUPON_MEMBER_SUBJECT}', '${SINGLE_PAYMENT_MEMBER_SUBJECT}');
  `)
}

function runSql(sql: string) {
  return execFileSync(
    'docker',
    [
      'exec',
      '-i',
      'horse-mysql',
      'sh',
      '-c',
      'exec env MYSQL_PWD="$MYSQL_PASSWORD" mysql --user="$MYSQL_USER" --database="$MYSQL_DATABASE" --default-character-set=utf8mb4 --batch --skip-column-names',
    ],
    { input: sql, encoding: 'utf8' },
  ).trim()
}

function createJwt(subject: string, role: 'MEMBER' | 'ADMIN') {
  const issuedAt = Math.floor(Date.now() / 1000)
  const header = encodeJwtPart({ alg: 'HS256', typ: 'JWT' })
  const payload = encodeJwtPart({ sub: subject, roles: [role], iat: issuedAt, exp: issuedAt + 3600 })
  const signature = createHmac('sha256', process.env.JWT_SECRET ?? DEFAULT_JWT_SECRET)
    .update(`${header}.${payload}`)
    .digest('base64url')
  return `${header}.${payload}.${signature}`
}

function encodeJwtPart(value: object) {
  return Buffer.from(JSON.stringify(value)).toString('base64url')
}

function corsHeaders() {
  return {
    'access-control-allow-origin': WEB_BASE_URL,
    'access-control-allow-methods': 'GET,POST,PATCH,DELETE,OPTIONS',
    'access-control-allow-headers': 'authorization,content-type',
  }
}

function classCapacityJson() {
  return JSON.stringify({
    FIRST_RIDE: 4,
    ROUND_BEGINNER: 4,
    ROUND_TROT: 4,
    LARGE_ARENA_BEGINNER: 5,
    LARGE_ARENA_TROT: 5,
    DRESSAGE: 5,
    JUMPING: 5,
  })
}

function mvpTwoSubjects() {
  return {
    beforeChange: 'e2e-m2-11-before-change',
    afterChange: 'e2e-m2-11-after-change',
    sameDayChange: 'e2e-m2-11-same-day-change',
    beforeCancel: 'e2e-m2-11-before-cancel',
    afterCancel: 'e2e-m2-11-after-cancel',
    adminCancel: 'e2e-m2-11-admin-cancel',
  } as const
}

function findTimeSlot(lessonDate: string, startTime: string) {
  return Number(runSql(`
    SELECT id FROM time_slot_capacities
    WHERE lesson_date = '${lessonDate}' AND start_time = '${startTime}';
  `))
}

function quoteList(values: readonly string[]) {
  return values.map((value) => `'${value}'`).join(', ')
}

function isWeekend(dateKey: string) {
  const day = new Date(`${dateKey}T00:00:00Z`).getUTCDay()
  return day === 0 || day === 6
}

function seoulDateKey() {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(new Date())
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return `${values.year}-${values.month}-${values.day}`
}

function addDays(dateKey: string, amount: number) {
  const date = new Date(`${dateKey}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + amount)
  return date.toISOString().slice(0, 10)
}
