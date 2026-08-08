import {
  AdminCouponRegistrationControllerApi,
  AdminMemberQueryControllerApi,
  ResponseError,
  type AdminMemberResponse,
  type CouponResponse,
} from '@horse/api-client'
import { bearerApiConfiguration } from '../../api/web-api-configuration'

export type CouponType = 'general' | 'dressage' | 'jumping'

export interface AdminCouponsApi {
  getMembers(): Promise<AdminMemberResponse[]>
  registerCoupon(memberId: number, type: CouponType): Promise<CouponResponse>
}

export type AdminCouponErrorKind = 'forbidden' | 'not-found' | 'validation' | 'unknown'

export function getAdminCouponErrorKind(error: unknown): AdminCouponErrorKind {
  if (!(error instanceof ResponseError)) return 'unknown'
  if (error.response.status === 403) return 'forbidden'
  if (error.response.status === 404) return 'not-found'
  if (error.response.status === 400) return 'validation'
  return 'unknown'
}

const memberApi = new AdminMemberQueryControllerApi(bearerApiConfiguration)
const couponApi = new AdminCouponRegistrationControllerApi(bearerApiConfiguration)

export const adminCouponsApi: AdminCouponsApi = {
  getMembers: async () => (await memberApi.getMembers()).content ?? [],
  registerCoupon: (memberId, type) => couponApi.register({
    memberId,
    couponRegistrationRequest: { type, totalCount: 10 },
  }),
}
