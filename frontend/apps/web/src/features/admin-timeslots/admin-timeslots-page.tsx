import { useRef, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { TimeSlotCapacityUpdateRequest, TimeSlotResponse } from '@horse/api-client'
import {
  adminTimeSlotsApi,
  getTimeSlotErrorKind,
  type AdminTimeSlotsApi,
} from './admin-timeslots.api'
import './admin-timeslots-page.css'

const CLASS_FIELDS = [
  ['FIRST_RIDE', '왕초보'], ['ROUND_BEGINNER', '원형초보'], ['ROUND_TROT', '원형 속보'],
  ['LARGE_ARENA_BEGINNER', '대마장초보'], ['LARGE_ARENA_TROT', '대마장 속보'],
  ['CANTER_BEGINNER', '구보초보'], ['CANTER', '구보'],
  ['DRESSAGE', '마장마술'], ['JUMPING', '장애물'],
] as const
const TIME_SLOTS_KEY = ['admin', 'timeslots'] as const

interface CapacityForm {
  totalCapacity: string
  roundArenaCapacity: string
  classCapacities: Record<string, string>
}

const emptyCapacity = (): CapacityForm => ({
  totalCapacity: '8',
  roundArenaCapacity: '4',
  classCapacities: Object.fromEntries(CLASS_FIELDS.map(([key]) => [key, '0'])),
})

function capacityFromSlot(slot: TimeSlotResponse): CapacityForm {
  return {
    totalCapacity: String(slot.totalCapacity ?? 0),
    roundArenaCapacity: String(slot.roundArenaCapacity ?? 0),
    classCapacities: Object.fromEntries(CLASS_FIELDS.map(([key]) => [key, String(slot.classCapacities?.[key] ?? 0)])),
  }
}

function parseCapacity(form: CapacityForm): TimeSlotCapacityUpdateRequest | string {
  const total = Number(form.totalCapacity)
  const round = Number(form.roundArenaCapacity)
  const classes = Object.fromEntries(CLASS_FIELDS.map(([key]) => [key, Number(form.classCapacities[key])]))
  if (!Number.isInteger(total) || total < 0 || total > 8) return '전체 정원은 0명에서 8명 사이여야 합니다.'
  if (!Number.isInteger(round) || round < 0 || round > 4 || round > total) {
    return '원형 정원은 전체 정원 이하이며 0명에서 4명 사이여야 합니다.'
  }
  if (Object.values(classes).some((capacity) => !Number.isInteger(capacity) || capacity < 0 || capacity > 8)) {
    return '클래스별 정원은 각각 0명에서 8명 사이여야 합니다.'
  }
  return { totalCapacity: total, roundArenaCapacity: round, classCapacities: classes }
}

function getErrorMessage(error: unknown) {
  const kind = getTimeSlotErrorKind(error)
  if (kind === 'forbidden') return '관리자 권한이 없어 시간대를 변경할 수 없습니다.'
  if (kind === 'validation') return '정원 또는 시간대 입력이 올바르지 않습니다.'
  if (kind === 'conflict') return '다른 변경 또는 예약 이력과 충돌했습니다. 목록을 다시 확인해 주세요.'
  return '시간대를 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

export function AdminTimeSlotsPage({ api = adminTimeSlotsApi }: { api?: AdminTimeSlotsApi }) {
  const queryClient = useQueryClient()
  const [lessonDate, setLessonDate] = useState('')
  const [startTime, setStartTime] = useState('')
  const [createCapacity, setCreateCapacity] = useState(emptyCapacity)
  const [editId, setEditId] = useState<number>()
  const [editCapacity, setEditCapacity] = useState<CapacityForm>()
  const [localError, setLocalError] = useState<string>()
  const commandLocked = useRef(false)
  const query = useQuery({ queryKey: TIME_SLOTS_KEY, queryFn: api.getTimeSlots })

  const updateCachedSlot = (updated: TimeSlotResponse) => {
    queryClient.setQueryData<TimeSlotResponse[]>(TIME_SLOTS_KEY, (current = []) => {
      const exists = current.some((slot) => slot.id === updated.id)
      const next = exists ? current.map((slot) => slot.id === updated.id ? updated : slot) : [...current, updated]
      return next.toSorted((left, right) => {
        const date = String(left.lessonDate).localeCompare(String(right.lessonDate))
        return date || String(left.startTime).localeCompare(String(right.startTime))
      })
    })
  }

  const command = useMutation({
    mutationFn: (operation:
      | { kind: 'create'; request: TimeSlotCapacityUpdateRequest }
      | { kind: 'capacity'; id: number; request: TimeSlotCapacityUpdateRequest }
      | { kind: 'status'; id: number; closed: boolean }) => {
      if (operation.kind === 'create') {
        return api.createTimeSlot({ lessonDate: new Date(lessonDate), startTime, ...operation.request })
      }
      if (operation.kind === 'capacity') return api.changeCapacity(operation.id, operation.request)
      return api.changeClosedStatus(operation.id, operation.closed)
    },
    onSuccess: (slot) => {
      updateCachedSlot(slot)
      setLocalError(undefined)
      setEditId(undefined)
      setEditCapacity(undefined)
    },
  })

  const runCommand = (operation: Parameters<typeof command.mutate>[0]) => {
    if (commandLocked.current) return
    commandLocked.current = true
    command.mutate(operation, { onSettled: () => { commandLocked.current = false } })
  }

  const createSlot = (event: FormEvent) => {
    event.preventDefault()
    if (!lessonDate || !startTime) {
      setLocalError('수업 날짜와 시작 시간을 입력해 주세요.')
      return
    }
    const capacity = parseCapacity(createCapacity)
    if (typeof capacity === 'string') {
      setLocalError(capacity)
      return
    }
    setLocalError(undefined)
    runCommand({ kind: 'create', request: capacity })
  }

  const saveCapacity = (id: number) => {
    if (!editCapacity) return
    const capacity = parseCapacity(editCapacity)
    if (typeof capacity === 'string') {
      setLocalError(capacity)
      return
    }
    setLocalError(undefined)
    runCommand({ kind: 'capacity', id, request: capacity })
  }

  if (query.isPending) return <TimeSlotsState message="시간대 목록을 불러오는 중입니다." />
  if (query.isError) return <TimeSlotsState error message={getErrorMessage(query.error)} />

  return (
    <main className="admin-timeslots-page">
      <div className="admin-timeslots-shell">
        <header className="admin-timeslots-header">
          <div><p className="admin-timeslots-eyebrow">SCHEDULE OPERATIONS</p><h1>시간대 및 정원</h1></div>
        </header>
        <div className="admin-timeslots-layout">
          <section className="admin-timeslots-panel admin-timeslots-section">
            <h2>시간대 생성</h2>
            <form className="admin-timeslots-form" onSubmit={createSlot}>
              <div className="admin-timeslots-main-capacity">
                <TextField label="수업 날짜" type="date" value={lessonDate} onChange={setLessonDate} disabled={command.isPending} />
                <TextField label="시작 시간" type="time" value={startTime} onChange={setStartTime} disabled={command.isPending} />
              </div>
              <CapacityFields value={createCapacity} onChange={setCreateCapacity} disabled={command.isPending} />
              {localError ? <p className="admin-timeslots-error" role="alert">{localError}</p> : null}
              {command.isError ? <p className="admin-timeslots-error" role="alert">{getErrorMessage(command.error)}</p> : null}
              <button className="admin-timeslots-button" type="submit" disabled={command.isPending}>시간대 생성</button>
            </form>
          </section>

          <section className="admin-timeslots-section" aria-label="시간대 목록">
            <h2>등록 시간대 {query.data.length}개</h2>
            {query.data.length === 0 ? <TimeSlotsState message="등록된 시간대가 없습니다." embedded /> : (
              <div className="admin-timeslots-list">
                {query.data.map((slot) => (
                  <article className={`admin-timeslot-card${slot.closed ? ' closed' : ''}`} key={slot.id}>
                    <div className="admin-timeslot-card-header">
                      <h3>{formatDate(slot.lessonDate)} · {slot.startTime?.slice(0, 5)}</h3>
                      <span className={`admin-timeslot-status${slot.closed ? ' closed' : ''}`}>{slot.closed ? '마감' : '예약 가능'}</span>
                    </div>
                    <div className="admin-timeslot-summary"><span>전체 {slot.totalCapacity}명</span><span>원형 {slot.roundArenaCapacity}명</span></div>
                    <dl className="admin-timeslot-classes">
                      {CLASS_FIELDS.map(([key, label]) => <div key={key}><dt>{label}</dt><dd>{slot.classCapacities?.[key] ?? 0}명</dd></div>)}
                    </dl>
                    {editId === slot.id && editCapacity ? (
                      <div className="admin-timeslot-edit">
                        <CapacityFields value={editCapacity} onChange={setEditCapacity} disabled={command.isPending} />
                        <div className="admin-timeslot-actions">
                          <button className="admin-timeslots-button secondary" type="button" onClick={() => setEditId(undefined)}>취소</button>
                          <button className="admin-timeslots-button" type="button" disabled={command.isPending} onClick={() => saveCapacity(slot.id as number)}>정원 저장</button>
                        </div>
                      </div>
                    ) : (
                      <div className="admin-timeslot-actions">
                        <button className="admin-timeslots-button secondary" type="button" disabled={command.isPending} onClick={() => { setEditId(slot.id); setEditCapacity(capacityFromSlot(slot)) }}>정원 수정</button>
                        <button className="admin-timeslots-button" type="button" disabled={command.isPending} onClick={() => runCommand({ kind: 'status', id: slot.id as number, closed: !slot.closed })}>{slot.closed ? '예약 재개' : '신규 예약 마감'}</button>
                      </div>
                    )}
                  </article>
                ))}
              </div>
            )}
          </section>
        </div>
      </div>
    </main>
  )
}

function formatDate(date?: Date) {
  return date ? new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', month: 'long', day: 'numeric', weekday: 'short' }).format(date) : '-'
}

function TextField({ label, type, value, onChange, disabled }: { label: string; type: string; value: string; onChange(value: string): void; disabled: boolean }) {
  return <div className="admin-timeslots-field"><label>{label}<input aria-label={label} type={type} value={value} disabled={disabled} onChange={(event) => onChange(event.target.value)} /></label></div>
}

function CapacityFields({ value, onChange, disabled }: { value: CapacityForm; onChange(value: CapacityForm): void; disabled: boolean }) {
  const changeClass = (key: string, next: string) => onChange({ ...value, classCapacities: { ...value.classCapacities, [key]: next } })
  return <>
    <div className="admin-timeslots-main-capacity">
      <TextField label="전체 정원" type="number" value={value.totalCapacity} disabled={disabled} onChange={(next) => onChange({ ...value, totalCapacity: next })} />
      <TextField label="원형 정원" type="number" value={value.roundArenaCapacity} disabled={disabled} onChange={(next) => onChange({ ...value, roundArenaCapacity: next })} />
    </div>
    <fieldset className="admin-timeslots-capacity-grid" disabled={disabled}>
      <legend>클래스별 정원</legend>
      <div className="admin-timeslots-capacity-inputs">
        {CLASS_FIELDS.map(([key, label]) => <TextField key={key} label={`${label} 정원`} type="number" value={value.classCapacities[key]} disabled={disabled} onChange={(next) => changeClass(key, next)} />)}
      </div>
    </fieldset>
  </>
}

function TimeSlotsState({ message, error = false, embedded = false }: { message: string; error?: boolean; embedded?: boolean }) {
  const content = <section className="admin-timeslots-state" role={error ? 'alert' : undefined}>{message}</section>
  return embedded ? content : <main className="admin-timeslots-page"><div className="admin-timeslots-shell">{content}</div></main>
}
