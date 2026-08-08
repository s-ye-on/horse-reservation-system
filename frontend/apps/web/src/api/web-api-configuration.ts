import { Configuration } from '@horse/api-client'
import { getWebAccessToken } from './web-access-token-memory'

const SAME_ORIGIN_API_BASE_PATH = ''

export const publicApiConfiguration = new Configuration({
  basePath: SAME_ORIGIN_API_BASE_PATH,
})

export const cookieApiConfiguration = new Configuration({
  basePath: SAME_ORIGIN_API_BASE_PATH,
  credentials: 'include',
})

export const bearerApiConfiguration = new Configuration({
  basePath: SAME_ORIGIN_API_BASE_PATH,
  accessToken: () => getWebAccessToken() ?? '',
})
