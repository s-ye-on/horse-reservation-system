import { ResponseError } from '@horse/api-client'
import { keepPreviousData, useMutation, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query'
import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react'
import { adminFamilyGroupsApi, getFamilyGroupErrorMessage, type AdminFamilyGroupsApi, type FamilyGroupAuditLog, type FamilyGroupFilters, type FamilyGroupMember, type FamilyGroupSummary, type FamilyMemberCandidate, type PageResponse } from './admin-family-groups.api'
import { FamilyGroupActionDialog } from './family-group-action-dialog'
import { formatFamilyAuditState, formatFamilyDateTime, familyAuditLabel } from './format-family-audit'
import './admin-family-groups-page.css'

const GROUP_PAGE_SIZE = 10
const MEMBER_PAGE_SIZE = 8
const AUDIT_PAGE_SIZE = 8
const CANDIDATE_PAGE_SIZE = 6
const EMPTY_FILTERS: FamilyGroupFilters = { query: '', status: 'ACTIVE' }
const GROUP_QUERY_KEY = ['admin', 'family-groups'] as const
type GroupAction = { kind: 'create' } | { kind: 'add'; group: FamilyGroupSummary; member: FamilyMemberCandidate; reason: string } | { kind: 'remove'; group: FamilyGroupSummary; member: FamilyGroupMember } | { kind: 'dissolve'; group: FamilyGroupSummary; reason: string }
type Command = { kind: 'create'; name: string; reason: string } | { kind: 'add' | 'remove'; groupId: number; memberId: number; reason: string } | { kind: 'dissolve'; groupId: number; reason: string }
type FieldErrors = { name?: string; reason?: string }
type Feedback = { error: boolean; message: string }

export function AdminFamilyGroupsPage({ api = adminFamilyGroupsApi }: { api?: AdminFamilyGroupsApi }) {
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
  const [candidateId, setCandidateId] = useState<number>()
  const [addReason, setAddReason] = useState('')
  const [dissolveReason, setDissolveReason] = useState('')
  const [addError, setAddError] = useState('')
  const [dissolveError, setDissolveError] = useState('')
  const [action, setAction] = useState<GroupAction>()
  const [dialogName, setDialogName] = useState('')
  const [dialogReason, setDialogReason] = useState('')
  const [dialogErrors, setDialogErrors] = useState<FieldErrors>({})
  const [dialogError, setDialogError] = useState('')
  const [feedback, setFeedback] = useState<Feedback>()
  const feedbackRef = useRef<HTMLElement>(null)
  const groupsQuery = useQuery({ queryKey: [...GROUP_QUERY_KEY, filters, groupPage], queryFn: () => api.getGroups(filters, groupPage, GROUP_PAGE_SIZE), placeholderData: keepPreviousData })
  const groups = groupsQuery.data?.content ?? []
  const selectedGroup = groups.find((group) => group.groupId === selectedGroupId) ?? groups[0]
  const activeGroupId = selectedGroup?.groupId
  const membersQuery = useQuery({ queryKey: [...GROUP_QUERY_KEY, activeGroupId, 'members', memberPage], queryFn: () => api.getMembers(activeGroupId as number, memberPage, MEMBER_PAGE_SIZE), enabled: activeGroupId !== undefined })
  const auditQuery = useQuery({ queryKey: [...GROUP_QUERY_KEY, activeGroupId, 'audit', auditPage], queryFn: () => api.getAuditLogs(activeGroupId as number, auditPage, AUDIT_PAGE_SIZE), enabled: activeGroupId !== undefined })
  const candidatesQuery = useQuery({ queryKey: [...GROUP_QUERY_KEY, 'candidates', candidateQuery, candidatePage], queryFn: () => api.getMemberCandidates(candidateQuery, candidatePage, CANDIDATE_PAGE_SIZE), enabled: selectedGroup?.status === 'ACTIVE', placeholderData: keepPreviousData })
  const candidate = candidatesQuery.data?.content.find((item) => item.memberId === candidateId)
  usePageBounds(groupPage, groupsQuery.data?.totalPages, setGroupPage)
  usePageBounds(memberPage, membersQuery.data?.totalPages, setMemberPage)
  usePageBounds(auditPage, auditQuery.data?.totalPages, setAuditPage)
  usePageBounds(candidatePage, candidatesQuery.data?.totalPages, setCandidatePage)
  useEffect(() => {
    setMemberPage(0); setAuditPage(0); setCandidatePage(0)
    setCandidateDraft(''); setCandidateQuery(''); setCandidateId(undefined)
    setAddReason(''); setDissolveReason(''); setAddError(''); setDissolveError('')
    setAction(undefined); setDialogErrors({}); setDialogError('')
  }, [activeGroupId])
  useEffect(() => { if (feedback && !action) feedbackRef.current?.focus() }, [feedback, action])
  const refreshGroupData = () => queryClient.invalidateQueries({ queryKey: GROUP_QUERY_KEY })
  const mutation = useMutation({
    retry: false,
    mutationFn: (command: Command) => {
      switch (command.kind) {
        case 'create': return api.createGroup(command.name, command.reason)
        case 'add': return api.addMember(command.groupId, command.memberId, command.reason)
        case 'remove': return api.removeMember(command.groupId, command.memberId, command.reason)
        case 'dissolve': return api.dissolveGroup(command.groupId, command.reason)
      }
    },
    onSuccess: async (_result, command) => {
      if (command.kind === 'create') { setFilters(EMPTY_FILTERS); setDraftFilters(EMPTY_FILTERS); setGroupPage(0); setSelectedGroupId(undefined) }
      setCandidateId(undefined); setAddReason(''); setDissolveReason('')
      await refreshGroupData()
      setAction(undefined)
      setFeedback({ error: false, message: { create: '구성원 0명의 운영 중 가족 그룹을 생성했습니다.', add: '구성원을 추가했습니다. 최신 구성원과 후보, 감사 이력을 확인해 주세요.', remove: '현재 구성원 관계를 종료했습니다. 그룹은 유지되며 기존 예약과 기록은 변경되지 않습니다.', dissolve: '가족 그룹을 해제했습니다. 모든 현재 관계가 종료됐으며 기존 예약과 과거 기록은 유지됩니다.' }[command.kind] })
    },
    onError: async (error) => {
      if (error instanceof ResponseError && error.response.status === 400) {
        const errors: FieldErrors = {}
        try {
          const body: { fieldErrors?: { field: string }[] } = await error.response.clone().json()
          for (const item of body.fieldErrors ?? []) {
            if (item.field === 'name') errors.name = '그룹 이름을 확인해 주세요. 공백만 입력할 수 없으며 최대 100자입니다.'
            if (item.field === 'reason') errors.reason = '사유를 확인해 주세요. 공백만 입력할 수 없으며 최대 500자입니다.'
          }
        } catch { /* Validation responses can omit field details. */ }
        setDialogErrors(errors); setDialogError('입력 내용을 확인한 뒤 다시 진행해 주세요.')
        return
      }
      setAction(undefined); setCandidateId(undefined)
      await refreshGroupData()
      setFeedback({ error: true, message: getFamilyGroupErrorMessage(error, '요청 결과를 확인하지 못했습니다. 최신 목록과 감사 이력을 확인한 뒤 다시 판단해 주세요.') })
    },
  })
  const pending = mutation.isPending
  const openAction = (next: GroupAction) => { setDialogName(''); setDialogReason(''); setDialogErrors({}); setDialogError(''); setAction(next) }
  const confirmAction = () => {
    if (!action || pending) return
    if (action.kind !== 'create' && (action.group.groupId !== activeGroupId || selectedGroup?.status !== 'ACTIVE' || groupsQuery.isFetching)) {
      setAction(undefined); setFeedback({ error: true, message: '선택한 그룹 상태가 변경되었거나 갱신 중입니다. 최신 상태를 확인한 뒤 다시 진행해 주세요.' }); void refreshGroupData(); return
    }
    const reason = action.kind === 'add' || action.kind === 'dissolve' ? action.reason : dialogReason
    const errors: FieldErrors = { reason: requiredError(reason, 500, '사유') }
    if (action.kind === 'create') errors.name = requiredError(dialogName, 100, '그룹 이름')
    setDialogErrors(errors)
    if (errors.reason || errors.name) return
    if (action.kind === 'create') mutation.mutate({ kind: 'create', name: dialogName.trim(), reason: reason.trim() })
    else if (action.kind === 'dissolve') mutation.mutate({ kind: 'dissolve', groupId: action.group.groupId, reason: reason.trim() })
    else mutation.mutate({ kind: action.kind, groupId: action.group.groupId, memberId: action.member.memberId, reason: reason.trim() })
  }
  const applyFilters = (event: FormEvent) => { event.preventDefault(); setAction(undefined); setFilters({ query: draftFilters.query.trim(), status: draftFilters.status }); setGroupPage(0); setSelectedGroupId(undefined) }

  return <main className="admin-family-page">
    <header className="admin-family-header"><div><p className="admin-family-eyebrow">회원 관계 운영</p><h1>가족 그룹 관리</h1><p>신규 예약에서 가족 구성원 소유 쿠폰을 후보로 확인할 수 있도록 현재 구성원 관계를 관리합니다.</p></div><button type="button" className="family-primary" disabled={pending} onClick={() => openAction({ kind: 'create' })}>새 그룹 만들기</button></header>
    {feedback ? <section ref={feedbackRef} tabIndex={-1} className={'admin-family-feedback' + (feedback.error ? ' family-error' : '')} role={feedback.error ? 'alert' : 'status'}><h2>작업 결과</h2><p>{feedback.message}</p></section> : null}
    {groupsQuery.isPending ? <FamilyPageState message="가족 그룹을 불러오는 중입니다." /> : groupsQuery.isError && !groupsQuery.data ? <FamilyPageState error message={getFamilyGroupErrorMessage(groupsQuery.error, '가족 그룹을 불러오지 못했습니다.')} onRetry={() => { void groupsQuery.refetch() }} /> : <>
      {groupsQuery.isError ? <QueryError message={getFamilyGroupErrorMessage(groupsQuery.error, '가족 그룹을 갱신하지 못했습니다.')} onRetry={() => { void groupsQuery.refetch() }} /> : null}
      <div className="admin-family-layout">
        <section className="admin-family-groups" aria-labelledby="family-list-title" aria-busy={groupsQuery.isFetching}>
          <header className="family-pane-head"><h2 id="family-list-title">가족 그룹 목록</h2><p>조회 결과 {groupsQuery.data?.totalElements ?? 0}개 · 페이지당 10건 · 최근 등록 순</p></header>
          <form className="admin-family-search" onSubmit={applyFilters}>
            <label>그룹 이름 검색<input type="search" maxLength={100} value={draftFilters.query} placeholder="그룹 이름 일부 입력" disabled={pending} onChange={(event) => setDraftFilters((current) => ({ ...current, query: event.target.value }))} /></label>
            <label>상태<select disabled={pending} value={draftFilters.status} onChange={(event) => setDraftFilters((current) => ({ ...current, status: event.target.value as FamilyGroupFilters['status'] }))}><option value="">전체</option><option value="ACTIVE">운영 중</option><option value="DISSOLVED">해제됨</option></select></label>
            <button type="submit" disabled={pending || groupsQuery.isFetching}>검색</button>
          </form>
          {groups.length === 0 ? <p className="admin-family-empty">조건에 맞는 가족 그룹이 없습니다.</p> : <ul className="family-group-list">{groups.map((group) => <li key={group.groupId}><button type="button" className="admin-family-group-select" aria-current={group.groupId === activeGroupId} aria-controls="family-group-detail" disabled={pending || groupsQuery.isFetching} onClick={() => setSelectedGroupId(group.groupId)}><strong>{group.name}</strong><small>{statusLabel(group.status)} · 현재 {group.activeMemberCount}명</small><small className="family-group-id">그룹 #{group.groupId}</small>{group.groupId === activeGroupId ? <span className="family-selected-copy">현재 선택</span> : null}</button></li>)}</ul>}
          <Pagination label="가족 그룹 목록" page={groupPage} data={groupsQuery.data} busy={pending || groupsQuery.isFetching} onPageChange={(page) => { setAction(undefined); setGroupPage(page); setSelectedGroupId(undefined) }} />
        </section>
        <div id="family-group-detail" className="admin-family-detail" aria-label="선택한 가족 그룹 상세">
          {selectedGroup ? <>
            <section className="family-summary" aria-labelledby="family-summary-title">
              <header className="admin-family-detail-header"><div><p className="admin-family-eyebrow">선택 그룹</p><h2 id="family-summary-title">{selectedGroup.name}</h2></div><span className={'admin-family-status admin-family-status-' + selectedGroup.status.toLowerCase()}>{statusLabel(selectedGroup.status)}</span></header>
              <dl className="family-summary-grid"><div><dt>현재 구성원</dt><dd>{selectedGroup.activeMemberCount}명</dd></div><div><dt>그룹 상태</dt><dd>{statusLabel(selectedGroup.status)}</dd></div></dl>
              <div className="family-coupon-note"><strong>쿠폰 소유권은 바뀌지 않습니다.</strong><p>같은 운영 중 그룹 구성원이 소유한 적합한 쿠폰이 신규 예약의 선택 후보가 될 수 있습니다. 가입 전 발급 쿠폰도 서버 조건에 맞으면 후보가 되며, 기존 예약과 쿠폰 사용 기록은 변경되지 않습니다.</p></div>
              <details className="family-metadata"><summary>그룹 식별 정보·생성·해제 시각 보기</summary><dl><div><dt>그룹 번호</dt><dd>#{selectedGroup.groupId}</dd></div><div><dt>생성 시각</dt><dd>{formatFamilyDateTime(selectedGroup.createdAt)}</dd></div>{selectedGroup.dissolvedAt ? <div><dt>해제 시각</dt><dd>{formatFamilyDateTime(selectedGroup.dissolvedAt)}</dd></div> : null}</dl></details>
            </section>
            <FamilySection title="현재 구성원" description="현재 관계가 유지 중인 회원입니다. 구성원마다 별도 가족 역할은 없습니다.">
              <QueryContent query={membersQuery} loading="구성원을 불러오는 중입니다." error="구성원을 불러오지 못했습니다.">{(members) => members.length === 0 ? <p className="admin-family-empty">{selectedGroup.status === 'ACTIVE' ? '현재 구성원이 없습니다. 빈 운영 중 그룹도 정상적으로 유지됩니다.' : '해제된 그룹에는 현재 구성원이 없습니다. 과거 기록은 유지됩니다.'}</p> : <ul className="admin-family-member-list">{members.map((member) => <li key={member.membershipId}><div><strong>{member.name}</strong><span>{member.phone}</span><span>가입 {formatFamilyDateTime(member.joinedAt)}</span></div>{selectedGroup.status === 'ACTIVE' ? <button className="family-danger" type="button" disabled={pending || membersQuery.isFetching || groupsQuery.isFetching} onClick={() => openAction({ kind: 'remove', group: selectedGroup, member })}>제거</button> : null}</li>)}</ul>}</QueryContent>
              <Pagination label="가족 구성원" page={memberPage} data={membersQuery.data} busy={pending || membersQuery.isFetching} onPageChange={setMemberPage} />
            </FamilySection>
            {selectedGroup.status === 'ACTIVE' ? <FamilySection title="구성원 추가" description="현재 어느 가족 그룹에도 속하지 않은 회원을 찾습니다.">
              <form className="admin-family-candidate-search" onSubmit={(event) => { event.preventDefault(); setCandidateId(undefined); setAddReason(''); setAddError(''); setCandidateQuery(candidateDraft.trim()); setCandidatePage(0) }}>
                <label>미배정 회원 검색<input type="search" maxLength={100} value={candidateDraft} aria-describedby="family-candidate-help" disabled={pending} onChange={(event) => setCandidateDraft(event.target.value)} placeholder="이름 또는 전화번호" /></label><button type="submit" disabled={pending || candidatesQuery.isFetching}>후보 검색</button>
              </form><p id="family-candidate-help" className="family-help">후보 조회 이후 소속이 바뀔 수 있습니다. 최종 소속 여부는 처리 시 서버가 다시 확인합니다.</p>
              <QueryContent query={candidatesQuery} loading="추가 가능한 회원을 불러오는 중입니다." error="추가 가능한 회원을 불러오지 못했습니다.">{(candidates) => candidates.length === 0 ? <p className="admin-family-empty">추가 가능한 회원이 없습니다.</p> : <ul className="admin-family-candidate-list">{candidates.map((item) => <li key={item.memberId}><button type="button" aria-pressed={item.memberId === candidateId} disabled={pending || candidatesQuery.isFetching} onClick={() => { setCandidateId(item.memberId); setAddReason(''); setAddError(''); setAction(undefined) }}><strong>{item.name}</strong><small>{item.phone}</small></button></li>)}</ul>}</QueryContent>
              <Pagination label="추가 가능 회원" page={candidatePage} data={candidatesQuery.data} busy={pending || candidatesQuery.isFetching} onPageChange={(page) => { setCandidateId(undefined); setAddReason(''); setAddError(''); setCandidatePage(page) }} />
              <p className="family-help">{candidate ? '선택한 회원: ' + candidate.name + ' · ' + candidate.phone : '추가할 회원 한 명을 선택해 주세요.'}</p>
              <form className="family-add-form" noValidate onSubmit={(event) => { event.preventDefault(); const error = requiredError(addReason, 500, '추가 사유'); setAddError(error ?? ''); if (!error && candidate) openAction({ kind: 'add', group: selectedGroup, member: candidate, reason: addReason.trim() }) }}>
                <ReasonField id="family-add-reason" label="추가 사유" value={addReason} error={addError} disabled={pending} onChange={(value) => { setAddReason(value); setAddError('') }} />
                <button type="submit" disabled={pending || !candidate || candidatesQuery.isFetching || groupsQuery.isFetching}>구성원 추가 확인</button>
              </form>
            </FamilySection> : null}
            <FamilySection title="변경 감사 이력" description="그룹 작업과 관계 변경, 사유와 시점을 확인합니다. 페이지당 8건.">
              <QueryContent query={auditQuery} loading="감사 이력을 불러오는 중입니다." error="감사 이력을 불러오지 못했습니다.">{(audits) => audits.length === 0 ? <p className="admin-family-empty">감사 이력이 없습니다.</p> : <ol className="admin-family-audit-list">{audits.map((audit) => <AuditItem key={audit.auditId} audit={audit} />)}</ol>}</QueryContent>
              <Pagination label="가족 변경 감사" page={auditPage} data={auditQuery.data} busy={pending || auditQuery.isFetching} onPageChange={setAuditPage} />
            </FamilySection>
            {selectedGroup.status === 'ACTIVE' ? <section className="admin-family-dissolve" aria-labelledby="family-dissolve-title"><h2 id="family-dissolve-title">그룹 해제</h2><p>모든 현재 구성원 관계를 종료합니다. 해제된 그룹은 다시 운영할 수 없으며 기존 예약과 과거 기록은 유지됩니다.</p><form noValidate onSubmit={(event) => { event.preventDefault(); const error = requiredError(dissolveReason, 500, '해제 사유'); setDissolveError(error ?? ''); if (!error) openAction({ kind: 'dissolve', group: selectedGroup, reason: dissolveReason.trim() }) }}><ReasonField id="family-dissolve-reason" label="해제 사유" value={dissolveReason} error={dissolveError} disabled={pending} onChange={(value) => { setDissolveReason(value); setDissolveError('') }} /><button className="family-danger" type="submit" disabled={pending || groupsQuery.isFetching}>그룹 해제 확인</button></form></section> : null}
          </> : <p className="admin-family-empty">가족 그룹을 선택하거나 새 그룹을 만들어 주세요.</p>}
        </div>
      </div>
    </>}
    {action ? <FamilyGroupActionDialog title={actionTitle(action.kind)} confirmLabel={confirmLabel(action.kind)} pending={pending} onClose={() => setAction(undefined)} onConfirm={confirmAction}>
      <p className="family-dialog-description">서버 계산 미리보기가 아닌 작업 내용 확인입니다.</p>
      {action.kind !== 'create' ? <dl className="family-dialog-facts"><div><dt>대상 그룹</dt><dd>{action.group.name}</dd></div><div><dt>그룹 번호</dt><dd>#{action.group.groupId}</dd></div><div><dt>현재 상태</dt><dd>{statusLabel(action.group.status)}</dd></div><div><dt>현재 구성원</dt><dd>{action.group.activeMemberCount}명</dd></div>{action.kind === 'add' || action.kind === 'remove' ? <div><dt>대상 회원</dt><dd>{action.member.name} · {action.member.phone}</dd></div> : null}</dl> : null}
      {action.kind === 'create' ? <><label className="family-name-field" htmlFor="family-create-name">그룹 이름<input id="family-create-name" maxLength={100} value={dialogName} aria-invalid={Boolean(dialogErrors.name)} aria-describedby="family-name-help family-name-error" disabled={pending} onChange={(event) => { setDialogName(event.target.value); setDialogErrors((current) => ({ ...current, name: undefined })) }} /></label><p id="family-name-help" className="family-help">필수 · 공백만 입력 불가 · 최대 100자 · 같은 이름 허용</p><p id="family-name-error" className="family-field-error">{dialogErrors.name}</p><p>구성원 0명의 운영 중 그룹을 만듭니다. 대표자나 최초 회원은 필요하지 않습니다.</p></> : null}
      {action.kind === 'create' || action.kind === 'remove' ? <ReasonField id="family-dialog-reason" label={action.kind === 'create' ? '생성 사유' : '제거 사유'} value={dialogReason} error={dialogErrors.reason} disabled={pending} onChange={(value) => { setDialogReason(value); setDialogErrors((current) => ({ ...current, reason: undefined })) }} /> : <ReasonField id="family-confirm-reason" label="확인 사유" value={action.reason} error={dialogErrors.reason} disabled={pending} onChange={(reason) => { setAction({ ...action, reason }); setDialogErrors((current) => ({ ...current, reason: undefined })) }} />}
      {action.kind === 'remove' ? <p>현재 관계만 종료합니다. 마지막 구성원을 제거해도 그룹은 운영 중으로 유지됩니다. 기존 예약과 쿠폰 사용 기록은 변경되지 않습니다. 재추가는 새로운 관계 생성입니다.</p> : null}
      {action.kind === 'add' ? <p>선택한 회원 한 명을 추가합니다. 이미 다른 가족 그룹에 소속되어 있다면 거절되며 자동으로 이동시키지 않습니다.</p> : null}
      {action.kind === 'dissolve' ? <p className="family-warning">모든 현재 관계가 한 번에 종료됩니다. 해제는 복구할 수 없으며 기존 예약과 쿠폰 사용 기록은 유지됩니다.</p> : null}
      {dialogError ? <p role="alert" className="family-field-error">{dialogError}</p> : null}
    </FamilyGroupActionDialog> : null}
  </main>
}

function FamilySection({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return <section className="admin-family-section" aria-label={title}><header><h2>{title}</h2><p>{description}</p></header>{children}</section>
}
function ReasonField({ id, label, value, error, disabled, onChange }: { id: string; label: string; value: string; error?: string; disabled: boolean; onChange(value: string): void }) {
  return <div className="family-reason-field"><label htmlFor={id}>{label}</label><textarea id={id} maxLength={500} value={value} disabled={disabled} aria-invalid={Boolean(error)} aria-describedby={id + '-help ' + id + '-error'} onChange={(event) => onChange(event.target.value)} /><p className="family-help" id={id + '-help'}>필수 · 공백만 입력 불가 · 최대 500자</p><p className="family-field-error" id={id + '-error'}>{error}</p></div>
}
function QueryContent<T>({ query, loading, error, children }: { query: UseQueryResult<PageResponse<T>, Error>; loading: string; error: string; children(items: T[]): ReactNode }) {
  if (query.isPending) return <p className="admin-family-empty" role="status">{loading}</p>
  if (query.isError) return <QueryError message={getFamilyGroupErrorMessage(query.error, error)} onRetry={() => { void query.refetch() }} />
  return <div aria-busy={query.isFetching}>{children(query.data.content)}</div>
}
function Pagination({ label, page, data, busy, onPageChange }: { label: string; page: number; data?: { totalPages: number; hasNext: boolean }; busy: boolean; onPageChange(page: number): void }) {
  if (!data?.totalPages) return null
  return <nav className="admin-family-pagination" aria-label={label + ' 페이지'}><button type="button" disabled={page === 0 || busy} onClick={() => onPageChange(page - 1)}>이전</button><span aria-live="polite">{page + 1} / {data.totalPages} 페이지</span><button type="button" disabled={!data.hasNext || page + 1 >= data.totalPages || busy} onClick={() => onPageChange(page + 1)}>다음</button></nav>
}
function AuditItem({ audit }: { audit: FamilyGroupAuditLog }) {
  return <li><div><strong>{familyAuditLabel(audit.action)}</strong><time dateTime={audit.occurredAt.toISOString()}>{formatFamilyDateTime(audit.occurredAt)}</time></div>{audit.memberName ? <p>대상 회원: {audit.memberName}</p> : null}<p className="family-audit-change">{formatFamilyAuditState(audit.fromState, audit.action)} → {formatFamilyAuditState(audit.toState, audit.action)}</p><p className="family-help">사유: {audit.reason}</p></li>
}
function FamilyPageState({ message, error = false, onRetry }: { message: string; error?: boolean; onRetry?: () => void }) { return <section className="admin-family-page-state" role={error ? 'alert' : 'status'}><p>{message}</p>{onRetry ? <button type="button" onClick={onRetry}>다시 시도</button> : null}</section> }
function QueryError({ message, onRetry }: { message: string; onRetry(): void }) { return <div className="admin-family-query-error" role="alert"><p>{message}</p><button type="button" onClick={onRetry}>다시 시도</button></div> }
function statusLabel(status: FamilyGroupSummary['status']) { return status === 'ACTIVE' ? '운영 중' : '해제됨' }
function requiredError(value: string, max: number, label: string) { return !value.trim() || value.length > max ? label + '를 입력해 주세요. 공백만 입력할 수 없으며 최대 ' + max + '자입니다.' : undefined }
function actionTitle(kind: GroupAction['kind']) { return { create: '새 가족 그룹 생성', add: '구성원 추가 확인', remove: '구성원 제거 확인', dissolve: '가족 그룹 해제 확인' }[kind] }
function confirmLabel(kind: GroupAction['kind']) { return { create: '그룹 생성', add: '구성원 추가', remove: '제거 확인', dissolve: '가족 그룹 해제' }[kind] }
function usePageBounds(page: number, totalPages: number | undefined, setPage: (page: number) => void) { useEffect(() => { if (totalPages === undefined) return; if (totalPages === 0 && page !== 0) setPage(0); else if (totalPages > 0 && page >= totalPages) setPage(totalPages - 1) }, [page, setPage, totalPages]) }
