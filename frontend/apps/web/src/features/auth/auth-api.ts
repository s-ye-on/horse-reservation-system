import {
  AuthControllerApi,
  ResponseError,
  type AuthAccountResponse,
  type AuthLoginRequest,
  type AuthSignupRequest,
  type ErrorResponse,
  type WebAuthTokenResponse,
} from '@horse/api-client'
import {
  bearerApiConfiguration,
  publicApiConfiguration,
} from '../../api/web-api-configuration'
import {
  ensureWebCsrfToken,
  requireWebCsrfInitialization,
  webAuthApiClient,
} from '../../api/web-auth-csrf'
import { refreshWebAuthentication } from '../../api/web-auth-session'

const authApiClient = new AuthControllerApi(publicApiConfiguration)
const authenticatedAuthApiClient = new AuthControllerApi(bearerApiConfiguration)

const FIELD_LABELS: Record<string, string> = {
  email: '이메일',
  password: '비밀번호',
  name: '이름',
  phone: '전화번호',
}

export interface AuthApi {
  signup(request: AuthSignupRequest): Promise<AuthAccountResponse>
  login(request: AuthLoginRequest): Promise<WebAuthTokenResponse>
  refreshAccessToken(): Promise<WebAuthTokenResponse>
  getCurrentAccount(): Promise<AuthAccountResponse>
  logout(): Promise<void>
}

export const authApi: AuthApi = {
  signup: (request) => authApiClient.signup({ authSignupRequest: request }),
  login: async (request) => {
    const csrfToken = await ensureWebCsrfToken()
    try {
      return await webAuthApiClient.webLogin({
        xXSRFTOKEN: csrfToken,
        authLoginRequest: request,
      })
    } catch (error) {
      if (error instanceof ResponseError && error.response.status === 403) {
        requireWebCsrfInitialization()
      }
      throw error
    }
  },
  refreshAccessToken: refreshWebAuthentication,
  getCurrentAccount: () => authenticatedAuthApiClient.getCurrentAuthAccount(),
  logout: async () => {
    const csrfToken = await ensureWebCsrfToken()
    try {
      await webAuthApiClient.webLogout({ xXSRFTOKEN: csrfToken })
    } catch (error) {
      if (error instanceof ResponseError && error.response.status === 403) {
        requireWebCsrfInitialization()
      }
      throw error
    }
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
