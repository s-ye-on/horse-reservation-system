import { createContext } from 'react'
import type { AuthAccountResponse, AuthLoginRequest } from '@horse/api-client'

export interface AuthContextValue {
  account: AuthAccountResponse | null
  isAuthenticated: boolean
  login(request: AuthLoginRequest): Promise<AuthAccountResponse>
  logout(): Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
