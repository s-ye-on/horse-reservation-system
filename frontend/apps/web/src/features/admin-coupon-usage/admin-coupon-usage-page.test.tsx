import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ResponseError } from '@horse/api-client'
import { AdminCouponUsagePage } from './admin-coupon-usage-page'
import type { AdminCouponUsageApi } from './admin-coupon-usage.api'

const DOWNLOAD = { blob: new Blob(['\uFEFF"header"\r\n'], { type: 'text/csv' }), fileName: 'coupon-usage.csv' }
const BUTTON_NAME = '현재 조건 쿠폰 사용 이력 CSV 다운로드'
const EMPTY_CRITERIA = { keyword: undefined, couponId: undefined, reservationId: undefined, occurredDateFrom: undefined, occurredDateTo: undefined }

beforeEach(() => {
  vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:coupon-csv')
  vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
  vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
})
afterEach(() => { cleanup(); vi.restoreAllMocks() })

function renderPage(api: AdminCouponUsageApi = { downloadUsageLogs: vi.fn().mockResolvedValue(DOWNLOAD) }) {
  render(<MemoryRouter><AdminCouponUsagePage api={api} /></MemoryRouter>)
  return api
}

describe('AdminCouponUsagePage', () => {
  it('서버가_지원하는_조건과_전체_export만_제공하며_목록이나_통계를_창작하지_않는다', () => {
    const api = renderPage()
    expect(screen.getByRole('heading', { name: '쿠폰 사용 이력 CSV' })).toBeInTheDocument()
    for (const label of ['회원 검색', '쿠폰 번호', '예약 번호', '처리 시작일', '처리 종료일']) expect(screen.getByLabelText(label)).toBeInTheDocument()
    expect(screen.getByText(/수업일이 아닌 처리 발생일/)).toBeInTheDocument()
    expect(screen.getByText(/조건의 전체 결과/)).toBeInTheDocument()
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(api.downloadUsageLogs).not.toHaveBeenCalled()
  })

  it('적용_조건_전체로_다운로드하며_이후_미적용_입력은_전송하지_않는다', async () => {
    const api = renderPage()
    fireEvent.change(screen.getByLabelText('회원 검색'), { target: { value: '  김하늘  ' } })
    fireEvent.change(screen.getByLabelText('쿠폰 번호'), { target: { value: '7' } })
    fireEvent.change(screen.getByLabelText('예약 번호'), { target: { value: '42' } })
    fireEvent.change(screen.getByLabelText('처리 시작일'), { target: { value: '2026-07-01' } })
    fireEvent.change(screen.getByLabelText('처리 종료일'), { target: { value: '2026-07-31' } })
    fireEvent.click(screen.getByRole('button', { name: '조건 적용' }))
    fireEvent.change(screen.getByLabelText('회원 검색'), { target: { value: '미적용 회원' } })
    fireEvent.click(screen.getByRole('button', { name: BUTTON_NAME }))
    await waitFor(() => expect(api.downloadUsageLogs).toHaveBeenCalledWith({ keyword: '김하늘', couponId: 7, reservationId: 42, occurredDateFrom: '2026-07-01', occurredDateTo: '2026-07-31' }))
    expect(await screen.findByRole('status')).toHaveTextContent('다운로드 조건: 회원: 김하늘')
    expect(URL.createObjectURL).toHaveBeenCalledWith(DOWNLOAD.blob)
    await waitFor(() => expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:coupon-csv'))
    expect(document.querySelector('a[download]')).not.toBeInTheDocument()
  })

  it('조건_초기화는_모든_export_제한을_제거한다', async () => {
    const api = renderPage()
    fireEvent.change(screen.getByLabelText('쿠폰 번호'), { target: { value: '7' } })
    fireEvent.click(screen.getByRole('button', { name: '조건 적용' }))
    fireEvent.click(screen.getByRole('button', { name: '초기화' }))
    fireEvent.click(screen.getByRole('button', { name: BUTTON_NAME }))
    await waitFor(() => expect(api.downloadUsageLogs).toHaveBeenCalledWith(EMPTY_CRITERIA))
  })

  it.each(['쿠폰 번호', '예약 번호'])('%s_잘못된_입력에_inline_error와_focus를_연결한다', (label) => {
    const api = renderPage()
    const input = screen.getByLabelText(label)
    fireEvent.change(input, { target: { value: '-1' } })
    fireEvent.click(screen.getByRole('button', { name: '조건 적용' }))
    expect(input).toHaveFocus()
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAttribute('aria-describedby', screen.getByRole('alert').id)
    expect(screen.getByRole('alert')).toHaveTextContent('1 이상의 정수')
    expect(api.downloadUsageLogs).not.toHaveBeenCalled()
  })

  it('역전된_날짜_범위는_적용하지_않는다', () => {
    renderPage()
    fireEvent.change(screen.getByLabelText('처리 시작일'), { target: { value: '2026-07-31' } })
    fireEvent.change(screen.getByLabelText('처리 종료일'), { target: { value: '2026-07-01' } })
    fireEvent.click(screen.getByRole('button', { name: '조건 적용' }))
    expect(screen.getByRole('alert')).toHaveTextContent('시작일은 종료일보다')
    expect(screen.getByLabelText('처리 시작일')).toHaveFocus()
  })

  it('다운로드_중_중복_클릭을_차단하고_요청_조건을_유지한다', async () => {
    let resolve!: (value: typeof DOWNLOAD) => void
    const api = renderPage({ downloadUsageLogs: vi.fn(() => new Promise<typeof DOWNLOAD>((done) => { resolve = done })) })
    const button = screen.getByRole('button', { name: BUTTON_NAME })
    fireEvent.click(button); fireEvent.click(button)
    expect(api.downloadUsageLogs).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('button', { name: 'CSV 준비 중...' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '조건 적용' })).toBeDisabled()
    fireEvent.change(screen.getByLabelText('회원 검색'), { target: { value: '다음 조건' } })
    resolve(DOWNLOAD)
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('다운로드 조건: 전체 회원'))
    expect(screen.getByRole('button', { name: BUTTON_NAME })).toBeEnabled()
  })

  it.each([[400, '조건을 다시 확인'], [401, '로그인이 필요'], [403, '관리자 권한'], [500, '다운로드하지 못했습니다']])('HTTP_%s_실패는_alert로_알리고_파일을_생성하지_않는다', async (status, message) => {
    renderPage({ downloadUsageLogs: vi.fn().mockRejectedValue(new ResponseError(new Response(null, { status }), 'RAW_INTERNAL_CODE')) })
    fireEvent.click(screen.getByRole('button', { name: BUTTON_NAME }))
    expect(await screen.findByRole('alert')).toHaveTextContent(message)
    expect(screen.getByRole('alert')).not.toHaveTextContent('RAW_INTERNAL_CODE')
    expect(URL.createObjectURL).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: BUTTON_NAME })).toBeEnabled()
  })

  it('실패_후_사용자가_같은_적용_조건으로_다시_다운로드할_수_있다', async () => {
    const api = renderPage({ downloadUsageLogs: vi.fn().mockRejectedValueOnce(new TypeError('network')).mockResolvedValue(DOWNLOAD) })
    fireEvent.click(screen.getByRole('button', { name: BUTTON_NAME }))
    await screen.findByRole('alert')
    fireEvent.click(screen.getByRole('button', { name: BUTTON_NAME }))
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('다운로드를 시작'))
    expect(api.downloadUsageLogs).toHaveBeenCalledTimes(2)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('다운로드_실행_실패에도_임시_링크와_URL을_정리한다', async () => {
    vi.mocked(HTMLAnchorElement.prototype.click).mockImplementation(() => { throw new Error('blocked') })
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: BUTTON_NAME }))
    await screen.findByRole('alert')
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:coupon-csv')
    expect(document.querySelector('a[download]')).not.toBeInTheDocument()
  })
})
