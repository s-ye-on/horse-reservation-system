import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { ResponseError, type AdminMemberPageResponse } from '@horse/api-client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import type {
  AdminMemberProgressionResponse,
  AdminMembersApi,
  MemberClassProgressionPreviewResponse,
} from './admin-members.api'
import { AdminMembersPage } from './admin-members-page'

const MEMBER: AdminMemberProgressionResponse = {
  id: 7,
  name: '김승마',
  phone: '010-1234-5678',
  generalRideCount: 21,
  dressageRideCount: 3,
  jumpingRideCount: 1,
  dressageApproved: false,
  jumpingApproved: true,
  canUseLargeArena: true,
  progressionValue: 26,
  progressionClass: 'LARGE_ARENA_TROT',
  effectiveClass: 'LARGE_ARENA_TROT',
  progressionManagementStartedAt: '2026-08-01T09:00:00',
  progressionBaselineClass: null,
  progressionBaselineThreshold: null,
  progressionBaselineActualRideCount: null,
  specialApprovalProgressionCredit: 5,
  promotionHoldClass: null,
}

const EMPTY_AUDIT_PAGE = {
  content: [],
  page: 0,
  size: 5,
  totalElements: 0,
  totalPages: 0,
  hasNext: false,
}

afterEach(cleanup)

function memberPage(
  content: AdminMemberProgressionResponse[],
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
    previewProgression: vi.fn().mockResolvedValue(previewResult(MEMBER)),
    setProgressionBaseline: vi.fn().mockResolvedValue(MEMBER),
    removeProgressionBaseline: vi.fn().mockResolvedValue(MEMBER),
    setPromotionHold: vi.fn().mockResolvedValue(MEMBER),
    removePromotionHold: vi.fn().mockResolvedValue(MEMBER),
    correctSpecialApprovalCredit: vi.fn().mockResolvedValue(MEMBER),
    adjustRideCount: vi.fn().mockResolvedValue(MEMBER),
    getProgressionAuditLogs: vi.fn().mockResolvedValue(EMPTY_AUDIT_PAGE),
    ...overrides,
  }
}

function previewResult(
  member: AdminMemberProgressionResponse,
  expectedOverrides: Partial<MemberClassProgressionPreviewResponse['expected']> = {},
): MemberClassProgressionPreviewResponse {
  const current = {
    actualCompletedRideCount: member.generalRideCount ?? 0,
    progressionValue: member.progressionValue,
    progressionClass: member.progressionClass,
    effectiveClass: member.effectiveClass,
    baselineClass: member.progressionBaselineClass,
    baselineThreshold: member.progressionBaselineThreshold,
    baselineActualRideCount: member.progressionBaselineActualRideCount,
    specialApprovalProgressionCredit: member.specialApprovalProgressionCredit,
    promotionHoldClass: member.promotionHoldClass,
  }
  return { stateToken: 'state-v1', current, expected: { ...current, ...expectedOverrides } }
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
    const rideGrid = document.querySelector('.admin-member-ride-grid') as HTMLElement
    expect(within(rideGrid).getByText('일반 기승').nextElementSibling).toHaveTextContent('21회')
    expect(screen.getByText('마장마술', { selector: 'dt' }).nextElementSibling).toHaveTextContent('3회')
    expect(screen.getByText('장애물', { selector: 'dt' }).nextElementSibling).toHaveTextContent('1회')
    expect(screen.getByText('대마장 이용 가능')).toBeInTheDocument()
  })

  it('승인_변경_응답을_즉시_화면에_반영한다', async () => {
    const updatedMember = { ...MEMBER, dressageApproved: true }
    const changeRidingPermissions = vi.fn().mockResolvedValue(updatedMember)
    const getProgressionAuditLogs = vi.fn().mockResolvedValue(EMPTY_AUDIT_PAGE)
    renderPage(createApi({ changeRidingPermissions, getProgressionAuditLogs }))

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
    await waitFor(() => expect(getProgressionAuditLogs).toHaveBeenCalledTimes(2))
  })

  it('승인_변경_중에는_모든_승인_버튼의_중복_제출을_막는다', async () => {
    let resolveUpdate: ((member: AdminMemberProgressionResponse) => void) | undefined
    const pendingUpdate = new Promise<AdminMemberProgressionResponse>((resolve) => {
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

  it('현재_progression과_감사_snapshot을_서버_응답으로_표시한다', async () => {
    const getProgressionAuditLogs = vi.fn().mockResolvedValue({
      content: [{
        auditId: 11,
        action: 'RIDE_COUNT_ADJUSTED' as const,
        fromState: { actualCompletedRideCount: 20, progressionClass: 'LARGE_ARENA_TROT', effectiveClass: 'LARGE_ARENA_TROT' },
        toState: {
          actualCompletedRideCount: 21,
          rideCountDelta: 1,
          progressionValue: 26,
          progressionClass: 'LARGE_ARENA_TROT',
          effectiveClass: 'LARGE_ARENA_TROT',
          specialApprovalProgressionCredit: 5,
          promotionHoldClass: 'ROUND_TROT',
        },
        actorAuthSubject: 'admin-1',
        reason: '누락 집계 정정',
        occurredAt: '2026-08-20T10:00:00',
      }],
      page: 0,
      size: 5,
      totalElements: 1,
      totalPages: 1,
      hasNext: false,
    })
    renderPage(createApi({ getProgressionAuditLogs }))

    expect(await screen.findByRole('heading', { name: '일반 클래스 progression' })).toBeInTheDocument()
    expect(screen.getByText('progression 값').nextElementSibling).toHaveTextContent('26')
    expect(screen.getByText('특수 승인 인정분').nextElementSibling).toHaveTextContent('5')
    expect(await screen.findByText('누락 집계 정정')).toBeInTheDocument()
    expect(getProgressionAuditLogs).toHaveBeenCalledWith(7, 0, 5)
    fireEvent.click(screen.getByText('전후 상태 보기'))
    expect(screen.getByText('보정 delta')).toBeInTheDocument()
    expect(screen.getAllByText('특수 승인 인정분').length).toBeGreaterThan(1)
    expect(screen.getAllByText('승급 보류 상한').length).toBeGreaterThan(1)
  })

  it('서버_preview를_확인하고_필수_사유와_함께_baseline을_적용한다', async () => {
    const previewProgression = vi.fn().mockResolvedValue(previewResult(MEMBER, {
      progressionValue: 70,
      progressionClass: 'CANTER_BEGINNER',
      effectiveClass: 'CANTER_BEGINNER',
      baselineClass: 'CANTER_BEGINNER',
      baselineThreshold: 70,
      baselineActualRideCount: 21,
    }))
    const updatedMember = {
      ...MEMBER,
      progressionValue: 70,
      progressionClass: 'CANTER_BEGINNER' as const,
      effectiveClass: 'CANTER_BEGINNER' as const,
      progressionBaselineClass: 'CANTER_BEGINNER' as const,
      progressionBaselineThreshold: 70,
      progressionBaselineActualRideCount: 21,
    }
    const setProgressionBaseline = vi.fn().mockResolvedValue(updatedMember)
    renderPage(createApi({ previewProgression, setProgressionBaseline }))

    await screen.findByRole('heading', { name: '일반 클래스 progression' })
    fireEvent.change(screen.getByLabelText('시작 클래스'), { target: { value: 'CANTER_BEGINNER' } })
    fireEvent.click(screen.getByRole('button', { name: '예상 결과 확인' }))

    await waitFor(() => expect(previewProgression).toHaveBeenCalledWith(7, {
      action: 'SET_BASELINE',
      baselineClass: 'CANTER_BEGINNER',
    }))
    expect(await screen.findByRole('region', { name: '변경 전후 예상 클래스' })).toHaveTextContent('구보초보')
    const applyButton = screen.getByRole('button', { name: '확인 후 적용' })
    expect(applyButton).toBeDisabled()
    fireEvent.change(screen.getByLabelText('관리자 사유'), { target: { value: '기존 경력 확인' } })
    fireEvent.click(applyButton)

    await waitFor(() => expect(setProgressionBaseline).toHaveBeenCalledWith(
      7,
      'CANTER_BEGINNER',
      '기존 경력 확인',
      'state-v1',
    ))
    await waitFor(() => expect(screen.getByText('progression 값').nextElementSibling).toHaveTextContent('70'))
  })

  it('preview_이후_상태가_바뀌면_적용을_중단하고_재확인을_안내한다', async () => {
    const staleResponse = new Response(JSON.stringify({ code: 'MEMBER_CLASS_STATE_CONFLICT' }), {
      status: 409,
      headers: { 'Content-Type': 'application/json' },
    })
    const setProgressionBaseline = vi.fn().mockRejectedValue(
      new ResponseError(staleResponse, 'stale progression preview'),
    )
    renderPage(createApi({ setProgressionBaseline }))

    await screen.findByRole('heading', { name: '일반 클래스 progression' })
    fireEvent.click(screen.getByRole('button', { name: '예상 결과 확인' }))
    await screen.findByRole('region', { name: '변경 전후 예상 클래스' })
    fireEvent.change(screen.getByLabelText('관리자 사유'), { target: { value: '경력 확인' } })
    fireEvent.click(screen.getByRole('button', { name: '확인 후 적용' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      '다른 변경이 반영됐습니다. 현재 상태에서 예상 결과를 다시 확인해 주세요.',
    )
    expect(screen.queryByRole('region', { name: '변경 전후 예상 클래스' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '확인 후 적용' })).toBeDisabled()
  })

  it('횟수_보정값은_프론트에서_계산하지_않고_서버_preview와_Command에_전달한다', async () => {
    const previewProgression = vi.fn().mockResolvedValue(previewResult(MEMBER, {
      actualCompletedRideCount: 23,
      progressionValue: 28,
    }))
    const adjustRideCount = vi.fn().mockResolvedValue({
      ...MEMBER,
      generalRideCount: 23,
      progressionValue: 28,
    })
    renderPage(createApi({ previewProgression, adjustRideCount }))

    await screen.findByRole('heading', { name: '일반 클래스 progression' })
    fireEvent.change(screen.getByLabelText('관리 작업'), { target: { value: 'ADJUST_RIDE_COUNT' } })
    fireEvent.change(screen.getByLabelText('보정 delta'), { target: { value: '2' } })
    fireEvent.click(screen.getByRole('button', { name: '예상 결과 확인' }))

    await waitFor(() => expect(previewProgression).toHaveBeenCalledWith(7, {
      action: 'ADJUST_RIDE_COUNT',
      rideCountDelta: 2,
    }))
    fireEvent.change(screen.getByLabelText('관리자 사유'), { target: { value: '누락 2회 정정' } })
    fireEvent.click(await screen.findByRole('button', { name: '확인 후 적용' }))
    await waitFor(() => expect(adjustRideCount).toHaveBeenCalledWith(7, 2, '누락 2회 정정', 'state-v1'))
  })

  it('hold_설정과_특수_승인_인정분_교정을_preview_토큰으로_적용한다', async () => {
    const ordinaryMember = {
      ...MEMBER,
      jumpingApproved: false,
      specialApprovalProgressionCredit: 5,
    }
    const setPromotionHold = vi.fn().mockResolvedValue({
      ...ordinaryMember,
      promotionHoldClass: 'ROUND_TROT' as const,
      effectiveClass: 'ROUND_TROT' as const,
    })
    const correctSpecialApprovalCredit = vi.fn().mockResolvedValue({
      ...ordinaryMember,
      specialApprovalProgressionCredit: 3,
    })
    renderPage(createApi({
      getMembers: vi.fn().mockResolvedValue(memberPage([ordinaryMember])),
      getMember: vi.fn().mockResolvedValue(ordinaryMember),
      setPromotionHold,
      correctSpecialApprovalCredit,
    }))

    await screen.findByRole('heading', { name: '일반 클래스 progression' })
    fireEvent.change(screen.getByLabelText('관리 작업'), { target: { value: 'SET_PROMOTION_HOLD' } })
    fireEvent.change(screen.getByLabelText('보류 상한 클래스'), { target: { value: 'ROUND_TROT' } })
    fireEvent.click(screen.getByRole('button', { name: '예상 결과 확인' }))
    await screen.findByRole('region', { name: '변경 전후 예상 클래스' })
    fireEvent.change(screen.getByLabelText('관리자 사유'), { target: { value: '안전 확인' } })
    fireEvent.click(screen.getByRole('button', { name: '확인 후 적용' }))
    await waitFor(() => expect(setPromotionHold).toHaveBeenCalledWith(
      7,
      'ROUND_TROT',
      '안전 확인',
      'state-v1',
    ))

    fireEvent.change(screen.getByLabelText('관리 작업'), {
      target: { value: 'CORRECT_SPECIAL_APPROVAL_CREDIT' },
    })
    fireEvent.change(screen.getByLabelText('교정 후 인정분'), { target: { value: '3' } })
    fireEvent.click(screen.getByRole('button', { name: '예상 결과 확인' }))
    await screen.findByRole('region', { name: '변경 전후 예상 클래스' })
    fireEvent.change(screen.getByLabelText('관리자 사유'), { target: { value: '인정분 재검토' } })
    fireEvent.click(screen.getByRole('button', { name: '확인 후 적용' }))
    await waitFor(() => expect(correctSpecialApprovalCredit).toHaveBeenCalledWith(
      7,
      3,
      '인정분 재검토',
      'state-v1',
    ))
  })

  it('baseline과_hold_해제_Command도_서버_preview_이후에만_적용한다', async () => {
    const configuredMember = {
      ...MEMBER,
      jumpingApproved: false,
      progressionBaselineClass: 'LARGE_ARENA_TROT' as const,
      progressionBaselineThreshold: 26,
      progressionBaselineActualRideCount: 21,
      promotionHoldClass: 'ROUND_TROT' as const,
      effectiveClass: 'ROUND_TROT' as const,
    }
    const removeProgressionBaseline = vi.fn().mockResolvedValue(configuredMember)
    const removePromotionHold = vi.fn().mockResolvedValue(configuredMember)
    renderPage(createApi({
      getMembers: vi.fn().mockResolvedValue(memberPage([configuredMember])),
      getMember: vi.fn().mockResolvedValue(configuredMember),
      removeProgressionBaseline,
      removePromotionHold,
    }))

    await screen.findByRole('heading', { name: '일반 클래스 progression' })
    fireEvent.change(screen.getByLabelText('관리 작업'), { target: { value: 'REMOVE_BASELINE' } })
    fireEvent.click(screen.getByRole('button', { name: '예상 결과 확인' }))
    await screen.findByRole('region', { name: '변경 전후 예상 클래스' })
    fireEvent.change(screen.getByLabelText('관리자 사유'), { target: { value: 'baseline 교정' } })
    fireEvent.click(screen.getByRole('button', { name: '확인 후 적용' }))
    await waitFor(() => expect(removeProgressionBaseline).toHaveBeenCalledWith(7, 'baseline 교정', 'state-v1'))

    fireEvent.change(screen.getByLabelText('관리 작업'), { target: { value: 'REMOVE_PROMOTION_HOLD' } })
    fireEvent.click(screen.getByRole('button', { name: '예상 결과 확인' }))
    await screen.findByRole('region', { name: '변경 전후 예상 클래스' })
    fireEvent.change(screen.getByLabelText('관리자 사유'), { target: { value: '안전 재평가 완료' } })
    fireEvent.click(screen.getByRole('button', { name: '확인 후 적용' }))
    await waitFor(() => expect(removePromotionHold).toHaveBeenCalledWith(7, '안전 재평가 완료', 'state-v1'))
  })

  it('promotion_hold와_특수_승인의_상호_배타_해제_순서를_안내한다', async () => {
    const heldMember = {
      ...MEMBER,
      jumpingApproved: false,
      promotionHoldClass: 'ROUND_TROT' as const,
      effectiveClass: 'ROUND_TROT' as const,
    }
    renderPage(createApi({
      getMembers: vi.fn().mockResolvedValue(memberPage([heldMember])),
      getMember: vi.fn().mockResolvedValue(heldMember),
    }))

    expect(await screen.findByText('특수 승인을 변경하려면 승급 보류를 먼저 명시적으로 해제해야 합니다.'))
      .toBeInTheDocument()
    expect(screen.getByRole('switch', { name: '마장마술 승인' })).toBeDisabled()
    expect(screen.getByRole('switch', { name: '장애물 승인' })).toBeDisabled()
    const management = screen.getByLabelText('관리 작업')
    expect(within(management).getByRole('option', { name: '승급 보류 해제' })).toBeEnabled()
  })
})
