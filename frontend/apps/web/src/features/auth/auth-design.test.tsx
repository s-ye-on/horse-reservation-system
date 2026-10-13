import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { ResponseError } from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { authApi } from './auth-api'
import { AuthLoginPage } from './auth-login-page'
import { AuthSignupPage } from './auth-signup-page'
import { AuthAccessDeniedPage } from './auth-access-denied-page'
import { useAuth } from './use-auth'

vi.mock('./use-auth', () => ({ useAuth: vi.fn() }))

function signupForm() {
  render(<MemoryRouter><AuthSignupPage /></MemoryRouter>)
  fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'member@horse.test' } })
  fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'password-1234' } })
  fireEvent.change(screen.getByLabelText('이름'), { target: { value: '회원' } })
  fireEvent.change(screen.getByLabelText('전화번호'), { target: { value: '010-1234-5678' } })
}

afterEach(() => { cleanup(); vi.restoreAllMocks() })

describe('Auth 디자인과 오류 포커스', () => {
  it('UTF-8 초과는 요청하지 않고 비밀번호 필드에 연결한다', async () => {
    const signup = vi.spyOn(authApi, 'signup')
    signupForm()
    const password = screen.getByLabelText('비밀번호')
    fireEvent.change(password, { target: { value: '가'.repeat(25) } })
    fireEvent.click(screen.getByRole('button', { name: '회원가입' }))
    expect(signup).not.toHaveBeenCalled()
    expect(password).toHaveAttribute('aria-invalid', 'true')
    expect(password).toHaveAttribute('aria-describedby', 'signup-password-help signup-password-error')
    expect(password).toHaveFocus()
    expect(password).toHaveValue('가'.repeat(25))
    fireEvent.change(password, { target: { value: '가'.repeat(24) } })
    expect(password).not.toHaveAttribute('aria-invalid')
  })

  it('서버 비밀번호 오류도 필드 안내로 변환한다', async () => {
    vi.spyOn(authApi, 'signup').mockRejectedValue(new ResponseError(new Response(JSON.stringify({
      code: 'AUTH_INVALID_PASSWORD', message: '비밀번호 형식이 올바르지 않습니다.', fieldErrors: [],
    }), { status: 400 })))
    signupForm()
    fireEvent.click(screen.getByRole('button', { name: '회원가입' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('72바이트')
    expect(screen.getByLabelText('비밀번호')).toHaveFocus()
    expect(screen.getByLabelText('비밀번호')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.queryByText('AUTH_INVALID_PASSWORD')).not.toBeInTheDocument()
  })

  it('입력을 고치는 중 다른 오류 필드로 포커스를 빼앗지 않는다', () => {
    render(<MemoryRouter><AuthSignupPage /></MemoryRouter>)
    fireEvent.click(screen.getByRole('button', { name: '회원가입' }))
    const email = screen.getByLabelText('이메일')
    expect(email).toHaveFocus()
    fireEvent.change(email, { target: { value: 'member@horse.test' } })
    expect(email).toHaveFocus()
    expect(screen.getByLabelText('비밀번호')).toHaveAttribute('aria-invalid', 'true')
  })

  it('고친 서버 오류는 요약에 남기지 않는다', async () => {
    vi.spyOn(authApi, 'signup').mockRejectedValue(new ResponseError(new Response(JSON.stringify({
      code: 'INVALID_REQUEST', message: '요청 값이 올바르지 않습니다.', fieldErrors: [
        { field: 'email', message: '이메일 오류' }, { field: 'phone', message: '전화번호 오류' },
      ],
    }), { status: 400 })))
    signupForm()
    fireEvent.click(screen.getByRole('button', { name: '회원가입' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('이메일 오류')
    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'other@horse.test' } })
    expect(screen.getByRole('alert')).not.toHaveTextContent('이메일 오류')
    expect(screen.getByLabelText('전화번호')).toHaveAttribute('aria-invalid', 'true')
  })

  it('가입 성공 후 로그인 완료 안내로 포커스를 이동한다', async () => {
    vi.spyOn(authApi, 'signup').mockResolvedValue({ subject: 'member', email: 'member@horse.test', memberId: 1, role: 'MEMBER', status: 'ACTIVE' })
    vi.mocked(useAuth).mockReturnValue({ login: vi.fn() } as unknown as ReturnType<typeof useAuth>)
    render(<MemoryRouter initialEntries={['/signup']}><Routes>
      <Route path="/signup" element={<AuthSignupPage />} />
      <Route path="/login" element={<AuthLoginPage />} />
    </Routes></MemoryRouter>)
    for (const [label, value] of [['이메일', 'member@horse.test'], ['비밀번호', 'password-1234'], ['이름', '회원'], ['전화번호', '010-1234-5678']]) {
      fireEvent.change(screen.getByLabelText(label), { target: { value } })
    }
    fireEvent.click(screen.getByRole('button', { name: '회원가입' }))
    expect(await screen.findByText('회원가입이 완료되었습니다. 로그인해 주세요.')).toHaveFocus()
  })

  it('로그인 중 중복 제출을 막고 실패 안내로 포커스를 이동한다', async () => {
    let reject!: (error: Error) => void
    const login = vi.fn().mockImplementation(() => new Promise((_resolve, rejectPromise) => { reject = rejectPromise }))
    vi.mocked(useAuth).mockReturnValue({ login } as unknown as ReturnType<typeof useAuth>)
    render(<MemoryRouter><AuthLoginPage /></MemoryRouter>)
    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'member@horse.test' } })
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'password-1234' } })
    const form = screen.getByRole('button', { name: '로그인' }).closest('form')!
    fireEvent.submit(form); fireEvent.submit(form)
    expect(login).toHaveBeenCalledOnce()
    expect(screen.getByLabelText('비밀번호')).toBeDisabled()
    expect(screen.getByRole('status')).toHaveTextContent('로그인하고 있습니다.')
    reject(new Error('network failure'))
    expect(await screen.findByRole('alert')).toHaveFocus()
    await waitFor(() => expect(screen.getByRole('button', { name: '로그인' })).toBeEnabled())
  })

  it('접근 안내는 사용자 문구와 홈 이동을 제공한다', () => {
    render(<MemoryRouter><AuthAccessDeniedPage /></MemoryRouter>)
    expect(screen.getByRole('heading', { name: '이 화면을 이용할 수 없습니다' })).toHaveFocus()
    expect(screen.getByRole('link', { name: '홈으로 이동' })).toHaveAttribute('href', '/')
    expect(screen.queryByText(/403|FORBIDDEN/)).not.toBeInTheDocument()
  })
})
