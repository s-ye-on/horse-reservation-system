import {
  AdminManualReservationControllerApi, AdminMemberQueryControllerApi, AdminTimeSlotControllerApi, AdminReservationQueryControllerApi, ResponseError,
  type AdminManualReservationRequest, type AdminMemberPageResponse, type AdminMemberResponse, type AdminReservationResponse, type ReservationApplicationResponse, type TimeSlotResponse,
} from '@horse/api-client'
import { bearerApiConfiguration } from '../../api/web-api-configuration'
import { getAdminReservationCommandErrorMessage } from './admin-reservations.api'

export interface AdminManualReservationApi {
  getMembers(page: number, size: number, query?: string): Promise<AdminMemberPageResponse>
  getMember(memberId: number): Promise<AdminMemberResponse>
  getTimeSlots(): Promise<TimeSlotResponse[]>
  getReservation(reservationId: number): Promise<AdminReservationResponse>
  create(request: AdminManualReservationRequest, key: string): Promise<ReservationApplicationResponse>
}

const members = new AdminMemberQueryControllerApi(bearerApiConfiguration)
const timeSlots = new AdminTimeSlotControllerApi(bearerApiConfiguration)
const reservations = new AdminManualReservationControllerApi(bearerApiConfiguration)
const reservationQuery = new AdminReservationQueryControllerApi(bearerApiConfiguration)

export const adminManualReservationApi: AdminManualReservationApi = {
  getMembers: (page, size, query) => members.getMembers({ page, size, query }),
  getMember: (memberId) => members.getMember({ memberId }),
  getTimeSlots: () => timeSlots.getTimeSlots(),
  getReservation: (reservationId) => reservationQuery.getReservation({ reservationId }),
  create: (request, key) => reservations.createAdminManualReservation({ idempotencyKey: key, adminManualReservationRequest: request }),
}

export async function describeManualReservationError(error: unknown): Promise<{ message: string; uncertain: boolean; fieldErrors?: Record<string, string> }> {
  if (!(error instanceof ResponseError)) return { message: '생성 결과를 확인하지 못했습니다. 입력을 바꾸지 않고 동일 요청 결과를 다시 확인해 주세요.', uncertain: true }
  let body: { code?: string; fieldErrors?: { field: string; message: string }[] } = {}
  try {
    const value: unknown = await error.response.clone().json()
    if (value && typeof value === 'object') body = value as typeof body
  } catch { /* Fall back to the HTTP response category. */ }
  if (body.code === 'RESERVATION_IDEMPOTENCY_KEY_CONFLICT' || body.code === 'RESERVATION_IDEMPOTENCY_STATE_CONFLICT') {
    return { message: '기존 생성 요청과 충돌했습니다. 새 요청으로 다시 보내지 말고 예약 목록에서 생성 여부를 확인해 주세요.', uncertain: true }
  }
  const status = error.response.status
  if (status >= 500 || status === 408 || status === 429) {
    return { message: body.code === 'SCHEDULE_CONFIG_SYNC_IN_PROGRESS'
      ? '일정을 반영 중이라 수동 예약을 생성할 수 없습니다. 반영 후 동일 요청 결과를 다시 확인해 주세요.'
      : '생성 결과를 확인하지 못했습니다. 동일 요청 결과를 다시 확인해 주세요.', uncertain: true }
  }
  if (status === 401) return { message: '로그인이 필요합니다. 다시 로그인한 뒤 요청을 확인해 주세요.', uncertain: false }
  if (status === 403) return { message: '관리자 권한이 없어 수동 예약을 생성할 수 없습니다.', uncertain: false }
  if (status === 404 && ['MEMBER_NOT_FOUND', 'TIMESLOT_NOT_FOUND'].includes(body.code ?? '')) return { message: '회원 또는 수업 시간 정보가 변경되었습니다. 최신 목록에서 다시 선택해 주세요.', uncertain: false }
  if (status === 400 && ['COMMON_INVALID_REQUEST', 'RESERVATION_INVALID_RIDING_CLASS', 'RESERVATION_INVALID_ADMIN_MEMO'].includes(body.code ?? '')) return {
    message: '회원·수업 시간·클래스와 사유를 확인해 주세요.', uncertain: false,
    fieldErrors: Array.isArray(body.fieldErrors) ? Object.fromEntries(body.fieldErrors.filter((item) => item && typeof item.field === 'string' && typeof item.message === 'string').map((item) => [item.field, item.message])) : undefined,
  }
  if (status === 409 && ['RESERVATION_LESSON_ALREADY_STARTED', 'TIMESLOT_CAPACITY_EXCEEDED', 'TIMESLOT_CLOSED', 'RESERVATION_OVERLAPPING_ACTIVE_RESERVATION', 'RESERVATION_DUPLICATE_ACTIVE_TIME_SLOT', 'SCHEDULE_DATE_NOT_RESERVABLE', 'COUPON_HOLD_STATE_CONFLICT'].includes(body.code ?? '')) return { message: await getAdminReservationCommandErrorMessage(error) ?? '현재 자격·정원·중복 예약 또는 운영 상태로 생성할 수 없습니다. 최신 정보를 확인해 주세요.', uncertain: false }
  return { message: '생성 결과를 확인하지 못했습니다. 동일 요청 결과를 다시 확인해 주세요.', uncertain: true }
}
