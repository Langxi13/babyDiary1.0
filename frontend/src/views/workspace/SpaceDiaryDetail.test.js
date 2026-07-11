import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

const detail = readFileSync(new URL('./SpaceDiaryDetail.vue', import.meta.url), 'utf8')
const api = readFileSync(new URL('../../api/workspace.js', import.meta.url), 'utf8')

test('workspace comments expose edit and delete controls for their author', () => {
  assert.match(detail, /isOwnComment\(comment\)/)
  assert.match(detail, /beginCommentEdit\(comment\)/)
  assert.match(detail, /removeComment\(comment\)/)
  assert.match(api, /updateComment:/)
  assert.match(api, /removeComment:/)
})

test('private shares can be listed and revoked after creation', () => {
  assert.match(detail, /活动中的分享/)
  assert.match(detail, /active-share-list/)
  assert.match(detail, /revokeShare\(share\)/)
  assert.match(api, /shares:[\s\S]*list:/)
  assert.match(api, /shares:[\s\S]*revoke:/)
})
