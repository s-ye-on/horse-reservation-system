import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { clearWebAccessToken, getWebAccessToken, setWebAccessToken } from './web-access-token-memory'
import { authenticatedFetch } from './web-authenticated-fetch'
import { beginWebAuthOperation } from './web-auth-session'

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function refreshResponse(token = 'access-token-a2'): Response {
  return jsonResponse({
    accessToken: token,
    tokenType: 'Bearer',
    accessTokenExpiresAt: '2026-08-09T12:15:00Z',
  })
}

function bearer(init: RequestInit | undefined): string | null {
  return new Headers(init?.headers).get('Authorization')
}

describe('authenticatedFetch', () => {
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

  it('동시_401을_single_flight로_갱신하고_모든_요청을_A2로_한_번만_재시도한다', async () => {
    const requestCounts = new Map<string, number>()
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      const path = new URL(input.toString(), 'http://localhost').pathname
      if (path === '/api/auth/web/refresh') return Promise.resolve(refreshResponse())

      const count = (requestCounts.get(path) ?? 0) + 1
      requestCounts.set(path, count)
      return Promise.resolve(count === 1 ? new Response(null, { status: 401 }) : jsonResponse({ path }))
    })
    vi.stubGlobal('fetch', fetchMock)
    setWebAccessToken('access-token-a1')

    const responses = await Promise.all(['/api/a', '/api/b', '/api/c'].map((path) => authenticatedFetch(path, {
      headers: { Authorization: 'Bearer access-token-a1' },
    })))

    expect(responses.map((response) => response.status)).toEqual([200, 200, 200])
    expect(fetchMock.mock.calls.filter(([input]) => input === '/api/auth/web/refresh')).toHaveLength(1)
    expect(getWebAccessToken()).toBe('access-token-a2')
    for (const path of ['/api/a', '/api/b', '/api/c']) {
      const calls = fetchMock.mock.calls.filter(([input]) => input === path)
      expect(calls).toHaveLength(2)
      expect(bearer(calls[0][1])).toBe('Bearer access-token-a1')
      expect(bearer(calls[1][1])).toBe('Bearer access-token-a2')
    }
  })

  it('재시도도_401이면_추가_refresh_없이_Token을_제거한다', async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      if (input === '/api/auth/web/refresh') return Promise.resolve(refreshResponse())
      return Promise.resolve(new Response(null, { status: 401 }))
    })
    vi.stubGlobal('fetch', fetchMock)
    setWebAccessToken('access-token-a1')

    const response = await authenticatedFetch('/api/protected', {
      headers: { Authorization: 'Bearer access-token-a1' },
    })

    expect(response.status).toBe(401)
    expect(fetchMock.mock.calls.filter(([input]) => input === '/api/auth/web/refresh')).toHaveLength(1)
    expect(fetchMock.mock.calls.filter(([input]) => input === '/api/protected')).toHaveLength(2)
    expect(getWebAccessToken()).toBeNull()
  })

  it('403과_인증_Endpoint의_401은_refresh하지_않는다', async () => {
    const fetchMock = vi.fn()
      .mockResolvedValueOnce(new Response(null, { status: 403 }))
      .mockResolvedValueOnce(new Response(null, { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)
    setWebAccessToken('access-token-a1')

    await authenticatedFetch('/api/protected', { headers: { Authorization: 'Bearer access-token-a1' } })
    await authenticatedFetch('/api/auth/web/refresh', { headers: { Authorization: 'Bearer access-token-a1' } })

    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('refresh의_network_또는_5xx_실패는_기존_인증_상태를_폐기하지_않는다', async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      if (input === '/api/auth/web/refresh') return Promise.resolve(new Response(null, { status: 503 }))
      return Promise.resolve(new Response(null, { status: 401 }))
    })
    vi.stubGlobal('fetch', fetchMock)
    setWebAccessToken('access-token-a1')

    const response = await authenticatedFetch('/api/protected', {
      headers: { Authorization: 'Bearer access-token-a1' },
    })

    expect(response.status).toBe(401)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(getWebAccessToken()).toBe('access-token-a1')
  })

  it('refresh의_network_실패도_재시도_loop나_인증_폐기를_만들지_않는다', async () => {
    const fetchMock = vi.fn().mockImplementation((input: RequestInfo | URL) => {
      if (input === '/api/auth/web/refresh') return Promise.reject(new TypeError('network unavailable'))
      return Promise.resolve(new Response(null, { status: 401 }))
    })
    vi.stubGlobal('fetch', fetchMock)
    setWebAccessToken('access-token-a1')

    const response = await authenticatedFetch('/api/protected', {
      headers: { Authorization: 'Bearer access-token-a1' },
    })

    expect(response.status).toBe(401)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(getWebAccessToken()).toBe('access-token-a1')
  })
})
