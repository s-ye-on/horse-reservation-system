import {
  AdminMemberQueryControllerApi,
  ResponseError,
  type AdminMemberPageResponse,
  type AdminMemberResponse,
  type MemberRidingPermissionUpdateRequest,
} from '@horse/api-client'
import { bearerApiConfiguration } from '../../api/web-api-configuration'

export type GeneralRidingGrade =
  | 'FIRST_RIDE'
  | 'ROUND_BEGINNER'
  | 'ROUND_TROT'
  | 'LARGE_ARENA_BEGINNER'
  | 'LARGE_ARENA_TROT'
  | 'CANTER_BEGINNER'
  | 'CANTER'

export interface AdminMemberProgressionResponse extends AdminMemberResponse {
  progressionValue: number
  progressionClass: GeneralRidingGrade
  effectiveClass: GeneralRidingGrade
  progressionManagementStartedAt: string | null
  progressionBaselineClass: GeneralRidingGrade | null
  progressionBaselineThreshold: number | null
  progressionBaselineActualRideCount: number | null
  specialApprovalProgressionCredit: number
  promotionHoldClass: GeneralRidingGrade | null
}

export interface MemberClassProgressionProjection {
  actualCompletedRideCount: number
  progressionValue: number
  progressionClass: GeneralRidingGrade
  effectiveClass: GeneralRidingGrade
  baselineClass: GeneralRidingGrade | null
  baselineThreshold: number | null
  baselineActualRideCount: number | null
  specialApprovalProgressionCredit: number
  promotionHoldClass: GeneralRidingGrade | null
}

export type MemberClassProgressionPreviewAction =
  | 'SET_BASELINE'
  | 'REMOVE_BASELINE'
  | 'SET_PROMOTION_HOLD'
  | 'REMOVE_PROMOTION_HOLD'
  | 'CORRECT_SPECIAL_APPROVAL_CREDIT'
  | 'ADJUST_RIDE_COUNT'

export interface MemberClassProgressionPreviewRequest {
  action: MemberClassProgressionPreviewAction
  baselineClass?: GeneralRidingGrade
  promotionHoldClass?: GeneralRidingGrade
  specialApprovalProgressionCredit?: number
  rideCountDelta?: number
}

export interface MemberClassProgressionPreviewResponse {
  stateToken: string
  current: MemberClassProgressionProjection
  expected: MemberClassProgressionProjection
}

export type MemberClassProgressionAuditAction =
  | 'PROGRESSION_INITIALIZED'
  | 'BASELINE_SET'
  | 'BASELINE_CHANGED'
  | 'BASELINE_REMOVED'
  | 'SPECIAL_APPROVAL_CHANGED'
  | 'SPECIAL_APPROVAL_CREDIT_CORRECTED'
  | 'RIDE_COUNT_ADJUSTED'
  | 'PROMOTION_HOLD_SET'
  | 'PROMOTION_HOLD_CHANGED'
  | 'PROMOTION_HOLD_REMOVED'

export interface MemberClassProgressionAuditResponse {
  auditId: number
  action: MemberClassProgressionAuditAction
  fromState: Record<string, unknown> | null
  toState: Record<string, unknown>
  actorAuthSubject: string
  reason: string
  occurredAt: string
}

export interface MemberClassProgressionAuditPageResponse {
  content: MemberClassProgressionAuditResponse[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
}

export interface AdminMembersApi {
  getMembers(page: number, size: number): Promise<AdminMemberPageResponse>
  getMember(memberId: number): Promise<AdminMemberProgressionResponse>
  changeRidingPermissions(
    memberId: number,
    permissions: MemberRidingPermissionChangeRequest,
  ): Promise<AdminMemberProgressionResponse>
  previewProgression(
    memberId: number,
    request: MemberClassProgressionPreviewRequest,
  ): Promise<MemberClassProgressionPreviewResponse>
  setProgressionBaseline(
    memberId: number,
    baselineClass: GeneralRidingGrade,
    reason: string,
    stateToken: string,
  ): Promise<AdminMemberProgressionResponse>
  removeProgressionBaseline(
    memberId: number,
    reason: string,
    stateToken: string,
  ): Promise<AdminMemberProgressionResponse>
  setPromotionHold(
    memberId: number,
    promotionHoldClass: GeneralRidingGrade,
    reason: string,
    stateToken: string,
  ): Promise<AdminMemberProgressionResponse>
  removePromotionHold(memberId: number, reason: string, stateToken: string): Promise<AdminMemberProgressionResponse>
  correctSpecialApprovalCredit(
    memberId: number,
    specialApprovalProgressionCredit: number,
    reason: string,
    stateToken: string,
  ): Promise<AdminMemberProgressionResponse>
  adjustRideCount(
    memberId: number,
    delta: number,
    reason: string,
    stateToken: string,
  ): Promise<AdminMemberProgressionResponse>
  getProgressionAuditLogs(
    memberId: number,
    page: number,
    size: number,
  ): Promise<MemberClassProgressionAuditPageResponse>
}

export type MemberRidingPermissionChangeRequest = MemberRidingPermissionUpdateRequest & {
  reason: string
}

export type AdminMembersErrorKind = 'forbidden' | 'not-found' | 'conflict' | 'validation' | 'unknown'

export function getAdminMembersErrorKind(error: unknown): AdminMembersErrorKind {
  if (!(error instanceof ResponseError)) return 'unknown'
  if (error.response.status === 403) return 'forbidden'
  if (error.response.status === 404) return 'not-found'
  if (error.response.status === 409) return 'conflict'
  if (error.response.status === 400) return 'validation'
  return 'unknown'
}

export async function getAdminMembersErrorCode(error: unknown): Promise<string | undefined> {
  if (!(error instanceof ResponseError)) return undefined
  try {
    const body = await error.response.clone().json() as { code?: unknown }
    return typeof body.code === 'string' ? body.code : undefined
  } catch {
    return undefined
  }
}

const queryApi = new AdminMemberQueryControllerApi(bearerApiConfiguration)

// M32-09 replaces this Phase B boundary with the regenerated OpenAPI client.
async function adminMemberRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const accessToken = await bearerApiConfiguration.accessToken?.()
  const headers = new Headers({ Accept: 'application/json', ...init.headers })
  if (init.body !== undefined) headers.set('Content-Type', 'application/json')
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  const fetchApi = bearerApiConfiguration.fetchApi ?? globalThis.fetch
  const response = await fetchApi(`${bearerApiConfiguration.basePath}${path}`, { ...init, headers })
  if (!response.ok) throw new ResponseError(response, 'Admin member progression request failed')
  return response.json() as Promise<T>
}

function progressionPath(memberId: number, suffix = '') {
  return `/api/admin/members/${memberId}/class-progression${suffix}`
}

export const adminMembersApi: AdminMembersApi = {
  getMembers: (page, size) => queryApi.getMembers({ page, size }),
  getMember: (memberId) => adminMemberRequest(`/api/admin/members/${memberId}`),
  changeRidingPermissions: (memberId, permissions) => adminMemberRequest(
    `/api/admin/members/${memberId}/riding-permissions`,
    { method: 'PATCH', body: JSON.stringify(permissions) },
  ),
  previewProgression: (memberId, request) => adminMemberRequest(
    progressionPath(memberId, '/preview'),
    { method: 'POST', body: JSON.stringify(request) },
  ),
  setProgressionBaseline: (memberId, baselineClass, reason, stateToken) => adminMemberRequest(
    progressionPath(memberId, '/baseline'),
    { method: 'PUT', headers: { 'If-Match': stateToken }, body: JSON.stringify({ baselineClass, reason }) },
  ),
  removeProgressionBaseline: (memberId, reason, stateToken) => adminMemberRequest(
    progressionPath(memberId, '/baseline'),
    { method: 'DELETE', headers: { 'If-Match': stateToken }, body: JSON.stringify({ reason }) },
  ),
  setPromotionHold: (memberId, promotionHoldClass, reason, stateToken) => adminMemberRequest(
    progressionPath(memberId, '/promotion-hold'),
    { method: 'PUT', headers: { 'If-Match': stateToken }, body: JSON.stringify({ promotionHoldClass, reason }) },
  ),
  removePromotionHold: (memberId, reason, stateToken) => adminMemberRequest(
    progressionPath(memberId, '/promotion-hold'),
    { method: 'DELETE', headers: { 'If-Match': stateToken }, body: JSON.stringify({ reason }) },
  ),
  correctSpecialApprovalCredit: (
    memberId,
    specialApprovalProgressionCredit,
    reason,
    stateToken,
  ) => adminMemberRequest(
    progressionPath(memberId, '/special-approval-credit'),
    {
      method: 'PUT',
      headers: { 'If-Match': stateToken },
      body: JSON.stringify({ specialApprovalProgressionCredit, reason }),
    },
  ),
  adjustRideCount: (memberId, delta, reason, stateToken) => adminMemberRequest(
    progressionPath(memberId, '/ride-count-adjustments'),
    { method: 'POST', headers: { 'If-Match': stateToken }, body: JSON.stringify({ delta, reason }) },
  ),
  getProgressionAuditLogs: (memberId, page, size) => adminMemberRequest(
    `${progressionPath(memberId, '/audit-logs')}?page=${page}&size=${size}`,
  ),
}
