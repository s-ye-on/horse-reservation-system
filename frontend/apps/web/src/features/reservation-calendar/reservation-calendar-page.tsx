import { useEffect, useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import type { MemberAvailableTimeSlotResponse } from '@horse/api-client'
import {
  getReservationCalendarErrorKind,
  reservationCalendarApi,
  type ReservationCalendarApi,
} from './reservation-calendar.api'
import './reservation-calendar-page.css'

const CLASS_LABELS: Record<string, string> = {
  FIRST_RIDE: '왕초보', ROUND_BEGINNER: '원형초보', ROUND_TROT: '원형 속보',
  LARGE_ARENA_BEGINNER: '대마장초보', LARGE_ARENA_TROT: '대마장 속보',
  CANTER_BEGINNER: '구보초보', CANTER: '구보',
  DRESSAGE: '마장마술', JUMPING: '장애물',
}

function seoulDateKey(now = new Date()) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(now)
  const value = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return `${value.year}-${value.month}-${value.day}`
}

function addDays(dateKey: string, amount: number) {
  const date = new Date(`${dateKey}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + amount)
  return date.toISOString().slice(0, 10)
}

function addMonths(dateKey: string, amount: number) {
  const [year, month, day] = dateKey.split('-').map(Number)
  const first = new Date(Date.UTC(year, month - 1 + amount, 1))
  const lastDay = new Date(Date.UTC(first.getUTCFullYear(), first.getUTCMonth() + 1, 0)).getUTCDate()
  first.setUTCDate(Math.min(day, lastDay))
  return first.toISOString().slice(0, 10)
}

function errorMessage(error: unknown) {
  const kind = getReservationCalendarErrorKind(error)
  if (kind === 'forbidden') return '회원 권한을 확인할 수 없습니다. 다시 로그인해 주세요.'
  if (kind === 'validation') return '선택한 날짜 또는 클래스는 조회할 수 없습니다.'
  return '예약 가능한 시간을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

export function ReservationCalendarPage({ api = reservationCalendarApi, now = new Date() }: { api?: ReservationCalendarApi; now?: Date }) {
  const today = useMemo(() => seoulDateKey(now), [now])
  const lastDate = useMemo(() => addMonths(today, 3), [today])
  const [selectedDate, setSelectedDate] = useState(today)
  const [selectedClass, setSelectedClass] = useState('')
  const classesQuery = useQuery({ queryKey: ['member', 'available-classes'], queryFn: api.getAvailableClasses })
  const classes = useMemo(() => classesQuery.data?.availableRidingClasses ?? [], [classesQuery.data])

  useEffect(() => {
    if (!selectedClass && classes.length > 0) setSelectedClass(classes[0])
  }, [classes, selectedClass])

  const timeSlotsQuery = useQuery({
    queryKey: ['member', 'available-timeslots', selectedDate, selectedClass],
    queryFn: () => api.getAvailableTimeSlots(selectedDate, selectedClass),
    enabled: Boolean(selectedClass),
  })

  const changeDate = (nextDate: string) => {
    if (nextDate < today || nextDate > lastDate) return
    setSelectedDate(nextDate)
  }

  if (classesQuery.isPending) return <CalendarState message="예약 가능 클래스를 확인하는 중입니다." />
  if (classesQuery.isError) return <CalendarState error message={errorMessage(classesQuery.error)} />

  return (
    <main className="reservation-calendar-page">
      <div className="reservation-calendar-shell">
        <header className="reservation-calendar-header">
          <p>LESSON BOOKING</p>
          <h1>수업 예약</h1>
          <span>현재 등급은 <strong>{CLASS_LABELS[classesQuery.data.currentGeneralGrade ?? ''] ?? '-'}</strong>입니다.</span>
        </header>

        {classes.length === 0 ? <CalendarState embedded message="현재 예약 가능한 클래스가 없습니다. 관리자에게 문의해 주세요." /> : <>
          <section className="reservation-calendar-controls" aria-label="예약 조건 선택">
            <div>
              <h2>클래스</h2>
              <div className="reservation-calendar-classes" role="radiogroup" aria-label="예약 가능 클래스">
                {classes.map((ridingClass) => (
                  <button
                    key={ridingClass}
                    type="button"
                    role="radio"
                    aria-checked={selectedClass === ridingClass}
                    className={selectedClass === ridingClass ? 'selected' : ''}
                    onClick={() => setSelectedClass(ridingClass)}
                  >{CLASS_LABELS[ridingClass] ?? ridingClass}</button>
                ))}
              </div>
            </div>
            <div className="reservation-calendar-date-control">
              <h2>날짜</h2>
              <div>
                <button type="button" aria-label="이전 날짜" disabled={selectedDate === today} onClick={() => changeDate(addDays(selectedDate, -1))}>이전</button>
                <input aria-label="수업 날짜" type="date" min={today} max={lastDate} value={selectedDate} onChange={(event) => changeDate(event.target.value)} />
                <button type="button" aria-label="다음 날짜" disabled={selectedDate === lastDate} onClick={() => changeDate(addDays(selectedDate, 1))}>다음</button>
              </div>
              <span>{formatDate(selectedDate)}</span>
            </div>
          </section>

          <section className="reservation-calendar-times" aria-live="polite">
            <div className="reservation-calendar-times-heading"><div><h2>시간 선택</h2><p>{CLASS_LABELS[selectedClass] ?? selectedClass} · {formatDate(selectedDate)}</p></div></div>
            {timeSlotsQuery.isPending || timeSlotsQuery.isFetching ? <CalendarState embedded message="시간대를 불러오는 중입니다." /> : null}
            {timeSlotsQuery.isError ? <CalendarState embedded error message={errorMessage(timeSlotsQuery.error)} /> : null}
            {timeSlotsQuery.isSuccess && (timeSlotsQuery.data.timeSlots?.length ?? 0) === 0 ? <CalendarState embedded message="이 날짜에는 등록된 수업 시간이 없습니다." /> : null}
            {timeSlotsQuery.isSuccess && (timeSlotsQuery.data.timeSlots?.length ?? 0) > 0 ? (
              <div className="reservation-calendar-slot-list">
                {timeSlotsQuery.data.timeSlots?.map((slot) => <TimeSlot key={slot.timeSlotId} slot={slot} classType={selectedClass} date={selectedDate} />)}
              </div>
            ) : null}
          </section>
        </>}
      </div>
    </main>
  )
}

function TimeSlot({ slot, classType, date }: { slot: MemberAvailableTimeSlotResponse; classType: string; date: string }) {
  const state = slotState(slot)
  const content = <><strong>{slot.startTime?.slice(0, 5) ?? '-'}</strong><span>{state}</span>{slot.reservable ? <small>잔여 {slot.remainingCapacity ?? 0}자리</small> : null}</>
  if (!slot.reservable) return <div className={`reservation-calendar-slot unavailable ${slot.unavailableReason?.toLowerCase() ?? ''}`} aria-disabled="true">{content}</div>
  const query = new URLSearchParams({ timeSlotId: String(slot.timeSlotId), classType, date })
  return <Link className="reservation-calendar-slot" to={`/reservations/new?${query.toString()}`}>{content}</Link>
}

function slotState(slot: MemberAvailableTimeSlotResponse) {
  if (slot.unavailableReason === 'CLOSED' || slot.closed) return '운영 마감'
  if (slot.unavailableReason === 'FULL') return '예약 마감'
  if (slot.unavailableReason === 'NOT_ELIGIBLE') return '예약 불가 클래스'
  return '예약 가능'
}

function formatDate(dateKey: string) {
  return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', month: 'long', day: 'numeric', weekday: 'long' }).format(new Date(`${dateKey}T00:00:00+09:00`))
}

function CalendarState({ message, error = false, embedded = false }: { message: string; error?: boolean; embedded?: boolean }) {
  const content = <section className="reservation-calendar-state" role={error ? 'alert' : undefined}>{message}</section>
  return embedded ? content : <main className="reservation-calendar-page"><div className="reservation-calendar-shell">{content}</div></main>
}
