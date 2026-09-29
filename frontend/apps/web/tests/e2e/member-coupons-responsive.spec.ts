import { expect, test, type Page, type Route } from '@playwright/test'
import { createAuthenticatedPage, navigateWithinApp } from './e2e-support'

test('회원은_쿠폰_현황과_변동_내역을_320px에서도_확인한다', async ({ browser }) => {
  const page = await createAuthenticatedPage(
    browser,
    'e2e-member-coupons',
    'MEMBER',
    { viewport: { width: 1280, height: 900 } },
  )
  await mockCouponApis(page)

  await navigateWithinApp(page, '/my/coupons')

  await expect(page.getByRole('heading', { name: '보유 쿠폰 및 이용 현황' })).toBeVisible()
  const couponCard = page.getByRole('article')
  await expect(couponCard.getByRole('heading', { name: '일반 기승 쿠폰' })).toBeVisible()
  await expect(couponCard.getByText('총 12회 중 잔여 7회')).toBeVisible()
  await expect(couponCard.getByText('예약 처리 중').locator('..')).toContainText('2회')
  await expect(couponCard.getByText('무료 변경').locator('..')).toContainText('사용 가능')
  await expect(page.getByRole('table', { name: '쿠폰 변동 내역' })).toBeVisible()
  await expect(page.getByText('수업 완료로 사용')).toBeVisible()
  await expect(page.getByText('GENERAL')).toHaveCount(0)
  expect(await couponGridColumnCount(page)).toBe(2)

  await page.setViewportSize({ width: 960, height: 900 })
  await expectNoHorizontalOverflow(page)
  await expect(page.getByRole('navigation', { name: '보유 쿠폰 페이지' })).toBeVisible()

  await page.setViewportSize({ width: 320, height: 800 })
  await expectNoHorizontalOverflow(page)
  expect(await couponGridColumnCount(page)).toBe(1)
  await expect(page.locator('.my-coupons-header').getByRole('link', { name: '수업 예약' })).toHaveCSS('min-height', '44px')
  await expect(page.getByRole('button', { name: '다음' }).first()).toHaveCSS('min-height', '44px')
  await expect(page.getByRole('cell', { name: /쿠폰 번호 41/ }).first()).toHaveCSS('display', 'grid')

  await page.unrouteAll({ behavior: 'ignoreErrors' })
  await page.context().close()
})

test('회원_쿠폰_화면은_쿠폰과_변동_내역의_빈_상태를_구분한다', async ({ browser }) => {
  const page = await createAuthenticatedPage(browser, 'e2e-member-coupons-empty', 'MEMBER')
  await page.route('**/api/me/coupons**', (route) => fulfillJson(route, emptyPage()))
  await page.route('**/api/me/coupon-usage-logs**', (route) => fulfillJson(route, emptyPage()))

  await navigateWithinApp(page, '/my/coupons')

  await expect(page.getByText('보유한 쿠폰이 없습니다.')).toBeVisible()
  await expect(page.getByText('아직 쿠폰 변동 내역이 없습니다.')).toBeVisible()

  await page.unrouteAll({ behavior: 'ignoreErrors' })
  await page.context().close()
})

async function mockCouponApis(page: Page) {
  await page.route('**/api/me/coupons**', (route) => fulfillJson(route, {
    content: [{
      couponId: 41,
      type: 'general',
      totalCount: 12,
      remainingCount: 7,
      heldCount: 2,
      availableCount: 5,
      firstUsedAt: '2026-09-01T00:00:00+09:00',
      expiresAt: '2026-12-01T00:00:00+09:00',
      freeChangeUsed: false,
      status: 'active',
    }],
    page: 0,
    size: 20,
    totalElements: 21,
    totalPages: 2,
    hasNext: true,
  }))
  await page.route('**/api/me/coupon-usage-logs**', (route) => fulfillJson(route, {
    content: [{
      usageLogId: 91,
      couponId: 41,
      reservationId: 10482,
      memberId: 7,
      couponOwnerMemberId: 7,
      familyGroupId: null,
      action: 'used',
      countDelta: -1,
      occurredAt: '2026-09-24T19:40:00+09:00',
      actorType: 'system',
    }],
    page: 0,
    size: 20,
    totalElements: 21,
    totalPages: 2,
    hasNext: true,
  }))
}

function emptyPage() {
  return { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false }
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

async function couponGridColumnCount(page: Page) {
  return page.locator('.my-coupon-list').evaluate((element) =>
    getComputedStyle(element).gridTemplateColumns.split(' ').length)
}
