import { useEffect, useId, useRef, type ReactNode } from 'react'

export function MemberChangeDialog({ title, pending, children, onClose, onConfirm }: {
  title: string
  pending: boolean
  children: ReactNode
  onClose(): void
  onConfirm(): void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const heading = useRef<HTMLHeadingElement>(null)
  const titleId = useId()
  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null
    const element = dialog.current
    element?.showModal()
    heading.current?.focus()
    return () => {
      element?.close()
      if (opener?.isConnected) opener.focus()
    }
  }, [])
  return <dialog ref={dialog} className="member-change-dialog" aria-modal="true" aria-labelledby={titleId}
    onCancel={(event) => { event.preventDefault(); if (!pending) onClose() }}
    onKeyDown={(event) => {
      if (event.key !== 'Tab') return
      const nodes = Array.from(event.currentTarget.querySelectorAll<HTMLElement>('button, input:not([type="hidden"]), select, textarea, a[href], summary, [contenteditable="true"], [tabindex]')).filter((node) => node.tabIndex >= 0 && !node.matches(':disabled') && !node.closest('[hidden]') && node.getClientRects().length > 0 && getComputedStyle(node).visibility !== 'hidden')
      const first = nodes[0], last = nodes[nodes.length - 1]
      if (!first) { event.preventDefault(); heading.current?.focus(); return }
      if (event.shiftKey && (document.activeElement === first || document.activeElement === heading.current)) { event.preventDefault(); last.focus() }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
    }}>
    <header><div><p className="admin-members-eyebrow">변경 확인</p><h2 id={titleId} ref={heading} tabIndex={-1}>{title}</h2><p>선택한 회원 한 명의 정보만 변경합니다.</p></div><button type="button" aria-label="확인 창 닫기" disabled={pending} onClick={onClose}>닫기</button></header>
    <div className="member-change-dialog-body">{children}</div>
    <footer><button type="button" disabled={pending} onClick={onClose}>돌아가기</button><button type="button" className="admin-member-primary-action" disabled={pending} onClick={onConfirm}>{pending ? '적용 중' : '변경 적용'}</button></footer>
  </dialog>
}
