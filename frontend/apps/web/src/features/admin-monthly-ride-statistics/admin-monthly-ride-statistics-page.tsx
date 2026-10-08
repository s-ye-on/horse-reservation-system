import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import type { AdminMonthlyRideStatisticsResponse } from '@horse/api-client'
import {
  adminMonthlyRideStatisticsApi,
  getAdminMonthlyRideStatisticsErrorKind,
  type AdminMonthlyRideStatisticsApi,
  type MonthlyRideType,
} from './admin-monthly-ride-statistics.api'
import './admin-monthly-ride-statistics-page.css'

const RIDE_TYPE_OPTIONS: ReadonlyArray<{ value: MonthlyRideType; label: string }> = [
  { value: 'ALL', label: '전체 기승' },
  { value: 'GENERAL', label: '일반 기승' },
  { value: 'DRESSAGE', label: '마장마술' },
  { value: 'JUMPING', label: '장애물' },
]

interface AdminMonthlyRideStatisticsPageProps {
  api?: AdminMonthlyRideStatisticsApi
  now?: Date
}

export function AdminMonthlyRideStatisticsPage({
  api = adminMonthlyRideStatisticsApi,
  now = new Date(),
}: AdminMonthlyRideStatisticsPageProps) {
  const currentMonth = seoulMonth(now)
  const [selectedMonth, setSelectedMonth] = useState(currentMonth)
  const [selectedRideType, setSelectedRideType] = useState<MonthlyRideType>('ALL')
  const statisticsQuery = useQuery({
    queryKey: ['admin', 'monthly-ride-statistics', selectedMonth, selectedRideType],
    queryFn: () => api.getStatistics(selectedMonth, selectedRideType),
  })
  const statistics = statisticsQuery.isSuccess ? statisticsQuery.data : undefined
  const announcement = statisticsQuery.isPending || statisticsQuery.isFetching
    ? '월간 기승 현황을 불러오는 중입니다.'
    : statistics
      ? `${formatMonth(statistics.month)} ${rideTypeLabel(statistics.rideType)} 조회 완료. ${statistics.totalCompletedRideCount === 0
        ? '완료된 수업이 없습니다.'
        : `총 기승 횟수 ${statistics.totalCompletedRideCount}회, 최다 기승 횟수 ${statistics.topCompletedRideCount}회, 최다 회원 ${statistics.leaders.length}명.`}`
      : ''

  return (
    <main className="admin-monthly-statistics-page">
      <div className="admin-monthly-statistics-shell">
        <header className="admin-monthly-statistics-header">
          <div>
            <p>MONTHLY RIDES</p>
            <h1>월간 기승 현황</h1>
            <span>수업 날짜를 기준으로 완료된 예약의 월간 기승 횟수를 확인합니다.</span>
          </div>
          <Link to="/admin">관리자 메뉴</Link>
        </header>

        <section className="admin-monthly-statistics-controls" aria-label="통계 조회 조건">
          <div className="admin-monthly-statistics-month-controls">
            <button
              type="button"
              aria-label="이전 달"
              onClick={() => setSelectedMonth((month) => moveMonth(month, -1))}
            >
              <span aria-hidden="true">&larr;</span>
              <span>이전 달</span>
            </button>
            <label>
              조회 월
              <input
                type="month"
                required
                value={selectedMonth}
                aria-describedby="monthly-statistics-period-help"
                onChange={(event) => {
                  if (/^\d{4}-(0[1-9]|1[0-2])$/.test(event.target.value)) setSelectedMonth(event.target.value)
                }}
              />
            </label>
            <button
              type="button"
              aria-label="다음 달"
              onClick={() => setSelectedMonth((month) => moveMonth(month, 1))}
            >
              <span>다음 달</span>
              <span aria-hidden="true">&rarr;</span>
            </button>
            <button
              type="button"
              className="secondary"
              disabled={selectedMonth === currentMonth}
              onClick={() => setSelectedMonth(currentMonth)}
            >
              이번 달
            </button>
          </div>
          <p id="monthly-statistics-period-help" className="admin-monthly-statistics-help">조회 월은 서울 기준 수업 날짜가 속한 달입니다.</p>

          <fieldset className="admin-monthly-statistics-type-controls">
            <legend>기승 종류</legend>
            <div>
              {RIDE_TYPE_OPTIONS.map((option) => (
                <button
                  type="button"
                  aria-pressed={selectedRideType === option.value}
                  className={selectedRideType === option.value ? 'selected' : ''}
                  key={option.value}
                  onClick={() => setSelectedRideType(option.value)}
                >
                  {option.label}
                </button>
              ))}
            </div>
          </fieldset>
        </section>

        <section className="admin-monthly-statistics-results" aria-label="월간 기승 조회 결과">
          <p className="admin-monthly-statistics-announcement" role="status" aria-atomic="true">{announcement}</p>
          <div className="admin-monthly-statistics-results-heading">
            <div>
              <h2>{formatMonth(statisticsQuery.isSuccess ? statisticsQuery.data.month : selectedMonth)} {rideTypeLabel(statisticsQuery.isSuccess ? statisticsQuery.data.rideType : selectedRideType)}</h2>
              <p>Horse에서 완료 처리된 예약만 집계합니다.</p>
            </div>
          </div>

          {statisticsQuery.isPending ? (
            <StatisticsState message="월간 기승 현황을 불러오는 중입니다." />
          ) : null}
          {statisticsQuery.isError ? (
            <StatisticsError
              error={statisticsQuery.error}
              pending={statisticsQuery.isFetching}
              retry={() => { void statisticsQuery.refetch() }}
            />
          ) : null}
          {statisticsQuery.isSuccess ? <StatisticsResult statistics={statisticsQuery.data} /> : null}
          {statisticsQuery.isSuccess && statisticsQuery.isFetching ? <StatisticsState message="월간 기승 현황을 갱신 중입니다. 기존 조회 결과를 표시하고 있습니다." /> : null}
        </section>
        <details className="admin-monthly-statistics-policy">
          <summary>집계 기준</summary>
          <p>Horse에서 수업 완료 처리된 예약만 수업 날짜 기준으로 집계합니다. 취소·노쇼·대기 예약은 포함하지 않습니다.</p>
          <p>인정 시작 클래스, 특수 승인 추가 인정분, 기승 횟수 보정과 Horse 운영 이전 경력은 이 통계에 포함하지 않습니다.</p>
        </details>
      </div>
    </main>
  )
}

function StatisticsResult({ statistics }: { statistics: AdminMonthlyRideStatisticsResponse }) {
  const isEmpty = statistics.totalCompletedRideCount === 0
  return (
    <>
      <section className="admin-monthly-statistics-metrics" aria-label="월간 기승 집계">
        <article>
          <span>총 기승 횟수</span>
          <strong>{statistics.totalCompletedRideCount}</strong>
          <small>선택한 월·종류의 완료 수업 전체</small>
        </article>
        <article>
          <span>최다 기승 횟수</span>
          <strong>{statistics.topCompletedRideCount}</strong>
          <small>{isEmpty ? '해당 기록 없음' : '한 회원의 월간 최고 횟수'}</small>
        </article>
      </section>

      {isEmpty ? (
        <StatisticsState message="선택한 월과 기승 종류에 완료된 수업이 없습니다." />
      ) : (
        <section className="admin-monthly-statistics-leaders" aria-labelledby="monthly-leaders-title">
          <div>
            <h3 id="monthly-leaders-title">
              {statistics.leaders.length > 1 ? '공동 최다 기승 회원' : '최다 기승 회원'}
            </h3>
            <span>최다 회원 {statistics.leaders.length}명</span>
          </div>
          <ul>
            {statistics.leaders.map((leader) => (
              <li key={leader.memberId}>
                <strong>{leader.memberName}</strong>
                <span>{leader.completedRideCount}회</span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </>
  )
}

function StatisticsError({ error, retry, pending }: { error: unknown; retry: () => void; pending: boolean }) {
  const kind = getAdminMonthlyRideStatisticsErrorKind(error)
  const message = kind === 'unauthorized'
    ? '관리자 로그인이 필요합니다.'
    : kind === 'forbidden'
      ? '월간 기승 현황을 조회할 권한이 없습니다.'
      : kind === 'validation'
        ? '조회할 월과 기승 종류를 다시 확인해 주세요.'
        : '월간 기승 현황을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.'

  return (
    <section className="admin-monthly-statistics-state error" role="alert">
      <p>{message}</p>
      <button type="button" disabled={pending} onClick={retry}>{pending ? '조회 중' : '다시 시도'}</button>
    </section>
  )
}

function StatisticsState({ message }: { message: string }) {
  return <section className="admin-monthly-statistics-state">{message}</section>
}

function seoulMonth(date: Date) {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone: 'Asia/Seoul',
    year: 'numeric',
    month: '2-digit',
  }).formatToParts(date)
  const year = parts.find((part) => part.type === 'year')?.value
  const month = parts.find((part) => part.type === 'month')?.value
  if (!year || !month) throw new Error('현재 월을 계산할 수 없습니다.')
  return `${year}-${month}`
}

function moveMonth(value: string, amount: number) {
  const [year, month] = value.split('-').map(Number)
  const moved = new Date(Date.UTC(year, month - 1 + amount, 1))
  return `${moved.getUTCFullYear()}-${String(moved.getUTCMonth() + 1).padStart(2, '0')}`
}

function formatMonth(value: string) {
  const [year, month] = value.split('-').map(Number)
  return `${year}년 ${month}월`
}

function rideTypeLabel(value: MonthlyRideType) {
  return RIDE_TYPE_OPTIONS.find((option) => option.value === value)?.label ?? ''
}
