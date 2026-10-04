import { ResponseError } from '@horse/api-client'
import { getAdminAttendanceErrorKind } from './admin-attendance.api'

const CLASS_LABELS: Record<string, string> = {
  FIRST_RIDE: '왕초보', ROUND_BEGINNER: '원형초보', ROUND_TROT: '원형 속보',
  LARGE_ARENA_BEGINNER: '대마장초보', LARGE_ARENA_TROT: '대마장 속보',
  CANTER_BEGINNER: '구보초보', CANTER: '구보', DRESSAGE: '마장마술', JUMPING: '장애물',
}

export function attendanceClassLabel(value: string) {
  return CLASS_LABELS[value] ?? '수업 종류 확인 필요'
}

export function attendanceCouponTypeLabel(value?: string) {
  switch (value) {
    case 'general': return '일반 기승 쿠폰'
    case 'dressage': return '마장마술 쿠폰'
    case 'jumping': return '장애물 쿠폰'
    default: return '쿠폰 종류 확인 필요'
  }
}

export function attendanceErrorMessage(code?: string | null) {
  if (code === 'RESERVATION_LESSON_NOT_STARTED') return '아직 수업 시작 전이라 처리할 수 없습니다.'
  if (code === 'TIMESLOT_CLOSURE_COMMAND_NOT_ALLOWED') return '현재 휴강 처리된 수업 시간입니다. 운영 상태를 확인해 주세요.'
  if (code === 'RESERVATION_INVALID_STATUS') return '예약 상태가 변경되었습니다. 최신 상태를 확인해 주세요.'
  if (code === 'RESERVATION_NOT_FOUND') return '예약을 찾을 수 없습니다. 최신 상태를 확인해 주세요.'
  if (code?.startsWith('COUPON_')) return '쿠폰 상태가 변경되었습니다. 최신 상태를 확인한 뒤 다시 판단해 주세요.'
  if (code === 'RESERVATION_INVALID_COUPON_ACTION' || code === 'RESERVATION_INVALID_ADMIN_MEMO' || code === 'COMMON_INVALID_REQUEST') return '쿠폰 처리와 관리자 메모를 다시 확인해 주세요.'
  return '처리 결과를 확인하지 못했습니다. 최신 상태를 확인해 주세요.'
}

export async function attendanceRequestError(error: unknown) {
  const kind = getAdminAttendanceErrorKind(error)
  if (kind === 'forbidden') return '관리자 권한이 없어 처리할 수 없습니다.'
  if (error instanceof ResponseError) {
    try {
      const body: unknown = await error.response.clone().json()
      if (body && typeof body === 'object' && 'code' in body && typeof body.code === 'string') return attendanceErrorMessage(body.code)
    } catch { /* A transport error may not contain a JSON error response. */ }
  }
  if (kind === 'validation') return '쿠폰 처리와 관리자 메모를 다시 확인해 주세요.'
  if (kind === 'conflict') return attendanceErrorMessage('RESERVATION_INVALID_STATUS')
  return attendanceErrorMessage()
}
