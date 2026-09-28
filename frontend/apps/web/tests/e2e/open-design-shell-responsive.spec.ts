import { expect, test } from '@playwright/test'
import { createAuthenticatedPage } from './e2e-support'

test('회원_shell은_320px에서_주요_route와_가로_폭을_유지한다', async ({ browser }) => {
  const page = await createAuthenticatedPage(
    browser,
    'e2e-open-design-member',
    'MEMBER',
    { viewport: { width: 320, height: 800 } },
  )

  const navigation = page.getByRole('navigation', { name: '회원 메뉴' })
  await expect(navigation).toBeVisible()
  await expect(navigation.getByRole('link', { name: '수업 예약' })).toHaveAttribute('aria-current', 'page')
  await expect(navigation.getByRole('link', { name: '내 예약' })).toHaveAttribute('href', '/my/reservations')
  await expect(navigation.getByRole('link', { name: '내 쿠폰' })).toHaveAttribute('href', '/my/coupons')

  const dimensions = await page.evaluate(() => ({
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth,
    viewport: window.innerWidth,
  }))
  expect(dimensions.document).toBeLessThanOrEqual(dimensions.viewport)
  expect(dimensions.body).toBeLessThanOrEqual(dimensions.viewport)
  await expect(page.getByRole('button', { name: '로그아웃' })).toHaveCSS('min-height', '44px')
  await page.unrouteAll({ behavior: 'ignoreErrors' })
  await page.context().close()
})

test('관리자_shell은_desktop과_960px_이하에서_같은_route를_제공한다', async ({ browser }) => {
  const desktopPage = await createAuthenticatedPage(
    browser,
    'e2e-open-design-admin-desktop',
    'ADMIN',
    { viewport: { width: 1280, height: 900 } },
  )

  const desktopNavigation = desktopPage.getByRole('navigation', { name: '관리자 전체 메뉴' })
  await expect(desktopNavigation).toBeVisible()
  await expect(desktopNavigation.getByRole('link', { name: '운영 홈' })).toHaveAttribute('aria-current', 'page')
  await expect(desktopPage.getByRole('navigation', { name: '모바일 관리자 메뉴' })).toBeHidden()

  const mobilePage = await createAuthenticatedPage(
    browser,
    'e2e-open-design-admin-mobile',
    'ADMIN',
    { viewport: { width: 960, height: 900 } },
  )

  await expect(mobilePage.getByRole('navigation', { name: '관리자 전체 메뉴' })).toBeHidden()
  const mobileNavigation = mobilePage.getByRole('navigation', { name: '모바일 관리자 메뉴' })
  await expect(mobileNavigation).toBeVisible()
  await expect(mobilePage.getByRole('link', { name: 'Unicorn Stable 홈' })).toBeVisible()
  await mobileNavigation.getByRole('combobox', { name: '관리 업무 이동' })
    .selectOption('/admin/schedule-configuration')
  await expect(mobilePage).toHaveURL(/\/admin\/schedule-configuration$/)

  await mobilePage.setViewportSize({ width: 320, height: 800 })
  const dimensions = await mobilePage.evaluate(() => ({
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth,
    viewport: window.innerWidth,
  }))
  expect(dimensions.document).toBeLessThanOrEqual(dimensions.viewport)
  expect(dimensions.body).toBeLessThanOrEqual(dimensions.viewport)
  await expect(mobilePage.getByRole('link', { name: 'Unicorn Stable 홈' })).toBeVisible()
  await desktopPage.unrouteAll({ behavior: 'ignoreErrors' })
  await mobilePage.unrouteAll({ behavior: 'ignoreErrors' })
  await desktopPage.context().close()
  await mobilePage.context().close()
})
