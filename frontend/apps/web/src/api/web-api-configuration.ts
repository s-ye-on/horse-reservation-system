import { Configuration } from '@horse/api-client'
import { getWebAccessToken } from './web-access-token-memory'
import { authenticatedFetch } from './web-authenticated-fetch'
import {
  cookieApiConfiguration,
  publicApiConfiguration,
  SAME_ORIGIN_API_BASE_PATH,
} from './web-api-base-configuration'

export { cookieApiConfiguration, publicApiConfiguration }

export const bearerApiConfiguration = new Configuration({
  basePath: SAME_ORIGIN_API_BASE_PATH,
  accessToken: () => getWebAccessToken() ?? '',
  fetchApi: authenticatedFetch,
})
