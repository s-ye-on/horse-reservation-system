import {
  MemberAvailableTimeSlotsControllerApi,
  MemberReservationChangeControllerApi,
  MemberReservationQueryControllerApi,
  ResponseError,
  type MemberAvailableTimeSlotsResponse,
  type MemberReservationResponse,
  type ReservationChangePreviewResponse,
  type ReservationChangeResponse,
} from '@horse/api-client'
import { bearerApiConfiguration } from '../../api/web-api-configuration'

export interface ReservationChangeApi {
  getMyReservation(reservationId: number): Promise<MemberReservationResponse>
  getAvailableTimeSlots(date: string, classType: string): Promise<MemberAvailableTimeSlotsResponse>
  previewChange(reservationId: number, targetTimeSlotId: number): Promise<ReservationChangePreviewResponse>
  changeReservation(reservationId: number, targetTimeSlotId: number, reason?: string): Promise<ReservationChangeResponse>
}

export type ReservationChangeErrorKind = 'unauthorized' | 'not-found' | 'validation' | 'conflict' | 'unknown'

export function getReservationChangeErrorKind(error: unknown): ReservationChangeErrorKind {
  if (!(error instanceof ResponseError)) return 'unknown'
  if (error.response.status === 401 || error.response.status === 403) return 'unauthorized'
  if (error.response.status === 404) return 'not-found'
  if (error.response.status === 400) return 'validation'
  if (error.response.status === 409) return 'conflict'
  return 'unknown'
}

const reservationQueryApi = new MemberReservationQueryControllerApi(bearerApiConfiguration)
const timeSlotsApi = new MemberAvailableTimeSlotsControllerApi(bearerApiConfiguration)
const reservationChangeClient = new MemberReservationChangeControllerApi(bearerApiConfiguration)

export const reservationChangeApi: ReservationChangeApi = {
  getMyReservation: (reservationId) => reservationQueryApi.getMyReservation({ reservationId }),
  getAvailableTimeSlots: (date, classType) => timeSlotsApi.getAvailableTimeSlots({ date, classType }),
  previewChange: (reservationId, targetTimeSlotId) => reservationChangeClient.previewReservationChange({ reservationId, targetTimeSlotId }),
  changeReservation: (reservationId, targetTimeSlotId, reason) => reservationChangeClient.change({
    reservationId,
    memberReservationChangeRequest: { targetTimeSlotId, reason: reason || undefined },
  }),
}
