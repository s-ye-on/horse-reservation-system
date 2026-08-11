import { QueryClient } from '@tanstack/react-query'

export const webQueryClient = new QueryClient()

export function clearWebAccountQueryCache() {
  webQueryClient.clear()
}
