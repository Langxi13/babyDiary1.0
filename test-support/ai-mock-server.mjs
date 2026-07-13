import { createServer } from 'node:http'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'

const MAX_REQUEST_BYTES = 1024 * 1024

const sendJson = (response, status, payload) => {
  response.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store'
  })
  response.end(JSON.stringify(payload))
}

const consumeRequestBody = async request => {
  let size = 0
  for await (const chunk of request) {
    size += chunk.length
    if (size > MAX_REQUEST_BYTES) throw new Error('request body too large')
  }
}

export const createAiMockServer = () => createServer(async (request, response) => {
  const url = new URL(request.url || '/', `http://${request.headers.host || '127.0.0.1'}`)

  if (request.method === 'GET' && url.pathname === '/health') {
    sendJson(response, 200, { status: 'UP' })
    return
  }

  if (request.method === 'GET' && url.pathname === '/v1/models') {
    sendJson(response, 200, { data: [{ id: 'baby-diary-test-model' }] })
    return
  }

  if (request.method === 'POST' && url.pathname === '/v1/chat/completions') {
    try {
      await consumeRequestBody(request)
    } catch {
      sendJson(response, 413, { error: { message: 'request body too large' } })
      return
    }
    sendJson(response, 200, {
      choices: [{
        message: {
          role: 'assistant',
          content: '# 测试报告\n\n你们一起记录了值得回顾的时刻。'
        }
      }]
    })
    return
  }

  sendJson(response, 404, { error: { message: 'not found' } })
})

const isMainModule = process.argv[1]
  && import.meta.url === pathToFileURL(resolve(process.argv[1])).href

if (isMainModule) {
  const host = process.env.AI_MOCK_HOST || '127.0.0.1'
  const port = Number(process.env.AI_MOCK_PORT || 8090)
  const server = createAiMockServer()

  server.on('error', error => {
    console.error(`AI mock failed: ${error.message}`)
    process.exitCode = 1
  })
  server.listen(port, host, () => {
    console.log(`AI mock listening on http://${host}:${port}`)
  })

  const shutdown = () => server.close(() => process.exit(0))
  process.on('SIGINT', shutdown)
  process.on('SIGTERM', shutdown)
}
