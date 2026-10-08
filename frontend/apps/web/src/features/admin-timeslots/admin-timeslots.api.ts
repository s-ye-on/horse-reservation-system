import {
  AdminTimeSlotControllerApi,
  ResponseError,
  type TimeSlotCapacityUpdateRequest,
  type TimeSlotCreateRequest,
  type TimeSlotResponse,
} from '@horse/api-client'
import { bearerApiConfiguration } from '../../api/web-api-configuration'

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

const api = new AdminTimeSlotControllerApi(bearerApiConfiguration)

export async function readTimeSlotError(error: unknown): Promise<{ message: string; field?: string }> {
  if (!(error instanceof ResponseError)) return { message: '요청 결과를 확인하지 못했습니다. 최신 시간대 목록을 확인해 주세요.' }
  const body = await error.response.clone().json().catch(() => ({})) as { code?: string }
  const messages: Record<string, { message: string; field?: string }> = {
    TIMESLOT_INVALID_LESSON_DATE: { message: '수업 날짜를 확인해 주세요.', field: 'lessonDate' },
    TIMESLOT_INVALID_START_TIME: { message: '시작 시각을 확인해 주세요.', field: 'startTime' },
    TIMESLOT_INVALID_LESSON_INTERVAL: { message: '45분 수업이 같은 날짜 안에 끝나는 시작 시각을 입력해 주세요.', field: 'startTime' },
    TIMESLOT_INVALID_TOTAL_CAPACITY: { message: '전체 정원은 0명에서 8명 사이여야 합니다.', field: 'totalCapacity' },
    TIMESLOT_INVALID_ROUND_ARENA_CAPACITY: { message: '원형마장 정원은 전체 이하이며 0명에서 4명 사이여야 합니다.', field: 'roundArenaCapacity' },
    TIMESLOT_INVALID_CLASS_CAPACITIES: { message: '9개 클래스 정원을 각각 0명에서 8명 사이로 입력해 주세요.', field: 'classCapacities' },
    TIMESLOT_ALREADY_EXISTS: { message: '같은 날짜와 시작 시각의 시간대가 이미 있습니다. 최신 목록을 확인해 주세요.' },
    TIMESLOT_NOT_FOUND: { message: '시간대가 변경되었거나 더 이상 존재하지 않습니다. 최신 목록을 확인해 주세요.' },
    TIMESLOT_LESSON_ALREADY_STARTED: { message: '이미 시작된 수업 시간대는 변경할 수 없습니다.' },
    TIMESLOT_CAPACITY_BELOW_OCCUPANCY: { message: '현재 예약 인원보다 정원을 작게 설정할 수 없습니다. 요청 정원을 다시 확인해 주세요.' },
    TIMESLOT_CLOSURE_ALREADY_IN_PROGRESS: { message: '이미 휴강 처리가 진행 중입니다. 휴무·휴강 관리에서 확인해 주세요.' },
    TIMESLOT_CLOSURE_WITHDRAWAL_NOT_ALLOWED: { message: '이미 처리된 예약이 있어 휴강을 철회할 수 없습니다. 휴무·휴강 관리에서 확인해 주세요.' },
    TIMESLOT_CLOSURE_NOT_FOUND: { message: '철회하거나 재개할 관리자 휴강 작업이 없습니다. 휴무·휴강 관리에서 확인해 주세요.' },
    SCHEDULE_CONFIG_SYNC_IN_PROGRESS: { message: '일정 설정을 반영 중이라 시간대 생성·정원 변경을 처리할 수 없습니다. 잠시 후 다시 확인해 주세요.' },
    SCHEDULE_DATE_NOT_RESERVABLE: { message: '휴무 처리 중이거나 휴무인 날짜의 시간대 생성·정원 변경은 할 수 없습니다.' },
    SCHEDULE_INVALID_SCHEDULE_DATE: { message: '운영 일정에 포함된 수업 날짜인지 확인해 주세요.', field: 'lessonDate' },
  }
  if (body?.code && messages[body.code]) return messages[body.code]
  if (error.response.status === 401) return { message: '로그인이 필요합니다. 다시 로그인해 주세요.' }
  if (error.response.status === 403) return { message: '관리자 권한이 없어 시간대를 변경할 수 없습니다.' }
  if (error.response.status === 400) return { message: '정원 또는 시간대 입력이 올바르지 않습니다. 입력 내용을 확인해 주세요.' }
  if (error.response.status === 404) return { message: '대상을 찾을 수 없습니다. 최신 목록을 확인해 주세요.' }
  if (error.response.status === 409) return { message: '현재 운영 상태와 요청이 충돌했습니다. 최신 목록을 확인해 주세요.' }
  return { message: '요청 결과를 확인하지 못했습니다. 최신 시간대 목록을 확인해 주세요.' }
}

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
