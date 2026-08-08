import { createHmac } from 'node:crypto'
import { execFileSync } from 'node:child_process'
import type { Browser, BrowserContextOptions, Page, Route } from '@playwright/test'

const BACKEND_BASE_URL = process.env.HORSE_E2E_BACKEND_BASE_URL ?? 'http://localhost:8080'
const WEB_BASE_URL = process.env.HORSE_E2E_WEB_BASE_URL ?? 'http://localhost:5173'
const LEGACY_CLIENT_API_BASE_URL = 'http://localhost:8080'
const E2E_DATABASE = process.env.HORSE_E2E_DATABASE
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

export interface MvpThreeFixture {
  lessonDate: string
  pendingMemberName: string
  completedMemberName: string
  pendingReservationId: number
  completedReservationId: number
  completedCouponId: number
}

export interface MvpThreeSnapshot {
  status: string
  remainingCount: number
  heldCount: number
  generalRideCount: number
  usedLogCount: number
}

export interface ReservationLifecycleFixture {
  memberSubject: string
  pastLessonDate: string
  futureLessonDate: string
  overdueMemberName: string
  upcomingMemberName: string
}

export interface M31R14ClosureFixture {
  dateClosureDate: string
  emptyClosureDate: string
  timeSlotClosureDate: string
  timeSlotClosureId: number
  timeSlotClosureReservationId: number
  timeSlotWithdrawId: number
}

export interface M31R14ScheduleSynchronizationSnapshot {
  status: string
  activeVersion: number
  pendingVersion?: number
}

export interface M31R14ScheduleConfigurationFixture {
  activeVersion: number
  guardVersion: number
  lastCompletedAt?: string
  lastFailedAt?: string
  lastFailureCode?: string
  lastFailureSummary?: string
  scheduleDateVersions: Array<{ scheduleDate: string; appliedVersion: number }>
  appliedVersion?: number
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

export async function createAuthenticatedPage(
  browser: Browser,
  authSubject: string,
  role: 'MEMBER' | 'ADMIN',
  contextOptions?: BrowserContextOptions,
) {
  const context = await browser.newContext(contextOptions)
  const page = await context.newPage()
  const token = createJwt(authSubject, role)
  await context.addCookies([{
    name: 'XSRF-TOKEN',
    value: 'e2e-csrf-token',
    url: WEB_BASE_URL,
    sameSite: 'Lax',
  }])

  const handleApiRoute = async (route: Route) => {
    if (route.request().method() === 'OPTIONS') {
      await route.fulfill({
        status: 204,
        headers: corsHeaders(),
      })
      return
    }

    const path = new URL(route.request().url()).pathname
    if (path === '/api/auth/web/csrf') {
      await route.fulfill({
        status: 200,
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({ headerName: 'X-XSRF-TOKEN', cookieName: 'XSRF-TOKEN' }),
      })
      return
    }
    if (path === '/api/auth/web/login') {
      await route.fulfill({
        status: 200,
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          accessToken: token,
          tokenType: 'Bearer',
          accessTokenExpiresAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
        }),
      })
      return
    }
    if (path === '/api/auth/me') {
      await route.fulfill({
        status: 200,
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify({
          subject: authSubject,
          memberId: null,
          email: `${authSubject}@e2e.test`,
          role,
          status: 'ACTIVE',
        }),
      })
      return
    }

    const response = await route.fetch({
      url: route.request().url()
        .replace(WEB_BASE_URL, BACKEND_BASE_URL)
        .replace(LEGACY_CLIENT_API_BASE_URL, BACKEND_BASE_URL),
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
  }
  await page.route(`${WEB_BASE_URL}/api/**`, handleApiRoute)
  await page.route(`${LEGACY_CLIENT_API_BASE_URL}/api/**`, handleApiRoute)

  await page.goto('/login')
  await page.getByLabel('이메일').fill(`${authSubject}@e2e.test`)
  await page.getByLabel('비밀번호').fill('e2e-password-not-sent-to-backend')
  await page.getByRole('button', { name: '로그인' }).click()
  const destination = role === 'ADMIN' ? '**/admin' : '**/reservations'
  await Promise.race([
    page.waitForURL(destination),
    page.getByRole('alert').waitFor({ state: 'visible' }),
  ])
  if (!page.url().endsWith(role === 'ADMIN' ? '/admin' : '/reservations')) {
    throw new Error(`E2E authentication failed: ${await page.getByRole('alert').textContent()}`)
  }

  return page
}

export async function navigateWithinApp(page: Page, destination: string) {
  await page.evaluate((path) => {
    window.history.pushState({}, '', path)
    window.dispatchEvent(new PopStateEvent('popstate'))
  }, destination)
  await page.waitForURL((url) => `${url.pathname}${url.search}${url.hash}` === destination)
}

export async function refreshWithinApp(page: Page) {
  const destination = await page.evaluate(() => `${location.pathname}${location.search}${location.hash}`)
  await navigateWithinApp(page, '/')
  await navigateWithinApp(page, destination)
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
      admin_closed
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

export function makeMvpOneCouponReservationAttendable(fixture: MvpOneFixture) {
  const lessonDate = addDays(seoulDateKey(), -1)
  runSql(`
    SET @active_config_version = (
      SELECT active_version
      FROM schedule_config_guard
      WHERE id = 1
    );

    INSERT INTO schedule_dates (schedule_date, status, applied_config_version)
    VALUES ('${lessonDate}', 'NORMAL', @active_config_version)
    ON DUPLICATE KEY UPDATE
      status = VALUES(status),
      applied_config_version = VALUES(applied_config_version);

    UPDATE reservations reservation
    JOIN members member ON member.id = reservation.member_id
    SET reservation.lesson_date = '${lessonDate}'
    WHERE member.auth_subject = '${COUPON_MEMBER_SUBJECT}';

    UPDATE time_slot_capacities
    SET lesson_date = '${lessonDate}'
    WHERE id = ${fixture.couponMember.timeSlotId};
  `)
  fixture.couponMember.lessonDate = lessonDate
}

export function prepareMvpTwoFixture(): MvpTwoFixture {
  const today = seoulDateKey()
  const beforeLessonDate = addDays(today, 2)
  const beforeTargetDate = addDays(today, 3)
  const afterTargetDate = addDays(today, 4)
  const afterCutoffLessonDate = seoulHour() >= 21 ? addDays(today, 1) : today
  const afterChangeStartTime = afterCutoffLessonDate === today ? '23:00:00' : '10:00:00'
  const afterCancelStartTime = afterCutoffLessonDate === today ? '23:05:00' : '14:00:00'
  const adminCancelStartTime = afterCutoffLessonDate === today ? '23:14:00' : '15:00:00'
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
      lesson_date, start_time, total_capacity, round_arena_capacity, class_capacity_json, admin_closed
    ) VALUES
      ('${beforeLessonDate}', '09:00:00', 8, 4, '${classCapacityJson()}', FALSE),
      ('${beforeTargetDate}', '09:00:00', 8, 4, '${classCapacityJson()}', FALSE),
      ('${afterCutoffLessonDate}', '${afterChangeStartTime}', 8, 4, '${classCapacityJson()}', FALSE),
      ('${afterTargetDate}', '10:00:00', 8, 4, '${classCapacityJson()}', FALSE),
      ('${today}', '11:00:00', 8, 4, '${classCapacityJson()}', FALSE),
      ('${today}', '12:00:00', 8, 4, '${classCapacityJson()}', FALSE),
      ('${beforeLessonDate}', '13:00:00', 8, 4, '${classCapacityJson()}', FALSE),
      ('${afterCutoffLessonDate}', '${afterCancelStartTime}', 8, 4, '${classCapacityJson()}', FALSE),
      ('${afterCutoffLessonDate}', '${adminCancelStartTime}', 8, 4, '${classCapacityJson()}', FALSE);

    INSERT INTO reservations (
      member_id, class_type, lesson_date, start_time, status, payment_source, coupon_id,
      approval_requested_at, admin_confirmed_at
    )
    SELECT member.id, 'ROUND_BEGINNER', '${beforeLessonDate}', '09:00:00',
      'confirmed', 'coupon', coupon.id, NOW(6), NOW(6)
    FROM members member JOIN coupons coupon ON coupon.member_id = member.id
    WHERE member.auth_subject = '${subjects.beforeChange}'
    UNION ALL
    SELECT member.id, 'ROUND_BEGINNER', '${afterCutoffLessonDate}', '${afterChangeStartTime}',
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
    SELECT member.id, 'ROUND_BEGINNER', '${afterCutoffLessonDate}', '${afterCancelStartTime}',
      'confirmed', 'coupon', coupon.id, NOW(6), NOW(6)
    FROM members member JOIN coupons coupon ON coupon.member_id = member.id
    WHERE member.auth_subject = '${subjects.afterCancel}'
    UNION ALL
    SELECT member.id, 'ROUND_BEGINNER', '${afterCutoffLessonDate}', '${adminCancelStartTime}',
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
      name: 'E2E 무료 변경', lessonDate: afterCutoffLessonDate, startTime: afterChangeStartTime,
      timeSlotId: findTimeSlot(afterCutoffLessonDate, afterChangeStartTime),
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
      name: 'E2E 마감후 취소', lessonDate: afterCutoffLessonDate, startTime: afterCancelStartTime,
      timeSlotId: findTimeSlot(afterCutoffLessonDate, afterCancelStartTime),
    },
    adminCancel: {
      ...fixtures.adminCancel,
      name: 'E2E 관리자 취소', lessonDate: afterCutoffLessonDate, startTime: adminCancelStartTime,
      timeSlotId: findTimeSlot(afterCutoffLessonDate, adminCancelStartTime),
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

    DELETE idempotency
    FROM reservation_application_idempotencies idempotency
    JOIN reservations reservation ON reservation.id = idempotency.reservation_id
    JOIN members member ON member.id = reservation.member_id
    WHERE member.auth_subject IN (${quoteList(subjects)});

    DELETE reservation
    FROM reservations reservation
    JOIN members member ON member.id = reservation.member_id
    WHERE member.auth_subject IN (${quoteList(subjects)});

    DELETE member_day_guard
    FROM reservation_member_day_guards member_day_guard
    JOIN members member ON member.id = member_day_guard.member_id
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
       OR (lesson_date IN ('${today}', '${addDays(today, 1)}')
         AND start_time IN ('10:00:00', '11:00:00', '12:00:00', '14:00:00', '15:00:00',
           '23:00:00', '23:05:00', '23:14:00'));
  `)
}

export function prepareMvpThreeFixture(): MvpThreeFixture {
  const lessonDate = addDays(seoulDateKey(), -1)
  const pendingSubject = 'e2e-m3-11-pending-member'
  const completedSubject = 'e2e-m3-11-completed-member'
  const pendingMemberName = 'E2E 운영 승인대기'
  const completedMemberName = 'E2E 운영 일괄완료'

  cleanupMvpThreeFixture(lessonDate)
  runSql(`
    SET @active_config_version = (
      SELECT active_version
      FROM schedule_config_guard
      WHERE id = 1
    );

    INSERT INTO schedule_dates (schedule_date, status, applied_config_version)
    VALUES ('${lessonDate}', 'NORMAL', @active_config_version)
    ON DUPLICATE KEY UPDATE
      status = VALUES(status),
      applied_config_version = VALUES(applied_config_version);

    INSERT INTO members (auth_subject, name, phone, general_ride_count)
    VALUES
      ('${pendingSubject}', '${pendingMemberName}', '010-9300-0001', 6),
      ('${completedSubject}', '${completedMemberName}', '010-9300-0002', 6);

    INSERT INTO coupons (
      member_id, coupon_type, total_count, remaining_count, held_count, status, created_by
    )
    SELECT id, 'general', 10, 10, 1, 'active', 'e2e-m3-11'
    FROM members
    WHERE auth_subject IN ('${pendingSubject}', '${completedSubject}');

    INSERT INTO time_slot_capacities (
      lesson_date, start_time, total_capacity, round_arena_capacity, class_capacity_json, admin_closed
    ) VALUES
      ('${lessonDate}', '16:00:00', 8, 4, '${classCapacityJson()}', FALSE),
      ('${lessonDate}', '17:00:00', 8, 4, '${classCapacityJson()}', FALSE);

    INSERT INTO reservations (
      member_id, class_type, lesson_date, start_time, status, payment_source, coupon_id,
      approval_requested_at, admin_confirmed_at
    )
    SELECT member.id, 'ROUND_TROT', '${lessonDate}', '16:00:00',
      'pending_admin_approval', 'coupon', coupon.id, DATE_SUB(NOW(6), INTERVAL 3 HOUR), NULL
    FROM members member JOIN coupons coupon ON coupon.member_id = member.id
    WHERE member.auth_subject = '${pendingSubject}'
    UNION ALL
    SELECT member.id, 'ROUND_TROT', '${lessonDate}', '17:00:00',
      'confirmed', 'coupon', coupon.id, DATE_SUB(NOW(6), INTERVAL 2 HOUR), NOW(6)
    FROM members member JOIN coupons coupon ON coupon.member_id = member.id
    WHERE member.auth_subject = '${completedSubject}';

    INSERT INTO coupon_usage_logs (
      coupon_id, reservation_id, member_id, action, count_delta, occurred_at, actor_type, memo
    )
    SELECT coupon.id, reservation.id, member.id, 'held', 1, NOW(6), 'member', 'M3-11 fixture'
    FROM members member
    JOIN coupons coupon ON coupon.member_id = member.id
    JOIN reservations reservation ON reservation.member_id = member.id
    WHERE member.auth_subject IN ('${pendingSubject}', '${completedSubject}');

    INSERT INTO coupon_usage_logs (
      coupon_id, reservation_id, member_id, action, count_delta, occurred_at, actor_type, memo
    )
    SELECT coupon.id, reservation.id, member.id, 'confirmed', 0, NOW(6), 'admin', 'M3-11 fixture'
    FROM members member
    JOIN coupons coupon ON coupon.member_id = member.id
    JOIN reservations reservation ON reservation.member_id = member.id
    WHERE member.auth_subject = '${completedSubject}';

    INSERT INTO reservation_change_logs (
      reservation_id, actor_auth_subject, actor_type, from_status, to_status,
      from_lesson_date, from_start_time, to_lesson_date, to_start_time,
      change_type, coupon_action, memo, created_at
    )
    SELECT reservation.id, '${completedSubject}', 'member', 'confirmed', 'confirmed',
      '${lessonDate}', '16:30:00', '${lessonDate}', '17:00:00',
      'schedule_changed', 'none', 'M3-11 감사 fixture', NOW(6)
    FROM reservations reservation
    JOIN members member ON member.id = reservation.member_id
    WHERE member.auth_subject = '${completedSubject}';
  `)

  const [pendingReservationId, completedReservationId, completedCouponId] = runSql(`
    SELECT
      (SELECT reservation.id FROM reservations reservation JOIN members member ON member.id = reservation.member_id
        WHERE member.auth_subject = '${pendingSubject}'),
      (SELECT reservation.id FROM reservations reservation JOIN members member ON member.id = reservation.member_id
        WHERE member.auth_subject = '${completedSubject}'),
      (SELECT coupon.id FROM coupons coupon JOIN members member ON member.id = coupon.member_id
        WHERE member.auth_subject = '${completedSubject}');
  `).split('\t').map(Number)

  return {
    lessonDate,
    pendingMemberName,
    completedMemberName,
    pendingReservationId,
    completedReservationId,
    completedCouponId,
  }
}

export function cleanupMvpThreeFixture(lessonDate: string) {
  runSql(`
    DELETE change_log
    FROM reservation_change_logs change_log
    JOIN reservations reservation ON reservation.id = change_log.reservation_id
    JOIN members member ON member.id = reservation.member_id
    WHERE member.auth_subject IN ('e2e-m3-11-pending-member', 'e2e-m3-11-completed-member');

    DELETE usage_log
    FROM coupon_usage_logs usage_log
    JOIN members member ON member.id = usage_log.member_id
    WHERE member.auth_subject IN ('e2e-m3-11-pending-member', 'e2e-m3-11-completed-member');

    DELETE idempotency
    FROM reservation_application_idempotencies idempotency
    JOIN reservations reservation ON reservation.id = idempotency.reservation_id
    JOIN members member ON member.id = reservation.member_id
    WHERE member.auth_subject IN ('e2e-m3-11-pending-member', 'e2e-m3-11-completed-member');

    DELETE reservation
    FROM reservations reservation
    JOIN members member ON member.id = reservation.member_id
    WHERE member.auth_subject IN ('e2e-m3-11-pending-member', 'e2e-m3-11-completed-member');

    DELETE member_day_guard
    FROM reservation_member_day_guards member_day_guard
    JOIN members member ON member.id = member_day_guard.member_id
    WHERE member.auth_subject IN ('e2e-m3-11-pending-member', 'e2e-m3-11-completed-member');

    DELETE coupon
    FROM coupons coupon
    JOIN members member ON member.id = coupon.member_id
    WHERE member.auth_subject IN ('e2e-m3-11-pending-member', 'e2e-m3-11-completed-member');

    DELETE FROM members
    WHERE auth_subject IN ('e2e-m3-11-pending-member', 'e2e-m3-11-completed-member');

    DELETE FROM time_slot_capacities
    WHERE lesson_date = '${lessonDate}' AND start_time IN ('16:00:00', '17:00:00');
  `)
}

export function readMvpThreeSnapshot(reservationId: number): MvpThreeSnapshot {
  const values = runSql(`
    SELECT reservation.status, coupon.remaining_count, coupon.held_count, member.general_ride_count,
      (SELECT COUNT(*) FROM coupon_usage_logs usage_log
        WHERE usage_log.reservation_id = reservation.id AND usage_log.action = 'used')
    FROM reservations reservation
    JOIN members member ON member.id = reservation.member_id
    JOIN coupons coupon ON coupon.id = reservation.coupon_id
    WHERE reservation.id = ${reservationId};
  `).split('\t')
  return {
    status: values[0],
    remainingCount: Number(values[1]),
    heldCount: Number(values[2]),
    generalRideCount: Number(values[3]),
    usedLogCount: Number(values[4]),
  }
}

export function prepareReservationLifecycleFixture(): ReservationLifecycleFixture {
  const memberSubject = 'e2e-m3-18-member'
  const overdueSubject = 'e2e-m3-18-overdue'
  const upcomingSubject = 'e2e-m3-18-upcoming'
  const pastLessonDate = addDays(seoulDateKey(), -1)
  const futureLessonDate = addDays(seoulDateKey(), 1)
  const overdueMemberName = 'E2E 지난 미처리'
  const upcomingMemberName = 'E2E 시작 전 수업'

  cleanupReservationLifecycleFixture()
  runSql(`
    SET @active_config_version := (
      SELECT active_version
      FROM schedule_config_guard
      WHERE id = 1
    );

    INSERT INTO schedule_dates (schedule_date, status, applied_config_version)
    VALUES
      ('${pastLessonDate}', 'NORMAL', @active_config_version),
      ('${futureLessonDate}', 'NORMAL', @active_config_version)
    ON DUPLICATE KEY UPDATE schedule_date = VALUES(schedule_date);

    INSERT INTO members (auth_subject, name, phone, general_ride_count)
    VALUES
      ('${memberSubject}', 'E2E 생명주기 회원', '010-9318-0001', 6),
      ('${overdueSubject}', '${overdueMemberName}', '010-9318-0002', 6),
      ('${upcomingSubject}', '${upcomingMemberName}', '010-9318-0003', 6);

    INSERT INTO coupons (
      member_id, coupon_type, total_count, remaining_count, held_count, status, created_by
    )
    SELECT id, 'general', 10, 10, 2, 'active', 'e2e-m3-18'
    FROM members
    WHERE auth_subject = '${memberSubject}';

    INSERT INTO reservations (
      member_id, class_type, lesson_date, start_time, status, payment_source, coupon_id,
      approval_requested_at, admin_confirmed_at
    )
    SELECT member.id, 'ROUND_TROT', '${pastLessonDate}', '09:00:00',
      'pending_admin_approval', 'coupon', coupon.id, DATE_SUB(NOW(6), INTERVAL 2 DAY), NULL
    FROM members member JOIN coupons coupon ON coupon.member_id = member.id
    WHERE member.auth_subject = '${memberSubject}'
    UNION ALL
    SELECT member.id, 'ROUND_BEGINNER', '${futureLessonDate}', '09:00:00',
      'pending_admin_approval', 'coupon', coupon.id, NOW(6), NULL
    FROM members member JOIN coupons coupon ON coupon.member_id = member.id
    WHERE member.auth_subject = '${memberSubject}'
    UNION ALL
    SELECT member.id, 'ROUND_TROT', '${pastLessonDate}', '10:00:00',
      'confirmed', 'single_payment', NULL, DATE_SUB(NOW(6), INTERVAL 2 DAY), DATE_SUB(NOW(6), INTERVAL 2 DAY)
    FROM members member
    WHERE member.auth_subject = '${overdueSubject}'
    UNION ALL
    SELECT member.id, 'ROUND_TROT', '${futureLessonDate}', '10:00:00',
      'confirmed', 'single_payment', NULL, NOW(6), NOW(6)
    FROM members member
    WHERE member.auth_subject = '${upcomingSubject}';

    INSERT INTO coupon_usage_logs (
      coupon_id, reservation_id, member_id, action, count_delta, occurred_at, actor_type, memo
    )
    SELECT coupon.id, reservation.id, member.id, 'held', 1, NOW(6), 'member', 'M3-18 fixture'
    FROM members member
    JOIN coupons coupon ON coupon.member_id = member.id
    JOIN reservations reservation ON reservation.member_id = member.id
    WHERE member.auth_subject = '${memberSubject}';
  `)

  return { memberSubject, pastLessonDate, futureLessonDate, overdueMemberName, upcomingMemberName }
}

export function cleanupReservationLifecycleFixture() {
  runSql(`
    DELETE change_log
    FROM reservation_change_logs change_log
    JOIN reservations reservation ON reservation.id = change_log.reservation_id
    JOIN members member ON member.id = reservation.member_id
    WHERE member.auth_subject IN ('e2e-m3-18-member', 'e2e-m3-18-overdue', 'e2e-m3-18-upcoming');

    DELETE usage_log
    FROM coupon_usage_logs usage_log
    JOIN members member ON member.id = usage_log.member_id
    WHERE member.auth_subject IN ('e2e-m3-18-member', 'e2e-m3-18-overdue', 'e2e-m3-18-upcoming');

    DELETE reservation
    FROM reservations reservation
    JOIN members member ON member.id = reservation.member_id
    WHERE member.auth_subject IN ('e2e-m3-18-member', 'e2e-m3-18-overdue', 'e2e-m3-18-upcoming');

    DELETE member_day_guard
    FROM reservation_member_day_guards member_day_guard
    JOIN members member ON member.id = member_day_guard.member_id
    WHERE member.auth_subject IN ('e2e-m3-18-member', 'e2e-m3-18-overdue', 'e2e-m3-18-upcoming');

    DELETE coupon
    FROM coupons coupon
    JOIN members member ON member.id = coupon.member_id
    WHERE member.auth_subject = 'e2e-m3-18-member';

    DELETE FROM members
    WHERE auth_subject IN ('e2e-m3-18-member', 'e2e-m3-18-overdue', 'e2e-m3-18-upcoming');
  `)
}

export function prepareM31R14ScheduleConfigurationFixture(): M31R14ScheduleConfigurationFixture {
  cleanupM31R14ScheduleConfigurationArtifacts()
  const guard = runSql(`
    SELECT status,
      active_version,
      version,
      COALESCE(DATE_FORMAT(last_completed_at, '%Y-%m-%d %H:%i:%s.%f'), ''),
      COALESCE(DATE_FORMAT(last_failed_at, '%Y-%m-%d %H:%i:%s.%f'), ''),
      COALESCE(last_failure_code, ''),
      COALESCE(last_failure_summary, '')
    FROM schedule_config_guard
    WHERE id = 1;
  `).split('\t')
  if (guard[0] !== 'ACTIVE') {
    throw new Error('M31-R14 일정 설정 fixture는 ACTIVE 상태에서만 시작할 수 있습니다.')
  }

  const dateRows = runSql(`
    SELECT schedule_date, applied_config_version
    FROM schedule_dates
    ORDER BY schedule_date;
  `)
  const scheduleDateVersions = dateRows
    ? dateRows.split('\n').map((row) => {
      const [scheduleDate, appliedVersion] = row.split('\t')
      return { scheduleDate, appliedVersion: Number(appliedVersion) }
    })
    : []

  return {
    activeVersion: Number(guard[1]),
    guardVersion: Number(guard[2]),
    lastCompletedAt: guard[3] || undefined,
    lastFailedAt: guard[4] || undefined,
    lastFailureCode: guard[5] || undefined,
    lastFailureSummary: guard[6] || undefined,
    scheduleDateVersions,
  }
}

export function markM31R14ScheduleSynchronizationFailed() {
  const updated = Number(runSql(`
    UPDATE schedule_config_guard
    SET last_failed_at = NOW(6),
        last_failure_code = 'E2E_FORCED_FAILURE',
        last_failure_summary = 'Checkpoint retry contract'
    WHERE id = 1
      AND status = 'SYNCING';
    SELECT ROW_COUNT();
  `))
  if (updated !== 1) {
    throw new Error('M31-R14 실패 주입 전에 SYNCING 상태가 유지되지 않았습니다.')
  }
}

export function readM31R14ScheduleSynchronizationSnapshot(): M31R14ScheduleSynchronizationSnapshot {
  const values = runSql(`
    SELECT status, active_version, COALESCE(pending_version, '')
    FROM schedule_config_guard
    WHERE id = 1;
  `).split('\t')

  return {
    status: values[0],
    activeVersion: Number(values[1]),
    pendingVersion: values[2] ? Number(values[2]) : undefined,
  }
}

export function cleanupM31R14ScheduleConfigurationFixture(
  fixture: M31R14ScheduleConfigurationFixture,
) {
  cleanupM31R14ScheduleConfigurationArtifacts()
  const restoreExistingDates = fixture.scheduleDateVersions
    .map(({ scheduleDate, appliedVersion }) => `
      UPDATE schedule_dates
      SET applied_config_version = ${appliedVersion}
      WHERE schedule_date = '${scheduleDate}';
    `)
    .join('\n')

  runSql(`
    ${fixture.appliedVersion === undefined ? '' : `
      UPDATE schedule_dates
      SET applied_config_version = ${fixture.activeVersion}
      WHERE applied_config_version = ${fixture.appliedVersion};
    `}
    ${restoreExistingDates}

    UPDATE schedule_config_guard
    SET status = 'ACTIVE',
        active_version = ${fixture.activeVersion},
        pending_version = NULL,
        sync_started_at = NULL,
        sync_started_by = NULL,
        last_completed_at = ${sqlLiteral(fixture.lastCompletedAt)},
        last_failed_at = ${sqlLiteral(fixture.lastFailedAt)},
        last_failure_code = ${sqlLiteral(fixture.lastFailureCode)},
        last_failure_summary = ${sqlLiteral(fixture.lastFailureSummary)},
        version = ${fixture.guardVersion}
    WHERE id = 1;
  `)
}

function cleanupM31R14ScheduleConfigurationArtifacts() {
  runSql(`
    SET @e2e_pending_version := (
      SELECT CASE
        WHEN status = 'SYNCING' AND sync_started_by = 'e2e-m31-r14-config-admin'
          THEN pending_version
        ELSE NULL
      END
      FROM schedule_config_guard
      WHERE id = 1
    );
    SET @e2e_active_version := (
      SELECT active_version
      FROM schedule_config_guard
      WHERE id = 1
    );

    DELETE impact
    FROM time_slot_closure_impacts impact
    JOIN time_slot_closures closure ON closure.id = impact.closure_id
    JOIN time_slot_capacities time_slot ON time_slot.id = closure.time_slot_id
    JOIN regular_schedule_templates template ON template.id = time_slot.template_id
    WHERE template.created_by = 'e2e-m31-r14-config-admin';

    DELETE closure
    FROM time_slot_closures closure
    JOIN time_slot_capacities time_slot ON time_slot.id = closure.time_slot_id
    JOIN regular_schedule_templates template ON template.id = time_slot.template_id
    WHERE template.created_by = 'e2e-m31-r14-config-admin';

    DELETE time_slot
    FROM time_slot_capacities time_slot
    JOIN regular_schedule_templates template ON template.id = time_slot.template_id
    WHERE template.created_by = 'e2e-m31-r14-config-admin';

    DELETE FROM regular_schedule_templates
    WHERE created_by = 'e2e-m31-r14-config-admin';

    DELETE FROM schedule_audit_logs
    WHERE actor_auth_subject = 'e2e-m31-r14-config-admin';

    UPDATE schedule_config_guard
    SET status = 'ACTIVE',
        pending_version = NULL,
        sync_started_at = NULL,
        sync_started_by = NULL
    WHERE id = 1
      AND @e2e_pending_version IS NOT NULL;

    UPDATE schedule_dates
    SET applied_config_version = @e2e_active_version
    WHERE @e2e_pending_version IS NOT NULL
      AND applied_config_version = @e2e_pending_version;
  `)
}

export function prepareM31R14ClosureFixture(): M31R14ClosureFixture {
  const today = seoulDateKey()
  const dateClosureDate = addDays(today, 14)
  const emptyClosureDate = addDays(today, 15)
  const timeSlotClosureDate = addDays(today, 16)
  const timeSlotWithdrawDate = addDays(today, 17)
  const defaultDate = addDays(today, 1)

  cleanupM31R14ClosureFixture()
  runSql(`
    SET @active_config_version := (
      SELECT active_version
      FROM schedule_config_guard
      WHERE id = 1
    );

    INSERT INTO schedule_dates (schedule_date, status, applied_config_version)
    VALUES
      ('${defaultDate}', 'NORMAL', @active_config_version),
      ('${dateClosureDate}', 'NORMAL', @active_config_version),
      ('${emptyClosureDate}', 'NORMAL', @active_config_version),
      ('${timeSlotClosureDate}', 'NORMAL', @active_config_version),
      ('${timeSlotWithdrawDate}', 'NORMAL', @active_config_version)
    ON DUPLICATE KEY UPDATE schedule_date = VALUES(schedule_date);

    INSERT INTO members (auth_subject, name, phone, general_ride_count)
    VALUES
      ('e2e-m31-r14-date-member', 'R14 날짜 회원', '010-9400-0001', 6),
      ('e2e-m31-r14-slot-member', 'R14 휴강 회원', '010-9400-0002', 6),
      ('e2e-m31-r14-withdraw-member', 'R14 철회 회원', '010-9400-0003', 6);

    INSERT INTO time_slot_capacities (
      lesson_date,
      start_time,
      end_time,
      source,
      total_capacity,
      round_arena_capacity,
      class_capacity_json,
      admin_closed,
      recurring_holiday_closed,
      template_inactive_closed
    ) VALUES
      ('${dateClosureDate}', '18:00:00', '18:45:00', 'MANUAL', 8, 4, '${classCapacityJson()}', FALSE, FALSE, FALSE),
      ('${timeSlotClosureDate}', '19:00:00', '19:45:00', 'MANUAL', 8, 4, '${classCapacityJson()}', FALSE, FALSE, FALSE),
      ('${timeSlotWithdrawDate}', '20:00:00', '20:45:00', 'MANUAL', 8, 4, '${classCapacityJson()}', FALSE, FALSE, FALSE);

    INSERT INTO reservations (
      member_id,
      class_type,
      lesson_date,
      start_time,
      end_time,
      status,
      payment_source,
      coupon_id,
      approval_requested_at,
      admin_confirmed_at
    )
    SELECT id, 'ROUND_TROT', '${dateClosureDate}', '18:00:00', '18:45:00',
      'confirmed', 'single_payment', NULL, NOW(6), NOW(6)
    FROM members
    WHERE auth_subject = 'e2e-m31-r14-date-member'
    UNION ALL
    SELECT id, 'ROUND_TROT', '${timeSlotClosureDate}', '19:00:00', '19:45:00',
      'confirmed', 'single_payment', NULL, NOW(6), NOW(6)
    FROM members
    WHERE auth_subject = 'e2e-m31-r14-slot-member'
    UNION ALL
    SELECT id, 'ROUND_TROT', '${timeSlotWithdrawDate}', '20:00:00', '20:45:00',
      'confirmed', 'single_payment', NULL, NOW(6), NOW(6)
    FROM members
    WHERE auth_subject = 'e2e-m31-r14-withdraw-member';
  `)

  const nonNormalDateCount = Number(runSql(`
    SELECT COUNT(*)
    FROM schedule_dates
    WHERE schedule_date IN (
      '${defaultDate}',
      '${dateClosureDate}',
      '${emptyClosureDate}',
      '${timeSlotClosureDate}',
      '${timeSlotWithdrawDate}'
    )
      AND (
        status <> 'NORMAL'
        OR applied_config_version <> (
          SELECT active_version
          FROM schedule_config_guard
          WHERE id = 1
        )
      );
  `))
  if (nonNormalDateCount > 0) {
    throw new Error('M31-R14 fixture 대상 날짜가 NORMAL 상태가 아닙니다.')
  }

  const values = runSql(`
    SELECT
      (SELECT id
       FROM time_slot_capacities
       WHERE lesson_date = '${timeSlotClosureDate}' AND start_time = '19:00:00'),
      (SELECT reservation.id
       FROM reservations reservation
       JOIN members member ON member.id = reservation.member_id
       WHERE member.auth_subject = 'e2e-m31-r14-slot-member'),
      (SELECT id
       FROM time_slot_capacities
       WHERE lesson_date = '${timeSlotWithdrawDate}' AND start_time = '20:00:00');
  `).split('\t').map(Number)

  return {
    dateClosureDate,
    emptyClosureDate,
    timeSlotClosureDate,
    timeSlotClosureId: values[0],
    timeSlotClosureReservationId: values[1],
    timeSlotWithdrawId: values[2],
  }
}

export function cleanupM31R14ClosureFixture() {
  runSql(`
    DROP TEMPORARY TABLE IF EXISTS e2e_m31_r14_time_slots;
    CREATE TEMPORARY TABLE e2e_m31_r14_time_slots (
      time_slot_id BIGINT PRIMARY KEY
    );

    INSERT IGNORE INTO e2e_m31_r14_time_slots (time_slot_id)
    SELECT DISTINCT time_slot.id
    FROM time_slot_capacities time_slot
    JOIN reservations reservation
      ON reservation.lesson_date = time_slot.lesson_date
     AND reservation.start_time = time_slot.start_time
    JOIN members member ON member.id = reservation.member_id
    WHERE member.auth_subject IN (
      'e2e-m31-r14-date-member',
      'e2e-m31-r14-slot-member',
      'e2e-m31-r14-withdraw-member'
    );

    INSERT IGNORE INTO e2e_m31_r14_time_slots (time_slot_id)
    SELECT closure.time_slot_id
    FROM time_slot_closures closure
    WHERE closure.started_by IN (
      'e2e-m31-r14-closure-admin',
      'e2e-m31-r14-admin'
    );

    DELETE impact
    FROM time_slot_closure_impacts impact
    JOIN time_slot_closures closure ON closure.id = impact.closure_id
    JOIN e2e_m31_r14_time_slots fixture_slot
      ON fixture_slot.time_slot_id = closure.time_slot_id;

    DELETE closure
    FROM time_slot_closures closure
    JOIN e2e_m31_r14_time_slots fixture_slot
      ON fixture_slot.time_slot_id = closure.time_slot_id;

    DELETE change_log
    FROM reservation_change_logs change_log
    JOIN reservations reservation ON reservation.id = change_log.reservation_id
    JOIN members member ON member.id = reservation.member_id
    WHERE member.auth_subject IN (
      'e2e-m31-r14-date-member',
      'e2e-m31-r14-slot-member',
      'e2e-m31-r14-withdraw-member'
    );

    DELETE usage_log
    FROM coupon_usage_logs usage_log
    JOIN members member ON member.id = usage_log.member_id
    WHERE member.auth_subject IN (
      'e2e-m31-r14-date-member',
      'e2e-m31-r14-slot-member',
      'e2e-m31-r14-withdraw-member'
    );

    DELETE reservation
    FROM reservations reservation
    JOIN members member ON member.id = reservation.member_id
    WHERE member.auth_subject IN (
      'e2e-m31-r14-date-member',
      'e2e-m31-r14-slot-member',
      'e2e-m31-r14-withdraw-member'
    );

    DELETE guard_row
    FROM reservation_member_day_guards guard_row
    JOIN members member ON member.id = guard_row.member_id
    WHERE member.auth_subject IN (
      'e2e-m31-r14-date-member',
      'e2e-m31-r14-slot-member',
      'e2e-m31-r14-withdraw-member'
    );

    DELETE time_slot
    FROM time_slot_capacities time_slot
    JOIN e2e_m31_r14_time_slots fixture_slot
      ON fixture_slot.time_slot_id = time_slot.id;

    DELETE FROM members
    WHERE auth_subject IN (
      'e2e-m31-r14-date-member',
      'e2e-m31-r14-slot-member',
      'e2e-m31-r14-withdraw-member'
    );

    DELETE FROM schedule_audit_logs
    WHERE actor_auth_subject IN (
      'e2e-m31-r14-closure-admin',
      'e2e-m31-r14-admin'
    );

    UPDATE schedule_dates
    SET status = 'NORMAL',
        resume_status = NULL,
        reason = NULL,
        changed_by = NULL
    WHERE changed_by IN (
      'e2e-m31-r14-closure-admin',
      'e2e-m31-r14-admin'
    );

    DROP TEMPORARY TABLE e2e_m31_r14_time_slots;
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

    DELETE idempotency
    FROM reservation_application_idempotencies idempotency
    JOIN reservations reservation ON reservation.id = idempotency.reservation_id
    JOIN members member ON member.id = reservation.member_id
    WHERE member.auth_subject IN ('${COUPON_MEMBER_SUBJECT}', '${SINGLE_PAYMENT_MEMBER_SUBJECT}');

    DELETE reservation
    FROM reservations reservation
    JOIN members member ON member.id = reservation.member_id
    WHERE member.auth_subject IN ('${COUPON_MEMBER_SUBJECT}', '${SINGLE_PAYMENT_MEMBER_SUBJECT}');

    DELETE member_day_guard
    FROM reservation_member_day_guards member_day_guard
    JOIN members member ON member.id = member_day_guard.member_id
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
  if (E2E_DATABASE !== undefined && !/^[A-Za-z0-9_]+$/.test(E2E_DATABASE)) {
    throw new Error('E2E database name contains unsupported characters.')
  }
  return execFileSync(
    'docker',
    [
      'exec',
      '-i',
      ...(E2E_DATABASE === undefined ? [] : ['--env', `HORSE_E2E_DATABASE=${E2E_DATABASE}`]),
      'horse-mysql',
      'sh',
      '-c',
      'exec env MYSQL_PWD="$MYSQL_PASSWORD" mysql --user="$MYSQL_USER" --database="${HORSE_E2E_DATABASE:-$MYSQL_DATABASE}" --default-character-set=utf8mb4 --batch --skip-column-names',
    ],
    { input: sql, encoding: 'utf8' },
  ).trim()
}

function sqlLiteral(value?: string) {
  if (value === undefined) return 'NULL'
  return `'${value.replaceAll('\\', '\\\\').replaceAll("'", "''")}'`
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

function seoulHour() {
  return Number(new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Seoul',
    hour: '2-digit',
    hourCycle: 'h23',
  }).format(new Date()))
}

function addDays(dateKey: string, amount: number) {
  const date = new Date(`${dateKey}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + amount)
  return date.toISOString().slice(0, 10)
}
