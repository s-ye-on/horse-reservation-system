import {
  MemberReservationCancelControllerApi,
  MemberReservationQueryControllerApi,
  ResponseError,
  type MemberReservationResponse,
  type ReservationCancellationPreviewResponse,
  type ReservationCancelResponse,
} from '@horse/api-client'
import { apiConfiguration } from '../../config/api-configuration'

export interface ReservationCancelApi {
  getMyReservations(): Promise<MemberReservationResponse[]>
  previewCancellation(reservationId: number): Promise<ReservationCancellationPreviewResponse>
  cancelReservation(reservationId: number, reason: string): Promise<ReservationCancelResponse>
}

export type ReservationCancelErrorKind = 'unauthorized' | 'validation' | 'conflict' | 'unknown'

export function getReservationCancelErrorKind(error: unknown): ReservationCancelErrorKind {
  if (!(error instanceof ResponseError)) return 'unknown'
  if (error.response.status === 401 || error.response.status === 403) return 'unauthorized'
  if (error.response.status === 400 || error.response.status === 404) return 'validation'
  if (error.response.status === 409) return 'conflict'
  return 'unknown'
}

const reservationQueryApi = new MemberReservationQueryControllerApi(apiConfiguration)
const reservationCancelClient = new MemberReservationCancelControllerApi(apiConfiguration)

export const reservationCancelApi: ReservationCancelApi = {
  getMyReservations: async () => (await reservationQueryApi.getMyReservations()).content ?? [],
  previewCancellation: (reservationId) => reservationCancelClient.preview2({ reservationId }),
  cancelReservation: (reservationId, reason) => reservationCancelClient.cancel({
    reservationId,
    memberReservationCancelRequest: { reason },
  }),
}
