import {
  MemberCouponQueryControllerApi,
  ResponseError,
  type MemberCouponResponse,
  type MemberCouponUsageResponse,
} from '@horse/api-client'

export interface MemberCouponOverview {
  coupons: MemberCouponResponse[]
  usageLogs: MemberCouponUsageResponse[]
}

export interface MyCouponsApi {
  getOverview(): Promise<MemberCouponOverview>
}

export function isMyCouponsUnauthorized(error: unknown) {
  return error instanceof ResponseError && (error.response.status === 401 || error.response.status === 403)
}

const queryApi = new MemberCouponQueryControllerApi()

export const myCouponsApi: MyCouponsApi = {
  getOverview: async () => {
    const [couponPage, usageLogPage] = await Promise.all([queryApi.getCoupons(), queryApi.getUsageLogs()])
    return {
      coupons: couponPage.content ?? [],
      usageLogs: usageLogPage.content ?? [],
    }
  },
}
