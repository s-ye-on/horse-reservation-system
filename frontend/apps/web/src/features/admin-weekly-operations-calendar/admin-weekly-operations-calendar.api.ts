import {
  AdminReservationQueryControllerApi,
  ResponseError,
  type AdminWeeklyOperationsCalendarResponse,
} from '@horse/api-client'
import { bearerApiConfiguration } from '../../api/web-api-configuration'

export interface AdminWeeklyOperationsCalendarApi {
  getCalendar(referenceDate: string): Promise<AdminWeeklyOperationsCalendarResponse>
}

export type AdminWeeklyOperationsCalendarErrorKind =
  | 'unauthorized'
  | 'forbidden'
  | 'validation'
  | 'unknown'

export function getAdminWeeklyOperationsCalendarErrorKind(
  error: unknown,
): AdminWeeklyOperationsCalendarErrorKind {
  if (!(error instanceof ResponseError)) return 'unknown'
  if (error.response.status === 401) return 'unauthorized'
  if (error.response.status === 403) return 'forbidden'
  if (error.response.status === 400) return 'validation'
  return 'unknown'
}

const queryApi = new AdminReservationQueryControllerApi(bearerApiConfiguration)

export const adminWeeklyOperationsCalendarApi: AdminWeeklyOperationsCalendarApi = {
  getCalendar: (referenceDate) => queryApi.getWeeklyOperationsCalendar({ referenceDate }),
}
