import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from './use-auth'

interface AuthRouteGuardProps {
  requiredRole: string
}

export function AuthRouteGuard({ requiredRole }: AuthRouteGuardProps) {
  const { account, status } = useAuth()
  const location = useLocation()

  if (status === 'initializing') {
    return <p role="status">로그인 상태를 확인하고 있습니다.</p>
  }

  if (!account) {
    const from = `${location.pathname}${location.search}${location.hash}`
    return <Navigate to="/login" replace state={{ from }} />
  }

  if (account.role !== requiredRole) {
    return <Navigate to="/forbidden" replace />
  }

  return <Outlet />
}
