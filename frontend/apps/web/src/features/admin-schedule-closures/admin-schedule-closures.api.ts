import {
  AdminScheduleDateControllerApi,
  AdminTimeSlotClosureControllerApi,
  AdminTimeSlotControllerApi,
  ResponseError,
  type ErrorResponse,
  type ReservationCancelResponse,
  type ScheduleDateClosureImpactResponse,
  type ScheduleDateResponse,
  type TimeSlotClosureResponse,
  type TimeSlotResponse,
} from '@horse/api-client'
import { bearerApiConfiguration } from '../../api/web-api-configuration'

export interface AdminScheduleClosuresApi {
  getScheduleDate(scheduleDate: Date): Promise<ScheduleDateResponse>
  getDateImpact(scheduleDate: Date): Promise<ScheduleDateClosureImpactResponse>
  startDateClosing(scheduleDate: Date, reason: string, expectedVersion: number): Promise<ScheduleDateClosureImpactResponse>
  cancelDateReservation(scheduleDate: Date, reservationId: number, memo: string): Promise<ReservationCancelResponse>
  completeDateClosing(scheduleDate: Date, reason: string, expectedVersion: number): Promise<ScheduleDateClosureImpactResponse>
  cancelDateClosing(scheduleDate: Date, reason: string, expectedVersion: number): Promise<ScheduleDateClosureImpactResponse>
  getTimeSlots(): Promise<TimeSlotResponse[]>
  getTimeSlotClosure(timeSlotId: number): Promise<TimeSlotClosureResponse | null>
  startTimeSlotClosure(timeSlotId: number, reason: string): Promise<TimeSlotClosureResponse>
  cancelTimeSlotReservation(timeSlotId: number, reservationId: number, memo: string): Promise<ReservationCancelResponse>
  completeTimeSlotClosure(timeSlotId: number, reason: string, expectedVersion: number): Promise<TimeSlotClosureResponse>
  withdrawTimeSlotClosure(timeSlotId: number, reason: string, expectedVersion: number): Promise<TimeSlotClosureResponse>
  reopenTimeSlot(timeSlotId: number, reason: string, expectedVersion: number): Promise<TimeSlotClosureResponse>
}

export interface ScheduleClosureApiError {
  status?: number
  code?: string
  message: string
  details?: Record<string, unknown>
}

const CLOSURE_ERROR_MESSAGES: Record<string, string> = {
  SCHEDULE_DATE_VERSION_CONFLICT: '다른 변경이 먼저 반영되었습니다. 최신 상태를 확인해 주세요.',
  TIMESLOT_CLOSURE_VERSION_CONFLICT: '다른 변경이 먼저 반영되었습니다. 최신 상태를 확인해 주세요.',
  TIMESLOT_CLOSURE_IMPACTS_UNRESOLVED: '아직 처리해야 할 예약이 남아 있습니다.',
  TIMESLOT_ACTIVE_RESERVATIONS_EXIST_ON_CLOSURE_DATE: '아직 처리해야 할 예약이 남아 있습니다.',
  TIMESLOT_CLOSURE_WITHDRAWAL_NOT_ALLOWED: '이미 처리된 예약이 있어 휴강을 철회할 수 없습니다.',
  SCHEDULE_DATE_NOT_CLOSING: '현재 휴무 처리 중인 날짜가 아닙니다. 최신 상태를 확인해 주세요.',
  TIMESLOT_CLOSURE_NOT_IN_PROGRESS: '현재 진행 중인 휴강 작업이 아닙니다. 최신 상태를 확인해 주세요.',
  TIMESLOT_LESSON_ALREADY_STARTED: '이미 시작된 수업 시간은 휴강 처리할 수 없습니다.',
}

export async function readScheduleClosureApiError(error: unknown): Promise<ScheduleClosureApiError> {
  if (!(error instanceof ResponseError)) {
    return { message: '휴무 운영 상태를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.' }
  }
  const fallback = error.response.status === 403
    ? '관리자 권한이 없어 휴무 운영 상태를 변경할 수 없습니다.'
    : error.response.status === 409
      ? '다른 작업으로 상태가 변경되었습니다. 최신 상태를 다시 조회해 주세요.'
      : error.response.status === 503
        ? '시간표를 갱신하고 있습니다. 잠시 후 다시 시도해 주세요.'
        : '휴무 운영 상태를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
  try {
    const body = await error.response.clone().json() as Partial<ErrorResponse>
    return {
      status: error.response.status,
      code: body.code,
      message: CLOSURE_ERROR_MESSAGES[body.code ?? ''] ?? body.message ?? fallback,
      details: body.details as Record<string, unknown> | undefined,
    }
  } catch {
    return { status: error.response.status, message: fallback }
  }
}

const scheduleDateApi = new AdminScheduleDateControllerApi(bearerApiConfiguration)
const timeSlotClosureApi = new AdminTimeSlotClosureControllerApi(bearerApiConfiguration)
const timeSlotApi = new AdminTimeSlotControllerApi(bearerApiConfiguration)

export const adminScheduleClosuresApi: AdminScheduleClosuresApi = {
  getScheduleDate: (scheduleDate) => scheduleDateApi.getScheduleDate({ scheduleDate }),
  getDateImpact: (scheduleDate) => scheduleDateApi.getClosureImpact({ scheduleDate }),
  startDateClosing: (scheduleDate, reason, expectedVersion) => scheduleDateApi.startClosing({
    scheduleDate,
    scheduleDateActionRequest: { reason, expectedVersion },
  }),
  cancelDateReservation: (scheduleDate, reservationId, memo) => scheduleDateApi.cancelReservation1({
    scheduleDate,
    reservationId,
    scheduleClosureCancelRequest: { memo },
  }),
  completeDateClosing: (scheduleDate, reason, expectedVersion) => scheduleDateApi.close({
    scheduleDate,
    scheduleDateActionRequest: { reason, expectedVersion },
  }),
  cancelDateClosing: (scheduleDate, reason, expectedVersion) => scheduleDateApi.cancelClosing({
    scheduleDate,
    scheduleDateActionRequest: { reason, expectedVersion },
  }),
  getTimeSlots: () => timeSlotApi.getTimeSlots(),
  getTimeSlotClosure: async (timeSlotId) => {
    try {
      return await timeSlotClosureApi.getClosure({ timeSlotId })
    } catch (error) {
      if (error instanceof ResponseError && error.response.status === 404) return null
      throw error
    }
  },
  startTimeSlotClosure: (timeSlotId, reason) => timeSlotClosureApi.start({
    timeSlotId,
    timeSlotClosureStartRequest: { reason },
  }),
  cancelTimeSlotReservation: (timeSlotId, reservationId, memo) => timeSlotClosureApi.cancelReservation({
    timeSlotId,
    reservationId,
    scheduleClosureCancelRequest: { memo },
  }),
  completeTimeSlotClosure: (timeSlotId, reason, expectedVersion) => timeSlotClosureApi.complete({
    timeSlotId,
    timeSlotClosureActionRequest: { reason, expectedVersion },
  }),
  withdrawTimeSlotClosure: (timeSlotId, reason, expectedVersion) => timeSlotClosureApi.withdraw({
    timeSlotId,
    timeSlotClosureActionRequest: { reason, expectedVersion },
  }),
  reopenTimeSlot: (timeSlotId, reason, expectedVersion) => timeSlotClosureApi.reopen({
    timeSlotId,
    timeSlotClosureActionRequest: { reason, expectedVersion },
  }),
}
