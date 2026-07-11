import { execFileSync } from 'node:child_process'
import path from 'node:path'

export default async function globalTeardown() {
  const projectRoot = path.resolve('..')
  try {
    execFileSync('docker', [
      'compose',
      '-p', 'baby-diary-e2e',
      '-f', path.join(projectRoot, 'compose.e2e.yaml'),
      'down', '--volumes', '--remove-orphans'
    ], { cwd: projectRoot, stdio: 'inherit' })
  } catch (error) {
    process.stderr.write(`Unable to clean E2E containers: ${error.message}\n`)
  }
}
