import {
  MemberAvailableRidingClassesControllerApi,
  MemberAvailableTimeSlotsControllerApi,
  ResponseError,
  type MemberAvailableRidingClassesResponse,
  type MemberAvailableTimeSlotsResponse,
} from '@horse/api-client'

export interface ReservationCalendarApi {
  getAvailableClasses(): Promise<MemberAvailableRidingClassesResponse>
  getAvailableTimeSlots(date: string, classType: string): Promise<MemberAvailableTimeSlotsResponse>
}

export type ReservationCalendarErrorKind = 'forbidden' | 'validation' | 'unknown'

export function getReservationCalendarErrorKind(error: unknown): ReservationCalendarErrorKind {
  if (!(error instanceof ResponseError)) return 'unknown'
  if (error.response.status === 403) return 'forbidden'
  if (error.response.status === 400) return 'validation'
  return 'unknown'
}

const classesApi = new MemberAvailableRidingClassesControllerApi()
const timeSlotsApi = new MemberAvailableTimeSlotsControllerApi()

export const reservationCalendarApi: ReservationCalendarApi = {
  getAvailableClasses: () => classesApi.getAvailableRidingClasses(),
  getAvailableTimeSlots: (date, classType) => timeSlotsApi.getAvailableTimeSlots({ date, classType }),
}
