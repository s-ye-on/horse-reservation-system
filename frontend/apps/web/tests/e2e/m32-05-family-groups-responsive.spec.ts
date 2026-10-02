import { expect, test } from '@playwright/test'
import { createAuthenticatedPage, navigateWithinApp } from './e2e-support'

const WEB_ORIGIN = process.env.HORSE_E2E_WEB_BASE_URL ?? 'http://localhost:5173'
const JSON_HEADERS = { 'Content-Type': 'application/json' }

test('가족_그룹_관리는_320px에서_검색_구성원_감사를_조작할_수_있다', async ({ browser }) => {
  const page = await createAuthenticatedPage(
    browser,
    'e2e-m32-05-family-admin',
    'ADMIN',
    { viewport: { width: 320, height: 800 } },
  )

  await page.route(`${WEB_ORIGIN}/api/admin/family-groups**`, async (route) => {
    const request = route.request()
    if (request.method() !== 'GET') {
      await route.fulfill({ status: 200, headers: JSON_HEADERS, body: '{}' })
      return
    }
    const path = new URL(request.url()).pathname
    const body = path.endsWith('/members')
      ? pageResponse([familyMember()])
      : path.endsWith('/member-candidates')
        ? pageResponse([memberCandidate()])
        : path.endsWith('/audit-logs')
          ? pageResponse([auditLog()])
          : pageResponse([familyGroup()])
    await route.fulfill({ status: 200, headers: JSON_HEADERS, body: JSON.stringify(body) })
  })

  await navigateWithinApp(page, '/admin/family-groups')
  await expect(page.getByRole('heading', { name: '가족 그룹 관리' })).toBeVisible()
  await expect(page.getByRole('button', { name: /김 가족/ })).toBeVisible()
  await expect(page.getByRole('heading', { name: '현재 구성원' })).toBeVisible()
  await expect(page.getByRole('heading', { name: '변경 감사 이력' })).toBeVisible()
  await expectNoHorizontalOverflow(page)

  await page.getByRole('button', { name: /이후보/ }).click()
  await page.getByLabel('추가 사유').fill('가족 확인 완료')
  await expect(page.getByRole('button', { name: '구성원 추가 확인' })).toBeEnabled()
  await page.getByRole('button', { name: '제거' }).click()
  await page.getByLabel('제거 사유').fill('가족 관계 변경')
  await expect(page.getByRole('button', { name: '제거 확인' })).toBeEnabled()
  await expectNoHorizontalOverflow(page)

  await page.context().close()
})

async function expectNoHorizontalOverflow(page: import('@playwright/test').Page) {
  const dimensions = await page.evaluate(() => ({
    viewport: window.innerWidth,
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth,
  }))
  expect(dimensions.document).toBeLessThanOrEqual(dimensions.viewport)
  expect(dimensions.body).toBeLessThanOrEqual(dimensions.viewport)
}

function pageResponse<T>(content: T[]) {
  return { content, page: 0, size: 10, totalElements: content.length, totalPages: content.length ? 1 : 0, hasNext: false }
}

function familyGroup() {
  return {
    groupId: 11,
    name: '김 가족',
    status: 'ACTIVE',
    activeMemberCount: 1,
    createdAt: '2026-08-15T10:00:00+09:00',
    dissolvedAt: null,
  }
}

function familyMember() {
  return {
    membershipId: 21,
    memberId: 31,
    name: '김승마',
    phone: '010-1234-5678',
    joinedAt: '2026-08-15T10:00:00+09:00',
  }
}

function memberCandidate() {
  return { memberId: 32, name: '이후보', phone: '010-2222-2222' }
}

function auditLog() {
  return {
    auditId: 41,
    action: 'MEMBER_ADDED',
    memberId: 31,
    memberName: '김승마',
    fromState: { status: 'NONE' },
    toState: { status: 'ACTIVE' },
    actorAuthSubject: 'admin-family',
    reason: '가족 확인 완료',
    occurredAt: '2026-08-15T10:00:00+09:00',
  }
}
