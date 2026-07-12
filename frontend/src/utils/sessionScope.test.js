import assert from 'node:assert/strict'
import test from 'node:test'

import {
  advanceClientSessionGeneration,
  getClientSessionGeneration,
  isClientSessionGenerationCurrent
} from './sessionScope.js'

test('advancing the client session invalidates contexts captured by the previous account', () => {
  const previousGeneration = getClientSessionGeneration()
  assert.equal(isClientSessionGenerationCurrent(previousGeneration), true)

  const currentGeneration = advanceClientSessionGeneration()
  assert.equal(isClientSessionGenerationCurrent(previousGeneration), false)
  assert.equal(isClientSessionGenerationCurrent(currentGeneration), true)
})
