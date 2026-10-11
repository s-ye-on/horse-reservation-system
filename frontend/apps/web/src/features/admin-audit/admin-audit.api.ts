import {
  AdminReservationAuditExportControllerApi,
  AdminReservationAuditQueryControllerApi,
  ResponseError,
  type AdminReservationAuditPageResponse,
} from '@horse/api-client'
import { bearerApiConfiguration } from '../../api/web-api-configuration'
import { parseCsvFileName } from '../../api/csv-download'

export interface AdminAuditCriteria {
  keyword?: string
  reservationId?: number
  occurredDateFrom?: string
  occurredDateTo?: string
  actorType?: string
  changeType?: string
}

export interface AdminAuditFilters extends AdminAuditCriteria {
  page: number
  size: number
}

export interface AdminAuditApi {
  getAuditLogs(filters: AdminAuditFilters): Promise<AdminReservationAuditPageResponse>
  downloadAuditLogs(criteria: AdminAuditCriteria): Promise<AdminAuditDownload>
}

export interface AdminAuditDownload {
  blob: Blob
  fileName: string
}

export type AdminAuditErrorKind = 'unauthorized' | 'forbidden' | 'validation' | 'unknown'

export function getAdminAuditErrorKind(error: unknown): AdminAuditErrorKind {
  if (!(error instanceof ResponseError)) return 'unknown'
  if (error.response.status === 401) return 'unauthorized'
  if (error.response.status === 403) return 'forbidden'
  if (error.response.status === 400) return 'validation'
  return 'unknown'
}

const auditApi = new AdminReservationAuditQueryControllerApi(bearerApiConfiguration)
const auditExportApi = new AdminReservationAuditExportControllerApi(bearerApiConfiguration)
const FALLBACK_CSV_FILE_NAME = 'reservation-audit.csv'

function toApiDate(value?: string) {
  return value ? new Date(`${value}T00:00:00.000Z`) : undefined
}

function toAuditCriteria(filters: AdminAuditCriteria) {
  return {
    keyword: filters.keyword,
    reservationId: filters.reservationId,
    occurredDateFrom: toApiDate(filters.occurredDateFrom),
    occurredDateTo: toApiDate(filters.occurredDateTo),
    actorType: filters.actorType,
    changeType: filters.changeType,
  }
}

export function parseAuditCsvFileName(contentDisposition: string | null) {
  return parseCsvFileName(contentDisposition, FALLBACK_CSV_FILE_NAME)
}

export const adminAuditApi: AdminAuditApi = {
  getAuditLogs: (filters) => auditApi.getAuditLogs({
    ...toAuditCriteria(filters),
    page: filters.page,
    size: filters.size,
  }),
  downloadAuditLogs: async (filters) => {
    const response = await auditExportApi.export1Raw(toAuditCriteria(filters))
    const contentType = response.raw.headers.get('content-type')
    if (!contentType || !/^text\/csv(?:;|$)/i.test(contentType.trim())) {
      throw new Error('CSV 응답 형식이 올바르지 않습니다.')
    }

    return {
      blob: await response.raw.blob(),
      fileName: parseAuditCsvFileName(response.raw.headers.get('content-disposition')),
    }
  },
}
