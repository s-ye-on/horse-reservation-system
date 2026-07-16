import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import type { MemberAvailableTimeSlotResponse, MemberReservationResponse, ReservationChangeResponse } from '@horse/api-client'
import {
  getReservationChangeErrorKind,
  reservationChangeApi,
  type ReservationChangeApi,
} from './reservation-change.api'
import './reservation-change-page.css'

const ACTIVE_STATUSES = new Set(['pending_admin_approval', 'pending_payment', 'confirmed'])
const CLASS_LABELS: Record<string, string> = {
  FIRST_RIDE: '왕초보', ROUND_BEGINNER: '원형초보', ROUND_TROT: '원형 속보',
  LARGE_ARENA_BEGINNER: '대마장초보', LARGE_ARENA_TROT: '대마장 속보',
  DRESSAGE: '마장마술', JUMPING: '장애물',
}

interface ReservationChangePageProps {
  api?: ReservationChangeApi
  today?: string
}

export function ReservationChangePage({ api = reservationChangeApi, today = getSeoulToday() }: ReservationChangePageProps) {
  const params = useParams()
  const reservationId = Number(params.reservationId)
  const validReservationId = Number.isSafeInteger(reservationId) && reservationId > 0
  const [selectedDate, setSelectedDate] = useState(today)
  const [targetTimeSlotId, setTargetTimeSlotId] = useState<number>()
  const [reason, setReason] = useState('')
  const queryClient = useQueryClient()

  const reservationsQuery = useQuery({
    queryKey: ['member', 'reservations'],
    queryFn: api.getMyReservations,
    enabled: validReservationId,
  })
  const reservation = reservationsQuery.data?.find((item) => item.reservationId === reservationId)
  const canChange = reservation && ACTIVE_STATUSES.has(reservation.status ?? '')
  const classType = reservation?.classType ?? ''
  const timeSlotsQuery = useQuery({
    queryKey: ['member', 'reservation-change', 'time-slots', selectedDate, classType],
    queryFn: () => api.getAvailableTimeSlots(selectedDate, classType),
    enabled: Boolean(canChange && classType),
  })
  const selectableTimeSlots = (timeSlotsQuery.data?.timeSlots ?? []).filter((slot) =>
    slot.reservable && slot.timeSlotId && !isCurrentTimeSlot(reservation, slot),
  )
  const previewQuery = useQuery({
    queryKey: ['member', 'reservation-change', 'preview', reservationId, targetTimeSlotId],
    queryFn: () => api.previewChange(reservationId, targetTimeSlotId as number),
    enabled: Boolean(canChange && targetTimeSlotId),
    retry: false,
  })
  const changeMutation = useMutation({
    mutationFn: () => api.changeReservation(reservationId, targetTimeSlotId as number, reason.trim() || undefined),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['member', 'reservations'] }),
  })

  if (!validReservationId) return <ChangeState error message="예약 번호가 올바르지 않습니다." />
  if (reservationsQuery.isPending) return <ChangeState message="예약 정보를 불러오는 중입니다." />
  if (reservationsQuery.isError) return <ChangeState error message={changeErrorMessage(reservationsQuery.error, '예약 정보를 불러오지 못했습니다.')} />
  if (!reservation) return <ChangeState error message="변경할 예약을 찾을 수 없습니다." />
  if (!canChange) return <ChangeState error message="현재 상태에서는 예약을 변경할 수 없습니다." />

  const chooseDate = (value: string) => {
    setSelectedDate(value)
    setTargetTimeSlotId(undefined)
    changeMutation.reset()
  }
  const chooseTimeSlot = (id: number) => {
    setTargetTimeSlotId(id)
    changeMutation.reset()
  }

  return (
    <main className="reservation-change-page">
      <div className="reservation-change-shell">
        <header className="reservation-change-header">
          <div><p>CHANGE LESSON</p><h1>예약 변경</h1><span>클래스는 유지되며, 서버 확인 결과를 본 뒤 변경합니다.</span></div>
          <Link to="/my/reservations">내 예약</Link>
        </header>

        <section className="reservation-change-current" aria-label="현재 예약">
          <h2>현재 예약</h2>
          <dl>
            <div><dt>클래스</dt><dd>{CLASS_LABELS[classType] ?? classType}</dd></div>
            <div><dt>날짜</dt><dd>{formatDate(reservation.lessonDate)}</dd></div>
            <div><dt>시간</dt><dd>{reservation.startTime?.slice(0, 5) ?? '-'}</dd></div>
          </dl>
        </section>

        <section className="reservation-change-selection">
          <h2>새 시간 선택</h2>
          <label htmlFor="change-date">날짜</label>
          <input id="change-date" type="date" min={today} max={addMonths(today, 3)} value={selectedDate} onChange={(event) => chooseDate(event.target.value)} />

          {timeSlotsQuery.isPending ? <p className="reservation-change-inline-state">예약 가능한 시간을 확인하는 중입니다.</p> : null}
          {timeSlotsQuery.isError ? <p className="reservation-change-error" role="alert">{changeErrorMessage(timeSlotsQuery.error, '시간대를 불러오지 못했습니다.')}</p> : null}
          {!timeSlotsQuery.isPending && !timeSlotsQuery.isError && selectableTimeSlots.length === 0 ? <p className="reservation-change-inline-state">선택한 날짜에 변경 가능한 시간이 없습니다.</p> : null}
          {selectableTimeSlots.length > 0 ? (
            <fieldset className="reservation-change-times">
              <legend>예약 가능한 시간</legend>
              {selectableTimeSlots.map((slot) => (
                <label key={slot.timeSlotId}>
                  <input type="radio" name="target-time-slot" checked={targetTimeSlotId === slot.timeSlotId} onChange={() => chooseTimeSlot(slot.timeSlotId as number)} />
                  <span>{slot.startTime?.slice(0, 5) ?? '-'}<small>잔여 {slot.remainingCapacity ?? 0}자리</small></span>
                </label>
              ))}
            </fieldset>
          ) : null}
        </section>

        {targetTimeSlotId && previewQuery.isPending ? <p className="reservation-change-inline-state">변경 정책을 확인하는 중입니다.</p> : null}
        {previewQuery.isError ? <p className="reservation-change-error" role="alert">{changeErrorMessage(previewQuery.error, '이 시간으로 변경할 수 없습니다.')}</p> : null}
        {previewQuery.data ? (
          <section className="reservation-change-preview" aria-live="polite">
            <h2>변경 전 확인</h2>
            <dl>
              <div><dt>변경 일시</dt><dd>{formatDate(previewQuery.data.targetLessonDate)} {previewQuery.data.targetStartTime?.slice(0, 5)}</dd></div>
              <div><dt>변경 기준</dt><dd>{timingLabel(previewQuery.data.timing)}</dd></div>
              <div><dt>쿠폰 처리</dt><dd>{couponActionLabel(previewQuery.data.couponAction)}</dd></div>
              <div><dt>무료 변경권</dt><dd>{previewQuery.data.freeChangeUsed ? '1회 사용' : '사용 안 함'}</dd></div>
            </dl>
            <label htmlFor="change-reason">변경 사유 <span>선택</span></label>
            <textarea id="change-reason" maxLength={500} value={reason} onChange={(event) => setReason(event.target.value)} />
          </section>
        ) : null}

        {changeMutation.isError ? <p className="reservation-change-error" role="alert">{changeErrorMessage(changeMutation.error, '예약을 변경하지 못했습니다.')}</p> : null}
        {changeMutation.data ? <ChangeResult response={changeMutation.data} /> : (
          <div className="reservation-change-actions">
            <Link to="/my/reservations">변경하지 않기</Link>
            <button type="button" disabled={!previewQuery.data || changeMutation.isPending} onClick={() => changeMutation.mutate()}>{changeMutation.isPending ? '변경 중' : '이 시간으로 변경'}</button>
          </div>
        )}
      </div>
    </main>
  )
}

function ChangeResult({ response }: { response: ReservationChangeResponse }) {
  return (
    <section className="reservation-change-result" aria-live="polite">
      <strong>예약이 변경되었습니다</strong>
      <p>{formatDate(response.lessonDate)} {response.startTime?.slice(0, 5)}</p>
      <span>무료 변경권 {response.freeChangeUsed ? '사용' : '미사용'} · 쿠폰 {couponActionLabel(response.couponAction)}</span>
      <Link to="/my/reservations">변경된 예약 확인</Link>
    </section>
  )
}

function ChangeState({ message, error = false }: { message: string; error?: boolean }) {
  return <main className="reservation-change-page"><div className="reservation-change-shell"><section className="reservation-change-state" role={error ? 'alert' : undefined}><p>{message}</p><Link to="/my/reservations">내 예약으로 돌아가기</Link></section></div></main>
}

function isCurrentTimeSlot(reservation: MemberReservationResponse | undefined, slot: MemberAvailableTimeSlotResponse) {
  return formatIsoDate(reservation?.lessonDate) === formatIsoDate(slot.lessonDate) && reservation?.startTime === slot.startTime
}

function changeErrorMessage(error: unknown, fallback: string) {
  const kind = getReservationChangeErrorKind(error)
  if (kind === 'unauthorized') return '회원 인증을 확인할 수 없습니다. 다시 로그인해 주세요.'
  if (kind === 'validation') return '예약 또는 변경할 시간 정보를 다시 확인해 주세요.'
  if (kind === 'conflict') return '선택한 시간이 마감되었거나 변경 정책상 처리할 수 없습니다. 다른 시간을 선택해 주세요.'
  return `${fallback} 잠시 후 다시 시도해 주세요.`
}

function timingLabel(timing?: string) {
  const normalized = timing?.toUpperCase()
  if (normalized === 'BEFORE_CUTOFF') return '변경 마감 전'
  if (normalized?.startsWith('AFTER_CUTOFF')) return '변경 마감 후'
  return timing ?? '-'
}

function couponActionLabel(action?: string) {
  if (action === 'FREE_CHANGE_USED' || action === 'free_change_used') return '무료 변경권 사용'
  if (action === 'DEDUCT' || action === 'deduct') return '1회 차감'
  if (action === 'RETURN' || action === 'return') return '반환 또는 재점유'
  if (action === 'NONE' || action === 'none') return '처리 없음'
  return action ?? '-'
}

function formatDate(date?: Date) {
  if (!date) return '-'
  return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: 'long', day: 'numeric', weekday: 'short' }).format(date)
}

function formatIsoDate(date?: Date) {
  if (!date) return ''
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).format(date)
}

function getSeoulToday() {
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date())
}

function addMonths(date: string, months: number) {
  const [year, month, day] = date.split('-').map(Number)
  const targetMonth = month - 1 + months
  const lastDay = new Date(Date.UTC(year, targetMonth + 1, 0)).getUTCDate()
  const target = new Date(Date.UTC(year, targetMonth, Math.min(day, lastDay)))
  return target.toISOString().slice(0, 10)
}
