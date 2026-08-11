import { StrictMode, useState } from 'react'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router'
import { ResponseError, type AuthAccountResponse } from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { clearWebAccessToken, getWebAccessToken } from '../../api/web-access-token-memory'
import { webQueryClient } from '../../api/web-query-client'
import { refreshWebAuthentication } from '../../api/web-auth-session'
import { authApi, type AuthApi } from './auth-api'
import { AuthLoginPage } from './auth-login-page'
import { AuthProvider } from './auth-provider'
import { AuthRouteGuard } from './auth-route-guard'
import { AuthSignupPage } from './auth-signup-page'
import { useAuth } from './use-auth'

const MEMBER_ACCOUNT: AuthAccountResponse = {
  subject: 'member-subject', memberId: 1, email: 'member@horse.test', role: 'MEMBER', status: 'ACTIVE',
}
const ADMIN_ACCOUNT: AuthAccountResponse = {
  subject: 'admin-subject', memberId: null, email: 'admin@horse.test', role: 'ADMIN', status: 'ACTIVE',
}

function responseError(status: number): ResponseError {
  return new ResponseError(new Response(null, { status }))
}

function createApi(account: AuthAccountResponse = MEMBER_ACCOUNT): AuthApi {
  return {
    signup: vi.fn().mockResolvedValue(account),
    login: vi.fn().mockResolvedValue({
      accessToken: 'memory-access-token', tokenType: 'Bearer', accessTokenExpiresAt: new Date(),
    }),
    refreshAccessToken: vi.fn().mockRejectedValue(responseError(401)),
    getCurrentAccount: vi.fn().mockResolvedValue(account),
    logout: vi.fn().mockResolvedValue(undefined),
  }
}

function LoginHarness() {
  const { account, login, logout } = useAuth()
  const [error, setError] = useState('')
  return (
    <>
      <p>{account?.email ?? 'anonymous'}</p>
      <button type="button" onClick={() => login({ email: 'member@horse.test', password: 'password-1234' }).catch(() => setError('failed'))}>login</button>
      <button type="button" onClick={() => logout().catch(() => setError('failed'))}>logout</button>
      <p>{error}</p>
    </>
  )
}

function RoleNavigationHarness() {
  const { login } = useAuth()
  const navigate = useNavigate()
  return (
    <button type="button" onClick={async () => {
      await login({ email: 'member@horse.test', password: 'password-1234' })
      navigate('/admin')
    }}>
      authenticate
    </button>
  )
}

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  clearWebAccessToken()
  webQueryClient.clear()
})

describe('웹 인증 흐름', () => {
  it('회원가입_성공_후_로그인_화면으로_이동한다', async () => {
    vi.spyOn(authApi, 'signup').mockResolvedValue(MEMBER_ACCOUNT)
    render(
      <MemoryRouter initialEntries={['/signup']}>
        <Routes>
          <Route path="/signup" element={<AuthSignupPage />} />
          <Route path="/login" element={<p>회원가입이 완료되었습니다. 로그인해 주세요.</p>} />
        </Routes>
      </MemoryRouter>,
    )

    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'member@horse.test' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'password-1234' } })
    fireEvent.change(screen.getByLabelText('이름'), { target: { value: '회원' } })
    fireEvent.change(screen.getByLabelText('전화번호'), { target: { value: '010-1234-5678' } })
    fireEvent.click(screen.getByRole('button', { name: '회원가입' }))

    expect(await screen.findByText('회원가입이 완료되었습니다. 로그인해 주세요.')).toBeInTheDocument()
  })

  it('회원가입_비밀번호는_8자부터_입력할_수_있다', () => {
    render(
      <MemoryRouter>
        <AuthSignupPage />
      </MemoryRouter>,
    )

    expect(screen.getByLabelText('비밀번호')).toHaveAttribute('minlength', '8')
    expect(screen.getByText('8자 이상 입력해 주세요.')).toBeInTheDocument()
  })

  it('회원가입_validation_오류를_공통_ErrorResponse로_표시한다', async () => {
    const response = new Response(JSON.stringify({
      code: 'INVALID_REQUEST', message: '요청 값이 올바르지 않습니다', status: 400,
      timestamp: '2026-08-08T10:00:00Z', path: '/api/auth/signup', details: {},
      fieldErrors: [{ field: 'email', message: '올바른 이메일 형식이어야 합니다' }],
    }), { status: 400, headers: { 'Content-Type': 'application/json' } })
    vi.spyOn(authApi, 'signup').mockRejectedValue(new ResponseError(response))
    render(
      <MemoryRouter>
        <AuthSignupPage />
      </MemoryRouter>,
    )

    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'member@horse.test' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'password-1234' } })
    fireEvent.change(screen.getByLabelText('이름'), { target: { value: '회원' } })
    fireEvent.change(screen.getByLabelText('전화번호'), { target: { value: '010-1234-5678' } })
    fireEvent.click(screen.getByRole('button', { name: '회원가입' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('이메일: 올바른 이메일 형식이어야 합니다')
  })

  it('로그인_후_me_결과와_메모리_Token으로_인증_상태를_구성한다', async () => {
    const api = createApi()
    const storageSpy = vi.spyOn(Storage.prototype, 'setItem')
    render(
      <AuthProvider api={api}>
        <LoginHarness />
      </AuthProvider>,
    )

    fireEvent.click(await screen.findByRole('button', { name: 'login' }))

    expect(await screen.findByText('member@horse.test')).toBeInTheDocument()
    expect(api.login).toHaveBeenCalledOnce()
    expect(api.getCurrentAccount).toHaveBeenCalledOnce()
    expect(getWebAccessToken()).toBe('memory-access-token')
    expect(storageSpy).not.toHaveBeenCalled()

  })

  it('다른_회원으로_로그인하면_이전_회원_조회_캐시를_제거한다', async () => {
    const api = createApi()
    render(
      <AuthProvider api={api}>
        <LoginHarness />
      </AuthProvider>,
    )

    await screen.findByText('anonymous')
    webQueryClient.setQueryData(['member', 'available-classes'], {
      currentGeneralGrade: 'LARGE_ARENA_TROT',
    })
    webQueryClient.setQueryData(['member', 'coupons', 0], { content: [{ couponId: 99 }] })

    fireEvent.click(screen.getByRole('button', { name: 'login' }))

    expect(await screen.findByText('member@horse.test')).toBeInTheDocument()
    expect(webQueryClient.getQueryData(['member', 'available-classes'])).toBeUndefined()
    expect(webQueryClient.getQueryData(['member', 'coupons', 0])).toBeUndefined()
  })

  it('logout_204_후_메모리_인증_상태를_초기화한다', async () => {
    const api = createApi()
    render(
      <AuthProvider api={api}>
        <LoginHarness />
      </AuthProvider>,
    )

    fireEvent.click(await screen.findByRole('button', { name: 'login' }))
    await screen.findByText('member@horse.test')
    fireEvent.click(screen.getByRole('button', { name: 'logout' }))

    await waitFor(() => expect(screen.getByText('anonymous')).toBeInTheDocument())
    expect(api.logout).toHaveBeenCalledOnce()
    expect(getWebAccessToken()).toBeNull()
  })

  it('비로그인_사용자의_보호_route_접근을_로그인으로_이동한다', async () => {
    render(
      <MemoryRouter initialEntries={['/member']}>
        <AuthProvider api={createApi()}>
          <Routes>
            <Route path="/login" element={<p>login page</p>} />
            <Route element={<AuthRouteGuard requiredRole="MEMBER" />}>
              <Route path="/member" element={<p>member page</p>} />
            </Route>
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    )

    expect(await screen.findByText('login page')).toBeInTheDocument()
  })

  it('MEMBER는_ADMIN_route에_접근할_수_없다', async () => {
    render(
      <MemoryRouter>
        <AuthProvider api={createApi(MEMBER_ACCOUNT)}>
          <Routes>
            <Route path="/" element={<RoleNavigationHarness />} />
            <Route path="/forbidden" element={<p>forbidden page</p>} />
            <Route element={<AuthRouteGuard requiredRole="ADMIN" />}>
              <Route path="/admin" element={<p>admin page</p>} />
            </Route>
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    )

    fireEvent.click(await screen.findByRole('button', { name: 'authenticate' }))
    expect(await screen.findByText('forbidden page')).toBeInTheDocument()
  })

  it('ADMIN은_ADMIN_route에_접근할_수_있다', async () => {
    render(
      <MemoryRouter>
        <AuthProvider api={createApi(ADMIN_ACCOUNT)}>
          <Routes>
            <Route path="/" element={<RoleNavigationHarness />} />
            <Route path="/forbidden" element={<p>forbidden page</p>} />
            <Route element={<AuthRouteGuard requiredRole="ADMIN" />}>
              <Route path="/admin" element={<p>admin page</p>} />
            </Route>
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    )

    fireEvent.click(await screen.findByRole('button', { name: 'authenticate' }))
    expect(await screen.findByText('admin page')).toBeInTheDocument()
  })

  it('로그인_화면은_Provider의_login을_호출하고_권한별_기본화면으로_이동한다', async () => {
    const api = createApi(ADMIN_ACCOUNT)
    render(
      <MemoryRouter initialEntries={['/login']}>
        <AuthProvider api={api}>
          <Routes>
            <Route path="/login" element={<AuthLoginPage />} />
            <Route path="/admin" element={<p>admin landing</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    )

    fireEvent.change(await screen.findByLabelText('이메일'), { target: { value: 'admin@horse.test' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'password-1234' } })
    fireEvent.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByText('admin landing')).toBeInTheDocument()
    expect(api.login).toHaveBeenCalledWith({ email: 'admin@horse.test', password: 'password-1234' })
    expect(api.getCurrentAccount).toHaveBeenCalledOnce()
  })

  it('보호_route에서_이동한_로그인은_원래_내부_경로로_복귀한다', async () => {
    const api = createApi(MEMBER_ACCOUNT)
    render(
      <MemoryRouter initialEntries={[{ pathname: '/login', state: { from: '/my/reservations/7/change?step=2' } }]}>
        <AuthProvider api={api}>
          <Routes>
            <Route path="/login" element={<AuthLoginPage />} />
            <Route path="/my/reservations/:reservationId/change" element={<p>original destination</p>} />
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    )

    fireEvent.change(await screen.findByLabelText('이메일'), { target: { value: 'member@horse.test' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'password-1234' } })
    fireEvent.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByText('original destination')).toBeInTheDocument()
  })

  it('잘못된_로그인_정보의_서버_오류를_표시한다', async () => {
    const response = new Response(JSON.stringify({
      code: 'AUTH_INVALID_CREDENTIALS', message: '이메일 또는 비밀번호가 올바르지 않습니다', status: 401,
      timestamp: '2026-08-08T10:00:00Z', path: '/api/auth/web/login', details: {}, fieldErrors: [],
    }), { status: 401, headers: { 'Content-Type': 'application/json' } })
    const api = createApi()
    vi.mocked(api.login).mockRejectedValue(new ResponseError(response))
    render(
      <MemoryRouter>
        <AuthProvider api={api}>
          <AuthLoginPage />
        </AuthProvider>
      </MemoryRouter>,
    )

    fireEvent.change(await screen.findByLabelText('이메일'), { target: { value: 'member@horse.test' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'wrong-password' } })
    fireEvent.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('이메일 또는 비밀번호가 올바르지 않습니다')
    expect(getWebAccessToken()).toBeNull()
  })

  it('시작_복구_중에는_보호_route를_로그인으로_redirect하지_않는다', async () => {
    let resolveRefresh!: (value: Awaited<ReturnType<AuthApi['refreshAccessToken']>>) => void
    const api = createApi()
    vi.mocked(api.refreshAccessToken).mockImplementation(() => new Promise((resolve) => {
      resolveRefresh = resolve
    }))

    render(
      <MemoryRouter initialEntries={['/member']}>
        <AuthProvider api={api}>
          <Routes>
            <Route path="/login" element={<p>login page</p>} />
            <Route element={<AuthRouteGuard requiredRole="MEMBER" />}>
              <Route path="/member" element={<p>member page</p>} />
            </Route>
          </Routes>
        </AuthProvider>
      </MemoryRouter>,
    )

    expect(screen.getByRole('status')).toHaveTextContent('로그인 상태를 확인하고 있습니다.')
    expect(screen.queryByText('login page')).not.toBeInTheDocument()
    resolveRefresh({ accessToken: 'restored-token', tokenType: 'Bearer', accessTokenExpiresAt: new Date() })

    expect(await screen.findByText('member page')).toBeInTheDocument()
    expect(api.getCurrentAccount).toHaveBeenCalledOnce()
    expect(getWebAccessToken()).toBe('restored-token')
  })

  it('StrictMode의_반복_effect에서도_시작_refresh를_한_번만_호출한다', async () => {
    const api = createApi()
    vi.mocked(api.refreshAccessToken).mockResolvedValue({
      accessToken: 'strict-mode-token', tokenType: 'Bearer', accessTokenExpiresAt: new Date(),
    })

    render(
      <StrictMode>
        <AuthProvider api={api}>
          <LoginHarness />
        </AuthProvider>
      </StrictMode>,
    )

    expect(await screen.findByText('member@horse.test')).toBeInTheDocument()
    expect(api.refreshAccessToken).toHaveBeenCalledOnce()
    expect(api.getCurrentAccount).toHaveBeenCalledOnce()
  })

  it('시작_refresh의_일시적_실패는_재시도할_수_있다', async () => {
    const api = createApi()
    vi.mocked(api.refreshAccessToken)
      .mockRejectedValueOnce(new Error('network failure'))
      .mockResolvedValueOnce({
        accessToken: 'retry-token', tokenType: 'Bearer', accessTokenExpiresAt: new Date(),
      })

    render(
      <AuthProvider api={api}>
        <LoginHarness />
      </AuthProvider>,
    )

    expect(await screen.findByRole('alert')).toHaveTextContent('로그인 상태를 확인하지 못했습니다.')
    expect(screen.getByRole('button', { name: 'login' })).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))

    expect(await screen.findByText('member@horse.test')).toBeInTheDocument()
    expect(api.refreshAccessToken).toHaveBeenCalledTimes(2)
  })

  it('logout_후_늦게_도착한_refresh가_인증_상태를_복구하지_못한다', async () => {
    const api = createApi()
    let resolveRefresh!: (response: Response) => void
    const fetchMock = vi.fn().mockImplementation(() => new Promise<Response>((resolve) => {
      resolveRefresh = resolve
    }))
    vi.stubGlobal('fetch', fetchMock)
    document.cookie = 'XSRF-TOKEN=csrf-token; Path=/'
    render(
      <AuthProvider api={api}>
        <LoginHarness />
      </AuthProvider>,
    )

    fireEvent.click(await screen.findByRole('button', { name: 'login' }))
    await screen.findByText('member@horse.test')
    const pendingRefresh = refreshWebAuthentication()
    await vi.waitFor(() => expect(fetchMock).toHaveBeenCalledOnce())
    fireEvent.click(screen.getByRole('button', { name: 'logout' }))
    await screen.findByText('anonymous')
    resolveRefresh(new Response(JSON.stringify({
      accessToken: 'late-access-token', tokenType: 'Bearer', accessTokenExpiresAt: '2026-08-09T12:15:00Z',
    }), { status: 200, headers: { 'Content-Type': 'application/json' } }))

    await expect(pendingRefresh).rejects.toThrow('superseded')
    expect(screen.getByText('anonymous')).toBeInTheDocument()
    expect(getWebAccessToken()).toBeNull()
  })

  it('인증된_상태에서_refresh_401이_발생하면_계정과_Token을_제거한다', async () => {
    const api = createApi()
    const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 401 }))
    vi.stubGlobal('fetch', fetchMock)
    document.cookie = 'XSRF-TOKEN=csrf-token; Path=/'
    render(
      <AuthProvider api={api}>
        <LoginHarness />
      </AuthProvider>,
    )

    fireEvent.click(await screen.findByRole('button', { name: 'login' }))
    await screen.findByText('member@horse.test')
    await expect(refreshWebAuthentication()).rejects.toBeInstanceOf(ResponseError)

    expect(await screen.findByText('anonymous')).toBeInTheDocument()
    expect(getWebAccessToken()).toBeNull()
  })
})
