import { useEffect, useId, useRef, type ReactNode } from 'react'

export function FamilyGroupActionDialog({ title, confirmLabel, pending, children, onClose, onConfirm }: {
  title: string; confirmLabel: string; pending: boolean; children: ReactNode; onClose(): void; onConfirm(): void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const heading = useRef<HTMLHeadingElement>(null)
  const titleId = useId()
  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null
    const element = dialog.current
    element?.showModal()
    heading.current?.focus()
    const previousOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      element?.close()
      document.body.style.overflow = previousOverflow
      if (opener?.isConnected && !opener.matches(':disabled')) opener.focus()
    }
  }, [])
  return <dialog ref={dialog} className="family-action-dialog" aria-modal="true" aria-labelledby={titleId}
    onCancel={(event) => { event.preventDefault(); if (!pending) onClose() }}
    onKeyDown={(event) => {
      if (event.key !== 'Tab') return
      const nodes = [...event.currentTarget.querySelectorAll<HTMLElement>('button, input:not([type="hidden"]), select, textarea, a[href], summary, [contenteditable="true"], [tabindex]')]
        .filter((node) => node.tabIndex >= 0 && !node.matches(':disabled') && !node.closest('[hidden], [inert]') && node.getClientRects().length > 0 && getComputedStyle(node).visibility !== 'hidden')
      const first = nodes[0], last = nodes[nodes.length - 1]
      if (!first) { event.preventDefault(); heading.current?.focus() }
      else if (event.shiftKey && (document.activeElement === first || document.activeElement === heading.current)) { event.preventDefault(); last.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
    }}>
    <header><div><p className="admin-family-eyebrow">작업 확인</p><h2 id={titleId} ref={heading} tabIndex={-1}>{title}</h2></div><button type="button" aria-label="확인 창 닫기" disabled={pending} onClick={onClose}>닫기</button></header>
    <div className="family-dialog-body">{children}</div>
    <footer><button type="button" disabled={pending} onClick={onClose}>돌아가기</button><button type="button" className="family-primary" disabled={pending} onClick={onConfirm}>{pending ? '처리 중' : confirmLabel}</button></footer>
  </dialog>
}
