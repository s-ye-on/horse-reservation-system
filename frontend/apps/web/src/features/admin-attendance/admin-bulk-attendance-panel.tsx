import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import type {
  AdminReservationResponse,
  BulkReservationAttendanceItemRequest,
  BulkReservationAttendanceResponse,
} from '@horse/api-client'
import { getAdminAttendanceErrorKind, type AdminAttendanceApi } from './admin-attendance.api'
import { isAttendanceProcessable } from './reservation-attendance-eligibility'

const MAX_MEMO_LENGTH = 500

type BulkAction = 'complete' | 'no_show'

interface DraftItem {
  selected: boolean
  action: BulkAction
  couponAction: string
  memo: string
}

function getBulkErrorMessage(error: unknown) {
  const kind = getAdminAttendanceErrorKind(error)
  if (kind === 'forbidden') return '관리자 권한이 없어 일괄 출석을 처리할 수 없습니다.'
  if (kind === 'validation') return '선택한 시간대와 출석 입력을 다시 확인해 주세요.'
  if (kind === 'conflict') return '예약 상태가 변경되었습니다. 최신 목록을 확인해 주세요.'
  return '일괄 출석을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.'
}

export function AdminBulkAttendancePanel({
  reservations,
  api,
  onProcessed,
}: {
  reservations: AdminReservationResponse[]
  api: AdminAttendanceApi
  onProcessed(): Promise<void>
}) {
  const commandLocked = useRef(false)
  const eligibleReservations = useMemo(() => reservations.filter(isAttendanceProcessable), [reservations])
  const dates = useMemo(() => uniqueSorted(eligibleReservations.map((reservation) => dateValue(reservation.lessonDate))), [eligibleReservations])
  const [selectedDate, setSelectedDate] = useState(dates[0] ?? '')
  const times = useMemo(() => uniqueSorted(eligibleReservations
    .filter((reservation) => dateValue(reservation.lessonDate) === selectedDate)
    .map((reservation) => reservation.startTime ?? '')), [eligibleReservations, selectedDate])
  const [selectedTime, setSelectedTime] = useState(times[0] ?? '')
  const selectedReservations = useMemo(() => eligibleReservations.filter((reservation) =>
    dateValue(reservation.lessonDate) === selectedDate && reservation.startTime === selectedTime),
  [eligibleReservations, selectedDate, selectedTime])
  const [drafts, setDrafts] = useState<Record<number, DraftItem>>({})
  const [localError, setLocalError] = useState<string>()
  const [result, setResult] = useState<BulkReservationAttendanceResponse>()

  useEffect(() => {
    if (!dates.includes(selectedDate)) setSelectedDate(dates[0] ?? '')
  }, [dates, selectedDate])

  useEffect(() => {
    if (!times.includes(selectedTime)) setSelectedTime(times[0] ?? '')
  }, [selectedTime, times])

  useEffect(() => {
    setDrafts(Object.fromEntries(selectedReservations.map((reservation) => [
      reservation.reservationId as number,
      createDraft(reservation),
    ])))
    setLocalError(undefined)
  }, [selectedReservations])

  const mutation = useMutation({
    mutationFn: (items: BulkReservationAttendanceItemRequest[]) =>
      api.processBulk(selectedDate, selectedTime, items),
    onSuccess: async (response) => {
      setResult(response)
      await onProcessed()
    },
  })

  const updateDraft = (reservationId: number, update: Partial<DraftItem>) => {
    setDrafts((current) => ({
      ...current,
      [reservationId]: { ...current[reservationId], ...update },
    }))
    setLocalError(undefined)
    mutation.reset()
  }

  const submit = () => {
    if (commandLocked.current) return
    const items = selectedReservations
      .filter((reservation) => drafts[reservation.reservationId as number]?.selected)
      .map((reservation) => toRequestItem(reservation, drafts[reservation.reservationId as number]))
    if (items.length === 0) {
      setLocalError('처리할 예약을 한 명 이상 선택해 주세요.')
      return
    }
    const invalidMemo = items.find((item) => item.action === 'no_show'
      && (!item.memo?.trim() || item.memo.trim().length > MAX_MEMO_LENGTH))
    if (invalidMemo) {
      setLocalError(`노쇼 관리자 메모는 1자 이상 ${MAX_MEMO_LENGTH}자 이하로 입력해 주세요.`)
      return
    }
    commandLocked.current = true
    setLocalError(undefined)
    setResult(undefined)
    mutation.mutate(items.map((item) => ({ ...item, memo: item.memo?.trim() })), {
      onSettled: () => { commandLocked.current = false },
    })
  }

  if (eligibleReservations.length === 0) {
    return <section className="admin-bulk-attendance-panel"><h2>시간대 일괄 처리</h2><p className="admin-bulk-attendance-empty">일괄 처리할 확정 예약이 없습니다.</p>{result ? <BulkResultPanel result={result} /> : null}</section>
  }

  return (
    <section className="admin-bulk-attendance-panel" aria-labelledby="bulk-attendance-title">
      <div className="admin-bulk-attendance-heading">
        <div><p>TIME SLOT BATCH</p><h2 id="bulk-attendance-title">시간대 일괄 처리</h2><span>같은 시간대의 출석과 노쇼를 한 번에 반영합니다.</span></div>
        <strong>{selectedReservations.length}명</strong>
      </div>

      <div className="admin-bulk-attendance-filters">
        <label>수업 날짜
          <select value={selectedDate} onChange={(event) => { setSelectedDate(event.target.value); setResult(undefined) }}>
            {dates.map((date) => <option value={date} key={date}>{date}</option>)}
          </select>
        </label>
        <label>시작 시간
          <select value={selectedTime} onChange={(event) => { setSelectedTime(event.target.value); setResult(undefined) }}>
            {times.map((time) => <option value={time} key={time}>{time.slice(0, 5)}</option>)}
          </select>
        </label>
      </div>

      {selectedReservations.length === 0 ? <p className="admin-bulk-attendance-empty">선택한 시간대에 확정 예약이 없습니다.</p> : (
        <div className="admin-bulk-attendance-list">
          {selectedReservations.map((reservation) => {
            const reservationId = reservation.reservationId as number
            const draft = drafts[reservationId] ?? createDraft(reservation)
            const isCoupon = reservation.paymentSource === 'coupon'
            return (
              <article className="admin-bulk-attendance-row" key={reservationId}>
                <label className="admin-bulk-attendance-member">
                  <input
                    type="checkbox"
                    checked={draft.selected}
                    disabled={mutation.isPending}
                    onChange={(event) => updateDraft(reservationId, { selected: event.target.checked })}
                  />
                  <span><strong>{reservation.memberName ?? '이름 없음'}</strong><small>{reservation.classType ?? '-'}</small></span>
                </label>
                <label>출석 결과
                  <select
                    aria-label={`${reservation.memberName ?? reservationId} 출석 결과`}
                    value={draft.action}
                    disabled={!draft.selected || mutation.isPending}
                    onChange={(event) => updateDraft(reservationId, { action: event.target.value as BulkAction })}
                  >
                    <option value="complete">수업 완료</option>
                    <option value="no_show">노쇼</option>
                  </select>
                </label>
                {draft.action === 'no_show' && draft.selected ? (
                  <div className="admin-bulk-attendance-no-show">
                    <label>쿠폰 처리
                      <select
                        aria-label={`${reservation.memberName ?? reservationId} 쿠폰 처리`}
                        value={draft.couponAction}
                        disabled={mutation.isPending}
                        onChange={(event) => updateDraft(reservationId, { couponAction: event.target.value })}
                      >
                        {isCoupon ? <><option value="deduct">1회 차감</option><option value="return">점유 반환</option></> : <option value="none">쿠폰 처리 없음</option>}
                      </select>
                    </label>
                    <label>관리자 메모
                      <input
                        aria-label={`${reservation.memberName ?? reservationId} 관리자 메모`}
                        value={draft.memo}
                        maxLength={MAX_MEMO_LENGTH}
                        disabled={mutation.isPending}
                        onChange={(event) => updateDraft(reservationId, { memo: event.target.value })}
                      />
                    </label>
                  </div>
                ) : null}
              </article>
            )
          })}
        </div>
      )}

      {localError ? <p className="admin-attendance-error" role="alert">{localError}</p> : null}
      {mutation.isError ? <p className="admin-attendance-error" role="alert">{getBulkErrorMessage(mutation.error)}</p> : null}
      {result ? <BulkResultPanel result={result} /> : null}
      <div className="admin-bulk-attendance-submit">
        <button type="button" disabled={mutation.isPending || selectedReservations.length === 0} onClick={submit}>
          {mutation.isPending ? '일괄 처리 중' : '선택 예약 일괄 처리'}
        </button>
      </div>
    </section>
  )
}

function BulkResultPanel({ result }: { result: BulkReservationAttendanceResponse }) {
  return (
    <section className="admin-bulk-attendance-result" aria-live="polite">
      <div><strong>일괄 처리 결과</strong><span>성공 {result.succeededCount ?? 0}건 · 실패 {result.failedCount ?? 0}건</span></div>
      <ul>
        {(result.items ?? []).map((item, index) => (
          <li className={item.success ? 'success' : 'failure'} key={`${item.reservationId}-${index}`}>
            <strong>예약 #{item.reservationId}</strong>
            <span>{item.success ? `처리 완료 · ${item.status}` : `${item.errorCode} · ${item.errorMessage}`}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}

function createDraft(reservation: AdminReservationResponse): DraftItem {
  return {
    selected: true,
    action: 'complete',
    couponAction: reservation.paymentSource === 'coupon' ? 'deduct' : 'none',
    memo: '',
  }
}

function toRequestItem(
  reservation: AdminReservationResponse,
  draft: DraftItem,
): BulkReservationAttendanceItemRequest {
  const item: BulkReservationAttendanceItemRequest = {
    reservationId: reservation.reservationId as number,
    action: draft.action,
  }
  if (draft.action === 'no_show') {
    item.couponAction = draft.couponAction
    item.memo = draft.memo
  }
  return item
}

function dateValue(value?: Date) {
  return value?.toISOString().slice(0, 10) ?? ''
}

function uniqueSorted(values: string[]) {
  return [...new Set(values.filter(Boolean))].toSorted()
}
