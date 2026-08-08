let accessToken: string | null = null

export function getWebAccessToken(): string | null {
  return accessToken
}

export function setWebAccessToken(token: string): void {
  accessToken = token
}

export function clearWebAccessToken(): void {
  accessToken = null
}
