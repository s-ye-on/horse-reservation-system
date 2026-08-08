import { expect, test } from '@playwright/test'

test('실제 웹 인증은 새로고침 후 세션을 복구하고 로그아웃 후에는 복구하지 않는다', async ({ page, context }) => {
  const unique = `${Date.now()}-${test.info().workerIndex}`
  const email = `m31-16b2-${unique}@horse.test`
  const password = 'web-auth-password-1234'

  await page.goto('/signup')
  await page.getByLabel('이메일').fill(email)
  await page.getByLabel('비밀번호').fill(password)
  await page.getByLabel('이름').fill('웹 인증 회원')
  await page.getByLabel('전화번호').fill('010-1234-5678')
  await page.getByRole('button', { name: '회원가입' }).click()

  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('status')).toHaveText('회원가입이 완료되었습니다. 로그인해 주세요.')
  await page.getByLabel('이메일').fill(email)
  await page.getByLabel('비밀번호').fill(password)

  let protectedRequestCount = 0
  let refreshRequestCount = 0
  const countRefreshRequest = (request: { url(): string }) => {
    if (new URL(request.url()).pathname === '/api/auth/web/refresh') refreshRequestCount += 1
  }
  page.on('request', countRefreshRequest)
  await page.route('**/api/auth/me', async (route) => {
    protectedRequestCount += 1
    if (protectedRequestCount === 1) {
      await route.fulfill({
        status: 401,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 'COMMON_UNAUTHORIZED', message: '인증이 필요합니다.', status: 401,
          timestamp: new Date().toISOString(), path: '/api/auth/me', details: {}, fieldErrors: [],
        }),
      })
      return
    }
    await route.continue()
  })

  await page.getByRole('button', { name: '로그인' }).click()

  await expect(page).toHaveURL(/\/reservations$/)
  await expect(page.getByText(`${email} 로그인`)).toBeVisible()
  await expect.poll(() => protectedRequestCount).toBe(2)
  expect(refreshRequestCount).toBe(1)
  page.off('request', countRefreshRequest)
  await page.unroute('**/api/auth/me')

  const refreshCookieMetadata = (await context.cookies())
    .filter(({ name }) => name === 'HORSE_REFRESH_TOKEN')
    .map(({ name, httpOnly, path, sameSite }) => ({ name, httpOnly, path, sameSite }))
  expect(refreshCookieMetadata).toEqual([{
    name: 'HORSE_REFRESH_TOKEN',
    httpOnly: true,
    path: '/api/auth/web',
    sameSite: 'Lax',
  }])

  await page.reload()

  await expect(page).toHaveURL(/\/reservations$/)
  await expect(page.getByText(`${email} 로그인`)).toBeVisible()
  const browserStorage = await page.evaluate(() => ({
    localStorageKeys: Object.keys(localStorage),
    sessionStorageKeys: Object.keys(sessionStorage),
  }))
  expect(browserStorage).toEqual({ localStorageKeys: [], sessionStorageKeys: [] })

  await page.getByRole('button', { name: '로그아웃' }).click()
  await expect(page).toHaveURL(/\/login$/)
  await page.reload()

  await expect(page).toHaveURL(/\/login$/)
  await expect(page.getByRole('heading', { name: '로그인' })).toBeVisible()
  await expect(page.getByText(`${email} 로그인`)).toHaveCount(0)
})
