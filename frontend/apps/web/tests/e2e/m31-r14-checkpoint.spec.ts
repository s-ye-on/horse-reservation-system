import { expect, test } from '@playwright/test'
import {
  cleanupM31R14ClosureFixture,
  cleanupM31R14ScheduleConfigurationFixture,
  createAuthenticatedPage,
  markM31R14ScheduleSynchronizationFailed,
  navigateWithinApp,
  prepareM31R14ClosureFixture,
  prepareM31R14ScheduleConfigurationFixture,
  readM31R14ScheduleSynchronizationSnapshot,
  refreshWithinApp,
  type M31R14ClosureFixture,
} from './e2e-support'

const CONFIG_ADMIN_SUBJECT = 'e2e-m31-r14-config-admin'
const CLOSURE_ADMIN_SUBJECT = 'e2e-m31-r14-closure-admin'
const DEVICE_CONTEXT = {
  viewport: { width: 320, height: 900 },
  timezoneId: 'America/Los_Angeles',
}

test.describe.configure({ mode: 'serial' })

test.describe('M31-R14 Checkpoint 1 핵심 운영 흐름', () => {
  test('정규_시간표는_영향_확인_후_반영하고_실패한_동기화를_같은_version으로_재시도한다', async ({ browser }) => {
    test.setTimeout(120_000)
    const fixture = prepareM31R14ScheduleConfigurationFixture()

    const page = await createAuthenticatedPage(browser, CONFIG_ADMIN_SUBJECT, 'ADMIN', DEVICE_CONTEXT)
    try {
      await navigateWithinApp(page, '/admin/schedule-configuration')
      await expect(page.getByRole('heading', { name: '정규 시간표 및 정기 휴일' })).toBeVisible()
      await expect(page.getByText('시간표 최신 상태')).toBeVisible()

      const templateForm = page.getByRole('group', { name: '정규 시간표 생성' })
      await templateForm.getByLabel('요일').selectOption('SUNDAY')
      await templateForm.getByLabel('시작 시간').fill('18:00')
      await expect(templateForm.getByLabel('종료 시간')).toHaveValue('18:45')
      await templateForm.getByLabel('변경 사유').fill('Checkpoint 1 실제 E2E')
      await templateForm.getByRole('button', { name: '영향 미리보기' }).click()

      const preview = page.getByRole('dialog', { name: '정규 시간표 생성 영향 확인' })
      await expect(preview).toBeVisible()
      await expect(preview.getByText('영향 날짜')).toBeVisible()
      await preview.getByRole('button', { name: '변경 확정' }).click()

      await expect(page.getByText(/정규 시간표 생성 요청을 반영했습니다/)).toBeVisible()
      await expect(page.getByText('시간표 갱신 중')).toBeVisible()

      const pending = readM31R14ScheduleSynchronizationSnapshot()
      expect(pending.status).toBe('SYNCING')
      expect(pending.pendingVersion).toBeGreaterThan(pending.activeVersion)
      fixture.appliedVersion = pending.pendingVersion
      markM31R14ScheduleSynchronizationFailed()
      await refreshWithinApp(page)
      await expect(page.getByText(/E2E_FORCED_FAILURE/)).toBeVisible()
      const retryResponsePromise = page.waitForResponse((response) =>
        response.url().endsWith('/api/admin/jobs/sync-schedule-occurrences/retry')
        && response.request().method() === 'POST')
      await page.getByRole('button', { name: '동기화 재시도' }).click()
      const retryResponse = await retryResponsePromise
      expect(retryResponse.request().postDataJSON()).toEqual({
        pendingVersion: pending.pendingVersion,
      })
      expect(await retryResponse.json()).toMatchObject({
        targetVersion: pending.pendingVersion,
        synchronization: {
          status: 'ACTIVE',
          activeVersion: pending.pendingVersion,
        },
      })

      await expect(page.getByText(/동일한 설정 버전의 동기화를 다시 실행했습니다/)).toBeVisible()
      await expect(page.getByText('시간표 최신 상태')).toBeVisible()
      await expect(page.getByRole('heading', { name: '일요일 18:00~18:45' })).toBeVisible()
      const weeklyOverview = page.getByRole('region', { name: '주간 정규 시간표 상세' })
      const createdTemplate = weeklyOverview.getByLabel('일요일 18:00 정규 시간표')
      await expect(createdTemplate).toContainText('전체 8명 · 원형 4명')
      await expect(createdTemplate.locator('[data-class-capacity="FIRST_RIDE"]')).toContainText('왕초보0명')
      await expect(createdTemplate.locator('[data-class-capacity="JUMPING"]')).toContainText('장애물0명')
      expect(readM31R14ScheduleSynchronizationSnapshot()).toEqual({
        status: 'ACTIVE',
        activeVersion: pending.pendingVersion,
        pendingVersion: undefined,
      })
      await expectNoHorizontalOverflow(page)
    } finally {
      await page.context().close().catch(() => undefined)
      cleanupM31R14ScheduleConfigurationFixture(fixture)
    }
  })

  test('날짜와_TimeSlot_휴무는_고정_영향을_개별_정리하고_완료_철회_재개한다', async ({ browser }) => {
    test.setTimeout(120_000)
    const fixture = prepareM31R14ClosureFixture()
    const page = await createAuthenticatedPage(browser, CLOSURE_ADMIN_SUBJECT, 'ADMIN', DEVICE_CONTEXT)

    try {
      await navigateWithinApp(page, '/admin/schedule-closures')
      await closeDateWithReservation(page, fixture)
      await closeDateWithoutReservation(page, fixture.emptyClosureDate)
      await completeAndReopenTimeSlotClosure(page, fixture)
      await withdrawEmptyTimeSlotClosure(page, fixture.timeSlotWithdrawId)
      await expectNoHorizontalOverflow(page)
    } finally {
      await page.context().close().catch(() => undefined)
      cleanupM31R14ClosureFixture()
    }
  })
})

async function closeDateWithReservation(
  page: import('@playwright/test').Page,
  fixture: M31R14ClosureFixture,
) {
  const datePanel = page.getByRole('tabpanel', { name: '날짜 전체 휴무' })
  await datePanel.getByLabel('대상 날짜').fill(fixture.dateClosureDate)
  await expect(datePanel.getByRole('heading', { name: /R14 날짜 회원/ })).toBeVisible()
  await datePanel.getByLabel('운영 사유').fill('Checkpoint 날짜 휴무')
  await datePanel.getByRole('button', { name: '날짜 휴무 시작' }).click()
  await page.getByRole('dialog', { name: '날짜 휴무 처리 시작' })
    .getByRole('button', { name: '날짜 휴무 시작' }).click()

  await expect(page.getByText(/날짜 휴무를 시작했습니다/)).toBeVisible()
  const reservation = datePanel.locator('article').filter({ hasText: 'R14 날짜 회원' })
  await reservation.locator('summary').click()
  await reservation.getByLabel('관리자 메모').fill('고객 연락 후 날짜 휴무')
  await reservation.getByRole('button', { name: '예약 한 건 취소' }).click()
  const cancellationResponse = page.waitForResponse((response) =>
    response.url().includes('/cancel-for-closure')
    && response.request().method() === 'POST')
  await page.getByRole('dialog', { name: /예약 번호.*취소 확인/ })
    .getByRole('button', { name: '예약 한 건 취소' }).click()
  expect(await (await cancellationResponse).json()).toMatchObject({
    changed: true,
    couponAction: 'none',
  })
  await expect(reservation).toHaveCount(0)

  await datePanel.getByRole('button', { name: '휴무 완료' }).click()
  await page.getByRole('dialog', { name: '날짜 휴무 완료' })
    .getByRole('button', { name: '휴무 완료' }).click()
  await expect(page.getByText(/날짜 휴무가 완료되었습니다/)).toBeVisible()
  await expect(datePanel.locator('[data-status="CLOSED"]')).toBeVisible()
}

async function closeDateWithoutReservation(
  page: import('@playwright/test').Page,
  emptyClosureDate: string,
) {
  const datePanel = page.getByRole('tabpanel', { name: '날짜 전체 휴무' })
  await datePanel.getByLabel('대상 날짜').fill(emptyClosureDate)
  await expect(datePanel.getByText('0건', { exact: true }).first()).toBeVisible()
  await datePanel.getByLabel('운영 사유').fill('예약 없는 날짜 휴무')
  await datePanel.getByRole('button', { name: '날짜 휴무 시작' }).click()
  await page.getByRole('dialog', { name: '날짜 휴무 처리 시작' })
    .getByRole('button', { name: '날짜 휴무 시작' }).click()

  await expect(page.getByText(/날짜 휴무가 완료되었습니다/)).toBeVisible()
  await expect(datePanel.locator('[data-status="CLOSED"]')).toBeVisible()
}

async function completeAndReopenTimeSlotClosure(
  page: import('@playwright/test').Page,
  fixture: M31R14ClosureFixture,
) {
  await page.getByRole('tab', { name: '개별 수업 휴강' }).click()
  const slotPanel = page.getByRole('tabpanel', { name: '개별 수업 휴강' })
  await slotPanel.getByLabel('대상 수업 시간').selectOption(String(fixture.timeSlotClosureId))
  await expect(slotPanel.getByText('아직 영향 예약이 확정되지 않았습니다. 휴강 시작 후 확인할 수 있습니다.')).toBeVisible()
  await expect(slotPanel.getByLabel('대상 수업 시간').locator('option:checked'))
    .toContainText(formatSeoulDate(fixture.timeSlotClosureDate))
  await slotPanel.getByLabel('운영 사유').fill('Checkpoint 개별 휴강')
  await slotPanel.getByRole('button', { name: '휴강 시작 검토' }).click()
  await page.getByRole('dialog', { name: '개별 수업 시간 휴강 시작' })
    .getByRole('button', { name: '휴강 시작' }).click()

  await expect(page.getByText(/개별 휴강을 시작하고 영향 예약 목록을 고정했습니다/)).toBeVisible()
  const reservation = slotPanel.locator('article').filter({ hasText: 'R14 휴강 회원' })
  await expect(reservation).toContainText(`예약 번호 ${fixture.timeSlotClosureReservationId}`)
  await reservation.locator('summary').click()
  await reservation.getByLabel('관리자 메모').fill('고객 연락 후 개별 휴강')
  await reservation.getByRole('button', { name: '예약 한 건 취소' }).click()
  await page.getByRole('dialog', { name: /예약 번호.*취소 확인/ })
    .getByRole('button', { name: '예약 한 건 취소' }).click()
  await expect(reservation.getByText('취소 처리 완료 · 별도 쿠폰 처리가 없습니다.')).toBeVisible()

  await slotPanel.getByRole('button', { name: '휴강 완료' }).click()
  await page.getByRole('dialog', { name: '개별 휴강 완료' })
    .getByRole('button', { name: '휴강 완료' }).click()
  await expect(page.getByText(/휴강을 완료했습니다/)).toBeVisible()

  await slotPanel.getByRole('button', { name: '관리자 휴강 해제' }).click()
  await page.getByRole('dialog', { name: '관리자 휴강 설정 해제' })
    .getByRole('button', { name: '관리자 휴강 해제' }).click()
  await expect(page.getByText(/기존 예약은 복구되지 않습니다/)).toBeVisible()
  await expect(slotPanel.getByRole('heading', { name: '관리자 휴강 해제' })).toBeVisible()
}

async function withdrawEmptyTimeSlotClosure(
  page: import('@playwright/test').Page,
  timeSlotId: number,
) {
  const slotPanel = page.getByRole('tabpanel', { name: '개별 수업 휴강' })
  await slotPanel.getByLabel('대상 수업 시간').selectOption(String(timeSlotId))
  await expect(slotPanel.getByText('아직 영향 예약이 확정되지 않았습니다. 휴강 시작 후 확인할 수 있습니다.')).toBeVisible()
  await slotPanel.getByLabel('운영 사유').fill('Checkpoint 철회 검증')
  await slotPanel.getByRole('button', { name: '휴강 시작 검토' }).click()
  await page.getByRole('dialog', { name: '개별 수업 시간 휴강 시작' })
    .getByRole('button', { name: '휴강 시작' }).click()
  await expect(page.getByText(/개별 휴강을 시작하고 영향 예약 목록을 고정했습니다/)).toBeVisible()

  await slotPanel.getByRole('button', { name: '휴강 철회' }).click()
  await page.getByRole('dialog', { name: '개별 휴강 철회' })
    .getByRole('button', { name: '휴강 철회' }).click()
  await expect(page.getByText(/휴강을 철회했습니다/)).toBeVisible()
  await expect(slotPanel.locator('[data-status="WITHDRAWN"]')).toBeVisible()
}

function formatSeoulDate(dateKey: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'long',
    timeZone: 'Asia/Seoul',
  }).format(new Date(`${dateKey}T00:00:00.000Z`))
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
