import { createContext } from 'react'
import type { AuthAccountResponse, AuthLoginRequest } from '@horse/api-client'

export type AuthStatus = 'initializing' | 'authenticated' | 'unauthenticated' | 'restore-error'

export interface AuthContextValue {
  account: AuthAccountResponse | null
  isAuthenticated: boolean
  status: AuthStatus
  login(request: AuthLoginRequest): Promise<AuthAccountResponse>
  logout(): Promise<void>
  retrySessionRestore(): Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
