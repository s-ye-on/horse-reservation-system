import type { FetchAPI } from '@horse/api-client'
import { getWebAccessToken } from './web-access-token-memory'
import {
  expireWebAuthentication,
  getWebAuthOperationVersion,
  isCurrentWebAuthOperation,
  refreshWebAuthentication,
} from './web-auth-session'

const AUTH_REFRESH_EXCLUDED_PATHS = new Set([
  '/api/auth/signup',
  '/api/auth/web/csrf',
  '/api/auth/web/login',
  '/api/auth/web/refresh',
  '/api/auth/web/logout',
])

function requestPath(input: RequestInfo | URL): string {
  const value = typeof input === 'string' || input instanceof URL ? input.toString() : input.url
  return new URL(value, globalThis.location?.origin ?? 'http://localhost').pathname
}

function bearerToken(headersInit: HeadersInit | undefined): string | null {
  const value = new Headers(headersInit).get('Authorization')
  if (!value?.startsWith('Bearer ')) return null
  return value.slice('Bearer '.length)
}

function withCurrentBearer(init: RequestInit): RequestInit | null {
  const token = getWebAccessToken()
  if (!token) return null
  const headers = new Headers(init.headers)
  headers.set('Authorization', `Bearer ${token}`)
  return { ...init, headers }
}

export const authenticatedFetch: FetchAPI = async (input, init = {}) => {
  const requestVersion = getWebAuthOperationVersion()
  const requestAccessToken = bearerToken(init.headers)
  const response = await globalThis.fetch(input, init)

  if (
    response.status !== 401
    || !requestAccessToken
    || AUTH_REFRESH_EXCLUDED_PATHS.has(requestPath(input))
    || !isCurrentWebAuthOperation(requestVersion)
  ) {
    return response
  }

  if (getWebAccessToken() === requestAccessToken) {
    try {
      await refreshWebAuthentication()
    } catch {
      return response
    }
  }

  if (!isCurrentWebAuthOperation(requestVersion)) return response
  const retryInit = withCurrentBearer(init)
  if (!retryInit) return response

  const retryResponse = await globalThis.fetch(input, retryInit)
  if (retryResponse.status === 401 && isCurrentWebAuthOperation(requestVersion)) {
    expireWebAuthentication()
  }
  return retryResponse
}
