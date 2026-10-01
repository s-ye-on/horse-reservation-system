import { expect, test, type Page } from '@playwright/test'
import { createAuthenticatedPage, navigateWithinApp } from './e2e-support'

const ORIGIN = process.env.HORSE_E2E_WEB_BASE_URL ?? 'http://localhost:5173'
const DATE = '2026-09-30'
const allowed: { allowed: boolean; blockedReason: string | null } = { allowed: true, blockedReason: null }
function reservation(index: number) {
  return {
    reservationId: 910 + index, memberId: 100 + index,
    memberName: index === 2 ? '긴 회원 이름과 수업 정보를 확인하는 운영 사례' : `기승회원${index}`,
    memberPhone: '010-1234-5678', lessonDate: DATE, startTime: '14:00:00',
    classType: index % 3 === 0 ? 'ROUND_TROT' : index % 3 === 1 ? 'DRESSAGE' : 'JUMPING',
    status: 'confirmed', paymentSource: index === 1 ? 'single_payment' : 'coupon',
    coupon: index === 1 ? null : { couponId: 70 + index, couponType: 'GENERAL', status: 'active', remainingCount: 5, heldCount: 1, expiresAt: null },
    actions: { complete: allowed, noShow: allowed, change: allowed, cancel: allowed, approve: allowed },
    approvalRequestedAt: `${DATE}T01:00:00Z`, createdAt: `${DATE}T01:00:00Z`, updatedAt: `${DATE}T01:00:00Z`,
  }
}

for (const width of [1440, 960, 320]) {
  test(`출석_${width}px_단건_혼합_부분성공과_modal_키보드`, async ({ browser }) => {
    const page = await createAuthenticatedPage(browser, `e2e-attendance-${width}`, 'ADMIN', { viewport: { width, height: 1000 } })
    let current = Array.from({ length: 11 }, (_, index) => reservation(index))
    current.push({ ...reservation(11), lessonDate: '2099-10-05', actions: { complete: { allowed: false, blockedReason: 'RESERVATION_LESSON_NOT_STARTED' }, noShow: { allowed: false, blockedReason: 'RESERVATION_LESSON_NOT_STARTED' }, change: allowed, cancel: allowed, approve: allowed } })
    let queryCount = 0
    let failComplete = true
    const commands: Array<{ path: string; body: Record<string, unknown> | undefined }> = []
    await page.route(`${ORIGIN}/api/admin/reservations**`, async (route) => {
      const request = route.request(), url = new URL(request.url()), path = url.pathname
      if (request.method() === 'GET') {
        queryCount++
        expect(url.searchParams.get('status')).toBe('confirmed')
        expect(url.searchParams.get('size')).toBe('100')
        expect(url.searchParams.get('page')).toBe('0')
        await route.fulfill({ json: { content: current, page: 0, size: 100, totalElements: current.length, totalPages: 1, hasNext: false } })
        return
      }
      const body = request.postData() ? request.postDataJSON() : undefined
      commands.push({ path, body })
      if (path.endsWith('/complete-bulk')) {
        const items = (body.items as Array<{ reservationId: number; action: string }>)
        expect(items.length).toBeLessThanOrEqual(8)
        expect(body.lessonDate).toBe(DATE)
        expect(body.startTime).toBe('14:00:00')
        const results = items.map((item, index) => ({ ...item, success: index !== 1, status: index === 1 ? null : item.action === 'complete' ? 'completed' : 'no_show', errorCode: index === 1 ? 'COUPON_HOLD_STATE_CONFLICT' : null, errorMessage: null }))
        current = current.filter((entry) => !results.some((item) => item.success && item.reservationId === entry.reservationId))
        await route.fulfill({ json: { requestedCount: items.length, succeededCount: items.length - 1, failedCount: 1, items: results } })
      } else if (path.endsWith('/complete') && failComplete) {
        failComplete = false
        await route.fulfill({ status: 409, json: { code: 'TIMESLOT_CLOSURE_COMMAND_NOT_ALLOWED', message: '휴강', status: 409 } })
      } else {
        const id = Number(path.split('/').at(-2))
        current = current.filter((entry) => entry.reservationId !== id)
        await route.fulfill({ json: path.endsWith('/complete')
          ? { reservationId: id, status: 'completed', paymentSource: 'coupon', couponId: 70, generalRideCount: 8, dressageRideCount: 2, jumpingRideCount: 1 }
          : { reservationId: id, status: 'no_show', paymentSource: 'single_payment', couponId: null, couponAction: 'none', adminMemo: body.memo } })
      }
    })
    await navigateWithinApp(page, '/admin/attendance')
    await expect(page.getByText('불러온 확정 예약 12건 · 최대 100건 표시')).toBeVisible()
    const before = page.getByRole('article', { name: '기승회원11', exact: true })
    await expect(before.getByRole('checkbox')).toBeDisabled()
    await expect(before.getByRole('button', { name: '수업 완료' })).toBeDisabled()
    await expect(before.getByRole('button', { name: '노쇼 입력' })).toBeDisabled()
    await expectNoOverflow(page)
    await page.screenshot({ path: `/tmp/horse-attendance-${width}.png`, fullPage: false })

    const single = page.getByRole('article', { name: '기승회원0', exact: true })
    const checkbox = single.getByRole('checkbox')
    await checkbox.focus(); await page.keyboard.press('Space')
    await expect(checkbox).toBeChecked()
    await page.keyboard.press('Space'); await expect(checkbox).not.toBeChecked()
    const complete = single.getByRole('button', { name: '수업 완료' })
    await complete.click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByRole('heading')).toBeFocused()
    const confirm = dialog.getByRole('button', { name: '이 예약 결과 기록' })
    const close = dialog.getByRole('button', { name: '확인 창 닫기' })
    await page.keyboard.press('Shift+Tab'); await expect(confirm).toBeFocused()
    await page.keyboard.press('Tab'); await expect(close).toBeFocused()
    await page.keyboard.press('Shift+Tab'); await expect(confirm).toBeFocused()
    await checkbox.evaluate((element) => (element as HTMLElement).focus())
    await expect(confirm).toBeFocused()
    await page.keyboard.press('Escape'); await expect(complete).toBeFocused()
    await complete.click()
    const priorQueries = queryCount
    await confirm.click()
    await expect(page.getByRole('alert')).toContainText('현재 휴강 처리된 수업 시간')
    await expect.poll(() => queryCount).toBeGreaterThan(priorQueries)
    await expect(single).toBeVisible()
    await complete.click(); await confirm.click()
    await expect(page.getByRole('status', { name: '이번 처리 결과' })).toBeFocused()
    await expect(single).toHaveCount(0)
    expect(commands[0].body).toBeUndefined()

    const payment = page.getByRole('article', { name: '기승회원1', exact: true })
    await payment.getByRole('button', { name: '노쇼 입력' }).click()
    await expect(payment.getByLabel('기승회원1 관리자 메모')).toBeFocused()
    await expect(payment.getByRole('combobox')).toHaveCount(0)
    await payment.getByLabel('기승회원1 관리자 메모').fill('당일 미방문 확인')
    await payment.getByRole('button', { name: '노쇼 확인' }).click()
    await dialog.getByRole('button', { name: '이 예약 결과 기록' }).click()
    await expect(payment).toHaveCount(0)
    expect(commands.at(-1)?.body).toEqual({ couponAction: 'none', memo: '당일 미방문 확인' })

    const remaining = current.filter((entry) => entry.lessonDate === DATE)
    for (const entry of remaining.slice(0, 8)) {
      const card = page.getByRole('article', { name: entry.memberName, exact: true })
      await card.getByRole('checkbox').check()
      await card.getByLabel(`${entry.memberName} 처리 결과`).selectOption('complete')
    }
    await expect(page.getByRole('article', { name: remaining[8].memberName, exact: true }).getByRole('checkbox')).toBeDisabled()
    for (const [index, couponAction] of [[1, 'deduct'], [2, 'return']] as const) {
      const entry = remaining[index], card = page.getByRole('article', { name: entry.memberName, exact: true })
      await card.getByLabel(`${entry.memberName} 처리 결과`).selectOption('no_show')
      await card.getByLabel(`${entry.memberName} 쿠폰 처리`).selectOption(couponAction)
      await card.getByLabel(`${entry.memberName} 관리자 메모`).fill(index === 1 ? '당일 미방문 확인' : '긴메모'.repeat(166))
    }
    await page.getByRole('button', { name: '선택 예약 함께 확인' }).first().click()
    await expect(dialog).toContainText('수업 완료 6건 · 노쇼 2건')
    await expect(dialog).toContainText('쿠폰 점유 반환')
    await expectNoOverflow(page)
    expect(await page.locator('.attendance-dialog-scroll').evaluate((element) => element.scrollHeight > element.clientHeight)).toBe(true)
    await page.screenshot({ path: `/tmp/horse-attendance-dialog-${width}.png`, fullPage: false })
    await dialog.getByRole('button', { name: '선택 8건 결과 기록' }).click()
    const result = page.getByRole('status', { name: '이번 처리 결과' })
    await expect(result).toContainText('요청 8건 · 성공 7건 · 실패 1건')
    await expect(result.getByRole('alert')).toContainText('쿠폰 상태가 변경되었습니다')
    await expect(result).toBeFocused()
    await expect(page.getByRole('article', { name: remaining[0].memberName, exact: true })).toHaveCount(0)
    await expect(page.getByRole('article', { name: remaining[1].memberName, exact: true })).toBeVisible()
    const batch = commands.at(-1)?.body
    const batchItems = batch?.items
    expect(batchItems).toBeDefined()
    expect((batchItems as unknown[]).slice(0, 3)).toMatchObject([{ action: 'complete' }, { action: 'no_show', couponAction: 'deduct' }, { action: 'no_show', couponAction: 'return' }])
    await expectNoOverflow(page)
    for (const element of await page.locator('.admin-attendance-page button:visible, .admin-attendance-page select:visible, .admin-attendance-page textarea:visible, .admin-attendance-page summary:visible, .attendance-checkbox:visible').all()) {
      expect((await element.boundingBox())?.height).toBeGreaterThanOrEqual(44)
    }
    await page.screenshot({ path: `/tmp/horse-attendance-result-${width}.png`, fullPage: false })
    await page.context().close()
  })
}

async function expectNoOverflow(page: Page) {
  const widths = await page.evaluate(() => [innerWidth, document.documentElement.scrollWidth, document.body.scrollWidth])
  expect(widths[1]).toBeLessThanOrEqual(widths[0])
  expect(widths[2]).toBeLessThanOrEqual(widths[0])
}
