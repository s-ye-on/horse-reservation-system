import { ResponseError, type WebAuthTokenResponse } from '@horse/api-client'
import { clearWebAccessToken, setWebAccessToken } from './web-access-token-memory'
import {
  ensureWebCsrfToken,
  requireWebCsrfInitialization,
  webAuthApiClient,
} from './web-auth-csrf'

export type WebAuthSessionEvent = 'unauthorized' | 'csrf-failure'

class StaleWebAuthOperationError extends Error {
  constructor() {
    super('A newer authentication operation superseded this request.')
    this.name = 'StaleWebAuthOperationError'
  }
}

let operationVersion = 0
let refreshInFlight: { version: number; promise: Promise<WebAuthTokenResponse> } | null = null
const sessionListeners = new Set<(event: WebAuthSessionEvent) => void>()

export function getWebAuthOperationVersion(): number {
  return operationVersion
}

export function beginWebAuthOperation(): number {
  operationVersion += 1
  return operationVersion
}

export function isCurrentWebAuthOperation(version: number): boolean {
  return version === operationVersion
}

export function isStaleWebAuthOperationError(error: unknown): boolean {
  return error instanceof StaleWebAuthOperationError
}

export function subscribeToWebAuthSession(
  listener: (event: WebAuthSessionEvent) => void,
): () => void {
  sessionListeners.add(listener)
  return () => sessionListeners.delete(listener)
}

function notifySessionListeners(event: WebAuthSessionEvent) {
  sessionListeners.forEach((listener) => listener(event))
}

export function expireWebAuthentication(): void {
  beginWebAuthOperation()
  clearWebAccessToken()
  notifySessionListeners('unauthorized')
}

async function performRefresh(version: number): Promise<WebAuthTokenResponse> {
  try {
    const csrfToken = await ensureWebCsrfToken()
    const response = await webAuthApiClient.refreshWebAccessToken({ xXSRFTOKEN: csrfToken })
    if (!isCurrentWebAuthOperation(version)) throw new StaleWebAuthOperationError()
    setWebAccessToken(response.accessToken)
    return response
  } catch (error) {
    if (!isCurrentWebAuthOperation(version)) throw new StaleWebAuthOperationError()
    if (error instanceof ResponseError && error.response.status === 401) {
      clearWebAccessToken()
      notifySessionListeners('unauthorized')
    } else if (error instanceof ResponseError && error.response.status === 403) {
      clearWebAccessToken()
      requireWebCsrfInitialization()
      notifySessionListeners('csrf-failure')
    }
    throw error
  }
}

export function refreshWebAuthentication(): Promise<WebAuthTokenResponse> {
  const version = getWebAuthOperationVersion()
  if (refreshInFlight?.version === version) return refreshInFlight.promise

  const promise = performRefresh(version)
  refreshInFlight = { version, promise }
  void promise.finally(() => {
    if (refreshInFlight?.promise === promise) refreshInFlight = null
  }).catch(() => undefined)
  return promise
}
