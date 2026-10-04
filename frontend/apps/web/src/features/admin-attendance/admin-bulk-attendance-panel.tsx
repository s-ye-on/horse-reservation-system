import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import type { AdminReservationResponse, BulkReservationAttendanceItemRequest, BulkReservationAttendanceResponse, ReservationCompletionResponse, ReservationNoShowResponse } from '@horse/api-client'
import type { AdminAttendanceApi } from './admin-attendance.api'
import { attendanceClassLabel, attendanceCouponTypeLabel, attendanceErrorMessage, attendanceRequestError } from './attendance-feedback'

type Action = 'complete' | 'no_show'
interface Draft { selected: boolean; action: Action | ''; couponAction: string; memo: string; error?: string }
interface Confirmation { reservations: AdminReservationResponse[]; items: BulkReservationAttendanceItemRequest[]; bulk: boolean }
interface Feedback { response: BulkReservationAttendanceResponse; names: Record<number, string>; completion?: ReservationCompletionResponse; noShow?: ReservationNoShowResponse }
const emptyDraft = (): Draft => ({ selected: false, action: '', couponAction: '', memo: '' })
const dateValue = (value: Date) => value.toISOString().slice(0, 10)
const groupKey = (reservation: AdminReservationResponse) => `${dateValue(reservation.lessonDate)}|${reservation.startTime}`
const canSelect = (reservation: AdminReservationResponse) => reservation.actions.complete.allowed || reservation.actions.noShow.allowed
const actionLabel = (action: string) => action === 'complete' ? '수업 완료' : '노쇼'
const couponLabel = (action?: string | null) => action === 'deduct' ? '쿠폰 1회 차감' : action === 'return' ? '쿠폰 점유 반환' : '별도 쿠폰 처리 없음'

export function AdminBulkAttendancePanel({ reservations, api, onProcessed }: {
  reservations: AdminReservationResponse[]; api: AdminAttendanceApi; onProcessed(): Promise<void>
}) {
  const [drafts, setDrafts] = useState<Record<number, Draft>>({})
  const [confirmation, setConfirmation] = useState<Confirmation>()
  const [result, setResult] = useState<Feedback>()
  const [error, setError] = useState<string>()
  const dialogRef = useRef<HTMLDialogElement>(null)
  const titleRef = useRef<HTMLHeadingElement>(null)
  const resultRef = useRef<HTMLElement>(null)
  const errorRef = useRef<HTMLParagraphElement>(null)
  const opener = useRef<HTMLElement | null>(null)
  const locked = useRef(false)
  const groups = useMemo(() => {
    const map = new Map<string, AdminReservationResponse[]>()
    for (const reservation of reservations) {
      const key = groupKey(reservation)
      map.set(key, [...(map.get(key) ?? []), reservation])
    }
    return [...map.entries()].sort(([a], [b]) => a.localeCompare(b))
  }, [reservations])

  const mutation = useMutation({
    retry: false,
    mutationFn: async (command: Confirmation): Promise<Feedback> => {
      const names = Object.fromEntries(command.reservations.map((reservation) => [reservation.reservationId, reservation.memberName]))
      if (command.bulk) return { names, response: await api.processBulk(dateValue(command.reservations[0].lessonDate), command.reservations[0].startTime, command.items) }
      const item = command.items[0]
      if (item.action === 'complete') {
        const completion = await api.complete(item.reservationId)
        return { names, completion, response: { requestedCount: 1, succeededCount: 1, failedCount: 0, items: [{ reservationId: completion.reservationId, action: item.action, success: true, status: completion.status, errorCode: null, errorMessage: null }] } }
      }
      const response = await api.noShow(item.reservationId, item.couponAction ?? '', item.memo ?? '')
      return { names, noShow: response, response: { requestedCount: 1, succeededCount: 1, failedCount: 0, items: [{ reservationId: response.reservationId, action: item.action, success: true, status: response.status, errorCode: null, errorMessage: null }] } }
    },
    onSuccess: (feedback, command) => {
      setResult(feedback)
      setDrafts((current) => {
        const next = { ...current }
        for (const item of command.items) next[item.reservationId] = { ...(next[item.reservationId] ?? emptyDraft()), selected: false }
        return next
      })
    },
    onError: async (failure) => { setError(await attendanceRequestError(failure)) },
    onSettled: async (_data, failure) => {
      setConfirmation(undefined)
      dialogRef.current?.close()
      try { await onProcessed() } catch { setError('최신 예약을 확인하지 못했습니다. 다시 불러와 주세요.') }
      locked.current = false
      requestAnimationFrame(() => (failure ? errorRef.current : resultRef.current)?.focus())
    },
  })

  useEffect(() => {
    if (!confirmation) return
    dialogRef.current?.showModal()
    titleRef.current?.focus()
  }, [confirmation])

  const update = (id: number, value: Partial<Draft>) => {
    setDrafts((current) => ({ ...current, [id]: { ...(current[id] ?? emptyDraft()), ...value, error: undefined } }))
    setError(undefined)
  }
  const close = () => {
    if (locked.current) return
    dialogRef.current?.close()
    setConfirmation(undefined)
    if (opener.current?.isConnected) opener.current.focus()
  }
  const prepare = (targets: AdminReservationResponse[], bulk: boolean, singleAction?: Action) => {
    if (locked.current) return
    setError(undefined)
    if (!targets.length || targets.length > 8 || new Set(targets.map(groupKey)).size !== 1) {
      setError('같은 날짜와 시작 시각의 예약을 1~8건 선택해 주세요.')
      return
    }
    let invalidId: number | undefined
    let invalidField = 'action'
    const nextDrafts = { ...drafts }
    const items = targets.map((reservation) => {
      const id = reservation.reservationId
      const draft = { ...(drafts[id] ?? emptyDraft()), ...(singleAction ? { action: singleAction } : {}) }
      let message = ''
      let field = 'action'
      const allowed = draft.action === 'complete' ? reservation.actions.complete.allowed : draft.action === 'no_show' ? reservation.actions.noShow.allowed : false
      if (!draft.action) message = '각 예약의 처리 결과를 선택해 주세요.'
      else if (!allowed) message = '현재 선택한 처리는 불가능합니다. 최신 상태를 확인해 주세요.'
      else if (draft.action === 'no_show') {
        if (reservation.paymentSource === 'coupon' && !['deduct', 'return'].includes(draft.couponAction)) { message = '쿠폰 처리 방식을 선택해 주세요.'; field = 'coupon' }
        else if (!draft.memo.trim() || draft.memo.trim().length > 500) { message = '관리자 메모는 1자 이상 500자 이하로 입력해 주세요.'; field = 'memo' }
      }
      nextDrafts[id] = { ...draft, error: message || undefined }
      if (message && invalidId === undefined) { invalidId = id; invalidField = field }
      return { reservationId: id, action: draft.action, ...(draft.action === 'no_show' ? { couponAction: reservation.paymentSource === 'coupon' ? draft.couponAction : 'none', memo: draft.memo.trim() } : {}) }
    })
    setDrafts(nextDrafts)
    if (invalidId !== undefined) {
      requestAnimationFrame(() => document.getElementById(`attendance-${invalidField}-${invalidId}`)?.focus())
      return
    }
    opener.current = document.activeElement as HTMLElement
    setConfirmation({ reservations: targets, items, bulk })
  }
  const execute = () => {
    if (!confirmation || locked.current) return
    // Recheck the latest query data; command-time server validation remains authoritative.
    const stale = confirmation.items.some((item) => {
      const latest = reservations.find((reservation) => reservation.reservationId === item.reservationId)
      return !latest || groupKey(latest) !== groupKey(confirmation.reservations[0]) || !(item.action === 'complete' ? latest.actions.complete.allowed : latest.actions.noShow.allowed)
    })
    if (stale) {
      close()
      setError('예약 상태가 변경되었습니다. 최신 상태를 확인해 주세요.')
      void onProcessed().catch(() => setError('최신 예약을 확인하지 못했습니다. 다시 불러와 주세요.'))
      requestAnimationFrame(() => errorRef.current?.focus())
      return
    }
    locked.current = true
    setResult(undefined)
    mutation.mutate(confirmation)
  }

  return <div className="attendance-workspace">
    {result ? <section className="attendance-result" ref={resultRef} tabIndex={-1} role="status" aria-labelledby="attendance-result-title">
      <h2 id="attendance-result-title">이번 처리 결과</h2>
      <p>요청 {result.response.requestedCount}건 · 성공 {result.response.succeededCount}건 · 실패 {result.response.failedCount}건</p>
      <p className="attendance-help">성공한 예약은 확정 예약 목록에서 제외될 수 있습니다.</p>
      <ul>{result.response.items.map((item, index) => <li key={`${item.reservationId}-${index}`} className={item.success ? 'success' : 'failure'} role={item.success ? undefined : 'alert'}>
        <div><strong>{result.names[item.reservationId] ?? '예약'} · 예약 번호 {item.reservationId}</strong><p>{item.success ? `${actionLabel(item.action)} 기록 성공` : attendanceErrorMessage(item.errorCode)}</p></div><span>{actionLabel(item.action)}</span>
      </li>)}</ul>
      {result.completion ? <p>일반 {result.completion.generalRideCount}회 · 마장마술 {result.completion.dressageRideCount}회 · 장애물 {result.completion.jumpingRideCount}회</p> : null}
      {result.noShow ? <p>{couponLabel(result.noShow.couponAction)}</p> : null}
    </section> : null}
    {error ? <p className="attendance-alert" role="alert" ref={errorRef} tabIndex={-1}>{error}</p> : null}
    {groups.length === 0 ? <section className="attendance-state"><h2>현재 불러온 확정 예약이 없습니다.</h2><p>새 예약이 확정되면 날짜와 시작 시각별로 표시됩니다.</p></section> : null}
    {groups.map(([key, entries]) => {
      const selected = entries.filter((reservation) => drafts[reservation.reservationId]?.selected)
      const groupId = `attendance-group-${key.replace(/[^0-9]/g, '')}`
      return <section className="attendance-group" key={key} aria-labelledby={groupId}>
        <header className="attendance-group-header"><div><p className="attendance-eyebrow">운영 시간대</p><h2 id={groupId}>{dateValue(entries[0].lessonDate).replaceAll('-', '.')} · {entries[0].startTime.slice(0, 5)}</h2><p>이 시간대의 확정 예약 {entries.length}건 · 수업 종류는 각 예약에 표시됩니다.</p></div><span className="attendance-badge">{entries.some(canSelect) ? '처리 가능 예약 있음' : '처리 가능 예약 없음'}</span></header>
        <div className="attendance-list">{entries.map((reservation) => {
          const id = reservation.reservationId, draft = drafts[id] ?? emptyDraft(), isCoupon = reservation.paymentSource === 'coupon'
          const invalid = draft.error
          return <article className="attendance-card" key={id} aria-labelledby={`attendance-member-${id}`}>
            <div className="attendance-card-top">
              <label className="attendance-checkbox"><input type="checkbox" aria-label={`${reservation.memberName} 예약 번호 ${id} 일괄 처리 선택`} checked={draft.selected} disabled={!canSelect(reservation) || mutation.isPending || (!draft.selected && selected.length >= 8)} onChange={(event) => update(id, { selected: event.target.checked })} /></label>
              <div><h3 id={`attendance-member-${id}`}>{reservation.memberName}</h3><p className="attendance-meta"><span>예약 번호 {id}</span><span>{dateValue(reservation.lessonDate).replaceAll('-', '.')} · {reservation.startTime.slice(0, 5)}</span><span>수업 종류 <strong>{attendanceClassLabel(reservation.classType)}</strong></span><span>예약 확정</span><span>{isCoupon ? attendanceCouponTypeLabel(reservation.coupon?.couponType) : '단건 결제'}</span></p></div>
              <span className={`attendance-badge ${canSelect(reservation) ? 'ready' : 'blocked'}`}>{canSelect(reservation) ? '처리 가능' : '처리 불가'}</span>
            </div>
            {!canSelect(reservation) ? <p className="attendance-blocked">{attendanceErrorMessage(reservation.actions.complete.blockedReason ?? reservation.actions.noShow.blockedReason)}</p> : null}
            <div className="attendance-card-body">
              <div className="attendance-actions"><button type="button" disabled={!reservation.actions.complete.allowed || mutation.isPending} onClick={() => prepare([reservation], false, 'complete')}>수업 완료</button><button type="button" disabled={!reservation.actions.noShow.allowed || mutation.isPending} aria-expanded={draft.action === 'no_show'} aria-controls={`attendance-no-show-${id}`} onClick={() => { update(id, { action: draft.action === 'no_show' ? '' : 'no_show' }); requestAnimationFrame(() => document.getElementById(`attendance-${isCoupon ? 'coupon' : 'memo'}-${id}`)?.focus()) }}>노쇼 입력</button></div>
              <details className="attendance-details"><summary>연락처·결제 정보 보기</summary><dl><div><dt>연락처</dt><dd>{reservation.memberPhone}</dd></div><div><dt>결제 방식</dt><dd>{isCoupon ? '쿠폰 예약' : '단건 결제'}</dd></div>{reservation.coupon ? <div><dt>쿠폰 횟수</dt><dd>잔여 {reservation.coupon.remainingCount}회 · 예약 처리 중 {reservation.coupon.heldCount}회</dd></div> : null}</dl>{reservation.coupon ? <p className="attendance-help">쿠폰 번호 {reservation.coupon.couponId}</p> : null}</details>
              {draft.selected ? <div className="attendance-field"><label htmlFor={`attendance-action-${id}`}>{reservation.memberName} 처리 결과</label><select id={`attendance-action-${id}`} value={draft.action} disabled={mutation.isPending} aria-invalid={Boolean(invalid)} aria-describedby={invalid ? `attendance-error-${id}` : undefined} onChange={(event) => update(id, { action: event.target.value as Draft['action'] })}><option value="">결과 선택</option><option value="complete" disabled={!reservation.actions.complete.allowed}>수업 완료</option><option value="no_show" disabled={!reservation.actions.noShow.allowed}>노쇼</option></select></div> : null}
              <div id={`attendance-no-show-${id}`} hidden={draft.action !== 'no_show'}>
                {draft.action === 'no_show' ? <div className="attendance-no-show-fields">
                  {isCoupon ? <p className="attendance-help">처리 대상: {attendanceCouponTypeLabel(reservation.coupon?.couponType)}</p> : null}
                  {isCoupon ? <div className="attendance-field"><label htmlFor={`attendance-coupon-${id}`}>{reservation.memberName} 쿠폰 처리</label><select id={`attendance-coupon-${id}`} value={draft.couponAction} disabled={mutation.isPending} aria-invalid={Boolean(invalid?.includes('쿠폰'))} aria-describedby={`attendance-coupon-help-${id}${invalid ? ` attendance-error-${id}` : ''}`} onChange={(event) => update(id, { couponAction: event.target.value })}><option value="">선택해 주세요</option><option value="deduct">쿠폰 1회 차감</option><option value="return">쿠폰 점유 반환</option></select><p className="attendance-help" id={`attendance-coupon-help-${id}`}>반환 후 사용 가능 횟수는 최신 쿠폰 상태를 따릅니다.</p></div> : <p className="attendance-help">단건 결제 예약 · 별도 쿠폰 처리 없음</p>}
                  <div className="attendance-field"><label htmlFor={`attendance-memo-${id}`}>{reservation.memberName} 관리자 메모</label><textarea id={`attendance-memo-${id}`} value={draft.memo} required maxLength={500} disabled={mutation.isPending} aria-invalid={Boolean(invalid?.includes('메모'))} aria-describedby={`attendance-memo-help-${id}${invalid ? ` attendance-error-${id}` : ''}`} onChange={(event) => update(id, { memo: event.target.value })} /><p id={`attendance-memo-help-${id}`} className="attendance-help">앞뒤 공백을 제외하고 1~500자로 입력해 주세요.</p></div>
                  {!draft.selected ? <button type="button" onClick={() => prepare([reservation], false, 'no_show')} disabled={mutation.isPending || !reservation.actions.noShow.allowed}>노쇼 확인</button> : null}
                </div> : null}
              </div>
              {invalid ? <p className="attendance-field-error" id={`attendance-error-${id}`} role="alert">{invalid}</p> : null}
            </div>
          </article>
        })}</div>
        <footer className="attendance-bulk-bar"><p><strong>{selected.length}건 선택</strong> · 같은 날짜와 시작 시각의 예약만 함께 처리합니다. 최대 8건</p><button type="button" disabled={!selected.length || mutation.isPending} onClick={() => prepare(selected, true)}>선택 예약 함께 확인</button></footer>
      </section>
    })}
    {confirmation ? <dialog ref={dialogRef} className="attendance-dialog" aria-modal="true" aria-labelledby="attendance-confirm-title" aria-describedby="attendance-confirm-desc" onCancel={(event) => { event.preventDefault(); close() }} onKeyDown={(event) => {
      if (event.key !== 'Tab') return
      const nodes = Array.from(event.currentTarget.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href], [tabindex="0"]')).filter((node) => !node.closest('[hidden]'))
      const first = nodes[0], last = nodes[nodes.length - 1]
      if (!first) { event.preventDefault(); titleRef.current?.focus(); return }
      if (event.shiftKey && (document.activeElement === first || document.activeElement === titleRef.current)) { event.preventDefault(); last.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
    }}>
      <header className="attendance-dialog-header"><div><p className="attendance-eyebrow">처리 확인</p><h2 id="attendance-confirm-title" ref={titleRef} tabIndex={-1}>예약별 처리 내용을 확인해 주세요.</h2><p id="attendance-confirm-desc">서버 계산 미리보기가 아닌 처리 내용 확인입니다.</p></div><button type="button" aria-label="확인 창 닫기" disabled={mutation.isPending} onClick={close}>닫기</button></header>
      <div className="attendance-dialog-scroll"><dl className="attendance-dialog-summary"><div><dt>날짜·시작 시각</dt><dd>{dateValue(confirmation.reservations[0].lessonDate).replaceAll('-', '.')} · {confirmation.reservations[0].startTime.slice(0, 5)}</dd></div><div><dt>선택 예약</dt><dd>{confirmation.items.length}건</dd></div><div><dt>처리 종류</dt><dd>수업 완료 {confirmation.items.filter((item) => item.action === 'complete').length}건 · 노쇼 {confirmation.items.filter((item) => item.action === 'no_show').length}건</dd></div></dl>
        <ul className="attendance-dialog-items">{confirmation.items.map((item, index) => <li key={item.reservationId}><strong>{confirmation.reservations[index].memberName} · 예약 번호 {item.reservationId}</strong><p>{attendanceClassLabel(confirmation.reservations[index].classType)} · {actionLabel(item.action)}</p>{item.action === 'no_show' ? <><p>{confirmation.reservations[index].paymentSource === 'coupon' ? `${attendanceCouponTypeLabel(confirmation.reservations[index].coupon?.couponType)} · ${couponLabel(item.couponAction)}` : `단건 결제 · ${couponLabel(item.couponAction)}`}</p><p>관리자 메모: {item.memo}</p></> : null}</li>)}</ul>
        <p className="attendance-help">일괄 요청도 예약마다 독립적으로 처리됩니다. 성공한 예약은 다른 예약의 실패로 되돌아가지 않습니다.</p>
      </div>
      <footer className="attendance-actions attendance-dialog-actions"><button type="button" disabled={mutation.isPending} onClick={close}>돌아가기</button><button className="primary" type="button" disabled={mutation.isPending} onClick={execute}>{mutation.isPending ? '기록 중' : confirmation.bulk ? `선택 ${confirmation.items.length}건 결과 기록` : '이 예약 결과 기록'}</button></footer>
    </dialog> : null}
  </div>
}
