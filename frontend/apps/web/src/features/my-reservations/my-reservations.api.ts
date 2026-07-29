import {
  MemberReservationQueryControllerApi,
  ResponseError,
  type MemberReservationResponse,
} from '@horse/api-client'

export interface MyReservationsApi {
  getMyReservations(): Promise<MemberReservationResponse[]>
}

export function isMyReservationsUnauthorized(error: unknown) {
  return error instanceof ResponseError && (error.response.status === 401 || error.response.status === 403)
}

const queryApi = new MemberReservationQueryControllerApi()

export const myReservationsApi: MyReservationsApi = {
  getMyReservations: async () => (await queryApi.getMyReservations()).content ?? [],
}
