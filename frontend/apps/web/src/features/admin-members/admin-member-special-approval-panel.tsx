import { useRef, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { getAdminMembersErrorKind, type AdminMemberProgressionResponse, type AdminMembersApi } from './admin-members.api'
import { MemberChangeDialog } from './member-change-dialog'

type Permission = 'dressageApproved' | 'jumpingApproved'
const labels: Record<Permission, string> = { dressageApproved: '마장마술', jumpingApproved: '장애물' }

export function AdminMemberSpecialApprovalPanel({ member, api, onMemberUpdated }: {
  member: AdminMemberProgressionResponse
  api: AdminMembersApi
  onMemberUpdated(member: AdminMemberProgressionResponse): void
}) {
  const client = useQueryClient()
  const [permission, setPermission] = useState<Permission>()
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState<string>()
  const [error, setError] = useState<string>()
  const [success, setSuccess] = useState<string>()
  const [confirmation, setConfirmation] = useState<{ permission: Permission; approved: boolean; reason: string }>()
  const result = useRef<HTMLParagraphElement>(null)
  const locked = useRef(false)
  const mutation = useMutation({
    retry: false,
    mutationFn: async (command: NonNullable<typeof confirmation>) => {
      // Both booleans are replacement values, not independent partial PATCH fields.
      const latest = await api.getMember(member.id)
      onMemberUpdated(latest)
      if (Boolean(latest[command.permission]) === command.approved) throw new Error('STALE_APPROVAL')
      if (command.approved && latest.promotionHoldClass != null) throw new Error('PROMOTION_HOLD')
      return api.changeRidingPermissions(member.id, {
        dressageApproved: command.permission === 'dressageApproved' ? command.approved : Boolean(latest.dressageApproved),
        jumpingApproved: command.permission === 'jumpingApproved' ? command.approved : Boolean(latest.jumpingApproved),
        reason: command.reason,
      })
    },
    onSuccess: async (updated, command) => {
      onMemberUpdated(updated)
      setSuccess(`${labels[command.permission]} ${command.approved ? '승인을' : '승인 해제를'} 반영했습니다.`)
      setReason('')
      setPermission(undefined)
      await client.invalidateQueries({ queryKey: ['admin', 'members', member.id, 'progression-audit'] })
    },
    onError: async (failure) => {
      const kind = getAdminMembersErrorKind(failure)
      setError(failure instanceof Error && failure.message === 'PROMOTION_HOLD'
        ? '특수 승인을 부여하려면 자동 승급 보류를 먼저 직접 해제해 주세요.'
        : failure instanceof Error && failure.message === 'STALE_APPROVAL'
          ? '다른 변경이 먼저 반영되었습니다. 최신 승인 상태를 확인해 주세요.'
          : kind === 'conflict' ? '승급 보류와 특수 승인 상태가 변경되었습니다. 최신 상태를 확인해 주세요.'
          : kind === 'validation' ? '관리자 사유를 다시 확인해 주세요.'
          : kind === 'forbidden' ? '관리자 권한이 없어 처리할 수 없습니다.' : '승인 상태를 변경하지 못했습니다. 최신 상태를 확인해 주세요.')
      await client.invalidateQueries({ queryKey: ['admin', 'members', member.id], exact: true })
      await client.invalidateQueries({ queryKey: ['admin', 'member-list'] })
    },
    onSettled: () => {
      locked.current = false
      setConfirmation(undefined)
      requestAnimationFrame(() => result.current?.focus())
    },
  })
  const review = () => {
    if (!permission || mutation.isPending) return
    if (!reason.trim() || reason.trim().length > 500) { setReasonError('관리자 사유는 공백을 제외하고 1~500자로 입력해 주세요.'); document.getElementById('member-special-reason')?.focus(); return }
    setReasonError(undefined)
    setError(undefined)
    setSuccess(undefined)
    setConfirmation({ permission, approved: !member[permission], reason: reason.trim() })
  }
  return <section className="admin-member-permissions" aria-labelledby="special-permission-title">
    <div className="admin-member-section-heading"><div><h3 id="special-permission-title">특수 클래스 승인</h3><p>마장마술과 장애물 예약 자격을 각각 관리합니다.</p></div></div>
    <div className="admin-member-approval-grid">{(Object.keys(labels) as Permission[]).map((key) => <article className="admin-member-approval-card" key={key}>
      <h4>{labels[key]}</h4><span className={`admin-member-badge ${member[key] ? 'approved' : ''}`}>{member[key] ? '승인 · 예약 가능' : '미승인'}</span>
      <button type="button" disabled={mutation.isPending || (!member[key] && member.promotionHoldClass != null)} onClick={() => { setPermission(key); setReason(''); setReasonError(undefined); setError(undefined); setSuccess(undefined) }}>{labels[key]} 승인 변경</button>
    </article>)}</div>
    {member.promotionHoldClass != null ? <p className="admin-member-progression-guidance" role="status">특수 승인을 부여하려면 자동 승급 보류를 먼저 직접 해제해 주세요.</p> : null}
    {permission ? <div className="admin-member-special-editor"><h4>{labels[permission]} {member[permission] ? '승인 해제' : '승인'}</h4>
      <label htmlFor="member-special-reason">승인 변경 사유</label><textarea id="member-special-reason" maxLength={500} value={reason} disabled={mutation.isPending} aria-invalid={Boolean(reasonError)} aria-describedby="member-special-reason-help member-special-reason-error" onChange={(event) => { setReason(event.target.value); setReasonError(undefined) }} />
      <p id="member-special-reason-help" className="admin-member-help">필수 · 공백 제외 1~500자</p><p id="member-special-reason-error" className="admin-member-inline-error" role={reasonError ? 'alert' : undefined}>{reasonError}</p>
      <button type="button" disabled={mutation.isPending} onClick={review}>승인 변경 내용 확인</button>
    </div> : null}
    {error || success ? <p ref={result} tabIndex={-1} className={error ? 'admin-member-inline-error' : 'admin-member-success'} role={error ? 'alert' : 'status'}>{error ?? success}</p> : null}
    {confirmation ? <MemberChangeDialog title={`${labels[confirmation.permission]} ${confirmation.approved ? '승인' : '승인 해제'} 확인`} pending={mutation.isPending} onClose={() => setConfirmation(undefined)} onConfirm={() => { if (!locked.current) { locked.current = true; mutation.mutate(confirmation) } }}>
      <dl className="admin-member-confirm-facts"><div><dt>회원</dt><dd>{member.name} · {member.phone}</dd></div><div><dt>변경</dt><dd>{labels[confirmation.permission]} {confirmation.approved ? '미승인 → 승인' : '승인 → 미승인'}</dd></div><div><dt>사유</dt><dd>{confirmation.reason}</dd></div></dl>
      <p>{confirmation.approved ? '일반 승급 산정 기준에 미달하면 서버가 부족분만 추가 인정합니다. 실제 일반 기승 횟수는 증가하지 않습니다.' : '승인을 해제해도 이전에 부여된 특수 승인 추가 인정분은 자동으로 제거되지 않습니다.'}</p>
    </MemberChangeDialog> : null}
  </section>
}
