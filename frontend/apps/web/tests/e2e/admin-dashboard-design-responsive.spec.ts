import { expect, test, type Page } from '@playwright/test'
import { createAuthenticatedPage, navigateWithinApp } from './e2e-support'

const ORIGIN = process.env.HORSE_E2E_WEB_BASE_URL ?? 'http://localhost:5173'
const summary = { lessonDateFrom: '2030-09-01', lessonDateTo: '2030-09-01', totalCount: 500,
  statusCounts: [{ status: 'pending_admin_approval', count: 220 }, { status: 'pending_payment', count: 120 }, { status: 'payment_expired', count: 10 }], dailyCounts: [] }
const reservation = { reservationId: 11, memberId: 11, memberName: '긴회원이름'.repeat(20), memberPhone: '010-' + '1234567890'.repeat(5),
  classType: 'ROUND_TROT', lessonDate: '2030-09-01', startTime: '09:00:00', status: 'pending_admin_approval', paymentSource: 'coupon',
  coupon: null, paymentDueAt: null, approvalRequestedAt: '2030-08-29T01:00:00Z', adminConfirmedAt: null,
  rejectedAt: null, rejectedBy: null, rejectionReason: null, cancelledAt: null, cancellationResponsibility: null, couponAction: null, adminMemo: null,
  approvalWarning: 'critical', createdAt: '2030-08-29T01:00:00Z', updatedAt: '2030-08-29T01:00:00Z', displayGroup: 'UPCOMING',
  actions: { change: { allowed: true, blockedReason: null }, cancel: { allowed: true, blockedReason: null },
    complete: { allowed: false, blockedReason: 'RESERVATION_INVALID_STATUS' }, noShow: { allowed: false, blockedReason: 'RESERVATION_INVALID_STATUS' }, approve: { allowed: true, blockedReason: null } } }
async function noOverflow(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth && document.body.scrollWidth <= innerWidth)).toBe(true)
}
for (const width of [1440, 960, 320]) {
  test(`기간_집계_예약카드와_키보드_흐름을_${width}px에서_검증한다`, async ({ browser }) => {
    const page = await createAuthenticatedPage(browser, `dashboard-design-${width}`, 'ADMIN', { viewport: { width, height: 900 } })
    const requests: Array<{ method: string; url: URL }> = []
    await page.route(`${ORIGIN}/api/admin/reservations**`, async (route) => {
      const url = new URL(route.request().url())
      requests.push({ method: route.request().method(), url })
      if (url.pathname.endsWith('/summary')) {
        const from = url.searchParams.get('lessonDateFrom') ?? url.searchParams.get('lessonDateTo') ?? '2030-09-01'
        const to = url.searchParams.get('lessonDateTo') ?? from
        await route.fulfill({ json: { ...summary, lessonDateFrom: from, lessonDateTo: to } }); return
      }
      const status = url.searchParams.get('status')
      await route.fulfill({ json: { content: [{ ...reservation, status,
        memberName: status === 'pending_admin_approval' ? reservation.memberName : '입금 회원',
        paymentSource: status === 'pending_admin_approval' ? 'coupon' : 'single_payment', approvalWarning: status === 'pending_admin_approval' ? 'critical' : null }],
        page: 0, size: 100, totalElements: 220, totalPages: 3, hasNext: true } })
    })
    await navigateWithinApp(page, '/admin/dashboard')
    await expect(page.getByRole('button', { name: '쿠폰 승인대기 220건' })).toHaveAttribute('aria-pressed', 'true')
    await expect(page.getByText('불러온 예약 1건 · 최대 100건 표시')).toBeVisible()
    await expect(page.getByText('500', { exact: true })).toBeVisible()
    const totalCard = page.locator('.admin-dashboard-total')
    const textColor = await totalCard.evaluate((element) => getComputedStyle(element).color)
    const surfaceColor = await totalCard.evaluate((element) => getComputedStyle(element).backgroundColor)
    await expect(page.getByRole('button', { name: '쿠폰 승인대기 220건' })).toHaveCSS('color', textColor)
    await expect(page.getByRole('button', { name: '입금대기 120건' })).toHaveCSS('color', textColor)
    await expect(page.getByRole('button', { name: '입금대기 120건' })).toHaveCSS('background-color', surfaceColor)
    const card = page.getByRole('article').filter({ hasText: reservation.memberName })
    await expect(card.getByText('원형 속보')).toBeVisible()
    await expect(card.getByText('긴급', { exact: true })).toBeVisible()
    await noOverflow(page)
    await page.screenshot({ path: `/tmp/horse-dashboard-${width}.png`, fullPage: true })
    if (width <= 960) await expect(page.getByRole('navigation', { name: '모바일 관리자 메뉴' })).toBeVisible()
    const targets = await page.locator('.admin-dashboard-page button, .admin-dashboard-page input, .admin-dashboard-page a').evaluateAll((elements) =>
      elements.filter((element) => element.getClientRects().length).every((element) => element.getBoundingClientRect().height >= 44))
    expect(targets).toBe(true)
    await page.getByLabel('시작일', { exact: true }).focus()
    // Native date controls have several keyboard segments before the next field.
    for (let step = 0; step < 6 && !await page.getByLabel('종료일', { exact: true }).evaluate((element) => element === document.activeElement); step++) {
      await page.keyboard.press('Tab')
    }
    await expect(page.getByLabel('종료일', { exact: true })).toBeFocused()
    for (let step = 0; step < 6 && !await page.getByLabel('시작일', { exact: true }).evaluate((element) => element === document.activeElement); step++) {
      await page.keyboard.press('Shift+Tab')
    }
    await expect(page.getByLabel('시작일', { exact: true })).toBeFocused()
    await page.getByRole('button', { name: '기간 적용' }).focus()
    await page.keyboard.press('Tab')
    await expect(page.getByRole('button', { name: '오늘' })).toBeFocused()
    await page.keyboard.press('Shift+Tab')
    await expect(page.getByRole('button', { name: '기간 적용' })).toBeFocused()
    const payment = page.getByRole('button', { name: '입금대기 120건' })
    await payment.focus(); await payment.press('Enter')
    await expect(payment).toHaveAttribute('aria-pressed', 'true')
    await expect(page.getByText('입금 회원', { exact: true })).toBeVisible()
    await page.getByLabel('시작일', { exact: true }).fill('2030-09-10')
    await page.getByLabel('종료일', { exact: true }).fill('2030-09-02')
    const countBefore = requests.filter((item) => item.url.pathname.endsWith('/summary')).length
    await page.getByRole('button', { name: '기간 적용' }).click()
    await expect(page.getByRole('alert')).toContainText('종료일은 시작일보다')
    await expect(page.getByLabel('종료일', { exact: true })).toBeFocused()
    await expect(page.getByLabel('종료일', { exact: true })).toHaveAttribute('aria-describedby', 'dashboard-period-help dashboard-period-error')
    expect(requests.filter((item) => item.url.pathname.endsWith('/summary'))).toHaveLength(countBefore)
    await page.getByLabel('종료일', { exact: true }).fill('2030-09-12')
    await page.getByRole('button', { name: '기간 적용' }).click()
    await expect(page.getByRole('heading', { name: '2030-09-10 ~ 2030-09-12' })).toBeVisible()
    await expect.poll(() => requests.filter((item) => !item.url.pathname.endsWith('/summary')).at(-1)?.url.searchParams.get('lessonDateTo')).toBe('2030-09-12')
    await noOverflow(page)
    expect(requests.every((item) => item.method === 'GET')).toBe(true)
    expect(requests.filter((item) => !item.url.pathname.endsWith('/summary')).every((item) => item.url.searchParams.get('page') === '0' && item.url.searchParams.get('size') === '100')).toBe(true)
    await page.context().close()
  })
}
test('조회오류에서도_기간과_재조회를_유지하고_같은기간_목록도_갱신한다', async ({ browser }) => {
  const page = await createAuthenticatedPage(browser, 'dashboard-errors', 'ADMIN', { viewport: { width: 320, height: 900 } })
  let failSummary = true, failList = false, memberName = '기존 조회 회원'
  let listCount = 0
  await page.route(`${ORIGIN}/api/admin/reservations**`, async (route) => {
    if (new URL(route.request().url()).pathname.endsWith('/summary')) {
      await route.fulfill(failSummary ? { status: 403, json: { code: 'COMMON_ACCESS_DENIED' } } : { json: summary }); return
    }
    listCount++
    await route.fulfill(failList ? { status: 403, json: { code: 'COMMON_ACCESS_DENIED' } } : { json: {
      content: [{ ...reservation, memberName }], page: 0, size: 100, totalElements: 220, totalPages: 3, hasNext: true } })
  })
  await navigateWithinApp(page, '/admin/dashboard')
  await expect(page.getByRole('alert')).toContainText('관리자 권한', { timeout: 15000 })
  await expect(page.getByLabel('시작일')).toBeVisible()
  await expect(page.getByRole('button', { name: '오늘' })).toBeEnabled()
  await expect(page.getByText(/불러온 예약 0건/)).toHaveCount(0)
  expect(listCount).toBe(0)
  failSummary = false
  await page.getByRole('button', { name: '다시 조회' }).click()
  await expect(page.getByText(memberName, { exact: true })).toBeVisible()
  memberName = '최신 조회 회원'
  await page.getByRole('button', { name: '기간 적용' }).click()
  await expect(page.getByText(memberName, { exact: true })).toBeVisible()
  expect(listCount).toBe(2)
  failList = true
  await page.getByRole('button', { name: '입금대기 120건' }).click()
  await expect(page.getByRole('alert')).toContainText('관리자 권한', { timeout: 15000 })
  await expect(page.getByText(/불러온 예약 0건/)).toHaveCount(0)
  await expect(page.getByRole('button', { name: '쿠폰 승인대기 220건' })).toBeVisible()
  failList = false
  await page.getByRole('button', { name: '다시 조회' }).click()
  await expect(page.getByText('불러온 예약 1건 · 최대 100건 표시')).toBeVisible()
  await noOverflow(page)
  await page.context().close()
})
