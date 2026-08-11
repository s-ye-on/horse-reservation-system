#!/usr/bin/env node

import { spawn, spawnSync } from 'node:child_process'
import { existsSync, mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import net from 'node:net'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const requestedRuntimeDir = process.env.HORSE_E2E_RUNTIME_DIR
const runtimeDir = createRuntimeDirectory(requestedRuntimeDir)
const databaseName = `horse_e2e_${process.pid}_${Date.now().toString(36)}`
const databaseUser = process.env.MYSQL_USER ?? 'horse'
const timeoutMs = parseTimeout(process.env.HORSE_E2E_TEST_TIMEOUT_MS)
let databaseCreated = false
let child
let receivedSignal
let timedOut = false
let timeoutHandle

if (!/^[A-Za-z0-9_]+$/.test(databaseUser)) {
  throw new Error('MySQL test user contains unsupported characters.')
}

function createRuntimeDirectory(requestedDirectory) {
  if (requestedDirectory === undefined) {
    return mkdtempSync(path.join(os.tmpdir(), 'horse-e2e.'))
  }
  const resolvedDirectory = path.resolve(requestedDirectory)
  if (existsSync(resolvedDirectory)) {
    throw new Error(`E2E runtime directory already exists: ${resolvedDirectory}`)
  }
  mkdirSync(resolvedDirectory, { recursive: true })
  return resolvedDirectory
}

function parseTimeout(value) {
  if (value === undefined) return 0
  const parsed = Number(value)
  if (!Number.isSafeInteger(parsed) || parsed <= 0) {
    throw new Error('HORSE_E2E_TEST_TIMEOUT_MS must be a positive integer.')
  }
  return parsed
}

function runChecked(command, args, options = {}) {
  const result = spawnSync(command, args, {
    cwd: rootDir,
    encoding: 'utf8',
    stdio: options.input === undefined ? 'inherit' : ['pipe', 'inherit', 'inherit'],
    ...options,
  })
  if (result.status !== 0) {
    throw new Error(`${command} exited with status ${result.status ?? 'unknown'}.`)
  }
}

function executeRootSql(sql) {
  runChecked(
    'docker',
    [
      'exec', '-i', 'horse-mysql', 'sh', '-c',
      'exec env MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql --user=root --batch --skip-column-names',
    ],
    { input: sql },
  )
}

function allocatePort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer()
    server.once('error', reject)
    server.listen(0, '127.0.0.1', () => {
      const address = server.address()
      if (typeof address === 'string' || address === null) {
        server.close()
        reject(new Error('Failed to allocate a TCP port.'))
        return
      }
      server.close((error) => error ? reject(error) : resolve(address.port))
    })
  })
}

function terminateChild(signal = 'SIGTERM') {
  if (!child || child.exitCode !== null || child.signalCode !== null) return
  if (process.platform === 'win32') {
    child.kill(signal)
    return
  }
  try {
    process.kill(-child.pid, signal)
  } catch (error) {
    if (error.code !== 'ESRCH') throw error
  }
}

function cleanup() {
  let cleanupFailed = false
  if (databaseCreated) {
    try {
      executeRootSql(`DROP DATABASE IF EXISTS \`${databaseName}\`;\n`)
    } catch (error) {
      cleanupFailed = true
      process.stderr.write(`${error.message}\n`)
    }
  }
  rmSync(runtimeDir, { recursive: true, force: true })
  return !cleanupFailed
}

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    if (receivedSignal) return
    receivedSignal = signal
    if (!child) {
      cleanup()
      process.exit(signal === 'SIGINT' ? 130 : 143)
    }
    terminateChild(signal)
  })
}

async function main() {
  runChecked(process.env.MISE_BIN ?? 'mise', ['run', 'db:up'])
  databaseCreated = true
  executeRootSql(
    `CREATE DATABASE \`${databaseName}\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n`
    + `GRANT ALL PRIVILEGES ON \`${databaseName}\`.* TO '${databaseUser}'@'%';\n`,
  )
  const [backendPort, webPort] = await Promise.all([allocatePort(), allocatePort()])
  const environment = {
    ...process.env,
    HORSE_E2E_BACKEND_PORT: String(backendPort),
    HORSE_E2E_WEB_PORT: String(webPort),
    HORSE_E2E_BACKEND_BASE_URL: `http://127.0.0.1:${backendPort}`,
    HORSE_E2E_WEB_BASE_URL: `http://localhost:${webPort}`,
    HORSE_E2E_DATABASE: databaseName,
    HORSE_E2E_RUNTIME_DIR: runtimeDir,
    TMPDIR: runtimeDir,
    DB_URL: `jdbc:mysql://127.0.0.1:${process.env.MYSQL_PORT ?? '3306'}/${databaseName}`
      + '?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Seoul',
    DB_USERNAME: process.env.DB_USERNAME ?? databaseUser,
    DB_PASSWORD: process.env.DB_PASSWORD ?? process.env.MYSQL_PASSWORD ?? 'horse_dev',
  }

  const playwrightArgs = process.argv.slice(2)
  if (playwrightArgs[0] === '--') playwrightArgs.shift()

  child = spawn(
    'pnpm',
    ['--dir', 'frontend/apps/web', 'exec', 'playwright', 'test', ...playwrightArgs],
    {
      cwd: rootDir,
      env: environment,
      stdio: 'inherit',
      detached: process.platform !== 'win32',
    },
  )
  const metadata = { pid: process.pid, childPid: child.pid, backendPort, webPort, databaseName }
  writeFileSync(path.join(runtimeDir, 'resources.json'), `${JSON.stringify(metadata)}\n`)
  if (timeoutMs > 0) {
    timeoutHandle = setTimeout(() => {
      timedOut = true
      terminateChild()
    }, timeoutMs)
  }

  const result = await new Promise((resolve, reject) => {
    child.once('error', reject)
    child.once('exit', (code, signal) => resolve({ code, signal }))
  })
  if (timeoutHandle !== undefined) clearTimeout(timeoutHandle)
  const cleanupSucceeded = cleanup()

  if (!cleanupSucceeded) return 1
  if (timedOut) return 124
  if (receivedSignal === 'SIGINT') return 130
  if (receivedSignal === 'SIGTERM') return 143
  if (result.signal) return 1
  return result.code ?? 1
}

main()
  .then((exitCode) => {
    process.exitCode = exitCode
  })
  .catch((error) => {
    if (timeoutHandle !== undefined) clearTimeout(timeoutHandle)
    terminateChild()
    cleanup()
    process.stderr.write(`${error.stack ?? error.message}\n`)
    process.exitCode = 1
  })
