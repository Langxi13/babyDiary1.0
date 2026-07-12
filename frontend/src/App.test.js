import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'

const source = readFileSync(new URL('./App.vue', import.meta.url), 'utf8')

test('app provides Chinese locale for Element Plus date components', () => {
  assert.match(source, /<el-config-provider\s+:locale="zhCn">/)
  assert.match(source, /import zhCn from 'element-plus\/es\/locale\/lang\/zh-cn\.mjs'/)
})

test('app refreshes authenticated user info on startup and foreground resume', () => {
  assert.match(source, /import\s*\{\s*onBeforeUnmount,\s*onMounted,\s*watch\s*\}\s*from 'vue'/)
  assert.match(source, /import\s*\{\s*useAuthStore\s*\}\s*from '@\/stores\/auth'/)
  assert.match(source, /const authStore = useAuthStore\(\)/)
  assert.match(source, /const refreshUserInfo = \(\) => \{[\s\S]*?authStore\.getUserInfo\(\)/)
  assert.match(source, /document\.addEventListener\('visibilitychange', refreshUserInfo\)/)
  assert.match(source, /window\.addEventListener\('focus', refreshUserInfo\)/)
  assert.match(source, /onMounted\(\(\) => \{[\s\S]*?refreshUserInfo\(\)/)
  assert.match(source, /onBeforeUnmount\(\(\) => \{[\s\S]*?document\.removeEventListener\('visibilitychange', refreshUserInfo\)/)
})

test('protected page content is hidden as soon as the local session is cleared', () => {
  assert.match(source, /<mobile-app-shell v-if="route\.meta\.requiresAuth && authStore\.isLoggedIn" :key="authStore\.sessionVersion">/)
  assert.match(source, /<router-view v-else-if="!route\.meta\.requiresAuth"/)
})

test('app mounts one reusable identity verification dialog', () => {
  assert.match(source, /<step-up-dialog\s*\/>/)
  assert.match(source, /import StepUpDialog from '@\/components\/security\/StepUpDialog\.vue'/)
})
