import { expect, test } from '@playwright/test'
import { createAuthenticatedPage, navigateWithinApp } from './e2e-support'

const ORIGIN = process.env.HORSE_E2E_WEB_BASE_URL ?? 'http://localhost:5173'

for (const width of [1440, 960, 320]) {
  test(`감사_조회와_CSV_조건_키보드_긴_텍스트를_${width}px에서_검증한다`, async ({ browser }) => {
    const page = await createAuthenticatedPage(browser, `audit-design-${width}`, 'ADMIN', { viewport: { width, height: 900 } })
    const name = 'LongMemberName'.repeat(12)
    const memo = 'LongReason'.repeat(50)
    let mode: 'normal' | 'empty' | 'error' = 'normal'
    let release!: () => void
    const loading = new Promise<void>((resolve) => { release = resolve })
    let releasePage!: () => void
    const pageLoading = new Promise<void>((resolve) => { releasePage = resolve })
    const requests: URL[] = []
    const methods: string[] = []
    await page.route(`${ORIGIN}/api/admin/audit-logs**`, async (route) => {
      const url = new URL(route.request().url())
      methods.push(route.request().method())
      requests.push(url)
      if (url.pathname.endsWith('/export')) {
        await route.fulfill({ contentType: 'text/csv;charset=UTF-8', headers: { 'content-disposition': 'attachment; filename="reservation-audit.csv"' }, body: '\uFEFF"memo"\r\n"CSV result"\r\n' })
        return
      }
      await loading
      if (mode === 'error') { await route.fulfill({ status: 403, json: { code: 'COMMON_ACCESS_DENIED' } }); return }
      const content = mode === 'empty' ? [] : [{ auditLogId: 81, reservationId: 42, memberId: 7, memberName: name,
        actorAuthSubject: 'private-authentication-subject', actorType: 'admin', changeType: 'admin_reservation_created',
        fromStatus: 'confirmed', toStatus: 'confirmed', fromLessonDate: '2026-07-20', toLessonDate: '2026-07-20',
        fromStartTime: '09:00:00', toStartTime: '09:00:00', couponAction: 'none', memo, occurredAt: '2026-07-16T10:30:00+09:00' }]
      const currentPage = Number(url.searchParams.get('page') ?? 0)
      if (currentPage === 1) await pageLoading
      await route.fulfill({ json: { content, page: currentPage, size: 20, totalElements: mode === 'empty' ? 0 : 21, totalPages: mode === 'empty' ? 0 : 2, hasNext: currentPage === 0 && mode !== 'empty' } })
    })
    await navigateWithinApp(page, '/admin/audit-logs')
    await expect(page.getByRole('status')).toContainText('불러오는 중')
    await expect(page.getByText(/전체 0건/)).toHaveCount(0)
    await noOverflow(page)
    release()
    await expect(page.getByText(name, { exact: true })).toBeVisible()
    await expect(page.locator('.admin-audit-memo')).toBeVisible()
    await expect(page.locator('.admin-audit-memo')).toContainText(memo)
    await expect(page.getByText('전체 21건 · 현재 페이지 1건')).toBeVisible()
    await expect(page.locator('.admin-audit-announcement')).toContainText('감사 이력 조회 완료')
    await expect(page.getByText('private-authentication-subject')).toHaveCount(0)
    await noOverflow(page)
    const targets = await page.locator('.admin-audit-page button, .admin-audit-page input, .admin-audit-page select, .admin-audit-page a, .admin-audit-page summary')
      .evaluateAll((elements) => elements.every((element) => element.getBoundingClientRect().height >= 44))
    expect(targets).toBe(true)
    if (width <= 960) await expect(page.getByRole('navigation', { name: '모바일 관리자 메뉴' })).toBeVisible()
    const details = page.getByText('처리 상세 · 예약 #42', { exact: true })
    await details.focus(); await page.keyboard.press('Enter')
    await expect(page.locator('.admin-audit-details')).toHaveAttribute('open', '')
    await expect(details).toHaveCSS('outline-style', 'solid')
    await page.getByRole('button', { name: '다음', exact: true }).focus()
    await page.keyboard.press('Enter')
    await expect(page.getByText('페이지 조회 중', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '다음', exact: true })).toBeFocused()
    await expect(page.getByRole('button', { name: '다음', exact: true })).toHaveAttribute('aria-disabled', 'true')
    await page.keyboard.press('Enter')
    await page.keyboard.press('Space')
    await page.getByRole('button', { name: '다음', exact: true }).click({ force: true })
    expect(requests.filter((url) => url.searchParams.get('page') === '1')).toHaveLength(1)
    expect(requests.some((url) => url.searchParams.get('page') === '2')).toBe(false)
    releasePage()
    await expect(page.getByText('2 / 2 페이지')).toBeVisible()
    await expect(page.getByRole('button', { name: '다음', exact: true })).toBeFocused()
    await page.keyboard.press('Enter')
    expect(requests.filter((url) => url.searchParams.get('page') === '1')).toHaveLength(1)
    expect(requests.some((url) => url.searchParams.get('page') === '2')).toBe(false)
    await page.getByLabel('회원 검색').fill('applied-member')
    await page.getByRole('button', { name: '조건 적용' }).click()
    await expect(page.getByText('1 / 2 페이지')).toBeVisible()
    await page.getByLabel('회원 검색').fill('draft-member')
    await page.getByLabel('예약 번호').focus()
    await page.keyboard.type('e')
    expect(await page.locator('#audit-reservation-id').evaluate((element: HTMLInputElement) => element.validity.badInput)).toBe(true)
    await page.getByRole('button', { name: '조건 적용' }).click()
    await expect(page.getByRole('alert')).toContainText('예약 번호는 1 이상의 정수')
    await expect(page.locator('#audit-reservation-id')).toBeFocused()
    await page.locator('#audit-reservation-id').fill('')
    const downloadPromise = page.waitForEvent('download')
    await page.getByRole('button', { name: '현재 조건 CSV 다운로드' }).click()
    const download = await downloadPromise
    expect(download.suggestedFilename()).toBe('reservation-audit.csv')
    const exportUrl = requests.findLast((url) => url.pathname.endsWith('/export'))!
    expect(exportUrl.searchParams.get('keyword')).toBe('applied-member')
    expect(exportUrl.searchParams.has('page')).toBe(false)
    expect(exportUrl.searchParams.has('size')).toBe(false)
    await expect(page.getByRole('status')).toContainText('CSV 다운로드를 시작했습니다')
    await page.screenshot({ path: `/tmp/horse-audit-${width}.png`, fullPage: true })
    await page.getByLabel('시작일').fill('2026-07-31')
    await page.getByLabel('종료일').fill('2026-07-01')
    await page.getByRole('button', { name: '조건 적용' }).click()
    await expect(page.getByRole('alert')).toContainText('시작일은 종료일보다')
    await expect(page.locator('#audit-date-from')).toBeFocused()
    await expect(page.locator('#audit-date-from')).toHaveAttribute('aria-invalid', 'true')
    await noOverflow(page)
    mode = 'empty'
    await page.getByRole('button', { name: '초기화' }).click()
    await expect(page.getByText('조회 조건에 해당하는 예약 감사 이력이 없습니다.')).toBeVisible()
    mode = 'error'
    await page.getByRole('button', { name: '조건 적용' }).click()
    await expect(page.getByRole('alert')).toContainText('관리자 권한이 없어', { timeout: 15000 })
    await expect(page.getByText(/전체 0건/)).toHaveCount(0)
    await expect(page.getByLabel('회원 검색')).toBeEnabled()
    await noOverflow(page)
    mode = 'normal'
    await page.getByRole('button', { name: '다시 시도', exact: true }).click()
    await expect(page.getByText(name, { exact: true })).toBeVisible()
    expect(methods.every((method) => method === 'GET')).toBe(true)
    await page.context().close()
  })
}

async function noOverflow(page: import('@playwright/test').Page) {
  const dimensions = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth, body: document.body.scrollWidth }))
  expect(dimensions.document).toBeLessThanOrEqual(dimensions.viewport)
  expect(dimensions.body).toBeLessThanOrEqual(dimensions.viewport)
}
