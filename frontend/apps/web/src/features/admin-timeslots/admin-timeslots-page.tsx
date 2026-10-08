import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import type { TimeSlotCapacityUpdateRequest, TimeSlotCreateRequest, TimeSlotResponse } from '@horse/api-client'
import { adminTimeSlotsApi, readTimeSlotError, type AdminTimeSlotsApi } from './admin-timeslots.api'
import { TimeSlotActionDialog } from './time-slot-action-dialog'
import './admin-timeslots-page.css'

const CLASS_FIELDS = [
  ['FIRST_RIDE', '왕초보'], ['ROUND_BEGINNER', '원형초보'], ['ROUND_TROT', '원형 속보'],
  ['LARGE_ARENA_BEGINNER', '대마장초보'], ['LARGE_ARENA_TROT', '대마장 속보'],
  ['CANTER_BEGINNER', '구보초보'], ['CANTER', '구보'],
  ['DRESSAGE', '마장마술'], ['JUMPING', '장애물'],
] as const
const TIME_SLOTS_KEY = ['admin', 'timeslots'] as const
interface CapacityForm { totalCapacity: string; roundArenaCapacity: string; classCapacities: Record<string, string> }
interface Editor { slot?: TimeSlotResponse; lessonDate: string; startTime: string; capacity: CapacityForm }
type Operation =
  | { kind: 'create'; request: TimeSlotCreateRequest }
  | { kind: 'capacity'; slot: TimeSlotResponse; request: TimeSlotCapacityUpdateRequest }
  | { kind: 'status'; slot: TimeSlotResponse; closed: boolean }
type FieldErrors = Record<string, string>

function newEditor(slot?: TimeSlotResponse): Editor {
  return { slot, lessonDate: slot?.lessonDate.toISOString().slice(0, 10) ?? '', startTime: slot?.startTime.slice(0, 5) ?? '',
    capacity: { totalCapacity: String(slot?.totalCapacity ?? 8), roundArenaCapacity: String(slot?.roundArenaCapacity ?? 4),
      classCapacities: Object.fromEntries(CLASS_FIELDS.map(([key]) => [key, String(slot?.classCapacities[key] ?? 0)])) } }
}
function validateCapacity(form: CapacityForm) {
  const errors: FieldErrors = {}
  const integer = (value: string, maximum: number) => /^\d+$/.test(value) && Number.isSafeInteger(Number(value)) && Number(value) <= maximum
  if (!integer(form.totalCapacity, 8)) errors.totalCapacity = '전체 정원은 0명에서 8명 사이의 정수여야 합니다.'
  if (!integer(form.roundArenaCapacity, 4) || Number(form.roundArenaCapacity) > Number(form.totalCapacity)) {
    errors.roundArenaCapacity = '원형마장 정원은 전체 이하이며 0명에서 4명 사이의 정수여야 합니다.'
  }
  for (const [key, label] of CLASS_FIELDS) if (!integer(form.classCapacities[key], 8)) errors[key] = `${label} 정원은 0명에서 8명 사이의 정수여야 합니다.`
  const request: TimeSlotCapacityUpdateRequest = { totalCapacity: Number(form.totalCapacity), roundArenaCapacity: Number(form.roundArenaCapacity),
    classCapacities: Object.fromEntries(CLASS_FIELDS.map(([key]) => [key, Number(form.classCapacities[key])])) }
  return { errors, request }
}
function dateLabel(date: Date) { return new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: 'long', day: 'numeric', weekday: 'short' }).format(date) }
function slotLabel(slot: TimeSlotResponse) { return `${dateLabel(slot.lessonDate)} · ${slot.startTime.slice(0, 5)}` }

export function AdminTimeSlotsPage({ api = adminTimeSlotsApi }: { api?: AdminTimeSlotsApi }) {
  const queryClient = useQueryClient()
  const query = useQuery({ queryKey: TIME_SLOTS_KEY, queryFn: api.getTimeSlots })
  const [editor, setEditor] = useState<Editor>()
  const [operation, setOperation] = useState<Operation>()
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [error, setError] = useState<string>()
  const [result, setResult] = useState<{ slot: TimeSlotResponse; message: string }>()
  const resultRef = useRef<HTMLHeadingElement>(null)
  const errorRef = useRef<HTMLDivElement>(null)
  const commandGuard = useRef(false)
  useEffect(() => { if (result) resultRef.current?.focus() }, [result])
  useEffect(() => { if (error && !editor && !operation) errorRef.current?.focus() }, [error, editor, operation])
  const refresh = () => queryClient.invalidateQueries({ queryKey: TIME_SLOTS_KEY })
  const command = useMutation({
    retry: false,
    mutationFn: (request: Operation) => request.kind === 'create' ? api.createTimeSlot(request.request)
      : request.kind === 'capacity' ? api.changeCapacity(request.slot.id, request.request)
        : api.changeClosedStatus(request.slot.id, request.closed),
    onSuccess: async (slot, request) => {
      queryClient.setQueryData<TimeSlotResponse[]>(TIME_SLOTS_KEY, (current = []) => {
        const remaining = current.filter((item) => item.id !== slot.id)
        return [...remaining, slot].toSorted((a, b) => a.lessonDate.getTime() - b.lessonDate.getTime() || a.startTime.localeCompare(b.startTime))
      })
      setEditor(undefined); setOperation(undefined); setError(undefined); setFieldErrors({})
      setResult({ slot, message: request.kind === 'create' ? '수동 시간대가 생성되었습니다.'
        : request.kind === 'capacity' ? '시간대 정원이 변경되었습니다.'
          : request.closed ? '신규 예약 마감 요청이 반영되었습니다. 영향 예약 처리는 휴무·휴강 관리에서 확인해 주세요.'
            : slot.closed ? '철회·재개 요청이 처리되었습니다. 이 시간대는 여전히 신규 예약 마감 상태입니다.'
              : '철회·재개 요청이 처리되었습니다. 시간대 마감 표시가 해제되어 있습니다. 최종 예약 가능 여부는 예약 시 서버가 확인합니다.' })
      await refresh()
    },
    onError: async (failure) => {
      const feedback = await readTimeSlotError(failure)
      setError(feedback.message)
      if (feedback.field && editor) setFieldErrors({ [feedback.field]: feedback.message })
      setOperation(undefined)
      await refresh()
      const latest = queryClient.getQueryData<TimeSlotResponse[]>(TIME_SLOTS_KEY)
      setEditor((draft) => {
        if (!draft?.slot) return draft
        const slot = latest?.find((item) => item.id === draft.slot?.id)
        return slot ? { ...draft, slot } : draft
      })
    },
    onSettled: () => { commandGuard.current = false },
  })
  const beginEditor = (slot?: TimeSlotResponse) => { setEditor(newEditor(slot)); setOperation(undefined); setError(undefined); setFieldErrors({}) }
  const closeDialog = () => { if (commandGuard.current) return; setEditor(undefined); setOperation(undefined); setFieldErrors({}) }
  const review = (event: FormEvent) => {
    event.preventDefault()
    if (!editor || commandGuard.current || command.isPending) return
    const { errors, request } = validateCapacity(editor.capacity)
    if (!editor.slot && !editor.lessonDate) errors.lessonDate = '수업 날짜를 입력해 주세요.'
    if (!editor.slot && !editor.startTime) errors.startTime = '시작 시각을 입력해 주세요.'
    setFieldErrors(errors)
    if (Object.keys(errors).length) {
      document.getElementById(`timeslot-field-${Object.keys(errors)[0]}`)?.focus()
      return
    }
    setError(undefined)
    setOperation(editor.slot ? { kind: 'capacity', slot: editor.slot, request }
      : { kind: 'create', request: { ...request, lessonDate: new Date(`${editor.lessonDate}T00:00:00Z`), startTime: editor.startTime } })
  }
  const confirm = () => {
    if (!operation || commandGuard.current) return
    commandGuard.current = true
    setError(undefined); setResult(undefined)
    command.mutate(operation)
  }
  const beginStatus = (slot: TimeSlotResponse, closed: boolean) => {
    setEditor(undefined); setFieldErrors({}); setError(undefined)
    setOperation({ kind: 'status', slot, closed })
  }
  const groups = new Map<string, TimeSlotResponse[]>()
  for (const slot of query.data ?? []) {
    const date = slot.lessonDate.toISOString().slice(0, 10)
    groups.set(date, [...(groups.get(date) ?? []), slot])
  }
  const title = operation ? operation.kind === 'create' ? '시간대 생성 확인'
    : operation.kind === 'capacity' ? '정원 변경 확인' : operation.closed ? '신규 예약 마감 확인' : '관리자 마감 철회·재개 확인'
    : editor?.slot ? '시간대 정원 편집' : '수동 시간대 생성'

  return <main className="admin-timeslots-page">
    <header className="admin-timeslots-header"><div><p className="timeslot-eyebrow">SCHEDULE OPERATIONS</p><h1>시간대 및 정원</h1>
      <p>아직 시작하지 않은 수업 시간대의 정원을 관리합니다.</p></div>
      <button type="button" className="timeslot-primary" disabled={command.isPending} onClick={() => beginEditor()}>수동 시간대 생성</button></header>
    <div className="timeslot-context"><p>정규 시간표 변경과 휴무·휴강 예약 정리는 각 관리 화면에서 진행합니다.</p>
      <div><Link to="/admin/schedule-configuration">정규 시간표 관리</Link><Link to="/admin/schedule-closures">휴무·휴강 관리</Link></div></div>
    {result ? <section className="timeslot-feedback" role="status" aria-label="이번 처리 결과"><h2 ref={resultRef} tabIndex={-1}>이번 처리 결과</h2>
      <p>{slotLabel(result.slot)}</p><strong>{result.message}</strong>
      <p>전체 {result.slot.totalCapacity}명 · 원형마장 {result.slot.roundArenaCapacity}명 · {result.slot.closed ? '신규 예약 마감' : '마감 표시 없음'}</p></section> : null}
    {error && !editor ? <div ref={errorRef} tabIndex={-1} className="timeslot-alert" role="alert"><p>{error}</p><Link to="/admin/schedule-closures">휴무·휴강 상태 확인</Link></div> : null}
    {query.isPending ? <p className="timeslot-empty" role="status">시간대 목록을 불러오는 중입니다.</p>
      : query.isError ? <div className="timeslot-alert" role="alert"><p>{result ? '요청은 처리되었지만 최신 시간대 목록을 다시 불러오지 못했습니다.' : '시간대 목록을 불러오지 못했습니다.'}</p><button type="button" onClick={() => void query.refetch()}>목록 다시 조회</button></div>
        : groups.size === 0 ? <p className="timeslot-empty">아직 시작하지 않은 등록 시간대가 없습니다.</p>
          : <div className="timeslot-days" aria-label="날짜별 시간대 목록">{[...groups].map(([date, slots]) => <section className="timeslot-day" key={date} aria-labelledby={`timeslot-date-${date}`}>
            <div className="timeslot-day-heading"><h2 id={`timeslot-date-${date}`}>{dateLabel(slots[0].lessonDate)}</h2><span>{slots.length}개 시간대</span></div>
            <ul className="timeslot-list">{slots.map((slot) => <li key={slot.id}><article className="timeslot-card" aria-label={`${slotLabel(slot)} 시간대`}>
              <header><h3>{slot.startTime.slice(0, 5)}</h3><span className={`timeslot-badge${slot.closed ? ' closed' : ''}`}>{slot.closed ? '신규 예약 마감' : '마감 표시 없음'}</span></header>
              <dl className="timeslot-capacity-summary"><div><dt>전체 정원</dt><dd>{slot.totalCapacity}명</dd></div><div><dt>원형마장 정원</dt><dd>{slot.roundArenaCapacity}명</dd></div></dl>
              <details className="timeslot-disclosure"><summary aria-label={`${slotLabel(slot)} 클래스별 정원 보기`}>9개 클래스별 정원</summary><CapacitySummary request={slot} /></details>
              <div className="timeslot-actions"><button type="button" className="timeslot-primary" disabled={command.isPending} onClick={() => beginEditor(slot)}>정원 수정</button>
                <details className="timeslot-operation-details"><summary>마감·재개 요청</summary><p>표시된 마감 여부만으로 관리자 휴강 상태를 판단하지 않습니다. 실제 처리 가능 여부는 서버가 확인합니다.</p>
                  <button type="button" disabled={command.isPending} onClick={() => beginStatus(slot, true)}>신규 예약 마감</button>
                  <button type="button" disabled={command.isPending} onClick={() => beginStatus(slot, false)}>관리자 마감 철회·재개</button></details></div>
            </article></li>)}</ul>
          </section>)}</div>}
    {editor || operation ? <TimeSlotActionDialog title={title} pending={command.isPending} onClose={closeDialog}>
      {operation ? <>
        <p className="timeslot-help">서버 계산 미리보기가 아닌 처리 내용 확인입니다.</p>
        <h3>{operation.kind === 'create' ? `${dateLabel(operation.request.lessonDate as Date)} · ${operation.request.startTime}` : slotLabel(operation.slot)}</h3>
        {operation.kind === 'status' ? <div className="timeslot-warning"><p>현재 표시: {operation.slot.closed ? '신규 예약 마감' : '마감 표시 없음'}</p>
          <p>{operation.closed ? '관리자 휴강을 시작하고 신규 예약을 차단합니다. 기존 예약은 자동 취소되지 않으며, 영향 예약은 휴무·휴강 관리에서 별도로 처리합니다.'
            : '진행 중인 관리자 휴강은 영향 예약을 아직 처리하지 않은 경우에만 철회할 수 있습니다. 완료된 휴강은 관리자 마감 원인만 해제합니다. 다른 마감 원인이 남으면 신규 예약은 계속 마감입니다.'}</p></div>
          : <><dl className="timeslot-capacity-summary"><div><dt>전체 정원</dt><dd>{operation.kind === 'capacity' ? `${operation.slot.totalCapacity} → ` : ''}{operation.request.totalCapacity}명</dd></div>
            <div><dt>원형마장 정원</dt><dd>{operation.kind === 'capacity' ? `${operation.slot.roundArenaCapacity} → ` : ''}{operation.request.roundArenaCapacity}명</dd></div></dl>
            <CapacitySummary request={operation.request} previous={operation.kind === 'capacity' ? operation.slot : undefined} />
            <p className="timeslot-warning">{operation.kind === 'capacity' ? '현재 예약 점유보다 작은 정원은 서버가 거부합니다. 개별 정원을 저장하면 이후 정규 시간표 정원 동기화에서 제외됩니다. 기존 예약은 자동 취소되지 않습니다.' : '수동 시간대를 생성합니다. 정원·날짜·시각의 최종 허용 여부는 서버가 확인합니다.'}</p></>}
        <footer><button type="button" disabled={command.isPending} onClick={() => editor ? setOperation(undefined) : closeDialog()}>{editor ? '입력으로 돌아가기' : '돌아가기'}</button>
          <button type="button" className="timeslot-primary" disabled={command.isPending} onClick={confirm}>{command.isPending ? '처리 중' : '확인 후 적용'}</button></footer>
      </> : editor ? <form onSubmit={review} noValidate>
        {editor.slot ? <><p>{slotLabel(editor.slot)}</p><p className="timeslot-warning">개별 정원 저장은 정규 시간표의 이후 정원 동기화에서 이 시간대를 제외합니다.</p></>
          : <div className="timeslot-form-grid"><Field name="lessonDate" label="수업 날짜" type="date" value={editor.lessonDate} error={fieldErrors.lessonDate} onChange={(value) => setEditor({ ...editor, lessonDate: value })} />
            <Field name="startTime" label="시작 시각" type="time" value={editor.startTime} error={fieldErrors.startTime} onChange={(value) => setEditor({ ...editor, startTime: value })} /></div>}
        <p className="timeslot-help" id="timeslot-capacity-help">전체 정원은 최대 8명, 원형마장은 최대 4명이며 전체 정원 이하여야 합니다. 클래스별 정원의 합계를 전체 정원과 맞출 필요는 없습니다.</p>
        <div className="timeslot-form-grid"><Field name="totalCapacity" label="전체 정원" value={editor.capacity.totalCapacity} error={fieldErrors.totalCapacity} maximum={8} onChange={(value) => setEditor({ ...editor, capacity: { ...editor.capacity, totalCapacity: value } })} />
          <Field name="roundArenaCapacity" label="원형마장 정원" value={editor.capacity.roundArenaCapacity} error={fieldErrors.roundArenaCapacity} maximum={4} onChange={(value) => setEditor({ ...editor, capacity: { ...editor.capacity, roundArenaCapacity: value } })} /></div>
        <fieldset className="timeslot-class-fields"><legend>클래스별 정원</legend><p className="timeslot-help">각 클래스는 0~8명입니다. 하나의 시간대에 여러 클래스 예약이 함께 존재할 수 있습니다.</p>
          {fieldErrors.classCapacities ? <p id="timeslot-class-error" className="timeslot-field-error" role="alert">{fieldErrors.classCapacities}</p> : null}
          <div className="timeslot-form-grid">{CLASS_FIELDS.map(([key, label]) => <Field key={key} name={key} label={`${label} 정원`} value={editor.capacity.classCapacities[key]} maximum={8}
            error={fieldErrors[key]} groupError={Boolean(fieldErrors.classCapacities)} onChange={(value) => setEditor({ ...editor, capacity: { ...editor.capacity, classCapacities: { ...editor.capacity.classCapacities, [key]: value } } })} />)}</div></fieldset>
        {error ? <p className="timeslot-alert" role="alert">{error}</p> : null}
        <footer><button type="button" disabled={command.isPending} onClick={closeDialog}>돌아가기</button><button type="submit" className="timeslot-primary" disabled={command.isPending}>변경 내용 확인</button></footer>
      </form> : null}
    </TimeSlotActionDialog> : null}
  </main>
}

function Field({ name, label, value, onChange, error, groupError, maximum, type = 'number' }: {
  name: string; label: string; value: string; onChange(value: string): void; error?: string; groupError?: boolean; maximum?: number; type?: string
}) {
  const id = `timeslot-field-${name}`
  return <div className="timeslot-field"><label htmlFor={id}>{label}</label><input id={id} type={type} value={value} min={type === 'number' ? 0 : undefined} max={maximum} step={type === 'number' ? 1 : undefined}
    aria-invalid={Boolean(error || groupError)} aria-describedby={[type === 'number' ? 'timeslot-capacity-help' : '', error ? `${id}-error` : '', groupError ? 'timeslot-class-error' : ''].filter(Boolean).join(' ') || undefined}
    onChange={(event) => onChange(event.target.value)} />{error ? <p className="timeslot-field-error" role="alert" id={`${id}-error`}>{error}</p> : null}</div>
}
function CapacitySummary({ request, previous }: { request: TimeSlotCapacityUpdateRequest; previous?: TimeSlotResponse }) {
  return <dl className="timeslot-class-summary">{CLASS_FIELDS.map(([key, label]) => <div key={key}><dt>{label}</dt><dd>{previous ? `${previous.classCapacities[key]} → ` : ''}{request.classCapacities?.[key]}명</dd></div>)}</dl>
}
