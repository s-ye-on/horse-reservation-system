import { useQuery, useQueryClient } from '@tanstack/react-query'
import { adminAttendanceApi, type AdminAttendanceApi } from './admin-attendance.api'
import { AdminBulkAttendancePanel } from './admin-bulk-attendance-panel'
import './admin-attendance-page.css'

const ATTENDANCE_KEY = ['admin', 'confirmed-reservations'] as const

export function AdminAttendancePage({ api = adminAttendanceApi }: { api?: AdminAttendanceApi }) {
  const client = useQueryClient()
  const query = useQuery({ queryKey: ATTENDANCE_KEY, queryFn: api.getConfirmedReservations })
  return (
    <main className="admin-attendance-page">
      <header className="attendance-header">
        <p className="attendance-eyebrow">예약 결과 기록</p>
        <h1>수업 완료 및 노쇼 관리</h1>
        <p>확정 예약의 실제 기승 결과를 한 건씩 기록하거나 같은 시간대의 예약을 함께 처리합니다.</p>
      </header>
      {query.isPending ? <p className="attendance-state" role="status">확정 예약을 불러오는 중입니다.</p> : null}
      {query.isError ? <section className="attendance-alert" role="alert"><p>확정 예약을 불러오지 못했습니다. 최신 예약을 다시 확인해 주세요.</p><button type="button" onClick={() => query.refetch()}>다시 불러오기</button></section> : null}
      {query.data ? <>
        <section className="attendance-intro" aria-label="처리 대상 안내">
          <div><strong>날짜와 시작 시각별 확정 예약</strong><p>수업 시작 시각부터 완료와 노쇼 처리가 가능합니다. 실제 처리 가능 여부는 최신 예약 상태를 기준으로 확인합니다.</p></div>
          <span>불러온 확정 예약 {query.data.length}건 · 최대 100건 표시</span>
        </section>
        <AdminBulkAttendancePanel reservations={query.data} api={api} onProcessed={() => client.invalidateQueries({ queryKey: ATTENDANCE_KEY })} />
      </> : null}
    </main>
  )
}
