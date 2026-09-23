import { expect, test } from '@playwright/test'
import { createAuthenticatedPage, navigateWithinApp } from './e2e-support'

const WEB_ORIGIN = process.env.HORSE_E2E_WEB_BASE_URL ?? 'http://localhost:5173'
const JSON_HEADERS = { 'Content-Type': 'application/json' }

test('정규_시간표_삭제와_예약_정리_동선은_320px에서_사용할_수_있다', async ({ browser }) => {
  let templateActive = true
  let deleteRequest: Record<string, unknown> | undefined
  const page = await createAuthenticatedPage(
    browser,
    'e2e-m34-07-schedule-admin',
    'ADMIN',
    { viewport: { width: 320, height: 900 } },
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
  await page.getByLabel('운영 변경 사유').fill('겨울 운영 종료')
  await templateSection.getByRole('button', { name: '삭제' }).click()

  const dialog = page.getByRole('dialog', { name: '정규 시간표 삭제 영향 확인' })
  await expect(dialog).toContainText('정리할 예약')
  await expect(dialog).toContainText('1건')
  await dialog.getByRole('button', { name: '삭제 확정' }).click()

  await expect(page.getByRole('region', { name: '예약 정리 필요' })).toBeVisible()
  await expect(page.getByText('김정리')).toBeVisible()
  await expect(page.getByRole('link', { name: '예약 확인' })).toHaveAttribute('href', '/admin/reservations?reservationId=77')
  await expect(templateSection.getByRole('heading', { name: '화요일 09:00~09:45' })).toHaveCount(0)
  expect(deleteRequest).toEqual({ expectedConfigVersion: 7, reason: '겨울 운영 종료' })
  await expectNoHorizontalOverflow(page)

  await page.context().close()
})

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
