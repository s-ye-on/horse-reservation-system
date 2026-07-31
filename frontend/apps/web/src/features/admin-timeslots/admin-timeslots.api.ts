import {
  AdminTimeSlotControllerApi,
  ResponseError,
  type TimeSlotCapacityUpdateRequest,
  type TimeSlotCreateRequest,
  type TimeSlotResponse,
} from '@horse/api-client'
import { apiConfiguration } from '../../config/api-configuration'

export interface AdminTimeSlotsApi {
  getTimeSlots(): Promise<TimeSlotResponse[]>
  createTimeSlot(request: TimeSlotCreateRequest): Promise<TimeSlotResponse>
  changeCapacity(timeSlotId: number, request: TimeSlotCapacityUpdateRequest): Promise<TimeSlotResponse>
  changeClosedStatus(timeSlotId: number, closed: boolean): Promise<TimeSlotResponse>
}

export type TimeSlotErrorKind = 'forbidden' | 'validation' | 'conflict' | 'unknown'

export function getTimeSlotErrorKind(error: unknown): TimeSlotErrorKind {
  if (!(error instanceof ResponseError)) return 'unknown'
  if (error.response.status === 403) return 'forbidden'
  if (error.response.status === 400) return 'validation'
  if (error.response.status === 409) return 'conflict'
  return 'unknown'
}

const api = new AdminTimeSlotControllerApi(apiConfiguration)

export const adminTimeSlotsApi: AdminTimeSlotsApi = {
  getTimeSlots: () => api.getTimeSlots(),
  createTimeSlot: (request) => api.createTimeSlot({ timeSlotCreateRequest: request }),
  changeCapacity: (timeSlotId, request) => api.changeCapacity({
    timeSlotId,
    timeSlotCapacityUpdateRequest: request,
  }),
  changeClosedStatus: (timeSlotId, closed) => api.changeClosedStatus({
    timeSlotId,
    timeSlotStatusUpdateRequest: { closed },
  }),
}
