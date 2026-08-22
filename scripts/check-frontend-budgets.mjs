import { gzipSync } from 'node:zlib'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { basename, join } from 'node:path'

const root = new URL('../frontend/dist/', import.meta.url)
const assetsDirectory = new URL('assets/', root)
const files = readdirSync(assetsDirectory).filter(name => name.endsWith('.js'))
const gzipBytes = name => gzipSync(readFileSync(new URL(`assets/${name}`, root))).length
const manifest = JSON.parse(readFileSync(new URL('.vite/manifest.json', root), 'utf8'))
const entryKey = Object.keys(manifest).find(key => manifest[key].isEntry)
if (!entryKey) throw new Error('frontend entry was not generated')

const collectStaticFiles = (key, collected = new Set()) => {
  const chunk = manifest[key]
  if (!chunk) throw new Error(`manifest chunk is missing: ${key}`)
  if (chunk.file.endsWith('.js')) collected.add(basename(chunk.file))
  for (const importedKey of chunk.imports || []) collectStaticFiles(importedKey, collected)
  return collected
}

const main = [basename(manifest[entryKey].file)]
const editor = files.filter(name => name.startsWith('editor-vendor-'))
const initial = [...collectStaticFiles(entryKey)]

const assertBudget = (label, names, maximum) => {
  if (!names.length) throw new Error(`${label} chunk was not generated`)
  const bytes = names.reduce((total, name) => total + gzipBytes(name), 0)
  if (bytes > maximum) {
    throw new Error(`${label} gzip budget exceeded: ${bytes} > ${maximum} bytes (${names.join(', ')})`)
  }
  process.stdout.write(`${label}: ${bytes}/${maximum} gzip bytes\n`)
}

assertBudget('main chunk', main, 115 * 1024)
assertBudget('editor chunk', editor, 130 * 1024)
assertBudget('initial JavaScript', initial, 200 * 1024)

const publicBudgets = [
  ['app-icon-display.png', 100 * 1024],
  ['apple-touch-icon.png', 500 * 1024]
]
for (const [name, maximum] of publicBudgets) {
  const bytes = statSync(join(new URL('.', root).pathname, name)).size
  if (bytes > maximum) throw new Error(`${name} budget exceeded: ${bytes} > ${maximum} bytes`)
}
