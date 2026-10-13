import type { InputHTMLAttributes } from 'react'

interface AuthFormFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  id: string
  label: string
  hint?: string
  error?: string
}

export function AuthFormField({ id, label, hint, error, ...inputProps }: AuthFormFieldProps) {
  const description = [hint ? `${id}-help` : '', error ? `${id}-error` : ''].filter(Boolean).join(' ')
  return (
    <div className="auth-form-field">
      <label htmlFor={id}>{label}</label>
      <input {...inputProps} id={id} aria-invalid={error ? true : undefined} aria-describedby={description || undefined} />
      {hint ? <p id={`${id}-help`} className="auth-field-hint">{hint}</p> : null}
      {error ? <p id={`${id}-error`} className="auth-field-error">{error}</p> : null}
    </div>
  )
}
