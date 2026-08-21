import {
  AdminMemberClassProgressionControllerApi,
  AdminMemberClassProgressionQueryControllerApi,
  AdminMemberQueryControllerApi,
  AdminMemberRidingPermissionControllerApi,
  ResponseError,
  type AdminMemberPageResponse,
  type AdminMemberResponse,
  type MemberClassProgressionAuditPageResponse,
  type MemberClassProgressionAuditResponse,
  type MemberClassProgressionPreviewRequest,
  type MemberClassProgressionPreviewResponse,
  type MemberRidingPermissionUpdateRequest,
} from '@horse/api-client'
import { bearerApiConfiguration } from '../../api/web-api-configuration'

export type GeneralRidingGrade = AdminMemberResponse['progressionClass']
export type AdminMemberProgressionResponse = AdminMemberResponse
export type MemberClassProgressionPreviewAction = MemberClassProgressionPreviewRequest['action']
export type MemberClassProgressionAuditAction = MemberClassProgressionAuditResponse['action']

export type {
  MemberClassProgressionAuditPageResponse,
  MemberClassProgressionAuditResponse,
  MemberClassProgressionPreviewRequest,
  MemberClassProgressionPreviewResponse,
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

export type MemberRidingPermissionChangeRequest = MemberRidingPermissionUpdateRequest

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
const ridingPermissionApi = new AdminMemberRidingPermissionControllerApi(bearerApiConfiguration)
const progressionCommandApi = new AdminMemberClassProgressionControllerApi(bearerApiConfiguration)
const progressionQueryApi = new AdminMemberClassProgressionQueryControllerApi(bearerApiConfiguration)

export const adminMembersApi: AdminMembersApi = {
  getMembers: (page, size) => queryApi.getMembers({ page, size }),
  getMember: (memberId) => queryApi.getMember({ memberId }),
  changeRidingPermissions: (memberId, permissions) => ridingPermissionApi.changeRidingPermissions({
    memberId,
    memberRidingPermissionUpdateRequest: permissions,
  }),
  previewProgression: (memberId, request) => progressionQueryApi.previewMemberClassProgression({
    memberId,
    memberClassProgressionPreviewRequest: request,
  }),
  setProgressionBaseline: (memberId, baselineClass, reason, stateToken) => (
    progressionCommandApi.setMemberProgressionBaseline({
      memberId,
      memberProgressionBaselineRequest: { baselineClass, reason },
      ifMatch: stateToken,
    })
  ),
  removeProgressionBaseline: (memberId, reason, stateToken) => (
    progressionCommandApi.removeMemberProgressionBaseline({
      memberId,
      memberClassChangeReasonRequest: { reason },
      ifMatch: stateToken,
    })
  ),
  setPromotionHold: (memberId, promotionHoldClass, reason, stateToken) => (
    progressionCommandApi.setMemberPromotionHold({
      memberId,
      memberPromotionHoldRequest: { promotionHoldClass, reason },
      ifMatch: stateToken,
    })
  ),
  removePromotionHold: (memberId, reason, stateToken) => (
    progressionCommandApi.removeMemberPromotionHold({
      memberId,
      memberClassChangeReasonRequest: { reason },
      ifMatch: stateToken,
    })
  ),
  correctSpecialApprovalCredit: (
    memberId,
    specialApprovalProgressionCredit,
    reason,
    stateToken,
  ) => progressionCommandApi.correctMemberSpecialApprovalCredit({
    memberId,
    memberProgressionCreditCorrectionRequest: { specialApprovalProgressionCredit, reason },
    ifMatch: stateToken,
  }),
  adjustRideCount: (memberId, delta, reason, stateToken) => (
    progressionCommandApi.adjustMemberActualCompletedRideCount({
      memberId,
      memberRideCountAdjustmentRequest: { delta, reason },
      ifMatch: stateToken,
    })
  ),
  getProgressionAuditLogs: (memberId, page, size) => (
    progressionQueryApi.getMemberClassProgressionAuditLogs({ memberId, page, size })
  ),
}
