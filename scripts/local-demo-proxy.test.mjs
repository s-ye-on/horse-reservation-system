import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { after, before, test } from 'node:test'
import { createDemoProxy } from './local-demo-proxy.mjs'

let upstream
let proxy
let proxyBaseUrl

before(async () => {
  upstream = createServer((request, response) => {
    if (request.url === '/api/auth/web/refresh') {
      response.writeHead(401, {
        'content-type': 'application/json',
        'set-cookie': 'REFRESH_TOKEN=; Max-Age=0; Path=/; HttpOnly',
      })
      response.end(JSON.stringify({ code: 'AUTH_REQUIRED' }))
      return
    }
    response.writeHead(200, { 'content-type': 'application/json' })
    response.end(JSON.stringify({
      authorization: request.headers.authorization,
      cookie: request.headers.cookie,
      csrf: request.headers['x-xsrf-token'],
    }))
  })
  await listen(upstream)
  const upstreamAddress = upstream.address()
  proxy = createDemoProxy({
    targetPort: upstreamAddress.port,
  })
  await listen(proxy)
  proxyBaseUrl = `http://127.0.0.1:${proxy.address().port}`
})

after(async () => {
  await Promise.all([close(proxy), close(upstream)])
})

test('개발_프록시는_브라우저_preflight에_필요한_메서드를_허용한다', async () => {
  const response = await fetch(`${proxyBaseUrl}/api/me/reservations`, {
    method: 'OPTIONS',
    headers: { origin: 'http://127.0.0.1:5173' },
  })

  assert.equal(response.status, 204)
  assert.equal(response.headers.get('access-control-allow-origin'), 'http://127.0.0.1:5173')
  assert.equal(response.headers.get('access-control-allow-methods'), 'GET,POST,PUT,PATCH,DELETE,OPTIONS')
  assert.equal(response.headers.get('access-control-allow-headers'), 'authorization,content-type,x-xsrf-token')
  assert.equal(response.headers.get('access-control-allow-credentials'), 'true')
})

test('개발_프록시는_인증되지_않은_요청에_JWT를_주입하지_않는다', async () => {
  const response = await fetch(`${proxyBaseUrl}/api/me/reservations`, {
    headers: { origin: 'http://localhost:5173' },
  })
  const { authorization } = await response.json()

  assert.equal(response.status, 200)
  assert.equal(response.headers.get('access-control-allow-origin'), 'http://localhost:5173')
  assert.equal(authorization, undefined)
})

test('개발_프록시는_Web_인증_응답과_쿠키를_그대로_전달한다', async () => {
  const refreshResponse = await fetch(`${proxyBaseUrl}/api/auth/web/refresh`, {
    method: 'POST',
    headers: {
      origin: 'http://127.0.0.1:5173',
      cookie: 'XSRF-TOKEN=csrf-token',
      'x-xsrf-token': 'csrf-token',
    },
  })

  assert.equal(refreshResponse.status, 401)
  assert.deepEqual(await refreshResponse.json(), { code: 'AUTH_REQUIRED' })
  assert.match(refreshResponse.headers.get('set-cookie'), /REFRESH_TOKEN=/)
})

test('개발_프록시는_Web_인증_쿠키와_CSRF_헤더를_Backend에_전달한다', async () => {
  const response = await fetch(`${proxyBaseUrl}/api/auth/web/logout`, {
    method: 'POST',
    headers: {
      cookie: 'XSRF-TOKEN=csrf-token',
      'x-xsrf-token': 'csrf-token',
    },
  })
  const payload = await response.json()

  assert.equal(payload.cookie, 'XSRF-TOKEN=csrf-token')
  assert.equal(payload.csrf, 'csrf-token')
})

test('개발_프록시는_브라우저가_보낸_Authorization을_덮어쓰지_않는다', async () => {
  const response = await fetch(`${proxyBaseUrl}/api/auth/me`, {
    headers: {
      origin: 'http://localhost:5173',
      authorization: 'Bearer browser-access-token',
    },
  })
  const { authorization } = await response.json()

  assert.equal(response.status, 200)
  assert.equal(authorization, 'Bearer browser-access-token')
})

function listen(server) {
  return new Promise((resolve) => server.listen(0, '127.0.0.1', resolve))
}

function close(server) {
  return new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()))
}
