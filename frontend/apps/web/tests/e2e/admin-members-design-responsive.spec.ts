import { expect, test, type Page } from '@playwright/test'
import { createAuthenticatedPage, navigateWithinApp } from './e2e-support'

const ORIGIN = process.env.HORSE_E2E_WEB_BASE_URL ?? 'http://localhost:5173'

for (const width of [1440, 960, 320]) {
  test(`회원_디자인_${width}px_보류_미리보기_conflict_dialog_접근성`, async ({ browser }) => {
    const page = await createAuthenticatedPage(browser, `members-design-${width}`, 'ADMIN', { viewport: { width, height: 900 } })
    let current = member()
    let conflict = true
    let token = '"members-original-token"'
    let detailReads = 0
    let auditReads = 0
    await page.route(`${ORIGIN}/api/admin/members**`, async (route) => {
      const request = route.request()
      const url = new URL(request.url())
      const respond = async (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
      if (url.pathname.endsWith('/class-progression/audit-logs')) {
        auditReads++
        expect(url.searchParams.get('size')).toBe('5')
        await respond({ content: [{ auditId: 1, action: 'PROMOTION_HOLD_SET', reason: '운영 기록 확인 '.repeat(12), occurredAt: '2026-09-30T14:00:00', actorAuthSubject: 'hidden-auth-subject', fromState: { promotionHoldClass: null }, toState: { promotionHoldClass: 'FIRST_RIDE' } }], page: 0, size: 5, totalElements: 1, totalPages: 1, hasNext: false })
      } else if (url.pathname.endsWith('/class-progression/preview')) {
        const input = request.postDataJSON()
        expect(input.action).toBe('SET_PROMOTION_HOLD')
        expect(input.promotionHoldClass).toBe('FIRST_RIDE')
        await respond({ stateToken: token, current: projection(current), expected: { ...projection(current), effectiveClass: 'FIRST_RIDE', promotionHoldClass: 'FIRST_RIDE' } })
      } else if (request.method() !== 'GET') {
        expect(request.headers()['if-match']).toBe(token)
        if (conflict) {
          conflict = false
          current = { ...current, generalRideCount: 8, progressionValue: 8 }
          token = '"members-new-token-unchanged"'
          await respond({ code: 'MEMBER_CLASS_STATE_CONFLICT', message: 'state conflict' }, 409)
        } else {
          current = { ...current, promotionHoldClass: 'FIRST_RIDE', effectiveClass: 'FIRST_RIDE' }
          await respond(current)
        }
      } else if (url.pathname === '/api/admin/members') {
        expect(url.searchParams.get('size')).toBe('20')
        await respond({ content: [current], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false })
      } else {
        detailReads++
        await respond(current)
      }
    })
    await navigateWithinApp(page, '/admin/members')
    const selection = page.getByRole('button', { name: /김승마/ })
    await expect(selection).toHaveAttribute('aria-current', 'true')
    await expect(selection).toHaveAttribute('aria-controls', 'admin-member-detail')
    await page.getByLabel('변경 항목').selectOption('SET_PROMOTION_HOLD')
    await page.getByLabel('보류할 최고 클래스').selectOption('FIRST_RIDE')
    await page.getByRole('button', { name: '변경 결과 미리보기' }).click()
    const preview = page.getByRole('region', { name: '변경 전후 예상 클래스' })
    await expect(preview).toContainText('왕초보')
    await expect(preview).toContainText('원형 속보')
    await expect(preview).toContainText('보류 상한')
    await page.getByLabel('관리자 사유', { exact: true }).fill('장기 관리 사유 '.repeat(50).slice(0, 498))
    const opener = page.getByRole('button', { name: '변경 내용 확인' })
    await opener.click()
    const dialog = page.getByRole('dialog')
    const title = dialog.getByRole('heading', { name: '자동 승급 보류 설정·변경 확인' })
    await expect(title).toBeFocused()
    await page.keyboard.press('Shift+Tab')
    await expect(dialog.getByRole('button', { name: '변경 적용' })).toBeFocused()
    await page.keyboard.press('Tab')
    await expect(dialog.getByRole('button', { name: '확인 창 닫기' })).toBeFocused()
    await selection.evaluate((node: HTMLElement) => node.focus())
    expect(await dialog.evaluate((node) => node.contains(document.activeElement))).toBe(true)
    await assertOverflow(page)
    await page.screenshot({ path: `/tmp/horse-members-dialog-${width}.png` })
    await page.keyboard.press('Escape')
    await expect(dialog).not.toBeVisible()
    await expect(opener).toBeFocused()
    await opener.click()
    await dialog.getByRole('button', { name: '변경 적용' }).click()
    await expect(page.getByRole('alert')).toContainText('현재 상태에서 예상 결과를 다시 확인')
    await expect.poll(() => detailReads).toBeGreaterThan(1)
    await expect(opener).toBeDisabled()
    await expect(preview).toHaveCount(0)
    await page.getByRole('button', { name: '변경 결과 미리보기' }).click()
    await expect(preview).toBeVisible()
    await opener.click()
    await dialog.getByRole('button', { name: '변경 적용' }).click()
    const success = page.getByText('자동 승급 보류 설정·변경 처리가 완료됐습니다.')
    await expect(success).toBeVisible()
    await expect(success).toBeFocused()
    await expect.poll(() => auditReads).toBeGreaterThan(1)
    const summary = page.getByRole('region', { name: '회원 요약' })
    await expect(summary).toContainText('원형 속보')
    await expect(summary).toContainText('왕초보')
    await expect(page.getByRole('button', { name: '마장마술 승인 변경' })).toBeDisabled()
    await page.getByText('기승 기록·계산 상세 보기').click()
    await page.getByText('전후 상태 보기').click()
    await expect(page.getByText('hidden-auth-subject')).toHaveCount(0)
    await assertOverflow(page)
    await page.evaluate(() => window.scrollTo(0, 0))
    await page.screenshot({ path: `/tmp/horse-members-${width}.png`, fullPage: true })
    const targets = await page.locator('.admin-members-page button:visible, .admin-members-page select:visible, .admin-members-page textarea:visible, .admin-members-page summary:visible').evaluateAll((nodes) => nodes.map((node) => ({ label: node.textContent, height: node.getBoundingClientRect().height })))
    for (const target of targets) expect(target.height, target.label ?? '').toBeGreaterThanOrEqual(44)
    await page.context().close()
  })

  test(`회원_특수승인_${width}px_최신_반대쪽_값_보존_해제_인정분_유지`, async ({ browser }) => {
    const page = await createAuthenticatedPage(browser, `members-special-${width}`, 'ADMIN', { viewport: { width, height: 900 } })
    let current = { ...member(), specialApprovalProgressionCredit: 5 }
    let approvalCommands = 0
    await page.route(`${ORIGIN}/api/admin/members**`, async (route) => {
      const request = route.request()
      const path = new URL(request.url()).pathname
      let body: unknown = current
      if (path.endsWith('/class-progression/audit-logs')) {
        body = { content: [], page: 0, size: 5, totalElements: 0, totalPages: 0, hasNext: false }
      } else if (request.method() !== 'GET') {
        expect(path).toMatch(/riding-permissions$/)
        const input = request.postDataJSON()
        expect(input.reason).toBe('승인 사유 확인')
        if (approvalCommands === 0) {
          expect(input).toMatchObject({ dressageApproved: true, jumpingApproved: true })
        } else {
          expect(input).toMatchObject({ dressageApproved: false, jumpingApproved: true })
        }
        approvalCommands++
        current = { ...current, dressageApproved: input.dressageApproved, jumpingApproved: input.jumpingApproved }
        body = current
      } else if (path === '/api/admin/members') {
        body = { content: [current], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false }
      }
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
    })
    await navigateWithinApp(page, '/admin/members')
    await page.getByRole('button', { name: '마장마술 승인 변경' }).click()
    const review = page.getByRole('button', { name: '승인 변경 내용 확인' })
    await review.click()
    await expect(page.getByLabel('승인 변경 사유')).toHaveAttribute('aria-invalid', 'true')
    await expect(page.getByRole('alert')).toContainText('1~500자')
    await page.getByLabel('승인 변경 사유').fill('승인 사유 확인')
    await review.click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByRole('heading', { name: '마장마술 승인 확인' })).toBeFocused()
    await expect(dialog).toContainText('실제 일반 기승 횟수는 증가하지 않습니다')
    await assertOverflow(page)
    // Another administrator changes the opposite approval after confirmation opens.
    current = { ...current, jumpingApproved: true }
    await dialog.getByRole('button', { name: '변경 적용' }).click()
    await expect(page.getByText('마장마술 승인을 반영했습니다.')).toBeFocused()
    await expect(page.getByRole('option', { name: '특수 승인 추가 인정 횟수 교정' })).toBeDisabled()
    await page.getByRole('button', { name: '마장마술 승인 변경' }).click()
    await page.getByLabel('승인 변경 사유').fill('승인 사유 확인')
    await review.click()
    await expect(dialog).toContainText('자동으로 제거되지 않습니다')
    await dialog.getByRole('button', { name: '변경 적용' }).click()
    await expect(page.getByText('마장마술 승인 해제를 반영했습니다.')).toBeVisible()
    expect(current.specialApprovalProgressionCredit).toBe(5)
    expect(current.jumpingApproved).toBe(true)
    await page.getByText('기승 기록·계산 상세 보기').click()
    await expect(page.locator('.admin-member-ride-grid')).toContainText('5회')
    await assertOverflow(page)
    expect(approvalCommands).toBe(2)
    await page.context().close()
  })
}

async function assertOverflow(page: Page) {
  const size = await page.evaluate(() => ({ viewport: innerWidth, doc: document.documentElement.scrollWidth, body: document.body.scrollWidth }))
  expect(size.doc).toBeLessThanOrEqual(size.viewport)
  expect(size.body).toBeLessThanOrEqual(size.viewport)
}

function member() {
  return { id: 71, name: '김승마 긴 회원 이름 정보 확인', phone: '010-1234-5678', generalRideCount: 7, dressageRideCount: 0, jumpingRideCount: 0, dressageApproved: false, jumpingApproved: false, canUseLargeArena: false, progressionValue: 7, progressionClass: 'ROUND_TROT', effectiveClass: 'ROUND_TROT', progressionManagementStartedAt: '2026-09-01T09:00:00', progressionBaselineClass: null, progressionBaselineThreshold: null, progressionBaselineActualRideCount: null, specialApprovalProgressionCredit: 0, promotionHoldClass: null as string | null }
}

function projection(value: ReturnType<typeof member>) {
  return { actualCompletedRideCount: value.generalRideCount, progressionValue: value.progressionValue, progressionClass: value.progressionClass, effectiveClass: value.effectiveClass, baselineClass: value.progressionBaselineClass, baselineThreshold: value.progressionBaselineThreshold, baselineActualRideCount: value.progressionBaselineActualRideCount, specialApprovalProgressionCredit: value.specialApprovalProgressionCredit, promotionHoldClass: value.promotionHoldClass }
}
