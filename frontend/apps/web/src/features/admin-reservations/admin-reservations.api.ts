import {
  AdminReservationCancelControllerApi,
  AdminReservationChangeControllerApi,
  AdminPendingPaymentRestoreControllerApi,
  AdminReservationConfirmControllerApi,
  AdminReservationQueryControllerApi,
  AdminReservationRejectControllerApi,
  AdminTimeSlotControllerApi,
  ResponseError,
  type AdminReservationPageResponse,
  type ReservationCancellationPreviewResponse,
  type TimeSlotResponse,
} from '@horse/api-client'

export interface AdminReservationsApi {
  getReservations(status: string, page: number, size: number): Promise<AdminReservationPageResponse>
  confirm(reservationId: number): Promise<void>
  reject(reservationId: number, reason: string): Promise<void>
  restore(reservationId: number, memo: string): Promise<void>
  getTimeSlots(): Promise<TimeSlotResponse[]>
  previewCancellation(reservationId: number, responsibility: string): Promise<ReservationCancellationPreviewResponse>
  change(reservationId: number, targetTimeSlotId: number, memo: string): Promise<void>
  cancel(reservationId: number, responsibility: string, couponAction: string, memo: string): Promise<void>
}

export type AdminReservationErrorKind = 'forbidden' | 'validation' | 'conflict' | 'unknown'

export function getAdminReservationErrorKind(error: unknown): AdminReservationErrorKind {
  if (!(error instanceof ResponseError)) return 'unknown'
  if (error.response.status === 403) return 'forbidden'
  if (error.response.status === 400) return 'validation'
  if (error.response.status === 409) return 'conflict'
  return 'unknown'
}

const queryApi = new AdminReservationQueryControllerApi()
const confirmApi = new AdminReservationConfirmControllerApi()
const rejectApi = new AdminReservationRejectControllerApi()
const restoreApi = new AdminPendingPaymentRestoreControllerApi()
const timeSlotApi = new AdminTimeSlotControllerApi()
const changeApi = new AdminReservationChangeControllerApi()
const cancelApi = new AdminReservationCancelControllerApi()

export const adminReservationsApi: AdminReservationsApi = {
  getReservations: (status, page, size) => queryApi.getReservations({ status, page, size }),
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
