import type { AdminManualReservationRequest } from '@horse/api-client'

export interface ManualReservationAttempt {
  key: string
  executionId?: string
  request: AdminManualReservationRequest
  memberName: string
  memberPhone: string
  lessonDate: string
  startTime: string
}

function storageKey(subject: string) { return `horse:admin-manual-reservation:${encodeURIComponent(subject)}` }

export function readManualReservationAttempt(subject: string): ManualReservationAttempt | undefined {
  const raw = sessionStorage.getItem(storageKey(subject))
  if (!raw) return undefined
  const value: unknown = JSON.parse(raw)
  if (!value || typeof value !== 'object' || !('key' in value) || !('request' in value)) throw new Error('Invalid saved request')
  const candidate = value as ManualReservationAttempt
  if (typeof candidate.key !== 'string' || !candidate.key || candidate.key.length > 255
    || !Number.isSafeInteger(candidate.request?.memberId) || candidate.request.memberId <= 0
    || !Number.isSafeInteger(candidate.request?.timeSlotId) || candidate.request.timeSlotId <= 0
    || typeof candidate.request.classType !== 'string' || !candidate.request.classType
    || typeof candidate.request.reason !== 'string' || !candidate.request.reason.trim() || candidate.request.reason.length > 500
    || ![candidate.memberName, candidate.memberPhone, candidate.lessonDate, candidate.startTime].every((item) => typeof item === 'string')
    || !/^\d{4}-\d{2}-\d{2}$/.test(candidate.lessonDate) || Number.isNaN(new Date(candidate.lessonDate).getTime())
    || !/^\d{2}:\d{2}(:\d{2})?$/.test(candidate.startTime)) throw new Error('Invalid saved request')
  return candidate
}

export function saveManualReservationAttempt(subject: string, attempt: ManualReservationAttempt) {
  sessionStorage.setItem(storageKey(subject), JSON.stringify(attempt))
}

export function clearManualReservationAttempt(subject: string, expectedExecutionId?: string) {
  if (expectedExecutionId && readManualReservationAttempt(subject)?.executionId !== expectedExecutionId) return false
  sessionStorage.removeItem(storageKey(subject))
  return true
}
