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
