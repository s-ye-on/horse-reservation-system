import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { getAuthErrorMessage } from './auth-api'
import { useAuth } from './use-auth'
import './auth-pages.css'

interface LoginLocationState {
  signupCompleted?: boolean
  from?: string
}

function authenticatedDestination(state: LoginLocationState | null, role: string) {
  if (state?.from?.startsWith('/') && !state.from.startsWith('//')) {
    return state.from
  }
  return role === 'ADMIN' ? '/admin' : '/reservations'
}

export function AuthLoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as LoginLocationState | null
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (isSubmitting) return
    setIsSubmitting(true)
    setError(null)
    try {
      const account = await login({ email, password })
      navigate(authenticatedDestination(state, account.role), { replace: true })
    } catch (loginError) {
      setError(await getAuthErrorMessage(loginError, '로그인하지 못했습니다. 이메일과 비밀번호를 확인해 주세요.'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="auth-page-shell">
      <section className="auth-form-panel" aria-labelledby="login-title">
        <Link className="auth-back-link" to="/">홈</Link>
        <h1 id="login-title">로그인</h1>
        {state?.signupCompleted ? <p role="status">회원가입이 완료되었습니다. 로그인해 주세요.</p> : null}
        <form onSubmit={handleSubmit}>
          <label htmlFor="login-email">이메일</label>
          <input
            id="login-email"
            name="email"
            type="email"
            autoComplete="email"
            maxLength={254}
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />
          <label htmlFor="login-password">비밀번호</label>
          <input
            id="login-password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          {error ? <p className="auth-form-error" role="alert">{error}</p> : null}
          <button type="submit" disabled={isSubmitting}>
            {isSubmitting ? '로그인 중' : '로그인'}
          </button>
        </form>
        <p>아직 계정이 없으신가요? <Link to="/signup">회원가입</Link></p>
      </section>
    </main>
  )
}
