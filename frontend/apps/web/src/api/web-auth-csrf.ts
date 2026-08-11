import { WebAuthControllerApi } from '@horse/api-client'
import { cookieApiConfiguration } from './web-api-base-configuration'

const CSRF_COOKIE_NAME = 'XSRF-TOKEN'
let csrfInitializationRequired = false

export const webAuthApiClient = new WebAuthControllerApi(cookieApiConfiguration)

function readCookie(cookieName: string): string | null {
  const prefix = `${encodeURIComponent(cookieName)}=`
  const cookie = document.cookie
    .split(';')
    .map((value) => value.trim())
    .find((value) => value.startsWith(prefix))

  if (!cookie) return null

  const value = cookie.slice(prefix.length)
  try {
    return decodeURIComponent(value)
  } catch {
    return value
  }
}

export async function ensureWebCsrfToken(): Promise<string> {
  const existingToken = readCookie(CSRF_COOKIE_NAME)
  if (existingToken && !csrfInitializationRequired) return existingToken

  const contract = await webAuthApiClient.initializeWebCsrfToken()
  const token = readCookie(contract.cookieName)
  if (!token) {
    throw new Error('CSRF Cookie를 확인할 수 없습니다.')
  }
  csrfInitializationRequired = false
  return token
}

export function requireWebCsrfInitialization(): void {
  csrfInitializationRequired = true
}
