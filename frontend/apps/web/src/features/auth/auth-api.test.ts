import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { authApi } from './auth-api'
import { clearWebAccessToken, setWebAccessToken } from '../../api/web-access-token-memory'

const ACCOUNT = {
  subject: 'member-subject',
  memberId: 1,
  email: 'member@horse.test',
  role: 'MEMBER',
  status: 'ACTIVE',
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('authApi', () => {
  beforeEach(() => {
    clearWebAccessToken()
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    clearWebAccessToken()
  })

  it('로그인_전에_CSRF를_초기화하고_Cookie값을_Header로_전달한다', async () => {
    const fetchMock = vi.fn()
      .mockImplementationOnce(() => {
        document.cookie = 'XSRF-TOKEN=csrf-token-value; Path=/'
        return Promise.resolve(jsonResponse({ headerName: 'X-XSRF-TOKEN', cookieName: 'XSRF-TOKEN' }))
      })
      .mockResolvedValueOnce(jsonResponse({
        accessToken: 'access-token-value',
        tokenType: 'Bearer',
        accessTokenExpiresAt: '2026-08-08T11:15:00Z',
      }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await authApi.login({ email: 'member@horse.test', password: 'password-1234' })

    expect(result.accessToken).toBe('access-token-value')
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/web/csrf')
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: 'GET', credentials: 'include' })
    expect(fetchMock.mock.calls[1][0]).toBe('/api/auth/web/login')
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'csrf-token-value' }),
    })
  })

  it('현재_계정_조회에_메모리_Access_Token을_Bearer로_전달한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ACCOUNT))
    vi.stubGlobal('fetch', fetchMock)
    setWebAccessToken('memory-access-token')

    await expect(authApi.getCurrentAccount()).resolves.toEqual(ACCOUNT)

    expect(fetchMock.mock.calls[0][0]).toBe('/api/auth/me')
    expect(fetchMock.mock.calls[0][1]).toMatchObject({
      headers: expect.objectContaining({ Authorization: 'Bearer memory-access-token' }),
    })
  })

  it('로그아웃에_CSRF와_credentials를_전달한다', async () => {
    const fetchMock = vi.fn()
      .mockImplementationOnce(() => {
        document.cookie = 'XSRF-TOKEN=logout-csrf-token; Path=/'
        return Promise.resolve(jsonResponse({ headerName: 'X-XSRF-TOKEN', cookieName: 'XSRF-TOKEN' }))
      })
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetchMock)

    await authApi.logout()

    expect(fetchMock.mock.calls[1][0]).toBe('/api/auth/web/logout')
    expect(fetchMock.mock.calls[1][1]).toMatchObject({
      method: 'POST',
      credentials: 'include',
      headers: expect.objectContaining({ 'X-XSRF-TOKEN': 'logout-csrf-token' }),
    })
  })
})
