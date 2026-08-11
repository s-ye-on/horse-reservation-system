import assert from 'node:assert/strict'
import { createServer } from 'node:http'
import { after, before, test } from 'node:test'
import { createDemoProxy } from './local-demo-proxy.mjs'

const TEST_SECRET = 'local-demo-proxy-test-secret-32-bytes'
let upstream
let proxy
let proxyBaseUrl

before(async () => {
  upstream = createServer((request, response) => {
    response.writeHead(200, { 'content-type': 'application/json' })
    response.end(JSON.stringify({ authorization: request.headers.authorization }))
  })
  await listen(upstream)
  const upstreamAddress = upstream.address()
  proxy = createDemoProxy({
    targetPort: upstreamAddress.port,
    jwtSecret: TEST_SECRET,
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
})

test('개발_프록시는_회원과_관리자_권한_JWT를_주입한다', async () => {
  const response = await fetch(`${proxyBaseUrl}/api/me/reservations`, {
    headers: { origin: 'http://localhost:5173' },
  })
  const { authorization } = await response.json()
  const token = authorization.replace('Bearer ', '')
  const payload = JSON.parse(Buffer.from(token.split('.')[1], 'base64url').toString('utf8'))

  assert.equal(response.status, 200)
  assert.equal(response.headers.get('access-control-allow-origin'), 'http://localhost:5173')
  assert.equal(payload.sub, 'local-demo-member')
  assert.deepEqual(payload.roles, ['MEMBER', 'ADMIN'])
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
