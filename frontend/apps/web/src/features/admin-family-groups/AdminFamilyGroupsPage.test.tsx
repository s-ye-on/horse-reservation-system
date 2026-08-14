import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  type AdminFamilyGroupsApi,
  type FamilyGroupAuditLog,
  type FamilyGroupFilters,
  type FamilyGroupMember,
  type FamilyGroupSummary,
  type FamilyMemberCandidate,
  type PageResponse,
} from './admin-family-groups.api'
import { AdminFamilyGroupsPage } from './admin-family-groups-page'

const GROUP: FamilyGroupSummary = {
  groupId: 11,
  name: '김 가족',
  status: 'ACTIVE',
  activeMemberCount: 1,
  createdAt: '2026-08-15T10:00:00+09:00',
  dissolvedAt: null,
}
const MEMBER: FamilyGroupMember = {
  membershipId: 21,
  memberId: 31,
  name: '김승마',
  phone: '010-1234-5678',
  joinedAt: '2026-08-15T10:00:00+09:00',
}
const CANDIDATE: FamilyMemberCandidate = {
  memberId: 32,
  name: '이후보',
  phone: '010-2222-2222',
}
const AUDIT: FamilyGroupAuditLog = {
  auditId: 41,
  action: 'MEMBER_ADDED',
  memberId: MEMBER.memberId,
  memberName: MEMBER.name,
  fromState: { status: 'NONE' },
  toState: { status: 'ACTIVE' },
  actorAuthSubject: 'admin-family',
  reason: '가족 확인 완료',
  occurredAt: '2026-08-15T10:00:00+09:00',
}

afterEach(cleanup)

function page<T>(content: T[], currentPage = 0, totalPages = content.length === 0 ? 0 : 1, totalElements = content.length): PageResponse<T> {
  return { content, page: currentPage, size: 10, totalElements, totalPages, hasNext: currentPage + 1 < totalPages }
}

function createApi(overrides: Partial<AdminFamilyGroupsApi> = {}): AdminFamilyGroupsApi {
  return {
    getGroups: vi.fn().mockResolvedValue(page([GROUP])),
    getMembers: vi.fn().mockResolvedValue(page([MEMBER])),
    getMemberCandidates: vi.fn().mockResolvedValue(page([CANDIDATE])),
    getAuditLogs: vi.fn().mockResolvedValue(page([AUDIT])),
    createGroup: vi.fn().mockResolvedValue(undefined),
    addMember: vi.fn().mockResolvedValue(undefined),
    removeMember: vi.fn().mockResolvedValue(undefined),
    dissolveGroup: vi.fn().mockResolvedValue(undefined),
    ...overrides,
  }
}

function renderPage(api: AdminFamilyGroupsApi) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  })
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={client}>{children}</QueryClientProvider>
  )
  return render(<AdminFamilyGroupsPage api={api} />, { wrapper: Wrapper })
}

describe('AdminFamilyGroupsPage', () => {
  it('그룹과_현재_구성원과_감사_이력을_표시한다', async () => {
    renderPage(createApi())

    expect(screen.getByText('가족 그룹을 불러오는 중입니다.')).toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: '김 가족' })).toBeInTheDocument()
    expect(screen.getByText('김승마')).toBeInTheDocument()
    expect(screen.getAllByText('구성원 추가')).toHaveLength(2)
    expect(screen.getByText(/가족 확인 완료/)).toBeInTheDocument()
    expect(screen.getByText('처리자 admin-family')).toBeInTheDocument()
  })

  it('검색과_상태_변경은_그룹_Page를_0으로_초기화한다', async () => {
    const nextGroup = { ...GROUP, groupId: 12, name: '박 가족' }
    const getGroups = vi.fn((_filters: FamilyGroupFilters, currentPage: number) => Promise.resolve(
      currentPage === 0 ? page([GROUP], 0, 2, 2) : page([nextGroup], 1, 2, 2),
    ))
    renderPage(createApi({ getGroups }))

    const groupPagination = await screen.findByRole('navigation', { name: '가족 그룹 목록 페이지' })
    fireEvent.click(within(groupPagination).getByRole('button', { name: '다음' }))
    await waitFor(() => expect(getGroups).toHaveBeenCalledWith({ query: '', status: 'ACTIVE' }, 1, 10))

    const searchButton = screen.getByRole('button', { name: '검색' })
    const searchForm = searchButton.closest('form') as HTMLFormElement
    fireEvent.change(within(searchForm).getByLabelText('그룹 이름'), { target: { value: '김' } })
    fireEvent.change(within(searchForm).getByLabelText('상태'), { target: { value: '' } })
    fireEvent.click(searchButton)
    await waitFor(() => expect(getGroups).toHaveBeenCalledWith({ query: '김', status: '' }, 0, 10))
  })

  it('필수_사유와_함께_그룹_생성_구성원_추가와_제거를_요청한다', async () => {
    const api = createApi()
    renderPage(api)
    await screen.findByRole('heading', { name: '김 가족' })

    const createDetails = screen.getByText('새 가족 그룹').closest('details') as HTMLDetailsElement
    fireEvent.click(within(createDetails).getByText('새 가족 그룹'))
    fireEvent.change(within(createDetails).getByLabelText('그룹 이름'), { target: { value: '새 가족' } })
    fireEvent.change(within(createDetails).getByLabelText('생성 사유'), { target: { value: '신규 등록' } })
    fireEvent.click(within(createDetails).getByRole('button', { name: '그룹 생성' }))
    await waitFor(() => expect(api.createGroup).toHaveBeenCalledWith('새 가족', '신규 등록'))
    await screen.findByRole('heading', { name: '김 가족' })

    fireEvent.change(screen.getByLabelText('추가 사유'), { target: { value: '가족 확인' } })
    fireEvent.click(screen.getByRole('button', { name: '추가' }))
    await waitFor(() => expect(api.addMember).toHaveBeenCalledWith(GROUP.groupId, CANDIDATE.memberId, '가족 확인'))

    fireEvent.click(screen.getByRole('button', { name: '제거' }))
    fireEvent.change(screen.getByLabelText('제거 사유'), { target: { value: '가족 관계 종료' } })
    fireEvent.click(screen.getByRole('button', { name: '제거 확인' }))
    await waitFor(() => expect(api.removeMember).toHaveBeenCalledWith(GROUP.groupId, MEMBER.memberId, '가족 관계 종료'))
  })

  it('구성원_변경_성공_후_그룹과_구성원_후보와_감사를_다시_조회한다', async () => {
    const api = createApi()
    renderPage(api)
    await screen.findByRole('heading', { name: '김 가족' })
    await waitFor(() => {
      expect(api.getGroups).toHaveBeenCalledTimes(1)
      expect(api.getMembers).toHaveBeenCalledTimes(1)
      expect(api.getMemberCandidates).toHaveBeenCalledTimes(1)
      expect(api.getAuditLogs).toHaveBeenCalledTimes(1)
    })

    fireEvent.change(screen.getByLabelText('추가 사유'), { target: { value: '가족 확인' } })
    fireEvent.click(screen.getByRole('button', { name: '추가' }))

    await waitFor(() => {
      expect(api.getGroups).toHaveBeenCalledTimes(2)
      expect(api.getMembers).toHaveBeenCalledTimes(2)
      expect(api.getMemberCandidates).toHaveBeenCalledTimes(2)
      expect(api.getAuditLogs).toHaveBeenCalledTimes(2)
    })
  })

  it('명시적인_사유로만_그룹을_해제하고_중복_요청을_막는다', async () => {
    let resolveDissolution: (() => void) | undefined
    const dissolveGroup = vi.fn().mockReturnValue(new Promise<void>((resolve) => { resolveDissolution = resolve }))
    renderPage(createApi({ dissolveGroup }))
    await screen.findByRole('heading', { name: '김 가족' })

    const dissolveButton = screen.getByRole('button', { name: '가족 그룹 해제' })
    expect(dissolveButton).toBeDisabled()
    fireEvent.change(screen.getByLabelText('해제 사유'), { target: { value: '그룹 운영 종료' } })
    fireEvent.click(dissolveButton)
    await waitFor(() => expect(dissolveButton).toBeDisabled())
    fireEvent.click(dissolveButton)
    expect(dissolveGroup).toHaveBeenCalledTimes(1)
    resolveDissolution?.()
    await waitFor(() => expect(dissolveButton).not.toHaveTextContent('해제 중'))
  })

  it('빈_검색_결과와_조회_실패의_재시도를_구분한다', async () => {
    const emptyView = renderPage(createApi({ getGroups: vi.fn().mockResolvedValue(page([])) }))
    expect(await screen.findByText('조건에 맞는 가족 그룹이 없습니다.')).toBeInTheDocument()
    emptyView.unmount()

    const getGroups = vi.fn()
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce(page([GROUP]))
    renderPage(createApi({ getGroups }))
    expect(await screen.findByRole('alert')).toHaveTextContent('가족 그룹을 불러오지 못했습니다.')
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByRole('heading', { name: '김 가족' })).toBeInTheDocument()
  })

  it('320px에서도_그룹_선택과_구성원_변경_감사_조작을_제공한다', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 320 })
    renderPage(createApi())

    expect(await screen.findByRole('button', { name: /김 가족/ })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '현재 구성원' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '구성원 추가' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '변경 감사' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '가족 그룹 해제' })).toBeInTheDocument()
  })
})
