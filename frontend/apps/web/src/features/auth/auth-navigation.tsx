import { useState } from 'react'
import { NavLink, useNavigate } from 'react-router'
import { AppLogo } from '../../components/app-logo'
import { getAuthErrorMessage } from './auth-api'
import { useAuth } from './use-auth'

interface AuthNavigationProps {
  variant?: 'default' | 'admin'
}

export function AuthNavigation({ variant = 'default' }: AuthNavigationProps) {
  const { account, logout } = useAuth()
  const navigate = useNavigate()
  const [isLoggingOut, setIsLoggingOut] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!account) {
    return (
      <header className="app-topbar">
        <div className="app-topbar-inner">
          <AppLogo />
          <nav className="public-session-navigation" aria-label="계정 메뉴">
            <NavLink to="/login">로그인</NavLink>
            <NavLink className="app-navigation-primary-action" to="/signup">회원가입</NavLink>
          </nav>
        </div>
      </header>
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

  if (account.role === 'ADMIN' && variant === 'admin') {
    return (
      <header className="admin-account-header">
        <div className="admin-account-brand">
          <AppLogo />
          <NavLink className="admin-account-home-link" to="/">서비스 홈</NavLink>
        </div>
        <div className="auth-account-actions">
          <p><strong>{account.email}</strong> 로그인</p>
          <button type="button" onClick={handleLogout} disabled={isLoggingOut}>
            {isLoggingOut ? '로그아웃 중' : '로그아웃'}
          </button>
        </div>
        {error ? <p className="auth-session-error" role="alert">{error}</p> : null}
      </header>
    )
  }

  if (account.role === 'ADMIN') {
    return (
      <header className="app-topbar">
        <div className="app-topbar-inner member-topbar-inner">
          <AppLogo />
          <nav className="member-session-navigation" aria-label="관리자 메뉴">
            <NavLink to="/" end>홈</NavLink>
            <NavLink to="/admin">관리자</NavLink>
          </nav>
          <div className="auth-account-actions">
            <p><strong>{account.email}</strong> 로그인</p>
            <button type="button" onClick={handleLogout} disabled={isLoggingOut}>
              {isLoggingOut ? '로그아웃 중' : '로그아웃'}
            </button>
          </div>
          {error ? <p className="auth-session-error" role="alert">{error}</p> : null}
        </div>
      </header>
    )
  }

  return (
    <header className="app-topbar">
      <div className="app-topbar-inner member-topbar-inner">
        <AppLogo />
        <nav className="member-session-navigation" aria-label="회원 메뉴">
          <NavLink to="/" end>홈</NavLink>
          <NavLink to="/reservations">수업 예약</NavLink>
          <NavLink to="/my/reservations">내 예약</NavLink>
          <NavLink to="/my/coupons">내 쿠폰</NavLink>
        </nav>
        <div className="auth-account-actions">
          <p><strong>{account.email}</strong> 로그인</p>
          <button type="button" onClick={handleLogout} disabled={isLoggingOut}>
            {isLoggingOut ? '로그아웃 중' : '로그아웃'}
          </button>
        </div>
        {error ? <p className="auth-session-error" role="alert">{error}</p> : null}
      </div>
    </header>
  )
}
