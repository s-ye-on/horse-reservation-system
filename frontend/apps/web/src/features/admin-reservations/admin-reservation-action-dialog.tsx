import { useEffect, useId, useRef, type ReactNode } from 'react'

export function AdminReservationActionDialog({ title, pending, onClose, children }: {
  title: string; pending: boolean; onClose(): void; children: ReactNode
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const heading = useRef<HTMLHeadingElement>(null)
  const titleId = useId()
  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null
    const element = dialog.current
    element?.showModal()
    heading.current?.focus()
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      element?.close()
      document.body.style.overflow = overflow
      if (opener?.isConnected && !opener.matches(':disabled')) opener.focus()
    }
  }, [])
  return <dialog ref={dialog} className="admin-reservation-dialog" aria-modal="true" aria-labelledby={titleId}
    onCancel={(event) => { event.preventDefault(); if (!pending) onClose() }}
    onKeyDown={(event) => {
      if (event.key !== 'Tab') return
      const nodes = [...event.currentTarget.querySelectorAll<HTMLElement>('button, input:not([type="hidden"]), select, textarea, a[href], summary, [tabindex]')]
        .filter((node) => node.tabIndex >= 0 && !node.matches(':disabled') && !node.closest('[hidden], [inert]') && node.getClientRects().length > 0 && getComputedStyle(node).visibility !== 'hidden')
      const first = nodes[0], last = nodes[nodes.length - 1]
      if (!first) { event.preventDefault(); heading.current?.focus() }
      else if (event.shiftKey && (document.activeElement === first || document.activeElement === heading.current)) { event.preventDefault(); last.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
    }}>
    <header><div><p className="admin-reservations-eyebrow">예약 작업</p><h2 id={titleId} ref={heading} tabIndex={-1}>{title}</h2></div>
      <button type="button" aria-label="확인 창 닫기" disabled={pending} onClick={onClose}>닫기</button></header>
    <div className="admin-reservation-dialog-body">{children}</div>
  </dialog>
}
