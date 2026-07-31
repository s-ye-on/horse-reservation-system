import {
  MemberAvailableTimeSlotsControllerApi,
  ReservationApplicationControllerApi,
  ResponseError,
  type MemberAvailableTimeSlotResponse,
  type ReservationApplicationResponse,
} from '@horse/api-client'
import { apiConfiguration } from '../../config/api-configuration'

export interface ReservationApplicationApi {
  getSelectedTimeSlot(date: string, classType: string, timeSlotId: number): Promise<MemberAvailableTimeSlotResponse | undefined>
  apply(timeSlotId: number, classType: string, idempotencyKey: string): Promise<ReservationApplicationResponse>
}

export type ReservationApplicationErrorKind = 'unauthorized' | 'validation' | 'conflict' | 'unknown'

export function getReservationApplicationErrorKind(error: unknown): ReservationApplicationErrorKind {
  if (!(error instanceof ResponseError)) return 'unknown'
  if (error.response.status === 401 || error.response.status === 403) return 'unauthorized'
  if (error.response.status === 400 || error.response.status === 404) return 'validation'
  if (error.response.status === 409) return 'conflict'
  return 'unknown'
}

const timeSlotsApi = new MemberAvailableTimeSlotsControllerApi(apiConfiguration)
const applicationApi = new ReservationApplicationControllerApi(apiConfiguration)

export const reservationApplicationApi: ReservationApplicationApi = {
  getSelectedTimeSlot: async (date, classType, timeSlotId) => {
    const response = await timeSlotsApi.getAvailableTimeSlots({ date, classType })
    return response.timeSlots?.find((timeSlot) => timeSlot.timeSlotId === timeSlotId)
  },
  apply: (timeSlotId, classType, idempotencyKey) => applicationApi.apply({
    idempotencyKey,
    reservationApplicationRequest: { timeSlotId, classType },
  }),
}
