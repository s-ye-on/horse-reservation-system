import { expect, test } from '@playwright/test'
import { createAuthenticatedPage, navigateWithinApp } from './e2e-support'

const WEB_ORIGIN = process.env.HORSE_E2E_WEB_BASE_URL ?? 'http://localhost:5173'
const JSON_HEADERS = { 'Content-Type': 'application/json' }

test('기존_사용_중_쿠폰은_320px에서_현재_상태와_최초_사용일을_등록한다', async ({ browser }) => {
  let registrationBody: Record<string, unknown> | undefined
  const page = await createAuthenticatedPage(
    browser,
    'e2e-coupon-adoption-admin',
    'ADMIN',
    { viewport: { width: 320, height: 900 } },
  )

  await page.route(`${WEB_ORIGIN}/api/admin/members**`, async (route) => {
    const request = route.request()
    if (request.method() === 'POST') {
      registrationBody = request.postDataJSON() as Record<string, unknown>
      await route.fulfill({
        status: 201,
        headers: JSON_HEADERS,
        body: JSON.stringify({
          id: 91,
          memberId: 3,
          type: 'general',
          totalCount: 10,
          remainingCount: 7,
          heldCount: 0,
          firstUsedAt: '2026-07-03T00:00:00+09:00',
          expiresAt: '2026-10-03T00:00:00+09:00',
          freeChangeUsed: false,
          status: 'active',
          createdBy: 'e2e-coupon-adoption-admin',
          createdAt: '2026-09-04T12:00:00+09:00',
        }),
      })
      return
    }

    await route.fulfill({
      status: 200,
      headers: JSON_HEADERS,
      body: JSON.stringify({
        content: [member()],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
      }),
    })
  })

  await navigateWithinApp(page, '/admin/coupons/new')
  await page.getByLabel('등록 대상').selectOption('3')
  await page.getByRole('radio', { name: /일반/ }).click()
  await page.getByRole('radio', { name: /기존 쿠폰 등록/ }).click()
  await page.getByLabel('이미 사용한 횟수').fill('3')
  await page.getByLabel('실제 최초 사용일').fill('2026-07-03')

  await expect(page.getByText(/등록 후 남은 횟수/)).toContainText('7회')
  await page.getByRole('button', { name: '쿠폰 등록' }).click()

  const result = page.locator('.admin-coupon-success')
  await expect(result.getByRole('heading', { name: '쿠폰이 등록되었습니다' })).toBeVisible()
  await expect(result.getByText('7회', { exact: true })).toBeVisible()
  expect(registrationBody).toEqual({
    type: 'general',
    totalCount: 10,
    usedCount: 3,
    firstUsedDate: '2026-07-03',
  })
  await expectNoHorizontalOverflow(page)

  await page.context().close()
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

function member() {
  return {
    id: 3,
    name: '이기승',
    phone: '010-2222-3333',
    generalRideCount: 8,
    progressionValue: 26,
    progressionClass: 'LARGE_ARENA_TROT',
    effectiveClass: 'LARGE_ARENA_TROT',
    progressionManagementStartedAt: '2026-08-01T00:00:00+09:00',
    progressionBaselineClass: null,
    progressionBaselineThreshold: null,
    progressionBaselineActualRideCount: null,
    specialApprovalProgressionCredit: 18,
    promotionHoldClass: null,
    dressageRideCount: 0,
    jumpingRideCount: 0,
    dressageApproved: true,
    jumpingApproved: false,
    canUseLargeArena: true,
  }
}
