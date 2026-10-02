import { ResponseError } from '@horse/api-client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import type { ReactNode } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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
  createdAt: new Date('2026-08-15T10:00:00+09:00'),
  dissolvedAt: null,
}
const MEMBER: FamilyGroupMember = {
  membershipId: 21,
  memberId: 31,
  name: '김승마',
  phone: '010-1234-5678',
  joinedAt: new Date('2026-08-15T10:00:00+09:00'),
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
  fromState: {
    groupId: GROUP.groupId,
    name: null,
    status: 'NONE',
    activeMemberIds: null,
    dissolvedAt: null,
    membershipId: null,
    memberId: MEMBER.memberId,
    joinedAt: null,
    endedAt: null,
  },
  toState: {
    groupId: GROUP.groupId,
    name: null,
    status: 'ACTIVE',
    activeMemberIds: null,
    dissolvedAt: null,
    membershipId: MEMBER.membershipId,
    memberId: MEMBER.memberId,
    joinedAt: MEMBER.joinedAt.toISOString(),
    endedAt: null,
  },
  actorAuthSubject: 'admin-family',
  reason: '가족 확인 완료',
  occurredAt: new Date('2026-08-15T10:00:00+09:00'),
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

beforeEach(() => {
  Object.defineProperty(HTMLDialogElement.prototype, 'showModal', { configurable: true, value: function () { this.setAttribute('open', '') } })
  Object.defineProperty(HTMLDialogElement.prototype, 'close', { configurable: true, value: function () { this.removeAttribute('open') } })
})

async function loaded(api = createApi()) {
  const view = renderPage(api)
  await screen.findByRole('heading', { name: '김 가족' })
  await waitFor(() => expect(screen.getByRole('button', { name: /이후보/ })).toBeEnabled())
  return view
}
function chooseCandidate() { fireEvent.click(screen.getByRole('button', { name: /이후보/ })) }
function reviewAdd() {
  chooseCandidate()
  fireEvent.change(screen.getByLabelText('추가 사유'), { target: { value: '가족 확인' } })
  fireEvent.click(screen.getByRole('button', { name: '구성원 추가 확인' }))
}
function confirm(label: string) { fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: label })) }

describe('AdminFamilyGroupsPage', () => {
  it('서버_집계와_자연스러운_감사를_표시하고_내부정보를_숨긴다', async () => {
    const api = createApi({ getGroups: vi.fn().mockResolvedValue(page([{ ...GROUP, activeMemberCount: 32 }])) })
    renderPage(api)
    expect(screen.getByText('가족 그룹을 불러오는 중입니다.')).toBeInTheDocument()
    await screen.findByRole('heading', { name: '김 가족' })
    expect(screen.getByText('32명')).toBeInTheDocument()
    expect(screen.getByText(/미소속 → 관계 유지 중/)).toBeInTheDocument()
    expect(screen.queryByText(/admin-family/)).not.toBeInTheDocument()
    expect(screen.queryByText(/membershipId/)).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: /김 가족/ })).toHaveAttribute('aria-current', 'true')
    expect(screen.getByRole('button', { name: /김 가족/ })).toHaveAttribute('aria-controls', 'family-group-detail')
  })

  it('검색과_상태_변경은_그룹_Page를_0으로_초기화한다', async () => {
    const nextGroup = { ...GROUP, groupId: 12, name: '박 가족' }
    const getGroups = vi.fn((_filters: FamilyGroupFilters, currentPage: number) => Promise.resolve(currentPage === 0 ? page([GROUP], 0, 2, 2) : page([nextGroup], 1, 2, 2)))
    await loaded(createApi({ getGroups }))
    fireEvent.click(within(screen.getByRole('navigation', { name: '가족 그룹 목록 페이지' })).getByRole('button', { name: '다음' }))
    await screen.findByRole('heading', { name: '박 가족' })
    const form = screen.getByRole('button', { name: '검색' }).closest('form')!
    fireEvent.change(within(form).getByLabelText('그룹 이름 검색'), { target: { value: '김' } })
    fireEvent.change(within(form).getByLabelText('상태'), { target: { value: '' } })
    fireEvent.click(within(form).getByRole('button', { name: '검색' }))
    await waitFor(() => expect(getGroups).toHaveBeenCalledWith({ query: '김', status: '' }, 0, 10))
  })

  it('네_영역의_실제_Page_크기를_보존한다', async () => {
    const api = createApi({
      getMembers: vi.fn().mockResolvedValue(page([MEMBER], 0, 2, 9)),
      getMemberCandidates: vi.fn().mockResolvedValue(page([CANDIDATE], 0, 2, 7)),
      getAuditLogs: vi.fn().mockResolvedValue(page([AUDIT], 0, 2, 9)),
    })
    await loaded(api)
    expect(api.getMembers).toHaveBeenCalledWith(11, 0, 8)
    expect(api.getMemberCandidates).toHaveBeenCalledWith('', 0, 6)
    expect(api.getAuditLogs).toHaveBeenCalledWith(11, 0, 8)
    for (const label of ['가족 구성원 페이지', '추가 가능 회원 페이지', '가족 변경 감사 페이지']) fireEvent.click(within(screen.getByRole('navigation', { name: label })).getByRole('button', { name: '다음' }))
    await waitFor(() => {
      expect(api.getMembers).toHaveBeenCalledWith(11, 1, 8)
      expect(api.getMemberCandidates).toHaveBeenCalledWith('', 1, 6)
      expect(api.getAuditLogs).toHaveBeenCalledWith(11, 1, 8)
    })
  })

  it('그룹_생성은_확인창에서_필수_입력을_검증하고_자동재시도하지_않는다', async () => {
    const api = createApi({ createGroup: vi.fn().mockRejectedValue(new Error('network')) })
    await loaded(api)
    fireEvent.click(screen.getByRole('button', { name: '새 그룹 만들기' }))
    confirm('그룹 생성')
    expect(api.createGroup).not.toHaveBeenCalled()
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByLabelText('그룹 이름')).toHaveAttribute('aria-invalid', 'true')
    expect(within(dialog).getByLabelText('생성 사유')).toHaveAttribute('aria-invalid', 'true')
    fireEvent.change(within(dialog).getByLabelText('그룹 이름'), { target: { value: '새 가족' } })
    fireEvent.change(within(dialog).getByLabelText('생성 사유'), { target: { value: '신규 등록' } })
    confirm('그룹 생성')
    await screen.findByRole('alert')
    expect(api.createGroup).toHaveBeenCalledTimes(1)
    expect(api.createGroup).toHaveBeenCalledWith('새 가족', '신규 등록')
  })

  it('빈_운영중_그룹_생성_후_서버응답을_사용한다', async () => {
    let created = false
    const api = createApi({
      createGroup: vi.fn(async () => { created = true }),
      getGroups: vi.fn(async () => page([created ? { ...GROUP, groupId: 12, name: '새 가족', activeMemberCount: 0 } : GROUP])),
      getMembers: vi.fn(async () => page(created ? [] : [MEMBER])),
    })
    await loaded(api)
    fireEvent.click(screen.getByRole('button', { name: '새 그룹 만들기' }))
    const dialog = screen.getByRole('dialog')
    fireEvent.change(within(dialog).getByLabelText('그룹 이름'), { target: { value: '새 가족' } })
    fireEvent.change(within(dialog).getByLabelText('생성 사유'), { target: { value: '새 등록' } })
    confirm('그룹 생성')
    await screen.findByRole('heading', { name: '새 가족' })
    expect(await screen.findByText(/빈 운영 중 그룹도 정상적으로 유지/)).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('구성원 0명')
    expect(screen.getByRole('status')).toHaveFocus()
  })

  it('추가는_후보한명과_사유를_확인한후_실행하고_모든_family_query를_갱신한다', async () => {
    const api = createApi()
    await loaded(api)
    reviewAdd()
    expect(api.addMember).not.toHaveBeenCalled()
    expect(screen.getByRole('dialog')).toHaveTextContent('자동으로 이동시키지 않습니다')
    confirm('구성원 추가')
    await waitFor(() => expect(api.addMember).toHaveBeenCalledWith(11, 32, '가족 확인'))
    await waitFor(() => {
      expect(api.getGroups).toHaveBeenCalledTimes(2)
      expect(api.getMembers).toHaveBeenCalledTimes(2)
      expect(api.getMemberCandidates).toHaveBeenCalledTimes(2)
      expect(api.getAuditLogs).toHaveBeenCalledTimes(2)
    })
  })

  it('추가사유의_공백과_길이를_field와_연결한다', async () => {
    const api = createApi()
    await loaded(api)
    chooseCandidate()
    fireEvent.change(screen.getByLabelText('추가 사유'), { target: { value: '   ' } })
    fireEvent.click(screen.getByRole('button', { name: '구성원 추가 확인' }))
    expect(screen.getByLabelText('추가 사유')).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByLabelText('추가 사유')).toHaveAccessibleDescription(/공백만 입력할 수 없으며 최대 500자/)
    expect(api.addMember).not.toHaveBeenCalled()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it.each([404, 409])('소속_대상_충돌_%s_후_자동이동없이_최신상태를_조회한다', async (status) => {
    const api = createApi({ addMember: vi.fn().mockRejectedValue(new ResponseError(new Response(null, { status }), 'failed')) })
    await loaded(api)
    reviewAdd()
    confirm('구성원 추가')
    await screen.findByRole('alert')
    await waitFor(() => expect(api.getMemberCandidates).toHaveBeenCalledTimes(2))
    expect(api.getMembers).toHaveBeenCalledTimes(2)
    expect(api.getGroups).toHaveBeenCalledTimes(2)
    expect(api.getAuditLogs).toHaveBeenCalledTimes(2)
    expect(api.removeMember).not.toHaveBeenCalled()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('서버_400_필드오류를_확인창_입력에_연결한다', async () => {
    const api = createApi({ createGroup: vi.fn().mockRejectedValue(new ResponseError(new Response(JSON.stringify({ fieldErrors: [{ field: 'name' }] }), { status: 400 }), 'invalid')) })
    await loaded(api)
    fireEvent.click(screen.getByRole('button', { name: '새 그룹 만들기' }))
    const dialog = screen.getByRole('dialog')
    fireEvent.change(within(dialog).getByLabelText('그룹 이름'), { target: { value: '가족' } })
    fireEvent.change(within(dialog).getByLabelText('생성 사유'), { target: { value: '등록' } })
    confirm('그룹 생성')
    await waitFor(() => expect(within(dialog).getByLabelText('그룹 이름')).toHaveAttribute('aria-invalid', 'true'))
    expect(within(dialog).getByRole('alert')).toHaveTextContent('입력 내용')
  })

  it('마지막_구성원_제거와_재추가는_별도_Command이며_그룹은_운영중이다', async () => {
    let removed = false
    let readded = false
    const api = createApi({
      removeMember: vi.fn(async () => { removed = true }),
      addMember: vi.fn(async () => { readded = true }),
      getGroups: vi.fn(async () => page([{ ...GROUP, activeMemberCount: removed && !readded ? 0 : 1 }])),
      getMembers: vi.fn(async () => page(removed && !readded ? [] : [{ ...MEMBER, membershipId: readded ? 99 : 21 }])),
      getMemberCandidates: vi.fn(async () => page(removed ? [{ memberId: MEMBER.memberId, name: MEMBER.name, phone: MEMBER.phone }] : [CANDIDATE])),
    })
    await loaded(api)
    fireEvent.click(screen.getByRole('button', { name: '제거' }))
    const dialog = screen.getByRole('dialog')
    expect(dialog).toHaveTextContent('재추가는 새로운 관계 생성')
    fireEvent.change(within(dialog).getByLabelText('제거 사유'), { target: { value: '관계 종료' } })
    confirm('제거 확인')
    await screen.findByText(/빈 운영 중 그룹도 정상적으로 유지/)
    expect(screen.getByRole('button', { name: '그룹 해제 확인' })).toBeInTheDocument()
    expect(api.dissolveGroup).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole('button', { name: /김승마/ }))
    fireEvent.change(screen.getByLabelText('추가 사유'), { target: { value: '새 관계 등록' } })
    fireEvent.click(screen.getByRole('button', { name: '구성원 추가 확인' }))
    confirm('구성원 추가')
    await waitFor(() => expect(api.addMember).toHaveBeenCalledWith(11, 31, '새 관계 등록'))
    expect(api.removeMember).toHaveBeenCalledWith(11, 31, '관계 종료')
  })

  it('해제는_명시적인_확인후_한번만_요청하고_해제된_서버상태를_표시한다', async () => {
    let dissolved = false
    let finish: (() => void) | undefined
    const api = createApi({
      dissolveGroup: vi.fn(() => new Promise<void>((resolve) => { finish = () => { dissolved = true; resolve() } })),
      getGroups: vi.fn(async () => page<FamilyGroupSummary>([dissolved ? { ...GROUP, status: 'DISSOLVED', activeMemberCount: 0, dissolvedAt: new Date() } : GROUP])),
      getMembers: vi.fn(async () => page(dissolved ? [] : [MEMBER])),
    })
    await loaded(api)
    fireEvent.change(screen.getByLabelText('해제 사유'), { target: { value: '운영 종료' } })
    fireEvent.click(screen.getByRole('button', { name: '그룹 해제 확인' }))
    expect(api.dissolveGroup).not.toHaveBeenCalled()
    const dialog = screen.getByRole('dialog')
    expect(dialog).toHaveTextContent('복구할 수 없으며 기존 예약')
    expect(dialog).toHaveTextContent('#11')
    confirm('가족 그룹 해제')
    await waitFor(() => expect(within(dialog).getByRole('button', { name: '처리 중' })).toBeDisabled())
    fireEvent.click(within(dialog).getByRole('button', { name: '처리 중' }))
    expect(api.dissolveGroup).toHaveBeenCalledTimes(1)
    finish?.()
    await screen.findByText('해제된 그룹에는 현재 구성원이 없습니다. 과거 기록은 유지됩니다.')
    expect(screen.queryByRole('heading', { name: '구성원 추가' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: '그룹 해제 확인' })).not.toBeInTheDocument()
    expect(screen.queryByText('PARTIAL_SUCCESS')).not.toBeInTheDocument()
  })

  it('그룹과_회원_대상이_바뀌면_사유와_검색_확인창을_폐기한다', async () => {
    const api = createApi({ getGroups: vi.fn().mockResolvedValue(page([GROUP, { ...GROUP, groupId: 12, name: '박 가족' }])) })
    await loaded(api)
    fireEvent.change(screen.getByLabelText('미배정 회원 검색'), { target: { value: '이' } })
    reviewAdd()
    fireEvent.click(screen.getByRole('button', { name: /박 가족/ }))
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument())
    expect(screen.getByLabelText('미배정 회원 검색')).toHaveValue('')
    expect(screen.getByLabelText('추가 사유')).toHaveValue('')
    expect(screen.getByLabelText('해제 사유')).toHaveValue('')
    expect(screen.getByRole('button', { name: '구성원 추가 확인' })).toBeDisabled()
    expect(api.addMember).not.toHaveBeenCalled()
  })

  it('빈_검색_결과와_조회_실패의_재시도를_구분한다', async () => {
    const emptyView = renderPage(createApi({ getGroups: vi.fn().mockResolvedValue(page([])) }))
    expect(await screen.findByText('조건에 맞는 가족 그룹이 없습니다.')).toBeInTheDocument()
    emptyView.unmount()
    const getGroups = vi.fn().mockRejectedValueOnce(new Error('network')).mockResolvedValueOnce(page([GROUP]))
    renderPage(createApi({ getGroups }))
    expect(await screen.findByRole('alert')).toHaveTextContent('가족 그룹을 불러오지 못했습니다.')
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))
    expect(await screen.findByRole('heading', { name: '김 가족' })).toBeInTheDocument()
  })
})
