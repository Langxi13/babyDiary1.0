<template>
  <el-dialog
    v-model="visible"
    class="step-up-dialog"
    modal-class="step-up-dialog-overlay"
    width="430px"
    append-to-body
    align-center
    :show-close="!submitting"
    :close-on-click-modal="false"
    :close-on-press-escape="!submitting"
    :before-close="beforeClose"
    @opened="focusPassword"
    @closed="handleClosed"
  >
    <template #header="{ titleId, titleClass }">
      <div class="step-up-dialog__heading">
        <span class="step-up-dialog__icon" aria-hidden="true">
          <el-icon><Lock /></el-icon>
        </span>
        <div>
          <h2 :id="titleId" :class="titleClass">验证身份</h2>
          <p>为保护私密内容，请确认是你本人。</p>
        </div>
      </div>
    </template>

    <form class="step-up-dialog__form" @submit.prevent="submit">
      <label class="step-up-dialog__field" for="step-up-password">
        <span>当前登录密码</span>
        <el-input
          id="step-up-password"
          ref="passwordInput"
          v-model="password"
          type="password"
          show-password
          autocomplete="current-password"
          placeholder="请输入当前密码"
          size="large"
          :disabled="submitting"
          @input="errorMessage = ''"
        />
      </label>

      <p v-if="errorMessage" class="step-up-dialog__error" role="alert">
        {{ errorMessage }}
      </p>

      <p class="step-up-dialog__note">
        验证通过后，短时间内进行其他敏感操作无需重复输入。
      </p>
    </form>

    <template #footer>
      <div class="step-up-dialog__actions">
        <el-button size="large" :disabled="submitting" @click="cancel">取消</el-button>
        <el-button type="primary" size="large" :loading="submitting" @click="submit">
          确认验证
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElButton } from 'element-plus/es/components/button/index.mjs'
import { ElDialog } from 'element-plus/es/components/dialog/index.mjs'
import { ElIcon } from 'element-plus/es/components/icon/index.mjs'
import { ElInput } from 'element-plus/es/components/input/index.mjs'
import { Lock } from '@element-plus/icons-vue'
import { registerStepUpDialog } from '@/utils/stepUpDialog'
import 'element-plus/es/components/button/style/css.mjs'
import 'element-plus/es/components/dialog/style/css.mjs'
import 'element-plus/es/components/icon/style/css.mjs'
import 'element-plus/es/components/input/style/css.mjs'

const visible = ref(false)
const password = ref('')
const errorMessage = ref('')
const submitting = ref(false)
const passwordInput = ref(null)

let pendingPromise = null
let resolvePending = null
let rejectPending = null
let unregisterDialog = null

const clearPending = () => {
  pendingPromise = null
  resolvePending = null
  rejectPending = null
}

const resetForm = () => {
  password.value = ''
  errorMessage.value = ''
  submitting.value = false
}

const focusPassword = async () => {
  await nextTick()
  passwordInput.value?.focus()
}

const open = () => {
  if (pendingPromise) return pendingPromise
  resetForm()
  visible.value = true
  pendingPromise = new Promise((resolve, reject) => {
    resolvePending = resolve
    rejectPending = reject
  })
  return pendingPromise
}

const cancelPending = () => {
  const reject = rejectPending
  clearPending()
  const error = new Error('已取消身份验证')
  error.code = 'STEP_UP_CANCELLED'
  reject?.(error)
}

const cancel = () => {
  if (submitting.value) return
  cancelPending()
  visible.value = false
}

const beforeClose = done => {
  if (submitting.value) return
  cancelPending()
  done()
}

const submit = async () => {
  if (submitting.value) return
  if (!password.value) {
    errorMessage.value = '请输入当前登录密码'
    await focusPassword()
    return
  }

  submitting.value = true
  errorMessage.value = ''
  try {
    const { authApi } = await import('@/api/auth')
    const response = await authApi.stepUp(password.value)
    const resolve = resolvePending
    clearPending()
    visible.value = false
    resolve?.(response.data)
  } catch (error) {
    errorMessage.value = error.response?.data?.detail
      || error.response?.data?.message
      || '验证失败，请检查密码后重试'
    await nextTick()
    passwordInput.value?.select()
  } finally {
    submitting.value = false
  }
}

const handleClosed = () => {
  if (pendingPromise) cancelPending()
  resetForm()
}

const handleSessionReset = () => {
  if (!visible.value) return
  cancelPending()
  visible.value = false
}

onMounted(() => {
  unregisterDialog = registerStepUpDialog(open)
  window.addEventListener('auth:session-reset', handleSessionReset)
})

onBeforeUnmount(() => {
  unregisterDialog?.()
  window.removeEventListener('auth:session-reset', handleSessionReset)
  if (pendingPromise) cancelPending()
})
</script>

<style src="./styles/StepUpDialog.scss" lang="scss"></style>
