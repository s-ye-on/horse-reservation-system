import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { authApi, getAuthErrorMessage } from './auth-api'
import './auth-pages.css'

export function AuthSignupPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (isSubmitting) return
    setIsSubmitting(true)
    setError(null)
    try {
      await authApi.signup({ email, password, name, phone })
      navigate('/login', { replace: true, state: { signupCompleted: true } })
    } catch (signupError) {
      setError(await getAuthErrorMessage(signupError, '회원가입하지 못했습니다. 입력 내용을 확인해 주세요.'))
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <main className="auth-page-shell">
      <section className="auth-form-panel" aria-labelledby="signup-title">
        <Link className="auth-back-link" to="/">홈</Link>
        <h1 id="signup-title">회원가입</h1>
        <form onSubmit={handleSubmit}>
          <label htmlFor="signup-email">이메일</label>
          <input id="signup-email" name="email" type="email" autoComplete="email" maxLength={254} required value={email} onChange={(event) => setEmail(event.target.value)} />
          <label htmlFor="signup-password">비밀번호</label>
          <input id="signup-password" name="password" type="password" autoComplete="new-password" minLength={8} maxLength={72} required value={password} onChange={(event) => setPassword(event.target.value)} />
          <p className="auth-field-hint">8자 이상 입력해 주세요.</p>
          <label htmlFor="signup-name">이름</label>
          <input id="signup-name" name="name" type="text" autoComplete="name" maxLength={100} required value={name} onChange={(event) => setName(event.target.value)} />
          <label htmlFor="signup-phone">전화번호</label>
          <input id="signup-phone" name="phone" type="tel" autoComplete="tel" maxLength={30} required value={phone} onChange={(event) => setPhone(event.target.value)} />
          {error ? <p className="auth-form-error" role="alert">{error}</p> : null}
          <button type="submit" disabled={isSubmitting}>
            {isSubmitting ? '가입 중' : '회원가입'}
          </button>
        </form>
        <p>이미 계정이 있으신가요? <Link to="/login">로그인</Link></p>
      </section>
    </main>
  )
}
