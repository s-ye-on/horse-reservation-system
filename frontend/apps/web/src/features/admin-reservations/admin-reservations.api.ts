import {
  AdminPendingPaymentRestoreControllerApi,
  AdminReservationConfirmControllerApi,
  AdminReservationQueryControllerApi,
  AdminReservationRejectControllerApi,
  ResponseError,
  type AdminReservationResponse,
} from '@horse/api-client'

const ACTIONABLE_STATUSES = [
  'pending_admin_approval',
  'pending_payment',
  'payment_expired',
] as const

export interface AdminReservationsApi {
  getActionableReservations(): Promise<AdminReservationResponse[]>
  confirm(reservationId: number): Promise<void>
  reject(reservationId: number, reason: string): Promise<void>
  restore(reservationId: number, memo: string): Promise<void>
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

export const adminReservationsApi: AdminReservationsApi = {
  getActionableReservations: async () => {
    const pages = await Promise.all(ACTIONABLE_STATUSES.map((status) =>
      queryApi.getReservations({ status, page: 0, size: 100 })))
    return pages.flatMap((page) => page.content ?? [])
  },
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
}
