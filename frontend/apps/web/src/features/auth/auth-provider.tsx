import { useEffect, useMemo, useState, type ReactNode } from 'react'
import type { AuthAccountResponse } from '@horse/api-client'
import { clearWebAccessToken, setWebAccessToken } from '../../api/web-access-token-memory'
import { authApi, type AuthApi } from './auth-api'
import { AuthContext, type AuthContextValue } from './auth-context'

interface AuthProviderProps {
  children: ReactNode
  api?: AuthApi
}

export function AuthProvider({ children, api = authApi }: AuthProviderProps) {
  const [account, setAccount] = useState<AuthAccountResponse | null>(null)

  useEffect(() => {
    clearWebAccessToken()
    return clearWebAccessToken
  }, [])

  const value = useMemo<AuthContextValue>(() => ({
    account,
    isAuthenticated: account !== null,
    login: async (request) => {
      const tokenResponse = await api.login(request)
      setWebAccessToken(tokenResponse.accessToken)
      try {
        const currentAccount = await api.getCurrentAccount()
        setAccount(currentAccount)
        return currentAccount
      } catch (error) {
        clearWebAccessToken()
        setAccount(null)
        throw error
      }
    },
    logout: async () => {
      await api.logout()
      clearWebAccessToken()
      setAccount(null)
    },
  }), [account, api])

  return <AuthContext value={value}>{children}</AuthContext>
}
