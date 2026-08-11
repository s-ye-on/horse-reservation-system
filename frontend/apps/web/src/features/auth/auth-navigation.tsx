import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { getAuthErrorMessage } from './auth-api'
import { useAuth } from './use-auth'

export function AuthNavigation() {
  const { account, logout } = useAuth()
  const navigate = useNavigate()
  const [isLoggingOut, setIsLoggingOut] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!account) {
    return (
      <nav className="auth-session-navigation" aria-label="계정 메뉴">
        <Link to="/login">로그인</Link>
        <Link to="/signup">회원가입</Link>
      </nav>
    )
  }

  const handleLogout = async () => {
    if (isLoggingOut) return
    setIsLoggingOut(true)
    setError(null)
    try {
      await logout()
      navigate('/login', { replace: true })
    } catch (logoutError) {
      setError(await getAuthErrorMessage(logoutError, '로그아웃하지 못했습니다. 다시 시도해 주세요.'))
    } finally {
      setIsLoggingOut(false)
    }
  }

  return (
    <div className="auth-session-bar">
      <p><strong>{account.email}</strong> 로그인</p>
      <button type="button" onClick={handleLogout} disabled={isLoggingOut}>
        {isLoggingOut ? '로그아웃 중' : '로그아웃'}
      </button>
      {error ? <p role="alert">{error}</p> : null}
    </div>
  )
}
