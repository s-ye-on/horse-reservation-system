import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { AuthFormField } from './auth-form-field'
import { validateAuthForm } from './auth-form-validation'
import { useAuthForm } from './use-auth-form'
import { useAuth } from './use-auth'
import './auth-pages.css'

interface LoginLocationState {
  signupCompleted?: boolean
  from?: string
}

function authenticatedDestination(state: LoginLocationState | null, role: string) {
  if (state?.from?.startsWith('/') && !state.from.startsWith('//')) return state.from
  return role === 'ADMIN' ? '/admin' : '/reservations'
}

export function AuthLoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const state = location.state as LoginLocationState | null
  const titleRef = useRef<HTMLHeadingElement>(null)
  const successRef = useRef<HTMLParagraphElement>(null)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const { formRef, alertRef, isSubmitting, error, clearFieldError, submit } = useAuthForm()

  useEffect(() => {
    if (state?.signupCompleted) successRef.current?.focus()
    else titleRef.current?.focus()
  }, [state?.signupCompleted])

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    void submit(validateAuthForm({ email, password }, false), async () => {
      const account = await login({ email, password })
      navigate(authenticatedDestination(state, account.role), {
        replace: true,
        state: { authLoginCompleted: true },
      })
    }, '로그인하지 못했습니다. 이메일과 비밀번호를 확인해 주세요.')
  }

  return (
    <main className="auth-page-shell">
      <section className="auth-form-panel" aria-labelledby="login-title">
        <Link className="auth-back-link" to="/">홈</Link>
        <header className="auth-page-heading">
          <h1 id="login-title" ref={titleRef} tabIndex={-1}>로그인</h1>
          <p>계정으로 로그인하고 수업 예약을 확인하세요.</p>
        </header>
        {state?.signupCompleted ? <p ref={successRef} tabIndex={-1} className="auth-form-success" role="status">회원가입이 완료되었습니다. 로그인해 주세요.</p> : null}
        <form ref={formRef} onSubmit={handleSubmit} noValidate aria-busy={isSubmitting}>
          <fieldset disabled={isSubmitting}>
            <legend className="auth-sr-only">로그인 정보</legend>
            <AuthFormField id="login-email" label="이메일" name="email" type="email" autoComplete="email" maxLength={254} required value={email} error={error?.fields.email} onChange={(event) => { setEmail(event.target.value); clearFieldError('email') }} />
            <AuthFormField id="login-password" label="비밀번호" name="password" type="password" autoComplete="current-password" required value={password} error={error?.fields.password} onChange={(event) => { setPassword(event.target.value); clearFieldError('password') }} />
          </fieldset>
          {error ? <p ref={alertRef} tabIndex={-1} className="auth-form-error" role="alert">{error.message}</p> : null}
          {isSubmitting ? <p className="auth-field-hint" role="status">로그인하고 있습니다.</p> : null}
          <button type="submit" disabled={isSubmitting}>{isSubmitting ? '로그인 중' : '로그인'}</button>
        </form>
        <p className="auth-form-footer">아직 계정이 없으신가요? <Link to="/signup">회원가입</Link></p>
      </section>
    </main>
  )
}
