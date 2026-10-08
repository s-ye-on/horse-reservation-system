import { useEffect, useId, useRef, type ReactNode } from 'react'

export function TimeSlotActionDialog({ title, pending, children, onClose }: {
  title: string; pending: boolean; children: ReactNode; onClose(): void
}) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const headingRef = useRef<HTMLHeadingElement>(null)
  const titleId = useId()
  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null
    const dialog = dialogRef.current
    dialog?.showModal()
    const overflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      dialog?.close()
      document.body.style.overflow = overflow
      if (opener?.isConnected && !opener.matches(':disabled')) opener.focus()
    }
  }, [])
  useEffect(() => { headingRef.current?.focus() }, [title])
  return <dialog ref={dialogRef} className="timeslot-dialog" aria-modal="true" aria-labelledby={titleId}
    onCancel={(event) => { event.preventDefault(); if (!pending) onClose() }}
    onKeyDown={(event) => {
      if (event.key !== 'Tab') return
      const tabbable = [...event.currentTarget.querySelectorAll<HTMLElement>('button, input:not([type="hidden"]), select, textarea, a[href], summary, [tabindex]')]
        .filter((element) => element.tabIndex >= 0 && !element.matches(':disabled')
          && !element.closest('[hidden], [inert]') && element.getClientRects().length > 0
          && getComputedStyle(element).visibility !== 'hidden')
      const first = tabbable[0], last = tabbable.at(-1)
      if (!first) { event.preventDefault(); headingRef.current?.focus() }
      else if (event.shiftKey && (document.activeElement === first || document.activeElement === headingRef.current)) {
        event.preventDefault(); last?.focus()
      } else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus() }
    }}>
    <header><h2 ref={headingRef} id={titleId} tabIndex={-1}>{title}</h2>
      <button type="button" aria-label="시간대 작업 창 닫기" disabled={pending} onClick={onClose}>닫기</button></header>
    <div className="timeslot-dialog-body">{children}</div>
  </dialog>
}
