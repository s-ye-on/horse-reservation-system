import { expect, test } from '@playwright/test'
import { createAuthenticatedPage, navigateWithinApp } from './e2e-support'

const WEB_ORIGIN = process.env.HORSE_E2E_WEB_BASE_URL ?? 'http://localhost:5173'
const JSON_HEADERS = { 'Content-Type': 'application/json' }

test('회원_class_progression_관리는_320px에서_preview와_감사를_확인할_수_있다', async ({ browser }) => {
  let auditRequestCount = 0
  const page = await createAuthenticatedPage(
    browser,
    'e2e-m32-08-member-admin',
    'ADMIN',
    { viewport: { width: 320, height: 900 } },
  )

  await page.route(`${WEB_ORIGIN}/api/admin/members**`, async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname

    if (path.endsWith('/class-progression/preview')) {
      await route.fulfill({
        status: 200,
        headers: JSON_HEADERS,
        body: JSON.stringify({
          stateToken: '"e2e-state-v1"',
          current: progressionProjection(26, 'LARGE_ARENA_TROT'),
          expected: {
            ...progressionProjection(70, 'CANTER_BEGINNER'),
            baselineClass: 'CANTER_BEGINNER',
            baselineThreshold: 70,
            baselineActualRideCount: 21,
          },
        }),
      })
      return
    }

    if (path.endsWith('/class-progression/audit-logs')) {
      auditRequestCount++
      await route.fulfill({
        status: 200,
        headers: JSON_HEADERS,
        body: JSON.stringify({
          content: [{
            auditId: 31,
            action: 'RIDE_COUNT_ADJUSTED',
            fromState: { actualCompletedRideCount: 20, progressionClass: 'LARGE_ARENA_TROT', effectiveClass: 'LARGE_ARENA_TROT' },
            toState: { actualCompletedRideCount: 21, progressionClass: 'LARGE_ARENA_TROT', effectiveClass: 'LARGE_ARENA_TROT' },
            actorAuthSubject: 'e2e-admin',
            reason: '누락 집계 정정',
            occurredAt: '2026-08-20T10:00:00',
          }],
          page: 0,
          size: 5,
          totalElements: 1,
          totalPages: 1,
          hasNext: false,
        }),
      })
      return
    }

    if (request.method() !== 'GET') {
      expect(request.headers()['if-match']).toBe('"e2e-state-v1"')
      await route.fulfill({ status: 200, headers: JSON_HEADERS, body: JSON.stringify(member()) })
      return
    }

    const body = path === '/api/admin/members'
      ? { content: [member()], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false }
      : member()
    await route.fulfill({ status: 200, headers: JSON_HEADERS, body: JSON.stringify(body) })
  })

  await navigateWithinApp(page, '/admin/members')
  await expect(page.getByRole('heading', { name: '회원 및 기승 승인' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '일반 클래스 progression' })).toBeVisible()
  await expect(page.getByText('누락 집계 정정')).toBeVisible()
  await expectNoHorizontalOverflow(page)

  await page.getByLabel('시작 클래스', { exact: true }).selectOption('CANTER_BEGINNER')
  await page.getByRole('button', { name: '예상 결과 확인' }).click()
  await expect(page.getByRole('region', { name: '변경 전후 예상 클래스' })).toContainText('구보초보')
  await page.getByLabel('관리자 사유').fill('기존 경력 확인')
  await expect(page.getByRole('button', { name: '확인 후 적용' })).toBeEnabled()
  await page.getByRole('button', { name: '확인 후 적용' }).click()
  await expect(page.getByText('시작 클래스 설정·변경 처리가 완료됐습니다.')).toBeVisible()
  await expect.poll(() => auditRequestCount).toBeGreaterThan(1)
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
    id: 7,
    name: '김승마',
    phone: '010-1234-5678',
    generalRideCount: 21,
    dressageRideCount: 0,
    jumpingRideCount: 0,
    dressageApproved: false,
    jumpingApproved: false,
    canUseLargeArena: true,
    progressionValue: 26,
    progressionClass: 'LARGE_ARENA_TROT',
    effectiveClass: 'LARGE_ARENA_TROT',
    progressionManagementStartedAt: '2026-08-01T09:00:00',
    progressionBaselineClass: null,
    progressionBaselineThreshold: null,
    progressionBaselineActualRideCount: null,
    specialApprovalProgressionCredit: 5,
    promotionHoldClass: null,
  }
}

function progressionProjection(progressionValue: number, progressionClass: string) {
  return {
    actualCompletedRideCount: 21,
    progressionValue,
    progressionClass,
    effectiveClass: progressionClass,
    baselineClass: null,
    baselineThreshold: null,
    baselineActualRideCount: null,
    specialApprovalProgressionCredit: 5,
    promotionHoldClass: null,
  }
}
