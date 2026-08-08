import { createHmac } from 'node:crypto'
import { createServer, request as createRequest } from 'node:http'
import { pathToFileURL } from 'node:url'

const DEFAULT_ALLOWED_ORIGINS = new Set([
  'http://127.0.0.1:5173',
  'http://localhost:5173',
])
const ALLOWED_METHODS = 'GET,POST,PUT,PATCH,DELETE,OPTIONS'

export function createDemoProxy({
  targetHost = '127.0.0.1',
  targetPort,
  jwtSecret,
  authSubject = 'local-demo-member',
  allowedOrigins = DEFAULT_ALLOWED_ORIGINS,
}) {
  if (!targetPort || !jwtSecret) {
    throw new Error('targetPort and jwtSecret are required')
  }

  return createServer((incoming, outgoing) => {
    const corsHeaders = createCorsHeaders(incoming.headers.origin, allowedOrigins)
    if (incoming.method === 'OPTIONS') {
      outgoing.writeHead(204, corsHeaders)
      outgoing.end()
      return
    }

    const upstream = createRequest({
      hostname: targetHost,
      port: targetPort,
      path: incoming.url,
      method: incoming.method,
      headers: {
        ...incoming.headers,
        host: `${targetHost}:${targetPort}`,
        authorization: incoming.headers.authorization ?? `Bearer ${createJwt(authSubject, jwtSecret)}`,
      },
    }, (response) => {
      outgoing.writeHead(response.statusCode ?? 502, {
        ...response.headers,
        ...corsHeaders,
      })
      response.pipe(outgoing)
    })

    upstream.on('error', () => {
      if (!outgoing.headersSent) {
        outgoing.writeHead(502, { ...corsHeaders, 'content-type': 'application/json' })
      }
      outgoing.end(JSON.stringify({ message: 'Local demo backend is unavailable.' }))
    })
    incoming.pipe(upstream)
  })
}

export function createJwt(subject, secret, now = Math.floor(Date.now() / 1000)) {
  const header = encodeJwtPart({ alg: 'HS256', typ: 'JWT' })
  const payload = encodeJwtPart({
    sub: subject,
    roles: ['MEMBER', 'ADMIN'],
    iat: now,
    exp: now + 60 * 60 * 12,
  })
  const signature = createHmac('sha256', secret)
    .update(`${header}.${payload}`)
    .digest('base64url')
  return `${header}.${payload}.${signature}`
}

export function createCorsHeaders(origin, allowedOrigins = DEFAULT_ALLOWED_ORIGINS) {
  const allowedOrigin = origin && allowedOrigins.has(origin)
    ? origin
    : 'http://127.0.0.1:5173'
  return {
    'access-control-allow-origin': allowedOrigin,
    'access-control-allow-methods': ALLOWED_METHODS,
    'access-control-allow-headers': 'authorization,content-type',
    vary: 'Origin',
  }
}

function encodeJwtPart(value) {
  return Buffer.from(JSON.stringify(value)).toString('base64url')
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const port = Number(process.env.DEMO_PROXY_PORT ?? 8080)
  const targetPort = Number(process.env.DEMO_BACKEND_PORT ?? 8081)
  const jwtSecret = process.env.JWT_SECRET
  const server = createDemoProxy({ targetPort, jwtSecret })
  server.listen(port, '127.0.0.1', () => {
    process.stdout.write(`Local demo API proxy listening on http://127.0.0.1:${port}\n`)
  })
}
