import type { GeneralRidingGrade } from './admin-members.api'

export const GENERAL_GRADES: ReadonlyArray<{ value: GeneralRidingGrade; label: string }> = [
  { value: 'FIRST_RIDE', label: '왕초보' },
  { value: 'ROUND_BEGINNER', label: '원형초보' },
  { value: 'ROUND_TROT', label: '원형 속보' },
  { value: 'LARGE_ARENA_BEGINNER', label: '대마장초보' },
  { value: 'LARGE_ARENA_TROT', label: '대마장 속보' },
  { value: 'CANTER_BEGINNER', label: '구보초보' },
  { value: 'CANTER', label: '구보' },
]

export function gradeLabel(grade: GeneralRidingGrade | null | undefined) {
  if (grade === undefined) return '확인 불가'
  if (grade === null) return '미설정'
  return GENERAL_GRADES.find((candidate) => candidate.value === grade)?.label ?? '확인 불가'
}

export function formatDateTime(value: string | Date | null | undefined) {
  if (value === undefined) return '확인 불가'
  if (value === null) return '미설정'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? String(value) : new Intl.DateTimeFormat('ko-KR', { dateStyle: 'short', timeStyle: 'short' }).format(date)
}

export function formatCount(value: number | null | undefined) {
  return typeof value === 'number' && Number.isFinite(value) ? `${value}회` : '확인 불가'
}
