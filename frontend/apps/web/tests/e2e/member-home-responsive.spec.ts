import { expect, test } from '@playwright/test'
import { createAuthenticatedPage, navigateWithinApp } from './e2e-support'

test('회원_홈은_실제_요약_정보와_빠른_메뉴를_320px에서도_제공한다', async ({ browser }) => {
  const page = await createAuthenticatedPage(
    browser,
    'e2e-member-home',
    'MEMBER',
    { viewport: { width: 1280, height: 900 } },
  )

  await page.route('**/api/me/reservations**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [{
          reservationId: 10482,
          classType: 'LARGE_ARENA_TROT',
          lessonDate: '2026-10-03',
          startTime: '10:00:00',
          status: 'confirmed',
          paymentSource: 'coupon',
          coupon: null,
          paymentDueAt: null,
          rejectionReason: null,
          couponAction: null,
          approvalRequestedAt: '2026-09-20T10:00:00+09:00',
          adminConfirmedAt: '2026-09-20T11:00:00+09:00',
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
        }],
        page: 0,
        size: 1,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
      }),
    })
  })
  await page.route('**/api/me/coupons**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [],
        page: 0,
        size: 20,
        totalElements: 7,
        totalPages: 1,
        hasNext: false,
      }),
    })
  })

  await navigateWithinApp(page, '/')

  await expect(page.getByRole('heading', { name: '회원 홈' })).toBeVisible()
  await expect(page.getByRole('heading', { name: /2026년 10월 3일/ })).toBeVisible()
  await expect(page.getByText('대마장 속보')).toBeVisible()
  await expect(page.getByRole('complementary', { name: '내 쿠폰' })).toContainText('7장')
  const quickMenu = page.getByRole('region', { name: '빠른 메뉴' })
  await expect(quickMenu.getByRole('link', { name: /수업 예약/ })).toHaveAttribute('href', '/reservations')
  await expect(quickMenu.getByRole('link', { name: /내 예약/ })).toHaveAttribute('href', '/my/reservations')
  await expect(quickMenu.getByRole('link', { name: /내 쿠폰/ })).toHaveAttribute('href', '/my/coupons')

  await page.setViewportSize({ width: 320, height: 800 })
  const dimensions = await page.evaluate(() => ({
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth,
    viewport: window.innerWidth,
  }))
  expect(dimensions.document).toBeLessThanOrEqual(dimensions.viewport)
  expect(dimensions.body).toBeLessThanOrEqual(dimensions.viewport)
  await expect(page.getByRole('link', { name: '내 예약 확인' })).toHaveCSS('min-height', '44px')
  await expect(quickMenu.getByRole('link', { name: /내 쿠폰/ })).toBeVisible()

  await page.unrouteAll({ behavior: 'ignoreErrors' })
  await page.context().close()
})

test('회원_홈은_예정_예약이_없을_때_빈_상태를_제공한다', async ({ browser }) => {
  const page = await createAuthenticatedPage(
    browser,
    'e2e-member-home-empty',
    'MEMBER',
    { viewport: { width: 1280, height: 900 } },
  )

  await page.route('**/api/me/reservations**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [], page: 0, size: 1, totalElements: 0, totalPages: 0, hasNext: false,
      }),
    })
  })
  await page.route('**/api/me/coupons**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false,
      }),
    })
  })

  await navigateWithinApp(page, '/')

  await expect(page.getByRole('heading', { name: '예정된 수업이 없습니다' })).toBeVisible()
  await expect(page.getByRole('link', { name: '수업 예약하기' })).toHaveAttribute('href', '/reservations')
  await page.unrouteAll({ behavior: 'ignoreErrors' })
  await page.context().close()
})
