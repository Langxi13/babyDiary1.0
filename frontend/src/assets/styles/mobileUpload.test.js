import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = [
  readFileSync(new URL('./main.scss', import.meta.url), 'utf8'),
  readFileSync(new URL('./mobile-foundation.scss', import.meta.url), 'utf8')
].join('\n')

test('mobile shell uses the native file input as the upload tap target', () => {
  assert.match(source, /\.mobile-shell-content\s*\{[\s\S]*?\.el-upload\s*\{[\s\S]*?position:\s*relative;/)
  assert.match(source, /\.el-upload__input\s*\{[\s\S]*?display:\s*block\s*!important;/)
  assert.match(source, /\.el-upload__input\s*\{[\s\S]*?position:\s*absolute;/)
  assert.match(source, /\.el-upload__input\s*\{[\s\S]*?opacity:\s*0;/)
  assert.match(source, /\.el-upload__input\s*\{[\s\S]*?z-index:\s*2;/)
})
