import { ResponseError } from '@horse/api-client'
import { bearerApiConfiguration } from '../../api/web-api-configuration'

// M32-09 replaces these Phase B boundary types with the generated OpenAPI client.
export type FamilyGroupStatus = 'ACTIVE' | 'DISSOLVED'
export type FamilyGroupAuditAction = 'GROUP_CREATED' | 'MEMBER_ADDED' | 'MEMBER_REMOVED' | 'GROUP_DISSOLVED'

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

export interface FamilyGroupSummary {
  groupId: number
  name: string
  status: FamilyGroupStatus
  activeMemberCount: number
  createdAt: string
  dissolvedAt: string | null
}

export interface FamilyGroupMember {
  membershipId: number
  memberId: number
  name: string
  phone: string
  joinedAt: string
}

export interface FamilyMemberCandidate {
  memberId: number
  name: string
  phone: string
}

export interface FamilyGroupAuditLog {
  auditId: number
  action: FamilyGroupAuditAction
  memberId: number | null
  memberName: string | null
  fromState: Record<string, unknown> | null
  toState: Record<string, unknown> | null
  actorAuthSubject: string
  reason: string
  occurredAt: string
}

export interface FamilyGroupFilters {
  query: string
  status: FamilyGroupStatus | ''
}

export interface AdminFamilyGroupsApi {
  getGroups(filters: FamilyGroupFilters, page: number, size: number): Promise<PageResponse<FamilyGroupSummary>>
  getMembers(groupId: number, page: number, size: number): Promise<PageResponse<FamilyGroupMember>>
  getMemberCandidates(query: string, page: number, size: number): Promise<PageResponse<FamilyMemberCandidate>>
  getAuditLogs(groupId: number, page: number, size: number): Promise<PageResponse<FamilyGroupAuditLog>>
  createGroup(name: string, reason: string): Promise<void>
  addMember(groupId: number, memberId: number, reason: string): Promise<void>
  removeMember(groupId: number, memberId: number, reason: string): Promise<void>
  dissolveGroup(groupId: number, reason: string): Promise<void>
}

async function familyRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const accessToken = await bearerApiConfiguration.accessToken?.()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  if (init.body !== undefined) headers.set('Content-Type', 'application/json')
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)

  const fetchApi = bearerApiConfiguration.fetchApi ?? globalThis.fetch
  const response = await fetchApi(`${bearerApiConfiguration.basePath}${path}`, { ...init, headers })
  if (!response.ok) throw new ResponseError(response, 'Family group request failed')
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

function pageQuery(page: number, size: number) {
  return new URLSearchParams({ page: String(page), size: String(size) })
}

export const adminFamilyGroupsApi: AdminFamilyGroupsApi = {
  getGroups(filters, page, size) {
    const query = pageQuery(page, size)
    if (filters.query) query.set('query', filters.query)
    if (filters.status) query.set('status', filters.status)
    return familyRequest(`/api/admin/family-groups?${query}`)
  },
  getMembers(groupId, page, size) {
    return familyRequest(`/api/admin/family-groups/${groupId}/members?${pageQuery(page, size)}`)
  },
  getMemberCandidates(queryValue, page, size) {
    const query = pageQuery(page, size)
    if (queryValue) query.set('query', queryValue)
    return familyRequest(`/api/admin/family-groups/member-candidates?${query}`)
  },
  getAuditLogs(groupId, page, size) {
    return familyRequest(`/api/admin/family-groups/${groupId}/audit-logs?${pageQuery(page, size)}`)
  },
  async createGroup(name, reason) {
    await familyRequest('/api/admin/family-groups', {
      method: 'POST',
      body: JSON.stringify({ name, reason }),
    })
  },
  async addMember(groupId, memberId, reason) {
    await familyRequest(`/api/admin/family-groups/${groupId}/members`, {
      method: 'POST',
      body: JSON.stringify({ memberId, reason }),
    })
  },
  async removeMember(groupId, memberId, reason) {
    await familyRequest(`/api/admin/family-groups/${groupId}/members/${memberId}`, {
      method: 'DELETE',
      body: JSON.stringify({ reason }),
    })
  },
  async dissolveGroup(groupId, reason) {
    await familyRequest(`/api/admin/family-groups/${groupId}/dissolution`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    })
  },
}

export function getFamilyGroupErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof ResponseError)) return fallback
  if (error.response.status === 401) return '관리자 로그인이 필요합니다.'
  if (error.response.status === 403) return '가족 그룹을 관리할 권한이 없습니다.'
  if (error.response.status === 404) return '가족 그룹 또는 회원을 찾을 수 없습니다.'
  if (error.response.status === 409) return '현재 상태에서는 요청을 처리할 수 없습니다. 목록을 새로고침해 주세요.'
  return fallback
}
