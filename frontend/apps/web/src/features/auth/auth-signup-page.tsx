import { useEffect, useRef, useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { authApi } from './auth-api'
import { AuthFormField } from './auth-form-field'
import { AUTH_PASSWORD_HELP, validateAuthForm } from './auth-form-validation'
import { useAuthForm } from './use-auth-form'
import './auth-pages.css'

export function AuthSignupPage() {
  const navigate = useNavigate()
  const titleRef = useRef<HTMLHeadingElement>(null)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const { formRef, alertRef, isSubmitting, error, clearFieldError, submit } = useAuthForm()

  useEffect(() => { titleRef.current?.focus() }, [])

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    void submit(validateAuthForm({ email, password, name, phone }, true), async () => {
      await authApi.signup({ email, password, name, phone })
      navigate('/login', { replace: true, state: { signupCompleted: true } })
    }, '회원가입하지 못했습니다. 입력 내용을 확인해 주세요.')
  }

  return (
    <main className="auth-page-shell">
      <section className="auth-form-panel" aria-labelledby="signup-title">
        <Link className="auth-back-link" to="/">홈</Link>
        <header className="auth-page-heading">
          <h1 id="signup-title" ref={titleRef} tabIndex={-1}>회원가입</h1>
          <p>수업 예약에 사용할 계정을 만들어 주세요.</p>
        </header>
        <form ref={formRef} onSubmit={handleSubmit} noValidate aria-busy={isSubmitting}>
          <fieldset disabled={isSubmitting}>
            <legend className="auth-sr-only">회원가입 정보</legend>
            <AuthFormField id="signup-email" label="이메일" name="email" type="email" autoComplete="email" maxLength={254} required value={email} error={error?.fields.email} onChange={(event) => { setEmail(event.target.value); clearFieldError('email') }} />
            <AuthFormField id="signup-password" label="비밀번호" name="password" type="password" autoComplete="new-password" minLength={8} required value={password} hint={AUTH_PASSWORD_HELP} error={error?.fields.password} onChange={(event) => { setPassword(event.target.value); clearFieldError('password') }} />
            <AuthFormField id="signup-name" label="이름" name="name" type="text" autoComplete="name" maxLength={100} required value={name} error={error?.fields.name} onChange={(event) => { setName(event.target.value); clearFieldError('name') }} />
            <AuthFormField id="signup-phone" label="전화번호" name="phone" type="tel" autoComplete="tel" maxLength={30} required value={phone} error={error?.fields.phone} onChange={(event) => { setPhone(event.target.value); clearFieldError('phone') }} />
          </fieldset>
          {error ? <p ref={alertRef} tabIndex={-1} className="auth-form-error" role="alert">{error.message}</p> : null}
          {isSubmitting ? <p className="auth-field-hint" role="status">회원가입하고 있습니다.</p> : null}
          <button type="submit" disabled={isSubmitting}>{isSubmitting ? '가입 중' : '회원가입'}</button>
        </form>
        <p className="auth-form-footer">이미 계정이 있으신가요? <Link to="/login">로그인</Link></p>
      </section>
    </main>
  )
}
