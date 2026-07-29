import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { ResponseError, type AdminReservationAuditPageResponse } from '@horse/api-client'
import type { AdminAuditApi } from './admin-audit.api'
import { AdminAuditPage } from './admin-audit-page'

const AUDIT_PAGE: AdminReservationAuditPageResponse = {
  content: [{
    auditLogId: 81,
    reservationId: 42,
    memberId: 7,
    memberName: '김하늘',
    actorAuthSubject: 'admin-operator',
    actorType: 'admin',
    changeType: 'schedule_changed',
    fromStatus: 'confirmed',
    toStatus: 'confirmed',
    fromLessonDate: new Date('2026-07-20T00:00:00.000Z'),
    fromStartTime: '09:00:00',
    toLessonDate: new Date('2026-07-20T00:00:00.000Z'),
    toStartTime: '10:00:00',
    couponAction: 'free_change_used',
    couponId: null,
    paymentDueAt: null,
    memo: '회원 요청으로 시간 변경',
    occurredAt: new Date('2026-07-16T01:30:00.000Z'),
  }],
  page: 0,
  size: 20,
  totalElements: 21,
  totalPages: 2,
  hasNext: true,
}

afterEach(() => cleanup())

function createApi(overrides: Partial<AdminAuditApi> = {}): AdminAuditApi {
  return {
    getAuditLogs: vi.fn().mockResolvedValue(AUDIT_PAGE),
    ...overrides,
  }
}

function renderPage(api: AdminAuditApi) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  )
  return render(<AdminAuditPage api={api} />, { wrapper })
}

describe('AdminAuditPage', () => {
  it('최신_감사_이력의_전후_상태와_처리_근거를_표시한다', async () => {
    renderPage(createApi())

    expect(await screen.findByRole('heading', { name: '예약 감사 이력' })).toBeInTheDocument()
    expect(await screen.findByText('김하늘')).toBeInTheDocument()
    expect(screen.getByText('예약 #42')).toBeInTheDocument()
    expect(screen.getByText('일정 변경', { selector: '.admin-audit-type' })).toBeInTheDocument()
    expect(screen.getAllByText('예약확정')).toHaveLength(2)
    expect(screen.getByText('2026.07.20 09:00')).toBeInTheDocument()
    expect(screen.getByText('2026.07.20 10:00')).toBeInTheDocument()
    expect(screen.getByText('admin-operator')).toBeInTheDocument()
    expect(screen.getByText('무료 변경권 사용')).toBeInTheDocument()
    expect(screen.getByText('회원 요청으로 시간 변경')).toBeInTheDocument()
    expect(screen.getByText('전체 21건')).toBeInTheDocument()
  })

  it('회원_예약_기간_행위자_변경유형_필터를_첫_페이지에_적용한다', async () => {
    const api = createApi()
    renderPage(api)
    await screen.findByText('김하늘')

    fireEvent.change(screen.getByLabelText('회원 검색'), { target: { value: '  7788  ' } })
    fireEvent.change(screen.getByLabelText('예약 ID'), { target: { value: '42' } })
    fireEvent.change(screen.getByLabelText('시작일'), { target: { value: '2026-07-01' } })
    fireEvent.change(screen.getByLabelText('종료일'), { target: { value: '2026-07-31' } })
    fireEvent.change(screen.getByLabelText('처리 주체'), { target: { value: 'admin' } })
    fireEvent.change(screen.getByLabelText('변경 유형'), { target: { value: 'schedule_changed' } })
    fireEvent.click(screen.getByRole('button', { name: '조건 적용' }))

    await waitFor(() => expect(api.getAuditLogs).toHaveBeenLastCalledWith({
      keyword: '7788',
      reservationId: 42,
      occurredDateFrom: '2026-07-01',
      occurredDateTo: '2026-07-31',
      actorType: 'admin',
      changeType: 'schedule_changed',
      page: 0,
      size: 20,
    }))
  })

  it('필터를_초기화하면_전체_이력의_첫_페이지를_조회한다', async () => {
    const api = createApi()
    renderPage(api)
    await screen.findByText('김하늘')
    fireEvent.change(screen.getByLabelText('회원 검색'), { target: { value: '김하늘' } })
    fireEvent.click(screen.getByRole('button', { name: '조건 적용' }))
    await waitFor(() => expect(api.getAuditLogs).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: '김하늘' })))

    fireEvent.click(screen.getByRole('button', { name: '초기화' }))

    await waitFor(() => expect(api.getAuditLogs).toHaveBeenLastCalledWith({
      keyword: undefined,
      reservationId: undefined,
      occurredDateFrom: undefined,
      occurredDateTo: undefined,
      actorType: undefined,
      changeType: undefined,
      page: 0,
      size: 20,
    }))
  })

  it('다음_페이지를_선택하면_같은_조건의_다음_페이지를_조회한다', async () => {
    const api = createApi()
    renderPage(api)
    await screen.findByText('김하늘')

    fireEvent.click(screen.getByRole('button', { name: '다음' }))

    await waitFor(() => expect(api.getAuditLogs).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, size: 20 })))
    expect(await screen.findByText('2 / 2 페이지')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled()
  })

  it('시작일이_종료일보다_늦으면_API를_호출하지_않고_안내한다', async () => {
    const api = createApi()
    renderPage(api)
    await screen.findByText('김하늘')
    expect(api.getAuditLogs).toHaveBeenCalledTimes(1)
    fireEvent.change(screen.getByLabelText('시작일'), { target: { value: '2026-07-31' } })
    fireEvent.change(screen.getByLabelText('종료일'), { target: { value: '2026-07-01' } })

    fireEvent.click(screen.getByRole('button', { name: '조건 적용' }))

    expect(screen.getByRole('alert')).toHaveTextContent('시작일은 종료일보다 늦을 수 없습니다.')
    expect(api.getAuditLogs).toHaveBeenCalledTimes(1)
  })

  it('조회_결과가_없으면_빈_상태를_표시한다', async () => {
    renderPage(createApi({
      getAuditLogs: vi.fn().mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    }))

    expect(await screen.findByText('조회 조건에 해당하는 예약 감사 이력이 없습니다.')).toBeInTheDocument()
  })

  it('권한_오류를_일반_오류와_구분해_표시한다', async () => {
    const forbidden = new ResponseError(new Response(null, { status: 403 }), 'forbidden')
    renderPage(createApi({ getAuditLogs: vi.fn().mockRejectedValue(forbidden) }))

    expect(await screen.findByRole('alert')).toHaveTextContent('관리자 권한이 없어')
  })

  it('조회_오류를_재시도한다', async () => {
    const getAuditLogs = vi.fn()
      .mockRejectedValueOnce(new Error('failed'))
      .mockResolvedValue(AUDIT_PAGE)
    renderPage(createApi({ getAuditLogs }))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('감사 이력을 불러오지 못했습니다')
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))

    expect(await screen.findByText('김하늘')).toBeInTheDocument()
    expect(getAuditLogs).toHaveBeenCalledTimes(2)
  })

  it('현재_페이지가_범위를_벗어나면_마지막_유효_페이지로_복귀한다', async () => {
    let secondPageRequested = false
    const getAuditLogs = vi.fn((filters: { page?: number }) => {
      if (filters.page === 1) {
        secondPageRequested = true
        return Promise.resolve({ ...AUDIT_PAGE, page: 1, totalPages: 1, hasNext: false })
      }
      return Promise.resolve(AUDIT_PAGE)
    })
    renderPage(createApi({ getAuditLogs }))
    await screen.findByText('김하늘')

    fireEvent.click(screen.getByRole('button', { name: '다음' }))

    await waitFor(() => expect(secondPageRequested).toBe(true))
    await waitFor(() => expect(getAuditLogs).toHaveBeenLastCalledWith(expect.objectContaining({ page: 0 })))
    expect(await screen.findByText('1 / 2 페이지')).toBeInTheDocument()
  })

  it('로딩_중에는_결과_대신_상태를_표시한다', () => {
    renderPage(createApi({
      getAuditLogs: vi.fn(() => new Promise<AdminReservationAuditPageResponse>(() => undefined)),
    }))

    expect(screen.getByRole('status')).toHaveTextContent('감사 이력을 불러오는 중입니다.')
  })

  it('320px_화면에서도_검색과_페이지_기능을_사용할_수_있다', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 320 })
    renderPage(createApi())

    expect(await screen.findByText('김하늘')).toBeInTheDocument()
    expect(screen.getByLabelText('회원 검색')).toBeInTheDocument()
    expect(screen.getByLabelText('예약 ID')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '조건 적용' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '다음' })).toBeInTheDocument()
  })
})
