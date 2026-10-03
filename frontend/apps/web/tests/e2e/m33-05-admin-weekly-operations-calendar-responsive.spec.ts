import { expect, test } from '@playwright/test'
import { createAuthenticatedPage, navigateWithinApp } from './e2e-support'

const WEB_ORIGIN = process.env.HORSE_E2E_WEB_BASE_URL ?? 'http://localhost:5173'

test('주간_운영_캘린더는_320px에서_실제_슬롯과_예약을_표시하고_주를_이동한다', async ({ browser }) => {
  const page = await createAuthenticatedPage(
    browser,
    'e2e-m33-05-weekly-calendar-admin',
    'ADMIN',
    { viewport: { width: 320, height: 900 } },
  )
  const today = seoulDate(new Date())
  const requestedDates: string[] = []

  await page.route(`${WEB_ORIGIN}/api/admin/reservations/weekly-operations-calendar**`, async (route) => {
    const url = new URL(route.request().url())
    const referenceDate = url.searchParams.get('referenceDate') ?? today
    requestedDates.push(referenceDate)
    const weekStartDate = mondayOf(referenceDate)
    const weekEndDate = moveDate(weekStartDate, 6)
    const isNextWeek = referenceDate !== today

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        referenceDate,
        weekStartDate,
        weekEndDate,
        timeSlots: [
          {
            timeSlotId: isNextWeek ? 21 : 11,
            lessonDate: weekStartDate,
            startTime: '10:00:00',
            endTime: '11:00:00',
            totalCapacity: 4,
            roundArenaCapacity: 2,
            classCapacities: {},
            closed: false,
            reservations: [],
          },
          {
            timeSlotId: isNextWeek ? 22 : 12,
            lessonDate: moveDate(weekStartDate, 2),
            startTime: '14:00:00',
            endTime: '15:00:00',
            totalCapacity: 5,
            roundArenaCapacity: 2,
            classCapacities: {},
            closed: true,
            reservations: [
              {
                reservationId: 101,
                memberId: 11,
                memberName: isNextWeek ? '다음주 회원' : '김승인',
                ridingClass: 'ROUND_TROT',
                status: 'pending_admin_approval',
              },
              {
                reservationId: 102,
                memberId: 12,
                memberName: '이완료',
                ridingClass: 'DRESSAGE',
                status: 'completed',
              },
            ],
          },
        ],
      }),
    })
  })

  await navigateWithinApp(page, '/admin/weekly-operations-calendar')
  await expect(page.getByRole('heading', { name: '주간 운영 캘린더' })).toBeVisible()
  const board = page.getByRole('region', { name: '날짜별 운영 시간대' })
  const mondayStart = mondayOf(today)
  const mondayMonthDay = formatMonthDay(mondayStart)
  const mondayWeekday = weekdayName(mondayStart)
  const mondayGroup = board.getByRole('region', { name: `${mondayMonthDay} (${mondayWeekday})` })

  await expect(board).toBeVisible()
  await expect(board.getByRole('heading', { name: `${mondayMonthDay} (${mondayWeekday})` })).toBeVisible()
  await expect(mondayGroup).toBeAttached()
  await expect(board.getByText('현재 표시 대상 예약이 없습니다.')).toBeVisible()
  await expect(mondayGroup.getByRole('article', { name: `${mondayMonthDay} (${mondayWeekday}) 10:00 수업 시간대` })).toBeVisible()
  await expect(board.getByText('김승인')).toBeVisible()
  await expect(board.getByText('원형 속보')).toBeVisible()
  await expect(board.getByText('쿠폰 승인대기')).toBeVisible()
  await expect(board.getByText('이완료')).toBeVisible()
  await expect(board.getByText('마장마술')).toBeVisible()
  await expect(board.getByText('수업 완료')).toBeVisible()
  await expect(board.locator('[data-timeslot-id]')).toHaveCount(2)
  await expectNoHorizontalOverflow(page)

  await page.getByRole('button', { name: '다음 주' }).click()
  await expect(board.getByText('다음주 회원')).toBeVisible()
  expect(requestedDates.at(-1)).toBe(moveDate(today, 7))
  await expectNoHorizontalOverflow(page)

  await page.getByRole('button', { name: '오늘' }).click()
  await expect(board.getByText('김승인')).toBeVisible()
  expect(requestedDates.at(-1)).toBe(today)
  await expectNoHorizontalOverflow(page)

  await page.context().close()
})

function seoulDate(date: Date) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(date)
  const year = parts.find((part) => part.type === 'year')?.value
  const month = parts.find((part) => part.type === 'month')?.value
  const day = parts.find((part) => part.type === 'day')?.value
  if (!year || !month || !day) throw new Error('현재 날짜를 계산할 수 없습니다.')
  return `${year}-${month}-${day}`
}

function mondayOf(value: string) {
  const date = new Date(`${value}T00:00:00Z`)
  const daysFromMonday = (date.getUTCDay() + 6) % 7
  return moveDate(value, -daysFromMonday)
}

function moveDate(value: string, amount: number) {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(Date.UTC(year, month - 1, day + amount)).toISOString().slice(0, 10)
}

function formatMonthDay(value: string) {
  const [, month, day] = value.split('-').map(Number)
  return `${month}월 ${day}일`
}

function weekdayName(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul',
    weekday: 'short',
  }).format(new Date(`${value}T00:00:00Z`))
}

async function expectNoHorizontalOverflow(page: import('@playwright/test').Page) {
  const dimensions = await page.evaluate(() => ({
    viewport: window.innerWidth,
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth,
    overflow: [...document.querySelectorAll('body *')].filter((element) => {
      const rect = element.getBoundingClientRect()
      return rect.width > 0 && rect.right > window.innerWidth + 1
        && !element.closest('.admin-weekly-calendar-scroll')
    }).slice(0, 12).map((element) => ({ tag: element.tagName, class: element.className, text: element.textContent?.slice(0, 60) })),
  }))
  expect(dimensions.document, JSON.stringify(dimensions)).toBeLessThanOrEqual(dimensions.viewport)
  expect(dimensions.body).toBeLessThanOrEqual(dimensions.viewport)
}

test('Frozen_시간표는_desktop_내부스크롤과_960_320_전환_및_키보드_disclosure를_유지한다', async ({ browser }) => {
  const page = await createAuthenticatedPage(browser, 'weekly-frozen-responsive', 'ADMIN', {
    viewport: { width: 1440, height: 1000 },
  })
  const start = mondayOf(seoulDate(new Date()))
  const longName = '긴회원이름'.repeat(12) + 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.repeat(2)
  await page.route(`${WEB_ORIGIN}/api/admin/reservations/weekly-operations-calendar**`, async (route) => {
    expect(route.request().method()).toBe('GET')
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({
      referenceDate: start, weekStartDate: start, weekEndDate: moveDate(start, 6),
      timeSlots: [
        { timeSlotId: 1, lessonDate: start, startTime: '09:00:00', endTime: '09:45:00',
          totalCapacity: 8, roundArenaCapacity: 4, closed: false,
          classCapacities: { FIRST_RIDE: 0, ROUND_BEGINNER: 2, ROUND_TROT: 3, LARGE_ARENA_BEGINNER: 4,
            LARGE_ARENA_TROT: 5, CANTER_BEGINNER: 6, CANTER: 7, DRESSAGE: 8, JUMPING: 8 },
          reservations: Array.from({ length: 8 }, (_, index) => ({ reservationId: 100 + index,
            memberId: 10 + index, memberName: index === 0 ? longName : `회원 ${index}`,
            ridingClass: index % 2 ? 'DRESSAGE' : 'ROUND_TROT', status: index === 7 ? 'completed' : 'confirmed' })),
        },
        { timeSlotId: 2, lessonDate: moveDate(start, 6), startTime: '14:00:00', endTime: '14:45:00',
          totalCapacity: 4, roundArenaCapacity: 2, classCapacities: { JUMPING: 4 }, closed: true, reservations: [] },
      ],
    }) })
  })
  await navigateWithinApp(page, '/admin/weekly-operations-calendar')
  const desktop = page.getByRole('region', { name: '주간 운영 시간표' })
  const mobile = page.getByRole('region', { name: '날짜별 운영 시간대' })
  await expect(desktop.getByRole('table')).toBeVisible()
  await expect(desktop.getByText(longName)).toBeVisible()
  await expect(desktop.getByText('수업 완료')).toBeVisible()
  await expect(desktop.getByText('신규 예약 마감')).toBeVisible()
  await expect(desktop.getByText('예약 가능', { exact: true })).toHaveCount(0)
  const headerRelationships = await desktop.locator('td').evaluateAll((cells) => cells.every((cell) => {
    const ids = cell.getAttribute('headers')?.split(' ') ?? []
    return ids.length === 2 && ids.every((id) => document.getElementById(id)?.tagName === 'TH')
  }))
  expect(headerRelationships).toBe(true)

  for (const width of [1440, 1200, 961, 960, 320]) {
    await page.setViewportSize({ width, height: 1000 })
    await expectNoHorizontalOverflow(page)
    if (width > 960) {
      await expect(desktop).toBeVisible()
      await expect(mobile).toBeHidden()
      const dimensions = await desktop.evaluate((element) => ({ client: element.clientWidth, scroll: element.scrollWidth }))
      if (width <= 1200) expect(dimensions.scroll).toBeGreaterThan(dimensions.client)
      else expect(dimensions.scroll).toBe(dimensions.client)
    } else {
      await expect(desktop).toBeHidden()
      await expect(mobile).toBeVisible()
      await expect(page.getByRole('navigation', { name: '모바일 관리자 메뉴' })).toBeVisible()
      await expect(mobile.getByText(longName)).toBeVisible()
      await expect(mobile.getByText('수업 완료')).toBeVisible()
    }
  }

  const button = mobile.getByRole('button', { name: /09:00 클래스별 정원/ })
  await button.focus()
  await page.keyboard.press('Space')
  await expect(button).toHaveAttribute('aria-expanded', 'true')
  const panelId = await button.getAttribute('aria-controls')
  const panel = page.locator(`[id="${panelId}"]`)
  await expect(panel.getByText('왕초보')).toBeVisible()
  await expect(panel.getByText('0명')).toBeVisible()
  await expect(panel.getByText('장애물')).toBeVisible()
  await page.keyboard.press('Enter')
  await expect(button).toHaveAttribute('aria-expanded', 'false')
  await expect(panel).toBeHidden()
  await page.keyboard.press('Tab')
  await page.keyboard.press('Shift+Tab')
  await expect(button).toBeFocused()
  expect(await button.evaluate((element) => element.getBoundingClientRect().height)).toBeGreaterThanOrEqual(44)

  await page.setViewportSize({ width: 1200, height: 1000 })
  await desktop.focus()
  await page.keyboard.press('ArrowRight')
  await expect.poll(() => desktop.evaluate((element) => element.scrollLeft)).toBeGreaterThan(0)
  await desktop.evaluate((element) => { element.scrollLeft = element.scrollWidth })
  const lastHeader = desktop.getByRole('columnheader', { name: /\(일\)/ })
  await expect(lastHeader).toBeVisible()
  const headerRect = await lastHeader.boundingBox()
  const regionRect = await desktop.boundingBox()
  expect(headerRect!.x).toBeGreaterThanOrEqual(regionRect!.x)
  expect(headerRect!.x + headerRect!.width).toBeLessThanOrEqual(regionRect!.x + regionRect!.width)
  expect(await desktop.locator('.admin-weekly-calendar-sr-only').first().evaluate((element) =>
    element instanceof HTMLElement && element.offsetParent?.classList.contains('admin-weekly-calendar-scroll'))).toBe(true)
  await expectNoHorizontalOverflow(page)
  await page.context().close()
})
