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
  if (kind === 'validation') return '선택한 날짜 또는 수업 종류는 조회할 수 없습니다.'
  return '예약 가능한 시간을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

export function ReservationCalendarPage({ api = reservationCalendarApi, now = new Date() }: { api?: ReservationCalendarApi; now?: Date }) {
  const today = useMemo(() => seoulDateKey(now), [now])
  const lastDate = useMemo(() => addMonths(today, 3), [today])
  const [selectedDate, setSelectedDate] = useState(today)
  const [selectedClass, setSelectedClass] = useState('')
  const [selectedTimeSlotId, setSelectedTimeSlotId] = useState<number>()
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
  const selectedTimeSlot = timeSlotsQuery.data?.timeSlots?.find((slot) => slot.timeSlotId === selectedTimeSlotId)

  useEffect(() => {
    if (selectedTimeSlotId !== undefined && !selectedTimeSlot?.reservable) {
      setSelectedTimeSlotId(undefined)
    }
  }, [selectedTimeSlot, selectedTimeSlotId])

  const changeDate = (nextDate: string) => {
    if (nextDate < today || nextDate > lastDate) return
    setSelectedDate(nextDate)
    setSelectedTimeSlotId(undefined)
  }

  const changeClass = (nextClass: string) => {
    setSelectedClass(nextClass)
    setSelectedTimeSlotId(undefined)
  }

  if (classesQuery.isPending) return <CalendarState message="예약 가능한 수업 종류를 확인하는 중입니다." />
  if (classesQuery.isError) return <CalendarState error message={errorMessage(classesQuery.error)} />

  return (
    <main className="reservation-calendar-page">
      <div className="reservation-calendar-shell">
        <header className="reservation-calendar-header">
          <p>LESSON BOOKING</p>
          <h1>수업 예약</h1>
          <span>현재 등급은 <strong>{CLASS_LABELS[classesQuery.data.currentGeneralGrade ?? ''] ?? '-'}</strong>입니다.</span>
        </header>

        {classes.length === 0 ? <CalendarState embedded message="현재 예약 가능한 수업 종류가 없습니다. 관리자에게 문의해 주세요." /> : (
          <div className="reservation-booking-layout">
            <div className="reservation-booking-flow">
              <section className="reservation-booking-card" aria-labelledby="class-selection-title">
                <span className="reservation-booking-step">1단계 · 수업 종류</span>
                <h2 id="class-selection-title">예약할 수업을 선택해 주세요</h2>
                <p className="reservation-booking-guidance">현재 일반 기승 단계는 <strong>{CLASS_LABELS[classesQuery.data.currentGeneralGrade ?? ''] ?? '-'}</strong>입니다.</p>
                <fieldset className="reservation-calendar-classes">
                  <legend>예약 가능한 수업 종류</legend>
                  {classes.map((ridingClass) => (
                    <label className={selectedClass === ridingClass ? 'selected' : ''} key={ridingClass}>
                      <input
                        type="radio"
                        name="reservation-class"
                        checked={selectedClass === ridingClass}
                        onChange={() => changeClass(ridingClass)}
                      />
                      <span><strong>{CLASS_LABELS[ridingClass] ?? ridingClass}</strong><small>선택한 날짜의 예약 가능한 시간을 확인합니다.</small></span>
                      <b>예약 가능</b>
                    </label>
                  ))}
                </fieldset>
              </section>

              <section className="reservation-booking-card reservation-calendar-date-control" aria-labelledby="date-selection-title">
                <span className="reservation-booking-step">2단계 · 날짜</span>
                <h2 id="date-selection-title">수업 날짜를 선택해 주세요</h2>
                <p className="reservation-booking-guidance">오늘부터 예약 가능한 기간 안에서 날짜를 이동할 수 있습니다.</p>
                <div>
                  <button type="button" aria-label="이전 날짜" disabled={selectedDate === today} onClick={() => changeDate(addDays(selectedDate, -1))}>이전</button>
                  <input aria-label="수업 날짜" type="date" min={today} max={lastDate} value={selectedDate} onChange={(event) => changeDate(event.target.value)} />
                  <button type="button" aria-label="다음 날짜" disabled={selectedDate === lastDate} onClick={() => changeDate(addDays(selectedDate, 1))}>다음</button>
                </div>
                <strong className="reservation-calendar-selected-date">{formatDate(selectedDate)}</strong>
              </section>

              <section className="reservation-booking-card reservation-calendar-times" aria-live="polite" aria-labelledby="time-selection-title">
                <span className="reservation-booking-step">3단계 · 수업 시간</span>
                <div className="reservation-calendar-times-heading"><div><h2 id="time-selection-title">예약할 시간을 선택해 주세요</h2><p>{CLASS_LABELS[selectedClass] ?? selectedClass} · {formatDate(selectedDate)}</p></div></div>
                {timeSlotsQuery.isPending || timeSlotsQuery.isFetching ? <CalendarState embedded message="수업 시간을 불러오는 중입니다." /> : null}
                {timeSlotsQuery.isError ? <CalendarState embedded error message={errorMessage(timeSlotsQuery.error)} /> : null}
                {timeSlotsQuery.isSuccess && (timeSlotsQuery.data.timeSlots?.length ?? 0) === 0 ? <CalendarState embedded message="이 날짜에는 등록된 수업 시간이 없습니다." /> : null}
                {timeSlotsQuery.isSuccess && (timeSlotsQuery.data.timeSlots?.length ?? 0) > 0 ? (
                  <fieldset className="reservation-calendar-slot-list">
                    <legend>수업 시간과 잔여석</legend>
                    {timeSlotsQuery.data.timeSlots?.map((slot) => (
                      <TimeSlot
                        key={slot.timeSlotId}
                        slot={slot}
                        selected={selectedTimeSlotId === slot.timeSlotId}
                        onSelect={() => setSelectedTimeSlotId(slot.timeSlotId)}
                      />
                    ))}
                  </fieldset>
                ) : null}
              </section>
            </div>

            <aside className="reservation-booking-summary" aria-label="예약 요약">
              <span className="reservation-booking-step">다음 단계 · 신청 확인</span>
              <h2>예약 요약</h2>
              <dl>
                <div><dt>수업 종류</dt><dd>{CLASS_LABELS[selectedClass] ?? selectedClass}</dd></div>
                <div><dt>수업 날짜</dt><dd>{formatDate(selectedDate)}</dd></div>
                <div><dt>수업 시간</dt><dd>{selectedTimeSlot?.startTime?.slice(0, 5) ?? '시간을 선택해 주세요'}</dd></div>
                <div><dt>잔여석</dt><dd>{selectedTimeSlot ? `${selectedTimeSlot.remainingCapacity ?? 0}자리` : '-'}</dd></div>
              </dl>
              <p>다음 화면에서 최신 예약 가능 여부를 다시 확인한 뒤 신청합니다.</p>
              {selectedTimeSlot ? (
                <Link to={`/reservations/new?${new URLSearchParams({ timeSlotId: String(selectedTimeSlot.timeSlotId), classType: selectedClass, date: selectedDate }).toString()}`}>예약 내용 확인</Link>
              ) : <button type="button" disabled>수업 시간을 선택해 주세요</button>}
            </aside>
          </div>
        )}
      </div>
    </main>
  )
}

function TimeSlot({ slot, selected, onSelect }: { slot: MemberAvailableTimeSlotResponse; selected: boolean; onSelect: () => void }) {
  const state = slotState(slot)
  return (
    <label className={`reservation-calendar-slot${selected ? ' selected' : ''}${slot.reservable ? '' : ` unavailable ${slot.unavailableReason?.toLowerCase() ?? ''}`}`}>
      <input
        type="radio"
        name="reservation-time-slot"
        checked={selected}
        disabled={!slot.reservable}
        onChange={onSelect}
      />
      <span><strong>{slot.startTime?.slice(0, 5) ?? '-'}</strong><small>{slot.reservable ? '예약 가능' : '선택할 수 없음'}</small></span>
      <b>{slot.reservable ? `잔여 ${slot.remainingCapacity ?? 0}자리` : state}</b>
    </label>
  )
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
