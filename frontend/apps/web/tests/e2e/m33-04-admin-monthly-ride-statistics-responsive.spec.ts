import { expect, test } from '@playwright/test'
import { createAuthenticatedPage, navigateWithinApp } from './e2e-support'

const WEB_ORIGIN = process.env.HORSE_E2E_WEB_BASE_URL ?? 'http://localhost:5173'

test('월간_기승_현황은_320px에서_월과_종류를_바꾸고_공동_1위를_확인할_수_있다', async ({ browser }) => {
  const page = await createAuthenticatedPage(
    browser,
    'e2e-m33-04-monthly-statistics-admin',
    'ADMIN',
    { viewport: { width: 320, height: 900 } },
  )

  await page.route(`${WEB_ORIGIN}/api/admin/reservations/monthly-ride-statistics**`, async (route) => {
    const url = new URL(route.request().url())
    const month = url.searchParams.get('month') ?? '2026-09'
    const rideType = url.searchParams.get('rideType') ?? 'ALL'
    const isDressage = rideType === 'DRESSAGE'
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        month,
        rideType,
        totalCompletedRideCount: isDressage ? 10 : 18,
        topCompletedRideCount: isDressage ? 4 : 7,
        leaders: isDressage
          ? [
              { memberId: 11, memberName: '김마장', completedRideCount: 4 },
              { memberId: 12, memberName: '이마장', completedRideCount: 4 },
            ]
          : [{ memberId: 13, memberName: '박전체', completedRideCount: 7 }],
      }),
    })
  })

  await navigateWithinApp(page, '/admin/monthly-ride-statistics')
  await expect(page.getByRole('heading', { name: '월간 기승 현황' })).toBeVisible()
  await expect(page.getByText('박전체')).toBeVisible()
  await expectNoHorizontalOverflow(page)

  await page.getByLabel('조회 월').fill('2026-08')
  await page.getByRole('button', { name: '마장마술' }).click()
  await expect(page.getByRole('heading', { name: '공동 최다 기승 회원' })).toBeVisible()
  await expect(page.getByText('김마장')).toBeVisible()
  await expect(page.getByText('이마장')).toBeVisible()
  await expect(page.getByText('10')).toBeVisible()
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
