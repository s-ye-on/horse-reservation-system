import { expect, test, type Page } from '@playwright/test'
import { createAuthenticatedPage, navigateWithinApp } from './e2e-support'

const ORIGIN = process.env.HORSE_E2E_WEB_BASE_URL ?? 'http://localhost:5173'
const DATE = '2099-08-01'
const members = [
  { reservationId: 810, memberId: 1, memberName: '김기승', memberPhone: '010-1111-2222', classType: 'LARGE_ARENA_TROT', status: 'confirmed', paymentSource: 'coupon', couponId: 41 },
  { reservationId: 811, memberId: 2, memberName: '이기승', memberPhone: '010-3333-4444', classType: 'ROUND_BEGINNER', status: 'pending_payment', paymentSource: 'single_payment', couponId: null },
]

for (const width of [1440, 960, 320]) {
  test(`휴무_휴강은_${width}px에서_실제_재조회와_키보드_confirmation을_유지한다`, async ({ browser }) => {
    const page = await createAuthenticatedPage(browser, `e2e-closures-${width}`, 'ADMIN', { viewport: { width, height: 1000 } })
    let status = 'NORMAL'
    let version = 17
    let initial = members.length
    let current = [...members]
    let closure: Record<string, unknown> | null = null
    const commands: Array<{ path: string; body: Record<string, unknown> }> = []
    const impact = () => ({
      scheduleDate: DATE, status, resumeStatus: status === 'CLOSING' ? 'NORMAL' : null, version,
      initialReservationCount: initial, activeReservationCount: current.length,
      resolvedReservationCount: initial - current.length, remainingReservationCount: current.length,
      progressPercent: initial ? Math.round((initial - current.length) * 100 / initial) : 100,
      reservations: current, changed: false,
    })
    const slot = { id: 91, lessonDate: DATE, startTime: '14:00:00', totalCapacity: 8, roundArenaCapacity: 4, classCapacities: {}, closed: true }
    await page.route(`${ORIGIN}/api/admin/**`, async (route) => {
      const request = route.request()
      const path = new URL(request.url()).pathname
      let response: unknown
      let code = 200
      if (request.method() === 'POST') {
        const body = request.postDataJSON() as Record<string, unknown>
        commands.push({ path, body })
        if (path.endsWith('/closing/cancel')) {
          status = 'NORMAL'; version++; initial = current.length; response = impact()
        } else if (path.endsWith('/closing')) {
          status = 'CLOSING'; version++; response = impact()
        } else if (path.includes('/schedule-dates/') && path.endsWith('/cancel-for-closure')) {
          current = current.filter((member) => member.reservationId !== 810)
          response = { reservationId: 810, status: 'cancelled', responsibility: 'stable', couponAction: 'return', changed: true }
        } else if (path.endsWith('/closure')) {
          closure = { timeSlotId: 91, adminClosed: true, closed: true, status: 'IN_PROGRESS', reason: body.reason,
            version: 3, totalCount: 2, resolvedCount: 1, unresolvedCount: 1, progressPercent: 50,
            impacts: members.map((member, index) => ({ ...member, reservationStatusAtStart: member.status, currentStatus: member.status, moved: index === 0, resolved: index === 0 })) }
          response = closure
        } else { await route.fallback(); return }
      } else if (path.endsWith('/closure-impact') && path.includes('/schedule-dates/')) response = impact()
      else if (/\/schedule-dates\/[^/]+$/.test(path)) response = { scheduleDate: DATE, status, version, appliedConfigVersion: 2 }
      else if (path.endsWith('/timeslots')) response = [slot]
      else if (path.endsWith('/91/closure-impact')) { response = closure ?? {}; code = closure ? 200 : 404 }
      else { await route.fallback(); return }
      await route.fulfill({ status: code, contentType: 'application/json', body: JSON.stringify(response) })
    })
    await navigateWithinApp(page, '/admin/schedule-closures')
    await page.getByLabel('대상 날짜').fill(DATE)
    await expect(page.getByText('2건 중 0건 처리 · 0%')).toBeVisible()
    await page.getByLabel('운영 사유').fill('우천 휴무')
    const start = page.getByRole('button', { name: '날짜 휴무 시작' })
    await start.focus()
    await start.press('Enter')
    let dialog = page.getByRole('dialog', { name: '날짜 휴무 처리 시작' })
    await expect(dialog.getByRole('heading')).toBeFocused()
    await page.keyboard.press('Shift+Tab')
    await expect(dialog.getByRole('button', { name: '날짜 휴무 시작' })).toBeFocused()
    await page.keyboard.press('Tab')
    await expect(dialog.getByRole('button', { name: '취소', exact: true })).toBeFocused()
    await page.keyboard.press('Escape')
    await expect(start).toBeFocused()
    await start.click()
    await expectNoOverflow(page)
    await page.screenshot({ path: `/tmp/horse-closures-dialog-${width}.png`, fullPage: false })
    await dialog.getByRole('button', { name: '날짜 휴무 시작' }).click()
    await expect(page.getByRole('heading', { name: '휴무 처리 중', exact: true })).toBeVisible()
    const reservation = page.locator('article').filter({ hasText: '김기승' })
    await reservation.locator('summary').click()
    await reservation.getByLabel('관리자 메모').fill('운영 휴무로 예약 취소')
    await reservation.getByRole('button', { name: '예약 한 건 취소' }).click()
    dialog = page.getByRole('dialog', { name: '예약 번호 810 취소 확인' })
    await expect(dialog).toContainText('쿠폰이 반환됩니다')
    await dialog.getByRole('button', { name: '예약 한 건 취소' }).click()
    await expect(page.getByText('2건 중 1건 처리 · 50%')).toBeVisible()
    await page.getByRole('button', { name: '휴무 진행 취소' }).click()
    dialog = page.getByRole('dialog', { name: '휴무 진행 취소' })
    await expect(dialog).toContainText('자동으로 복구되지 않습니다')
    await dialog.getByRole('button', { name: '진행 취소 확정' }).click()
    await expect(page.getByText('1건 중 0건 처리 · 0%')).toBeVisible()
    await expect(page.getByRole('heading', { name: /예약 번호 810/ })).toHaveCount(0)
    await expect(page.getByRole('heading', { name: /예약 번호 811/ })).toBeVisible()
    expect(commands[0].body).toEqual({ reason: '우천 휴무', expectedVersion: 17 })
    expect(commands[1].body).toEqual({ memo: '운영 휴무로 예약 취소' })
    expect(commands[2].body).toEqual({ reason: '우천 휴무', expectedVersion: 18 })
    await expect(page.getByRole('heading', { level: 1 })).toBeFocused()
    await expectNoOverflow(page)
    await page.screenshot({ path: `/tmp/horse-closures-date-${width}.png`, fullPage: true })

    const dateTab = page.getByRole('tab', { name: '날짜 전체 휴무' })
    await dateTab.focus(); await dateTab.press('End')
    await expect(page.getByRole('tab', { name: '개별 수업 휴강' })).toBeFocused()
    await page.getByLabel('대상 날짜').fill(DATE)
    await expect(page.getByText('아직 영향 예약이 확정되지 않았습니다. 휴강 시작 후 확인할 수 있습니다.')).toBeVisible()
    await expect(page.getByLabel('영향 예약 처리 진행률')).toHaveCount(0)
    await page.getByLabel('운영 사유').fill('수업 시간 휴강')
    await page.getByRole('button', { name: '휴강 시작 검토' }).click()
    await page.getByRole('dialog').getByRole('button', { name: '휴강 시작', exact: true }).click()
    await expect(page.getByText('다른 시간으로 변경되어 처리 완료')).toBeVisible()
    await expect(page.getByRole('button', { name: '휴강 철회' })).toBeDisabled()
    await page.locator('article').filter({ hasText: '이기승' }).locator('summary').click()
    await expect(page.locator('article').filter({ hasText: '이기승' }).getByLabel('관리자 메모')).toBeEnabled()
    await expectNoOverflow(page)
    for (const control of await page.locator('.schedule-closures-page button:visible, .schedule-closures-page input:visible, .schedule-closures-page select:visible, .schedule-closures-page summary:visible').all()) {
      expect((await control.boundingBox())?.height).toBeGreaterThanOrEqual(44)
    }
    await page.screenshot({ path: `/tmp/horse-closures-slot-${width}.png`, fullPage: true })
    await page.context().close()
  })
}

async function expectNoOverflow(page: Page) {
  const widths = await page.evaluate(() => [window.innerWidth, document.documentElement.scrollWidth, document.body.scrollWidth])
  expect(widths[1]).toBeLessThanOrEqual(widths[0])
  expect(widths[2]).toBeLessThanOrEqual(widths[0])
}
