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

export interface ReservationChangeApi {
  getMyReservations(): Promise<MemberReservationResponse[]>
  getAvailableTimeSlots(date: string, classType: string): Promise<MemberAvailableTimeSlotsResponse>
  previewChange(reservationId: number, targetTimeSlotId: number): Promise<ReservationChangePreviewResponse>
  changeReservation(reservationId: number, targetTimeSlotId: number, reason?: string): Promise<ReservationChangeResponse>
}

export type ReservationChangeErrorKind = 'unauthorized' | 'validation' | 'conflict' | 'unknown'

export function getReservationChangeErrorKind(error: unknown): ReservationChangeErrorKind {
  if (!(error instanceof ResponseError)) return 'unknown'
  if (error.response.status === 401 || error.response.status === 403) return 'unauthorized'
  if (error.response.status === 400 || error.response.status === 404) return 'validation'
  if (error.response.status === 409) return 'conflict'
  return 'unknown'
}

const reservationQueryApi = new MemberReservationQueryControllerApi()
const timeSlotsApi = new MemberAvailableTimeSlotsControllerApi()
const reservationChangeClient = new MemberReservationChangeControllerApi()

export const reservationChangeApi: ReservationChangeApi = {
  getMyReservations: async () => (await reservationQueryApi.getMyReservations()).content ?? [],
  getAvailableTimeSlots: (date, classType) => timeSlotsApi.getAvailableTimeSlots({ date, classType }),
  previewChange: (reservationId, targetTimeSlotId) => reservationChangeClient.previewReservationChange({ reservationId, targetTimeSlotId }),
  changeReservation: (reservationId, targetTimeSlotId, reason) => reservationChangeClient.change({
    reservationId,
    memberReservationChangeRequest: { targetTimeSlotId, reason: reason || undefined },
  }),
}
