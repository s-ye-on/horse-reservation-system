import {
  AdminMemberQueryControllerApi,
  AuthControllerApi,
  MemberAvailableRidingClassesControllerApi,
} from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearWebAccessToken, setWebAccessToken } from './web-access-token-memory'
import { bearerApiConfiguration } from './web-api-configuration'

const ACCOUNT = {
  subject: 'member-subject',
  memberId: 1,
  email: 'member@horse.test',
  role: 'MEMBER',
  status: 'ACTIVE',
}

function accountResponse(): Response {
  return new Response(JSON.stringify(ACCOUNT), {
    status: 200,
    headers: { 'Content-Type': 'application/json' },
  })
}

afterEach(() => {
  vi.unstubAllGlobals()
  clearWebAccessToken()
})

describe('bearerApiConfiguration', () => {
  it('같은_Client가_요청마다_최신_메모리_Access_Token을_조회한다', async () => {
    const fetchMock = vi.fn().mockImplementation(() => Promise.resolve(accountResponse()))
    vi.stubGlobal('fetch', fetchMock)
    const client = new AuthControllerApi(bearerApiConfiguration)

    setWebAccessToken('access-token-a1')
    await client.getCurrentAuthAccount()
    setWebAccessToken('access-token-a2')
    await client.getCurrentAuthAccount()
    clearWebAccessToken()
    await client.getCurrentAuthAccount()

    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).get('Authorization')).toBe('Bearer access-token-a1')
    expect(new Headers(fetchMock.mock.calls[1][1]?.headers).get('Authorization')).toBe('Bearer access-token-a2')
    expect(new Headers(fetchMock.mock.calls[2][1]?.headers).get('Authorization')).toBeNull()
  })

  it('관리자와_회원_보호_API가_메모리_Access_Token을_Bearer로_전달한다', async () => {
    const adminMembersClient = new AdminMemberQueryControllerApi(bearerApiConfiguration)
    const eligibleClassesClient = new MemberAvailableRidingClassesControllerApi(bearerApiConfiguration)
    setWebAccessToken('protected-access-token')

    const adminRequest = await adminMembersClient.getMembersRequestOpts({})
    const memberRequest = await eligibleClassesClient.getAvailableRidingClassesRequestOpts()

    expect(new Headers(adminRequest.headers).get('Authorization')).toBe('Bearer protected-access-token')
    expect(new Headers(memberRequest.headers).get('Authorization')).toBe('Bearer protected-access-token')
  })
})
