import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearWebAccessToken, getWebAccessToken } from './web-access-token-memory'
import { beginWebAuthOperation, refreshWebAuthentication } from './web-auth-session'

function refreshResponse(): Response {
  return new Response(JSON.stringify({
    accessToken: 'refreshed-access-token',
    tokenType: 'Bearer',
    accessTokenExpiresAt: '2026-08-09T12:15:00Z',
  }), { status: 200, headers: { 'Content-Type': 'application/json' } })
}

describe('refreshWebAuthentication', () => {
  beforeEach(() => {
    beginWebAuthOperation()
    clearWebAccessToken()
    document.cookie = 'XSRF-TOKEN=csrf-token; Path=/'
  })

  afterEach(() => {
    vi.unstubAllGlobals()
    clearWebAccessToken()
    document.cookie = 'XSRF-TOKEN=; Max-Age=0; Path=/'
  })

  it('여러_caller가_같은_refresh_Promise와_HTTP_응답을_공유한다', async () => {
    const fetchMock = vi.fn().mockResolvedValue(refreshResponse())
    vi.stubGlobal('fetch', fetchMock)

    const first = refreshWebAuthentication()
    const second = refreshWebAuthentication()
    const third = refreshWebAuthentication()
    const results = await Promise.all([first, second, third])

    expect(results.map(({ accessToken }) => accessToken)).toEqual([
      'refreshed-access-token', 'refreshed-access-token', 'refreshed-access-token',
    ])
    expect(fetchMock).toHaveBeenCalledOnce()
    expect(getWebAccessToken()).toBe('refreshed-access-token')
  })

  it('logout으로_작업_version이_바뀌면_늦은_refresh가_Token을_복구하지_못한다', async () => {
    let resolveRefresh!: (response: Response) => void
    const fetchMock = vi.fn().mockImplementation(() => new Promise<Response>((resolve) => {
      resolveRefresh = resolve
    }))
    vi.stubGlobal('fetch', fetchMock)

    const refresh = refreshWebAuthentication()
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledOnce())
    beginWebAuthOperation()
    clearWebAccessToken()
    resolveRefresh(refreshResponse())

    await expect(refresh).rejects.toThrow('superseded')
    expect(getWebAccessToken()).toBeNull()
  })

  it('CSRF_403_다음_명시적_재시도는_CSRF를_다시_초기화한다', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(null, { status: 403 }))
      .mockImplementationOnce(() => {
        document.cookie = 'XSRF-TOKEN=fresh-csrf-token; Path=/'
        return Promise.resolve(new Response(JSON.stringify({
          headerName: 'X-XSRF-TOKEN', cookieName: 'XSRF-TOKEN',
        }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
      })
      .mockResolvedValueOnce(refreshResponse())
    vi.stubGlobal('fetch', fetchMock)

    await expect(refreshWebAuthentication()).rejects.toMatchObject({ response: { status: 403 } })
    await expect(refreshWebAuthentication()).resolves.toMatchObject({ accessToken: 'refreshed-access-token' })

    expect(fetchMock.mock.calls.map(([input]) => input)).toEqual([
      '/api/auth/web/refresh',
      '/api/auth/web/csrf',
      '/api/auth/web/refresh',
    ])
    expect(new Headers(fetchMock.mock.calls[2][1]?.headers).get('X-XSRF-TOKEN')).toBe('fresh-csrf-token')
  })
})
