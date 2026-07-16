import {
  AdminReservationAuditQueryControllerApi,
  ResponseError,
  type AdminReservationAuditPageResponse,
} from '@horse/api-client'

export interface AdminAuditFilters {
  keyword?: string
  reservationId?: number
  occurredDateFrom?: string
  occurredDateTo?: string
  actorType?: string
  changeType?: string
  page: number
  size: number
}

export interface AdminAuditApi {
  getAuditLogs(filters: AdminAuditFilters): Promise<AdminReservationAuditPageResponse>
}

export type AdminAuditErrorKind = 'forbidden' | 'validation' | 'unknown'

export function getAdminAuditErrorKind(error: unknown): AdminAuditErrorKind {
  if (!(error instanceof ResponseError)) return 'unknown'
  if (error.response.status === 403) return 'forbidden'
  if (error.response.status === 400) return 'validation'
  return 'unknown'
}

const auditApi = new AdminReservationAuditQueryControllerApi()

function toApiDate(value?: string) {
  return value ? new Date(`${value}T00:00:00.000Z`) : undefined
}

export const adminAuditApi: AdminAuditApi = {
  getAuditLogs: (filters) => auditApi.getAuditLogs({
    keyword: filters.keyword,
    reservationId: filters.reservationId,
    occurredDateFrom: toApiDate(filters.occurredDateFrom),
    occurredDateTo: toApiDate(filters.occurredDateTo),
    actorType: filters.actorType,
    changeType: filters.changeType,
    page: filters.page,
    size: filters.size,
  }),
}
