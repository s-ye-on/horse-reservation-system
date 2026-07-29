import {
  MemberReservationQueryControllerApi,
  ResponseError,
  type GetMyReservationsDisplayGroupEnum,
  type GetMyReservationsStatusEnum,
  type MemberReservationPageResponse,
} from '@horse/api-client'

export interface MyReservationsQuery {
  displayGroup: GetMyReservationsDisplayGroupEnum
  status?: GetMyReservationsStatusEnum
  page: number
  size: number
}

export interface MyReservationsApi {
  getMyReservations(query: MyReservationsQuery): Promise<MemberReservationPageResponse>
}

export function isMyReservationsUnauthorized(error: unknown) {
  return error instanceof ResponseError && (error.response.status === 401 || error.response.status === 403)
}

const queryApi = new MemberReservationQueryControllerApi()

export const myReservationsApi: MyReservationsApi = {
  getMyReservations: (query) => queryApi.getMyReservations(query),
}
