import { Configuration } from '@horse/api-client'

const configuredBasePath = import.meta.env.VITE_API_BASE_URL?.trim()

const basePath =
  configuredBasePath ||
  (import.meta.env.DEV
    ? 'http://localhost:8080'
    : window.location.origin)

export const apiConfiguration = new Configuration({
  basePath: basePath.replace(/\/+$/, ''),
})
