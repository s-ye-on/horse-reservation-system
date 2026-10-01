import { useEffect, useState } from 'react'
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import type { AdminMemberPageResponse, AdminMemberResponse } from '@horse/api-client'
import { adminMembersApi, getAdminMembersErrorKind, type AdminMemberProgressionResponse, type AdminMembersApi } from './admin-members.api'
import { AdminMemberClassProgressionPanel } from './admin-member-class-progression-panel'
import { gradeLabel, formatCount, formatDateTime } from './member-formatters'
import { AdminMemberSpecialApprovalPanel } from './admin-member-special-approval-panel'
import './admin-members-page.css'

const MEMBER_LIST_QUERY_KEY = ['admin', 'member-list'] as const
const PAGE_SIZE = 20
const EMPTY_MEMBERS: AdminMemberResponse[] = []

function getErrorMessage(error: unknown) {
  const kind = getAdminMembersErrorKind(error)
  if (kind === 'forbidden') return '관리자 권한이 없어 회원 정보를 확인할 수 없습니다.'
  if (kind === 'not-found') return '선택한 회원이 더 이상 존재하지 않습니다.'
  return '회원 정보를 불러오지 못했습니다. 최신 상태를 다시 확인해 주세요.'
}

export function AdminMembersPage({ api = adminMembersApi }: { api?: AdminMembersApi }) {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [selectedMemberId, setSelectedMemberId] = useState<number>()
  const membersQuery = useQuery({ queryKey: [...MEMBER_LIST_QUERY_KEY, page], queryFn: () => api.getMembers(page, PAGE_SIZE), placeholderData: keepPreviousData })
  const members = membersQuery.data?.content ?? EMPTY_MEMBERS
  const totalPages = membersQuery.data?.totalPages ?? 0
  const activeMemberId = selectedMemberId !== undefined && members.some((member) => member.id === selectedMemberId) ? selectedMemberId : members[0]?.id
  useEffect(() => {
    if (totalPages === 0 && page !== 0) setPage(0)
    else if (totalPages > 0 && page >= totalPages) setPage(totalPages - 1)
  }, [page, totalPages])
  const memberQuery = useQuery({ queryKey: ['admin', 'members', activeMemberId], queryFn: () => api.getMember(activeMemberId as number), enabled: activeMemberId !== undefined })
  const updateMemberCaches = (updatedMember: AdminMemberProgressionResponse) => {
    queryClient.setQueriesData<AdminMemberPageResponse>({ queryKey: MEMBER_LIST_QUERY_KEY }, (current) => current ? { ...current, content: current.content.map((member) => member.id === updatedMember.id ? updatedMember : member) } : current)
    queryClient.setQueryData(['admin', 'members', updatedMember.id], updatedMember)
  }
  return <main className="admin-members-page">
    <header className="admin-members-header"><p className="admin-members-eyebrow">회원 운영</p><h1>회원 기승 단계 및 승인 관리</h1><p>일반 기승 단계와 마장마술·장애물 승인, 변경 감사 이력을 확인합니다.</p></header>
    {membersQuery.isPending ? <p className="admin-members-state" role="status">회원 목록을 불러오는 중입니다.</p> : null}
    {membersQuery.isError ? <div className="admin-members-page-error" role="alert"><p>{getErrorMessage(membersQuery.error)}</p><button type="button" onClick={() => { void membersQuery.refetch() }}>다시 불러오기</button></div> : null}
    {membersQuery.data ? members.length === 0 ? <section className="admin-members-state"><h2>등록된 회원이 없습니다.</h2><p>회원 정보가 등록되면 기승 단계와 승인 상태를 관리할 수 있습니다.</p></section> : <div className="admin-members-layout">
      <section className="admin-members-panel admin-members-master" aria-label="회원 목록" aria-busy={membersQuery.isFetching}>
        <header className="admin-members-pane-head"><h2>회원 목록</h2><p>전체 {membersQuery.data.totalElements}명</p><p>최근 등록 순 · 페이지당 20명</p></header>
        <ul className="admin-members-list">{members.map((member) => <li key={member.id}><button type="button" className="admin-member-select" aria-current={member.id === activeMemberId ? 'true' : undefined} aria-controls="admin-member-detail" disabled={membersQuery.isPlaceholderData} onClick={() => setSelectedMemberId(member.id)}>
          <strong>{member.name}</strong><span>{member.phone}</span>{member.id === activeMemberId ? <small>현재 선택</small> : null}
        </button></li>)}</ul>
        {totalPages > 0 ? <nav className="admin-members-pagination" aria-label="회원 목록 페이지"><button type="button" disabled={page === 0 || membersQuery.isFetching} onClick={() => setPage((value) => value - 1)}>이전</button><span aria-live="polite">{page + 1} / {totalPages} 페이지</span><button type="button" disabled={page + 1 >= totalPages || !membersQuery.data.hasNext || membersQuery.isFetching} onClick={() => setPage((value) => value + 1)}>다음</button></nav> : null}
        {membersQuery.isFetching ? <p className="admin-member-help" role="status">페이지 이동 중입니다.</p> : null}
      </section>
      <div id="admin-member-detail" className="admin-member-detail" aria-label="회원 상세">
        {memberQuery.isPending ? <p className="admin-members-state" role="status">회원 상세를 불러오는 중입니다.</p> : memberQuery.isError ? <section className="admin-members-page-error" role="alert"><p>{getErrorMessage(memberQuery.error)}</p><button type="button" onClick={() => { void memberQuery.refetch() }}>다시 불러오기</button></section> : memberQuery.data ? <MemberDetail key={memberQuery.data.id} member={memberQuery.data} api={api} onMemberUpdated={updateMemberCaches} /> : null}
      </div>
    </div> : null}
  </main>
}

function MemberDetail({ member, api, onMemberUpdated }: { member: AdminMemberProgressionResponse; api: AdminMembersApi; onMemberUpdated(member: AdminMemberProgressionResponse): void }) {
  return <>
    <section className="admin-member-summary" aria-label="회원 요약">
      <header className="admin-member-section-heading"><div><h2>{member.name}</h2><p>{member.phone}</p></div><span className="admin-member-badge">{member.canUseLargeArena ? '대마장 이용 가능' : '원형마장 이용'}</span></header>
      <dl className="admin-member-summary-grid">
        <div className="primary"><dt>현재 예약 가능 일반 단계</dt><dd>{gradeLabel(member.effectiveClass)}</dd></div><div><dt>일반 승급 단계</dt><dd>{gradeLabel(member.progressionClass)}</dd></div>
        <div><dt>마장마술 승인</dt><dd>{member.dressageApproved ? '승인' : '미승인'}</dd></div><div><dt>장애물 승인</dt><dd>{member.jumpingApproved ? '승인' : '미승인'}</dd></div><div><dt>자동 승급 보류 상한</dt><dd>{member.promotionHoldClass == null ? '보류 없음' : gradeLabel(member.promotionHoldClass)}</dd></div>
      </dl>
      <p className="admin-member-help">{member.promotionHoldClass != null ? '일반 승급 산정은 계속되지만 예약 가능 단계는 보류 상한을 따릅니다.' : '예약 가능 단계는 서버가 계산한 일반 승급 결과를 따릅니다.'}</p>
      <details className="admin-member-calculation"><summary>기승 기록·계산 상세 보기</summary><dl className="admin-member-ride-grid">
        <div><dt>일반 기승</dt><dd>{formatCount(member.generalRideCount)}</dd></div><div><dt>마장마술</dt><dd>{formatCount(member.dressageRideCount)}</dd></div><div><dt>장애물</dt><dd>{formatCount(member.jumpingRideCount)}</dd></div>
        <div><dt>승급 산정 횟수</dt><dd>{formatCount(member.progressionValue)}</dd></div><div><dt>인정 시작 클래스</dt><dd>{gradeLabel(member.progressionBaselineClass)}</dd></div><div><dt>특수 승인 추가 인정 횟수</dt><dd>{formatCount(member.specialApprovalProgressionCredit)}</dd></div>
        <div><dt>인정 시작 기준 횟수</dt><dd>{member.progressionBaselineThreshold == null ? '미설정' : formatCount(member.progressionBaselineThreshold)}</dd></div><div><dt>인정 시점 실제 기승</dt><dd>{member.progressionBaselineActualRideCount == null ? '미설정' : formatCount(member.progressionBaselineActualRideCount)}</dd></div><div><dt>승급 관리 시작 시각</dt><dd>{formatDateTime(member.progressionManagementStartedAt)}</dd></div>
      </dl></details>
    </section>
    <AdminMemberSpecialApprovalPanel member={member} api={api} onMemberUpdated={onMemberUpdated} />
    <AdminMemberClassProgressionPanel member={member} api={api} onMemberUpdated={onMemberUpdated} />
  </>
}
