import { Link } from 'react-router'
import horseLogoUrl from '../assets/horse-logo.png'

export function AppLogo() {
  return (
    <Link className="app-logo" to="/" aria-label="Unicorn Stable 홈">
      <span className="app-logo-mark" aria-hidden="true">
        <img src={horseLogoUrl} alt="" />
      </span>
      <span className="app-logo-wordmark">
        <strong>Unicorn Stable</strong>
        <small>유니콘승마클럽</small>
      </span>
    </Link>
  )
}
