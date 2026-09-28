import { Outlet } from 'react-router'
import { AuthNavigation } from '../features/auth/auth-navigation'

export function MemberLayout() {
  return (
    <div className="member-app-shell">
      <AuthNavigation />
      <div className="member-layout-content" id="main-content" tabIndex={-1}>
        <Outlet />
      </div>
    </div>
  )
}
