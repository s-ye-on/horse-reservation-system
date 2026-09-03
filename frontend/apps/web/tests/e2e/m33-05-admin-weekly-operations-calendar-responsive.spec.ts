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
  const board = page.getByRole('region', { name: '주간 운영 시간표' })
  const mondayStart = mondayOf(today)
  const mondayMonthDay = formatMonthDay(mondayStart)
  const mondayWeekday = weekdayName(mondayStart)
  const mondayGroup = board.getByRole('group', { name: `${mondayMonthDay} (${mondayWeekday}) 10:00 시간대` })

  await expect(board).toBeVisible()
  await expect(board.getByRole('heading', { name: `${mondayWeekday} ${formatShortDate(mondayStart)}` })).toBeAttached()
  await expect(board.getByRole('heading', { name: '10:00' })).toBeAttached()
  await expect(mondayGroup).toBeAttached()
  await expect(page.getByText('예약 없음')).toBeVisible()
  await expect(mondayGroup.getByRole('article', { name: `${mondayMonthDay} (${mondayWeekday}) 10:00 수업 시간대` })).toBeVisible()
  await expect(page.getByText('김승인')).toBeVisible()
  await expect(page.getByText('원형 속보')).toBeVisible()
  await expect(page.getByText('쿠폰 승인대기')).toBeVisible()
  await expect(page.getByText('이완료')).toBeVisible()
  await expect(page.getByText('마장마술')).toBeVisible()
  await expect(page.getByText('수업 완료')).toBeVisible()
  await expect(page.locator('[data-timeslot-id]')).toHaveCount(2)
  await expectNoHorizontalOverflow(page)

  await page.getByRole('button', { name: '다음 주' }).click()
  await expect(page.getByText('다음주 회원')).toBeVisible()
  expect(requestedDates.at(-1)).toBe(moveDate(today, 7))
  await expectNoHorizontalOverflow(page)

  await page.getByRole('button', { name: '오늘' }).click()
  await expect(page.getByText('김승인')).toBeVisible()
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

function formatShortDate(value: string) {
  const [, month, day] = value.split('-').map(Number)
  return `${month}. ${day}.`
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
  }))
  expect(dimensions.document).toBeLessThanOrEqual(dimensions.viewport)
  expect(dimensions.body).toBeLessThanOrEqual(dimensions.viewport)
}
