import { keepPreviousData, useMutation, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query'
import { type FormEvent, useEffect, useState } from 'react'
import {
  adminFamilyGroupsApi,
  type AdminFamilyGroupsApi,
  type FamilyGroupAuditAction,
  type FamilyGroupAuditLog,
  type FamilyGroupFilters,
  type FamilyGroupMember,
  type FamilyGroupSummary,
  type FamilyMemberCandidate,
  type PageResponse,
  getFamilyGroupErrorMessage,
} from './admin-family-groups.api'
import './admin-family-groups-page.css'

const GROUP_PAGE_SIZE = 10
const MEMBER_PAGE_SIZE = 8
const AUDIT_PAGE_SIZE = 8
const CANDIDATE_PAGE_SIZE = 6
const EMPTY_FILTERS: FamilyGroupFilters = { query: '', status: 'ACTIVE' }
const GROUP_QUERY_KEY = ['admin', 'family-groups'] as const

interface AdminFamilyGroupsPageProps {
  api?: AdminFamilyGroupsApi
}

export function AdminFamilyGroupsPage({ api = adminFamilyGroupsApi }: AdminFamilyGroupsPageProps) {
  const queryClient = useQueryClient()
  const [draftFilters, setDraftFilters] = useState(EMPTY_FILTERS)
  const [filters, setFilters] = useState(EMPTY_FILTERS)
  const [groupPage, setGroupPage] = useState(0)
  const [selectedGroupId, setSelectedGroupId] = useState<number>()
  const [memberPage, setMemberPage] = useState(0)
  const [auditPage, setAuditPage] = useState(0)
  const [candidatePage, setCandidatePage] = useState(0)
  const [candidateDraft, setCandidateDraft] = useState('')
  const [candidateQuery, setCandidateQuery] = useState('')
  const [createName, setCreateName] = useState('')
  const [createReason, setCreateReason] = useState('')
  const [addReason, setAddReason] = useState('')
  const [removalTargetId, setRemovalTargetId] = useState<number>()
  const [removalReason, setRemovalReason] = useState('')
  const [dissolutionReason, setDissolutionReason] = useState('')

  const groupsQuery = useQuery({
    queryKey: [...GROUP_QUERY_KEY, filters, groupPage],
    queryFn: () => api.getGroups(filters, groupPage, GROUP_PAGE_SIZE),
    placeholderData: keepPreviousData,
  })
  const groups = groupsQuery.data?.content ?? []
  const selectedGroup = groups.find((group) => group.groupId === selectedGroupId) ?? groups[0]
  const activeGroupId = selectedGroup?.groupId

  useEffect(() => {
    const totalPages = groupsQuery.data?.totalPages ?? 0
    if (totalPages === 0 && groupPage !== 0) setGroupPage(0)
    else if (totalPages > 0 && groupPage >= totalPages) setGroupPage(totalPages - 1)
  }, [groupPage, groupsQuery.data?.totalPages])

  useEffect(() => {
    setMemberPage(0)
    setAuditPage(0)
    setCandidatePage(0)
    setRemovalTargetId(undefined)
    setRemovalReason('')
  }, [activeGroupId])

  const membersQuery = useQuery({
    queryKey: [...GROUP_QUERY_KEY, activeGroupId, 'members', memberPage],
    queryFn: () => api.getMembers(activeGroupId as number, memberPage, MEMBER_PAGE_SIZE),
    enabled: activeGroupId !== undefined,
  })
  const auditQuery = useQuery({
    queryKey: [...GROUP_QUERY_KEY, activeGroupId, 'audit', auditPage],
    queryFn: () => api.getAuditLogs(activeGroupId as number, auditPage, AUDIT_PAGE_SIZE),
    enabled: activeGroupId !== undefined,
  })
  const candidatesQuery = useQuery({
    queryKey: [...GROUP_QUERY_KEY, 'candidates', candidateQuery, candidatePage],
    queryFn: () => api.getMemberCandidates(candidateQuery, candidatePage, CANDIDATE_PAGE_SIZE),
    enabled: selectedGroup?.status === 'ACTIVE',
    placeholderData: keepPreviousData,
  })

  usePageBounds(memberPage, membersQuery.data?.totalPages, setMemberPage)
  usePageBounds(auditPage, auditQuery.data?.totalPages, setAuditPage)
  usePageBounds(candidatePage, candidatesQuery.data?.totalPages, setCandidatePage)

  const refreshGroupData = async () => {
    await queryClient.invalidateQueries({ queryKey: GROUP_QUERY_KEY })
  }

  const createMutation = useMutation({
    mutationFn: () => api.createGroup(createName.trim(), createReason.trim()),
    onSuccess: async () => {
      setCreateName('')
      setCreateReason('')
      setFilters(EMPTY_FILTERS)
      setDraftFilters(EMPTY_FILTERS)
      setGroupPage(0)
      setSelectedGroupId(undefined)
      await refreshGroupData()
    },
  })
  const addMutation = useMutation({
    mutationFn: (memberId: number) => api.addMember(activeGroupId as number, memberId, addReason.trim()),
    onSuccess: async () => {
      setAddReason('')
      await refreshGroupData()
    },
  })
  const removeMutation = useMutation({
    mutationFn: (memberId: number) => api.removeMember(activeGroupId as number, memberId, removalReason.trim()),
    onSuccess: async () => {
      setRemovalTargetId(undefined)
      setRemovalReason('')
      await refreshGroupData()
    },
  })
  const dissolveMutation = useMutation({
    mutationFn: () => api.dissolveGroup(activeGroupId as number, dissolutionReason.trim()),
    onSuccess: async () => {
      setDissolutionReason('')
      await refreshGroupData()
    },
  })

  const applyFilters = (event: FormEvent) => {
    event.preventDefault()
    setFilters({ query: draftFilters.query.trim(), status: draftFilters.status })
    setGroupPage(0)
    setSelectedGroupId(undefined)
  }

  if (groupsQuery.isPending) {
    return <FamilyPageState message="가족 그룹을 불러오는 중입니다." />
  }
  if (groupsQuery.isError && groupsQuery.data === undefined) {
    return (
      <FamilyPageState
        error
        message={getFamilyGroupErrorMessage(groupsQuery.error, '가족 그룹을 불러오지 못했습니다.')}
        onRetry={() => { void groupsQuery.refetch() }}
      />
    )
  }

  return (
    <main className="admin-family-page">
      <div className="admin-family-shell">
        <header className="admin-family-header">
          <div>
            <p className="admin-family-eyebrow">FAMILY OPERATIONS</p>
            <h1>가족 그룹 관리</h1>
          </div>
          <p>전체 {groupsQuery.data?.totalElements ?? 0}개 그룹</p>
        </header>

        <section className="admin-family-toolbar" aria-label="가족 그룹 검색과 생성">
          <form className="admin-family-search" onSubmit={applyFilters}>
            <label>
              그룹 이름
              <input
                value={draftFilters.query}
                maxLength={100}
                onChange={(event) => setDraftFilters((current) => ({ ...current, query: event.target.value }))}
              />
            </label>
            <label>
              상태
              <select
                value={draftFilters.status}
                onChange={(event) => setDraftFilters((current) => ({
                  ...current,
                  status: event.target.value as FamilyGroupFilters['status'],
                }))}
              >
                <option value="">전체</option>
                <option value="ACTIVE">운영 중</option>
                <option value="DISSOLVED">해제됨</option>
              </select>
            </label>
            <button type="submit" disabled={groupsQuery.isFetching}>검색</button>
          </form>

          <details className="admin-family-create">
            <summary>새 가족 그룹</summary>
            <form onSubmit={(event) => { event.preventDefault(); createMutation.mutate() }}>
              <label>그룹 이름<input required maxLength={100} value={createName} onChange={(event) => setCreateName(event.target.value)} /></label>
              <label>생성 사유<textarea required maxLength={500} value={createReason} onChange={(event) => setCreateReason(event.target.value)} /></label>
              <button type="submit" disabled={createMutation.isPending || !createName.trim() || !createReason.trim()}>
                {createMutation.isPending ? '생성 중' : '그룹 생성'}
              </button>
              {createMutation.isError ? <MutationError error={createMutation.error} fallback="그룹을 생성하지 못했습니다." /> : null}
            </form>
          </details>
        </section>

        {groupsQuery.isError ? (
          <div className="admin-family-inline-error" role="alert">
            <span>{getFamilyGroupErrorMessage(groupsQuery.error, '가족 그룹을 갱신하지 못했습니다.')}</span>
            <button type="button" onClick={() => { void groupsQuery.refetch() }}>다시 시도</button>
          </div>
        ) : null}

        <div className="admin-family-layout">
          <section className="admin-family-panel admin-family-groups" aria-label="가족 그룹 목록" aria-busy={groupsQuery.isFetching}>
            {groups.length === 0 ? (
              <p className="admin-family-empty">조건에 맞는 가족 그룹이 없습니다.</p>
            ) : (
              <ul>
                {groups.map((group) => (
                  <li key={group.groupId}>
                    <button
                      type="button"
                      className="admin-family-group-select"
                      aria-current={group.groupId === activeGroupId}
                      onClick={() => setSelectedGroupId(group.groupId)}
                    >
                      <span><strong>{group.name}</strong><small>#{group.groupId}</small></span>
                      <span><small>{statusLabel(group.status)}</small><b>{group.activeMemberCount}명</b></span>
                    </button>
                  </li>
                ))}
              </ul>
            )}
            <Pagination
              label="가족 그룹 목록"
              page={groupPage}
              totalPages={groupsQuery.data?.totalPages ?? 0}
              hasNext={Boolean(groupsQuery.data?.hasNext)}
              busy={groupsQuery.isFetching}
              onPageChange={setGroupPage}
            />
          </section>

          <section className="admin-family-panel admin-family-detail" aria-label="선택한 가족 그룹 상세">
            {selectedGroup ? (
              <>
                <header className="admin-family-detail-header">
                  <div><p>그룹 #{selectedGroup.groupId}</p><h2>{selectedGroup.name}</h2></div>
                  <span className={`admin-family-status admin-family-status-${selectedGroup.status.toLowerCase()}`}>
                    {statusLabel(selectedGroup.status)}
                  </span>
                </header>
                <p className="admin-family-detail-meta">
                  생성 {formatDateTime(selectedGroup.createdAt)} · 현재 구성원 {selectedGroup.activeMemberCount}명
                </p>
                <GroupMembers
                  group={selectedGroup}
                  page={memberPage}
                  query={membersQuery}
                  removalTargetId={removalTargetId}
                  removalReason={removalReason}
                  removing={removeMutation.isPending}
                  onPageChange={setMemberPage}
                  onStartRemoval={(memberId) => { setRemovalTargetId(memberId); setRemovalReason('') }}
                  onCancelRemoval={() => { setRemovalTargetId(undefined); setRemovalReason('') }}
                  onRemovalReasonChange={setRemovalReason}
                  onRemove={(memberId) => removeMutation.mutate(memberId)}
                />
                {removeMutation.isError ? <MutationError error={removeMutation.error} fallback="구성원을 제거하지 못했습니다." /> : null}

                {selectedGroup.status === 'ACTIVE' ? (
                  <MemberCandidates
                    draft={candidateDraft}
                    queryValue={candidateQuery}
                    page={candidatePage}
                    query={candidatesQuery}
                    reason={addReason}
                    adding={addMutation.isPending}
                    onDraftChange={setCandidateDraft}
                    onSearch={() => { setCandidateQuery(candidateDraft.trim()); setCandidatePage(0) }}
                    onPageChange={setCandidatePage}
                    onReasonChange={setAddReason}
                    onAdd={(memberId) => addMutation.mutate(memberId)}
                  />
                ) : null}
                {addMutation.isError ? <MutationError error={addMutation.error} fallback="구성원을 추가하지 못했습니다." /> : null}

                <GroupAudit page={auditPage} query={auditQuery} onPageChange={setAuditPage} />

                {selectedGroup.status === 'ACTIVE' ? (
                  <form className="admin-family-dissolve" onSubmit={(event) => { event.preventDefault(); dissolveMutation.mutate() }}>
                    <h3>그룹 해제</h3>
                    <p>그룹 해제는 되돌릴 수 없으며 모든 현재 membership을 종료합니다.</p>
                    <label>해제 사유<textarea required maxLength={500} value={dissolutionReason} onChange={(event) => setDissolutionReason(event.target.value)} /></label>
                    <button type="submit" disabled={dissolveMutation.isPending || !dissolutionReason.trim()}>
                      {dissolveMutation.isPending ? '해제 중' : '가족 그룹 해제'}
                    </button>
                    {dissolveMutation.isError ? <MutationError error={dissolveMutation.error} fallback="가족 그룹을 해제하지 못했습니다." /> : null}
                  </form>
                ) : null}
              </>
            ) : (
              <p className="admin-family-empty">가족 그룹을 선택해 주세요.</p>
            )}
          </section>
        </div>
      </div>
    </main>
  )
}

function GroupMembers({ group, page, query, removalTargetId, removalReason, removing, onPageChange, onStartRemoval, onCancelRemoval, onRemovalReasonChange, onRemove }: {
  group: FamilyGroupSummary
  page: number
  query: UseQueryResult<PageResponse<FamilyGroupMember>, Error>
  removalTargetId?: number
  removalReason: string
  removing: boolean
  onPageChange: (page: number) => void
  onStartRemoval: (memberId: number) => void
  onCancelRemoval: () => void
  onRemovalReasonChange: (reason: string) => void
  onRemove: (memberId: number) => void
}) {
  const members = query.data?.content ?? []
  return (
    <section className="admin-family-section" aria-labelledby="family-members-title" aria-busy={query.isFetching}>
      <div className="admin-family-section-heading"><h3 id="family-members-title">현재 구성원</h3><span>{query.data?.totalElements ?? group.activeMemberCount}명</span></div>
      {query.isPending ? <p className="admin-family-empty" role="status">구성원을 불러오는 중입니다.</p> : query.isError ? <QueryError message="구성원을 불러오지 못했습니다." onRetry={() => { void query.refetch() }} /> : members.length === 0 ? (
        <p className="admin-family-empty">현재 구성원이 없는 ACTIVE 그룹도 유지할 수 있습니다.</p>
      ) : (
        <ul className="admin-family-member-list">
          {members.map((member) => (
            <li key={member.membershipId}>
              <div><strong>{member.name}</strong><span>{member.phone} · 가입 {formatDateTime(member.joinedAt)}</span></div>
              {group.status === 'ACTIVE' ? (
                removalTargetId === member.memberId ? (
                  <div className="admin-family-inline-form">
                    <label>제거 사유<input required maxLength={500} value={removalReason} onChange={(event) => onRemovalReasonChange(event.target.value)} /></label>
                    <button type="button" disabled={removing || !removalReason.trim()} onClick={() => onRemove(member.memberId)}>제거 확인</button>
                    <button type="button" disabled={removing} onClick={onCancelRemoval}>취소</button>
                  </div>
                ) : <button type="button" onClick={() => onStartRemoval(member.memberId)}>제거</button>
              ) : null}
            </li>
          ))}
        </ul>
      )}
      <Pagination label="가족 구성원" page={page} totalPages={query.data?.totalPages ?? 0} hasNext={Boolean(query.data?.hasNext)} busy={query.isFetching} onPageChange={onPageChange} />
    </section>
  )
}

function MemberCandidates({ draft, queryValue, page, query, reason, adding, onDraftChange, onSearch, onPageChange, onReasonChange, onAdd }: {
  draft: string
  queryValue: string
  page: number
  query: UseQueryResult<PageResponse<FamilyMemberCandidate>, Error>
  reason: string
  adding: boolean
  onDraftChange: (value: string) => void
  onSearch: () => void
  onPageChange: (page: number) => void
  onReasonChange: (value: string) => void
  onAdd: (memberId: number) => void
}) {
  const candidates = query.data?.content ?? []
  return (
    <section className="admin-family-section" aria-labelledby="family-add-title" aria-busy={query.isFetching}>
      <h3 id="family-add-title">구성원 추가</h3>
      <form className="admin-family-candidate-search" onSubmit={(event) => { event.preventDefault(); onSearch() }}>
        <label>회원 이름 또는 연락처<input value={draft} maxLength={100} onChange={(event) => onDraftChange(event.target.value)} /></label>
        <button type="submit" disabled={query.isFetching}>후보 검색</button>
      </form>
      <label className="admin-family-reason">추가 사유<input required maxLength={500} value={reason} onChange={(event) => onReasonChange(event.target.value)} /></label>
      {query.isPending ? <p className="admin-family-empty" role="status">추가 가능한 회원을 불러오는 중입니다.</p> : query.isError ? <QueryError message="추가 가능한 회원을 불러오지 못했습니다." onRetry={() => { void query.refetch() }} /> : candidates.length === 0 ? (
        <p className="admin-family-empty">{queryValue ? '검색 조건에 맞는 추가 가능 회원이 없습니다.' : '추가 가능한 회원이 없습니다.'}</p>
      ) : (
        <ul className="admin-family-candidate-list">
          {candidates.map((candidate) => (
            <li key={candidate.memberId}>
              <span><strong>{candidate.name}</strong><small>{candidate.phone}</small></span>
              <button type="button" disabled={adding || !reason.trim()} onClick={() => onAdd(candidate.memberId)}>추가</button>
            </li>
          ))}
        </ul>
      )}
      <Pagination label="추가 가능 회원" page={page} totalPages={query.data?.totalPages ?? 0} hasNext={Boolean(query.data?.hasNext)} busy={query.isFetching} onPageChange={onPageChange} />
    </section>
  )
}

function GroupAudit({ page, query, onPageChange }: {
  page: number
  query: UseQueryResult<PageResponse<FamilyGroupAuditLog>, Error>
  onPageChange: (page: number) => void
}) {
  const auditLogs = query.data?.content ?? []
  return (
    <section className="admin-family-section" aria-labelledby="family-audit-title" aria-busy={query.isFetching}>
      <h3 id="family-audit-title">변경 감사</h3>
      {query.isPending ? <p className="admin-family-empty" role="status">감사 이력을 불러오는 중입니다.</p> : query.isError ? <QueryError message="감사 이력을 불러오지 못했습니다." onRetry={() => { void query.refetch() }} /> : auditLogs.length === 0 ? (
        <p className="admin-family-empty">감사 이력이 없습니다.</p>
      ) : (
        <ol className="admin-family-audit-list">
          {auditLogs.map((audit) => (
            <li key={audit.auditId}>
              <div><strong>{auditLabel(audit.action)}</strong><time dateTime={audit.occurredAt}>{formatDateTime(audit.occurredAt)}</time></div>
              <p>{audit.memberName ? `${audit.memberName} · ` : ''}{audit.reason}</p>
              <small>처리자 {audit.actorAuthSubject}</small>
              <details className="admin-family-audit-state">
                <summary>전후 상태</summary>
                <dl>
                  <div><dt>변경 전</dt><dd>{formatAuditState(audit.fromState)}</dd></div>
                  <div><dt>변경 후</dt><dd>{formatAuditState(audit.toState)}</dd></div>
                </dl>
              </details>
            </li>
          ))}
        </ol>
      )}
      <Pagination label="가족 변경 감사" page={page} totalPages={query.data?.totalPages ?? 0} hasNext={Boolean(query.data?.hasNext)} busy={query.isFetching} onPageChange={onPageChange} />
    </section>
  )
}

function Pagination({ label, page, totalPages, hasNext, busy, onPageChange }: { label: string; page: number; totalPages: number; hasNext: boolean; busy: boolean; onPageChange: (page: number) => void }) {
  if (totalPages === 0) return null
  return (
    <nav className="admin-family-pagination" aria-label={`${label} 페이지`}>
      <button type="button" disabled={page === 0 || busy} onClick={() => onPageChange(page - 1)}>이전</button>
      <span aria-live="polite">{page + 1} / {totalPages} 페이지</span>
      <button type="button" disabled={!hasNext || page + 1 >= totalPages || busy} onClick={() => onPageChange(page + 1)}>다음</button>
    </nav>
  )
}

function FamilyPageState({ message, error = false, onRetry }: { message: string; error?: boolean; onRetry?: () => void }) {
  return <main className={`admin-family-page-state${error ? ' admin-family-error' : ''}`} role={error ? 'alert' : 'status'}><p>{message}</p>{onRetry ? <button type="button" onClick={onRetry}>다시 시도</button> : null}</main>
}

function QueryError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return <div className="admin-family-query-error" role="alert"><span>{message}</span><button type="button" onClick={onRetry}>다시 시도</button></div>
}

function MutationError({ error, fallback }: { error: unknown; fallback: string }) {
  return <p className="admin-family-mutation-error" role="alert">{getFamilyGroupErrorMessage(error, fallback)}</p>
}

function statusLabel(status: FamilyGroupSummary['status']) {
  return status === 'ACTIVE' ? '운영 중' : '해제됨'
}

function auditLabel(action: FamilyGroupAuditAction) {
  return ({ GROUP_CREATED: '그룹 생성', MEMBER_ADDED: '구성원 추가', MEMBER_REMOVED: '구성원 제거', GROUP_DISSOLVED: '그룹 해제' })[action]
}

function formatDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('ko-KR', {
    timeZone: 'Asia/Seoul', year: 'numeric', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(date)
}

function formatAuditState(value: Record<string, unknown> | null) {
  return value === null ? '없음' : JSON.stringify(value)
}

function usePageBounds(page: number, totalPages: number | undefined, setPage: (page: number) => void) {
  useEffect(() => {
    if (totalPages === undefined) return
    if (totalPages === 0 && page !== 0) setPage(0)
    else if (totalPages > 0 && page >= totalPages) setPage(totalPages - 1)
  }, [page, setPage, totalPages])
}
