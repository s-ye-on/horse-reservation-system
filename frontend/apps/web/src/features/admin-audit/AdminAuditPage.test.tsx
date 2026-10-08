import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { ResponseError, type AdminReservationAuditPageResponse } from '@horse/api-client'
import type { AdminAuditApi, AdminAuditDownload } from './admin-audit.api'
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

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

function createApi(overrides: Partial<AdminAuditApi> = {}): AdminAuditApi {
  return {
    getAuditLogs: vi.fn().mockResolvedValue(AUDIT_PAGE),
    downloadAuditLogs: vi.fn().mockResolvedValue({
      blob: new Blob(['audit,csv'], { type: 'text/csv' }),
      fileName: 'reservation-audit.csv',
    }),
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
  it('관리자_생성_이력과_필터를_제공하고_미지_enum을_노출하지_않는다', async () => {
    const api = createApi({ getAuditLogs: vi.fn().mockResolvedValue({ ...AUDIT_PAGE,
      content: [{ ...AUDIT_PAGE.content[0], changeType: 'admin_reservation_created', toStatus: 'unknown_status', couponAction: 'unknown_action' }],
    }) })
    renderPage(api)
    await screen.findByText('김하늘')
    expect(screen.getByText('관리자 예약 생성', { selector: '.admin-audit-type' })).toBeInTheDocument()
    expect(screen.queryByText('unknown_status')).not.toBeInTheDocument()
    expect(screen.queryByText('unknown_action')).not.toBeInTheDocument()
    fireEvent.change(screen.getByLabelText('변경 유형'), { target: { value: 'admin_reservation_created' } })
    fireEvent.click(screen.getByRole('button', { name: '조건 적용' }))
    await waitFor(() => expect(api.getAuditLogs).toHaveBeenLastCalledWith(expect.objectContaining({ changeType: 'admin_reservation_created' })))
  })

  it('새_조회_완료_전에는_이전_건수와_목록을_새_조건_결과처럼_보이지_않는다', async () => {
    let resolve!: (value: AdminReservationAuditPageResponse) => void
    const getAuditLogs = vi.fn().mockResolvedValueOnce(AUDIT_PAGE)
      .mockImplementationOnce(() => new Promise<AdminReservationAuditPageResponse>((done) => { resolve = done }))
    renderPage(createApi({ getAuditLogs }))
    await screen.findByText('김하늘')
    fireEvent.change(screen.getByLabelText('회원 검색'), { target: { value: '다른 회원' } })
    fireEvent.click(screen.getByRole('button', { name: '조건 적용' }))
    await screen.findByText('감사 이력을 불러오는 중입니다.')
    expect(screen.queryByText(/전체 21건/)).not.toBeInTheDocument()
    expect(screen.queryByText('김하늘')).not.toBeInTheDocument()
    resolve({ ...AUDIT_PAGE, content: [], totalElements: 0, totalPages: 0, hasNext: false })
    expect(await screen.findByText('전체 0건 · 현재 페이지 0건')).toBeInTheDocument()
  })

  it('같은_조건_재적용도_서버를_다시_조회한다', async () => {
    const api = createApi()
    renderPage(api)
    await screen.findByText('김하늘')
    fireEvent.click(screen.getByRole('button', { name: '조건 적용' }))
    await waitFor(() => expect(api.getAuditLogs).toHaveBeenCalledTimes(2))
  })

  it('안전하지_않은_예약_번호는_필드_오류와_focus로_안내한다', async () => {
    const api = createApi()
    renderPage(api)
    await screen.findByText('김하늘')
    fireEvent.change(screen.getByLabelText('예약 번호'), { target: { value: '9007199254740993' } })
    fireEvent.click(screen.getByRole('button', { name: '조건 적용' }))
    expect(screen.getByRole('alert')).toHaveTextContent('예약 번호는 1 이상의 정수')
    expect(document.getElementById('audit-reservation-id')).toHaveAttribute('aria-invalid', 'true')
    expect(document.getElementById('audit-reservation-id')).toHaveFocus()
    expect(api.getAuditLogs).toHaveBeenCalledTimes(1)
  })

  it('미적용_입력은_CSV에_포함하지_않고_다운로드_중_조건이_바뀌어도_원래_조건을_안내한다', async () => {
    let resolve!: (value: AdminAuditDownload) => void
    const downloadAuditLogs = vi.fn(() => new Promise<AdminAuditDownload>((done) => { resolve = done }))
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:csv')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    renderPage(createApi({ downloadAuditLogs }))
    await screen.findByText('김하늘')
    fireEvent.change(screen.getByLabelText('회원 검색'), { target: { value: '미적용 회원' } })
    fireEvent.click(screen.getByRole('button', { name: '현재 조건 CSV 다운로드' }))
    expect(downloadAuditLogs).toHaveBeenCalledWith(expect.objectContaining({ keyword: undefined }))
    fireEvent.click(screen.getByRole('button', { name: '조건 적용' }))
    resolve({ blob: new Blob(['csv']), fileName: 'audit.csv' })
    await waitFor(() => expect(screen.getByText(/CSV 다운로드를 시작했습니다/)).toHaveTextContent('다운로드 조건: 전체 기간 · 전체 회원 · 전체 작업'))
  })

  it('최신_감사_이력의_전후_상태와_처리_근거를_표시한다', async () => {
    renderPage(createApi())

    expect(await screen.findByRole('heading', { name: '예약 감사 이력' })).toBeInTheDocument()
    expect(await screen.findByText('김하늘')).toBeInTheDocument()
    expect(screen.getByText('예약 #42')).toBeInTheDocument()
    expect(screen.getByText('일정 변경', { selector: '.admin-audit-type' })).toBeInTheDocument()
    expect(screen.getAllByText('예약확정')).toHaveLength(2)
    expect(screen.getByText('2026.07.20 09:00')).toBeInTheDocument()
    expect(screen.getByText('2026.07.20 10:00')).toBeInTheDocument()
    expect(screen.queryByText('admin-operator')).not.toBeInTheDocument()
    expect(screen.getByText('무료 변경권 사용')).toBeInTheDocument()
    expect(screen.getByText('회원 요청으로 시간 변경')).toBeInTheDocument()
    expect(screen.getByText('전체 21건 · 현재 페이지 1건')).toBeInTheDocument()
  })

  it('회원_예약_기간_행위자_변경유형_필터를_첫_페이지에_적용한다', async () => {
    const api = createApi()
    renderPage(api)
    await screen.findByText('김하늘')

    fireEvent.change(screen.getByLabelText('회원 검색'), { target: { value: '  7788  ' } })
    fireEvent.change(screen.getByLabelText('예약 번호'), { target: { value: '42' } })
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
    expect(screen.getByRole('button', { name: '다음' })).toHaveAttribute('aria-disabled', 'true')
    const callCount = vi.mocked(api.getAuditLogs).mock.calls.length
    fireEvent.click(screen.getByRole('button', { name: '다음' }))
    expect(api.getAuditLogs).toHaveBeenCalledTimes(callCount)
  })

  it('현재_적용된_필터로_CSV를_다운로드하고_목록과_페이지를_유지한다', async () => {
    const blob = new Blob(['audit,csv'], { type: 'text/csv' })
    const downloadAuditLogs = vi.fn().mockResolvedValue({ blob, fileName: '예약 감사.csv' })
    const createObjectUrl = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:audit-csv')
    const revokeObjectUrl = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
    let downloadedFileName = ''
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      downloadedFileName = this.download
    })
    const api = createApi({ downloadAuditLogs })
    renderPage(api)
    await screen.findByText('김하늘')

    fireEvent.change(screen.getByLabelText('회원 검색'), { target: { value: '  7788  ' } })
    fireEvent.change(screen.getByLabelText('시작일'), { target: { value: '2026-07-01' } })
    fireEvent.change(screen.getByLabelText('종료일'), { target: { value: '2026-07-31' } })
    fireEvent.change(screen.getByLabelText('처리 주체'), { target: { value: 'admin' } })
    fireEvent.change(screen.getByLabelText('변경 유형'), { target: { value: 'schedule_changed' } })
    fireEvent.click(screen.getByRole('button', { name: '조건 적용' }))
    await waitFor(() => expect(api.getAuditLogs).toHaveBeenLastCalledWith(expect.objectContaining({
      keyword: '7788',
      occurredDateFrom: '2026-07-01',
      occurredDateTo: '2026-07-31',
      actorType: 'admin',
      changeType: 'schedule_changed',
      page: 0,
    })))

    fireEvent.click(screen.getByRole('button', { name: '현재 조건 CSV 다운로드' }))

    await waitFor(() => expect(downloadAuditLogs).toHaveBeenCalledWith({
      keyword: '7788',
      reservationId: undefined,
      occurredDateFrom: '2026-07-01',
      occurredDateTo: '2026-07-31',
      actorType: 'admin',
      changeType: 'schedule_changed',
    }))
    expect(createObjectUrl).toHaveBeenCalledWith(blob)
    await waitFor(() => expect(revokeObjectUrl).toHaveBeenCalledWith('blob:audit-csv'))
    expect(downloadedFileName).toBe('예약 감사.csv')
    expect(document.querySelector('a[download]')).not.toBeInTheDocument()
    expect(screen.getByText('김하늘')).toBeInTheDocument()
    expect(screen.getByText('1 / 2 페이지')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '현재 조건 CSV 다운로드' })).toBeEnabled()
  })

  it('다운로드_중에는_중복_요청을_막고_진행_상태를_표시한다', async () => {
    let resolveDownload: ((download: AdminAuditDownload) => void) | undefined
    const downloadAuditLogs = vi.fn(() => new Promise<AdminAuditDownload>((resolve) => {
      resolveDownload = resolve
    }))
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:audit-csv')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    renderPage(createApi({ downloadAuditLogs }))
    await screen.findByText('김하늘')

    const downloadButton = screen.getByRole('button', { name: '현재 조건 CSV 다운로드' })
    fireEvent.click(downloadButton)
    fireEvent.click(downloadButton)

    expect(await screen.findByRole('button', { name: 'CSV 준비 중...' })).toBeDisabled()
    expect(downloadAuditLogs).toHaveBeenCalledTimes(1)

    resolveDownload?.({
      blob: new Blob(['audit,csv'], { type: 'text/csv' }),
      fileName: 'reservation-audit.csv',
    })
    expect(await screen.findByRole('button', { name: '현재 조건 CSV 다운로드' })).toBeEnabled()
  })

  it.each([
    [401, '로그인이 필요합니다.'],
    [403, '관리자 권한이 없어 CSV를 다운로드할 수 없습니다.'],
    [500, 'CSV를 다운로드하지 못했습니다.'],
  ])('%s_다운로드_실패를_파일로_저장하지_않고_목록에_안내한다', async (status, message) => {
    const failure = new ResponseError(new Response(null, { status }), 'download failed')
    const createObjectUrl = vi.spyOn(URL, 'createObjectURL')
    renderPage(createApi({ downloadAuditLogs: vi.fn().mockRejectedValue(failure) }))
    await screen.findByText('김하늘')

    fireEvent.click(screen.getByRole('button', { name: '현재 조건 CSV 다운로드' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(createObjectUrl).not.toHaveBeenCalled()
    expect(screen.getByText('김하늘')).toBeInTheDocument()
    expect(screen.getByText('1 / 2 페이지')).toBeInTheDocument()
  })

  it('네트워크_실패_후_같은_조건으로_재시도할_수_있다', async () => {
    const downloadAuditLogs = vi.fn()
      .mockRejectedValueOnce(new TypeError('network failed'))
      .mockResolvedValue({
        blob: new Blob(['audit,csv'], { type: 'text/csv' }),
        fileName: 'reservation-audit.csv',
      })
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:audit-csv')
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    renderPage(createApi({ downloadAuditLogs }))
    await screen.findByText('김하늘')
    fireEvent.click(screen.getByRole('button', { name: '현재 조건 CSV 다운로드' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('CSV를 다운로드하지 못했습니다.')

    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }))

    await waitFor(() => expect(downloadAuditLogs).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(click).toHaveBeenCalledTimes(1))
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('링크_실행이_실패해도_임시_링크와_객체_URL을_정리한다', async () => {
    const revokeObjectUrl = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:audit-csv')
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {
      throw new Error('download blocked')
    })
    renderPage(createApi())
    await screen.findByText('김하늘')

    fireEvent.click(screen.getByRole('button', { name: '현재 조건 CSV 다운로드' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('CSV를 다운로드하지 못했습니다.')
    expect(revokeObjectUrl).toHaveBeenCalledWith('blob:audit-csv')
    expect(document.querySelector('a[download]')).not.toBeInTheDocument()
  })

  it('필터를_다시_적용하면_이전_다운로드_오류를_정리한다', async () => {
    renderPage(createApi({ downloadAuditLogs: vi.fn().mockRejectedValue(new Error('failed')) }))
    await screen.findByText('김하늘')
    fireEvent.click(screen.getByRole('button', { name: '현재 조건 CSV 다운로드' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('CSV를 다운로드하지 못했습니다.')

    fireEvent.change(screen.getByLabelText('회원 검색'), { target: { value: '김하늘' } })
    fireEvent.click(screen.getByRole('button', { name: '조건 적용' }))

    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
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
    expect(screen.queryByText(/전체 0건/)).not.toBeInTheDocument()
  })

  it('320px_화면에서도_검색과_페이지_기능을_사용할_수_있다', async () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 320 })
    renderPage(createApi())

    expect(await screen.findByText('김하늘')).toBeInTheDocument()
    expect(screen.getByLabelText('회원 검색')).toBeInTheDocument()
    expect(screen.getByLabelText('예약 번호')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '조건 적용' })).toBeInTheDocument()
    const downloadButton = screen.getByRole('button', { name: '현재 조건 CSV 다운로드' })
    downloadButton.focus()
    expect(downloadButton).toHaveFocus()
    expect(screen.getByRole('button', { name: '다음' })).toBeInTheDocument()
  })
})
