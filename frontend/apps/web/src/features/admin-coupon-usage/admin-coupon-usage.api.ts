import { AdminCouponUsageExportControllerApi, ResponseError } from '@horse/api-client'
import { bearerApiConfiguration } from '../../api/web-api-configuration'
import { parseCsvFileName } from '../../api/csv-download'

export interface AdminCouponUsageCriteria {
  keyword?: string
  couponId?: number
  reservationId?: number
  occurredDateFrom?: string
  occurredDateTo?: string
}

export interface AdminCouponUsageApi {
  downloadUsageLogs(criteria: AdminCouponUsageCriteria): Promise<{ blob: Blob; fileName: string }>
}

export function couponUsageDownloadErrorMessage(error: unknown) {
  if (error instanceof ResponseError) {
    if (error.response.status === 401) return '로그인이 필요합니다. 다시 로그인해 주세요.'
    if (error.response.status === 403) return '관리자 권한이 없어 쿠폰 사용 이력을 다운로드할 수 없습니다.'
    if (error.response.status === 400) return '쿠폰 사용 이력 다운로드 조건을 다시 확인해 주세요.'
  }
  return '쿠폰 사용 이력을 다운로드하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

const exportApi = new AdminCouponUsageExportControllerApi(bearerApiConfiguration)
const toApiDate = (value?: string) => value ? new Date(`${value}T00:00:00.000Z`) : undefined

export const adminCouponUsageApi: AdminCouponUsageApi = {
  downloadUsageLogs: async (criteria) => {
    const response = await exportApi._exportRaw({
      keyword: criteria.keyword,
      couponId: criteria.couponId,
      reservationId: criteria.reservationId,
      occurredDateFrom: toApiDate(criteria.occurredDateFrom),
      occurredDateTo: toApiDate(criteria.occurredDateTo),
    })
    const contentType = response.raw.headers.get('content-type')
    if (!contentType || !/^text\/csv(?:;|$)/i.test(contentType.trim())) {
      throw new Error('CSV 응답 형식이 올바르지 않습니다.')
    }
    return {
      blob: await response.raw.blob(),
      fileName: parseCsvFileName(response.raw.headers.get('content-disposition'), 'coupon-usage.csv'),
    }
  },
}
