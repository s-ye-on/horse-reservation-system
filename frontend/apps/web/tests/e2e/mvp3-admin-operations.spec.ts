import { expect, test } from '@playwright/test'
import {
  cleanupMvpThreeFixture,
  createAuthenticatedPage,
  prepareMvpThreeFixture,
  readMvpThreeSnapshot,
  type MvpThreeFixture,
} from './e2e-support'

test.describe.configure({ mode: 'serial' })

test.describe('MVP 3 관리자 운영 핵심 흐름', () => {
  let fixture: MvpThreeFixture

  test.beforeAll(() => {
    fixture = prepareMvpThreeFixture()
  })

  test.afterAll(() => {
    if (fixture) cleanupMvpThreeFixture(fixture.lessonDate)
  })

  test('운영_대시보드는_기간별_승인대기_집계와_회원을_표시한다', async ({ browser }) => {
    const page = await createAuthenticatedPage(browser, 'e2e-m3-11-admin', 'ADMIN')
    await page.goto('/admin/dashboard')

    await page.getByLabel('시작일').fill(fixture.lessonDate)
    await page.getByLabel('종료일').fill(fixture.lessonDate)
    await page.getByRole('button', { name: '기간 적용' }).click()

    await expect(page.getByRole('button', { name: '쿠폰 승인대기 1건' })).toBeVisible()
    await expect(page.locator('article').filter({ hasText: fixture.pendingMemberName })).toBeVisible()
    await page.context().close()
  })

  test('시간대_일괄_완료는_예약과_쿠폰과_기승_횟수를_한_번만_반영한다', async ({ browser }) => {
    const page = await createAuthenticatedPage(browser, 'e2e-m3-11-admin', 'ADMIN')
    await page.goto('/admin/attendance')

    await page.getByLabel('수업 날짜').selectOption(fixture.lessonDate)
    await page.getByLabel('시작 시간').selectOption('17:00:00')
    const responsePromise = page.waitForResponse((response) =>
      response.url().endsWith('/api/admin/reservations/complete-bulk')
      && response.request().method() === 'POST')
    await page.getByRole('button', { name: '선택 예약 일괄 처리' }).click()
    const response = await responsePromise

    expect(await response.json()).toMatchObject({ requestedCount: 1, succeededCount: 1, failedCount: 0 })
    await expect(page.getByText('일괄 처리 결과')).toBeVisible()
    await expect(page.getByText('성공 1건 · 실패 0건')).toBeVisible()
    expect(readMvpThreeSnapshot(fixture.completedReservationId)).toEqual({
      status: 'completed',
      remainingCount: 9,
      heldCount: 0,
      generalRideCount: 7,
      usedLogCount: 1,
    })
    await page.context().close()
  })

  test('감사_화면과_CSV는_같은_예약_이력을_관리자에게_제공한다', async ({ browser }) => {
    const page = await createAuthenticatedPage(browser, 'e2e-m3-11-admin', 'ADMIN')
    await page.goto('/admin/audit-logs')

    await page.getByLabel('회원 검색').fill(fixture.completedMemberName)
    await page.getByRole('button', { name: '조건 적용' }).click()
    const auditRow = page.locator('article').filter({ hasText: fixture.completedMemberName })
    await expect(auditRow).toContainText(`예약 #${fixture.completedReservationId}`)
    await expect(auditRow).toContainText('일정 변경')
    await expect(auditRow).toContainText('M3-11 감사 fixture')

    const exportResponsePromise = page.waitForResponse((response) =>
      response.url().includes('/api/admin/audit-logs/export?'))
    const exportResult = await page.evaluate(async (keyword) => {
      const response = await fetch(
        `http://localhost:8080/api/admin/audit-logs/export?keyword=${encodeURIComponent(keyword)}`,
      )
      const bytes = new Uint8Array(await response.arrayBuffer())
      return {
        status: response.status,
        contentType: response.headers.get('content-type'),
        bom: Array.from(bytes.slice(0, 3)),
        body: new TextDecoder().decode(bytes),
      }
    }, fixture.completedMemberName)
    const exportResponse = await exportResponsePromise

    expect(exportResult.status).toBe(200)
    expect(exportResult.contentType).toContain('text/csv')
    expect(exportResponse.headers()['content-disposition']).toContain('attachment')
    expect(exportResult.bom).toEqual([0xef, 0xbb, 0xbf])
    expect(exportResult.body).toContain(fixture.completedMemberName)
    expect(exportResult.body).toContain(String(fixture.completedReservationId))
    await page.context().close()
  })
})
