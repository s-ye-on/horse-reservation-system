import {
  AdminFamilyGroupCommandControllerApi,
  AdminFamilyGroupQueryControllerApi,
  ResponseError,
  type FamilyGroupAuditResponse,
  type FamilyGroupMemberResponse,
  type FamilyGroupSummaryResponse,
  type FamilyMemberCandidateResponse,
} from '@horse/api-client'
import { bearerApiConfiguration } from '../../api/web-api-configuration'

export type FamilyGroupStatus = FamilyGroupSummaryResponse['status']
export type FamilyGroupAuditAction = FamilyGroupAuditResponse['action']

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

export type FamilyGroupSummary = FamilyGroupSummaryResponse
export type FamilyGroupMember = FamilyGroupMemberResponse
export type FamilyMemberCandidate = FamilyMemberCandidateResponse
export type FamilyGroupAuditLog = FamilyGroupAuditResponse

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

const queryApi = new AdminFamilyGroupQueryControllerApi(bearerApiConfiguration)
const commandApi = new AdminFamilyGroupCommandControllerApi(bearerApiConfiguration)

export const adminFamilyGroupsApi: AdminFamilyGroupsApi = {
  getGroups: (filters, page, size) => queryApi.getFamilyGroups({
    query: filters.query || undefined,
    status: filters.status || undefined,
    page,
    size,
  }),
  getMembers: (groupId, page, size) => queryApi.getFamilyGroupMembers({ groupId, page, size }),
  getMemberCandidates: (query, page, size) => queryApi.getFamilyMemberCandidates({
    query: query || undefined,
    page,
    size,
  }),
  getAuditLogs: (groupId, page, size) => queryApi.getFamilyGroupAuditLogs({ groupId, page, size }),
  async createGroup(name, reason) {
    await commandApi.createFamilyGroup({ familyGroupCreateRequest: { name, reason } })
  },
  async addMember(groupId, memberId, reason) {
    await commandApi.addFamilyGroupMember({
      groupId,
      familyMemberAddRequest: { memberId, reason },
    })
  },
  async removeMember(groupId, memberId, reason) {
    await commandApi.removeFamilyGroupMember({
      groupId,
      memberId,
      familyReasonRequest: { reason },
    })
  },
  async dissolveGroup(groupId, reason) {
    await commandApi.dissolveFamilyGroup({ groupId, familyReasonRequest: { reason } })
  },
}

export function getFamilyGroupErrorMessage(error: unknown, fallback: string): string {
  if (!(error instanceof ResponseError)) return fallback
  if (error.response.status === 401) return '관리자 로그인이 필요합니다.'
  if (error.response.status === 403) return '가족 그룹을 관리할 권한이 없습니다.'
  if (error.response.status === 400) return '입력 내용을 확인해 주세요. 이름과 사유의 필수 입력 및 길이 제한을 확인해 주세요.'
  if (error.response.status === 404) return '그룹 또는 회원 관계가 변경되었습니다. 최신 목록을 확인한 뒤 다시 진행해 주세요.'
  if (error.response.status === 409) return '회원 소속 또는 그룹 상태가 변경되었습니다. 최신 목록을 확인한 뒤 다시 판단해 주세요.'
  return fallback
}
