import { useId, useMemo, useState } from 'react'
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
            <p className="admin-weekly-calendar-eyebrow">운영 현황 · 조회 전용</p>
            <h1>주간 운영 캘린더</h1>
            <span>월요일부터 일요일까지 실제 생성된 운영 시간대와 표시 대상 예약을 확인합니다. 변경 작업은 관련 관리자 화면에서 진행합니다.</span>
          </div>
        </header>

        <section className="admin-weekly-calendar-toolbar" aria-label="조회 주 이동">
          <div className="admin-weekly-calendar-toolbar-top">
            <div>
              <p className="admin-weekly-calendar-kicker">선택 주 · 월요일부터 일요일</p>
              <h2 className="admin-weekly-calendar-week-range">
                {calendarQuery.isSuccess
                  ? formatWeekRange(dateKey(calendarQuery.data.weekStartDate), dateKey(calendarQuery.data.weekEndDate))
                  : '주간 운영 시간대 조회'}
              </h2>
            </div>
            <div className="admin-weekly-calendar-controls">
              <button type="button" aria-label="이전 주" onClick={() => setReferenceDate((date) => moveDate(date, -7))}>
                이전 주
              </button>
              <button type="button" className="today" disabled={referenceDate === currentDate} onClick={() => setReferenceDate(currentDate)}>
                오늘
              </button>
              <button type="button" aria-label="다음 주" onClick={() => setReferenceDate((date) => moveDate(date, 7))}>
                다음 주
              </button>
            </div>
          </div>
          <p className="admin-weekly-calendar-week-note">예약이 없는 실제 시간대도 함께 표시합니다.</p>
        </section>

        <section className="admin-weekly-calendar-results" aria-live="polite">
          {calendarQuery.isPending ? (
            <CalendarState message="주간 운영 일정을 불러오는 중입니다." />
          ) : null}
          {calendarQuery.isError ? (
            <CalendarError error={calendarQuery.error} retry={() => calendarQuery.refetch()} />
          ) : null}
          {calendarQuery.isSuccess ? <WeeklyCalendar calendar={calendarQuery.data} today={currentDate} /> : null}
        </section>
        <section className="admin-weekly-calendar-related" aria-labelledby="weekly-related-title">
          <h2 id="weekly-related-title">변경이 필요한 경우</h2>
          <p>이 캘린더는 조회 전용입니다. 변경 작업은 각 관리자 화면에서 진행해 주세요.</p>
          <nav aria-label="관련 관리자 화면">
            <Link to="/admin/schedule-configuration">일정 설정</Link>
            <Link to="/admin/schedule-closures">휴무·휴강</Link>
            <Link to="/admin/timeslots">시간대 관리</Link>
            <Link to="/admin/reservations">예약 관리</Link>
            <Link to="/admin/attendance">완료·노쇼</Link>
          </nav>
        </section>
      </div>
    </main>
  )
}

function WeeklyCalendar({ calendar, today }: { calendar: AdminWeeklyOperationsCalendarResponse; today: string }) {
  if (calendar.timeSlots.length === 0) {
    return (
      <section className="admin-weekly-calendar-state">
        <h2>이 주에 표시할 운영 시간대가 없습니다.</h2>
        <p>현재 조회 응답에 표시할 시간대가 없다는 뜻입니다. 휴무 여부나 조회 가능 기간을 의미하지 않습니다.</p>
      </section>
    )
  }

  return (
    <>
      <div className="admin-weekly-calendar-results-heading">
        <h2>한 주의 운영 시간대</h2>
        <p>실제 시간대 {calendar.timeSlots.length}개 · 표시 예약 {calendar.timeSlots.reduce((sum, slot) => sum + slot.reservations.length, 0)}건</p>
      </div>
      <WeeklyTimeTable calendar={calendar} today={today} />
    </>
  )
}

function WeeklyTimeTable({ calendar, today }: { calendar: AdminWeeklyOperationsCalendarResponse; today: string }) {
  const tableId = useId()
  const weekStart = dateKey(calendar.weekStartDate)
  const days = useMemo(() => Array.from({ length: 7 }, (_, index) => moveDate(weekStart, index)), [weekStart])
  const startTimes = useMemo(() => (
    [...new Set(calendar.timeSlots.map((slot) => slot.startTime))].sort(compareTimes)
  ), [calendar.timeSlots])
  const slotsByCell = useMemo(() => groupSlots(calendar.timeSlots), [calendar.timeSlots])

  return (
    <>
      <section className="admin-weekly-calendar-scroll" aria-label="주간 운영 시간표" tabIndex={0}>
        <table className="admin-weekly-calendar-timetable">
          <caption className="admin-weekly-calendar-sr-only">
            {formatWeekRange(dateKey(calendar.weekStartDate), dateKey(calendar.weekEndDate))} 실제 운영 시간대와 표시 예약
          </caption>
          <thead>
            <tr>
              <th scope="col" className="admin-weekly-calendar-time-axis">시작 시각</th>
              {days.map((day) => (
                <th scope="col" id={`${tableId}-day-${day}`} key={day} data-day-heading={day} aria-current={day === today ? 'date' : undefined}>
                  {fullDateLabel(day)}
                  <small>시간대 {calendar.timeSlots.filter((slot) => dateKey(slot.lessonDate) === day).length}개</small>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {startTimes.map((startTime) => (
              <tr key={startTime}>
                <th scope="row" className="admin-weekly-calendar-time-axis" id={`${tableId}-time-${startTime}`} data-time-heading={startTime}>
                  {shortTime(startTime)}
                </th>
                {days.map((day) => {
                  const slots = slotsByCell.get(`${day}|${startTime}`) ?? []
                  return (
                    <td key={day} headers={`${tableId}-time-${startTime} ${tableId}-day-${day}`}
                      data-calendar-cell={day} data-calendar-start-time={startTime}
                      className={slots.length === 0 ? 'admin-weekly-calendar-empty-cell' : undefined}>
                      {slots.length ? slots.map((slot) => <TimeSlotCard key={slot.timeSlotId} slot={slot} />) : (
                        <>
                          <span aria-hidden="true">&mdash;</span>
                          <span className="admin-weekly-calendar-sr-only">표시할 운영 시간대가 없습니다.</span>
                        </>
                      )}
                    </td>
                  )
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </section>
      <section className="admin-weekly-calendar-mobile" aria-label="날짜별 운영 시간대">
        {days.map((day) => {
          const daySlots = startTimes.flatMap((time) => slotsByCell.get(`${day}|${time}`) ?? [])
          return (
            <section className="admin-weekly-calendar-day" key={day} aria-labelledby={`${tableId}-mobile-${day}`} aria-current={day === today ? 'date' : undefined}>
              <header><h3 id={`${tableId}-mobile-${day}`}>{fullDateLabel(day)}</h3><p>시간대 {daySlots.length}개</p></header>
              {daySlots.length ? daySlots.map((slot) => <TimeSlotCard key={slot.timeSlotId} slot={slot} />)
                : <p className="admin-weekly-calendar-day-empty">표시할 운영 시간대가 없습니다.</p>}
            </section>
          )
        })}
      </section>
    </>
  )
}

function TimeSlotCard({ slot }: { slot: AdminWeeklyOperationsTimeSlotResponse }) {
  const capacityId = useId()
  const [expanded, setExpanded] = useState(false)
  const lessonDate = dateKey(slot.lessonDate)
  return (
    <article
      className={`admin-weekly-calendar-slot${slot.closed ? ' closed' : ''}`}
      data-timeslot-id={slot.timeSlotId}
      aria-label={`${fullDateLabel(lessonDate)} ${shortTime(slot.startTime)} 수업 시간대`}
    >
      <header>
        <div>
          <strong>{shortTime(slot.startTime)}–{shortTime(slot.endTime)}</strong>
        </div>
        {slot.closed ? <span className="admin-weekly-calendar-closed">신규 예약 마감</span> : null}
      </header>
      {slot.reservations.length === 0 ? (
        <p className="admin-weekly-calendar-empty-reservations">현재 표시 대상 예약이 없습니다.</p>
      ) : (
        <ul className="admin-weekly-calendar-reservations">
          {slot.reservations.map((reservation) => (
            <ReservationRow key={reservation.reservationId} reservation={reservation} />
          ))}
        </ul>
      )}
      <p className="admin-weekly-calendar-capacity">
        <span>전체 정원 <strong>{slot.totalCapacity}명</strong></span>
        <span>원형마장 <strong>{slot.roundArenaCapacity}명</strong></span>
      </p>
      <button type="button" className="admin-weekly-calendar-disclosure"
        aria-label={`${fullDateLabel(lessonDate)} ${shortTime(slot.startTime)} 클래스별 정원`}
        aria-expanded={expanded} aria-controls={capacityId} onClick={() => setExpanded((value) => !value)}>
        클래스별 정원 <span aria-hidden="true">{expanded ? '−' : '+'}</span>
      </button>
      <ul className="admin-weekly-calendar-class-capacities" id={capacityId} hidden={!expanded}>
        {Object.entries(slot.classCapacities).map(([ridingClass, capacity]) => (
          <li key={ridingClass}>
            <span>{RIDING_CLASS_LABELS[ridingClass as AdminWeeklyOperationsReservationResponseRidingClassEnum] ?? '클래스 확인 필요'}</span>
            <strong>{capacity}명</strong>
          </li>
        ))}
        {Object.keys(slot.classCapacities).length === 0 ? <li>클래스별 정원 정보가 없습니다.</li> : null}
      </ul>
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
