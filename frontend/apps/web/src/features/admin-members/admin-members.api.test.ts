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
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(new Response(JSON.stringify({
      id: 7,
      name: '김승마',
      phone: '010-0000-0000',
      generalRideCount: 20,
      dressageRideCount: 0,
      jumpingRideCount: 0,
      dressageApproved: true,
      jumpingApproved: false,
      canUseLargeArena: true,
    }), { status: 200, headers: { 'Content-Type': 'application/json' } })))
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
})
