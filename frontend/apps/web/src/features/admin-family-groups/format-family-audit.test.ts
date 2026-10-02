import { describe, expect, it } from 'vitest'
import { familyAuditLabel, formatFamilyAuditState, formatFamilyDateTime } from './format-family-audit'
import type { FamilyGroupAuditLog } from './admin-family-groups.api'

function state(value: Partial<NonNullable<FamilyGroupAuditLog['fromState']>>) {
  return { groupId: null, name: null, status: null, activeMemberIds: null, dissolvedAt: null, membershipId: null, memberId: null, joinedAt: null, endedAt: null, ...value }
}
describe('family audit display', () => {
  it('현재 관계 상태를 사용자 문구로 표시한다', () => {
    expect(formatFamilyAuditState(null, 'GROUP_CREATED')).toBe('없음')
    expect(formatFamilyAuditState(state({ status: 'NONE' }), 'MEMBER_ADDED')).toBe('미소속')
    expect(formatFamilyAuditState(state({ status: 'ACTIVE' }), 'MEMBER_ADDED')).toBe('관계 유지 중')
    expect(formatFamilyAuditState(state({ status: 'ENDED', endedAt: '2026-09-01T10:00:00+09:00', joinedAt: '2026-08-01T10:00:00+09:00' }), 'MEMBER_REMOVED')).toMatch(/관계 종료 · 가입.*종료/)
  })
  it('감사 snapshot의 상태와 구성원 수를 보여주고 내부 ID를 숨긴다', () => {
    const rendered = formatFamilyAuditState(state({ groupId: 999, name: '가족', status: 'DISSOLVED', activeMemberIds: [], dissolvedAt: '2026-09-01T10:00:00+09:00' }), 'GROUP_DISSOLVED')
    expect(rendered).toMatch(/가족 · 해제됨 · 현재 구성원 0명 · 해제/)
    expect(rendered).not.toContain('999')
    expect(formatFamilyAuditState(state({ status: 'ACTIVE', activeMemberIds: [1, 2] }), 'GROUP_CREATED')).toBe('운영 중 · 현재 구성원 2명')
    expect(formatFamilyAuditState(state({ status: 'UNKNOWN' }), 'GROUP_CREATED')).toBe('상태 정보 없음')
  })
  it('실제 작업만 mapping하고 잘못된 날짜를 안전하게 표현한다', () => {
    expect(['GROUP_CREATED', 'MEMBER_ADDED', 'MEMBER_REMOVED', 'GROUP_DISSOLVED'].map((action) => familyAuditLabel(action as FamilyGroupAuditLog['action']))).toEqual(['그룹 생성', '구성원 추가', '구성원 제거', '그룹 해제'])
    expect(formatFamilyDateTime('bad')).toBe('시각 확인 필요')
    expect(formatFamilyDateTime(new Date('2026-09-01T01:00:00Z'))).toContain('10:00')
  })
})
