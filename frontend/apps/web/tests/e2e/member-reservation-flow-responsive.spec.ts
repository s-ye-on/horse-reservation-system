import { expect, test, type Page, type Route } from '@playwright/test'
import { createAuthenticatedPage, navigateWithinApp } from './e2e-support'

const classesResponse = {
  currentGeneralGrade: 'ROUND_BEGINNER',
  progressionValue: 2,
  progressionClass: 'ROUND_BEGINNER',
  effectiveClass: 'ROUND_BEGINNER',
  dressageApproved: false,
  jumpingApproved: false,
  canUseLargeArena: false,
  availableRidingClasses: ['ROUND_BEGINNER', 'FIRST_RIDE'],
}

test('회원은_반응형_예약_화면에서_수업을_선택하고_신청한다', async ({ browser }) => {
  const page = await createAuthenticatedPage(
    browser,
    'e2e-member-reservation-redesign',
    'MEMBER',
    { viewport: { width: 1280, height: 900 } },
  )
  await mockClasses(page)
  await page.route('**/api/timeslots**', async (route) => {
    const date = new URL(route.request().url()).searchParams.get('date') ?? '2026-09-28'
    await fulfillJson(route, {
      date,
      classType: 'ROUND_BEGINNER',
      timeSlots: [
        { timeSlotId: 501, lessonDate: date, startTime: '09:00:00', closed: false, reservable: true, remainingCapacity: 2, unavailableReason: null },
        { timeSlotId: 502, lessonDate: date, startTime: '11:00:00', closed: false, reservable: false, remainingCapacity: 0, unavailableReason: 'FULL' },
      ],
    })
  })
  await page.route('**/api/reservations', async (route) => {
    if (route.request().method() !== 'POST') {
      await route.fallback()
      return
    }
    const body = route.request().postDataJSON() as { timeSlotId: number; classType: string }
    await fulfillJson(route, {
      reservationId: 10482,
      classType: body.classType,
      lessonDate: new URL(page.url()).searchParams.get('date'),
      startTime: '09:00:00',
      status: 'pending_admin_approval',
      paymentSource: 'coupon',
      coupon: { couponId: 44, expiresAt: null, remainingCount: 6, heldCount: 1, availableCount: 5 },
      paymentDueAt: null,
    })
  })

  await navigateWithinApp(page, '/')
  await navigateWithinApp(page, '/reservations')

  await expect(page.getByRole('heading', { name: '수업 예약' })).toBeVisible()
  await expect(page.getByRole('radio', { name: /09:00/ })).toBeEnabled()
  await expect(page.getByRole('radio', { name: /11:00/ })).toBeDisabled()
  await expect(page.getByRole('complementary', { name: '예약 요약' })).toHaveCSS('position', 'sticky')

  await page.getByRole('radio', { name: /09:00/ }).check()
  const confirmationLink = page.getByRole('link', { name: '예약 내용 확인' })
  await expect(confirmationLink).toBeVisible()

  await page.setViewportSize({ width: 960, height: 900 })
  await expect(page.getByRole('complementary', { name: '예약 요약' })).toHaveCSS('position', 'static')

  await page.setViewportSize({ width: 320, height: 800 })
  await expectNoHorizontalOverflow(page)
  await expect(confirmationLink).toHaveCSS('min-height', '48px')
  await confirmationLink.click()

  await expect(page).toHaveURL(/\/reservations\/new\?timeSlotId=501/)
  await expect(page.getByRole('heading', { name: '예약 신청 확인' })).toBeVisible()
  await expect(page.getByText('잔여석').locator('..')).toContainText('2자리')
  await expectNoHorizontalOverflow(page)

  await page.getByRole('button', { name: '예약 신청' }).click()
  await expect(page.getByText('신청 완료')).toBeVisible()
  await expect(page.getByText('예약 번호 10482 · 관리자 승인대기')).toBeVisible()
  await expect(page.getByText('예약 처리 중 횟수').locator('..')).toContainText('1회')
  await expect(page.getByText(/pending_admin_approval/)).toHaveCount(0)

  await page.unrouteAll({ behavior: 'ignoreErrors' })
  await page.context().close()
})

test('회원_예약_화면은_만석과_시간_없음_상태에서_다음_단계를_막는다', async ({ browser }) => {
  const page = await createAuthenticatedPage(browser, 'e2e-member-reservation-empty', 'MEMBER')
  await mockClasses(page)
  await page.route('**/api/timeslots**', async (route) => {
    const url = new URL(route.request().url())
    const date = url.searchParams.get('date') ?? '2026-09-28'
    const classType = url.searchParams.get('classType') ?? 'ROUND_BEGINNER'
    await fulfillJson(route, {
      date,
      classType,
      timeSlots: classType === 'FIRST_RIDE' ? [] : [
        { timeSlotId: 601, lessonDate: date, startTime: '14:00:00', closed: false, reservable: false, remainingCapacity: 0, unavailableReason: 'FULL' },
      ],
    })
  })

  await navigateWithinApp(page, '/')
  await navigateWithinApp(page, '/reservations')

  await expect(page.getByRole('radio', { name: /14:00/ })).toBeDisabled()
  await expect(page.getByRole('button', { name: '수업 시간을 선택해 주세요' })).toBeDisabled()

  await page.getByRole('radio', { name: /왕초보/ }).check()
  await expect(page.getByText('이 날짜에는 등록된 수업 시간이 없습니다.')).toBeVisible()
  await expect(page.getByRole('button', { name: '수업 시간을 선택해 주세요' })).toBeDisabled()

  await page.unrouteAll({ behavior: 'ignoreErrors' })
  await page.context().close()
})

async function mockClasses(page: Page) {
  await page.route('**/api/me/eligible-classes', async (route) => fulfillJson(route, classesResponse))
}

async function fulfillJson(route: Route, body: unknown) {
  await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })
}

async function expectNoHorizontalOverflow(page: Page) {
  const dimensions = await page.evaluate(() => ({
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth,
    viewport: window.innerWidth,
  }))
  expect(dimensions.document).toBeLessThanOrEqual(dimensions.viewport)
  expect(dimensions.body).toBeLessThanOrEqual(dimensions.viewport)
}
