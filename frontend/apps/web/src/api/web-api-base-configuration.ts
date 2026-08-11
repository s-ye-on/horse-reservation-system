import { Configuration } from '@horse/api-client'

export const SAME_ORIGIN_API_BASE_PATH = ''

export const publicApiConfiguration = new Configuration({
  basePath: SAME_ORIGIN_API_BASE_PATH,
})

export const cookieApiConfiguration = new Configuration({
  basePath: SAME_ORIGIN_API_BASE_PATH,
  credentials: 'include',
})
