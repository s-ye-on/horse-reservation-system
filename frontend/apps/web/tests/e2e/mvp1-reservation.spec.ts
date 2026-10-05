import { expect, test, type Page } from '@playwright/test'
import {
  cleanupFixture,
  createAuthenticatedPage,
  makeMvpOneCouponReservationAttendable,
  navigateWithinApp,
  prepareMvpOneFixture,
  refreshWithinApp,
  type MemberFixture,
  type MvpOneFixture,
} from './e2e-support'

test.describe.configure({ mode: 'serial' })

test.describe('MVP 1 예약 핵심 흐름', () => {
  let fixture: MvpOneFixture

  test.beforeAll(() => {
    fixture = prepareMvpOneFixture()
  })

  test.afterAll(() => {
    if (fixture) cleanupFixture(fixture)
  })

  test('쿠폰_회원은_예약_승인과_수업_완료_결과를_한_번만_확인한다', async ({ browser }) => {
    const member = fixture.couponMember
    const memberPage = await createAuthenticatedPage(browser, member.authSubject, 'MEMBER')
    const adminPage = await createAuthenticatedPage(browser, 'e2e-m1-29-admin', 'ADMIN')

    await applyForReservation(memberPage, member)
    await expect(memberPage.getByText(/관리자 승인대기/)).toBeVisible()
    await expect(memberPage.getByText('예약 처리 중 횟수').locator('..')).toContainText('1회')

    await confirmReservation(adminPage, member.name, '쿠폰 예약 확정')

    await navigateWithinApp(memberPage, '/my/reservations')
    const confirmedReservation = reservationCard(memberPage, '원형초보')
    await expect(confirmedReservation.getByText('예약 확정', { exact: true })).toBeVisible()
    await expect(confirmedReservation.getByText(`사용 예정 쿠폰 #${member.couponId}`)).toBeVisible()

    makeMvpOneCouponReservationAttendable(fixture)
    await navigateWithinApp(adminPage, '/admin/attendance')
    const attendanceCard = adminPage.locator('article').filter({ hasText: member.name })
    await attendanceCard.getByRole('button', { name: '수업 완료' }).click()
    const completionResponse = adminPage.waitForResponse((response) =>
      response.url().endsWith('/complete') && response.request().method() === 'POST')
    await attendanceCard.getByRole('button', { name: '완료 처리 확인' }).click()
    const completionBody = await (await completionResponse).json()
    expect(completionBody).toMatchObject({ status: 'completed', generalRideCount: 2, couponId: member.couponId })
    await expect(adminPage.getByText('수업 완료 반영')).toBeVisible()
    await expect(adminPage.getByText('일반 2회')).toBeVisible()

    await navigateWithinApp(memberPage, '/my/reservations')
    await memberPage.getByRole('tab', { name: /지난 예약/ }).click()
    const completedReservation = reservationCard(memberPage, '원형초보')
    await expect(completedReservation.getByText('수업 완료', { exact: true })).toBeVisible()

    await navigateWithinApp(memberPage, '/my/coupons')
    const couponCard = memberPage.locator('article').filter({ hasText: `COUPON #${member.couponId}` })
    await expect(couponCard.getByText('잔여').locator('..')).toContainText('9')
    await expect(memberPage.getByText('수업 완료 사용')).toHaveCount(1)

    await navigateWithinApp(adminPage, '/admin/members')
    await adminPage.getByRole('button', { name: new RegExp(member.name) }).click()
    const memberDetail = adminPage.getByRole('region', { name: '회원 상세' })
    await expect(memberDetail.getByText('일반 기승', { exact: true, selector: 'dt' }).locator('..'))
      .toContainText('2회')

    await memberPage.context().close()
    await adminPage.context().close()
  })

  test('쿠폰이_없는_회원은_입금대기에서_관리자_확정으로_전환된다', async ({ browser }) => {
    const member = fixture.singlePaymentMember
    const memberPage = await createAuthenticatedPage(browser, member.authSubject, 'MEMBER')
    const adminPage = await createAuthenticatedPage(browser, 'e2e-m1-29-admin', 'ADMIN')

    await applyForReservation(memberPage, member)
    await expect(memberPage.getByText(/입금 확인 대기/)).toBeVisible()
    await expect(memberPage.getByText('안내된 기한까지 입금해 주세요')).toBeVisible()

    await navigateWithinApp(memberPage, '/my/reservations')
    await expect(reservationCard(memberPage, '원형초보').getByText('입금 확인 대기')).toBeVisible()

    await confirmReservation(adminPage, member.name, '입금 확인 및 확정')

    await refreshWithinApp(memberPage)
    await expect(reservationCard(memberPage, '원형초보').getByText('예약 확정', { exact: true })).toBeVisible()

    await memberPage.context().close()
    await adminPage.context().close()
  })
})

async function applyForReservation(page: Page, member: MemberFixture) {
  const query = new URLSearchParams({
    timeSlotId: String(member.timeSlotId),
    classType: 'ROUND_BEGINNER',
    date: member.lessonDate,
  })
  await navigateWithinApp(page, `/reservations/new?${query}`)
  await expect(page.getByRole('heading', { name: '예약 신청 확인' })).toBeVisible()
  await page.getByRole('button', { name: '예약 신청' }).click()
  await expect(page.getByText('신청 완료')).toBeVisible()
}

async function confirmReservation(page: Page, memberName: string, actionName: string) {
  await navigateWithinApp(page, '/admin/reservations')
  const card = page.locator('article').filter({ hasText: memberName })
  await expect(card).toBeVisible()
  await card.getByRole('button', { name: actionName }).click()
  const confirmResponse = page.waitForResponse((response) =>
    response.url().endsWith('/confirm') && response.request().method() === 'POST')
  await page.getByRole('dialog', { name: '예약 확정' }).getByRole('button', { name: '예약 확정 확인' }).click()
  const body = await (await confirmResponse).json()
  expect(body.status).toBe('confirmed')
  await expect(card.getByRole('button', { name: actionName })).toHaveCount(0)
  await expect(card.getByRole('button', { name: '시간 변경' })).toBeVisible()
  await expect(card.getByRole('button', { name: '예약 취소' })).toBeVisible()
}

function reservationCard(page: Page, className: string) {
  return page.locator('article').filter({ hasText: className })
}
