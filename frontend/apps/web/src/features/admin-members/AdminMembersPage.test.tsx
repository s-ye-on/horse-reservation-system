import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { ResponseError, type AdminMemberPageResponse, type AdminMemberResponse } from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import type { AdminMembersApi } from './admin-members.api'
import { AdminMembersPage } from './admin-members-page'

const MEMBER: AdminMemberResponse = {
  id: 7,
  name: '김승마',
  phone: '010-1234-5678',
  generalRideCount: 21,
  dressageRideCount: 3,
  jumpingRideCount: 1,
  dressageApproved: false,
  jumpingApproved: true,
  canUseLargeArena: true,
}

afterEach(cleanup)

function memberPage(
  content: AdminMemberResponse[],
  page = 0,
  totalPages = content.length === 0 ? 0 : 1,
  totalElements = content.length,
): AdminMemberPageResponse {
  return { content, page, size: 20, totalElements, totalPages, hasNext: page + 1 < totalPages }
}

function createApi(overrides: Partial<AdminMembersApi> = {}): AdminMembersApi {
  return {
    getMembers: vi.fn().mockResolvedValue(memberPage([MEMBER])),
    getMember: vi.fn().mockResolvedValue(MEMBER),
    changeRidingPermissions: vi.fn().mockResolvedValue(MEMBER),
    ...overrides,
  }
}

function renderPage(api: AdminMembersApi) {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  })
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  return render(<AdminMembersPage api={api} />, { wrapper: Wrapper })
}

describe('AdminMembersPage', () => {
  it('회원_목록과_상세_기승_정보를_표시한다', async () => {
    renderPage(createApi())

    expect(screen.getByText('회원 목록을 불러오는 중입니다.')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: '김승마' })).toBeInTheDocument()
    expect(screen.getByText('일반 기승').nextElementSibling).toHaveTextContent('21회')
    expect(screen.getByText('마장마술', { selector: 'dt' }).nextElementSibling).toHaveTextContent('3회')
    expect(screen.getByText('장애물', { selector: 'dt' }).nextElementSibling).toHaveTextContent('1회')
    expect(screen.getByText('대마장 이용 가능')).toBeInTheDocument()
  })

  it('승인_변경_응답을_즉시_화면에_반영한다', async () => {
    const updatedMember = { ...MEMBER, dressageApproved: true }
    const changeRidingPermissions = vi.fn().mockResolvedValue(updatedMember)
    renderPage(createApi({ changeRidingPermissions }))

    const dressageSwitch = await screen.findByRole('switch', { name: '마장마술 승인' })
    fireEvent.change(screen.getByRole('textbox', { name: '승인 변경 사유' }), {
      target: { value: '실력 확인 완료' },
    })
    fireEvent.click(dressageSwitch)

    await waitFor(() => expect(changeRidingPermissions).toHaveBeenCalledWith(7, {
      dressageApproved: true,
      jumpingApproved: true,
      reason: '실력 확인 완료',
    }))
    await waitFor(() => expect(dressageSwitch).toHaveAttribute('aria-checked', 'true'))
  })

  it('승인_변경_중에는_모든_승인_버튼의_중복_제출을_막는다', async () => {
    let resolveUpdate: ((member: AdminMemberResponse) => void) | undefined
    const pendingUpdate = new Promise<AdminMemberResponse>((resolve) => {
      resolveUpdate = resolve
    })
    const changeRidingPermissions = vi.fn().mockReturnValue(pendingUpdate)
    renderPage(createApi({ changeRidingPermissions }))

    const dressageSwitch = await screen.findByRole('switch', { name: '마장마술 승인' })
    fireEvent.change(screen.getByRole('textbox', { name: '승인 변경 사유' }), {
      target: { value: '실력 확인 완료' },
    })
    fireEvent.click(dressageSwitch)

    await waitFor(() => {
      expect(dressageSwitch).toBeDisabled()
      expect(screen.getByRole('switch', { name: '장애물 승인' })).toBeDisabled()
    })
    fireEvent.click(dressageSwitch)
    expect(changeRidingPermissions).toHaveBeenCalledTimes(1)
    resolveUpdate?.({ ...MEMBER, dressageApproved: true })
  })

  it('빈_목록과_권한_거부를_구분해_표시한다', async () => {
    const emptyView = renderPage(createApi({ getMembers: vi.fn().mockResolvedValue(memberPage([])) }))
    expect(await screen.findByText('등록된 회원이 없습니다.')).toBeInTheDocument()
    emptyView.unmount()

    const forbidden = new ResponseError(new Response(null, { status: 403 }), 'forbidden')
    renderPage(createApi({ getMembers: vi.fn().mockRejectedValue(forbidden) }))
    expect(await screen.findByRole('alert')).toHaveTextContent('관리자 권한이 없어')
  })

  it('회원_Page를_이동하고_전체_건수와_버튼_경계를_표시한다', async () => {
    const secondMember = { ...MEMBER, id: 2, name: '이기승', phone: '010-2222-2222' }
    const getMembers = vi.fn((page: number) => Promise.resolve(
      page === 0
        ? memberPage([MEMBER], 0, 2, 2)
        : memberPage([secondMember], 1, 2, 2),
    ))
    const getMember = vi.fn((memberId: number) => Promise.resolve(
      memberId === MEMBER.id ? MEMBER : secondMember,
    ))
    renderPage(createApi({ getMembers, getMember }))

    expect(await screen.findByText('전체 2명')).toBeInTheDocument()
    const navigation = screen.getByRole('navigation', { name: '회원 목록 페이지' })
    expect(screen.getByText('1 / 2 페이지')).toBeInTheDocument()
    expect(navigation.querySelector('button:first-of-type')).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: '다음' }))
    expect(await screen.findByRole('button', { name: /이기승/ })).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: '이기승' })).toBeInTheDocument()
    expect(screen.queryByRole('heading', { name: '김승마' })).not.toBeInTheDocument()
    expect(screen.getByText('2 / 2 페이지')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled()
    fireEvent.click(screen.getByRole('button', { name: '이전' }))
    expect(await screen.findByRole('button', { name: /김승마/ })).toBeInTheDocument()
  })

  it('320px_화면에서도_회원_선택과_승인_제어가_노출된다', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 320 })
    renderPage(createApi())

    expect(await screen.findByRole('button', { name: /김승마/ })).toBeInTheDocument()
    expect(await screen.findByRole('switch', { name: '마장마술 승인' })).toBeInTheDocument()
    expect(screen.getByRole('switch', { name: '장애물 승인' })).toBeInTheDocument()
  })
})
