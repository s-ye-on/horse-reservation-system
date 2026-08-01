import { expect, test } from '@playwright/test'

const API_ORIGIN = 'http://localhost:8080'
const WEB_ORIGIN = process.env.HORSE_E2E_WEB_BASE_URL ?? 'http://127.0.0.1:5173'
const CORS_HEADERS = {
  'Access-Control-Allow-Origin': WEB_ORIGIN,
  'Access-Control-Allow-Headers': '*',
  'Access-Control-Allow-Methods': 'GET,POST,PATCH,DELETE,OPTIONS',
  'Content-Type': 'application/json',
}

test('날짜_휴무와_개별_휴강은_320px에서_가로_넘침_없이_조작할_수_있다', async ({ browser }) => {
  const context = await browser.newContext({ viewport: { width: 320, height: 800 } })
  const page = await context.newPage()

  await page.route(`${API_ORIGIN}/**`, async (route) => {
    if (route.request().method() === 'OPTIONS') {
      await route.fulfill({ status: 204, headers: CORS_HEADERS })
      return
    }

    const path = new URL(route.request().url()).pathname
    const body = path.endsWith('/closure-impact')
      ? dateImpactResponse()
      : path.endsWith('/timeslots/31/closure')
        ? timeSlotClosureResponse()
        : path.endsWith('/timeslots')
          ? [timeSlotResponse()]
          : scheduleDateResponse()
    await route.fulfill({ status: 200, headers: CORS_HEADERS, body: JSON.stringify(body) })
  })

  await page.goto('/admin/schedule-closures')
  await expect(page.getByRole('heading', { name: '날짜 전체 휴무' })).toBeVisible()
  await expectNoHorizontalOverflow(page)

  await page.getByLabel('운영 사유').fill('320px 휴무 확인')
  const dateClosureTrigger = page.getByRole('button', { name: '영향 확인 후 휴무 시작' })
  await dateClosureTrigger.click()
  const dialog = page.getByRole('dialog')
  await expect(dialog).toBeVisible()
  await expect(dialog).toBeInViewport()
  await expectNoHorizontalOverflow(page)
  await page.keyboard.press('Escape')
  await expect(dateClosureTrigger).toBeFocused()

  const dateTab = page.getByRole('tab', { name: '날짜 전체 휴무' })
  const slotTab = page.getByRole('tab', { name: '개별 TimeSlot 휴강' })
  await dateTab.focus()
  await page.keyboard.press('End')
  await expect(slotTab).toBeFocused()
  await expect(slotTab).toHaveAttribute('aria-selected', 'true')
  await expect(slotTab).toHaveAttribute('tabindex', '0')
  await expect(page.getByRole('tabpanel', { name: '개별 TimeSlot 휴강' })).toBeVisible()
  await page.keyboard.press('Home')
  await expect(dateTab).toBeFocused()
  await page.keyboard.press('ArrowRight')
  await expect(slotTab).toBeFocused()
  await page.keyboard.press('ArrowLeft')
  await expect(dateTab).toBeFocused()
  await expect(dateTab).toHaveAttribute('aria-selected', 'true')
  await expect(page.getByRole('tabpanel', { name: '날짜 전체 휴무' })).toBeVisible()
  await page.keyboard.press('ArrowRight')
  await expect(slotTab).toBeFocused()
  await expect(page.getByLabel('대상 TimeSlot')).toBeVisible()
  await expectNoHorizontalOverflow(page)

  await context.close()
})

async function expectNoHorizontalOverflow(page: import('@playwright/test').Page) {
  const dimensions = await page.evaluate(() => ({
    viewport: window.innerWidth,
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth,
  }))
  expect(dimensions.document).toBeLessThanOrEqual(dimensions.viewport)
  expect(dimensions.body).toBeLessThanOrEqual(dimensions.viewport)
}

function scheduleDateResponse() {
  return {
    scheduleDate: '2026-08-01',
    status: 'NORMAL',
    appliedConfigVersion: 7,
    version: 3,
  }
}

function dateImpactResponse() {
  return {
    scheduleDate: '2026-08-01',
    status: 'NORMAL',
    version: 3,
    initialReservationCount: 1,
    activeReservationCount: 1,
    resolvedReservationCount: 0,
    remainingReservationCount: 1,
    progressPercent: 0,
    reservations: [{
      reservationId: 20,
      memberId: 2,
      memberName: '김회원',
      memberPhone: '010-2222-2222',
      classType: 'ROUND_TROT',
      status: 'CONFIRMED',
      paymentSource: 'COUPON',
      couponId: 9,
    }],
    changed: false,
  }
}

function timeSlotResponse() {
  return {
    id: 31,
    lessonDate: '2026-08-02',
    startTime: '10:00:00',
    totalCapacity: 8,
    roundArenaCapacity: 4,
    classCapacities: {},
    closed: true,
  }
}

function timeSlotClosureResponse() {
  return {
    timeSlotId: 31,
    adminClosed: true,
    closed: true,
    status: 'IN_PROGRESS',
    reason: '우천',
    startedAt: '2026-07-30T10:00:00+09:00',
    version: 2,
    totalCount: 1,
    resolvedCount: 0,
    unresolvedCount: 1,
    progressPercent: 0,
    impacts: [{
      reservationId: 20,
      reservationStatusAtStart: 'CONFIRMED',
      currentStatus: 'CONFIRMED',
      resolved: false,
      moved: false,
      memberId: 2,
      memberName: '김회원',
      memberPhone: '010-2222-2222',
      classType: 'ROUND_TROT',
      paymentSource: 'COUPON',
      couponId: 9,
    }],
  }
}
