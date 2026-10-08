import { expect, test, type Page } from '@playwright/test'
import { createAuthenticatedPage, navigateWithinApp } from './e2e-support'

const ORIGIN = process.env.HORSE_E2E_WEB_BASE_URL ?? 'http://localhost:5173'
const capacities = { FIRST_RIDE: 2, ROUND_BEGINNER: 2, ROUND_TROT: 2, LARGE_ARENA_BEGINNER: 3,
  LARGE_ARENA_TROT: 3, CANTER_BEGINNER: 3, CANTER: 3, DRESSAGE: 1, JUMPING: 1 }
const fixture = { id: 4, lessonDate: '2030-08-10', startTime: '09:00:00', totalCapacity: 8,
  roundArenaCapacity: 4, classCapacities: capacities, closed: true,
  createdAt: '2030-07-29T01:00:00Z', updatedAt: '2030-07-29T01:00:00Z' }
async function noOverflow(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth && document.body.scrollWidth <= innerWidth)).toBe(true)
}
for (const width of [1440, 960, 320]) {
  test(`Frozen_카드_정원편집_생성과_dialog_keyboard는_${width}px에서_동작한다`, async ({ browser }) => {
    const page = await createAuthenticatedPage(browser, `timeslots-design-${width}`, 'ADMIN', { viewport: { width, height: 900 } })
    const slots = [fixture]
    const calls: Array<{ method: string; body: Record<string, unknown> }> = []
    await page.route(`${ORIGIN}/api/admin/timeslots**`, async (route) => {
      const method = route.request().method()
      if (method === 'GET') { await route.fulfill({ json: slots }); return }
      const body = route.request().postDataJSON()
      calls.push({ method, body })
      if (method === 'POST') { slots.push({ ...fixture, ...body, id: 5 }); await route.fulfill({ status: 201, json: slots[1] }); return }
      slots[0] = { ...slots[0], ...body }
      await route.fulfill({ json: slots[0] })
    })
    await navigateWithinApp(page, '/admin/timeslots')
    const card = page.getByRole('article').first()
    await expect(card.getByRole('heading', { name: '09:00' })).toBeVisible()
    await expect(page.getByText('예약 가능', { exact: true })).toHaveCount(0)
    await noOverflow(page)
    await page.screenshot({ path: `/tmp/horse-timeslots-initial-${width}.png`, fullPage: true })
    if (width <= 960) await expect(page.getByRole('navigation', { name: '모바일 관리자 메뉴' })).toBeVisible()
    const disclosure = card.locator('summary').first()
    await disclosure.focus(); await disclosure.press('Enter')
    await expect(card.getByText('마장마술', { exact: true })).toBeVisible()
    const opener = card.getByRole('button', { name: '정원 수정' })
    await opener.click()
    let dialog = page.getByRole('dialog', { name: '시간대 정원 편집' })
    await expect(dialog.getByRole('heading', { name: '시간대 정원 편집' })).toBeFocused()
    const dialogTargets = await dialog.locator('input, button').evaluateAll((elements) => elements.every((element) => element.getBoundingClientRect().height >= 44))
    expect(dialogTargets).toBe(true)
    await page.keyboard.press('Shift+Tab')
    await expect(dialog.getByRole('button', { name: '변경 내용 확인' })).toBeFocused()
    await page.keyboard.press('Tab')
    await expect(dialog.getByRole('button', { name: '시간대 작업 창 닫기' })).toBeFocused()
    const total = dialog.getByLabel('전체 정원', { exact: true })
    await total.focus(); await total.press('Shift+Tab')
    await expect(dialog.getByRole('button', { name: '시간대 작업 창 닫기' })).toBeFocused()
    // Native modal must prevent programmatic focus from entering the background.
    await opener.evaluate((element: HTMLElement) => element.focus())
    expect(await dialog.evaluate((element) => element.contains(document.activeElement))).toBe(true)
    await page.keyboard.press('Escape')
    await expect(dialog).toHaveCount(0); await expect(opener).toBeFocused()
    await opener.click()
    dialog = page.getByRole('dialog', { name: '시간대 정원 편집' })
    await dialog.getByLabel('전체 정원', { exact: true }).fill('9')
    await dialog.getByRole('button', { name: '변경 내용 확인' }).click()
    await expect(dialog.getByLabel('전체 정원', { exact: true })).toHaveAttribute('aria-invalid', 'true')
    expect(calls).toHaveLength(0)
    await dialog.getByLabel('전체 정원', { exact: true }).fill('7')
    await dialog.getByRole('button', { name: '변경 내용 확인' }).click()
    dialog = page.getByRole('dialog', { name: '정원 변경 확인' })
    await expect(dialog.getByRole('heading', { name: '정원 변경 확인' })).toBeFocused()
    await expect(dialog.getByText(/정규 시간표 정원 동기화에서 제외/)).toBeVisible()
    await noOverflow(page)
    await page.screenshot({ path: `/tmp/horse-timeslots-confirm-${width}.png` })
    await dialog.getByRole('button', { name: '확인 후 적용' }).click()
    await expect(page.getByRole('heading', { name: '이번 처리 결과' })).toBeFocused()
    expect(calls[0]).toEqual({ method: 'PUT', body: { totalCapacity: 7, roundArenaCapacity: 4, classCapacities: capacities } })
    await page.getByRole('button', { name: '수동 시간대 생성' }).click()
    dialog = page.getByRole('dialog', { name: '수동 시간대 생성' })
    await dialog.getByLabel('수업 날짜').fill('2030-08-10')
    await dialog.getByLabel('시작 시각').fill('14:00')
    await dialog.getByRole('button', { name: '변경 내용 확인' }).click()
    await page.getByRole('dialog').getByRole('button', { name: '확인 후 적용' }).click()
    await expect(page.getByRole('article')).toHaveCount(2)
    expect(calls[1].body).toEqual({ lessonDate: '2030-08-10', startTime: '14:00', totalCapacity: 8, roundArenaCapacity: 4,
      classCapacities: Object.fromEntries(Object.keys(capacities).map((key) => [key, 0])) })
    await noOverflow(page)
    const targets = await page.locator('.admin-timeslots-page button, .admin-timeslots-page summary').evaluateAll((elements) =>
      elements.filter((element) => element.getClientRects().length > 0).every((element) => element.getBoundingClientRect().height >= 44))
    expect(targets).toBe(true)
    await page.screenshot({ path: `/tmp/horse-timeslots-${width}.png`, fullPage: true })
    await page.context().close()
  })
}
test('동기화와_철회_거부후_재조회하며_명시적_PATCH와_계속마감_결과를_유지한다', async ({ browser }) => {
  const page = await createAuthenticatedPage(browser, 'timeslots-server-guard', 'ADMIN', { viewport: { width: 320, height: 900 } })
  let getCount = 0, rejectCapacity = true, rejectStatus = true
  let finishRefresh!: () => void
  const refreshGate = new Promise<void>((resolve) => { finishRefresh = resolve })
  const patches: boolean[] = []
  await page.route(`${ORIGIN}/api/admin/timeslots**`, async (route) => {
    const method = route.request().method()
    if (method === 'GET') {
      getCount++
      if (getCount === 2) await refreshGate
      await route.fulfill({ json: [{ ...fixture, totalCapacity: getCount > 1 ? 6 : 8 }] }); return
    }
    if (method === 'PUT') {
      if (rejectCapacity) await route.fulfill({ status: 503, json: { code: 'SCHEDULE_CONFIG_SYNC_IN_PROGRESS' } })
      else await route.fulfill({ json: fixture })
      return
    }
    patches.push(route.request().postDataJSON().closed)
    if (rejectStatus) await route.fulfill({ status: 409, json: { code: 'TIMESLOT_CLOSURE_WITHDRAWAL_NOT_ALLOWED' } })
    else await route.fulfill({ json: fixture })
  })
  await navigateWithinApp(page, '/admin/timeslots')
  await page.getByRole('button', { name: '정원 수정' }).click()
  await page.getByLabel('전체 정원', { exact: true }).fill('7')
  await page.getByRole('button', { name: '변경 내용 확인' }).click()
  await page.getByRole('button', { name: '확인 후 적용' }).click()
  await expect(page.getByRole('alert')).toContainText('일정 설정을 반영 중')
  await expect(page.getByLabel('전체 정원', { exact: true })).toHaveValue('7')
  await expect.poll(() => getCount).toBe(2)
  await expect(page.getByRole('button', { name: '변경 내용 확인' })).toBeDisabled()
  finishRefresh()
  await expect(page.getByRole('button', { name: '변경 내용 확인' })).toBeEnabled()
  await page.getByRole('button', { name: '변경 내용 확인' }).click()
  await expect(page.getByRole('dialog', { name: '정원 변경 확인' })).toContainText('6 → 7명')
  await page.keyboard.press('Escape')
  const card = page.getByRole('article')
  await card.getByText('마감·재개 요청').click()
  await card.getByRole('button', { name: '관리자 마감 철회·재개' }).click()
  await page.getByRole('button', { name: '확인 후 적용' }).click()
  await expect(page.getByRole('alert')).toContainText('이미 처리된 예약')
  await expect(page.getByRole('alert')).toBeFocused()
  await expect.poll(() => getCount).toBe(3)
  rejectStatus = false; rejectCapacity = false
  await card.getByRole('button', { name: '관리자 마감 철회·재개' }).click()
  await page.getByRole('button', { name: '확인 후 적용' }).click()
  await expect(page.getByRole('status', { name: '이번 처리 결과' })).toContainText('여전히 신규 예약 마감')
  expect(patches).toEqual([false, false])
  await card.getByRole('button', { name: '신규 예약 마감', exact: true }).click()
  await expect(page.getByRole('dialog')).toContainText('기존 예약은 자동 취소되지')
  await page.getByRole('button', { name: '확인 후 적용' }).click()
  await expect(page.getByRole('status', { name: '이번 처리 결과' })).toContainText('영향 예약 처리는 휴무·휴강 관리')
  expect(patches).toEqual([false, false, true])
  await noOverflow(page)
  await page.context().close()
})
