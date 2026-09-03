import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearWebAccessToken, setWebAccessToken } from '../../api/web-access-token-memory'
import { adminMembersApi } from './admin-members.api'

beforeEach(() => {
  setWebAccessToken('member-admin-token')
})

afterEach(() => {
  clearWebAccessToken()
  vi.unstubAllGlobals()
})

describe('adminMembersApi', () => {
  it('특수_승인_변경에_Bearer와_필수_사유를_전달한다', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(memberResponse()))
    vi.stubGlobal('fetch', fetchMock)

    await adminMembersApi.changeRidingPermissions(7, {
      dressageApproved: true,
      jumpingApproved: false,
      reason: '실력 확인 완료',
    })

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/admin/members/7/riding-permissions')
    expect(init.method).toBe('PATCH')
    expect(init.body).toBe(JSON.stringify({
      dressageApproved: true,
      jumpingApproved: false,
      reason: '실력 확인 완료',
    }))
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer member-admin-token')
  })

  it('회원_상세의_progression_필드를_손실하지_않고_반환한다', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(memberResponse()))
    vi.stubGlobal('fetch', fetchMock)

    const member = await adminMembersApi.getMember(7)

    expect(member.progressionValue).toBe(26)
    expect(member.progressionClass).toBe('LARGE_ARENA_TROT')
    expect(member.specialApprovalProgressionCredit).toBe(6)
    expect(fetchMock).toHaveBeenCalledWith('/api/admin/members/7', expect.any(Object))
  })

  it('preview와_감사_Page는_Bearer를_사용해_전용_조회_Endpoint를_호출한다', async () => {
    const fetchMock = vi.fn()
      .mockImplementationOnce(() => Promise.resolve(jsonResponse({
        stateToken: '"state-v1"',
        current: progressionProjection(20),
        expected: progressionProjection(70),
      })))
      .mockImplementationOnce(() => Promise.resolve(jsonResponse({
        content: [], page: 1, size: 5, totalElements: 6, totalPages: 2, hasNext: false,
      })))
    vi.stubGlobal('fetch', fetchMock)

    await adminMembersApi.previewProgression(7, {
      action: 'SET_BASELINE',
      baselineClass: 'CANTER_BEGINNER',
    })
    await adminMembersApi.getProgressionAuditLogs(7, 1, 5)

    const [previewUrl, previewInit] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(previewUrl).toBe('/api/admin/members/7/class-progression/preview')
    expect(previewInit.method).toBe('POST')
    expect(previewInit.body).toBe(JSON.stringify({
      action: 'SET_BASELINE',
      baselineClass: 'CANTER_BEGINNER',
    }))
    expect(new Headers(previewInit.headers).get('Authorization')).toBe('Bearer member-admin-token')
    expect(fetchMock.mock.calls[1]?.[0]).toBe(
      '/api/admin/members/7/class-progression/audit-logs?page=1&size=5',
    )
  })

  it('baseline_hold_인정분_횟수_Command에_필수_사유와_입력값을_그대로_전달한다', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(memberResponse()))
    vi.stubGlobal('fetch', fetchMock)

    const stateToken = '"state-v1"'
    await adminMembersApi.setProgressionBaseline(7, 'LARGE_ARENA_TROT', '경력 확인', stateToken)
    await adminMembersApi.removeProgressionBaseline(7, '잘못된 baseline 교정', stateToken)
    await adminMembersApi.setPromotionHold(7, 'ROUND_TROT', '안전 확인', stateToken)
    await adminMembersApi.removePromotionHold(7, '재평가 완료', stateToken)
    await adminMembersApi.correctSpecialApprovalCredit(7, 3, '승인 인정분 교정', stateToken)
    await adminMembersApi.adjustRideCount(7, -1, '중복 집계 정정', stateToken)

    expect(requestSummary(fetchMock, 0)).toEqual({
      path: '/api/admin/members/7/class-progression/baseline',
      method: 'PUT',
      body: { baselineClass: 'LARGE_ARENA_TROT', reason: '경력 확인' },
    })
    expect(requestSummary(fetchMock, 1)).toEqual({
      path: '/api/admin/members/7/class-progression/baseline',
      method: 'DELETE',
      body: { reason: '잘못된 baseline 교정' },
    })
    expect(requestSummary(fetchMock, 2)).toEqual({
      path: '/api/admin/members/7/class-progression/promotion-hold',
      method: 'PUT',
      body: { promotionHoldClass: 'ROUND_TROT', reason: '안전 확인' },
    })
    expect(requestSummary(fetchMock, 3)).toEqual({
      path: '/api/admin/members/7/class-progression/promotion-hold',
      method: 'DELETE',
      body: { reason: '재평가 완료' },
    })
    expect(requestSummary(fetchMock, 4)).toEqual({
      path: '/api/admin/members/7/class-progression/special-approval-credit',
      method: 'PUT',
      body: { specialApprovalProgressionCredit: 3, reason: '승인 인정분 교정' },
    })
    expect(requestSummary(fetchMock, 5)).toEqual({
      path: '/api/admin/members/7/class-progression/ride-count-adjustments',
      method: 'POST',
      body: { delta: -1, reason: '중복 집계 정정' },
    })
    for (const call of fetchMock.mock.calls) {
      const init = call[1] as RequestInit
      expect(new Headers(init.headers).get('If-Match')).toBe(stateToken)
    }
  })
})

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

function memberResponse() {
  return jsonResponse({
    id: 7,
    name: '김승마',
    phone: '010-0000-0000',
    generalRideCount: 20,
    dressageRideCount: 0,
    jumpingRideCount: 0,
    dressageApproved: true,
    jumpingApproved: false,
    canUseLargeArena: true,
    progressionValue: 26,
    progressionClass: 'LARGE_ARENA_TROT',
    effectiveClass: 'LARGE_ARENA_TROT',
    progressionManagementStartedAt: '2026-08-01T09:00:00',
    progressionBaselineClass: null,
    progressionBaselineThreshold: null,
    progressionBaselineActualRideCount: null,
    specialApprovalProgressionCredit: 6,
    promotionHoldClass: null,
  })
}

function progressionProjection(progressionValue: number) {
  return {
    actualCompletedRideCount: 20,
    progressionValue,
    progressionClass: progressionValue >= 70 ? 'CANTER_BEGINNER' : 'ROUND_TROT',
    effectiveClass: progressionValue >= 70 ? 'CANTER_BEGINNER' : 'ROUND_TROT',
    baselineClass: null,
    baselineThreshold: null,
    baselineActualRideCount: null,
    specialApprovalProgressionCredit: 0,
    promotionHoldClass: null,
  }
}

function requestSummary(fetchMock: ReturnType<typeof vi.fn>, callIndex: number) {
  const [path, init] = fetchMock.mock.calls[callIndex] as [string, RequestInit]
  return {
    path,
    method: init.method,
    body: init.body ? JSON.parse(init.body as string) : undefined,
  }
}
