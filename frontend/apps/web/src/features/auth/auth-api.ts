import {
  AuthControllerApi,
  ResponseError,
  WebAuthControllerApi,
  type AuthAccountResponse,
  type AuthLoginRequest,
  type AuthSignupRequest,
  type ErrorResponse,
  type WebAuthTokenResponse,
} from '@horse/api-client'
import {
  bearerApiConfiguration,
  cookieApiConfiguration,
  publicApiConfiguration,
} from '../../api/web-api-configuration'

const authApiClient = new AuthControllerApi(publicApiConfiguration)
const authenticatedAuthApiClient = new AuthControllerApi(bearerApiConfiguration)
const webAuthApiClient = new WebAuthControllerApi(cookieApiConfiguration)

const FIELD_LABELS: Record<string, string> = {
  email: '이메일',
  password: '비밀번호',
  name: '이름',
  phone: '전화번호',
}

export interface AuthApi {
  signup(request: AuthSignupRequest): Promise<AuthAccountResponse>
  login(request: AuthLoginRequest): Promise<WebAuthTokenResponse>
  getCurrentAccount(): Promise<AuthAccountResponse>
  logout(): Promise<void>
}

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

async function initializeCsrfToken(): Promise<string> {
  const contract = await webAuthApiClient.initializeWebCsrfToken()
  const token = readCookie(contract.cookieName)
  if (!token) {
    throw new Error('CSRF Cookie를 확인할 수 없습니다.')
  }
  return token
}

export const authApi: AuthApi = {
  signup: (request) => authApiClient.signup({ authSignupRequest: request }),
  login: async (request) => {
    const csrfToken = await initializeCsrfToken()
    return webAuthApiClient.webLogin({
      xXSRFTOKEN: csrfToken,
      authLoginRequest: request,
    })
  },
  getCurrentAccount: () => authenticatedAuthApiClient.getCurrentAuthAccount(),
  logout: async () => {
    const csrfToken = await initializeCsrfToken()
    await webAuthApiClient.webLogout({ xXSRFTOKEN: csrfToken })
  },
}

function isErrorResponse(value: unknown): value is ErrorResponse {
  if (!value || typeof value !== 'object') return false
  const candidate = value as Record<string, unknown>
  return typeof candidate.message === 'string' && Array.isArray(candidate.fieldErrors)
}

export async function getAuthErrorMessage(error: unknown, fallback: string): Promise<string> {
  if (!(error instanceof ResponseError)) return fallback

  try {
    const body: unknown = await error.response.clone().json()
    if (!isErrorResponse(body)) return fallback
    if (body.fieldErrors.length === 0) return body.message

    return body.fieldErrors
      .map(({ field, message }) => `${FIELD_LABELS[field] ?? field}: ${message}`)
      .join(' ')
  } catch {
    return fallback
  }
}
