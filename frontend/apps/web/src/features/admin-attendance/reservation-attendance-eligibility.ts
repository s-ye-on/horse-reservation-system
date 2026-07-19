import type { AdminReservationResponse, ReservationActionAvailabilityResponse } from '@horse/api-client'

export function isAttendanceProcessable(reservation: AdminReservationResponse) {
  return reservation.actions?.complete?.allowed === true
    && reservation.actions.noShow?.allowed === true
}

export function attendanceBlockedMessage(reservation: AdminReservationResponse) {
  const reason = blockedReason(reservation.actions?.complete, reservation.actions?.noShow)
  if (reason === 'RESERVATION_LESSON_NOT_STARTED') return '수업 시작 전에는 완료 또는 노쇼 처리할 수 없습니다.'
  if (reason === 'RESERVATION_INVALID_STATUS') return '현재 예약 상태에서는 출석을 처리할 수 없습니다.'
  if (!reason) return '출석 처리 가능 여부를 확인할 수 없습니다.'
  return '현재 예약은 완료 또는 노쇼 처리할 수 없습니다.'
}

export function isLessonToday(reservation: AdminReservationResponse, now = new Date()) {
  return seoulDateValue(reservation.lessonDate) === seoulDateValue(now)
}

function blockedReason(
  complete?: ReservationActionAvailabilityResponse,
  noShow?: ReservationActionAvailabilityResponse,
) {
  return complete?.blockedReason ?? noShow?.blockedReason
}

function seoulDateValue(value?: Date) {
  if (!value) return ''
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit',
  }).formatToParts(value)
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return `${values.year}-${values.month}-${values.day}`
}
