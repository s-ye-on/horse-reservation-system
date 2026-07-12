/// <reference types="jest" />

import { API_BASE_URL } from './api'

describe('API_BASE_URL', () => {
  it('has a local development fallback', () => {
    expect(API_BASE_URL).toMatch(/^https?:\/\//)
  })
})
