import {
  AdminReservationAuditExportControllerApi,
  AdminReservationAuditQueryControllerApi,
  ResponseError,
  type AdminReservationAuditPageResponse,
} from '@horse/api-client'
import { bearerApiConfiguration } from '../../api/web-api-configuration'

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

function decodeFileName(value: string) {
  try {
    return decodeURIComponent(value)
  } catch {
    return ''
  }
}

function stripControlCharacters(value: string) {
  return Array.from(value)
    .filter((character) => {
      const codePoint = character.codePointAt(0) ?? 0
      return codePoint >= 32 && codePoint !== 127
    })
    .join('')
}

function sanitizeCsvFileName(value: string) {
  const candidate = stripControlCharacters(value)
    .replace(/\\(.)/g, '$1')
    .split(/[\\/]/)
    .at(-1) ?? ''
  const baseName = candidate
    .replace(/[<>:"|?*]/g, '_')
    .trim()
    .replace(/[. ]+$/g, '')

  if (!baseName || baseName === '.' || baseName === '..') return FALLBACK_CSV_FILE_NAME

  const safeBaseName = /^(?:con|prn|aux|nul|com[1-9]|lpt[1-9])(?:\.|$)/i.test(baseName)
    ? `_${baseName}`
    : baseName
  const csvFileName = safeBaseName.toLowerCase().endsWith('.csv') ? safeBaseName : `${safeBaseName}.csv`
  if (csvFileName.length <= 120) return csvFileName
  return `${csvFileName.slice(0, 116)}.csv`
}

export function parseAuditCsvFileName(contentDisposition: string | null) {
  if (!contentDisposition) return FALLBACK_CSV_FILE_NAME

  const encoded = contentDisposition.match(/filename\*\s*=\s*UTF-8'[^']*'("?)([^";]+)\1/i)?.[2]
  if (encoded) {
    const decoded = decodeFileName(encoded)
    if (decoded) return sanitizeCsvFileName(decoded)
  }

  const quoted = contentDisposition.match(/filename\s*=\s*"((?:\\.|[^"])*)"/i)?.[1]
  if (quoted) return sanitizeCsvFileName(quoted)

  const plain = contentDisposition.match(/filename\s*=\s*([^;]+)/i)?.[1]
  return plain ? sanitizeCsvFileName(plain) : FALLBACK_CSV_FILE_NAME
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
