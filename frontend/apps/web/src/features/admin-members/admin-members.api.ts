import {
  AdminMemberQueryControllerApi,
  AdminMemberRidingPermissionControllerApi,
  ResponseError,
  type AdminMemberPageResponse,
  type AdminMemberResponse,
  type MemberRidingPermissionUpdateRequest,
} from '@horse/api-client'
import { bearerApiConfiguration } from '../../api/web-api-configuration'

export interface AdminMembersApi {
  getMembers(page: number, size: number): Promise<AdminMemberPageResponse>
  getMember(memberId: number): Promise<AdminMemberResponse>
  changeRidingPermissions(
    memberId: number,
    permissions: MemberRidingPermissionUpdateRequest,
  ): Promise<AdminMemberResponse>
}

export type AdminMembersErrorKind = 'forbidden' | 'not-found' | 'unknown'

export function getAdminMembersErrorKind(error: unknown): AdminMembersErrorKind {
  if (!(error instanceof ResponseError)) {
    return 'unknown'
  }

  if (error.response.status === 403) {
    return 'forbidden'
  }

  if (error.response.status === 404) {
    return 'not-found'
  }

  return 'unknown'
}

const queryApi = new AdminMemberQueryControllerApi(bearerApiConfiguration)
const permissionApi = new AdminMemberRidingPermissionControllerApi(bearerApiConfiguration)

export const adminMembersApi: AdminMembersApi = {
  getMembers: (page, size) => queryApi.getMembers({ page, size }),
  getMember: (memberId) => queryApi.getMember({ memberId }),
  changeRidingPermissions: (memberId, permissions) =>
    permissionApi.changeRidingPermissions({
      memberId,
      memberRidingPermissionUpdateRequest: permissions,
    }),
}
