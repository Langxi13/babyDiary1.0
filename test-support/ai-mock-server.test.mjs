import assert from 'node:assert/strict'
import { once } from 'node:events'
import test from 'node:test'

import { createAiMockServer } from './ai-mock-server.mjs'

test('AI E2E mock exposes health models and chat completion endpoints', async t => {
  const server = createAiMockServer()
  server.listen(0, '127.0.0.1')
  await once(server, 'listening')
  t.after(() => new Promise(resolveClose => server.close(resolveClose)))

  const address = server.address()
  const baseUrl = `http://127.0.0.1:${address.port}`

  const health = await fetch(`${baseUrl}/health`).then(response => response.json())
  assert.equal(health.status, 'UP')

  const models = await fetch(`${baseUrl}/v1/models`).then(response => response.json())
  assert.deepEqual(models.data, [{ id: 'baby-diary-test-model' }])

  const completion = await fetch(`${baseUrl}/v1/chat/completions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ messages: [{ role: 'user', content: '生成周报' }] })
  }).then(response => response.json())
  assert.match(completion.choices[0].message.content, /你们一起记录了/)
})
