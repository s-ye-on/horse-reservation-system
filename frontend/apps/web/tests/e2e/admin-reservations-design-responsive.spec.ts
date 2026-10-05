import { expect, test, type Page } from '@playwright/test'
import { createAuthenticatedPage, navigateWithinApp } from './e2e-support'

const ORIGIN = process.env.HORSE_E2E_WEB_BASE_URL ?? 'http://localhost:5173'
const date = '2030-08-12'
const base = {
  memberId: 1, memberPhone: '010-1234-5678', classType: 'ROUND_TROT', lessonDate: date,
  startTime: '09:00:00', paymentSource: 'coupon',
  coupon: { couponId: 91, couponType: 'general', status: 'active', remainingCount: 8, heldCount: 1, expiresAt: null },
  paymentDueAt: null, approvalRequestedAt: '2030-08-01T01:00:00Z', adminConfirmedAt: null,
  rejectedAt: null, rejectedBy: null, rejectionReason: null, cancelledAt: null,
  cancellationResponsibility: null, couponAction: null, adminMemo: null, approvalWarning: null,
  createdAt: '2030-08-01T01:00:00Z', updatedAt: '2030-08-01T01:00:00Z', displayGroup: 'UPCOMING',
  actions: { approve: { allowed: true }, change: { allowed: true }, cancel: { allowed: true }, complete: { allowed: false }, noShow: { allowed: false } },
}

async function fixture(page: Page) {
  const reservations = [
    { ...base, reservationId: 1, memberName: '김예약' + 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.repeat(4), status: 'pending_admin_approval', approvalWarning: 'critical' },
    { ...base, reservationId: 2, memberName: '이입금', status: 'pending_payment', paymentSource: 'single_payment', coupon: null, paymentDueAt: '2030-08-12T08:00:00Z' },
    { ...base, reservationId: 3, memberName: '박만료', status: 'payment_expired', paymentSource: 'single_payment', coupon: null },
    { ...base, reservationId: 4, memberName: '최확정', status: 'confirmed' },
  ]
  const calls: { id: number; action: string; body: unknown }[] = []
  let conflict = false
  let getCount = 0
  await page.route(`${ORIGIN}/api/admin/reservations**`, async (route) => {
    const request = route.request(), url = new URL(request.url())
    const match = url.pathname.match(/\/reservations\/(\d+)(?:\/([^/]+))?$/)
    const id = Number(match?.[1]), action = match?.[2]
    const reservation = reservations.find((item) => item.reservationId === id)
    if (request.method() === 'GET') {
      getCount++
      if (action === 'cancellation-preview') {
        const responsibility = url.searchParams.get('responsibility')
        return route.fulfill({ json: { reservationId: id, timing: 'before_cutoff', responsibility, couponAction: reservation?.paymentSource === 'single_payment' ? 'none' : responsibility === 'member' ? 'deduct' : 'return' } })
      }
      if (match) return route.fulfill({ json: reservation })
      const content = reservations.filter((item) => item.status === url.searchParams.get('status'))
      return route.fulfill({ json: { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0, hasNext: false } })
    }
    calls.push({ id, action: action ?? '', body: request.postDataJSON() })
    if (conflict) { conflict = false; return route.fulfill({ status: 409, json: { code: 'TIMESLOT_CLOSURE_COMMAND_NOT_ALLOWED' } }) }
    if (reservation) {
      if (action === 'confirm' || action === 'restore-payment') reservation.status = 'confirmed'
      if (action === 'reject') reservation.status = 'rejected'
      if (action === 'cancel') reservation.status = 'cancelled'
      if (action === 'change') reservation.lessonDate = '2030-08-13'
    }
    return route.fulfill({ json: { reservationId: id, status: reservation?.status, changed: true } })
  })
  await page.route(`${ORIGIN}/api/admin/timeslots**`, (route) => route.fulfill({ json: [{ id: 101, lessonDate: '2030-08-13', startTime: '10:00:00', totalCapacity: 8, roundArenaCapacity: 4, classCapacities: {}, closed: false, createdAt: '2030-08-01T01:00:00Z', updatedAt: '2030-08-01T01:00:00Z' }] }))
  return { calls, reservations, setConflict: () => { conflict = true }, getCount: () => getCount }
}

async function noOverflow(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(await page.evaluate(() => innerWidth))
}

test('B_예약_디자인은_1440_960_320과_modal_키보드_배경차단을_유지한다', async ({ browser }) => {
  const page = await createAuthenticatedPage(browser, 'reservations-b-responsive', 'ADMIN', { viewport: { width: 1440, height: 1000 } })
  await fixture(page)
  await navigateWithinApp(page, '/admin/reservations?reservationId=1')
  const focused = page.getByRole('region', { name: '예약 정리 대상' })
  for (const width of [1440, 960, 320]) {
    await page.setViewportSize({ width, height: 1000 })
    await expect(page.getByRole('heading', { name: '예약 운영 관리' })).toBeVisible()
    await noOverflow(page)
    if (width <= 960) await expect(page.getByRole('navigation', { name: '모바일 관리자 메뉴' })).toBeVisible()
    const opener = focused.getByRole('button', { name: '쿠폰 예약 확정' })
    await opener.click()
    const dialog = page.getByRole('dialog', { name: '예약 확정' })
    await expect(page.getByRole('dialog')).toHaveCount(1)
    const heading = dialog.getByRole('heading', { name: '예약 확정' })
    await expect(heading).toBeFocused()
    await page.keyboard.press('Shift+Tab')
    await expect(dialog.getByRole('button', { name: '예약 확정 확인' })).toBeFocused()
    await page.keyboard.press('Tab')
    await expect(dialog.getByRole('button', { name: '확인 창 닫기' })).toBeFocused()
    await page.keyboard.press('Shift+Tab')
    await expect(dialog.getByRole('button', { name: '예약 확정 확인' })).toBeFocused()
    const backgroundFocused = await page.evaluate(() => {
      document.querySelector<HTMLElement>('.admin-reservations-overview a')?.focus()
      return Boolean(document.activeElement?.closest('dialog'))
    })
    expect(backgroundFocused).toBe(true)
    const rect = await dialog.boundingBox()
    expect(rect!.x).toBeGreaterThanOrEqual(0)
    expect(rect!.x + rect!.width).toBeLessThanOrEqual(width)
    await noOverflow(page)
    await page.screenshot({ path: `/tmp/horse-reservations-dialog-${width}.png`, fullPage: true })
    await page.keyboard.press('Escape')
    await expect(dialog).toHaveCount(0)
    await expect(opener).toBeFocused()
    expect(await opener.evaluate((element) => element.getBoundingClientRect().height)).toBeGreaterThanOrEqual(44)
    await page.screenshot({ path: `/tmp/horse-reservations-${width}.png`, fullPage: true })
  }
  await page.context().close()
})

test('B_예약_작업은_충돌_재조회와_확정_반려_복구_변경_취소를_실제_payload로_구분한다', async ({ browser }) => {
  const page = await createAuthenticatedPage(browser, 'reservations-b-commands', 'ADMIN', { viewport: { width: 320, height: 1000 } })
  const state = await fixture(page)
  await navigateWithinApp(page, '/admin/reservations')
  const approval = page.locator('article').filter({ hasText: '김예약' })
  state.setConflict()
  await approval.getByRole('button', { name: '쿠폰 예약 확정' }).click()
  await page.getByRole('button', { name: '예약 확정 확인' }).click()
  await expect(page.getByRole('alert')).toContainText('현재 휴강 처리된 수업 시간')
  await expect(page.getByRole('heading', { name: '이번 처리 결과' })).toBeFocused()
  expect(state.getCount()).toBeGreaterThanOrEqual(8)
  await approval.getByRole('button', { name: '쿠폰 예약 확정' }).click()
  await page.getByRole('button', { name: '예약 확정 확인' }).click()
  await expect(approval.getByRole('button', { name: '쿠폰 예약 확정' })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '이번 처리 결과' })).toBeFocused()
  const payment = page.locator('article').filter({ hasText: '이입금' })
  await payment.getByRole('button', { name: '반려' }).click()
  await page.getByLabel('반려 사유').fill('   ')
  await page.getByRole('button', { name: '예약 반려 확인' }).click()
  await expect(page.getByLabel('반려 사유')).toHaveAttribute('aria-invalid', 'true')
  await page.getByLabel('반려 사유').fill('회원과 확인하여 반려')
  await page.getByRole('button', { name: '예약 반려 확인' }).click()
  await expect(payment).toHaveCount(0)
  const expired = page.locator('article').filter({ hasText: '박만료' })
  await expired.getByRole('button', { name: '만료 예약 복구' }).click()
  await page.getByLabel('복구 메모').fill('늦은 입금 확인')
  await page.getByRole('button', { name: '예약 복구 확인' }).click()
  await expect(expired.getByRole('button', { name: '만료 예약 복구' })).toHaveCount(0)
  const confirmed = page.locator('article').filter({ hasText: '최확정' })
  await confirmed.getByRole('button', { name: '시간 변경' }).click()
  await page.getByLabel('변경 시간대').selectOption('101')
  await page.getByLabel('관리자 메모').fill('시간 변경 확인')
  await page.getByRole('button', { name: '처리 내용 확인' }).click()
  await page.getByRole('button', { name: '시간 변경 확인' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await confirmed.getByRole('button', { name: '예약 취소' }).click()
  await page.getByLabel('취소 책임').selectOption('stable')
  await expect(page.getByLabel('최종 쿠폰 처리')).toHaveValue('return')
  await page.getByLabel('최종 쿠폰 처리').selectOption('deduct')
  await page.getByLabel('관리자 메모').fill('긴메모'.repeat(160))
  await page.getByRole('button', { name: '처리 내용 확인' }).click()
  await expect(page.getByRole('dialog').locator('dd').filter({ hasText: '마장 사유' })).toBeVisible()
  await noOverflow(page)
  await page.getByRole('button', { name: '예약 취소 확인' }).click()
  await expect(confirmed).toHaveCount(0)
  await expect(page.getByRole('status')).toContainText('최확정')
  await expect(page.getByRole('heading', { name: '이번 처리 결과' })).toBeFocused()
  expect(state.calls.find((call) => call.action === 'change')?.body).toEqual({ targetTimeSlotId: 101, memo: '시간 변경 확인' })
  expect(state.calls.find((call) => call.action === 'cancel')?.body).toEqual({ responsibility: 'stable', couponAction: 'deduct', memo: '긴메모'.repeat(160) })
  await page.context().close()
})
