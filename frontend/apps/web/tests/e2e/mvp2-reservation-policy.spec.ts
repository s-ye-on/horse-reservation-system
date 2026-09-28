import { expect, test, type Page } from '@playwright/test'
import {
  cleanupMvpTwoFixture,
  createAuthenticatedPage,
  navigateWithinApp,
  prepareMvpTwoFixture,
  readReservationPolicySnapshot,
  type MvpTwoFixture,
  type ReservationPolicyFixture,
} from './e2e-support'

test.describe.configure({ mode: 'serial' })

test.describe('MVP 2 예약 변경·취소 정책 행렬', () => {
  let fixture: MvpTwoFixture

  test.beforeAll(() => {
    fixture = prepareMvpTwoFixture()
  })

  test.afterAll(() => {
    if (fixture) cleanupMvpTwoFixture(fixture.sameDayChange.lessonDate)
  })

  test('마감_전_변경은_쿠폰과_무료_변경권을_그대로_유지한다', async ({ browser }) => {
    const policyCase = fixture.beforeCutoffChange
    const page = await createAuthenticatedPage(browser, policyCase.authSubject, 'MEMBER')

    await openChangePreview(page, policyCase)
    await expect(page.getByText('변경 마감 전', { exact: true })).toBeVisible()
    await expect(page.getByText('처리 없음', { exact: true })).toBeVisible()
    await expect(page.getByText('사용 안 함', { exact: true })).toBeVisible()
    await executeMemberChange(page, policyCase)

    const snapshot = readReservationPolicySnapshot(policyCase.reservationId)
    expect(snapshot).toMatchObject({
      status: 'confirmed',
      lessonDate: policyCase.targetLessonDate,
      startTime: policyCase.targetStartTime,
      remainingCount: 10,
      heldCount: 1,
      freeChangeUsed: false,
      freeChangeLogCount: 0,
      changeLogCount: 1,
    })
    await page.context().close()
  })

  test('마감_후_날짜_변경은_쿠폰당_무료_변경권을_한_번_사용한다', async ({ browser }) => {
    const policyCase = fixture.afterCutoffChange
    const page = await createAuthenticatedPage(browser, policyCase.authSubject, 'MEMBER')

    await openChangePreview(page, policyCase)
    await expect(page.getByText('변경 마감 후', { exact: true })).toBeVisible()
    await expect(page.getByText('무료 변경권 사용', { exact: true })).toBeVisible()
    await expect(page.getByText('1회 사용', { exact: true })).toBeVisible()
    await executeMemberChange(page, policyCase)

    const snapshot = readReservationPolicySnapshot(policyCase.reservationId)
    expect(snapshot).toMatchObject({
      status: 'confirmed',
      lessonDate: policyCase.targetLessonDate,
      startTime: policyCase.targetStartTime,
      remainingCount: 10,
      heldCount: 1,
      freeChangeUsed: true,
      freeChangeLogCount: 1,
      changeLogCount: 1,
    })
    await page.context().close()
  })

  test('당일_시간_변경은_서울_요일에_따라_무료권_미사용_또는_거부된다', async ({ browser }) => {
    const policyCase = fixture.sameDayChange
    const page = await createAuthenticatedPage(browser, policyCase.authSubject, 'MEMBER')

    await navigateWithinApp(page, `/my/reservations/${policyCase.reservationId}/change`)
    const unavailable = page.getByRole('alert').filter({ hasText: '현재 상태에서는 예약을 변경할 수 없습니다.' })
    await page.locator('h1, [role="alert"]').first().waitFor()
    if (await unavailable.isVisible()) {
      expect(readReservationPolicySnapshot(policyCase.reservationId)).toMatchObject({
        lessonDate: policyCase.lessonDate,
        startTime: policyCase.startTime,
        freeChangeUsed: false,
        changeLogCount: 0,
      })
      await page.context().close()
      return
    }

    await expect(page.getByRole('heading', { name: '예약 변경' })).toBeVisible()
    await page.getByLabel('날짜').fill(policyCase.targetLessonDate as string)
    const target = changeTarget(page, policyCase)
    if (await target.count() === 0) {
      await expect(target).toHaveCount(0)
      expect(readReservationPolicySnapshot(policyCase.reservationId)).toMatchObject({
        lessonDate: policyCase.lessonDate,
        startTime: policyCase.startTime,
        freeChangeUsed: false,
        changeLogCount: 0,
      })
    } else if (fixture.todayIsWeekend) {
      await target.click()
      await expect(page.getByRole('alert')).toContainText('변경 정책상 처리할 수 없습니다')
      expect(readReservationPolicySnapshot(policyCase.reservationId)).toMatchObject({
        lessonDate: policyCase.lessonDate,
        startTime: policyCase.startTime,
        freeChangeUsed: false,
        changeLogCount: 0,
      })
    } else {
      await target.click()
      await expect(page.getByText('변경 마감 후', { exact: true })).toBeVisible()
      await expect(page.getByText('처리 없음', { exact: true })).toBeVisible()
      await expect(page.getByText('사용 안 함', { exact: true })).toBeVisible()
      await executeMemberChange(page, policyCase)
      expect(readReservationPolicySnapshot(policyCase.reservationId)).toMatchObject({
        lessonDate: policyCase.targetLessonDate,
        startTime: policyCase.targetStartTime,
        remainingCount: 10,
        heldCount: 1,
        freeChangeUsed: false,
        freeChangeLogCount: 0,
        changeLogCount: 1,
      })
    }
    await page.context().close()
  })

  test('회원_취소는_마감_전_반환하고_마감_후_차감한다', async ({ browser }) => {
    const beforePage = await createAuthenticatedPage(browser, fixture.beforeCutoffCancel.authSubject, 'MEMBER')
    await cancelByMember(beforePage, fixture.beforeCutoffCancel, '취소 마감 전', '쿠폰 반환')
    expect(readReservationPolicySnapshot(fixture.beforeCutoffCancel.reservationId)).toMatchObject({
      status: 'cancelled', remainingCount: 10, heldCount: 0, changeLogCount: 1,
    })
    await beforePage.context().close()

    const afterPage = await createAuthenticatedPage(browser, fixture.afterCutoffCancel.authSubject, 'MEMBER')
    await cancelByMember(afterPage, fixture.afterCutoffCancel, '취소 마감 후', '1회 차감')
    expect(readReservationPolicySnapshot(fixture.afterCutoffCancel.reservationId)).toMatchObject({
      status: 'cancelled', remainingCount: 9, heldCount: 0, changeLogCount: 1,
    })
    await afterPage.context().close()
  })

  test('관리자는_책임별_권장안을_확인하고_허용된_쿠폰_처리로_재정의한다', async ({ browser }) => {
    const policyCase = fixture.adminCancel
    const page = await createAuthenticatedPage(browser, 'e2e-m2-11-admin', 'ADMIN')
    await navigateWithinApp(page, '/admin/reservations')
    const card = page.locator('article').filter({ hasText: policyCase.name })
    await expect(card).toBeVisible()
    await card.getByRole('button', { name: '예약 취소' }).click()

    const responsibility = card.getByLabel('취소 책임')
    const recommendation = card.locator('.admin-reservation-recommendation')
    await expect(recommendation).toContainText('1회 차감')
    await responsibility.selectOption('stable')
    await expect(recommendation).toContainText('쿠폰 반환')
    await responsibility.selectOption('exception')
    await expect(recommendation).toContainText('쿠폰 반환')
    await responsibility.selectOption('member')
    await expect(recommendation).toContainText('1회 차감')
    await responsibility.selectOption('stable')
    await expect(recommendation).toContainText('쿠폰 반환')

    await card.getByLabel('최종 쿠폰 처리').selectOption('deduct')
    await expect(card.getByText('권장안과 다른 최종 처리를 선택했습니다.')).toBeVisible()
    await card.getByLabel('관리자 메모').fill('회원과 협의하여 차감 처리')
    const responsePromise = page.waitForResponse((response) =>
      response.url().endsWith(`/api/admin/reservations/${policyCase.reservationId}/cancel`)
      && response.request().method() === 'POST')
    await card.getByRole('button', { name: '예약 취소 확인' }).click()
    const response = await responsePromise
    expect(await response.json()).toMatchObject({ status: 'cancelled', responsibility: 'stable', couponAction: 'deduct' })
    await expect(card).toBeHidden()
    expect(readReservationPolicySnapshot(policyCase.reservationId)).toMatchObject({
      status: 'cancelled', remainingCount: 9, heldCount: 0, changeLogCount: 1,
    })
    await page.context().close()
  })
})

async function openChangePreview(page: Page, policyCase: ReservationPolicyFixture) {
  await openChangePage(page, policyCase)
  const target = changeTarget(page, policyCase)
  await expect(target).toBeVisible()
  await target.click()
}

async function openChangePage(page: Page, policyCase: ReservationPolicyFixture) {
  await navigateWithinApp(page, `/my/reservations/${policyCase.reservationId}/change`)
  await expect(page.getByRole('heading', { name: '예약 변경' })).toBeVisible()
  await page.getByLabel('날짜').fill(policyCase.targetLessonDate as string)
}

function changeTarget(page: Page, policyCase: ReservationPolicyFixture) {
  return page.locator('.reservation-change-times label').filter({
    hasText: (policyCase.targetStartTime as string).slice(0, 5),
  })
}

async function executeMemberChange(page: Page, policyCase: ReservationPolicyFixture) {
  const responsePromise = page.waitForResponse((response) =>
    response.url().endsWith(`/api/me/reservations/${policyCase.reservationId}/change`)
    && response.request().method() === 'POST')
  await page.getByRole('button', { name: '이 시간으로 변경' }).click()
  const response = await responsePromise
  expect(await response.json()).toMatchObject({
    reservationId: policyCase.reservationId,
    lessonDate: policyCase.targetLessonDate,
    startTime: policyCase.targetStartTime,
  })
  await expect(page.getByText('예약이 변경되었습니다')).toBeVisible()
}

async function cancelByMember(
  page: Page,
  policyCase: ReservationPolicyFixture,
  timing: string,
  couponAction: string,
) {
  await navigateWithinApp(page, `/my/reservations/${policyCase.reservationId}/cancel`)
  await expect(page.getByRole('heading', { name: '예약 취소' })).toBeVisible()
  await expect(page.getByText(timing, { exact: true })).toBeVisible()
  await expect(page.getByText(couponAction, { exact: true })).toBeVisible()
  await page.getByLabel('취소 사유').fill('E2E 정책 검증 취소')
  const responsePromise = page.waitForResponse((response) =>
    response.url().endsWith(`/api/me/reservations/${policyCase.reservationId}/cancel`)
    && response.request().method() === 'POST')
  await page.getByRole('button', { name: '예약 취소 확정' }).click()
  const response = await responsePromise
  expect(await response.json()).toMatchObject({ status: 'cancelled' })
  await expect(page.getByText('예약이 취소되었습니다')).toBeVisible()
}
