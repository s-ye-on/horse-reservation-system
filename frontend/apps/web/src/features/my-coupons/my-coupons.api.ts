import {
  MemberCouponQueryControllerApi,
  ResponseError,
  type MemberCouponPageResponse,
  type MemberCouponUsagePageResponse,
} from '@horse/api-client'
import { bearerApiConfiguration } from '../../api/web-api-configuration'

export interface MyCouponsApi {
  getCoupons(page: number, size: number): Promise<MemberCouponPageResponse>
  getUsageLogs(page: number, size: number): Promise<MemberCouponUsagePageResponse>
}

export function isMyCouponsUnauthorized(error: unknown) {
  return error instanceof ResponseError && (error.response.status === 401 || error.response.status === 403)
}

const queryApi = new MemberCouponQueryControllerApi(bearerApiConfiguration)

export const myCouponsApi: MyCouponsApi = {
  getCoupons: (page, size) => queryApi.getCoupons({ page, size }),
  getUsageLogs: (page, size) => queryApi.getUsageLogs({ page, size }),
}
