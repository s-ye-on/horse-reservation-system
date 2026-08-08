import { Link } from 'react-router'

export function AuthAccessDeniedPage() {
  return (
    <main className="auth-page-shell">
      <section className="auth-form-panel" aria-labelledby="access-denied-title">
        <h1 id="access-denied-title">접근 권한이 없습니다</h1>
        <p>현재 계정으로는 이 화면을 사용할 수 없습니다.</p>
        <Link to="/">홈으로 이동</Link>
      </section>
    </main>
  )
}
