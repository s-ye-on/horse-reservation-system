import { readFile } from 'node:fs/promises'
import { expect, test } from '@playwright/test'
import { createAuthenticatedPage, navigateWithinApp } from './e2e-support'

const ORIGIN = process.env.HORSE_E2E_WEB_BASE_URL ?? 'http://localhost:5173'

for (const width of [1440, 960, 320]) {
  test(`쿠폰_사용_CSV의_적용조건_다운로드_키보드_오류를_${width}px에서_검증한다`, async ({ browser }) => {
    const page = await createAuthenticatedPage(browser, `coupon-usage-${width}`, 'ADMIN', { viewport: { width, height: 900 } })
    const requests: URL[] = []
    let mode: 'success' | 'error' | 'empty' = 'success'
    let release!: () => void
    const pending = new Promise<void>((resolve) => { release = resolve })
    const csv = '\uFEFF"member_name","memo"\r\n"김하늘","처리 내역"\r\n'
    await page.route(`${ORIGIN}/api/admin/coupon-usage-logs/export**`, async (route) => {
      expect(route.request().method()).toBe('GET')
      requests.push(new URL(route.request().url()))
      await pending
      if (mode === 'error') { await route.fulfill({ status: 403, json: { code: 'COMMON_ACCESS_DENIED' } }); return }
      await route.fulfill({ contentType: 'text/csv;charset=UTF-8', headers: { 'content-disposition': 'attachment; filename="coupon-usage.csv"' }, body: mode === 'empty' ? '\uFEFF"member_name","memo"\r\n' : csv })
    })
    await navigateWithinApp(page, '/admin/coupon-usage-logs')
    await expect(page.getByRole('heading', { name: '쿠폰 사용 이력 CSV', exact: true })).toBeVisible()
    if (width <= 960) {
      await expect(page.getByRole('combobox', { name: '관리 업무 이동' })).toHaveValue('/admin/coupon-usage-logs')
    } else {
      await expect(page.getByRole('navigation', { name: '관리자 전체 메뉴' }).getByRole('link', { name: '쿠폰 사용 이력 CSV' })).toHaveAttribute('aria-current', 'page')
    }
    await page.getByLabel('쿠폰 번호', { exact: true }).fill('-1')
    await page.getByRole('button', { name: '조건 적용', exact: true }).click()
    await expect(page.getByRole('alert')).toContainText('1 이상의 정수')
    await expect(page.getByLabel('쿠폰 번호', { exact: true })).toBeFocused()
    await expect(page.getByLabel('쿠폰 번호', { exact: true })).toHaveAttribute('aria-invalid', 'true')
    await page.getByLabel('쿠폰 번호', { exact: true }).fill('7')
    await page.getByLabel('예약 번호', { exact: true }).fill('42')
    await page.getByLabel('처리 시작일').fill('2026-07-01')
    await page.getByLabel('처리 종료일').fill('2026-07-31')
    const keyword = 'LongMemberName'.repeat(15)
    await page.getByLabel('회원 검색').fill(keyword)
    await page.getByRole('button', { name: '조건 적용', exact: true }).click()
    await page.getByLabel('회원 검색').fill('미적용 검색어')
    await noOverflow(page)
    const downloadButton = page.getByRole('button', { name: '현재 조건 쿠폰 사용 이력 CSV 다운로드' })
    await page.getByRole('button', { name: '초기화' }).focus()
    await page.keyboard.press('Tab')
    await expect(downloadButton).toBeFocused()
    await expect(downloadButton).toHaveCSS('outline-style', 'solid')
    const downloading = page.waitForEvent('download')
    await page.keyboard.press('Enter')
    await expect(page.getByRole('button', { name: 'CSV 준비 중...' })).toBeDisabled()
    await expect(page.getByRole('status')).toContainText('CSV를 준비')
    await page.keyboard.press('Enter')
    expect(requests).toHaveLength(1)
    release()
    const download = await downloading
    expect(download.suggestedFilename()).toBe('coupon-usage.csv')
    const path = await download.path()
    expect(path).not.toBeNull()
    expect(await readFile(path!)).toEqual(Buffer.from(csv, 'utf8'))
    expect(Object.fromEntries(requests[0].searchParams)).toEqual({ keyword, couponId: '7', reservationId: '42', occurredDateFrom: '2026-07-01', occurredDateTo: '2026-07-31' })
    await expect(page.getByRole('status')).toContainText('CSV 다운로드를 시작했습니다')
    await noOverflow(page)
    const targets = await page.locator('.coupon-usage-page button, .coupon-usage-page input, .coupon-usage-page a').evaluateAll((elements) => elements.every((element) => element.getBoundingClientRect().height >= 44))
    expect(targets).toBe(true)
    await page.evaluate(() => { (document.activeElement as HTMLElement)?.blur(); window.scrollTo(0, 0) })
    await page.screenshot({ path: `/tmp/horse-coupon-usage-${width}.png`, fullPage: true })
    mode = 'error'
    await downloadButton.click()
    await expect(page.getByRole('alert')).toContainText('관리자 권한이 없어')
    await expect(page.getByRole('alert')).not.toContainText('COMMON_ACCESS_DENIED')
    await expect(downloadButton).toBeEnabled()
    await noOverflow(page)
    mode = 'empty'
    await page.getByRole('button', { name: '초기화' }).click()
    const emptyDownload = page.waitForEvent('download')
    await downloadButton.click()
    const empty = await emptyDownload
    expect(requests.at(-1)!.search).toBe('')
    expect(await readFile((await empty.path())!)).toEqual(Buffer.from('\uFEFF"member_name","memo"\r\n', 'utf8'))
    await page.context().close()
  })
}

async function noOverflow(page: import('@playwright/test').Page) {
  const dimensions = await page.evaluate(() => ({ viewport: innerWidth, document: document.documentElement.scrollWidth, body: document.body.scrollWidth }))
  expect(dimensions.document).toBeLessThanOrEqual(dimensions.viewport)
  expect(dimensions.body).toBeLessThanOrEqual(dimensions.viewport)
}
