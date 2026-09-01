import {
  AdminReservationQueryControllerApi,
  ResponseError,
  type AdminMonthlyRideStatisticsResponse,
  type GetMonthlyRideStatisticsRideTypeEnum,
} from '@horse/api-client'
import { bearerApiConfiguration } from '../../api/web-api-configuration'

export type MonthlyRideType = GetMonthlyRideStatisticsRideTypeEnum

export interface AdminMonthlyRideStatisticsApi {
  getStatistics(month: string, rideType: MonthlyRideType): Promise<AdminMonthlyRideStatisticsResponse>
}

export type AdminMonthlyRideStatisticsErrorKind = 'unauthorized' | 'forbidden' | 'validation' | 'unknown'

export function getAdminMonthlyRideStatisticsErrorKind(
  error: unknown,
): AdminMonthlyRideStatisticsErrorKind {
  if (!(error instanceof ResponseError)) return 'unknown'
  if (error.response.status === 401) return 'unauthorized'
  if (error.response.status === 403) return 'forbidden'
  if (error.response.status === 400) return 'validation'
  return 'unknown'
}

const queryApi = new AdminReservationQueryControllerApi(bearerApiConfiguration)

export const adminMonthlyRideStatisticsApi: AdminMonthlyRideStatisticsApi = {
  getStatistics: (month, rideType) => queryApi.getMonthlyRideStatistics({ month, rideType }),
}
