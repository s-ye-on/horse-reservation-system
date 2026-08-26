import { useEffect, useState, type FormEvent } from 'react'
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

const AUDIT_PAGE_SIZE = 5
const GENERAL_GRADES: ReadonlyArray<{ value: GeneralRidingGrade; label: string }> = [
  { value: 'FIRST_RIDE', label: '왕초보' },
  { value: 'ROUND_BEGINNER', label: '원형초보' },
  { value: 'ROUND_TROT', label: '원형 속보' },
  { value: 'LARGE_ARENA_BEGINNER', label: '대마장초보' },
  { value: 'LARGE_ARENA_TROT', label: '대마장 속보' },
  { value: 'CANTER_BEGINNER', label: '구보초보' },
  { value: 'CANTER', label: '구보' },
]

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

export function AdminMemberClassProgressionPanel({
  member,
  api,
  onMemberUpdated,
}: AdminMemberClassProgressionPanelProps) {
  const queryClient = useQueryClient()
  const [action, setAction] = useState<MemberClassProgressionPreviewAction>('SET_BASELINE')
  const [grade, setGrade] = useState<GeneralRidingGrade>(member.progressionClass ?? 'FIRST_RIDE')
  const [numericValue, setNumericValue] = useState('1')
  const [reason, setReason] = useState('')
  const [preview, setPreview] = useState<MemberClassProgressionPreviewResponse>()
  const [localError, setLocalError] = useState<string>()
  const [successMessage, setSuccessMessage] = useState<string>()
  const [auditPage, setAuditPage] = useState(0)
  const hasSpecialApproval = member.dressageApproved || member.jumpingApproved

  useEffect(() => {
    setPreview(undefined)
    setLocalError(undefined)
  }, [
    member.dressageApproved,
    member.generalRideCount,
    member.jumpingApproved,
    member.progressionBaselineClass,
    member.progressionValue,
    member.promotionHoldClass,
    member.specialApprovalProgressionCredit,
  ])

  const auditQuery = useQuery({
    queryKey: ['admin', 'members', member.id, 'progression-audit', auditPage],
    queryFn: () => api.getProgressionAuditLogs(member.id, auditPage, AUDIT_PAGE_SIZE),
  })

  const previewMutation = useMutation({
    mutationFn: (request: MemberClassProgressionPreviewRequest) => api.previewProgression(member.id, request),
    onSuccess: (result) => {
      setPreview(result)
      setLocalError(undefined)
      setSuccessMessage(undefined)
    },
    onError: (error) => {
      setPreview(undefined)
      void progressionErrorMessage(error, 'preview').then(setLocalError)
    },
  })

  const commandMutation = useMutation({
    mutationFn: async () => {
      if (!preview) throw new Error('예상 결과를 먼저 확인해 주세요.')
      return executeCommand(api, member.id, action, grade, numericValue, reason.trim(), preview.stateToken)
    },
    onSuccess: (updatedMember) => {
      onMemberUpdated(updatedMember)
      setPreview(undefined)
      setReason('')
      setLocalError(undefined)
      setSuccessMessage(`${ACTION_LABELS[action]} 처리가 완료됐습니다.`)
      void queryClient.invalidateQueries({ queryKey: ['admin', 'members', member.id, 'progression-audit'] })
    },
    onError: (error) => {
      void progressionErrorMessage(error, 'command').then(({ message, staleState }) => {
        if (staleState) setPreview(undefined)
        setLocalError(message)
      })
    },
  })

  const resetPreview = () => {
    setPreview(undefined)
    setLocalError(undefined)
    setSuccessMessage(undefined)
    previewMutation.reset()
    commandMutation.reset()
  }

  const previewChange = (event: FormEvent) => {
    event.preventDefault()
    try {
      const request = buildPreviewRequest(action, grade, numericValue)
      setLocalError(undefined)
      previewMutation.mutate(request)
    } catch (error) {
      setPreview(undefined)
      setLocalError(error instanceof Error ? error.message : '입력값을 확인해 주세요.')
    }
  }

  const progressionDataAvailable = hasProgressionSnapshot(member)
  const actionDisabled = !progressionDataAvailable || isActionDisabled(action, member, hasSpecialApproval)
  const busy = previewMutation.isPending || commandMutation.isPending

  return (
    <section className="admin-member-progression" aria-labelledby="member-progression-title">
      <div className="admin-member-section-heading">
        <div>
          <h3 id="member-progression-title">일반 클래스 승급 관리</h3>
          <p>실제 일반 기승 기록과 관리자 인정 정보를 기준으로 현재 클래스를 계산합니다.</p>
        </div>
        <span>{gradeLabel(member.effectiveClass)}</span>
      </div>

      <dl className="admin-member-progression-grid">
        <ProgressionStat label="실제 일반 기승 횟수" value={formatCount(member.generalRideCount)} />
        <ProgressionStat label="승급 산정 횟수" value={formatCount(member.progressionValue)} />
        <ProgressionStat label="자동 산정 클래스" value={gradeLabel(member.progressionClass)} />
        <ProgressionStat label="현재 적용 클래스" value={gradeLabel(member.effectiveClass)} />
        <ProgressionStat label="인정 시작 클래스" value={gradeLabel(member.progressionBaselineClass)} />
        <ProgressionStat label="특수 승인 추가 인정 횟수" value={formatCount(member.specialApprovalProgressionCredit)} />
        <ProgressionStat label="자동 승급 보류 클래스" value={gradeLabel(member.promotionHoldClass)} />
        <ProgressionStat label="승급 관리 시작 시각" value={formatDateTime(member.progressionManagementStartedAt)} />
      </dl>

      <p className="admin-member-policy-note">
        특수 클래스 승인이 있는 회원은 자동 승급 보류를 함께 사용할 수 없습니다. 실제 기승 횟수 보정은
        Horse에서 누락되거나 중복 집계된 일반 기승 기록만 바로잡습니다.
      </p>

      <form className="admin-member-progression-form" onSubmit={previewChange}>
        <label>
          변경 항목
          <select
            aria-label="변경 항목"
            value={action}
            disabled={busy || !progressionDataAvailable}
            onChange={(event) => {
              setAction(event.target.value as MemberClassProgressionPreviewAction)
              resetPreview()
            }}
          >
            {(Object.keys(ACTION_LABELS) as MemberClassProgressionPreviewAction[]).map((candidate) => (
              <option
                key={candidate}
                value={candidate}
                disabled={!progressionDataAvailable || isActionDisabled(candidate, member, hasSpecialApproval)}
              >
                {ACTION_LABELS[candidate]}
              </option>
            ))}
          </select>
        </label>

        {action === 'SET_BASELINE' || action === 'SET_PROMOTION_HOLD' ? (
          <label>
            {action === 'SET_BASELINE' ? '인정 시작 클래스' : '보류할 최고 클래스'}
            <select
              aria-label={action === 'SET_BASELINE' ? '인정 시작 클래스' : '보류할 최고 클래스'}
              value={grade}
              disabled={busy || !progressionDataAvailable}
              onChange={(event) => {
                setGrade(event.target.value as GeneralRidingGrade)
                resetPreview()
              }}
            >
              {GENERAL_GRADES.map((candidate) => (
                <option key={candidate.value} value={candidate.value}>{candidate.label}</option>
              ))}
            </select>
          </label>
        ) : null}

        {action === 'CORRECT_SPECIAL_APPROVAL_CREDIT' || action === 'ADJUST_RIDE_COUNT' ? (
          <label>
            {action === 'CORRECT_SPECIAL_APPROVAL_CREDIT' ? '교정 후 추가 인정 횟수' : '기승 횟수 증감'}
            <input
              type="number"
              step="1"
              value={numericValue}
              disabled={busy || !progressionDataAvailable}
              onChange={(event) => {
                setNumericValue(event.target.value)
                resetPreview()
              }}
            />
          </label>
        ) : null}

        <label className="admin-member-progression-reason">
          관리자 사유
          <input
            type="text"
            maxLength={500}
            value={reason}
            disabled={busy || !progressionDataAvailable}
            onChange={(event) => setReason(event.target.value)}
          />
        </label>

        {actionDisabled ? (
          <p
            className="admin-member-progression-guidance"
            id="member-progression-apply-guidance"
            role="status"
          >
            {disabledActionGuidance(member, hasSpecialApproval, progressionDataAvailable)}
          </p>
        ) : (
          <p className="admin-member-progression-guidance" id="member-progression-apply-guidance">
            {preview
              ? reason.trim()
                ? '예상 결과를 확인했습니다. 이제 변경을 적용할 수 있습니다.'
                : '예상 결과를 확인했습니다. 관리자 사유를 입력하면 변경을 적용할 수 있습니다.'
              : '먼저 변경 결과를 미리 본 뒤 적용할 수 있습니다.'}
          </p>
        )}

        <div className="admin-member-progression-actions">
          <button type="submit" disabled={busy || actionDisabled}>변경 결과 미리보기</button>
          <button
            type="button"
            className="admin-member-primary-action"
            aria-describedby="member-progression-apply-guidance"
            disabled={busy || actionDisabled || preview === undefined || !reason.trim()}
            onClick={() => commandMutation.mutate()}
          >
            {commandMutation.isPending ? '적용 중' : '변경 적용'}
          </button>
        </div>
      </form>

      {preview ? <ProgressionPreview preview={preview} /> : null}
      {localError ? <p className="admin-member-inline-error" role="alert">{localError}</p> : null}
      {successMessage ? <p className="admin-member-success" role="status">{successMessage}</p> : null}

      <section className="admin-member-progression-audit" aria-labelledby="member-progression-audit-title">
        <div className="admin-member-section-heading">
          <h3 id="member-progression-audit-title">변경 감사</h3>
          <span>총 {auditQuery.data?.totalElements ?? 0}건</span>
        </div>
        {auditQuery.isPending ? (
          <p role="status">감사 이력을 불러오는 중입니다.</p>
        ) : auditQuery.isError ? (
          <p className="admin-member-inline-error" role="alert">감사 이력을 불러오지 못했습니다.</p>
        ) : auditQuery.data?.content.length === 0 ? (
          <p>기록된 변경 감사 이력이 없습니다.</p>
        ) : (
          <ol className="admin-member-progression-audit-list">
            {auditQuery.data?.content.map((audit) => (
              <li key={audit.auditId}>
                <div><strong>{AUDIT_LABELS[audit.action]}</strong><time dateTime={audit.occurredAt.toISOString()}>{formatDateTime(audit.occurredAt)}</time></div>
                <p>{audit.reason}</p>
                <small>처리자 {audit.actorAuthSubject}</small>
                <details>
                  <summary>전후 상태 보기</summary>
                  <dl>
                    <div><dt>변경 전</dt><dd><AuditState state={audit.fromState} /></dd></div>
                    <div><dt>변경 후</dt><dd><AuditState state={audit.toState} /></dd></div>
                  </dl>
                </details>
              </li>
            ))}
          </ol>
        )}
        {(auditQuery.data?.totalPages ?? 0) > 0 ? (
          <nav className="admin-member-progression-pagination" aria-label="회원 클래스 변경 감사 페이지">
            <button type="button" disabled={auditPage === 0 || auditQuery.isFetching} onClick={() => setAuditPage((value) => value - 1)}>이전</button>
            <span>{auditPage + 1} / {auditQuery.data?.totalPages ?? 1} 페이지</span>
            <button type="button" disabled={!auditQuery.data?.hasNext || auditQuery.isFetching} onClick={() => setAuditPage((value) => value + 1)}>다음</button>
          </nav>
        ) : null}
      </section>
    </section>
  )
}

function ProgressionStat({ label, value }: { label: string; value: string }) {
  return <div><dt>{label}</dt><dd>{value}</dd></div>
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

function gradeLabel(grade: GeneralRidingGrade | null | undefined) {
  if (grade === undefined) return '확인 불가'
  if (grade === null) return '미설정'
  return GENERAL_GRADES.find((candidate) => candidate.value === grade)?.label ?? '확인 불가'
}

function formatDateTime(value: string | Date | null | undefined) {
  if (value === undefined) return '확인 불가'
  if (value === null) return '미설정'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : new Intl.DateTimeFormat('ko-KR', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(date)
}

function formatCount(value: number | null | undefined) {
  return typeof value === 'number' && Number.isFinite(value) ? `${value}회` : '확인 불가'
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
