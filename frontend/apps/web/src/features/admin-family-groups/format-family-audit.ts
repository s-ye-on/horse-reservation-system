import type { FamilyGroupAuditAction, FamilyGroupAuditLog } from './admin-family-groups.api'

export function familyAuditLabel(action: FamilyGroupAuditAction) {
  return { GROUP_CREATED: '그룹 생성', MEMBER_ADDED: '구성원 추가', MEMBER_REMOVED: '구성원 제거', GROUP_DISSOLVED: '그룹 해제' }[action]
}

export function formatFamilyAuditState(value: FamilyGroupAuditLog['fromState'], action: FamilyGroupAuditAction): string {
  if (!value) return '없음'
  const membership = action === 'MEMBER_ADDED' || action === 'MEMBER_REMOVED'
  const label = membership
    ? ({ NONE: '미소속', ACTIVE: '관계 유지 중', ENDED: '관계 종료' } as Record<string, string>)[value.status ?? '']
    : ({ ACTIVE: '운영 중', DISSOLVED: '해제됨' } as Record<string, string>)[value.status ?? '']
  const parts = [value.name, label].filter((part): part is string => Boolean(part))
  if (Array.isArray(value.activeMemberIds)) parts.push(`현재 구성원 ${value.activeMemberIds.length}명`)
  if (value.joinedAt) parts.push(`가입 ${formatFamilyDateTime(value.joinedAt)}`)
  if (value.endedAt) parts.push(`종료 ${formatFamilyDateTime(value.endedAt)}`)
  if (value.dissolvedAt) parts.push(`해제 ${formatFamilyDateTime(value.dissolvedAt)}`)
  return parts.join(' · ') || '상태 정보 없음'
}

export function formatFamilyDateTime(value: string | Date) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '시각 확인 필요'
  return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date)
}
