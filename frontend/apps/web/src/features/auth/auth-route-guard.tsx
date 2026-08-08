import { Navigate, Outlet, useLocation } from 'react-router'
import { useAuth } from './use-auth'

interface AuthRouteGuardProps {
  requiredRole: string
}

export function AuthRouteGuard({ requiredRole }: AuthRouteGuardProps) {
  const { account } = useAuth()
  const location = useLocation()

  if (!account) {
    const from = `${location.pathname}${location.search}${location.hash}`
    return <Navigate to="/login" replace state={{ from }} />
  }

  if (account.role !== requiredRole) {
    return <Navigate to="/forbidden" replace />
  }

  return <Outlet />
}
