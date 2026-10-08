import { expect, test } from '@playwright/test'
import { createAuthenticatedPage, navigateWithinApp } from './e2e-support'

const WEB_ORIGIN = process.env.HORSE_E2E_WEB_BASE_URL ?? 'http://localhost:5173'

for (const width of [1440, 960, 320]) {
  test(`공통_디자인과_집계_오류_키보드_흐름을_${width}px에서_검증한다`, async ({ browser }) => {
    const page = await createAuthenticatedPage(browser, `monthly-design-${width}`, 'ADMIN', { viewport: { width, height: 900 } })
    const longName = 'LongMemberName'.repeat(10)
    const leaders = Array.from({ length: 12 }, (_, index) => ({
      memberId: index + 1000, memberName: index < 2 ? '동명이인' : `${longName}${index}`, completedRideCount: 123456789012,
    }))
    let mode: 'normal' | 'empty' | 'error' = 'normal'
    let release!: () => void
    const loading = new Promise<void>((resolve) => { release = resolve })
    const methods: string[] = []
    await page.route(`${WEB_ORIGIN}/api/admin/reservations/monthly-ride-statistics**`, async (route) => {
      methods.push(route.request().method())
      await loading
      const url = new URL(route.request().url())
      if (mode === 'error') { await route.fulfill({ status: 403, json: { code: 'COMMON_ACCESS_DENIED' } }); return }
      await route.fulfill({ json: { month: url.searchParams.get('month'), rideType: url.searchParams.get('rideType'),
        totalCompletedRideCount: mode === 'empty' ? 0 : 9999999999999,
        topCompletedRideCount: mode === 'empty' ? 0 : 123456789012, leaders: mode === 'empty' ? [] : leaders } })
    })
    await navigateWithinApp(page, '/admin/monthly-ride-statistics')
    await expect(page.getByRole('status')).toContainText('불러오는 중')
    await expect(page.getByText('총 기승 횟수')).toHaveCount(0)
    await expectNoHorizontalOverflow(page)
    release()
    await expect(page.getByRole('heading', { name: '공동 최다 기승 회원' })).toBeVisible()
    await expect(page.getByText('동명이인', { exact: true })).toHaveCount(2)
    await expect(page.locator('.admin-monthly-statistics-leaders li')).toHaveCount(12)
    await expect(page.getByText('9999999999999', { exact: true })).toBeVisible()
    await expect(page.getByText('최다 회원 12명', { exact: true })).toBeVisible()
    await expect(page.getByRole('status')).toContainText('총 기승 횟수 9999999999999회')
    await expectNoHorizontalOverflow(page)
    const targets = await page.locator('.admin-monthly-statistics-page button, .admin-monthly-statistics-page input, .admin-monthly-statistics-page a, .admin-monthly-statistics-page summary')
      .evaluateAll((elements) => elements.every((element) => element.getBoundingClientRect().height >= 44))
    expect(targets).toBe(true)
    if (width <= 960) await expect(page.getByRole('navigation', { name: '모바일 관리자 메뉴' })).toBeVisible()
    await page.screenshot({ path: `/tmp/horse-monthly-${width}.png`, fullPage: true })
    const general = page.getByRole('button', { name: '일반 기승', exact: true })
    await general.focus(); await page.keyboard.press('Enter')
    await expect(general).toHaveAttribute('aria-pressed', 'true')
    await expect(general).toHaveCSS('outline-style', 'solid')
    await page.keyboard.press('Tab')
    await expect(page.getByRole('button', { name: '마장마술', exact: true })).toBeFocused()
    await page.keyboard.press('Shift+Tab')
    await expect(general).toBeFocused()
    await page.getByText('집계 기준', { exact: true }).focus()
    await page.keyboard.press('Enter')
    await expect(page.locator('.admin-monthly-statistics-policy')).toHaveAttribute('open', '')
    await expect(page.getByText(/기승 횟수 보정과 Horse 운영 이전 경력/)).toBeVisible()
    await page.getByLabel('조회 월').fill('2025-12')
    await expect(page.getByRole('heading', { name: '2025년 12월 일반 기승' })).toBeVisible()
    await page.getByRole('button', { name: '다음 달', exact: true }).click()
    await expect(page.getByRole('heading', { name: '2026년 1월 일반 기승' })).toBeVisible()
    mode = 'empty'
    await page.getByRole('button', { name: '장애물', exact: true }).click()
    await expect(page.getByRole('status')).toContainText('완료된 수업이 없습니다')
    await expect(page.locator('.admin-monthly-statistics-metrics article')).toHaveCount(2)
    await expect(page.locator('.admin-monthly-statistics-leaders')).toHaveCount(0)
    await expectNoHorizontalOverflow(page)
    mode = 'error'
    await page.getByRole('button', { name: '마장마술', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('조회할 권한이 없습니다', { timeout: 15000 })
    await expect(page.getByText('총 기승 횟수')).toHaveCount(0)
    await expect(page.getByLabel('조회 월')).toBeEnabled()
    await expect(page.getByRole('button', { name: '이전 달', exact: true })).toBeEnabled()
    await expectNoHorizontalOverflow(page)
    await page.screenshot({ path: `/tmp/horse-monthly-error-${width}.png`, fullPage: true })
    mode = 'normal'
    await page.getByRole('button', { name: '다시 시도', exact: true }).click()
    await expect(page.getByRole('heading', { name: '공동 최다 기승 회원' })).toBeVisible()
    await expectNoHorizontalOverflow(page)
    expect(methods.every((method) => method === 'GET')).toBe(true)
    await page.context().close()
  })
}

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
  await expect(page.getByText('10', { exact: true })).toBeVisible()
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
