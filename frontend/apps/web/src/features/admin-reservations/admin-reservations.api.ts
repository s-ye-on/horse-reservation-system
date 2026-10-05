import {
  AdminReservationCancelControllerApi,
  AdminReservationChangeControllerApi,
  AdminPendingPaymentRestoreControllerApi,
  AdminReservationConfirmControllerApi,
  AdminReservationQueryControllerApi,
  AdminReservationRejectControllerApi,
  AdminTimeSlotControllerApi,
  ResponseError,
  type AdminReservationResponse,
  type AdminReservationPageResponse,
  type ReservationCancellationPreviewResponse,
  type TimeSlotResponse,
} from '@horse/api-client'
import { bearerApiConfiguration } from '../../api/web-api-configuration'

export interface AdminReservationsApi {
  getReservations(status: string, page: number, size: number): Promise<AdminReservationPageResponse>
  getReservation(reservationId: number): Promise<AdminReservationResponse>
  confirm(reservationId: number): Promise<void>
  reject(reservationId: number, reason: string): Promise<void>
  restore(reservationId: number, memo: string): Promise<void>
  getTimeSlots(): Promise<TimeSlotResponse[]>
  previewCancellation(reservationId: number, responsibility: string): Promise<ReservationCancellationPreviewResponse>
  change(reservationId: number, targetTimeSlotId: number, memo: string): Promise<void>
  cancel(reservationId: number, responsibility: string, couponAction: string, memo: string): Promise<void>
}

export type AdminReservationErrorKind = 'unauthorized' | 'forbidden' | 'not-found' | 'validation' | 'conflict' | 'unknown'

export function getAdminReservationErrorKind(error: unknown): AdminReservationErrorKind {
  if (!(error instanceof ResponseError)) return 'unknown'
  if (error.response.status === 401) return 'unauthorized'
  if (error.response.status === 403) return 'forbidden'
  if (error.response.status === 404) return 'not-found'
  if (error.response.status === 400) return 'validation'
  if (error.response.status === 409) return 'conflict'
  return 'unknown'
}

export async function getAdminReservationCommandErrorMessage(error: unknown): Promise<string | undefined> {
  if (!(error instanceof ResponseError)) return undefined
  try {
    const body: unknown = await error.response.clone().json()
    if (!body || typeof body !== 'object' || !('code' in body) || typeof body.code !== 'string') return undefined
    const messages: Record<string, string> = {
      RESERVATION_LESSON_ALREADY_STARTED: '이미 수업이 시작되어 처리할 수 없습니다. 최신 예약 상태를 확인해 주세요.',
      RESERVATION_PAYMENT_EXPIRED: '입금 확인 기한이 지났습니다. 최신 예약 상태를 확인해 주세요.',
      TIMESLOT_CLOSURE_COMMAND_NOT_ALLOWED: '현재 휴강 처리된 수업 시간입니다. 운영 상태를 확인해 주세요.',
      TIMESLOT_CAPACITY_EXCEEDED: '대상 시간대의 정원이 마감되었습니다. 다른 시간대를 확인해 주세요.',
      TIMESLOT_CLOSED: '대상 시간대가 마감되었습니다. 다른 시간대를 확인해 주세요.',
      RESERVATION_OVERLAPPING_ACTIVE_RESERVATION: '회원의 다른 예약과 시간이 겹칩니다. 최신 예약을 확인해 주세요.',
      RESERVATION_DUPLICATE_ACTIVE_TIME_SLOT: '회원에게 같은 시간대의 예약이 있습니다. 최신 예약을 확인해 주세요.',
      RESERVATION_CHANGE_NOT_ALLOWED: '현재 예약 조건에서는 시간 변경이 허용되지 않습니다. 최신 상태를 확인해 주세요.',
      RESERVATION_WEEKEND_SAME_DAY_CHANGE_NOT_ALLOWED: '주말 당일에는 같은 날짜의 다른 시간으로 변경할 수 없습니다.',
      COUPON_EXPIRED_FOR_LESSON: '변경할 수업일에 쿠폰이 유효하지 않습니다. 쿠폰 상태를 확인해 주세요.',
      COUPON_HOLD_STATE_CONFLICT: '쿠폰 점유 상태가 변경되었습니다. 최신 상태를 확인해 주세요.',
      COUPON_FREE_CHANGE_ALREADY_USED: '이 쿠폰의 무료 변경권을 이미 사용했습니다.',
      SCHEDULE_DATE_NOT_RESERVABLE: '대상 날짜가 휴무 처리 중이거나 휴무입니다. 운영 상태를 확인해 주세요.',
      SCHEDULE_CONFIG_SYNC_IN_PROGRESS: '일정 반영 중이라 이번 요청을 처리하지 못했습니다. 최신 상태를 확인한 뒤 다시 판단해 주세요.',
    }
    return messages[body.code]
  } catch { return undefined }
}

const queryApi = new AdminReservationQueryControllerApi(bearerApiConfiguration)
const confirmApi = new AdminReservationConfirmControllerApi(bearerApiConfiguration)
const rejectApi = new AdminReservationRejectControllerApi(bearerApiConfiguration)
const restoreApi = new AdminPendingPaymentRestoreControllerApi(bearerApiConfiguration)
const timeSlotApi = new AdminTimeSlotControllerApi(bearerApiConfiguration)
const changeApi = new AdminReservationChangeControllerApi(bearerApiConfiguration)
const cancelApi = new AdminReservationCancelControllerApi(bearerApiConfiguration)

export const adminReservationsApi: AdminReservationsApi = {
  getReservations: (status, page, size) => queryApi.getReservations({ status, page, size }),
  getReservation: (reservationId) => queryApi.getReservation({ reservationId }),
  confirm: async (reservationId) => {
    await confirmApi.confirm({ reservationId })
  },
  reject: async (reservationId, reason) => {
    await rejectApi.reject({ reservationId, reservationRejectRequest: { reason } })
  },
  restore: async (reservationId, memo) => {
    await restoreApi.restore({
      reservationId,
      reservationPaymentRestoreRequest: { memo },
    })
  },
  getTimeSlots: () => timeSlotApi.getTimeSlots(),
  previewCancellation: (reservationId, responsibility) => cancelApi.preview3({ reservationId, responsibility }),
  change: async (reservationId, targetTimeSlotId, memo) => {
    await changeApi.change1({ reservationId, adminReservationChangeRequest: { targetTimeSlotId, memo } })
  },
  cancel: async (reservationId, responsibility, couponAction, memo) => {
    await cancelApi.cancel1({
      reservationId,
      adminReservationCancelRequest: { responsibility, couponAction, memo },
    })
  },
}
