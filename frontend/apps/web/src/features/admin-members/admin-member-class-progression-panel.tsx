import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  getAdminMembersErrorCode,
  getAdminMembersErrorKind,
  type AdminMemberProgressionResponse,
  type AdminMembersApi,
  type GeneralRidingGrade,
  type MemberClassProgressionAuditAction,
  type MemberClassProgressionPreviewAction,
  type MemberClassProgressionPreviewRequest,
  type MemberClassProgressionPreviewResponse,
} from './admin-members.api'

import { MemberChangeDialog } from './member-change-dialog'
import { GENERAL_GRADES, gradeLabel, formatDateTime, formatCount } from './member-formatters'

const AUDIT_PAGE_SIZE = 5

const ACTION_LABELS: Record<MemberClassProgressionPreviewAction, string> = {
  SET_BASELINE: '인정 시작 클래스 설정·변경',
  REMOVE_BASELINE: '인정 시작 클래스 해제',
  SET_PROMOTION_HOLD: '자동 승급 보류 설정·변경',
  REMOVE_PROMOTION_HOLD: '자동 승급 보류 해제',
  CORRECT_SPECIAL_APPROVAL_CREDIT: '특수 승인 추가 인정 횟수 교정',
  ADJUST_RIDE_COUNT: '실제 일반 기승 횟수 보정',
}

const AUDIT_LABELS: Record<MemberClassProgressionAuditAction, string> = {
  PROGRESSION_INITIALIZED: '클래스 승급 관리 시작',
  BASELINE_SET: '인정 시작 클래스 설정',
  BASELINE_CHANGED: '인정 시작 클래스 변경',
  BASELINE_REMOVED: '인정 시작 클래스 해제',
  SPECIAL_APPROVAL_CHANGED: '특수 승인 변경',
  SPECIAL_APPROVAL_CREDIT_CORRECTED: '특수 승인 추가 인정 횟수 교정',
  RIDE_COUNT_ADJUSTED: '실제 일반 기승 횟수 보정',
  PROMOTION_HOLD_SET: '자동 승급 보류 설정',
  PROMOTION_HOLD_CHANGED: '자동 승급 보류 변경',
  PROMOTION_HOLD_REMOVED: '자동 승급 보류 해제',
}

interface AdminMemberClassProgressionPanelProps {
  member: AdminMemberProgressionResponse
  api: AdminMembersApi
  onMemberUpdated(member: AdminMemberProgressionResponse): void
}

export function AdminMemberClassProgressionPanel({ member, api, onMemberUpdated }: AdminMemberClassProgressionPanelProps) {
  const queryClient = useQueryClient()
  const [action, setAction] = useState<MemberClassProgressionPreviewAction>('SET_BASELINE')
  const [grade, setGrade] = useState<GeneralRidingGrade>(member.progressionClass ?? 'FIRST_RIDE')
  const [numericValue, setNumericValue] = useState('1')
  const [reason, setReason] = useState('')
  const [preview, setPreview] = useState<MemberClassProgressionPreviewResponse>()
  const [localError, setLocalError] = useState<string>()
  const [fieldError, setFieldError] = useState<string>()
  const [reasonError, setReasonError] = useState<string>()
  const [successMessage, setSuccessMessage] = useState<string>()
  const [auditPage, setAuditPage] = useState(0)
  const [confirmation, setConfirmation] = useState(false)
  const generation = useRef(0)
  const locked = useRef(false)
  const feedback = useRef<HTMLParagraphElement>(null)
  const hasSpecialApproval = member.dressageApproved || member.jumpingApproved
  const managementStartedAt = member.progressionManagementStartedAt?.valueOf()
  useEffect(() => {
    generation.current++
    setPreview(undefined)
    setConfirmation(false)
    setLocalError(undefined)
  }, [member.id, member.dressageApproved, member.generalRideCount, member.jumpingApproved, member.progressionBaselineClass, member.progressionBaselineThreshold, member.progressionBaselineActualRideCount, managementStartedAt, member.progressionValue, member.progressionClass, member.effectiveClass, member.promotionHoldClass, member.specialApprovalProgressionCredit])
  const auditQuery = useQuery({
    queryKey: ['admin', 'members', member.id, 'progression-audit', auditPage],
    queryFn: () => api.getProgressionAuditLogs(member.id, auditPage, AUDIT_PAGE_SIZE),
  })
  const previewMutation = useMutation({
    retry: false,
    mutationFn: async (request: { value: MemberClassProgressionPreviewRequest; generation: number }) => ({ value: await api.previewProgression(member.id, request.value), generation: request.generation }),
    onSuccess: (result) => {
      if (result.generation !== generation.current) return
      setPreview(result.value)
      setLocalError(undefined)
      setSuccessMessage(undefined)
    },
    onError: async (error, request) => {
      const message = await progressionErrorMessage(error, 'preview')
      if (request.generation !== generation.current) return
      setPreview(undefined)
      setLocalError(message)
    },
  })
  const commandMutation = useMutation({
    retry: false,
    mutationFn: async () => {
      if (!preview || isActionDisabled(action, member, hasSpecialApproval)) throw new Error('예상 결과를 다시 확인해 주세요.')
      return executeCommand(api, member.id, action, grade, numericValue, reason.trim(), preview.stateToken)
    },
    onSuccess: async (updatedMember) => {
      onMemberUpdated(updatedMember)
      setPreview(undefined)
      setReason('')
      setLocalError(undefined)
      setSuccessMessage(`${ACTION_LABELS[action]} 처리가 완료됐습니다.`)
      await queryClient.invalidateQueries({ queryKey: ['admin', 'members', member.id, 'progression-audit'] })
    },
    onError: async (error) => {
      const result = await progressionErrorMessage(error, 'command')
      setPreview(undefined)
      await queryClient.invalidateQueries({ queryKey: ['admin', 'members', member.id], exact: true })
      await queryClient.invalidateQueries({ queryKey: ['admin', 'member-list'] })
      setLocalError(result.message)
    },
    onSettled: () => { locked.current = false; setConfirmation(false); requestAnimationFrame(() => feedback.current?.focus()) },
  })
  const resetPreview = () => {
    generation.current++
    setPreview(undefined)
    setConfirmation(false)
    setFieldError(undefined)
    setReasonError(undefined)
    setLocalError(undefined)
    setSuccessMessage(undefined)
    previewMutation.reset()
    commandMutation.reset()
  }
  const progressionDataAvailable = hasProgressionSnapshot(member)
  const actionDisabled = !progressionDataAvailable || isActionDisabled(action, member, hasSpecialApproval)
  const busy = previewMutation.isPending || commandMutation.isPending
  const previewChange = (event: FormEvent) => {
    event.preventDefault()
    if (busy || actionDisabled) return
    try {
      const request = buildPreviewRequest(action, grade, numericValue)
      if (action === 'CORRECT_SPECIAL_APPROVAL_CREDIT' && (Number(numericValue) < 0 || Number(numericValue) > member.specialApprovalProgressionCredit)) throw new Error('추가 인정 횟수는 0 이상이며 기존 인정 횟수 이하로 입력해 주세요.')
      setLocalError(undefined)
      setFieldError(undefined)
      previewMutation.mutate({ value: request, generation: generation.current })
    } catch (error) { setPreview(undefined); setFieldError(error instanceof Error ? error.message : '입력값을 확인해 주세요.') }
  }
  const review = () => {
    if (!preview || busy || actionDisabled) return
    if (!reason.trim() || reason.trim().length > 500) { setReasonError('관리자 사유는 공백을 제외하고 1~500자로 입력해 주세요.'); document.getElementById('member-general-reason')?.focus(); return }
    setReasonError(undefined)
    setConfirmation(true)
  }
  return <>
    <section className="admin-member-progression" aria-labelledby="member-progression-title">
      <div className="admin-member-section-heading"><div><h3 id="member-progression-title">일반 클래스 승급 관리</h3><p>작업을 선택하고 변경 전·후 결과를 확인한 뒤 적용합니다.</p></div></div>
      <p className="admin-member-policy-note">실제 기승 횟수 보정은 Horse에서 누락되거나 중복 집계된 일반 기승 기록만 바로잡습니다. 특수 승인과 자동 승급 보류는 함께 사용할 수 없습니다.</p>
      <form className="admin-member-progression-form" onSubmit={previewChange} noValidate>
        <label htmlFor="member-general-action">변경 항목</label>
        <select id="member-general-action" value={action} disabled={busy || !progressionDataAvailable} onChange={(event) => { setAction(event.target.value as MemberClassProgressionPreviewAction); resetPreview() }}>
          {(Object.keys(ACTION_LABELS) as MemberClassProgressionPreviewAction[]).map((candidate) => <option key={candidate} value={candidate} disabled={!progressionDataAvailable || isActionDisabled(candidate, member, hasSpecialApproval)}>{ACTION_LABELS[candidate]}</option>)}
        </select>
        {action === 'SET_BASELINE' || action === 'SET_PROMOTION_HOLD' ? <div className="admin-member-field">
          <label htmlFor="member-general-grade">{action === 'SET_BASELINE' ? '인정 시작 클래스' : '보류할 최고 클래스'}</label>
          <select id="member-general-grade" value={grade} disabled={busy || !progressionDataAvailable} onChange={(event) => { setGrade(event.target.value as GeneralRidingGrade); resetPreview() }}>{GENERAL_GRADES.map((candidate) => <option key={candidate.value} value={candidate.value}>{candidate.label}</option>)}</select>
        </div> : null}
        {action === 'CORRECT_SPECIAL_APPROVAL_CREDIT' || action === 'ADJUST_RIDE_COUNT' ? <div className="admin-member-field">
          <label htmlFor="member-general-value">{action === 'CORRECT_SPECIAL_APPROVAL_CREDIT' ? '교정 후 추가 인정 횟수' : '기승 횟수 증감'}</label>
          <input id="member-general-value" type="number" step="1" min={action === 'CORRECT_SPECIAL_APPROVAL_CREDIT' ? 0 : undefined} max={action === 'CORRECT_SPECIAL_APPROVAL_CREDIT' ? member.specialApprovalProgressionCredit : undefined} value={numericValue} disabled={busy || !progressionDataAvailable} aria-invalid={Boolean(fieldError)} aria-describedby="member-general-value-error" onChange={(event) => { setNumericValue(event.target.value); resetPreview() }} />
        </div> : null}
        <p id="member-general-value-error" className="admin-member-inline-error" role={fieldError ? 'alert' : undefined}>{fieldError}</p>
        {actionDisabled ? <p className="admin-member-progression-guidance" id="member-progression-apply-guidance" role="status">{disabledActionGuidance(member, hasSpecialApproval, progressionDataAvailable)}</p> : <p className="admin-member-help" id="member-progression-apply-guidance">{preview ? reason.trim() ? '예상 결과를 확인했습니다. 변경 내용을 확인한 뒤 적용해 주세요.' : '예상 결과를 확인했습니다. 관리자 사유를 입력하면 변경을 적용할 수 있습니다.' : '먼저 변경 결과를 미리 본 뒤 적용할 수 있습니다.'}</p>}
        <button type="submit" disabled={busy || actionDisabled}>{previewMutation.isPending ? '결과 확인 중' : '변경 결과 미리보기'}</button>
      </form>
      {preview ? <ProgressionPreview preview={preview} /> : <div className="admin-member-progression-preview"><h4>변경 전·후 확인</h4><p>미리보기를 실행하면 현재 값과 변경 후 예상 결과가 표시됩니다.</p></div>}
      <div className="admin-member-reason-area">
        <label htmlFor="member-general-reason">관리자 사유</label><textarea id="member-general-reason" maxLength={500} value={reason} disabled={busy || !progressionDataAvailable} aria-invalid={Boolean(reasonError)} aria-describedby="member-general-reason-help member-general-reason-error" onChange={(event) => { setReason(event.target.value); setReasonError(undefined) }} />
        <p className="admin-member-help" id="member-general-reason-help">미리보기 확인 후 입력 · 필수 · 공백 제외 1~500자</p><p id="member-general-reason-error" className="admin-member-inline-error" role={reasonError ? 'alert' : undefined}>{reasonError}</p>
        <button type="button" className="admin-member-primary-action" aria-describedby="member-progression-apply-guidance" disabled={busy || actionDisabled || !preview} onClick={review}>변경 내용 확인</button>
      </div>
      {localError || successMessage ? <p ref={feedback} tabIndex={-1} className={localError ? 'admin-member-inline-error' : 'admin-member-success'} role={localError ? 'alert' : 'status'}>{localError ?? successMessage}</p> : null}
      {confirmation && preview ? <MemberChangeDialog title={`${ACTION_LABELS[action]} 확인`} pending={commandMutation.isPending} onClose={() => setConfirmation(false)} onConfirm={() => { if (!locked.current && preview && !actionDisabled) { locked.current = true; commandMutation.mutate() } }}>
        <dl className="admin-member-confirm-facts"><div><dt>회원</dt><dd>{member.name} · {member.phone}</dd></div><div><dt>작업</dt><dd>{ACTION_LABELS[action]}</dd></div><div><dt>사유</dt><dd>{reason.trim()}</dd></div></dl>
        <ProgressionPreview preview={preview} />
        <p>최종 결과는 서버 검증을 따릅니다. 적용 후 최신 회원 상태와 감사 이력을 확인해 주세요.</p>
      </MemberChangeDialog> : null}
    </section>
    <section className="admin-member-progression-audit" aria-labelledby="member-progression-audit-title">
      <div className="admin-member-section-heading"><div><h3 id="member-progression-audit-title">변경 감사 이력</h3><p>변경 내용·사유·시점 · 페이지당 5건</p></div><span>총 {auditQuery.data?.totalElements ?? 0}건</span></div>
      {auditQuery.isPending ? <p role="status">감사 이력을 불러오는 중입니다.</p> : auditQuery.isError ? <div role="alert"><p>감사 이력을 불러오지 못했습니다.</p><button type="button" onClick={() => { void auditQuery.refetch() }}>다시 불러오기</button></div> : auditQuery.data?.content.length === 0 ? <p>기록된 변경 감사 이력이 없습니다.</p> : <ol className="admin-member-progression-audit-list">{auditQuery.data?.content.map((audit) => <li key={audit.auditId}>
        <div className="admin-member-audit-top"><strong>{AUDIT_LABELS[audit.action]}</strong><time dateTime={audit.occurredAt.toISOString()}>{formatDateTime(audit.occurredAt)}</time></div><p>{audit.reason}</p>
        <details><summary>전후 상태 보기</summary><div className="admin-member-audit-comparison"><section><h4>변경 전</h4><AuditState state={audit.fromState} /></section><section><h4>변경 후</h4><AuditState state={audit.toState} /></section></div></details>
      </li>)}</ol>}
      {(auditQuery.data?.totalPages ?? 0) > 0 ? <nav className="admin-member-progression-pagination" aria-label="회원 클래스 변경 감사 페이지"><button type="button" disabled={auditPage === 0 || auditQuery.isFetching} onClick={() => setAuditPage((value) => value - 1)}>이전</button><span>{auditPage + 1} / {auditQuery.data?.totalPages ?? 1} 페이지</span><button type="button" disabled={!auditQuery.data?.hasNext || auditQuery.isFetching} onClick={() => setAuditPage((value) => value + 1)}>다음</button></nav> : null}
    </section>
  </>
}

function ProgressionPreview({ preview }: { preview: MemberClassProgressionPreviewResponse }) {
  return (
    <section className="admin-member-progression-preview" aria-live="polite" aria-label="변경 전후 예상 클래스">
      <h4>변경 전후 예상 결과</h4>
      <div>
        <ProgressionProjection title="변경 전" projection={preview.current} />
        <span aria-hidden="true">→</span>
        <ProgressionProjection title="변경 후" projection={preview.expected} />
      </div>
    </section>
  )
}

function ProgressionProjection({
  title,
  projection,
}: {
  title: string
  projection: MemberClassProgressionPreviewResponse['current']
}) {
  return (
    <dl>
      <div><dt>{title} 적용 클래스</dt><dd>{gradeLabel(projection.effectiveClass)}</dd></div>
      <div><dt>승급 산정</dt><dd>{formatCount(projection.progressionValue)} · {gradeLabel(projection.progressionClass)}</dd></div>
      <div><dt>인정 시작 클래스</dt><dd>{projection.baselineClass ? gradeLabel(projection.baselineClass) : '설정 없음'}</dd></div>
      <div><dt>보류 상한</dt><dd>{projection.promotionHoldClass ? gradeLabel(projection.promotionHoldClass) : '설정 없음'}</dd></div>
      <div><dt>실제 일반 기승 횟수</dt><dd>{formatCount(projection.actualCompletedRideCount)}</dd></div>
      <div><dt>특수 승인 추가 인정 횟수</dt><dd>{formatCount(projection.specialApprovalProgressionCredit)}</dd></div>
    </dl>
  )
}

function buildPreviewRequest(
  action: MemberClassProgressionPreviewAction,
  grade: GeneralRidingGrade,
  numericValue: string,
): MemberClassProgressionPreviewRequest {
  if (action === 'SET_BASELINE') return { action, baselineClass: grade }
  if (action === 'SET_PROMOTION_HOLD') return { action, promotionHoldClass: grade }
  if (action === 'CORRECT_SPECIAL_APPROVAL_CREDIT') {
    return { action, specialApprovalProgressionCredit: integerValue(numericValue) }
  }
  if (action === 'ADJUST_RIDE_COUNT') {
    const rideCountDelta = integerValue(numericValue)
    if (rideCountDelta === 0) throw new Error('기승 횟수 증감은 0이 아닌 정수여야 합니다.')
    return { action, rideCountDelta }
  }
  return { action }
}

async function executeCommand(
  api: AdminMembersApi,
  memberId: number,
  action: MemberClassProgressionPreviewAction,
  grade: GeneralRidingGrade,
  numericValue: string,
  reason: string,
  stateToken: string,
) {
  const request = buildPreviewRequest(action, grade, numericValue)
  switch (action) {
    case 'SET_BASELINE':
      return api.setProgressionBaseline(
        memberId,
        request.baselineClass as GeneralRidingGrade,
        reason,
        stateToken,
      )
    case 'REMOVE_BASELINE':
      return api.removeProgressionBaseline(memberId, reason, stateToken)
    case 'SET_PROMOTION_HOLD':
      return api.setPromotionHold(
        memberId,
        request.promotionHoldClass as GeneralRidingGrade,
        reason,
        stateToken,
      )
    case 'REMOVE_PROMOTION_HOLD':
      return api.removePromotionHold(memberId, reason, stateToken)
    case 'CORRECT_SPECIAL_APPROVAL_CREDIT':
      return api.correctSpecialApprovalCredit(
        memberId,
        request.specialApprovalProgressionCredit as number,
        reason,
        stateToken,
      )
    case 'ADJUST_RIDE_COUNT':
      return api.adjustRideCount(memberId, request.rideCountDelta as number, reason, stateToken)
  }
}

function isActionDisabled(
  action: MemberClassProgressionPreviewAction,
  member: AdminMemberProgressionResponse,
  hasSpecialApproval: boolean,
) {
  if (member.progressionManagementStartedAt === null) return action !== 'SET_BASELINE'
  if (action === 'REMOVE_BASELINE') return member.progressionBaselineClass === null
  if (action === 'REMOVE_PROMOTION_HOLD') return member.promotionHoldClass === null
  if (action === 'SET_PROMOTION_HOLD' || action === 'CORRECT_SPECIAL_APPROVAL_CREDIT') {
    return hasSpecialApproval
  }
  return false
}

function disabledActionGuidance(
  member: AdminMemberProgressionResponse,
  hasSpecialApproval: boolean,
  progressionDataAvailable: boolean,
) {
  if (!progressionDataAvailable) {
    return '회원 클래스 관리 정보를 불러오지 못해 변경할 수 없습니다. 잠시 후 화면을 새로고침해 주세요.'
  }
  if (member.progressionManagementStartedAt === null) {
    return '처음에는 인정 시작 클래스를 먼저 설정해야 다른 승급 관리 작업을 할 수 있습니다.'
  }
  if (hasSpecialApproval) {
    return '특수 승인을 먼저 명시적으로 해제해야 이 작업을 수행할 수 있습니다.'
  }
  return '현재 설정된 값이 없어 해제할 수 없습니다.'
}

function integerValue(value: string) {
  if (!value.trim()) throw new Error('값은 정수로 입력해 주세요.')
  const parsed = Number(value)
  if (!Number.isInteger(parsed)) throw new Error('값은 정수로 입력해 주세요.')
  return parsed
}

async function progressionErrorMessage(error: unknown, target: 'preview'): Promise<string>
async function progressionErrorMessage(
  error: unknown,
  target: 'command',
): Promise<{ message: string; staleState: boolean }>
async function progressionErrorMessage(error: unknown, target: 'preview' | 'command') {
  const kind = getAdminMembersErrorKind(error)
  const code = await getAdminMembersErrorCode(error)
  const staleState = code === 'MEMBER_CLASS_STATE_CONFLICT'
  let message: string
  if (staleState) {
    message = '다른 변경이 반영됐습니다. 현재 상태에서 예상 결과를 다시 확인해 주세요.'
  } else if (kind === 'conflict') {
    message = '특수 승인, 자동 승급 보류 상태 또는 클래스 승급 조건을 먼저 확인해 주세요.'
  } else if (kind === 'validation') {
    message = '입력한 클래스나 보정 값을 다시 확인해 주세요.'
  } else if (kind === 'forbidden') {
    message = '관리자 권한이 없어 처리할 수 없습니다.'
  } else {
    message = target === 'preview'
    ? '예상 결과를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.'
    : '변경을 적용하지 못했습니다. 현재 상태를 다시 확인해 주세요.'
  }
  return target === 'preview' ? message : { message, staleState }
}

function hasProgressionSnapshot(member: AdminMemberProgressionResponse) {
  return Number.isFinite(member.generalRideCount)
    && Number.isFinite(member.progressionValue)
    && Number.isFinite(member.specialApprovalProgressionCredit)
    && member.progressionClass !== undefined
    && member.effectiveClass !== undefined
}

function AuditState({ state }: { state: object | null }) {
  if (!state) return <span>없음</span>
  return (
    <ul>
      {auditStateEntries(state).map(([label, value]) => (
        <li key={label}><span>{label}</span><strong>{value}</strong></li>
      ))}
    </ul>
  )
}

function auditStateEntries(state: object): Array<[string, string]> {
  const values = state as Record<string, unknown>
  const entries: Array<[string, string]> = []
  addNumber(entries, values, 'actualCompletedRideCount', '실제 일반 기승 횟수', '회')
  addNumber(entries, values, 'rideCountDelta', '기승 횟수 증감', '회')
  addNumber(entries, values, 'progressionValue', '승급 산정 횟수', '회')
  addGrade(entries, values, 'progressionClass', '자동 산정 클래스')
  addGrade(entries, values, 'effectiveClass', '현재 적용 클래스')
  addGrade(entries, values, 'baselineClass', '인정 시작 클래스')
  addNumber(entries, values, 'baselineThreshold', '인정 시작 기준 횟수', '회')
  addNumber(entries, values, 'baselineActualRideCount', '인정 시점 실제 기승', '회')
  addNumber(entries, values, 'specialApprovalProgressionCredit', '특수 승인 추가 인정 횟수', '회')
  addGrade(entries, values, 'promotionHoldClass', '자동 승급 보류 클래스')
  for (const [key, label] of [['dressageApproved', '마장마술 승인'], ['jumpingApproved', '장애물 승인']]) {
    if (typeof values[key] === 'boolean') entries.push([label, values[key] ? '승인' : '미승인'])
  }
  return entries
}

function addNumber(
  entries: Array<[string, string]>,
  state: Record<string, unknown>,
  key: string,
  label: string,
  suffix = '',
) {
  if (typeof state[key] === 'number') entries.push([label, `${state[key]}${suffix}`])
}

function addGrade(
  entries: Array<[string, string]>,
  state: Record<string, unknown>,
  key: string,
  label: string,
) {
  if (typeof state[key] === 'string') {
    entries.push([label, gradeLabel(state[key] as GeneralRidingGrade)])
  }
}
