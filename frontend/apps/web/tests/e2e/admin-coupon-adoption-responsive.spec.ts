import { expect, test } from '@playwright/test'
import { createAuthenticatedPage, navigateWithinApp } from './e2e-support'

const WEB_ORIGIN = process.env.HORSE_E2E_WEB_BASE_URL ?? 'http://localhost:5173'
const JSON_HEADERS = { 'Content-Type': 'application/json' }

test('기존_사용_중_쿠폰은_320px에서_현재_상태와_최초_사용일을_등록한다', async ({ browser }) => {
  let registrationBody: Record<string, unknown> | undefined
  const page = await createAuthenticatedPage(
    browser,
    'e2e-coupon-adoption-admin',
    'ADMIN',
    { viewport: { width: 320, height: 900 } },
  )

  await page.route(`${WEB_ORIGIN}/api/admin/members**`, async (route) => {
    const request = route.request()
    if (request.method() === 'POST') {
      registrationBody = request.postDataJSON() as Record<string, unknown>
      await route.fulfill({
        status: 201,
        headers: JSON_HEADERS,
        body: JSON.stringify({
          id: 91,
          memberId: 3,
          type: 'general',
          totalCount: 10,
          remainingCount: 7,
          heldCount: 0,
          firstUsedAt: '2026-07-03T00:00:00+09:00',
          expiresAt: '2026-10-03T00:00:00+09:00',
          freeChangeUsed: false,
          status: 'active',
          createdBy: 'e2e-coupon-adoption-admin',
          createdAt: '2026-09-04T12:00:00+09:00',
        }),
      })
      return
    }

    await route.fulfill({
      status: 200,
      headers: JSON_HEADERS,
      body: JSON.stringify({
        content: [member()],
        page: 0,
        size: 20,
        totalElements: 1,
        totalPages: 1,
        hasNext: false,
      }),
    })
  })

  await navigateWithinApp(page, '/admin/coupons/new')
  await page.getByLabel('회원 검색').fill('이기승')
  await page.getByRole('option', { name: /이기승/ }).click()
  await page.getByRole('radio', { name: /일반/ }).click()
  await page.getByRole('radio', { name: /기존 쿠폰 등록/ }).click()
  await page.getByLabel('이미 사용한 횟수').fill('3')
  await page.getByLabel('실제 최초 사용일').fill('2026-07-03')

  await expect(page.getByText(/등록 후 남은 횟수/)).toContainText('7회')
  await page.getByRole('button', { name: '쿠폰 등록' }).click()

  const result = page.locator('.admin-coupon-success')
  await expect(result.getByRole('heading', { name: '쿠폰이 등록되었습니다' })).toBeVisible()
  await expect(result.getByText('7회', { exact: true })).toBeVisible()
  expect(registrationBody).toEqual({
    type: 'general',
    totalCount: 10,
    usedCount: 3,
    firstUsedDate: '2026-07-03',
  })
  await expectNoHorizontalOverflow(page)

  await page.context().close()
})

for (const width of [1440, 960, 320]) {
  test(`전체_회원_검색과_페이지_키보드_선택은_${width}px에서_쿠폰_초안과_긴_이름을_보존한다`, async ({ browser }) => {
    const page = await createAuthenticatedPage(browser, 'e2e-coupon-search-admin', 'ADMIN', {
      viewport: { width, height: 900 },
    })
    const longName = 'A'.repeat(100)
    const members = Array.from({ length: 61 }, (_, index) => ({
      ...member(), id: index + 1,
      name: index === 60 ? longName : `검증회원 ${index + 1}`,
      phone: `010-1234-${String(index + 1).padStart(4, '0')}`,
    }))
    const requests: Array<{ page: number; query: string }> = []
    const registrations: Array<{ memberId: number; body: unknown }> = []
    await page.route(`${WEB_ORIGIN}/api/admin/members**`, async (route) => {
      const request = route.request()
      const url = new URL(request.url())
      if (request.method() === 'POST') {
        const memberId = Number(url.pathname.split('/')[4])
        registrations.push({ memberId, body: request.postDataJSON() })
        await route.fulfill({ status: 201, headers: JSON_HEADERS, body: JSON.stringify({
          id: 92, memberId, type: 'general', totalCount: 25, remainingCount: 22, heldCount: 0,
          status: 'active', freeChangeUsed: false, createdBy: 'e2e-coupon-search-admin',
          createdAt: '2026-10-01T00:00:00Z', firstUsedAt: '2026-07-03T00:00:00+09:00',
        }) })
        return
      }
      const currentPage = Number(url.searchParams.get('page'))
      const size = Number(url.searchParams.get('size'))
      const query = url.searchParams.get('query') ?? ''
      requests.push({ page: currentPage, query })
      if (currentPage > 0) await new Promise((resolve) => setTimeout(resolve, 500))
      const phoneQuery = /^[\d\s-]+$/.test(query) ? query.replace(/[^\d]/g, '') : ''
      const filtered = members.filter((value) => !query
        || value.name.toLowerCase().includes(query.toLowerCase())
        || (phoneQuery && value.phone.replace(/[^\d]/g, '').includes(phoneQuery)))
      await route.fulfill({ status: 200, headers: JSON_HEADERS, body: JSON.stringify({
        content: filtered.slice(currentPage * size, (currentPage + 1) * size),
        page: currentPage, size, totalElements: filtered.length,
        totalPages: Math.ceil(filtered.length / size), hasNext: (currentPage + 1) * size < filtered.length,
      }) })
    })

    await navigateWithinApp(page, '/admin/coupons/new')
    await expect(page.getByText('전체 회원 61명 · 현재 페이지 20명')).toBeVisible()
    await page.getByRole('radio', { name: /일반/ }).check()
    await page.getByRole('radio', { name: /기존 쿠폰 등록/ }).check()
    await page.getByLabel('총 사용 가능 횟수').fill('25')
    await page.getByLabel('이미 사용한 횟수').fill('3')
    await page.getByLabel('실제 최초 사용일').fill('2026-07-03')
    const search = page.getByRole('combobox', { name: '회원 검색' })
    await search.focus()
    const nextPage = page.getByRole('button', { name: '다음', exact: true })
    await nextPage.focus()
    await nextPage.press('Enter')
    await expect(nextPage).toHaveAttribute('aria-disabled', 'true')
    await expect(nextPage).toBeFocused()
    await nextPage.press('Enter')
    await nextPage.press('Space')
    await expect(page.getByRole('option', { name: /검증회원 21 / })).toBeVisible()
    await expect(nextPage).toBeFocused()
    await expect(page.getByRole('navigation', { name: '회원 검색 결과 페이지' })).toContainText('2 / 4')
    expect(requests.filter((value) => value.page === 1 && value.query === '')).toHaveLength(1)
    await search.fill('검증회원')
    await expect(page.getByRole('option', { name: /검증회원 1 / })).toBeVisible()
    expect(requests.at(-1)).toEqual({ page: 0, query: '검증회원' })
    await nextPage.focus()
    await nextPage.press('Space')
    await expect(page.getByRole('option', { name: /검증회원 21 / })).toBeVisible()
    await expect(nextPage).toBeFocused()
    expect(requests.at(-1)).toEqual({ page: 1, query: '검증회원' })
    await nextPage.press('Enter')
    await expect(page.getByRole('option', { name: /검증회원 41 / })).toBeVisible()
    await expect(nextPage).toBeFocused()
    await expect(nextPage).toHaveAttribute('aria-disabled', 'true')
    const requestCount = requests.length
    await nextPage.press('Enter')
    await expect(page.getByRole('navigation', { name: '회원 검색 결과 페이지' })).toContainText('3 / 3')
    expect(requests).toHaveLength(requestCount)

    await search.fill('010-1234 0061')
    await expect(page.getByRole('option', { name: new RegExp(longName) })).toBeVisible()
    expect(requests.at(-1)).toEqual({ page: 0, query: '010-1234 0061' })
    await expectNoHorizontalOverflow(page)
    await search.press('Escape')
    await expect(search).toHaveValue('010-1234 0061')
    await expect(search).toHaveAttribute('aria-expanded', 'false')
    await search.press('ArrowDown')
    await search.press('Enter')
    await expect(page.getByLabel('선택 회원 정보')).toContainText(longName)
    await expectNoHorizontalOverflow(page)
    await page.screenshot({ path: `/tmp/horse-coupon-server-search-${width}.png`, fullPage: true })

    await search.fill('없는 회원')
    await expect(page.getByText('검색 조건과 일치하는 회원이 없습니다.')).toBeVisible()
    await search.press('Enter')
    await expect(page.getByLabel('선택 회원 정보')).toContainText(longName)
    expect(registrations).toHaveLength(0)
    await search.fill('')
    await expect(page.getByText('전체 회원 61명 · 현재 페이지 20명')).toBeVisible()
    await expect(page.getByLabel('총 사용 가능 횟수')).toHaveValue('25')
    await expect(page.getByLabel('이미 사용한 횟수')).toHaveValue('3')
    await expect(page.getByLabel('실제 최초 사용일')).toHaveValue('2026-07-03')
    await page.getByRole('button', { name: '쿠폰 등록', exact: true }).click()
    await expect(page.getByRole('heading', { name: '쿠폰이 등록되었습니다' })).toBeVisible()
    expect(registrations).toEqual([{ memberId: 61, body: {
      type: 'general', totalCount: 25, usedCount: 3, firstUsedDate: '2026-07-03',
    } }])
    await expectNoHorizontalOverflow(page)
    await page.context().close()
  })
}

test('페이지_조회_실패와_재조회는_320px에서_검색창_포커스를_유지한다', async ({ browser }) => {
  const page = await createAuthenticatedPage(browser, 'e2e-coupon-search-error', 'ADMIN', {
    viewport: { width: 320, height: 900 },
  })
  let failPage = true
  const requestedPages: number[] = []
  await page.route(`${WEB_ORIGIN}/api/admin/members**`, async (route) => {
    const params = new URL(route.request().url()).searchParams
    const currentPage = Number(params.get('page'))
    requestedPages.push(currentPage)
    if ((currentPage > 0 || params.get('query')) && failPage) {
      await route.fulfill({ status: 500, headers: JSON_HEADERS, body: JSON.stringify({ message: 'query failed' }) })
      return
    }
    await route.fulfill({ status: 200, headers: JSON_HEADERS, body: JSON.stringify({
      content: [member()], page: currentPage, size: 20, totalElements: 41, totalPages: 3, hasNext: currentPage < 2,
    }) })
  })
  await navigateWithinApp(page, '/admin/coupons/new')
  await expect(page.getByText('전체 회원 41명 · 현재 페이지 1명')).toBeVisible()
  const search = page.getByRole('combobox', { name: '회원 검색' })
  await search.focus()
  await page.getByRole('button', { name: '다음', exact: true }).focus()
  await page.getByRole('button', { name: '다음', exact: true }).press('Enter')
  await expect(page.getByRole('alert')).toContainText('회원 목록을 불러오지 못했습니다.', { timeout: 15000 })
  await expect(search).toBeFocused()
  expect(requestedPages.every((value) => value === 0 || value === 1)).toBe(true)
  failPage = false
  await page.getByRole('button', { name: '회원 목록 다시 조회' }).click()
  await expect(page.getByRole('option', { name: /이기승/ })).toBeVisible()
  await expect(search).toBeFocused()
  await expect(page.getByRole('navigation', { name: '회원 검색 결과 페이지' })).toContainText('2 / 3')
  failPage = true
  await search.fill('지연 실패')
  const count = page.getByLabel('총 사용 가능 횟수')
  await count.focus()
  await expect(page.getByRole('alert')).toContainText('회원 목록을 불러오지 못했습니다.', { timeout: 15000 })
  await expect(count).toBeFocused()
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

function member() {
  return {
    id: 3,
    name: '이기승',
    phone: '010-2222-3333',
    generalRideCount: 8,
    progressionValue: 26,
    progressionClass: 'LARGE_ARENA_TROT',
    effectiveClass: 'LARGE_ARENA_TROT',
    progressionManagementStartedAt: '2026-08-01T00:00:00+09:00',
    progressionBaselineClass: null,
    progressionBaselineThreshold: null,
    progressionBaselineActualRideCount: null,
    specialApprovalProgressionCredit: 18,
    promotionHoldClass: null,
    dressageRideCount: 0,
    jumpingRideCount: 0,
    dressageApproved: true,
    jumpingApproved: false,
    canUseLargeArena: true,
  }
}
