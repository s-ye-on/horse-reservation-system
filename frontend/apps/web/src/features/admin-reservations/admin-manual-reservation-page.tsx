import { useEffect, useRef, useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link } from 'react-router'
import type { AdminMemberResponse, ReservationApplicationResponse } from '@horse/api-client'
import { AdminReservationActionDialog } from './admin-reservation-action-dialog'
import { adminManualReservationApi, describeManualReservationError, type AdminManualReservationApi } from './admin-manual-reservation.api'
import { clearManualReservationAttempt, readManualReservationAttempt, saveManualReservationAttempt, type ManualReservationAttempt } from './admin-manual-reservation-attempt'
import './admin-reservations-page.css'
import './admin-manual-reservation-page.css'

const CLASSES: Record<string, string> = {
  FIRST_RIDE: '왕초보', ROUND_BEGINNER: '원형초보', ROUND_TROT: '원형 속보',
  LARGE_ARENA_BEGINNER: '대마장초보', LARGE_ARENA_TROT: '대마장 속보',
  CANTER_BEGINNER: '구보초보', CANTER: '구보', DRESSAGE: '마장마술', JUMPING: '장애물',
}
const CANDIDATES_KEY = ['admin', 'manual-reservation'] as const
const TIME_SLOTS_KEY = ['admin', 'timeslots'] as const
const STATUS_LABELS: Record<string, string> = { pending_admin_approval: '쿠폰 승인대기', pending_payment: '입금대기', confirmed: '예약 확정', payment_expired: '입금만료', approval_expired: '승인만료', rejected: '반려', cancelled: '취소', completed: '수업 완료', no_show: '노쇼' }
const formatDay = (date: Date) => new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: 'long', day: 'numeric', weekday: 'short' }).format(date)
const dateKey = (date: Date) => date.toISOString().slice(0, 10)
const formatDeadline = (date: Date) => new Intl.DateTimeFormat('ko-KR', { timeZone: 'Asia/Seoul', year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date)

function restoreAttempt(subject: string) {
  try { return { attempt: readManualReservationAttempt(subject), error: undefined } }
  catch { return { attempt: undefined, error: '저장된 생성 요청을 확인할 수 없습니다. 브라우저 저장소를 확인한 뒤 다시 열어 주세요.' } }
}

export function AdminManualReservationPage({ api = adminManualReservationApi, accountSubject }: { api?: AdminManualReservationApi; accountSubject: string }) {
  const queryClient = useQueryClient()
  const [saved] = useState(() => restoreAttempt(accountSubject))
  const [attempt, setAttempt] = useState<ManualReservationAttempt | undefined>(saved.attempt)
  const [uncertain, setUncertain] = useState(Boolean(saved.attempt))
  const [page, setPage] = useState(0)
  const [memberSearch, setMemberSearch] = useState('')
  const [searchQuery, setSearchQuery] = useState('')
  const [member, setMember] = useState<AdminMemberResponse>()
  const [day, setDay] = useState('')
  const [slotId, setSlotId] = useState('')
  const [classType, setClassType] = useState('')
  const [reason, setReason] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState<string | undefined>(saved.error)
  const [confirmation, setConfirmation] = useState<ManualReservationAttempt>()
  const [result, setResult] = useState<{ response: ReservationApplicationResponse; attempt: ManualReservationAttempt }>()
  const locked = useRef(false)
  const mounted = useRef(true)
  const execution = useRef<string | undefined>(undefined)
  const resultHeading = useRef<HTMLHeadingElement>(null)
  const feedback = useRef<HTMLDivElement>(null)
  const members = useQuery({ queryKey: [...CANDIDATES_KEY, 'members', page, searchQuery], queryFn: () => api.getMembers(page, 20, searchQuery || undefined), placeholderData: keepPreviousData })
  const slots = useQuery({ queryKey: TIME_SLOTS_KEY, queryFn: () => api.getTimeSlots() })
  const selectedMember = useQuery({ queryKey: [...CANDIDATES_KEY, 'member', member?.id], queryFn: () => api.getMember(member!.id), enabled: Boolean(member) && !attempt && !result })
  const currentReservation = useQuery({ queryKey: ['admin', 'actionable-reservations', 'focused', result?.response.reservationId], queryFn: () => api.getReservation(result!.response.reservationId), enabled: Boolean(result) })
  const dates = [...new Set((slots.data ?? []).map((slot) => dateKey(slot.lessonDate)))]
  const selectedDay = day || dates[0] || ''
  const dailySlots = (slots.data ?? []).filter((slot) => dateKey(slot.lessonDate) === selectedDay)
  const selectedSlot = slots.data?.find((slot) => slot.id === Number(slotId))
  const editingDisabled = Boolean(attempt || result || saved.error)
  const searchPending = memberSearch.trim() !== searchQuery
  const visibleMembers = members.data?.content ?? []

  useEffect(() => {
    if (!searchPending) return
    const timer = window.setTimeout(() => { setSearchQuery(memberSearch.trim()); setPage(0) }, 300)
    return () => window.clearTimeout(timer)
  }, [memberSearch, searchPending])

  useEffect(() => { mounted.current = true; return () => { mounted.current = false } }, [])
  const ownsExecution = (operation: ManualReservationAttempt) => mounted.current && execution.current === operation.executionId

  useEffect(() => {
    if (result && !confirmation) resultHeading.current?.focus()
    else if (message && !confirmation) feedback.current?.focus()
  }, [result, message, confirmation])
  useEffect(() => {
    if (!attempt || result) return
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = '' }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [attempt, result])

  const refreshOperationalData = async () => {
    await queryClient.invalidateQueries({ queryKey: ['admin', 'actionable-reservations'] })
    await queryClient.invalidateQueries({ queryKey: TIME_SLOTS_KEY })
    await queryClient.invalidateQueries({ queryKey: ['admin', 'reservation-adjustment', 'time-slots'] })
    await queryClient.invalidateQueries({ queryKey: ['admin', 'weekly-operations-calendar'] })
    await queryClient.invalidateQueries({ queryKey: CANDIDATES_KEY })
  }
  const creation = useMutation({
    mutationFn: (operation: ManualReservationAttempt) => api.create(operation.request, operation.key),
    retry: false,
    onSuccess: async (response, operation) => {
      if (!ownsExecution(operation)) return
      setResult({ response, attempt: operation })
      setUncertain(false)
      setMessage(undefined)
      try { if (!clearManualReservationAttempt(accountSubject, operation.executionId)) setMessage('다른 화면에서 진행 중인 요청이 있습니다. 새 예약 전에 해당 요청을 확인해 주세요.') } catch { setMessage('예약은 생성되었지만 저장된 요청을 정리하지 못했습니다. 새 예약 전에 브라우저 저장소를 확인해 주세요.') }
      await refreshOperationalData()
      if (!ownsExecution(operation)) return
      setConfirmation(undefined)
    },
    onError: async (error, operation) => {
      if (!ownsExecution(operation)) return
      const detail = await describeManualReservationError(error)
      if (!ownsExecution(operation)) return
      const mustKeep = uncertain || detail.uncertain
      setUncertain(mustKeep)
      setMessage(detail.message)
      setErrors(detail.fieldErrors ?? {})
      if (!mustKeep) {
        try { if (clearManualReservationAttempt(accountSubject, operation.executionId)) setAttempt(undefined); else { setUncertain(true); setMessage('다른 화면에서 진행 중인 요청이 있습니다. 입력을 변경하지 말고 해당 요청을 확인해 주세요.') } }
        catch { setUncertain(true); setMessage('저장된 요청을 정리하지 못했습니다. 입력을 변경하지 말고 브라우저 저장소를 확인해 주세요.') }
      }
      await refreshOperationalData()
      if (!ownsExecution(operation)) return
      setConfirmation(undefined)
    },
    onSettled: (_data, _error, operation) => { if (ownsExecution(operation)) locked.current = false },
  })

  const review = () => {
    const fields: Record<string, string> = {}
    if (!member || !selectedMember.isSuccess || selectedMember.isError) fields.memberId = '회원 정보를 확인하고 한 명을 선택해 주세요.'
    if (!selectedSlot || selectedSlot.closed || !slots.isSuccess) fields.timeSlotId = '현재 목록에서 마감되지 않은 수업 시간을 선택해 주세요.'
    if (!CLASSES[classType]) fields.classType = '수업 클래스를 선택해 주세요.'
    if (!reason.trim() || reason.length > 500) fields.reason = '사유는 공백을 제외한 1자 이상, 500자 이하로 입력해 주세요.'
    setErrors(fields)
    if (Object.keys(fields).length) {
      document.getElementById(Object.keys(fields)[0] === 'memberId' ? 'manual-member-heading' : `manual-${Object.keys(fields)[0]}`)?.focus()
      return
    }
    setMessage(undefined)
    setConfirmation({ key: crypto.randomUUID(), request: { memberId: member!.id, timeSlotId: selectedSlot!.id, classType, reason: reason.trim() }, memberName: selectedMember.data!.name, memberPhone: selectedMember.data!.phone, lessonDate: dateKey(selectedSlot!.lessonDate), startTime: selectedSlot!.startTime })
  }
  const submit = (operation: ManualReservationAttempt) => {
    if (locked.current) return
    const submission = { ...operation, executionId: crypto.randomUUID() }
    try { saveManualReservationAttempt(accountSubject, submission) }
    catch { setMessage('요청을 안전하게 보관할 수 없어 생성하지 않았습니다. 브라우저 저장소를 확인해 주세요.'); setConfirmation(undefined); return }
    locked.current = true
    execution.current = submission.executionId
    setAttempt(submission)
    creation.mutate(submission)
  }
  const reset = () => {
    try {
      if (readManualReservationAttempt(accountSubject) && !clearManualReservationAttempt(accountSubject, result?.attempt.executionId)) { setMessage('다른 화면에서 진행 중인 요청이 있습니다. 해당 요청을 확인해 주세요.'); return }
    } catch { setMessage('저장된 요청을 정리하지 못했습니다. 브라우저 저장소를 확인해 주세요.'); return }
    setAttempt(undefined); setUncertain(false); setResult(undefined); setMember(undefined); setMemberSearch(''); setSlotId(''); setClassType(''); setReason(''); setErrors({}); setMessage(undefined); creation.reset()
    document.getElementById('manual-member-heading')?.focus()
  }
  const fieldError = (field: string) => errors[field] ? <span id={`manual-${field}-error`} className="admin-manual-field-error" role="alert">{errors[field]}</span> : null

  return <main className="admin-reservations-page admin-manual-page">
    <header className="admin-reservations-header"><div><p className="admin-reservations-eyebrow">RESERVATION OPERATIONS</p><h1>수동 예약 추가</h1><p>회원과 수업을 선택하고 관리자 사유를 남깁니다.</p></div><Link className="admin-manual-back" to="/admin/reservations">예약 관리로 돌아가기</Link></header>
    {message ? <div className="admin-manual-alert" ref={feedback} tabIndex={-1} role="alert">{message}</div> : null}
    {result ? <section className="admin-reservations-result admin-manual-result" role="status"><p className="admin-reservations-eyebrow">생성 결과</p><h2 ref={resultHeading} tabIndex={-1}>{result.response.status === 'confirmed' ? '예약이 확정되었습니다' : result.response.status === 'pending_payment' ? '입금 확인을 기다리고 있습니다' : '생성 응답을 확인했습니다'}</h2>
      <p>{result.attempt.memberName}님 · 예약 번호 {result.response.reservationId}</p>
      <p>위 내용은 최초 생성 응답입니다. 현재 상태: {currentReservation.isFetching ? '확인 중' : currentReservation.data ? STATUS_LABELS[currentReservation.data.status] ?? '예약 상세에서 확인' : '확인 필요'}</p>
      {currentReservation.isError ? <div role="alert"><p>생성은 반영되었지만 현재 예약 상태를 조회하지 못했습니다.</p><button type="button" onClick={() => void currentReservation.refetch()}>현재 예약 다시 조회</button></div> : null}
      <dl className="admin-reservation-dialog-facts"><div><dt>수업</dt><dd>{formatDay(result.response.lessonDate)} · {result.response.startTime.slice(0, 5)}</dd></div><div><dt>클래스</dt><dd>{CLASSES[result.response.classType] ?? '수업 클래스 확인 필요'}</dd></div>
        <div><dt>결제 방식</dt><dd>{result.response.paymentSource === 'coupon' ? '쿠폰 예약' : result.response.paymentSource === 'single_payment' ? '단건 결제' : '예약 상세에서 확인'}</dd></div>
        {result.response.status === 'pending_payment' ? <div><dt>입금 마감 (한국 시간)</dt><dd>{result.response.paymentDueAt ? formatDeadline(result.response.paymentDueAt) : '예약 상세에서 확인해 주세요.'}</dd></div> : null}</dl>
      {result.response.coupon ? <details className="admin-reservation-metadata"><summary>처리된 쿠폰 정보</summary><dl className="admin-reservation-details"><div><dt>쿠폰 번호</dt><dd>{result.response.coupon.couponId}</dd></div><div><dt>서버 응답의 잔여 / 점유 횟수</dt><dd>{result.response.coupon.remainingCount}회 / {result.response.coupon.heldCount}회</dd></div></dl><p>현재 쿠폰 상태는 최신 조회를 따릅니다.</p></details> : null}
      <div className="admin-manual-actions"><Link to={`/admin/reservations?reservationId=${result.response.reservationId}`}>생성 예약 확인</Link><button type="button" onClick={reset} disabled={creation.isPending}>새 예약 추가</button></div>
    </section> : attempt ? <section className="admin-manual-unresolved" aria-labelledby="manual-unresolved-title"><h2 id="manual-unresolved-title">생성 요청 확인</h2><p>{creation.isPending ? '예약을 생성하고 있습니다.' : '이전 요청의 생성 여부가 아직 확인되지 않았습니다. 입력을 변경하지 않고 같은 요청을 다시 보냅니다.'}</p><AttemptFacts attempt={attempt} /><p className="admin-manual-note">선택 정보는 요청 당시의 내용입니다. 현재 예약 상태는 서버 응답과 최신 목록에서 확인합니다.</p><button type="button" disabled={creation.isPending} onClick={() => submit(attempt)}>{creation.isPending ? '생성 중' : '동일 요청 결과 확인'}</button></section> : null}
    {!result && !attempt ? <div className="admin-manual-workspace">
      <section aria-labelledby="manual-member-heading"><div className="admin-manual-section-title"><span>01</span><h2 id="manual-member-heading" tabIndex={-1}>회원 선택</h2></div>{fieldError('memberId')}
        <label htmlFor="manual-member-search">이름 또는 전화번호 검색</label>
        <div className="admin-manual-member-search"><input id="manual-member-search" type="search" value={memberSearch} maxLength={100} disabled={editingDisabled} aria-describedby="manual-member-search-help" onChange={(event) => setMemberSearch(event.target.value)} /><button type="button" disabled={editingDisabled || !memberSearch} onClick={() => setMemberSearch('')}>검색 지우기</button></div>
        <p id="manual-member-search-help" className="admin-manual-note">전체 회원의 이름 또는 전화번호를 검색합니다.</p>
        {member ? <p className="admin-manual-selection">선택 회원 <strong>{member.name}</strong><span>{member.phone}</span></p> : null}
        {members.isPending || searchPending ? <p role="status">회원 목록을 불러오는 중입니다.</p> : members.isError ? <div role="alert"><p>회원 목록을 불러오지 못했습니다.</p><button type="button" onClick={() => void members.refetch()}>회원 목록 다시 조회</button></div> : <>
          {members.isPlaceholderData ? <p role="status">회원 목록을 불러오는 중입니다.</p> : <p className="admin-manual-note" role="status">{searchQuery ? '검색 결과' : '전체 회원'} {members.data?.totalElements ?? 0}명 · 현재 페이지 {visibleMembers.length}명</p>}
          {members.isPlaceholderData ? null : !visibleMembers.length ? <p className="admin-reservations-empty">{searchQuery ? '검색 결과가 없습니다.' : '표시할 회원이 없습니다.'}</p> : <ul className="admin-manual-member-list">{visibleMembers.map((candidate) => <li key={candidate.id}><button type="button" aria-current={member?.id === candidate.id ? 'true' : undefined} disabled={editingDisabled} onClick={() => { setMember(candidate); setSlotId(''); setClassType(''); setReason(''); setErrors({}); setMessage(undefined) }}><strong>{candidate.name}</strong><span>{candidate.phone}</span><small>{member?.id === candidate.id ? '선택됨' : '선택'}</small></button></li>)}</ul>}
          <nav className="admin-reservations-pagination" aria-label="회원 목록 페이지"><button type="button" disabled={page === 0 || members.isFetching || editingDisabled} onClick={() => setPage(page - 1)}>이전</button><span>{page + 1} / {Math.max(members.data?.totalPages ?? 0, 1)}</span><button type="button" disabled={!members.data?.hasNext || members.isFetching || editingDisabled} onClick={() => setPage(page + 1)}>다음</button></nav>
        </>}
      </section>
      <section aria-labelledby="manual-lesson-heading"><div className="admin-manual-section-title"><span>02</span><h2 id="manual-lesson-heading">수업과 사유</h2></div>
        {selectedMember.isFetching ? <p role="status">선택한 회원 정보를 확인하는 중입니다.</p> : null}
        {selectedMember.isError ? <div role="alert"><p>선택 회원 정보를 확인할 수 없습니다.</p><button type="button" onClick={() => void selectedMember.refetch()}>회원 정보 다시 조회</button></div> : null}
        {slots.isPending ? <p role="status">수업 시간을 불러오는 중입니다.</p> : null}
        {slots.isError ? <div role="alert"><p>수업 시간을 불러오지 못했습니다.</p><button type="button" onClick={() => void slots.refetch()}>수업 시간 다시 조회</button></div> : null}
        {slots.isSuccess && !slots.data.length ? <p className="admin-reservations-empty">현재 조회된 시작 전 수업 시간이 없습니다.</p> : null}
        <form onSubmit={(event) => { event.preventDefault(); review() }} noValidate>
          <fieldset disabled={editingDisabled || !member}><legend className="admin-manual-sr-only">수동 예약 입력</legend>
            <label htmlFor="manual-day">수업 날짜</label><select id="manual-day" value={selectedDay} disabled={!slots.isSuccess} onChange={(event) => { setDay(event.target.value); setSlotId(''); setClassType(''); setErrors({}) }}><option value="">날짜 선택</option>{dates.map((value) => <option key={value} value={value}>{formatDay(new Date(value))}</option>)}</select>
            <label htmlFor="manual-timeSlotId">수업 시간</label><select id="manual-timeSlotId" value={slotId} disabled={!slots.isSuccess} aria-invalid={Boolean(errors.timeSlotId)} aria-describedby={`manual-slot-help${errors.timeSlotId ? ' manual-timeSlotId-error' : ''}`} onChange={(event) => { setSlotId(event.target.value); setClassType(''); setErrors({}) }}><option value="">시간 선택</option>{dailySlots.map((slot) => <option key={slot.id} value={slot.id} disabled={slot.closed}>{slot.startTime.slice(0, 5)}{slot.closed ? ' · 신규 예약 마감' : ''}</option>)}</select>{fieldError('timeSlotId')}
            <p id="manual-slot-help" className="admin-manual-note">정원·중복 예약·운영 상태는 생성 시 서버가 최종 확인합니다.</p>
            {selectedSlot ? <p className="admin-manual-note">전체 정원 {selectedSlot.totalCapacity}명 · 원형마장 정원 {selectedSlot.roundArenaCapacity}명</p> : null}
            <label htmlFor="manual-classType">수업 클래스</label><select id="manual-classType" value={classType} disabled={!selectedSlot} aria-invalid={Boolean(errors.classType)} aria-describedby={`manual-class-help${errors.classType ? ' manual-classType-error' : ''}`} onChange={(event) => { setClassType(event.target.value); setErrors({}) }}><option value="">클래스 선택</option>{Object.entries(CLASSES).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select>{fieldError('classType')}
            <p id="manual-class-help" className="admin-manual-note">클래스 목록은 회원의 예약 가능 자격을 보장하지 않습니다. 서버가 현재 자격을 확인합니다.</p>
            <label htmlFor="manual-reason">관리자 사유</label><textarea id="manual-reason" value={reason} maxLength={500} required aria-invalid={Boolean(errors.reason)} aria-describedby={`manual-reason-help${errors.reason ? ' manual-reason-error' : ''}`} onChange={(event) => { setReason(event.target.value); setErrors((current) => ({ ...current, reason: '' })) }} /><p id="manual-reason-help" className="admin-manual-note">필수 · 공백 불가 · 최대 500자 ({reason.length}/500)</p>{fieldError('reason')}
          </fieldset>
          <p className="admin-manual-policy">적합한 쿠폰은 서버가 자동으로 선택합니다. 쿠폰이 있으면 예약 확정, 없으면 입금대기로 생성됩니다.</p>
          <button type="submit" disabled={editingDisabled || !member || selectedMember.isFetching || !slots.isSuccess}>처리 내용 확인</button>
        </form>
      </section>
    </div> : null}
    {confirmation ? <AdminReservationActionDialog title="수동 예약 생성 확인" pending={creation.isPending} onClose={() => setConfirmation(undefined)}><AttemptFacts attempt={confirmation} /><p className="admin-manual-note">서버 계산 미리보기가 아닌 입력 내용 확인입니다. 자격·정원·일정 상태와 쿠폰은 서버가 최종 판단합니다.</p><div className="admin-reservation-actions"><button type="button" className="secondary" disabled={creation.isPending} onClick={() => setConfirmation(undefined)}>입력 수정</button><button type="button" disabled={creation.isPending} onClick={() => submit(confirmation)}>{creation.isPending ? '생성 중' : '예약 생성 확인'}</button></div></AdminReservationActionDialog> : null}
  </main>
}

function AttemptFacts({ attempt }: { attempt: ManualReservationAttempt }) {
  return <dl className="admin-reservation-dialog-facts"><div><dt>회원</dt><dd>{attempt.memberName}<br />{attempt.memberPhone}</dd></div><div><dt>수업 시간</dt><dd>{formatDay(new Date(attempt.lessonDate))} · {attempt.startTime.slice(0, 5)}</dd></div><div><dt>클래스</dt><dd>{CLASSES[attempt.request.classType] ?? '클래스 확인 필요'}</dd></div><div><dt>관리자 사유</dt><dd>{attempt.request.reason}</dd></div></dl>
}
