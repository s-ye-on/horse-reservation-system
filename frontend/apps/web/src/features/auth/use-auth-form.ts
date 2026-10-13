import { useEffect, useRef, useState } from 'react'
import { getAuthFormError } from './auth-api'
import type { AuthField, AuthFieldErrors } from './auth-form-validation'

export function useAuthForm() {
  const formRef = useRef<HTMLFormElement>(null)
  const alertRef = useRef<HTMLParagraphElement>(null)
  const submittingRef = useRef(false)
  const shouldFocusError = useRef(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [error, setError] = useState<{ message: string; fields: AuthFieldErrors } | null>(null)

  useEffect(() => {
    if (!error || !shouldFocusError.current) return
    shouldFocusError.current = false
    const field = Object.keys(error.fields)[0]
    const input = field ? formRef.current?.elements.namedItem(field) : null
    if (input instanceof HTMLElement) input.focus()
    else alertRef.current?.focus()
  }, [error])

  function clearFieldError(field: AuthField) {
    setError((current) => {
      if (!current) return null
      const fields = { ...current.fields }
      delete fields[field]
      return Object.keys(fields).length ? { message: '입력 내용을 확인해 주세요.', fields } : null
    })
  }

  async function submit(fields: AuthFieldErrors, action: () => Promise<void>, fallback: string) {
    if (submittingRef.current) return
    if (Object.keys(fields).length) {
      shouldFocusError.current = true
      setError({ message: '입력 내용을 확인해 주세요.', fields })
      return
    }
    submittingRef.current = true
    setIsSubmitting(true)
    setError(null)
    try {
      await action()
    } catch (failure) {
      shouldFocusError.current = true
      setError(await getAuthFormError(failure, fallback))
    } finally {
      submittingRef.current = false
      setIsSubmitting(false)
    }
  }

  return { formRef, alertRef, isSubmitting, error, clearFieldError, submit }
}
