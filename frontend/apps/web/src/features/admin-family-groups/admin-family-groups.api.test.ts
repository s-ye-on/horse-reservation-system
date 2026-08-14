import { ResponseError } from '@horse/api-client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearWebAccessToken, setWebAccessToken } from '../../api/web-access-token-memory'
import { adminFamilyGroupsApi, getFamilyGroupErrorMessage } from './admin-family-groups.api'

const EMPTY_PAGE = {
  content: [], page: 0, size: 10, totalElements: 0, totalPages: 0, hasNext: false,
}

beforeEach(() => {
  setWebAccessToken('family-access-token')
})

afterEach(() => {
  clearWebAccessToken()
  vi.unstubAllGlobals()
})

describe('adminFamilyGroupsApi', () => {
  it('그룹_검색에_Bearer와_Page_조건을_전달한다', async () => {
    const fetchMock = jsonFetchMock(EMPTY_PAGE)
    vi.stubGlobal('fetch', fetchMock)

    await adminFamilyGroupsApi.getGroups({ query: '김 가족', status: 'ACTIVE' }, 2, 10)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(url).toBe('/api/admin/family-groups?page=2&size=10&query=%EA%B9%80+%EA%B0%80%EC%A1%B1&status=ACTIVE')
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer family-access-token')
  })

  it('구성원_추가와_제거에_회원_ID와_필수_사유를_전달한다', async () => {
    const fetchMock = jsonFetchMock({})
    vi.stubGlobal('fetch', fetchMock)

    await adminFamilyGroupsApi.addMember(11, 31, '가족 확인')
    await adminFamilyGroupsApi.removeMember(11, 31, '가족 관계 종료')

    expect(fetchMock).toHaveBeenNthCalledWith(1, '/api/admin/family-groups/11/members', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ memberId: 31, reason: '가족 확인' }),
    }))
    expect(fetchMock).toHaveBeenNthCalledWith(2, '/api/admin/family-groups/11/members/31', expect.objectContaining({
      method: 'DELETE', body: JSON.stringify({ reason: '가족 관계 종료' }),
    }))
  })

  it('구성원_후보와_현재_구성원과_감사_Page를_각_조회_Endpoint에서_요청한다', async () => {
    const fetchMock = jsonFetchMock(EMPTY_PAGE)
    vi.stubGlobal('fetch', fetchMock)

    await adminFamilyGroupsApi.getMembers(11, 1, 8)
    await adminFamilyGroupsApi.getMemberCandidates('김 회원', 2, 6)
    await adminFamilyGroupsApi.getAuditLogs(11, 3, 8)

    expect(fetchMock).toHaveBeenNthCalledWith(
      1,
      '/api/admin/family-groups/11/members?page=1&size=8',
      expect.any(Object),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      2,
      '/api/admin/family-groups/member-candidates?page=2&size=6&query=%EA%B9%80+%ED%9A%8C%EC%9B%90',
      expect.any(Object),
    )
    expect(fetchMock).toHaveBeenNthCalledWith(
      3,
      '/api/admin/family-groups/11/audit-logs?page=3&size=8',
      expect.any(Object),
    )
  })

  it('그룹_해제는_명시적인_해제_Endpoint와_사유를_사용한다', async () => {
    const fetchMock = jsonFetchMock({})
    vi.stubGlobal('fetch', fetchMock)

    await adminFamilyGroupsApi.dissolveGroup(11, '운영 종료')

    expect(fetchMock).toHaveBeenCalledWith('/api/admin/family-groups/11/dissolution', expect.objectContaining({
      method: 'POST', body: JSON.stringify({ reason: '운영 종료' }),
    }))
  })

  it('공통_인증과_상태_오류를_관리자에게_이해_가능한_메시지로_변환한다', () => {
    expect(getFamilyGroupErrorMessage(responseError(401), 'fallback')).toBe('관리자 로그인이 필요합니다.')
    expect(getFamilyGroupErrorMessage(responseError(403), 'fallback')).toBe('가족 그룹을 관리할 권한이 없습니다.')
    expect(getFamilyGroupErrorMessage(responseError(404), 'fallback')).toBe('가족 그룹 또는 회원을 찾을 수 없습니다.')
    expect(getFamilyGroupErrorMessage(responseError(409), 'fallback')).toBe(
      '현재 상태에서는 요청을 처리할 수 없습니다. 목록을 새로고침해 주세요.',
    )
  })
})

function jsonResponse(body: unknown) {
  return new Response(JSON.stringify(body), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

function jsonFetchMock(body: unknown) {
  return vi.fn().mockImplementation(() => Promise.resolve(jsonResponse(body)))
}

function responseError(status: number) {
  return new ResponseError(new Response(null, { status }), 'failed')
}
