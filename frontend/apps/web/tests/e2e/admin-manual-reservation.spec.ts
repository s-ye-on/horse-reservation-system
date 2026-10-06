import { expect, test, type Page } from '@playwright/test'
import { cleanupFixture, createAuthenticatedPage, navigateWithinApp, prepareMvpOneFixture } from './e2e-support'

const ORIGIN = process.env.HORSE_E2E_WEB_BASE_URL ?? 'http://localhost:5173'
const memberName = 'LongMemberName'.repeat(8)
const member = { id: 7, name: memberName, phone: '010-1111-2222', generalRideCount: 1, dressageRideCount: 0, jumpingRideCount: 0, progressionValue: 1, progressionClass: 'ROUND_BEGINNER', effectiveClass: 'ROUND_BEGINNER', progressionManagementStartedAt: null, progressionBaselineClass: null, progressionBaselineThreshold: null, progressionBaselineActualRideCount: null, specialApprovalProgressionCredit: 0, promotionHoldClass: null, dressageApproved: false, jumpingApproved: false, canUseLargeArena: false }
const couponResult = { reservationId: 500, classType: 'FIRST_RIDE', lessonDate: '2030-08-12', startTime: '09:00:00', status: 'confirmed', paymentSource: 'coupon', coupon: { couponId: 91, expiresAt: null, remainingCount: 10, heldCount: 1, availableCount: 9 }, paymentDueAt: null }

async function fixture(page: Page) {
  const calls: { body: unknown; key: string | undefined }[] = []
  let mode: 'success' | 'uncertain' | 'syncing' | 'closed' | 'payment' = 'success'
  let reads = 0
  await page.route(`${ORIGIN}/api/admin/members**`, (route) => route.fulfill({ json: new URL(route.request().url()).pathname.endsWith('/7') ? member : { content: [member], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false } }))
  await page.route(`${ORIGIN}/api/admin/timeslots**`, (route) => { reads++; return route.fulfill({ json: [{ id: 100, lessonDate: '2030-08-12', startTime: '09:00:00', totalCapacity: 8, roundArenaCapacity: 4, classCapacities: {}, closed: false, createdAt: '2030-08-01T01:00:00Z', updatedAt: '2030-08-01T01:00:00Z' }] }) })
  await page.route(`${ORIGIN}/api/admin/reservations**`, async (route) => {
    if (route.request().method() === 'GET') return route.fulfill({ json: new URL(route.request().url()).pathname.endsWith('/500') ? { ...couponResult, status: 'completed' } : { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false } })
    calls.push({ body: route.request().postDataJSON(), key: route.request().headers()['idempotency-key'] })
    if (mode === 'uncertain') { mode = 'success'; return route.fulfill({ status: 502, json: { code: 'UPSTREAM_ERROR' } }) }
    if (mode === 'syncing') { mode = 'success'; return route.fulfill({ status: 503, json: { code: 'SCHEDULE_CONFIG_SYNC_IN_PROGRESS' } }) }
    if (mode === 'closed') { mode = 'success'; return route.fulfill({ status: 409, json: { code: 'TIMESLOT_CLOSED' } }) }
    return route.fulfill({ status: 201, json: mode === 'payment' ? { ...couponResult, paymentSource: 'single_payment', coupon: null, status: 'pending_payment', paymentDueAt: '2030-08-12T08:47:00+09:00' } : couponResult })
  })
  return { calls, reads: () => reads, mode: (value: typeof mode) => { mode = value } }
}

async function fill(page: Page, name = memberName, slotId = '100') {
  await page.getByRole('button', { name: new RegExp(name) }).click()
  await expect(page.getByRole('button', { name: '처리 내용 확인' })).toBeEnabled()
  await page.getByLabel('수업 시간', { exact: true }).selectOption(slotId)
  await page.getByLabel('수업 클래스', { exact: true }).selectOption('FIRST_RIDE')
  await page.getByLabel('관리자 사유').fill('LongReason'.repeat(50))
  await page.getByRole('button', { name: '처리 내용 확인' }).click()
}
async function noOverflow(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(await page.evaluate(() => innerWidth))
}

test('수동_예약은_1440_960_320과_긴입력_modal_키보드를_유지한다', async ({ browser }) => {
  const page = await createAuthenticatedPage(browser, 'manual-responsive', 'ADMIN', { viewport: { width: 1440, height: 1000 } })
  await fixture(page)
  await navigateWithinApp(page, '/admin/reservations')
  await page.getByRole('link', { name: '수동 예약 추가' }).click()
  await expect(page).toHaveURL(/\/admin\/reservations\/new$/)
  for (const width of [1440, 960, 320]) {
    await page.setViewportSize({ width, height: 1000 })
    await expect(page.getByRole('heading', { name: '수동 예약 추가' })).toBeVisible()
    await noOverflow(page)
    if (width <= 960) await expect(page.getByLabel('관리 업무 이동')).toHaveValue('/admin/reservations')
    await fill(page)
    const dialog = page.getByRole('dialog', { name: '수동 예약 생성 확인' })
    await expect(dialog.getByRole('heading')).toBeFocused()
    await page.keyboard.press('Shift+Tab')
    await expect(dialog.getByRole('button', { name: '예약 생성 확인' })).toBeFocused()
    await page.keyboard.press('Tab')
    await expect(dialog.getByRole('button', { name: '확인 창 닫기' })).toBeFocused()
    const backgroundBlocked = await page.evaluate(() => { document.querySelector<HTMLElement>('.admin-manual-back')?.focus(); return Boolean(document.activeElement?.closest('dialog')) })
    expect(backgroundBlocked).toBe(true)
    await noOverflow(page)
    const rect = await dialog.boundingBox()
    expect(rect!.x).toBeGreaterThanOrEqual(0)
    expect(rect!.x + rect!.width).toBeLessThanOrEqual(width)
    await page.screenshot({ path: `/tmp/horse-manual-dialog-${width}.png`, fullPage: true })
    await page.keyboard.press('Escape')
    await expect(dialog).toHaveCount(0)
    await expect(page.getByRole('button', { name: '처리 내용 확인' })).toBeFocused()
    for (const label of ['수업 날짜', '수업 시간', '수업 클래스', '관리자 사유']) expect(await page.getByLabel(label, { exact: true }).evaluate((element) => element.getBoundingClientRect().height)).toBeGreaterThanOrEqual(44)
    await page.screenshot({ path: `/tmp/horse-manual-${width}.png`, fullPage: true })
  }
  await page.getByRole('button', { name: '처리 내용 확인' }).click()
  await page.getByRole('button', { name: '예약 생성 확인' }).click()
  await expect(page.getByRole('heading', { name: '예약이 확정되었습니다' })).toBeFocused()
  await expect(page.getByRole('status')).toContainText('현재 상태: 수업 완료')
  await noOverflow(page)
  await page.screenshot({ path: '/tmp/horse-manual-result-320.png', fullPage: true })
  await page.context().close()
})

test('불확실한_생성과_SYNCING은_같은키를_복원하고_업무거절은_새시도로_구분한다', async ({ browser }) => {
  const page = await createAuthenticatedPage(browser, 'manual-retry', 'ADMIN', { viewport: { width: 320, height: 1000 } })
  const state = await fixture(page)
  await navigateWithinApp(page, '/admin/reservations/new')
  state.mode('uncertain'); await fill(page); await page.getByRole('button', { name: '예약 생성 확인' }).click()
  await expect(page.getByRole('alert')).toContainText('생성 결과를 확인하지 못했습니다')
  expect(state.calls).toHaveLength(1)
  await navigateWithinApp(page, '/admin/reservations')
  await page.getByRole('link', { name: '수동 예약 추가' }).click()
  await expect(page.getByRole('button', { name: '동일 요청 결과 확인' })).toBeVisible()
  expect(state.calls).toHaveLength(1)
  state.mode('syncing'); await page.getByRole('button', { name: '동일 요청 결과 확인' }).click()
  await expect(page.getByRole('alert')).toContainText('일정을 반영 중')
  await page.getByRole('button', { name: '동일 요청 결과 확인' }).click()
  await expect(page.getByRole('heading', { name: '예약이 확정되었습니다' })).toBeFocused()
  expect(state.calls[1]).toEqual(state.calls[0]); expect(state.calls[2]).toEqual(state.calls[0])
  await page.getByRole('button', { name: '새 예약 추가' }).click()
  state.mode('closed'); await fill(page); await page.getByRole('button', { name: '예약 생성 확인' }).click()
  await expect(page.getByRole('alert')).toContainText('대상 시간대가 마감')
  expect(state.reads()).toBeGreaterThanOrEqual(4)
  await expect(page.getByLabel('관리자 사유')).toBeVisible()
  state.mode('payment'); await page.getByRole('button', { name: '처리 내용 확인' }).click(); await page.getByRole('button', { name: '예약 생성 확인' }).click()
  await expect(page.getByRole('heading', { name: '입금 확인을 기다리고 있습니다' })).toBeFocused()
  await expect(page.getByRole('status')).toContainText('08:47')
  expect(state.calls[3].key).not.toBe(state.calls[0].key)
  expect(state.calls[4].key).not.toBe(state.calls[3].key)
  expect(state.calls[4].body).toEqual({ memberId: 7, timeSlotId: 100, classType: 'FIRST_RIDE', reason: 'LongReason'.repeat(50) })
  await noOverflow(page)
  await page.context().close()
})

test('실제_API로_수동_쿠폰확정과_입금대기를_생성한다', async ({ browser }) => {
  const data = prepareMvpOneFixture()
  const page = await createAuthenticatedPage(browser, 'manual-real-api', 'ADMIN', { viewport: { width: 960, height: 1000 } })
  try {
    await navigateWithinApp(page, '/admin/reservations/new')
    for (const target of [data.couponMember, data.singlePaymentMember]) {
      await page.getByRole('button', { name: new RegExp(target.name) }).click()
      await expect(page.getByRole('button', { name: '처리 내용 확인' })).toBeEnabled()
      await page.getByLabel('수업 날짜', { exact: true }).selectOption(target.lessonDate)
      await page.getByLabel('수업 시간', { exact: true }).selectOption(String(target.timeSlotId))
      await page.getByLabel('수업 클래스', { exact: true }).selectOption('FIRST_RIDE')
      await page.getByLabel('관리자 사유').fill('관리자 전화 접수 E2E')
      await page.getByRole('button', { name: '처리 내용 확인' }).click()
      await page.getByRole('button', { name: '예약 생성 확인' }).click()
      await expect(page.getByRole('heading', { name: target.couponId ? '예약이 확정되었습니다' : '입금 확인을 기다리고 있습니다' })).toBeVisible()
      if (!target.couponId) await expect(page.getByRole('status')).toContainText('입금 마감 (한국 시간)')
      await page.getByRole('button', { name: '새 예약 추가' }).click()
    }
  } finally { await page.context().close(); cleanupFixture(data) }
})
