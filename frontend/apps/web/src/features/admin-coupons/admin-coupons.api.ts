import {
  AdminCouponRegistrationControllerApi,
  AdminMemberQueryControllerApi,
  ResponseError,
  type AdminMemberPageResponse,
  type CouponRegistrationRequest,
  type CouponResponse,
  type ErrorResponse,
} from '@horse/api-client'
import { bearerApiConfiguration } from '../../api/web-api-configuration'

export type CouponType = 'general' | 'dressage' | 'jumping'

export interface AdminCouponsApi {
  getMembers(page: number, size: number, query?: string): Promise<AdminMemberPageResponse>
  registerCoupon(memberId: number, request: CouponRegistrationRequest): Promise<CouponResponse>
}

export type AdminCouponErrorKind = 'forbidden' | 'not-found' | 'validation' | 'unknown'

export function getAdminCouponErrorKind(error: unknown): AdminCouponErrorKind {
  if (!(error instanceof ResponseError)) return 'unknown'
  if (error.response.status === 403) return 'forbidden'
  if (error.response.status === 404) return 'not-found'
  if (error.response.status === 400) return 'validation'
  return 'unknown'
}

export async function getAdminCouponErrorMessage(error: unknown): Promise<string> {
  const kind = getAdminCouponErrorKind(error)
  const fallback = kind === 'forbidden'
    ? '관리자 권한이 없어 쿠폰을 등록할 수 없습니다.'
    : kind === 'not-found'
      ? '선택한 회원을 찾을 수 없습니다. 회원 목록을 다시 확인해 주세요.'
      : kind === 'validation'
        ? '쿠폰 정보가 올바르지 않습니다. 입력 내용을 확인해 주세요.'
        : '쿠폰을 등록하지 못했습니다. 처리 결과를 확인한 뒤 다시 시도해 주세요.'

  if (!(error instanceof ResponseError) || kind !== 'validation') return fallback
  try {
    const body = await error.response.clone().json() as Partial<ErrorResponse>
    return body.message ?? fallback
  } catch {
    return fallback
  }
}

const memberApi = new AdminMemberQueryControllerApi(bearerApiConfiguration)
const couponApi = new AdminCouponRegistrationControllerApi(bearerApiConfiguration)

export const adminCouponsApi: AdminCouponsApi = {
  getMembers: (page, size, query) => memberApi.getMembers({ page, size, query }),
  registerCoupon: (memberId, couponRegistrationRequest) => couponApi.register({
    memberId,
    couponRegistrationRequest,
  }),
}
