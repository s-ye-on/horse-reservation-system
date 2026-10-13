import { expect, test, type Page } from '@playwright/test'

async function noOverflow(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth && document.body.scrollWidth <= innerWidth)).toBe(true)
}

for (const width of [1440, 960, 320]) {
  test(`Auth 폼과 오류 포커스 및 세션 흐름을 ${width}px에서 확인한다`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 })
    let authenticated = false
    let signupMode: 'email' | 'password' | 'success' = 'email'
    let signupRequests = 0
    let csrfHeader = ''
    const longMessage = '이미 사용 중인 이메일입니다. 다른 이메일을 입력해 주세요. '.repeat(15)
    const account = { subject: 'auth-design-admin', memberId: null, email: 'admin@horse.test', role: 'ADMIN', status: 'ACTIVE' }
    const token = { accessToken: 'auth-design-token', tokenType: 'Bearer', accessTokenExpiresAt: '2030-01-01T00:00:00Z' }
    await page.route('**/api/auth/**', async (route) => {
      const path = new URL(route.request().url()).pathname
      if (path === '/api/auth/web/csrf') {
        await route.fulfill({ json: { cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }, headers: { 'Set-Cookie': 'XSRF-TOKEN=auth-design-csrf; Path=/; SameSite=Lax' } })
      } else if (path === '/api/auth/web/refresh') {
        await route.fulfill(authenticated ? { json: token } : { status: 401, json: {} })
      } else if (path === '/api/auth/signup') {
        signupRequests++
        if (signupMode === 'success') await route.fulfill({ status: 201, json: { ...account, role: 'MEMBER', memberId: 1 } })
        else await route.fulfill({ status: 400, json: {
          code: signupMode === 'password' ? 'AUTH_INVALID_PASSWORD' : 'INVALID_REQUEST',
          message: '입력 내용을 확인해 주세요.',
          fieldErrors: signupMode === 'email' ? [{ field: 'email', message: longMessage }] : [],
        } })
      } else if (path === '/api/auth/web/login') {
        authenticated = true
        csrfHeader = route.request().headers()['x-xsrf-token'] ?? ''
        await route.fulfill({ json: token, headers: { 'Set-Cookie': 'HORSE_REFRESH_TOKEN=auth-design-refresh; HttpOnly; Path=/api/auth/web; SameSite=Lax' } })
      } else if (path === '/api/auth/me') {
        await route.fulfill({ json: account })
      } else if (path === '/api/auth/web/logout') {
        authenticated = false
        await route.fulfill({ status: 204, headers: { 'Set-Cookie': 'HORSE_REFRESH_TOKEN=; Max-Age=0; HttpOnly; Path=/api/auth/web' } })
      } else await route.fulfill({ status: 404, json: {} })
    })

    await page.goto('/signup')
    await expect(page.getByRole('heading', { name: '회원가입' })).toBeFocused()
    await expect(page.getByRole('link', { name: 'Unicorn Stable 홈' })).toBeVisible()
    if (width === 320) {
      const navigation = await page.locator('.public-session-navigation a').evaluateAll((links) => links.map((link) => link.getBoundingClientRect().top))
      expect(navigation[0]).toBe(navigation[1])
      const textLines = await page.locator('.public-session-navigation a').evaluateAll((links) => links.map((link) => {
        const range = document.createRange()
        range.selectNodeContents(link)
        return range.getClientRects().length
      }))
      expect(textLines).toEqual([1, 1])
    }
    await page.keyboard.press('Tab')
    await expect(page.getByLabel('이메일', { exact: true })).toBeFocused()
    await page.keyboard.press('Tab')
    await expect(page.getByLabel('비밀번호', { exact: true })).toBeFocused()
    await page.keyboard.press('Shift+Tab')
    await expect(page.getByLabel('이메일', { exact: true })).toBeFocused()
    await page.getByLabel('이메일', { exact: true }).fill('admin@horse.test')
    await page.getByLabel('비밀번호', { exact: true }).fill('가'.repeat(25))
    await page.getByLabel('이름', { exact: true }).fill('긴회원이름'.repeat(20))
    await page.getByLabel('전화번호', { exact: true }).fill('010-1234-5678')
    await page.screenshot({ path: `/tmp/horse-auth-signup-${width}.png`, fullPage: true })
    await page.getByRole('button', { name: '회원가입' }).click()
    await expect(page.getByLabel('비밀번호', { exact: true })).toBeFocused()
    await expect(page.getByLabel('비밀번호', { exact: true })).toHaveAttribute('aria-invalid', 'true')
    await expect(page.getByLabel('비밀번호', { exact: true })).toHaveValue('가'.repeat(25))
    expect(signupRequests).toBe(0)
    await noOverflow(page)

    await page.getByLabel('비밀번호', { exact: true }).fill('가'.repeat(24))
    await page.getByRole('button', { name: '회원가입' }).click()
    await expect(page.getByRole('alert')).toContainText(longMessage.trim())
    await expect(page.getByLabel('이메일', { exact: true })).toBeFocused()
    await expect(page.getByLabel('이메일', { exact: true })).toHaveAttribute('aria-describedby', 'signup-email-error')
    await noOverflow(page)
    await page.screenshot({ path: `/tmp/horse-auth-signup-error-${width}.png`, fullPage: true })
    signupMode = 'password'
    await page.getByLabel('이메일', { exact: true }).fill('other@horse.test')
    await page.getByRole('button', { name: '회원가입' }).click()
    await expect(page.getByRole('alert')).toContainText('72바이트')
    await expect(page.getByLabel('비밀번호', { exact: true })).toBeFocused()
    signupMode = 'success'
    await page.getByLabel('비밀번호', { exact: true }).fill('password-1234')
    await page.getByRole('button', { name: '회원가입' }).click()
    await expect(page).toHaveURL(/\/login$/)
    await expect(page.getByText('회원가입이 완료되었습니다. 로그인해 주세요.')).toBeFocused()
    await noOverflow(page)
    await page.screenshot({ path: `/tmp/horse-auth-login-${width}.png`, fullPage: true })
    const targets = await page.locator('.auth-form-panel input, .auth-form-panel button, .auth-form-panel a').evaluateAll((elements) => elements.every((element) => {
      const bounds = element.getBoundingClientRect()
      return bounds.height >= 44 && bounds.width >= 44
    }))
    expect(targets).toBe(true)
    await page.getByLabel('이메일', { exact: true }).fill('admin@horse.test')
    await page.getByLabel('비밀번호', { exact: true }).fill('password-1234')
    await page.getByLabel('비밀번호', { exact: true }).press('Enter')
    await expect(page).toHaveURL(/\/admin$/)
    await expect(page.getByRole('heading', { name: '관리자', exact: true })).toBeFocused()
    expect(csrfHeader).toBe('auth-design-csrf')
    expect((await page.context().cookies()).find((cookie) => cookie.name === 'HORSE_REFRESH_TOKEN')?.httpOnly).toBe(true)
    await page.reload()
    await expect(page.getByRole('button', { name: '로그아웃' })).toBeVisible()
    await page.getByRole('button', { name: '로그아웃' }).click()
    await expect(page).toHaveURL(/\/login$/)
    await page.reload()
    await expect(page.getByRole('button', { name: '로그인' })).toBeVisible()
    expect(await page.evaluate(() => ({ local: Object.keys(localStorage), session: Object.keys(sessionStorage) }))).toEqual({ local: [], session: [] })

    await page.goto('/forbidden')
    await expect(page.getByRole('heading', { name: '이 화면을 이용할 수 없습니다' })).toBeFocused()
    await page.keyboard.press('Tab')
    await expect(page.getByRole('link', { name: '홈으로 이동' })).toBeFocused()
    await expect(page.getByRole('link', { name: '홈으로 이동' })).toHaveCSS('background-color', 'rgb(128, 0, 32)')
    await noOverflow(page)
    await page.screenshot({ path: `/tmp/horse-auth-forbidden-${width}.png`, fullPage: true })
  })
}

for (const initialPath of ['/login', '/my/coupons']) {
  test(`회원 로그인 후 ${initialPath === '/login' ? '기본 화면' : '원래 내부 경로'}로 복귀해 포커스를 유지한다`, async ({ page }) => {
    const origin = new URL(test.info().project.use.baseURL as string).origin
    await page.route(`${origin}/api/**`, async (route) => {
      const path = new URL(route.request().url()).pathname
      if (path.endsWith('/csrf')) await route.fulfill({ json: { cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }, headers: { 'Set-Cookie': 'XSRF-TOKEN=member-csrf; Path=/' } })
      else if (path.endsWith('/refresh')) await route.fulfill({ status: 401, json: {} })
      else if (path.endsWith('/login')) await route.fulfill({ json: { accessToken: 'member-token', tokenType: 'Bearer', accessTokenExpiresAt: '2030-01-01T00:00:00Z' } })
      else if (path === '/api/auth/me') await route.fulfill({ json: { subject: 'auth-member', memberId: 1, email: 'member@horse.test', role: 'MEMBER', status: 'ACTIVE' } })
      else if (path.endsWith('/eligible-classes')) await route.fulfill({ json: { currentGeneralGrade: 'FIRST_RIDE', generalRideCount: 0, availableRidingClasses: ['FIRST_RIDE'] } })
      else if (path.endsWith('/timeslots')) await route.fulfill({ json: { timeSlots: [] } })
      else await route.fulfill({ json: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false } })
    })
    await page.goto(initialPath)
    await expect(page).toHaveURL(/\/login$/)
    await page.getByLabel('이메일', { exact: true }).fill('member@horse.test')
    await page.getByLabel('비밀번호', { exact: true }).fill('password-1234')
    await page.getByRole('button', { name: '로그인' }).click()
    await expect(page).toHaveURL(initialPath === '/login' ? /\/reservations$/ : /\/my\/coupons$/)
    await expect.poll(() => page.locator('#main-content').evaluate((element) =>
      element === document.activeElement || element.querySelector('h1') === document.activeElement)).toBe(true)
    expect(await page.evaluate(() => document.activeElement?.getAttribute('tabindex'))).toBe('-1')
  })
}
