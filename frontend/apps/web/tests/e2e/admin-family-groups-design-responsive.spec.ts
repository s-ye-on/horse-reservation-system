import { expect, test, type Page } from '@playwright/test'
import { createAuthenticatedPage, navigateWithinApp } from './e2e-support'

const ORIGIN = process.env.HORSE_E2E_WEB_BASE_URL ?? 'http://localhost:5173'
const LONG_NAME = 'A'.repeat(100)
const LONG_REASON = '확인 사유 '.repeat(100).slice(0, 500)

for (const width of [1440, 960, 320]) {
  test(`가족_그룹_${width}px_생성_충돌_제거_재추가_해제_dialog`, async ({ browser }) => {
    const page = await createAuthenticatedPage(browser, `family-design-${width}`, 'ADMIN', { viewport: { width, height: 900 } })
    let groups = [group(11, LONG_NAME, 1), group(12, '두번째 가족', 0)]
    let members = [member(31)]
    let candidates = [member(32)]
    let conflict = true
    let commands = 0
    let membershipSequence = 100
    const reads = { groups: 0, members: 0, candidates: 0, audit: 0 }
    await page.route(`${ORIGIN}/api/admin/family-groups**`, async (route) => {
      const request = route.request(), url = new URL(request.url())
      const respond = (body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
      const target = groups.find((item) => item.groupId === Number(url.pathname.split('/')[4]))
      if (request.method() === 'GET') {
        if (url.pathname.endsWith('/members')) {
          reads.members++; expect(url.searchParams.get('size')).toBe('8')
          await respond(result(target?.groupId === 11 ? members : []))
        } else if (url.pathname.endsWith('/member-candidates')) {
          reads.candidates++; expect(url.searchParams.get('size')).toBe('6')
          await respond(result(candidates))
        } else if (url.pathname.endsWith('/audit-logs')) {
          reads.audit++; expect(url.searchParams.get('size')).toBe('8')
          await respond(result([{ auditId: 1, action: 'MEMBER_ADDED', memberId: 31, memberName: '회원31', fromState: { status: 'NONE' }, toState: { status: 'ACTIVE' }, reason: LONG_REASON, actorAuthSubject: 'hidden-actor', occurredAt: '2026-09-30T10:00:00+09:00' }]))
        } else {
          reads.groups++; expect(url.searchParams.get('size')).toBe('10')
          await respond(result(groups.filter((item) => !url.searchParams.get('status') || item.status === url.searchParams.get('status'))))
        }
        return
      }
      commands++
      const input = request.postDataJSON()
      expect(input.reason.trim().length).toBeGreaterThan(0)
      expect(input.reason.length).toBeLessThanOrEqual(500)
      if (url.pathname === '/api/admin/family-groups') {
        expect(Object.keys(input).sort()).toEqual(['name', 'reason'])
        groups = [group(13, input.name, 0), ...groups]
        await respond(groups[0], 201)
      } else if (url.pathname.endsWith('/dissolution')) {
        expect(Object.keys(input)).toEqual(['reason'])
        members = []
        groups = groups.map((item) => item.groupId === 11 ? { ...item, status: 'DISSOLVED', activeMemberCount: 0, dissolvedAt: '2026-10-01T10:00:00+09:00' } : item)
        await respond(groups.find((item) => item.groupId === 11))
      } else if (request.method() === 'DELETE') {
        const id = Number(url.pathname.split('/').at(-1)), removed = members.find((item) => item.memberId === id)!
        members = members.filter((item) => item.memberId !== id); candidates = [...candidates, removed]
        groups = groups.map((item) => item.groupId === 11 ? { ...item, activeMemberCount: members.length } : item)
        await respond({ ...removed, endedAt: '2026-10-01T10:00:00+09:00' })
      } else {
        expect(Object.keys(input).sort()).toEqual(['memberId', 'reason'])
        if (conflict) {
          conflict = false
          await respond({ code: 'FAMILY_MEMBER_ALREADY_ASSIGNED', message: 'conflict' }, 409)
        } else {
          const added = candidates.find((item) => item.memberId === input.memberId)!
          candidates = candidates.filter((item) => item.memberId !== input.memberId)
          members = [...members, { ...added, membershipId: ++membershipSequence }]
          groups = groups.map((item) => item.groupId === 11 ? { ...item, activeMemberCount: members.length } : item)
          await respond(members.at(-1), 201)
        }
      }
    })
    await navigateWithinApp(page, '/admin/family-groups')
    await expect(page.getByRole('heading', { name: LONG_NAME, exact: true })).toBeVisible()
    await assertOverflow(page)
    await expect(page.getByText('hidden-actor')).toHaveCount(0)
    await page.getByRole('button', { name: /회원32/ }).click()
    await page.getByLabel('추가 사유').fill('이전 대상 사유')
    await page.getByLabel('해제 사유').fill('이전 해제 사유')
    await page.getByRole('button', { name: /두번째 가족/ }).click()
    await expect(page.getByLabel('추가 사유')).toHaveValue('')
    await expect(page.getByLabel('해제 사유')).toHaveValue('')
    await page.getByRole('button', { name: new RegExp(LONG_NAME) }).click()

    const create = page.getByRole('button', { name: '새 그룹 만들기' })
    await create.click()
    const dialog = page.getByRole('dialog')
    await expect(dialog.getByRole('heading', { name: '새 가족 그룹 생성' })).toBeFocused()
    await page.keyboard.press('Shift+Tab')
    await expect(dialog.getByRole('button', { name: '그룹 생성' })).toBeFocused()
    await page.keyboard.press('Tab')
    await expect(dialog.getByRole('button', { name: '확인 창 닫기' })).toBeFocused()
    await create.evaluate((node: HTMLElement) => node.focus())
    expect(await dialog.evaluate((node) => node.contains(document.activeElement))).toBe(true)
    await page.keyboard.press('Escape')
    await expect(create).toBeFocused()
    await create.click()
    await dialog.getByRole('button', { name: '그룹 생성' }).click()
    await expect(dialog.getByLabel('그룹 이름', { exact: true })).toHaveAttribute('aria-invalid', 'true')
    expect(commands).toBe(0)
    await dialog.getByLabel('그룹 이름', { exact: true }).fill('새 빈 가족')
    await dialog.getByLabel('생성 사유').fill('빈 그룹 생성')
    await dialog.getByRole('button', { name: '그룹 생성' }).click()
    await expect(page.locator('.admin-family-feedback')).toContainText('구성원 0명')
    await expect(page.locator('.admin-family-feedback')).toBeFocused()
    await expect(page.getByRole('heading', { name: '새 빈 가족' })).toBeVisible()
    await page.getByRole('button', { name: new RegExp(LONG_NAME) }).click()

    for (let attempt = 0; attempt < 2; attempt++) {
      await page.getByRole('button', { name: /회원32/ }).click()
      await page.getByLabel('추가 사유').fill(LONG_REASON)
      await page.getByRole('button', { name: '구성원 추가 확인' }).click()
      await expect(dialog).toContainText(LONG_NAME)
      await assertOverflow(page)
      const previousReads = reads.candidates
      await dialog.getByRole('button', { name: '구성원 추가', exact: true }).click()
      if (attempt === 0) await expect(page.locator('.admin-family-feedback')).toContainText('회원 소속 또는 그룹 상태가 변경')
      else await expect(page.locator('.admin-family-feedback')).toContainText('구성원을 추가했습니다')
      await expect.poll(() => reads.candidates).toBeGreaterThan(previousReads)
    }
    const memberSection = page.getByRole('region', { name: '현재 구성원' })
    for (let count = 0; count < 2; count++) {
      await memberSection.getByRole('button', { name: '제거', exact: true }).first().click()
      await expect(dialog).toContainText('마지막 구성원을 제거해도 그룹은 운영 중')
      await dialog.getByLabel('제거 사유').fill('관계 종료')
      await dialog.getByRole('button', { name: '제거 확인' }).click()
      await expect(page.locator('.admin-family-feedback')).toContainText('현재 구성원 관계를 종료')
      await expect(memberSection.getByRole('button', { name: '제거', exact: true })).toHaveCount(1 - count)
    }
    await expect(memberSection).toContainText('빈 운영 중 그룹도 정상적으로 유지')
    await expect(page.getByRole('button', { name: '그룹 해제 확인' })).toBeVisible()
    await page.getByRole('button', { name: /회원31/ }).click()
    await page.getByLabel('추가 사유').fill('새 관계 등록')
    await page.getByRole('button', { name: '구성원 추가 확인' }).click()
    await dialog.getByRole('button', { name: '구성원 추가', exact: true }).click()
    await expect(memberSection.getByRole('button', { name: '제거' })).toHaveCount(1)
    expect(members[0].membershipId).toBeGreaterThan(100)

    await page.getByRole('combobox', { name: '상태', exact: true }).selectOption('')
    await page.getByRole('button', { name: '검색', exact: true }).click()
    await page.getByRole('button', { name: new RegExp(LONG_NAME) }).click()
    await page.getByLabel('해제 사유').fill(LONG_REASON)
    await page.getByRole('button', { name: '그룹 해제 확인' }).click()
    await expect(dialog).toContainText('복구할 수 없으며 기존 예약')
    await assertOverflow(page)
    await page.screenshot({ path: '/tmp/horse-family-dialog-' + width + '.png' })
    await dialog.getByRole('button', { name: '가족 그룹 해제', exact: true }).click()
    await expect(page.locator('.admin-family-feedback')).toContainText('가족 그룹을 해제했습니다')
    await expect(page.locator('.admin-family-feedback')).toBeFocused()
    await expect(memberSection).toContainText('해제된 그룹에는 현재 구성원이 없습니다')
    await expect(page.getByRole('heading', { name: '구성원 추가' })).toHaveCount(0)
    await assertOverflow(page)
    const targets = await page.locator('.admin-family-page button:visible, .admin-family-page select:visible, .admin-family-page input:visible, .admin-family-page textarea:visible, .admin-family-page summary:visible').evaluateAll((nodes) => nodes.map((node) => ({ label: node.textContent, height: node.getBoundingClientRect().height })))
    for (const target of targets) expect(target.height, target.label ?? '').toBeGreaterThanOrEqual(44)
    if (width <= 960) await expect(page.getByRole('navigation', { name: '모바일 관리자 메뉴' })).toBeVisible()
    await page.evaluate(() => window.scrollTo(0, 0))
    await page.screenshot({ path: '/tmp/horse-family-' + width + '.png', fullPage: true })
    await page.context().close()
  })
}
async function assertOverflow(page: Page) {
  const size = await page.evaluate(() => ({ viewport: innerWidth, doc: document.documentElement.scrollWidth, body: document.body.scrollWidth }))
  expect(size.doc).toBeLessThanOrEqual(size.viewport)
  expect(size.body).toBeLessThanOrEqual(size.viewport)
}
function result<T>(content: T[]) { return { content, page: 0, size: 10, totalElements: content.length, totalPages: content.length ? 1 : 0, hasNext: false } }
function group(groupId: number, name: string, activeMemberCount: number) { return { groupId, name, status: 'ACTIVE', activeMemberCount, createdAt: '2026-09-30T10:00:00+09:00', dissolvedAt: null as string | null } }
function member(memberId: number) { return { memberId, membershipId: memberId, name: '회원' + memberId, phone: '010-1234-5678', joinedAt: '2026-09-30T10:00:00+09:00' } }
