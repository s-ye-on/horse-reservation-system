import {
  AdminRecurringHolidayControllerApi,
  AdminScheduleSynchronizationControllerApi,
  AdminScheduleTemplateControllerApi,
  ResponseError,
  type ErrorResponse,
  type RecurringHolidayImpactResponse,
  type RecurringHolidayMutationResponse,
  type RecurringHolidayPreviewRequest,
  type RecurringHolidayRequest,
  type RecurringHolidayResponse,
  type ScheduleSynchronizationResponse,
  type ScheduleSynchronizationResultResponse,
  type ScheduleTemplateImpactResponse,
  type ScheduleTemplateCapacityConflictDetails,
  ScheduleTemplateMutationConflictResponseFromJSON,
  type ScheduleTemplateMutationResponse,
  type ScheduleTemplateFutureReservationsResponse,
  type ScheduleTemplatePreviewRequest,
  type ScheduleTemplateRequest,
  type ScheduleTemplateResponse,
} from '@horse/api-client'
import { bearerApiConfiguration } from '../../api/web-api-configuration'

export interface AdminScheduleConfigurationApi {
  getTemplates(): Promise<ScheduleTemplateResponse[]>
  previewTemplate(request: ScheduleTemplatePreviewRequest): Promise<ScheduleTemplateImpactResponse>
  createTemplate(request: ScheduleTemplateRequest): Promise<ScheduleTemplateMutationResponse>
  updateTemplate(templateId: number, request: ScheduleTemplateRequest): Promise<ScheduleTemplateMutationResponse>
  deleteTemplate(templateId: number, expectedConfigVersion: number, reason: string): Promise<ScheduleTemplateMutationResponse>
  getFutureOccupyingReservations(templateId: number): Promise<ScheduleTemplateFutureReservationsResponse>
  getHolidays(): Promise<RecurringHolidayResponse[]>
  previewHoliday(request: RecurringHolidayPreviewRequest): Promise<RecurringHolidayImpactResponse>
  createHoliday(request: RecurringHolidayRequest): Promise<RecurringHolidayMutationResponse>
  updateHoliday(holidayId: number, request: RecurringHolidayRequest): Promise<RecurringHolidayMutationResponse>
  changeHolidayActivation(holidayId: number, active: boolean, expectedConfigVersion: number, reason: string): Promise<RecurringHolidayMutationResponse>
  getSynchronization(): Promise<ScheduleSynchronizationResponse>
  retrySynchronization(pendingVersion: number): Promise<ScheduleSynchronizationResultResponse>
}

export interface ScheduleApiError {
  status?: number
  code?: string
  message: string
  capacityConflict?: ScheduleTemplateCapacityConflictDetails
}

export class ScheduleClientError extends Error {
  readonly status: number
  readonly code: string

  constructor(
    status: number,
    code: string,
    message: string,
  ) {
    super(message)
    this.status = status
    this.code = code
  }
}

export async function readScheduleApiError(error: unknown): Promise<ScheduleApiError> {
  if (error instanceof ScheduleClientError) {
    return { status: error.status, code: error.code, message: error.message }
  }
  if (!(error instanceof ResponseError)) {
    return { message: '일정 설정을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.' }
  }
  const fallback = error.response.status === 403
    ? '관리자 권한이 없어 일정 설정을 변경할 수 없습니다.'
    : '일정 설정을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
  try {
    const body = await error.response.clone().json() as Partial<ErrorResponse>
    if (error.response.status === 409 && body.code === 'TIMESLOT_CAPACITY_BELOW_OCCUPANCY') {
      const conflict = ScheduleTemplateMutationConflictResponseFromJSON(body)
      if (conflict.code === 'TIMESLOT_CAPACITY_BELOW_OCCUPANCY') {
        return {
          status: error.response.status,
          code: conflict.code,
          message: conflict.message,
          capacityConflict: conflict.details,
        }
      }
    }
    return { status: error.response.status, code: body.code, message: body.message ?? fallback }
  } catch {
    return { status: error.response.status, message: fallback }
  }
}

const templateApi = new AdminScheduleTemplateControllerApi(bearerApiConfiguration)
const holidayApi = new AdminRecurringHolidayControllerApi(bearerApiConfiguration)
const synchronizationApi = new AdminScheduleSynchronizationControllerApi(bearerApiConfiguration)

export const adminScheduleConfigurationApi: AdminScheduleConfigurationApi = {
  getTemplates: () => templateApi.getTemplates(),
  previewTemplate: (request) => templateApi.preview({ scheduleTemplatePreviewRequest: request }),
  createTemplate: (request) => templateApi.create({ scheduleTemplateRequest: request }),
  updateTemplate: (templateId, request) => templateApi.update({ templateId, scheduleTemplateRequest: request }),
  deleteTemplate: (templateId, expectedConfigVersion, reason) => templateApi._delete({
    templateId,
    scheduleTemplateDeleteRequest: { expectedConfigVersion, reason },
  }),
  getFutureOccupyingReservations: (templateId) => templateApi.getFutureOccupyingReservations({ templateId }),
  getHolidays: () => holidayApi.getHolidays(),
  previewHoliday: (request) => holidayApi.preview1({ recurringHolidayPreviewRequest: request }),
  createHoliday: (request) => holidayApi.create1({ recurringHolidayRequest: request }),
  updateHoliday: (holidayId, request) => holidayApi.update1({ holidayId, recurringHolidayRequest: request }),
  changeHolidayActivation: (holidayId, active, expectedConfigVersion, reason) => holidayApi.changeActivation1({
    holidayId,
    scheduleActivationRequest: { active, expectedConfigVersion, reason },
  }),
  getSynchronization: () => synchronizationApi.getStatus(),
  retrySynchronization: (pendingVersion) => synchronizationApi.retry({
    scheduleSynchronizationRetryRequest: { pendingVersion },
  }),
}
