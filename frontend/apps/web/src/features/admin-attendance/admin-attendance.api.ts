import {
  AdminReservationCompletionControllerApi,
  AdminReservationNoShowControllerApi,
  AdminReservationQueryControllerApi,
  ResponseError,
  type AdminReservationResponse,
  type ReservationCompletionResponse,
  type ReservationNoShowResponse,
} from '@horse/api-client'

export interface AdminAttendanceApi {
  getConfirmedReservations(): Promise<AdminReservationResponse[]>
  complete(reservationId: number): Promise<ReservationCompletionResponse>
  noShow(reservationId: number, couponAction: string, memo: string): Promise<ReservationNoShowResponse>
}

export type AdminAttendanceErrorKind = 'forbidden' | 'validation' | 'conflict' | 'unknown'

export function getAdminAttendanceErrorKind(error: unknown): AdminAttendanceErrorKind {
  if (!(error instanceof ResponseError)) return 'unknown'
  if (error.response.status === 403) return 'forbidden'
  if (error.response.status === 400) return 'validation'
  if (error.response.status === 409) return 'conflict'
  return 'unknown'
}

const queryApi = new AdminReservationQueryControllerApi()
const completionApi = new AdminReservationCompletionControllerApi()
const noShowApi = new AdminReservationNoShowControllerApi()
const ATTENDANCE_HISTORY_START = new Date('1970-01-01T00:00:00.000Z')

export const adminAttendanceApi: AdminAttendanceApi = {
  getConfirmedReservations: async () => {
    const page = await queryApi.getReservations({
      status: 'confirmed',
      lessonDateFrom: ATTENDANCE_HISTORY_START,
      page: 0,
      size: 100,
    })
    return page.content ?? []
  },
  complete: (reservationId) => completionApi.complete({ reservationId }),
  noShow: (reservationId, couponAction, memo) => noShowApi.process({
    reservationId,
    reservationNoShowRequest: { couponAction, memo },
  }),
}
