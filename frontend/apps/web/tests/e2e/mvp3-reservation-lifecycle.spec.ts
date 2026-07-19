import { expect, test } from '@playwright/test'
import {
  cleanupReservationLifecycleFixture,
  createAuthenticatedPage,
  prepareReservationLifecycleFixture,
  type ReservationLifecycleFixture,
} from './e2e-support'

test.describe.configure({ mode: 'serial' })

test.describe('MVP 3 시간 기반 예약 생명주기', () => {
  let fixture: ReservationLifecycleFixture

  test.beforeAll(() => {
    fixture = prepareReservationLifecycleFixture()
  })

  test.afterAll(() => {
    cleanupReservationLifecycleFixture()
  })

  test('지난_승인대기는_만료되고_회원은_예정과_지난_예약을_구분한다', async ({ browser }) => {
    const adminPage = await createAuthenticatedPage(browser, 'e2e-m3-18-admin', 'ADMIN')
    await adminPage.goto('/admin/dashboard')
    const expiryResponse = await adminPage.evaluate(async () => {
      const response = await fetch('http://localhost:8080/api/admin/jobs/expire-pending-approvals', {
        method: 'POST',
      })
      return { status: response.status, body: await response.json() }
    })
    expect(expiryResponse.status).toBe(200)

    const memberPage = await createAuthenticatedPage(browser, fixture.memberSubject, 'MEMBER')
    await memberPage.goto('/my/reservations')

    await expect(memberPage.getByRole('tab', { name: /예정 예약 1/ })).toHaveAttribute('aria-selected', 'true')
    const upcomingCard = memberPage.locator('article').filter({ hasText: '원형초보' })
    await expect(upcomingCard.getByText('관리자 승인대기')).toBeVisible()
    await expect(upcomingCard.getByRole('link', { name: '예약 변경' })).toBeVisible()
    await expect(upcomingCard.getByRole('link', { name: '취소하기' })).toBeVisible()

    await memberPage.getByRole('tab', { name: /지난 예약 1/ }).click()
    const expiredCard = memberPage.locator('article').filter({ hasText: '원형 속보' })
    await expect(expiredCard.getByText('승인 기한 만료')).toBeVisible()
    await expect(expiredCard.getByRole('link', { name: '예약 변경' })).toHaveCount(0)
    await expect(expiredCard.getByRole('link', { name: '취소하기' })).toHaveCount(0)

    await memberPage.context().close()
    await adminPage.context().close()
  })

  test('관리자_출석은_지난_미처리만_실행하고_시작_전_수업은_차단한다', async ({ browser }) => {
    const page = await createAuthenticatedPage(browser, 'e2e-m3-18-admin', 'ADMIN')
    await page.goto('/admin/attendance')

    const overdueSection = page.getByRole('region', { name: '지난 미처리' })
    const overdueCard = overdueSection.locator('article').filter({ hasText: fixture.overdueMemberName })
    await expect(overdueCard.getByRole('button', { name: '수업 완료' })).toBeVisible()
    await expect(overdueCard.getByRole('button', { name: '노쇼 처리' })).toBeVisible()

    const upcomingSection = page.getByRole('region', { name: '시작 전 수업' })
    const upcomingCard = upcomingSection.locator('article').filter({ hasText: fixture.upcomingMemberName })
    await expect(upcomingCard.getByText('수업 시작 전에는 완료 또는 노쇼 처리할 수 없습니다.')).toBeVisible()
    await expect(upcomingCard.getByRole('button', { name: '수업 완료' })).toHaveCount(0)
    await expect(upcomingCard.getByRole('button', { name: '노쇼 처리' })).toHaveCount(0)

    await expect(page.getByLabel('수업 날짜')).toHaveValue(fixture.pastLessonDate)
    await expect(page.getByLabel('수업 날짜').getByRole('option', { name: fixture.futureLessonDate })).toHaveCount(0)
    await page.context().close()
  })
})
