import {
  AdminReservationQueryControllerApi,
  ResponseError,
  type AdminReservationResponse,
  type AdminReservationSummaryResponse,
} from '@horse/api-client'
import { apiConfiguration } from '../../config/api-configuration'

export type DashboardReservationStatus =
  | 'pending_admin_approval'
  | 'pending_payment'
  | 'payment_expired'

export interface AdminDashboardApi {
  getSummary(lessonDateFrom?: string, lessonDateTo?: string): Promise<AdminReservationSummaryResponse>
  getReservations(
    status: DashboardReservationStatus,
    lessonDateFrom: string,
    lessonDateTo: string,
  ): Promise<AdminReservationResponse[]>
}

export type AdminDashboardErrorKind = 'forbidden' | 'validation' | 'unknown'

export function getAdminDashboardErrorKind(error: unknown): AdminDashboardErrorKind {
  if (!(error instanceof ResponseError)) return 'unknown'
  if (error.response.status === 403) return 'forbidden'
  if (error.response.status === 400) return 'validation'
  return 'unknown'
}

const queryApi = new AdminReservationQueryControllerApi(apiConfiguration)

function toApiDate(value?: string) {
  return value ? new Date(`${value}T00:00:00.000Z`) : undefined
}

export const adminDashboardApi: AdminDashboardApi = {
  getSummary: (lessonDateFrom, lessonDateTo) => queryApi.getSummary({
    lessonDateFrom: toApiDate(lessonDateFrom),
    lessonDateTo: toApiDate(lessonDateTo),
  }),
  getReservations: async (status, lessonDateFrom, lessonDateTo) => {
    const page = await queryApi.getReservations({
      status,
      lessonDateFrom: toApiDate(lessonDateFrom),
      lessonDateTo: toApiDate(lessonDateTo),
      page: 0,
      size: 100,
    })
    return page.content ?? []
  },
}
