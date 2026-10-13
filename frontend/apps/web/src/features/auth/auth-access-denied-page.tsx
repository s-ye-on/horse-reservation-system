import { useEffect, useRef } from 'react'
import { Link } from 'react-router'
import './auth-pages.css'

export function AuthAccessDeniedPage() {
  const titleRef = useRef<HTMLHeadingElement>(null)
  useEffect(() => { titleRef.current?.focus() }, [])

  return (
    <main className="auth-page-shell">
      <section className="auth-form-panel" aria-labelledby="access-denied-title">
        <header className="auth-page-heading">
          <h1 id="access-denied-title" ref={titleRef} tabIndex={-1}>이 화면을 이용할 수 없습니다</h1>
          <p>현재 계정에서 이용할 수 없는 화면입니다. 홈으로 돌아가 사용 가능한 메뉴를 확인해 주세요.</p>
        </header>
        <Link className="auth-primary-link" to="/">홈으로 이동</Link>
      </section>
    </main>
  )
}
