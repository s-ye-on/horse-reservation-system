import { expect, test, type Page, type Route } from '@playwright/test'
import { createAuthenticatedPage, navigateWithinApp } from './e2e-support'

const RESERVATION_ID = 10482

const reservation = {
  reservationId: RESERVATION_ID,
  classType: 'ROUND_BEGINNER',
  lessonDate: '2026-10-10',
  startTime: '09:00:00',
  status: 'confirmed',
  paymentSource: 'coupon',
  coupon: {
    couponId: 44,
    couponType: 'GENERAL',
    status: 'active',
    remainingCount: 6,
    heldCount: 1,
    availableCount: 5,
    expiresAt: '2026-12-31',
  },
  paymentDueAt: null,
  rejectionReason: null,
  couponAction: null,
  approvalRequestedAt: '2026-09-20T01:00:00Z',
  adminConfirmedAt: '2026-09-20T02:00:00Z',
  rejectedAt: null,
  cancelledAt: null,
  displayGroup: 'UPCOMING',
  actions: {
    change: { allowed: true, blockedReason: null },
    cancel: { allowed: true, blockedReason: null },
    complete: { allowed: false, blockedReason: null },
    noShow: { allowed: false, blockedReason: null },
    approve: { allowed: false, blockedReason: null },
  },
}

test('회원은_반응형_내_예약에서_서버_preview를_거쳐_변경과_취소를_확인한다', async ({ browser }) => {
  const page = await createAuthenticatedPage(
    browser,
    'e2e-member-reservation-management',
    'MEMBER',
    { viewport: { width: 1280, height: 900 } },
  )
  await mockReservationApis(page)

  await navigateWithinApp(page, '/my/reservations')
  await expect(page.getByRole('heading', { name: '내 예약' })).toBeVisible()
  await expect(page.getByRole('article')).toContainText('원형초보')
  await expect(page.getByText('confirmed')).toHaveCount(0)
  expect(await gridColumnCount(page)).toBe(3)

  await page.setViewportSize({ width: 960, height: 900 })
  expect(await gridColumnCount(page)).toBe(1)

  await page.setViewportSize({ width: 320, height: 800 })
  await expectNoHorizontalOverflow(page)
  await expect(page.getByRole('link', { name: '예약 변경' })).toHaveCSS('min-height', '44px')

  await page.getByRole('link', { name: '예약 변경' }).click()
  await expect(page).toHaveURL(`/my/reservations/${RESERVATION_ID}/change`)
  await expect(page.getByRole('heading', { name: '예약 변경' })).toBeVisible()
  const changeButton = page.getByRole('button', { name: '이 시간으로 변경' })
  await expect(changeButton).toBeDisabled()
  await page.locator('.reservation-change-times label').filter({ hasText: '10:00' }).click()
  await expect(page.getByRole('heading', { name: '예상 처리 결과' })).toBeVisible()
  await expect(changeButton).toBeEnabled()
  await expectNoHorizontalOverflow(page)

  await navigateWithinApp(page, `/my/reservations/${RESERVATION_ID}/cancel`)
  await expect(page.getByRole('heading', { name: '예약 취소' })).toBeVisible()
  await expect(page.getByText('회원 사유')).toBeVisible()
  const cancelButton = page.getByRole('button', { name: '예약 취소 확정' })
  await expect(cancelButton).toBeDisabled()
  await page.getByLabel('취소 사유').fill('개인 일정으로 취소합니다.')
  await expect(cancelButton).toBeEnabled()
  await expectNoHorizontalOverflow(page)

  await page.unrouteAll({ behavior: 'ignoreErrors' })
  await page.context().close()
})

async function mockReservationApis(page: Page) {
  await page.route('**/api/me/reservations**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname

    if (path === '/api/me/reservations') {
      await fulfillJson(route, {
        content: [reservation],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
      })
      return
    }
    if (path.endsWith('/change/preview')) {
      await fulfillJson(route, {
        reservationId: RESERVATION_ID,
        targetTimeSlotId: 9001,
        targetLessonDate: '2026-09-29',
        targetStartTime: '10:00:00',
        timing: 'before_cutoff',
        couponAction: 'none',
        freeChangeUsed: false,
      })
      return
    }
    if (path.endsWith('/cancellation-preview')) {
      await fulfillJson(route, {
        reservationId: RESERVATION_ID,
        timing: 'before_cutoff',
        responsibility: 'member',
        couponAction: 'return',
      })
      return
    }
    await route.fallback()
  })
  await page.route('**/api/timeslots**', async (route) => {
    const url = new URL(route.request().url())
    const date = url.searchParams.get('date') ?? '2026-09-29'
    await fulfillJson(route, {
      date,
      classType: 'ROUND_BEGINNER',
      timeSlots: [{
        timeSlotId: 9001,
        lessonDate: date,
        startTime: '10:00:00',
        closed: false,
        reservable: true,
        remainingCapacity: 2,
        unavailableReason: null,
      }],
    })
  })
}

async function fulfillJson(route: Route, body: unknown) {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
}

async function expectNoHorizontalOverflow(page: Page) {
  const dimensions = await page.evaluate(() => ({
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth,
    viewport: window.innerWidth,
  }))
  expect(dimensions.document).toBeLessThanOrEqual(dimensions.viewport)
  expect(dimensions.body).toBeLessThanOrEqual(dimensions.viewport)
}

async function gridColumnCount(page: Page) {
  return page.getByRole('article').evaluate((element) =>
    getComputedStyle(element).gridTemplateColumns.split(' ').length)
}
