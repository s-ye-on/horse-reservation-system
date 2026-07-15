import {
  AdminMemberQueryControllerApi,
  AdminMemberRidingPermissionControllerApi,
  ResponseError,
  type AdminMemberResponse,
  type MemberRidingPermissionUpdateRequest,
} from '@horse/api-client'

export interface AdminMembersApi {
  getMembers(): Promise<AdminMemberResponse[]>
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

const queryApi = new AdminMemberQueryControllerApi()
const permissionApi = new AdminMemberRidingPermissionControllerApi()

export const adminMembersApi: AdminMembersApi = {
  getMembers: () => queryApi.getMembers(),
  getMember: (memberId) => queryApi.getMember({ memberId }),
  changeRidingPermissions: (memberId, permissions) =>
    permissionApi.changeRidingPermissions({
      memberId,
      memberRidingPermissionUpdateRequest: permissions,
    }),
}
