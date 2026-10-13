import { describe, expect, it } from 'vitest'
import { validateAuthForm } from './auth-form-validation'

const values = { email: 'member@horse.test', name: '회원', phone: '010-1234-5678' }

describe('회원가입 비밀번호 경계', () => {
  it.each([
    ['ASCII 72바이트', 'a'.repeat(72), true],
    ['ASCII 73바이트', 'a'.repeat(73), false],
    ['한글 72바이트', '가'.repeat(24), true],
    ['한글 75바이트', '가'.repeat(25), false],
    ['이모지 72바이트', '😀'.repeat(18), true],
    ['이모지 76바이트', '😀'.repeat(19), false],
    ['혼합 72바이트', '가'.repeat(20) + 'a'.repeat(12), true],
    ['8자 미만', 'a'.repeat(7), false],
  ])('%s를 서버 기준으로 검증한다', (_label, password, allowed) => {
    expect(!validateAuthForm({ ...values, password }, true).password).toBe(allowed)
  })

  it('로그인은 회원가입 비밀번호 길이 조건을 새로 적용하지 않는다', () => {
    expect(validateAuthForm({ email: values.email, password: 'short' }, false)).toEqual({})
  })

  it('공백 필수 값과 필드 길이를 검증한다', () => {
    expect(Object.keys(validateAuthForm({ email: 'invalid', password: ' ', name: ' ', phone: '1'.repeat(31) }, true)))
      .toEqual(['email', 'password', 'name', 'phone'])
  })
})
