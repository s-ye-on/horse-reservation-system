export type AuthField = 'email' | 'password' | 'name' | 'phone'
export type AuthFieldErrors = Partial<Record<AuthField, string>>

export const AUTH_PASSWORD_HELP = '8자 이상, UTF-8 기준 72바이트 이하로 입력해 주세요. 한글은 보통 한 글자에 3바이트를 사용합니다.'

export function validateAuthForm(values: Partial<Record<AuthField, string>>, signup: boolean): AuthFieldErrors {
  const errors: AuthFieldErrors = {}
  const email = values.email?.trim() ?? ''
  if (!email || email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    errors.email = '올바른 이메일 주소를 입력해 주세요.'
  }
  if (!values.password?.trim()) errors.password = '비밀번호를 입력해 주세요.'
  else if (signup && (values.password.length < 8 || new TextEncoder().encode(values.password).length > 72)) {
    errors.password = AUTH_PASSWORD_HELP
  }
  if (signup) {
    if (!values.name?.trim() || values.name.length > 100) errors.name = '이름을 1~100자로 입력해 주세요.'
    if (!values.phone?.trim() || values.phone.length > 30) errors.phone = '전화번호를 1~30자로 입력해 주세요.'
  }
  return errors
}
