import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { ResponseError, type AuthAccountResponse } from '@horse/api-client'
import { clearWebAccessToken, setWebAccessToken } from '../../api/web-access-token-memory'
import { clearWebAccountQueryCache } from '../../api/web-query-client'
import {
  beginWebAuthOperation,
  getWebAuthOperationVersion,
  isCurrentWebAuthOperation,
  isStaleWebAuthOperationError,
  subscribeToWebAuthSession,
} from '../../api/web-auth-session'
import { authApi, type AuthApi } from './auth-api'
import { AuthContext, type AuthContextValue, type AuthStatus } from './auth-context'

interface AuthProviderProps {
  children: ReactNode
  api?: AuthApi
}

export function AuthProvider({ children, api = authApi }: AuthProviderProps) {
  const [account, setAccount] = useState<AuthAccountResponse | null>(null)
  const [status, setStatus] = useState<AuthStatus>('initializing')
  const mountedRef = useRef(false)
  const restorePromiseRef = useRef<Promise<void> | null>(null)

  const restoreSession = useCallback((): Promise<void> => {
    if (restorePromiseRef.current) return restorePromiseRef.current

    const operationVersion = getWebAuthOperationVersion()
    setStatus('initializing')
    const promise = (async () => {
      try {
        const tokenResponse = await api.refreshAccessToken()
        if (!isCurrentWebAuthOperation(operationVersion)) return
        setWebAccessToken(tokenResponse.accessToken)
        const currentAccount = await api.getCurrentAccount()
        if (!isCurrentWebAuthOperation(operationVersion) || !mountedRef.current) return
        clearWebAccountQueryCache()
        setAccount(currentAccount)
        setStatus('authenticated')
      } catch (error) {
        if (isStaleWebAuthOperationError(error) || !isCurrentWebAuthOperation(operationVersion)) return
        clearWebAccessToken()
        clearWebAccountQueryCache()
        if (!mountedRef.current) return
        setAccount(null)
        if (error instanceof ResponseError && error.response.status === 401) {
          setStatus('unauthenticated')
        } else {
          setStatus('restore-error')
        }
      }
    })()

    restorePromiseRef.current = promise
    void promise.finally(() => {
      if (restorePromiseRef.current === promise) restorePromiseRef.current = null
    })
    return promise
  }, [api])

  useEffect(() => {
    mountedRef.current = true
    const unsubscribe = subscribeToWebAuthSession((event) => {
      if (!mountedRef.current) return
      clearWebAccountQueryCache()
      setAccount(null)
      setStatus(event === 'unauthorized' ? 'unauthenticated' : 'restore-error')
    })
    void restoreSession()
    return () => {
      mountedRef.current = false
      unsubscribe()
    }
  }, [restoreSession])

  const value = useMemo<AuthContextValue>(() => ({
    account,
    isAuthenticated: status === 'authenticated' && account !== null,
    status,
    login: async (request) => {
      const operationVersion = beginWebAuthOperation()
      const tokenResponse = await api.login(request)
      if (!isCurrentWebAuthOperation(operationVersion)) {
        throw new Error('로그인 요청이 더 최신 인증 작업으로 대체되었습니다.')
      }
      setWebAccessToken(tokenResponse.accessToken)
      try {
        const currentAccount = await api.getCurrentAccount()
        if (!isCurrentWebAuthOperation(operationVersion)) {
          throw new Error('로그인 요청이 더 최신 인증 작업으로 대체되었습니다.')
        }
        clearWebAccountQueryCache()
        setAccount(currentAccount)
        setStatus('authenticated')
        return currentAccount
      } catch (error) {
        if (isCurrentWebAuthOperation(operationVersion)) {
          clearWebAccessToken()
          setAccount(null)
          setStatus('unauthenticated')
        }
        throw error
      }
    },
    logout: async () => {
      const operationVersion = beginWebAuthOperation()
      await api.logout()
      if (!isCurrentWebAuthOperation(operationVersion)) return
      clearWebAccessToken()
      clearWebAccountQueryCache()
      setAccount(null)
      setStatus('unauthenticated')
    },
    retrySessionRestore: restoreSession,
  }), [account, api, restoreSession, status])

  return (
    <AuthContext value={value}>
      {status === 'initializing' ? (
        <main className="auth-session-state" aria-busy="true">
          <p role="status">로그인 상태를 확인하고 있습니다.</p>
        </main>
      ) : null}
      {status === 'restore-error' ? (
        <aside className="auth-session-state">
          <p role="alert">로그인 상태를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.</p>
          <button type="button" onClick={() => void restoreSession()}>다시 시도</button>
        </aside>
      ) : null}
      {status !== 'initializing' ? children : null}
    </AuthContext>
  )
}
