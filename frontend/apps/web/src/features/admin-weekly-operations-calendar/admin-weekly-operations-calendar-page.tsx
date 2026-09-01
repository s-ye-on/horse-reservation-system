import { useMemo, useState, type CSSProperties } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import type {
  AdminWeeklyOperationsCalendarResponse,
  AdminWeeklyOperationsReservationResponse,
  AdminWeeklyOperationsReservationResponseRidingClassEnum,
  AdminWeeklyOperationsReservationResponseStatusEnum,
  AdminWeeklyOperationsTimeSlotResponse,
} from '@horse/api-client'
import {
  adminWeeklyOperationsCalendarApi,
  getAdminWeeklyOperationsCalendarErrorKind,
  type AdminWeeklyOperationsCalendarApi,
} from './admin-weekly-operations-calendar.api'
import './admin-weekly-operations-calendar-page.css'

const RIDING_CLASS_LABELS: Record<AdminWeeklyOperationsReservationResponseRidingClassEnum, string> = {
  FIRST_RIDE: '왕초보',
  ROUND_BEGINNER: '원형초보',
  ROUND_TROT: '원형 속보',
  LARGE_ARENA_BEGINNER: '대마장초보',
  LARGE_ARENA_TROT: '대마장 속보',
  CANTER_BEGINNER: '구보초보',
  CANTER: '구보',
  DRESSAGE: '마장마술',
  JUMPING: '장애물',
}

const RESERVATION_STATUS_LABELS: Record<AdminWeeklyOperationsReservationResponseStatusEnum, string> = {
  pending_admin_approval: '쿠폰 승인대기',
  pending_payment: '입금대기',
  confirmed: '예약 확정',
  completed: '수업 완료',
}

interface AdminWeeklyOperationsCalendarPageProps {
  api?: AdminWeeklyOperationsCalendarApi
  now?: Date
}

export function AdminWeeklyOperationsCalendarPage({
  api = adminWeeklyOperationsCalendarApi,
  now = new Date(),
}: AdminWeeklyOperationsCalendarPageProps) {
  const currentDate = seoulDate(now)
  const [referenceDate, setReferenceDate] = useState(currentDate)
  const calendarQuery = useQuery({
    queryKey: ['admin', 'weekly-operations-calendar', referenceDate],
    queryFn: () => api.getCalendar(referenceDate),
  })

  return (
    <main className="admin-weekly-calendar-page">
      <div className="admin-weekly-calendar-shell">
        <header className="admin-weekly-calendar-header">
          <div>
            <p>WEEKLY OPERATIONS</p>
            <h1>주간 운영 캘린더</h1>
            <span>실제 운영 시간대와 예약 회원을 주간 시간표로 확인합니다.</span>
          </div>
          <Link to="/admin">관리자 메뉴</Link>
        </header>

        <section className="admin-weekly-calendar-controls" aria-label="조회 주 이동">
          <button
            type="button"
            aria-label="이전 주"
            onClick={() => setReferenceDate((date) => moveDate(date, -7))}
          >
            <span aria-hidden="true">&larr;</span>
            <span>이전 주</span>
          </button>
          <button
            type="button"
            className="today"
            disabled={referenceDate === currentDate}
            onClick={() => setReferenceDate(currentDate)}
          >
            오늘
          </button>
          <button
            type="button"
            aria-label="다음 주"
            onClick={() => setReferenceDate((date) => moveDate(date, 7))}
          >
            <span>다음 주</span>
            <span aria-hidden="true">&rarr;</span>
          </button>
        </section>

        <section className="admin-weekly-calendar-results" aria-live="polite">
          {calendarQuery.isPending ? (
            <CalendarState message="주간 운영 일정을 불러오는 중입니다." />
          ) : null}
          {calendarQuery.isError ? (
            <CalendarError error={calendarQuery.error} retry={() => calendarQuery.refetch()} />
          ) : null}
          {calendarQuery.isSuccess ? <WeeklyCalendar calendar={calendarQuery.data} /> : null}
        </section>
      </div>
    </main>
  )
}

function WeeklyCalendar({ calendar }: { calendar: AdminWeeklyOperationsCalendarResponse }) {
  if (calendar.timeSlots.length === 0) {
    return (
      <>
        <CalendarHeading calendar={calendar} />
        <CalendarState message="이 주에는 운영 시간대가 없습니다." />
      </>
    )
  }

  return (
    <>
      <CalendarHeading calendar={calendar} />
      <WeeklyTimeTable calendar={calendar} />
    </>
  )
}

function CalendarHeading({ calendar }: { calendar: AdminWeeklyOperationsCalendarResponse }) {
  return (
    <div className="admin-weekly-calendar-results-heading">
      <div>
        <h2>{formatWeekRange(dateKey(calendar.weekStartDate), dateKey(calendar.weekEndDate))}</h2>
        <p>예약이 없는 실제 시간대도 함께 표시합니다.</p>
      </div>
    </div>
  )
}

function WeeklyTimeTable({ calendar }: { calendar: AdminWeeklyOperationsCalendarResponse }) {
  const weekStart = dateKey(calendar.weekStartDate)
  const days = useMemo(() => Array.from({ length: 7 }, (_, index) => moveDate(weekStart, index)), [weekStart])
  const startTimes = useMemo(() => (
    [...new Set(calendar.timeSlots.map((slot) => slot.startTime))].sort(compareTimes)
  ), [calendar.timeSlots])
  const slotsByCell = useMemo(() => groupSlots(calendar.timeSlots), [calendar.timeSlots])

  return (
    <section className="admin-weekly-calendar-timetable" aria-label="주간 운영 시간표">
      <h3 className="admin-weekly-calendar-corner">시간</h3>
      {days.map((day, dayIndex) => (
        <h3
          aria-label={`${weekdayLabel(day)} ${shortDateLabel(day)}`}
          className="admin-weekly-calendar-day-heading"
          data-day-heading={day}
          key={day}
          style={{ gridColumn: dayIndex + 2, gridRow: 1 }}
        >
          <strong>{weekdayLabel(day)}</strong>
          <span>{shortDateLabel(day)}</span>
        </h3>
      ))}
      {startTimes.map((startTime, timeIndex) => (
        <h3
          className="admin-weekly-calendar-time-heading"
          data-time-heading={startTime}
          key={startTime}
          style={{ gridColumn: 1, gridRow: timeIndex + 2 }}
        >
          {shortTime(startTime)}
        </h3>
      ))}
      {days.flatMap((day, dayIndex) => startTimes.flatMap((startTime, timeIndex) => {
        const slots = slotsByCell.get(`${day}|${startTime}`) ?? []
        if (slots.length === 0) return []
        const style: CSSProperties = { gridColumn: dayIndex + 2, gridRow: timeIndex + 2 }
        return [
          <div
            aria-label={`${fullDateLabel(day)} ${shortTime(startTime)} 시간대`}
            className="admin-weekly-calendar-cell"
            data-calendar-cell={day}
            data-calendar-start-time={startTime}
            key={`${day}|${startTime}`}
            role="group"
            style={style}
          >
            {slots.map((slot) => <TimeSlotCard key={slot.timeSlotId} slot={slot} />)}
          </div>,
        ]
      }))}
    </section>
  )
}

function TimeSlotCard({ slot }: { slot: AdminWeeklyOperationsTimeSlotResponse }) {
  const lessonDate = dateKey(slot.lessonDate)
  return (
    <article
      className={`admin-weekly-calendar-slot${slot.closed ? ' closed' : ''}`}
      data-timeslot-id={slot.timeSlotId}
      aria-label={`${fullDateLabel(lessonDate)} ${shortTime(slot.startTime)} 수업 시간대`}
    >
      <header>
        <div>
          <span className="admin-weekly-calendar-mobile-date">{fullDateLabel(lessonDate)}</span>
          <strong>{shortTime(slot.startTime)} - {shortTime(slot.endTime)}</strong>
        </div>
        {slot.closed ? <span className="admin-weekly-calendar-closed">신규 예약 마감</span> : null}
      </header>
      <p className="admin-weekly-calendar-capacity">
        전체 정원 {slot.totalCapacity}명 · 원형마장 {slot.roundArenaCapacity}명
      </p>
      {slot.reservations.length === 0 ? (
        <p className="admin-weekly-calendar-empty-reservations">예약 없음</p>
      ) : (
        <ul className="admin-weekly-calendar-reservations">
          {slot.reservations.map((reservation) => (
            <ReservationRow key={reservation.reservationId} reservation={reservation} />
          ))}
        </ul>
      )}
    </article>
  )
}

function ReservationRow({ reservation }: { reservation: AdminWeeklyOperationsReservationResponse }) {
  return (
    <li data-reservation-status={reservation.status}>
      <strong>{reservation.memberName}</strong>
      <span>{RIDING_CLASS_LABELS[reservation.ridingClass]}</span>
      <em className={`status-${reservation.status}`}>
        {RESERVATION_STATUS_LABELS[reservation.status]}
      </em>
    </li>
  )
}

function CalendarError({ error, retry }: { error: unknown; retry: () => void }) {
  const kind = getAdminWeeklyOperationsCalendarErrorKind(error)
  const message = kind === 'unauthorized'
    ? '관리자 로그인이 필요합니다.'
    : kind === 'forbidden'
      ? '주간 운영 일정을 조회할 권한이 없습니다.'
      : kind === 'validation'
        ? '조회할 기준 날짜를 다시 확인해 주세요.'
        : '주간 운영 일정을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'

  return (
    <section className="admin-weekly-calendar-state error" role="alert">
      <p>{message}</p>
      <button type="button" onClick={retry}>다시 시도</button>
    </section>
  )
}

function CalendarState({ message }: { message: string }) {
  return <section className="admin-weekly-calendar-state" role="status">{message}</section>
}

function groupSlots(slots: AdminWeeklyOperationsTimeSlotResponse[]) {
  const grouped = new Map<string, AdminWeeklyOperationsTimeSlotResponse[]>()
  for (const slot of [...slots].sort(compareSlots)) {
    const key = `${dateKey(slot.lessonDate)}|${slot.startTime}`
    grouped.set(key, [...(grouped.get(key) ?? []), slot])
  }
  return grouped
}

function compareSlots(left: AdminWeeklyOperationsTimeSlotResponse, right: AdminWeeklyOperationsTimeSlotResponse) {
  return dateKey(left.lessonDate).localeCompare(dateKey(right.lessonDate))
    || compareTimes(left.startTime, right.startTime)
    || left.timeSlotId - right.timeSlotId
}

function compareTimes(left: string, right: string) {
  return left.localeCompare(right)
}

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

function dateKey(date: Date) {
  return date.toISOString().slice(0, 10)
}

function moveDate(value: string, amount: number) {
  const [year, month, day] = value.split('-').map(Number)
  const moved = new Date(Date.UTC(year, month - 1, day + amount))
  return moved.toISOString().slice(0, 10)
}

function dateAtSeoulMidnight(value: string) {
  return new Date(`${value}T00:00:00+09:00`)
}

function weekdayLabel(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', weekday: 'short' })
    .format(dateAtSeoulMidnight(value))
}

function shortDateLabel(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', month: 'numeric', day: 'numeric' })
    .format(dateAtSeoulMidnight(value))
}

function fullDateLabel(value: string) {
  const [, month, day] = value.split('-').map(Number)
  return `${month}월 ${day}일 (${weekdayLabel(value)})`
}

function formatWeekRange(start: string, end: string) {
  const startLabel = new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: 'numeric', day: 'numeric',
  }).format(dateAtSeoulMidnight(start))
  const endLabel = new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: 'numeric', day: 'numeric',
  }).format(dateAtSeoulMidnight(end))
  return `${startLabel} - ${endLabel}`
}

function shortTime(value: string) {
  return value.slice(0, 5)
}
