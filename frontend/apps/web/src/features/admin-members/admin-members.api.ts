import {
  AdminMemberQueryControllerApi,
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
    permissions: MemberRidingPermissionChangeRequest,
  ): Promise<AdminMemberResponse>
}

export type MemberRidingPermissionChangeRequest = MemberRidingPermissionUpdateRequest & {
  reason: string
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

// M32-09 replaces this Phase B request boundary with the regenerated OpenAPI client.
async function changeRidingPermissions(
  memberId: number,
  permissions: MemberRidingPermissionChangeRequest,
): Promise<AdminMemberResponse> {
  const accessToken = await bearerApiConfiguration.accessToken?.()
  const headers = new Headers({ Accept: 'application/json', 'Content-Type': 'application/json' })
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)

  const fetchApi = bearerApiConfiguration.fetchApi ?? globalThis.fetch
  const response = await fetchApi(
    `${bearerApiConfiguration.basePath}/api/admin/members/${memberId}/riding-permissions`,
    { method: 'PATCH', headers, body: JSON.stringify(permissions) },
  )
  if (!response.ok) throw new ResponseError(response, 'Member riding permission request failed')
  return response.json() as Promise<AdminMemberResponse>
}

export const adminMembersApi: AdminMembersApi = {
  getMembers: (page, size) => queryApi.getMembers({ page, size }),
  getMember: (memberId) => queryApi.getMember({ memberId }),
  changeRidingPermissions,
}
