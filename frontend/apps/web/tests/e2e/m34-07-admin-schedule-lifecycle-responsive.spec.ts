import { expect, test } from '@playwright/test'
import { createAuthenticatedPage, navigateWithinApp } from './e2e-support'

const WEB_ORIGIN = process.env.HORSE_E2E_WEB_BASE_URL ?? 'http://localhost:5173'
const JSON_HEADERS = { 'Content-Type': 'application/json' }

for (const width of [1440, 960, 320]) {
test(`정규_시간표_삭제와_예약_정리_동선은_${width}px에서_사용할_수_있다`, async ({ browser }) => {
  let templateActive = true
  let deleteRequest: Record<string, unknown> | undefined
  const page = await createAuthenticatedPage(
    browser,
    'e2e-m34-07-schedule-admin',
    'ADMIN',
    { viewport: { width, height: 900 } },
  )

  await page.route(`${WEB_ORIGIN}/api/admin/schedule-templates**`, async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (path.endsWith('/future-occupying-reservations')) {
      await route.fulfill({ status: 200, headers: JSON_HEADERS, body: JSON.stringify(futureReservations()) })
      return
    }
    if (request.method() === 'DELETE') {
      deleteRequest = request.postDataJSON() as Record<string, unknown>
      templateActive = false
      await route.fulfill({ status: 200, headers: JSON_HEADERS, body: JSON.stringify(mutationResponse()) })
      return
    }
    await route.fulfill({ status: 200, headers: JSON_HEADERS, body: JSON.stringify([template(templateActive)]) })
  })
  await page.route(`${WEB_ORIGIN}/api/admin/recurring-holidays**`, async (route) => {
    await route.fulfill({ status: 200, headers: JSON_HEADERS, body: '[]' })
  })
  await page.route(`${WEB_ORIGIN}/api/admin/schedule-sync**`, async (route) => {
    await route.fulfill({ status: 200, headers: JSON_HEADERS, body: JSON.stringify(synchronization()) })
  })

  await navigateWithinApp(page, '/admin/schedule-configuration')
  const templateSection = page.getByRole('region', { name: '정규 시간표' })
  await expect(templateSection.getByRole('heading', { name: '화요일 09:00~09:45' })).toBeVisible()
  await page.screenshot({ path: `/tmp/horse-schedule-configuration-${width}.png`, fullPage: true })
  const templateTab = page.getByRole('tab', { name: '정규 시간표' })
  await templateTab.focus()
  await templateTab.press('ArrowRight')
  await expect(page.getByRole('tab', { name: '정기 휴일' })).toBeFocused()
  await page.getByRole('tab', { name: '정기 휴일' }).press('Home')
  await expect(templateTab).toBeFocused()
  await templateSection.getByRole('button', { name: '수정' }).click()
  const editor = page.getByRole('dialog', { name: '정규 시간표 수정' })
  await expect(editor.getByText('화요일 09:00 정규 시간표 수정')).toBeVisible()
  await expect(editor.getByLabel('요일')).toBeFocused()
  await expect(editor.getByLabel('전체 정원')).toHaveAttribute('max', '8')
  await expectNoHorizontalOverflow(page)
  await editor.getByRole('button', { name: '편집 창 닫기' }).press('Escape')
  await expect(templateSection.getByRole('button', { name: '수정' })).toBeFocused()
  await expect(page.getByLabel('운영 변경 사유')).toHaveCount(0)
  await templateSection.getByRole('button', { name: '삭제' }).click()

  const dialog = page.getByRole('dialog', { name: '정규 시간표 삭제 영향 확인' })
  await expect(dialog).toContainText('정리할 예약')
  await expect(dialog).toContainText('1건')
  await expect(dialog.getByLabel('삭제 사유')).toBeFocused()
  await dialog.getByRole('button', { name: '삭제 확정' }).click()
  await expect(dialog.getByRole('alert')).toContainText('삭제 사유를 입력해 주세요.')
  await dialog.getByLabel('삭제 사유').fill('겨울 운영 종료')
  await dialog.getByRole('button', { name: '삭제 확정' }).click()

  await expect(page.getByRole('region', { name: '예약 정리 필요' })).toBeVisible()
  await expect(page.getByText('예약 번호 77 · 김정리')).toBeVisible()
  await expect(page.getByRole('link', { name: '예약 확인' })).toHaveAttribute('href', '/admin/reservations?reservationId=77')
  await expect(templateSection.getByRole('heading', { name: '화요일 09:00~09:45' })).toHaveCount(0)
  await expect(page.getByRole('heading', { name: '정규 시간표 및 정기 휴일', exact: true })).toBeFocused()
  expect(deleteRequest).toEqual({ expectedConfigVersion: 7, reason: '겨울 운영 종료' })
  await expectNoHorizontalOverflow(page)

  await page.context().close()
})
}

function template(active: boolean) {
  return {
    templateId: 1,
    dayOfWeek: 'TUESDAY',
    startTime: '09:00:00',
    endTime: '09:45:00',
    totalCapacity: 8,
    roundArenaCapacity: 4,
    classCapacities: { ROUND_BEGINNER: 3 },
    active,
    version: active ? 0 : 1,
  }
}

test('휴일_Command와_동기화_실패_재시도는_실제_서버_응답으로_갱신한다', async ({ browser }) => {
  const page = await createAuthenticatedPage(browser, 'e2e-schedule-redesign-admin', 'ADMIN', { viewport: { width: 320, height: 900 } })
  const holiday = { holidayId: 2, dayOfWeek: 'MONDAY', effectiveFrom: '2026-01-01', effectiveTo: null, reason: '정기 휴무', active: true, version: 0 }
  const impact = { previous: { affectedDateCount: 0, templateTimeSlotCount: 0, activeReservationCount: 0 }, current: { affectedDateCount: 13, templateTimeSlotCount: 91, activeReservationCount: 2 }, combined: { affectedDateCount: 13, templateTimeSlotCount: 91, activeReservationCount: 2 } }
  let sync = synchronization()
  let saved: Record<string, unknown> | undefined
  let activation: Record<string, unknown> | undefined
  let retry: Record<string, unknown> | undefined
  await page.route(`${WEB_ORIGIN}/api/admin/schedule-templates**`, (route) => route.fulfill({ json: [template(true)] }))
  await page.route(`${WEB_ORIGIN}/api/admin/recurring-holidays**`, async (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/preview')) { await route.fulfill({ json: impact }); return }
    if (route.request().method() !== 'GET') {
      const payload = route.request().postDataJSON() as Record<string, unknown>
      if (path.endsWith('/activation')) { activation = payload; holiday.active = Boolean(payload.active) }
      else { saved = payload; holiday.reason = String(payload.holidayReason) }
      await route.fulfill({ json: { holiday, pendingConfigVersion: 8, impact, synchronization: sync } })
      return
    }
    await route.fulfill({ json: [holiday] })
  })
  await page.route(`${WEB_ORIGIN}/api/admin/schedule-sync**`, async (route) => {
    await route.fulfill({ json: sync })
  })
  await page.route(`${WEB_ORIGIN}/api/admin/jobs/sync-schedule-occurrences/retry`, async (route) => {
    retry = route.request().postDataJSON() as Record<string, unknown>
    sync = { ...synchronization(), activeVersion: 23 }
    await route.fulfill({ json: { targetVersion: 23, createdCount: 0, updatedCount: 0, appliedDateCount: 93, skippedDateCount: 0, synchronization: sync } })
  })
  await navigateWithinApp(page, '/admin/schedule-configuration')
  await page.getByRole('tab', { name: '정기 휴일' }).click()
  await page.getByRole('region', { name: '정기 휴일' }).getByRole('button', { name: '수정' }).click()
  const editor = page.getByRole('dialog', { name: '정기 휴일 수정' })
  await expect(editor.getByLabel('활성 상태')).toHaveCount(0)
  await editor.getByLabel('휴무 사유').fill('시설 점검')
  await editor.getByLabel('변경 사유').fill('정기 점검 반영')
  await editor.getByRole('button', { name: '영향 미리보기' }).click()
  const preview = page.getByRole('dialog', { name: '정기 휴일 변경 영향 확인' })
  await expect(preview).toContainText('자동 시간대')
  await expect(preview).not.toContainText('기존 시간대')
  const confirm = preview.getByRole('button', { name: '변경 확정' })
  await confirm.focus()
  await confirm.press('Tab')
  await expect(preview.getByRole('button', { name: '취소' })).toBeFocused()
  await preview.getByRole('button', { name: '취소' }).press('Shift+Tab')
  await expect(confirm).toBeFocused()
  await confirm.click()
  await expect(page.getByRole('heading', { name: '월요일 · 시설 점검' })).toBeVisible()
  expect(saved).toMatchObject({ holidayReason: '시설 점검', changeReason: '정기 점검 반영', expectedConfigVersion: 7 })
  expect(saved).not.toHaveProperty('active')
  await page.getByRole('button', { name: '비활성화' }).click()
  const activateDialog = page.getByRole('dialog', { name: '정기 휴일 비활성화 영향 확인' })
  await activateDialog.getByLabel('비활성화 사유').fill('휴무 해제')
  await activateDialog.getByRole('button', { name: '변경 확정' }).click()
  await expect(page.getByRole('button', { name: '활성화', exact: true })).toBeVisible()
  expect(activation).toEqual({ active: false, reason: '휴무 해제', expectedConfigVersion: 7 })

  sync = { ...synchronization(), status: 'SYNCING', pendingVersion: 23, lastFailedAt: '2026-10-01T08:00:00+09:00', lastFailureCode: 'SCHEDULE_SYNC_FAILED', lastFailureSummary: '동기화 연결 실패' } as unknown as ReturnType<typeof synchronization>
  await page.getByRole('button', { name: '활성화', exact: true }).click()
  const lockedDialog = page.getByRole('dialog', { name: '정기 휴일 활성화 영향 확인' })
  await lockedDialog.getByLabel('활성화 사유').fill('휴무 재개')
  await lockedDialog.getByRole('button', { name: '변경 확정' }).click()
  await expect(lockedDialog.getByRole('button', { name: '변경 확정' })).toBeDisabled()
  await lockedDialog.getByRole('button', { name: '취소' }).click()
  await expect(page.getByText('동기화 중 · 재시도 필요')).toBeVisible()
  await expect(page.getByRole('button', { name: '새 정규 시간표' })).toBeDisabled()
  await page.getByRole('button', { name: '동기화 재시도' }).click()
  await expect(page.getByText('시간표 최신 상태')).toBeVisible()
  expect(retry).toEqual({ pendingVersion: 23 })
  await expectNoHorizontalOverflow(page)
  await page.context().close()
})

function futureReservations() {
  return {
    templateId: 1,
    reservationCount: 1,
    reservations: [{
      reservationId: 77,
      lessonDate: '2026-10-06',
      startTime: '09:00:00',
      endTime: '09:45:00',
      memberId: 7,
      memberName: '김정리',
      memberPhone: '010-1234-5678',
      ridingClass: 'ROUND_BEGINNER',
      status: 'confirmed',
    }],
  }
}

function synchronization() {
  return {
    status: 'ACTIVE',
    activeVersion: 7,
    pendingVersion: null,
    horizonStart: '2026-09-23',
    horizonEnd: '2026-12-23',
    totalDateCount: 92,
    appliedDateCount: 92,
    remainingDateCount: 0,
    progressPercent: 100,
    syncStartedAt: null,
    lastCompletedAt: null,
    lastFailedAt: null,
    lastFailureCode: null,
    lastFailureSummary: null,
    longRunning: false,
  }
}

function mutationResponse() {
  return {
    template: template(false),
    pendingConfigVersion: 8,
    impact: { affectedDateCount: 13, existingTimeSlotCount: 12, activeReservationCount: 1 },
    synchronization: { ...synchronization(), status: 'SYNCING', pendingVersion: 8 },
  }
}

async function expectNoHorizontalOverflow(page: import('@playwright/test').Page) {
  const dimensions = await page.evaluate(() => ({
    viewport: window.innerWidth,
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth,
  }))
  expect(dimensions.document).toBeLessThanOrEqual(dimensions.viewport)
  expect(dimensions.body).toBeLessThanOrEqual(dimensions.viewport)
}
